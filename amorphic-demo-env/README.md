# Running the demo

Everything runs from this directory with one command, `./demo`. A stage is a different address table plus a different set of running containers. The app's code is the same in every stage. For the narrative, see [../docs/story.md](../docs/story.md).

> **Status:** the commands are written, but the services they run (`pricing-calc-py`, `pricing-calc-java`, `amorphic-demo-app`) are not built yet. The "Expected" notes below say what each stage should show. Replace them with captured output once the demo runs.

## Prerequisites

- Docker with Compose v2, `curl`, `jq`.
- The git tags `pricing-calc-java/v2.0` and `pricing-calc-java/v2.1`. `./demo` builds each Java image from its tag, so the working tree never has to change to replay the bug.

## Commands

```bash
./demo help
```

| Command | What it does |
|---|---|
| `./demo stage <id> [--weight N]` | Bring up a stage, print the address-table change from the previous stage, and restart the app. The restart also resets evidence. |
| `./demo diff <from> <to>` | Show what the governor changes between two stages. |
| `./demo quote <amount> [from] [to]` | One `/quote` call. |
| `./demo traffic [--requests N] [--edge-share PCT]` | Steady load, with a share of half-penny inputs from `traffic/edge-inputs.txt`. |
| `./demo evidence` | The `/evidence` summary. |
| `./demo gate [N]` | The governor's gate. It passes only with ≥ N consecutive matches (default 1000) and 0 mismatches. |
| `./demo status` / `logs` / `down` | Show the active stage, follow logs, or stop everything. |

## Walkthrough

### Stage 0: Baseline

```bash
./demo stage 0
./demo quote 10.00
```

Expected: a USD quote served by Python. `./demo evidence` lists Python under `served` and has no comparisons.

### Stage 1: Shadow

```bash
./demo stage 1
./demo traffic
./demo gate
```

Expected: the stage diff adds a `shadow` block for Java v2.0. The gate **fails**, and its last mismatch is a half-penny amount where Java rounded up and Python rounded to even. Note that input, because it becomes the regression test in v2.1.

### Stage 1b: Fix

```bash
./demo diff 1 1b
./demo stage 1b
./demo traffic
./demo gate
```

Expected: the only change is `@2.0` → `@2.1`. The v2.1 build includes the regression test for the stage 1 mismatch. The gate **passes**.

### Stage 2: Canary

```bash
./demo stage 2
./demo traffic
./demo gate
./demo stage 2 --weight 50
./demo traffic
./demo gate
```

Expected: Java serves about 10% of calls, then about 50%, and Python is compared on those calls. The gate passes at both weights.

### Stage 3: Promote

```bash
./demo stage 3
./demo traffic
./demo gate
```

Expected: the diff swaps champion and shadow. Java serves every call and Python is compared. The gate passes.

### Stage 3b: Deprecate

```bash
./demo stage 3b
./demo traffic --requests 200
./demo evidence
```

Expected: the Python container is gone. Quotes are still correct and `/evidence` shows no comparisons. (`./demo gate` reports that nothing is being compared, which is expected here.)

### Stage 4: Fold-in

```bash
./demo stage 4
./demo quote 10.00
./demo evidence
./demo status
```

Expected: the app is rebuilt with `pricing-calc-core` inside it, the Java container is gone, and only `app` is running. Quotes still match. `/evidence` shows `pkg:amorphic/pricing-calc@2.1?lang=java` served with a `local` binding.

```bash
./demo down
```

## `/evidence` shape

`./demo gate` relies on this shape:

```json
{
  "served": [
    { "address": "pkg:amorphic/pricing-calc@1.0?lang=python", "binding": "remote", "total": 1200 }
  ],
  "comparisons": [
    {
      "served": "pkg:amorphic/pricing-calc@1.0?lang=python",
      "shadow": "pkg:amorphic/pricing-calc@2.0?lang=java",
      "total": 1200,
      "matches": 1003,
      "mismatches": 197,
      "consecutiveMatches": 4,
      "lastMismatch": {
        "requestId": "traffic-1762425600-1187",
        "timestamp": "2026-11-06T10:40:00Z",
        "input": { "amount": "12.50", "from": "GBP", "to": "USD" },
        "servedValue": "12.62",
        "shadowValue": "12.63"
      }
    }
  ]
}
```

The values above only illustrate the shape. They aren't real output.

## Layout

| Path | Contents |
|---|---|
| `demo` | The stage switcher and demo commands. |
| `compose.yaml` | One compose file for all stages. `./demo` chooses which services run. |
| `addresses/stage-*.yaml` | One address table per stage, with no comments, so `./demo diff` shows only the real change. |
| `golden/vectors.json` | Shared contract test cases. |
| `traffic/edge-inputs.txt` | Half-penny inputs for the traffic script. Fill these in once the rate table is fixed. |
| `scripts/traffic.sh`, `scripts/gate.sh` | The load generator and the evidence gate. |
| `.run/` | The active stage and its rendered address table (gitignored). |
