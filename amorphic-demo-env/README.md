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
| `./demo stage <id> [--weight N]` | Bring up a stage, print the address-table change from the active stage, and restart the app. The restart also resets evidence. |
| `./demo diff <from> <to>` | Show what the governor changes between two stages. |
| `./demo quote <amount> [from] [to]` | One `/quote` call. |
| `./demo traffic [--requests N] [--edge-share PCT]` | Steady load (default 1200 requests). |
| `./demo evidence` | The `/evidence` summary. |
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

Going back works too: `./demo stage 3b` after stage 4 puts the remote binding back.

```bash
./demo down
```

## `/evidence` shape

`./demo gate` relies on this shape. `POST /evidence/reset` clears it without a restart.

```json
{
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
| `golden/vectors.json` | Shared contract test cases (rates, inputs, expected HALF_EVEN results). |
| `traffic/edge-inputs.txt` | Half-penny inputs where HALF_UP and HALF_EVEN disagree. |
| `scripts/traffic.sh`, `scripts/gate.sh` | The load generator and the evidence gate. |
| `.run/` | The active stage and its rendered address table (gitignored). |
