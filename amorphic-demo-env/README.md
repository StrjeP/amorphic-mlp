# Running the demo

Everything runs from this directory with one command, `./demo`. A stage is a different address table plus a different set of running containers. The app's code is the same in every stage. For the narrative, see [../docs/story.md](../docs/story.md).

The outputs below were captured from a real run on 2026-11-06. Request IDs, timestamps and the exact counts change on every run, because the traffic is random.

## Prerequisites

- Docker with Compose v2, `curl`, `jq`.
- The git tags `pricing-calc-java/v2.0` and `pricing-calc-java/v2.1`. `./demo` builds each Java image from its tag (`REBUILD=1` forces a rebuild), so the working tree never has to change to replay the bug.

## Rates and edge cases

| Pair | Rate |
|---|---|
| GBP→USD | 1.25 |
| EUR→USD | 1.05 |
| GBP→EUR | 1.19 |

Some amounts convert to exactly half a penny, for example 10.02 GBP → 12.525 USD. When the digit before the 5 is even, HALF_EVEN (Python) rounds down to 12.52, while HALF_UP (Java v2.0) rounds up to 12.53. `traffic/edge-inputs.txt` lists such inputs, and `./demo traffic` mixes them in (20% by default). Random amounts hit the case too: about 1 in 8 GBP→USD amounts does.

## Commands

