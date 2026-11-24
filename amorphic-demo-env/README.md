# Running the demo

New here? Start with the [step-by-step guide](../docs/guide.md), which walks through all of this in order. For the story in five minutes, run `./demo tour`. This page is the reference.

Everything runs from this directory with one command, `./demo`. A stage is a different address table plus a different set of running containers. The app's code is the same in every stage. For the narrative, see [../docs/story.md](../docs/story.md).

The outputs below were captured from a real run on 2026-11-06. Request IDs, timestamps and the exact counts change on every run, because the traffic is random.

## Prerequisites

- Docker with Compose v2, `curl`, `jq`.
- The git tags `pricing-calc-java/v2.0` and `pricing-calc-java/v2.1`. `./demo` builds each Java image with the core (the rounding) from its tag and the service shell from the current commit (`REBUILD=1` forces a rebuild), so the working tree never has to change to replay the bug.

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
| `./demo tour [--no-pause]` | The whole story, stage 0 to 4, in about five minutes, narrated in plain words: who answered customers at each stage, what the checks found, and a summary at the end. Pauses between stages. The full output goes to `.run/tour.log`. |
| `./demo stage <id> [--weight N]` | Bring up a stage: start the services it routes to and wait for their `/health` and for `/meta` to report the identity they were deployed with, print the address-table change, switch the app's routing **without a restart**, then stop services the new routing no longer uses. Only folding core in (stage 4) or out rebuilds the app. Every applied table starts a new evidence generation. |
| `./demo apply <file>` | Push any address table to the running app. Routing only: it doesn't start or stop containers. A table the app can't honour is rejected and the current routing stays live. |
| `./demo diff <from> <to>` | Show what the governor changes between two stages. |
| `./demo quote <amount> [from] [to]` | One `/quote` call. |
| `./demo report [account] [currency]` | Value an account (default `A-1001` in USD): load its positions, convert each, add them up (see [Outcomes and the account report](#outcomes-and-the-account-report)). |
| `./demo traffic [--requests N] [--edge-share PCT]` | Steady load (default 1200 requests). |
| `./demo evidence` | The `/evidence` summary. |
| `./demo inventory [--json]` | Observe the system now: routing, `/health` of each address, readiness and the catalogue (see [Inventory](#inventory)). |
| `./demo propose` | Ask the governor for the next address table, with the rule and evidence behind it (see [Governor](#governor)). |
| `./demo approve [id]` | A human approves a proposal: start what it needs, then apply it live (or rebuild, for the fold-in). |
| `./demo tooling <local\|remote> [inventory\|governor]` | Move the inventory and/or governor in-process or to their own services (see [Where the tooling runs](#where-the-tooling-runs)). Only a human does this. Stage changes keep the tooling where it is. |
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

The catalogue lists two kinds of entry. **Capabilities** do the business work and are routed by the address table: pricing-calc 1.0, 2.0 (withdrawn) and 2.1. **Tooling** runs the system itself: the catalogue (this file), the inventory, and the governor. The address table can route the inventory and governor too (see [Where the tooling runs](#where-the-tooling-runs)), and then the inventory reports them like any routed address. With no entry they run in-process in the app, and the inventory says so. The catalogue is a file.

**Ready** means all three of these are true:

- every remote address in the table answers `/health`;
- the evidence belongs to the table that's live now (it didn't change mid-look);
- there are at least 100 evidence records (`amorphic.inventory.min-evidence`), enough to judge.

**Problems** are mismatches between the table and the catalogue, such as a withdrawn version still being routed. They are reported, not acted on, and they don't affect readiness. The [governor](#governor) reads both: it proposes nothing until the inventory is ready, and it replaces a withdrawn version first.

### What `/health` is for

Every service in the demo answers `GET /health` with `{"status":"ok"}`. It does no work; it only proves the service is running and can take requests. The address table says what *should* be running. `/health` tells the inventory what *is*, the way knocking on a door tells you more than reading the floor plan.

- **Answers with 200**: the service is **UP**, and the inventory records how long it took.
- **No answer, an error, or no answer within 2 seconds**: it is **DOWN**, with the reason.
- **A `local` binding**: nothing to knock on, because it runs inside the app. It is **IN PROCESS**, and up whenever the app is.

Two reasons for checking this way rather than asking Docker. First, it works the same whether the inventory runs inside the app or as its own service later. Second, it is safe: a `/health` call can only ask, while access to Docker would let a container control the whole machine. It also tests what matters. A container can be "running" while the service in it has crashed or is still starting, but `/health` only answers once requests can be served. `./demo stage` uses the same check to wait for new services before routing to them.

### Captured output (2026-11-09)

Captured in phase 2, and three lines have changed since. The governor's tooling line read `PLANNED` until phase 3; it is shown below as it reads now. Since phase 4 the inventory and governor lines add "(no address-table entry, so the default)", and the catalogue is read from `/env/catalogue.yaml`.

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
  pkg:amorphic/governor@0.1?lang=java  in-process in the demo app
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

## Governor

The governor looks at what the inventory observes, applies the rule table in [`governor-rules.yaml`](governor-rules.yaml), and proposes the next address table, with the rule and the evidence behind it. **It never applies anything on its own.** A human approves each proposal.

- `./demo propose` asks for a proposal. The answer is one of four:
  - **a proposal**, with its rule, the reason, the evidence, which stage it is, and the diff;
  - **waiting**: the inventory isn't ready, or the rule's evidence gate isn't met yet;
  - **holding**: a rule says nothing should move, for example because versions disagree;
  - **no proposal**: no rule applies.
- `./demo approve [id]` approves it (default: the last proposal). It starts or updates the containers the proposed table routes to and waits for their `/health`. Then the governor checks that the table hasn't changed since the proposal, and the table is applied live. Services the new table no longer uses are then stopped. A fold-in proposal can't be applied live, so the governor says it needs a rebuild and `./demo approve` rebuilds the app with it (as `./demo stage 4`).

The rules are tried in order and the first match wins. Each rule names a *situation*, an optional *gate* (consecutive matches with zero mismatches) and what to *propose*:

| Rule | When | Gate | Proposes |
|---|---|---|---|
| `replace-withdrawn-shadow` | the shadow is withdrawn in the catalogue | | shadow the newest version |
| `hold-on-mismatch` | any comparison disagrees | | hold |
| `start-shadow` | a champion alone, and a newer version is catalogued | | shadow the newest version |
| `shadow-to-canary` | the shadow is newer than the champion | 1000 | canary at 10% |
| `raise-canary` | a canary below 50% | 100 | canary at 50% |
| `promote` | a canary at 50% or more | 1000 | canary becomes champion, old champion becomes shadow |
| `deprecate` | the shadow is older than the champion | 1000 | stop shadowing |
| `fold-in` | a remote champion alone, nothing newer, catalogue allows local | | bind it in-process (rebuild) |

The bar for raising the canary is 100, not 1000, because at 10% only about one call in ten is compared.

Two things only a human does:

- **Stage 1.** 2.0 is withdrawn in the catalogue, so the governor never proposes it. The planted bug is shown by hand with `./demo stage 1`, and the governor takes over from there.
- **The governor's and inventory's own entries.** The governor may never propose a change to either; every proposal is checked, and checked again before it is applied. Only a human can change them, with `./demo tooling` or `./demo apply`.

### Captured run (2026-11-10)

Stage 1 is set up by hand. From then on there is only `./demo traffic`, `./demo propose` and `./demo approve`.

Before there is any traffic, and again straight after every approval, the governor waits for the inventory to rebuild its picture:

```text
Waiting: the governor won't propose yet
  - only 0 evidence records for generation 1 (need 100)
```

In stage 1, shadow is catching the bug (`./demo gate`: 120 mismatches in 400 calls). The withdrawn-shadow rule comes before the hold rule, so the governor proposes the replacement:

```text
Proposal g1-replace-withdrawn-shadow
  rule:      replace-withdrawn-shadow
  why:       A human withdrew the shadowed version. Shadow the newest version instead.
  this is:   stage 1b

Proposed change:
--- live (generation 1)
+++ proposal g1-replace-withdrawn-shadow
@@ -4,5 +4,5 @@
       address: pkg:amorphic/pricing-calc@1.0?lang=python
       binding: { type: remote, url: http://py-pricing:8000 }
     shadow:
-      address: pkg:amorphic/pricing-calc@2.0?lang=java
+      address: pkg:amorphic/pricing-calc@2.1?lang=java
       binding: { type: remote, url: http://java-pricing:8080 }

Nothing changes until a human approves it:  ./demo approve g1-replace-withdrawn-shadow
```

```text
$ ./demo approve
Starting services (the first build of each image takes a few minutes)
Approving g1-replace-withdrawn-shadow
Applied without restart: generation 2
Now running: app py-pricing java-pricing  (stage 1b)
```

The approval brought Java up as `amorphic/pricing-calc-java:v2.1` before switching the routing. Each later step went the same way:

| From | Proposal | Evidence behind it | Approved to |
|---|---|---|---|
| 1b | `g2-shadow-to-canary` | 1200 consecutive matches (needs 1000) | stage 2, canary 10% |
| 2 at 10% | `g3-raise-canary` | 116 consecutive matches (needs 100) | stage 2, canary 50% |
| 2 at 50% | `g4-promote` | 1233 consecutive matches (needs 1000) | stage 3 |
| 3 | `g5-deprecate` | 1200 consecutive matches (needs 1000) | stage 3b; Python stopped |
| 3b | `g2-fold-in` | champion alone, nothing newer, may run local | stage 4, app rebuilt |

(The fold-in proposal's generation restarts at 2 because the check below was run first.)

**A stale approval is refused.** A fold-in was proposed at generation 1, a table was then applied by hand (generation 2), and the old proposal was approved:

```text
Not applied: the address table has changed since this proposal (generation 1 -> 2). Ask for a new one.
```

The fold-in, approved fresh:

```text
Approved g2-fold-in. The governor says it needs a rebuild:
  This moves pricing-calc in-process, and the running app wasn't built with it. Rebuild the app with this table (./demo approve does this via ./demo stage 4).
Rebuilding the app with pricing-calc-core folded in (a binding change can't add code to a running app)
Stage 4 is up. Running: app
```

And then:

```text
No proposal
  no rule applies (pricing-calc: champion pkg:amorphic/pricing-calc@2.1?lang=java local: folded in, nothing further to propose)
```

### Governor endpoints

| Endpoint | What it does |
|---|---|
| `POST /governor/proposals` | Look at the system now and propose. Returns `status`: `proposed`, `waiting`, `holding` or `no-change` (503 `unavailable` if it can't look). |
| `GET /governor/proposals/{id}` | A pending proposal, or 404. |
| `POST /governor/proposals/{id}/approve` | Apply it live (`applied`, with the new generation), or 409 with `needs-rebuild` (and how) or `rejected` (and why). |

## Where the tooling runs

The inventory and governor have addresses in the address table, like pricing-calc. Each has one entry, a champion, bound `local` (in-process in the app) or `remote` (its own service). With no entry, it runs in-process. The call sites don't change: `./demo inventory` and `./demo propose` still call the app's `/inventory` and `/governor/...`, and the app sends each call where the live table says, as it does for `/quote`.

```bash
./demo tooling remote              # both to their own services (inventory :8100, governor :8110)
./demo tooling local governor      # just the governor back in-process
./demo tooling local               # both back in-process
```

`./demo tooling` starts the services the new table needs, waits for their `/health`, applies the table live, and stops the ones it no longer uses. Stage files hold only pricing-calc. `./demo` adds the current tooling entries to every table it applies, so `./demo stage` and `./demo approve` never move the tooling, and neither can the governor. Moving the tooling is a new table, so evidence starts again and the governor waits for fresh evidence.

As services:

- **The inventory service** observes exactly what the in-process one does. It reads the app's `/admin/address-table` and `/evidence` over HTTP, probes `/health`, and reads the same catalogue. It stores nothing.
- **The governor service** asks the app's `/inventory` (which the app routes to wherever the inventory is) and sends approved tables to the app's `/admin/address-table`. Its pending proposals live in the service, so moving the governor drops them; they would be stale anyway, because moving it is a new table.
- Both answer `/health`, so the inventory checks them like any remote address. A tooling service that is down holds readiness back, and the error says which hop failed.
- Every call hands on its deadline, so a chain of remote calls finishes within the caller's time.

### Captured run (2026-11-10)

Stage 1b, both moved out:

```text
$ ./demo tooling remote
Address table change (1b -> tooling: inventory remote, governor remote):
@@ -6,3 +6,11 @@
     shadow:
       address: pkg:amorphic/pricing-calc@2.1?lang=java
       binding: { type: remote, url: http://java-pricing:8080 }
+  inventory:
+    champion:
+      address: pkg:amorphic/inventory@0.1?lang=java
+      binding: { type: remote, url: http://inventory:8080 }
+  governor:
+    champion:
+      address: pkg:amorphic/governor@0.1?lang=java
+      binding: { type: remote, url: http://governor:8080 }

Starting tooling services: inventory governor
Applying the table (tooling: inventory remote, governor remote)
Applied without restart: generation 2
```

After 1,200 calls the inventory service reports the tooling as routed and up:

```text
Inventory: READY  (generation 2, table from tooling: inventory remote, governor remote)

Routing and health:
  champion pkg:amorphic/pricing-calc@1.0?lang=python  remote  UP (3 ms)
  shadow   pkg:amorphic/pricing-calc@2.1?lang=java  remote  UP (3 ms)
  champion pkg:amorphic/inventory@0.1?lang=java  remote  UP (4 ms)
  champion pkg:amorphic/governor@0.1?lang=java  remote  UP (5 ms)
```

The governor service proposed `g2-shadow-to-canary` (stage 2), with the tooling entries unchanged, and `./demo approve` applied it through the service (generation 3).

With the inventory service stopped, nothing pretends. Each hop is named:

```text
$ ./demo inventory
Inventory unavailable: inventory at http://inventory:8080: unreachable: connection refused or host not found
$ ./demo propose
The governor couldn't look at the system: inventory unavailable: inventory at http://app:8080: inventory at http://inventory:8080: unreachable: connection refused or host not found
```

Then `./demo tooling local governor`, `./demo stage 3b`, and an approved fold-in. The rebuilt app kept the inventory remote and the governor in-process:

```text
Inventory: READY  (generation 1, table from /config/addresses.yaml)

Routing and health:
  champion pkg:amorphic/pricing-calc@2.1?lang=java  local  IN PROCESS
  champion pkg:amorphic/inventory@0.1?lang=java  remote  UP (6 ms)
  champion pkg:amorphic/governor@0.1?lang=java  local  IN PROCESS
```

`./demo quote 732.98` served 916.22 with the `local` binding, and `./demo tooling local` stopped the last tooling container.

## Outcomes and the account report

The app never names what prices its answers. Each endpoint asks for an **outcome** (spec 04): `/quote` asks for `pkg:outcome/quote@1.0`, and `/accounts/{account}/value` asks for `pkg:outcome/account-value@1.0`. The composer finds the outcome's recipe in `catalogue.yaml`, chooses a route for every step once, and runs the steps in order:

- **quote:** one step, `convert` (pricing-calc).
- **account-value:** three steps. `positions` loads what the account holds (from `accounts.yaml`), `convert` runs once for each position, and `total` adds them up (`sum`). A position already in the report's currency passes through with no call.

pricing-calc is routed by the stage's address table as before, so shadow, canary and fold-in all apply to every conversion. At the canary stage a report's conversions all go the same way: the choice is made once per plan. Positions and sum always run inside the app.

Every call in a plan is caused by the outcome request, and the whole plan runs on one topology, named after the outcome. When a step is shadowed, the composer also finishes the plan on the shadow's answers. That **shadow result** is never sent back; it goes to `/evidence` under `outcomes`.

- `./demo report [account] [currency]`: value an account (default `A-1001` in USD).

### Captured run (2026-11-24)

Stage 1. The customer gets Python's conversions; the cash needs none:

```console
$ ./demo report
{
  "account": "A-1001",
  "currency": "USD",
  "total": "2216.22",
  "positions": [
    { "name": "Pounds", "amount": "732.98", "currency": "GBP", "value": "916.22",
      "convertedBy": "pkg:amorphic/pricing-calc@1.0?lang=python" },
    { "name": "Euros", "amount": "1000.00", "currency": "EUR", "value": "1050.00",
      "convertedBy": "pkg:amorphic/pricing-calc@1.0?lang=python" },
    { "name": "Cash", "amount": "250.00", "currency": "USD", "value": "250.00",
      "convertedBy": null }
  ],
  "outcome": "pkg:outcome/account-value@1.0",
  "requestId": "8110dbaf-a2f9-42ce-9bab-027f93a92884"
}
```

On Java 2.0's conversions the account would have been worth a cent more:

```console
$ curl -s localhost:8080/evidence | jq -c '.outcomes[] | select(.outcome == "pkg:outcome/account-value@1.0") | {outcome, mismatches, served: .lastMismatch.served.amount, shadow: .lastMismatch.shadow.amount}'
{"outcome":"pkg:outcome/account-value@1.0","mismatches":1,"served":"2216.22","shadow":"2216.23"}
```

The report's topology names every step, and who answered in each role:

```console
$ T=$(curl -s localhost:8080/evidence | jq -r '.outcomes[] | select(.outcome == "pkg:outcome/account-value@1.0") | .lastMismatch.topology')
$ curl -s --get localhost:8080/topology --data-urlencode "address=$T" | jq -c '.roles[] | {step, role, instance: .instance.identity}'
{"step":"positions","role":"champion","instance":"demo-app-remote/positions"}
{"step":"convert","role":"champion","instance":"py-pricing-v1.0"}
{"step":"convert","role":"shadow","instance":"java-pricing-v2.0"}
{"step":"total","role":"champion","instance":"demo-app-remote/sum"}
```

Its calls, with envelope ids cut short. The tour's stage 1 report and this one ran on the same topology. Each wrote six records: the outcome request (caused by the caller's request id), then positions, two conversions, the total, and the total again on the shadow's answers, each caused by the outcome request:

```console
$ curl -s --get localhost:8080/topology/calls --data-urlencode "address=$T" | jq -c '.[] | {envelope: .envelope[0:14], causedBy: [.causedBy[] | .[0:14]]}'
{"envelope":"e-899bd577-3f6","causedBy":["tour-stage-1"]}
{"envelope":"e-47683cc7-238","causedBy":["e-899bd577-3f6"]}
{"envelope":"e-43b1aad1-025","causedBy":["e-899bd577-3f6"]}
{"envelope":"e-5e0e113c-5f8","causedBy":["e-899bd577-3f6"]}
{"envelope":"e-70f6e816-436","causedBy":["e-899bd577-3f6"]}
{"envelope":"e-735019d1-a29","causedBy":["e-899bd577-3f6"]}
{"envelope":"e-9e9de2a4-d8b","causedBy":["8110dbaf-a2f9-"]}
{"envelope":"e-112e1a76-13a","causedBy":["e-9e9de2a4-d8b"]}
{"envelope":"e-feee37d4-1f6","causedBy":["e-9e9de2a4-d8b"]}
{"envelope":"e-4a7ec027-0c2","causedBy":["e-9e9de2a4-d8b"]}
{"envelope":"e-3eb9453d-ba1","causedBy":["e-9e9de2a4-d8b"]}
{"envelope":"e-ff4d00eb-f50","causedBy":["e-9e9de2a4-d8b"]}
```

At stage 4 the same report says 2216.22, with every conversion made inside the app.

## Topology and the handshake

Every call records **exactly what it ran on**: its topology (spec 03). A topology lists each step's roles with the instance that actually answered, plus the caller (the app). Its identity is a hash of that content, and it has an address like everything else, named after the outcome it served: `pkg:topology/quote?hash=…` or `pkg:topology/account-value?hash=…` (see [Outcomes and the account report](#outcomes-and-the-account-report)). Calls that ran on the same shape share one topology, whatever the table generation. The records are appended to `.run/records/topology.jsonl`, so they survive an app restart; `./demo down` removes them.

**Instances are declared first**, in `instances.yaml`: each one's identity (like a car's VIN), what it realises, and its handle. `./demo` passes each service its identity, and each service reports itself on `GET /meta`, taking what it realises from its own code. When a table is applied, the app asks every remote role who it is, and refuses the table if the answer isn't what the table routes to. Java's replies also say who answered, on every call, so a container swapped between calls shows up as a new topology.

- `GET /topology?address=<topology address>`: one topology. With `pkg:topology/quote`, every topology recorded for quotes.
- `GET /topology/calls?address=<topology address>`: the calls that ran on it (envelope id, what caused it, generation, time).

### Captured run (2026-11-24)

This run was captured in slice 3, before the composer, when topologies were named after the capability (`pkg:topology/pricing-calc`) and roles had no step.

Stage 1. The mismatch's topology names who was compared, by identity, not by URL:

```console
$ T=$(curl -s localhost:8080/evidence | jq -r '.comparisons[0].topology')
$ curl -s --get localhost:8080/topology --data-urlencode "address=$T" | jq -c '.caller.identity, (.roles[] | {role, instance: .instance.identity})'
"demo-app-remote"
{"role":"champion","instance":"py-pricing-v1.0"}
{"role":"shadow","instance":"java-pricing-v2.0"}
```

Stage 1b, with a Java 2.0 container started by hand behind the URL the table gives to 2.1 (`JAVA_VERSION=v2.0 docker compose up -d java-pricing`). The next calls run on a new topology that names 2.0 as the shadow, and applying the table again is refused:

```console
$ curl -s localhost:8080/evidence | jq -c '.comparisons[] | {topology: .topology[-12:], total, mismatches}'
{"topology":"e19deaa10817","total":3,"mismatches":0}
{"topology":"bbddec39d1b0","total":3,"mismatches":3}
$ ./demo apply addresses/stage-1b.yaml
The app rejected the table (HTTP 400):
  routed to pkg:amorphic/pricing-calc@2.1?lang=java, but http://java-pricing:8080 is java-pricing-v2.0, which realises pkg:amorphic/pricing-calc@2.0?lang=java
Still live (generation 1): champion=pkg:amorphic/pricing-calc@1.0?lang=python (remote), shadow=pkg:amorphic/pricing-calc@2.1?lang=java (remote)
```

After Java 2.1 was back and the app restarted, both topologies still resolved. The 2.1 one had 1203 calls: 1200 from the tour's stage 1b (generation 3) and 3 from this run (generation 1), because the generation is in each call record, not in the topology.

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
        "shadowError": null,
        "topology": "pkg:topology/quote?hash=db96586a…"
      },
      "instances": { "served": "py-pricing-v1.0", "shadow": "java-pricing-v2.0" },
      "topology": "pkg:topology/quote?hash=db96586a…"
    }
  ],
  "outcomes": [
    {
      "outcome": "pkg:outcome/account-value@1.0",
      "total": 1, "matches": 0, "mismatches": 1,
      "lastMismatch": {
        "requestId": "8110dbaf-a2f9-42ce-9bab-027f93a92884",
        "timestamp": "2026-11-24T17:21:27.073373295Z",
        "served": { "amount": "2216.22", "currency": "USD" },
        "shadow": { "amount": "2216.23", "currency": "USD" },
        "shadowError": null,
        "topology": "pkg:topology/account-value?hash=843180f3…"
      }
    }
  ]
}
```

Each comparison is counted per pair of instances (`instances`): the exact instances compared, not just their addresses, so a service swapped behind the same URL is counted apart. A quote and an account report compared on the same two instances count together, because the evidence is about the instances, whichever plan they served. `topology` is the latest topology the pair was compared on, which `/topology?address=` resolves.

`outcomes` counts each outcome whose plan had a comparison: the result the caller got (`served`) against what it would have been on the compared answers (`shadow`). The gate reads only `comparisons`.

A comparison matches only when the two values are identical strings, so `12.5` and `12.50` count as a mismatch. A shadow that errors counts as a mismatch.

## Layout

| Path | Contents |
|---|---|
| `demo` | The stage switcher and demo commands. |
| `compose.yaml` | One compose file for all stages, including the inventory and governor services. `./demo` chooses which services run. |
| `addresses/stage-*.yaml` | One address table per stage, with no comments, so `./demo diff` shows only the real change. |
| `catalogue.yaml` | Every capability and tooling version, where it may run, and whether it is withdrawn or planned; the outcomes callers can ask for, each with its recipe. Edited by hand; read on every inventory observation and every plan. |
| `accounts.yaml` | The accounts the positions capability reads: the sample account `A-1001`. |
| `instances.yaml` | Every instance the system declares before it runs: its identity, what it realises, and its handle. Edited by hand; read whenever a table is applied. |
| `governor-rules.yaml` | The governor's rule table, tried in order. Edited by hand; read on every proposal. |
| `golden/vectors.json` | Shared contract test cases (rates, inputs, expected HALF_EVEN results). |
| `traffic/edge-inputs.txt` | Half-penny inputs where HALF_UP and HALF_EVEN disagree. |
| `schemas/` | The schemas the envelopes' payloads follow, listed in the catalogue. |
| `scripts/traffic.sh`, `scripts/gate.sh`, `scripts/tour.sh` | The load generator, the evidence gate and the narrated tour. |
| `.run/` | The active stage, its rendered address table, the tooling entries `./demo` adds to every table (`tooling.yaml`), and the topology log (`records/topology.jsonl`). Gitignored; `./demo down` removes it. |