| Command | What it does |
|---|---|
| `./demo stage <id> [--weight N]` | Bring up a stage: start the services it routes to and wait for their `/health`, print the address-table change, switch the app's routing **without a restart**, then stop services the new routing no longer uses. Only folding core in (stage 4) or out rebuilds the app. Every applied table starts a new evidence generation. |
| `./demo apply <file>` | Push any address table to the running app. Routing only: it doesn't start or stop containers. A table the app can't honour is rejected and the current routing stays live. |
| `./demo diff <from> <to>` | Show what the governor changes between two stages. |
| `./demo quote <amount> [from] [to]` | One `/quote` call. |
| `./demo traffic [--requests N] [--edge-share PCT]` | Steady load (default 1200 requests). |
| `./demo evidence` | The `/evidence` summary. |
| `./demo inventory [--json]` | Observe the system now: routing, `/health` of each address, readiness and the catalogue (see [Inventory](#inventory)). |
| `./demo gate [N]` | The governor's gate. It passes only with ≥ N consecutive matches (default 1000) and 0 mismatches. |
| `./demo status` / `logs` / `down` | Show the active stage, follow logs, or stop everything. |

## Walkthrough

### Stage 0: Baseline

```bash
./demo stage 0
./demo quote 10.02
```

```json
{
  "amount": "12.52",
  "currency": "USD",
  "servedBy": "pkg:amorphic/pricing-calc@1.0?lang=python",
  "binding": "remote",
  "requestId": "b8c63e85-90b2-4db6-b83d-08158df8b12b"
}
```

### Stage 1: Shadow

```bash
./demo stage 1
./demo traffic
./demo gate
```

```text
Comparisons since last reset (need >= 1000 consecutive matches, 0 mismatches):
  pkg:amorphic/pricing-calc@1.0?lang=python  vs  pkg:amorphic/pricing-calc@2.0?lang=java
    total 1200  matches 860  mismatches 340  consecutive 1
GATE FAIL
  last mismatch: 732.98 GBP->USD  served 916.22  shadow 916.23  (request traffic-1793973261-1199)
```

Customers only ever got Python's answers. The caught input wasn't one of the hand-picked edge cases: 732.98 was an ordinary random amount (× 1.25 = 916.225). The evidence log line for that call (`./demo logs app`):

```json
{"timestamp":"2026-11-06T13:54:43.135433423Z","requestId":"traffic-1793973261-1199","input":{"amount":"732.98","from":"GBP","to":"USD"},"address_served":"pkg:amorphic/pricing-calc@1.0?lang=python","binding_served":"remote","role_served":"champion","address_shadow":"pkg:amorphic/pricing-calc@2.0?lang=java","served_value":"916.22","shadow_value":"916.23","match":false}
```

This input became `RoundingRegressionTest` in v2.1, and a new case in `golden/vectors.json`.

### Stage 1b: Fix

```bash
./demo stage 1b
./demo traffic
./demo gate
```

```diff
     shadow:
-      address: pkg:amorphic/pricing-calc@2.0?lang=java
+      address: pkg:amorphic/pricing-calc@2.1?lang=java
```

```text
  pkg:amorphic/pricing-calc@1.0?lang=python  vs  pkg:amorphic/pricing-calc@2.1?lang=java
    total 1200  matches 1200  mismatches 0  consecutive 1200
GATE PASS
```

### Stage 2: Canary

At 10% only about one call in ten is served by Java and compared, so the gate runs with a lower bar there:

```bash
./demo stage 2
./demo traffic --requests 1500
./demo gate 100
```

```text
  pkg:amorphic/pricing-calc@2.1?lang=java  vs  pkg:amorphic/pricing-calc@1.0?lang=python
    total 129  matches 129  mismatches 0  consecutive 129
GATE PASS
```

Then raise the weight and use the full bar:

```bash
./demo stage 2 --weight 50
./demo traffic --requests 2500
./demo gate
```

```text
  pkg:amorphic/pricing-calc@2.1?lang=java  vs  pkg:amorphic/pricing-calc@1.0?lang=python
    total 1198  matches 1198  mismatches 0  consecutive 1198
GATE PASS
```

`./demo evidence` shows the split: Java served 1198 calls and Python served 1302.

### Stage 3: Promote

```bash
./demo stage 3
./demo traffic
./demo gate
```

```diff
     champion:
-      address: pkg:amorphic/pricing-calc@1.0?lang=python
-      binding: { type: remote, url: http://py-pricing:8000 }
-    canary:
       address: pkg:amorphic/pricing-calc@2.1?lang=java
       binding: { type: remote, url: http://java-pricing:8080 }
-      weight: 50
+    shadow:
+      address: pkg:amorphic/pricing-calc@1.0?lang=python
+      binding: { type: remote, url: http://py-pricing:8000 }
```

```text
  pkg:amorphic/pricing-calc@2.1?lang=java  vs  pkg:amorphic/pricing-calc@1.0?lang=python
    total 1200  matches 1200  mismatches 0  consecutive 1200
GATE PASS
```

### Stage 3b: Deprecate

```bash
./demo stage 3b
./demo traffic --requests 200
./demo evidence
```

```json
{"served":[{"address":"pkg:amorphic/pricing-calc@2.1?lang=java","binding":"remote","total":200}],"comparisons":[]}
```

Only `app` and `java-pricing` are running. `./demo gate` reports that nothing is being compared, which is expected here.

### Stage 4: Fold-in

```bash
./demo stage 4
./demo quote 732.98
./demo evidence
```

```diff
-      binding: { type: remote, url: http://java-pricing:8080 }
+      binding: { type: local }
```

```json
{"amount":"916.22","servedBy":"pkg:amorphic/pricing-calc@2.1?lang=java","binding":"local"}
```

```json
{"served":[{"address":"pkg:amorphic/pricing-calc@2.1?lang=java","binding":"local","total":202}],"comparisons":[]}
```

Only `app` (image `amorphic/demo-app:foldin`) is running. The app built without fold-in refuses the stage 4 table at startup:

```text
APPLICATION FAILED TO START

Description:

address pkg:amorphic/pricing-calc@2.1?lang=java has a local binding, but pricing-calc-core is not in this build. Rebuild the app with -PfoldIn=true (./demo stage 4 does this).
```

Going back works too: `./demo stage 3b` after stage 4 puts the remote binding back, rebuilding the app without core.

```bash
./demo down
```

## Switching without a restart

The app holds its routing behind one reference. A new address table is resolved and checked first, then swapped in, so requests never see a half-applied table. Each applied table is a new **generation**, and evidence restarts with it. A comparison that was still running under the old table when the switch happened carries the old generation, and is dropped instead of being counted against the new one.

In the run below (2026-11-09), the app container started once in stage 0 and kept running through stages 1, 1b, 2 (10% and 50%), 3 and 3b: generations 2 to 7. The gate results matched the restart-per-stage run above.

```text
Stage 1:  Applied without restart: generation 2   ->  GATE FAIL (366 mismatches of 1200)
Stage 1b: Applied without restart: generation 3   ->  GATE PASS (1200 of 1200)
Stage 2:  Applied without restart: generation 4   ->  GATE PASS at 10% (156 Java-served, all matched)
Stage 2:  Applied without restart: generation 5   ->  GATE PASS at 50% (1238 Java-served, all matched)
Stage 3:  Applied without restart: generation 6   ->  GATE PASS (1200 of 1200)
Stage 3b: Applied without restart: generation 7   ->  Python stopped, Java serves alone
```

The one stage change that can't be made live is the fold-in, because a running app can't load code it was built without. Asking the remote-only build for a local binding is rejected, and the current routing keeps serving:

```text
$ ./demo apply addresses/stage-4.yaml
The app rejected the table (HTTP 400):
  address pkg:amorphic/pricing-calc@2.1?lang=java has a local binding, but pricing-calc-core is not in this build. Rebuild the app with -PfoldIn=true (./demo stage 4 does this).
Still live (generation 7): champion=pkg:amorphic/pricing-calc@2.1?lang=java (remote)
```

`./demo stage 4` then says why it rebuilds:

```text
Rebuilding the app with pricing-calc-core folded in (a binding change can't add code to a running app)
```

### Admin endpoints

| Endpoint | What it does |
|---|---|
| `GET /admin/address-table` | The live table, its generation, when it was applied and where it came from. |
| `POST /admin/address-table` | Apply a YAML table (`Content-Type: application/yaml`, optional `X-Table-Source` header to name it). Returns the new generation, or 400 with the reason and the routing that stayed live. |
| `POST /evidence/reset` | Clear evidence without changing the table. |

There is no authentication on these; security is out of scope for the MLP.

## Inventory

`./demo inventory` asks the app what is actually running right now. It looks at three things every time you ask, and keeps nothing between asks:

1. **The live address table**: what each role (champion, shadow, canary) is supposed to be routed to.
2. **A `/health` check of every remote address in that table** (see below).
3. **`/evidence`**: how many calls have been served and compared under the current table.

It compares what it finds with **`catalogue.yaml`**, the hand-edited list of every version and where it may run, and reports **ready** or **not ready**, with the reasons.

The catalogue lists two kinds of entry. **Capabilities** do the business work and are routed by the address table: pricing-calc 1.0, 2.0 (withdrawn) and 2.1. **Tooling** runs the system itself: the catalogue (this file), the inventory, and the governor, which is marked *planned* until phase 3. Tooling isn't in the address table yet, so the inventory reports where it actually runs instead: the inventory in-process in the app, and the catalogue as a file. Phase 4 gives the inventory and governor their own addresses and bindings in the table.

**Ready** means all three of these are true:

- every remote address in the table answers `/health`;
- the evidence belongs to the table that's live now (it didn't change mid-look);
- there are at least 100 evidence records (`amorphic.inventory.min-evidence`), enough to judge.

**Problems** are mismatches between the table and the catalogue, such as a withdrawn version still being routed. They are reported, not acted on, and they don't affect readiness. The governor (phase 3) will read both.

### What `/health` is for

Every service in the demo answers `GET /health` with `{"status":"ok"}`. It does no work; it only proves the service is running and can take requests. The address table says what *should* be running. `/health` tells the inventory what *is*, the way knocking on a door tells you more than reading the floor plan.

- **Answers with 200**: the service is **UP**, and the inventory records how long it took.
- **No answer, an error, or no answer within 2 seconds**: it is **DOWN**, with the reason.
- **A `local` binding**: nothing to knock on, because it runs inside the app. It is **IN PROCESS**, and up whenever the app is.

Two reasons for checking this way rather than asking Docker. First, it works the same whether the inventory runs inside the app or as its own service later. Second, it is safe: a `/health` call can only ask, while access to Docker would let a container control the whole machine. It also tests what matters. A container can be "running" while the service in it has crashed or is still starting, but `/health` only answers once requests can be served. `./demo stage` uses the same check to wait for new services before routing to them.

### Captured output (2026-11-09)

Stage 1, after 150 calls. Ready, and it flags 2.0 because the catalogue marks it withdrawn:

```text
Inventory: READY  (generation 2, table from stage-1.yaml)

Routing and health:
  champion pkg:amorphic/pricing-calc@1.0?lang=python  remote  UP (1 ms)
  shadow   pkg:amorphic/pricing-calc@2.0?lang=java  remote  UP (1 ms)

Evidence: 150 calls served in generation 2 (ready needs 100)

Problems (reported, not acted on):
  - pkg:amorphic/pricing-calc@2.0?lang=java is routed as shadow but is withdrawn: Rounds HALF_UP; shadow caught it disagreeing with 1.0 in stage 1. Replaced by 2.1.

Catalogue, capabilities:
  pkg:amorphic/pricing-calc@1.0?lang=python  routed as champion
  pkg:amorphic/pricing-calc@2.0?lang=java  WITHDRAWN, but still routed as shadow
  pkg:amorphic/pricing-calc@2.1?lang=java  not routed

Catalogue, tooling:
  pkg:amorphic/catalogue@1.0?lang=yaml  file /catalogue/catalogue.yaml
  pkg:amorphic/inventory@0.1?lang=java  in-process in the demo app (this inventory)
  pkg:amorphic/governor@0.1?lang=java  PLANNED (governor phase 3)
```

Stage 3b with the Java service stopped by hand (`docker compose stop java-pricing`). The table still routes to it, but `/health` gets no answer, so the inventory says why it isn't ready:

```text
Inventory: NOT READY  (generation 1, table from /config/addresses.yaml)
  champion pkg:amorphic/pricing-calc@2.1?lang=java  remote  DOWN: unreachable: connection refused or host not found
Not ready because:
  - pkg:amorphic/pricing-calc@2.1?lang=java (champion, http://java-pricing:8080) is not answering /health: unreachable: connection refused or host not found
```

About 2 seconds after the service is started again, it shows `UP` again. The app keeps DNS answers for 5 seconds, and failed lookups for 1 second, so a restarted container is seen quickly.

Stage 4, folded in. Nothing to probe:

```text
Inventory: READY  (generation 1, table from /config/addresses.yaml)

Routing and health:
  champion pkg:amorphic/pricing-calc@2.1?lang=java  local  IN PROCESS

Evidence: 150 calls served in generation 1 (ready needs 100)
```

`./demo inventory --json` (or `GET /inventory`) returns the full snapshot. If the inventory can't observe at all, it returns 503 with the reason.

## `/evidence` shape

`./demo gate` relies on this shape. `generation` is the address table generation the counts belong to.

```json
{
  "generation": 2,
  "served": [
    { "address": "pkg:amorphic/pricing-calc@1.0?lang=python", "binding": "remote", "total": 1200 }
  ],
  "comparisons": [
    {
      "served": "pkg:amorphic/pricing-calc@1.0?lang=python",
      "shadow": "pkg:amorphic/pricing-calc@2.0?lang=java",
      "total": 1200, "matches": 860, "mismatches": 340, "consecutiveMatches": 1,
      "lastMismatch": {
        "requestId": "traffic-1793973261-1199",
        "timestamp": "2026-11-06T13:54:43.135433423Z",
        "input": { "amount": "732.98", "from": "GBP", "to": "USD" },
        "servedValue": "916.22",
        "shadowValue": "916.23",
        "shadowError": null
      }
    }
  ]
}
```

A comparison matches only when the two values are identical strings, so `12.5` and `12.50` count as a mismatch. A shadow that errors counts as a mismatch.

## Layout

| Path | Contents |
|---|---|
| `demo` | The stage switcher and demo commands. |
| `compose.yaml` | One compose file for all stages. `./demo` chooses which services run. |
| `addresses/stage-*.yaml` | One address table per stage, with no comments, so `./demo diff` shows only the real change. |
| `catalogue.yaml` | Every capability and tooling version, where it may run, and whether it is withdrawn or planned. Edited by hand; read on every inventory observation. |
| `golden/vectors.json` | Shared contract test cases (rates, inputs, expected HALF_EVEN results). |
| `traffic/edge-inputs.txt` | Half-penny inputs where HALF_UP and HALF_EVEN disagree. |
| `scripts/traffic.sh`, `scripts/gate.sh` | The load generator and the evidence gate. |
| `.run/` | The active stage and its rendered address table (gitignored). |
