# Amorphic MLP Demo — Build Plan

Handover plan for building a minimal, runnable demo of an "amorphic" system. A capability starts life as an external Python service, is re-implemented in Java, earns trust through shadow and canary traffic, and is finally folded into the calling application. Routing, addressing and governance are deliberately dumb and fixed. Each stage is a change to one address table. At first a person makes it by hand; with the governor, rules propose it from the evidence and a person approves it. Nothing advances on its own.

## What the demo must show

1. One call site in the app, `PricingCalc.convert(...)`, whose behaviour changes without the call site changing. The address and binding decide what actually runs.
2. A new implementation is adopted only when it has **evidenced** correctness against the incumbent.
3. A planted divergence (rounding) is caught by shadow comparison, which blocks adoption until it is fixed. The fix ships with a regression test built from the caught mismatch.
4. The final deployment shape changes: the external Java service disappears because the module now runs in-process.

## Terms (used in code and docs)

- **Address**: a PURL-style identity for a capability version, qualified by language, e.g. `pkg:amorphic/pricing-calc@1.0?lang=python`.
- **Binding**: how an address is reached, either `remote` (an HTTP URL) or `local` (an in-process bean). The inventory and governor are bound the same way.
- **Role**: `champion` (its answer is served), `shadow` (called and compared, answer discarded), `canary` (served for a % of calls; on those calls the champion is called as the comparison, so every call still produces evidence).
- **Governor**: whatever changes the address table between stages. In the MLP, a rule table proposes each change from the inventory and the evidence, and a human approves it (`./demo propose`, `./demo approve`). Stage 1, and anything touching the governor's or inventory's own entries, is done by hand (`./demo stage`, `./demo apply`).
- **Evidence record**: one JSON record per call stating what served, what shadowed, and whether they matched.

## Progress

| Work | State |
|---|---|
| Stages 0 to 4, with the planted bug, the fix and regression test (tags `pricing-calc-java/v2.0`, `v2.1`) and the captured demo run | Done |
| Governor phase 1: hot reload. Address tables apply live; each table is a new evidence generation | Done |
| Governor phase 2: catalogue and inventory, observed through `/health` | Done |
| Governor phase 3: the governor proposes each move from 1b to 4 and a human approves it | Done |
| Governor phase 4: the inventory and governor get their own addresses and bindings (`local` or `remote`) in the address table, with no change at their call sites | Done |
| A step-by-step guide for recreating the story yourself | Not started |

## Components

All six live in this monorepo as top-level directories. The Java projects (pricing core and service, demo app, inventory core and service, governor core and service) share one Gradle build at the repo root (`./gradlew`).

### 1. `pricing-calc-py`: incumbent Python service
- Python 3.12, FastAPI, uvicorn. Dockerfile (arm64-friendly).
- `GET /convert?amount=<decimal string>&from=GBP&to=USD` returns `{"amount": "<decimal string>", "currency": "USD"}`.
- Uses a fixed in-code rate table: GBP→USD 1.25, EUR→USD 1.05, GBP→EUR 1.19. These produce exact `.xx5` intermediate values for some inputs (e.g. 10.02 GBP → 12.525 USD).
- Uses `decimal.Decimal` and `quantize(Decimal("0.01"))`, which applies the default context rounding, **ROUND_HALF_EVEN** (banker's rounding). This is the behaviour the Java port must match.
- `GET /health`.
- Unit tests that include the `.xx5` edge cases, including ones with an even digit before the 5, where HALF_UP and HALF_EVEN disagree.

### 2. `pricing-calc-java`: challenger Java implementation (multi-module)
- Java 21, Gradle Kotlin DSL.
- Module `pricing-calc-core` is a plain library with no Spring and no web layer. It defines `PricingCalc` behaviour and holds the same rate table. The demo app uses it as a Gradle project dependency (`:pricing-calc-core`), so no `mavenLocal()` publishing is needed.
- Module `pricing-calc-service` is a thin Spring Boot REST wrapper over `core` that exposes the same `/convert` and `/health` contract as the Python service. It has a Dockerfile.
- **Planted bug (v2.0):** the core uses `setScale(2, RoundingMode.HALF_UP)`. The fix (v2.1) changes this to `RoundingMode.HALF_EVEN` and adds a **regression test** in `pricing-calc-core`. The test is built from the mismatch that shadow comparison caught (the "last mismatch example" in `/evidence`): same input, Python's answer as the expected value. It fails against v2.0 and passes against v2.1, so the bug can't come back silently. Keep both as git tags, `pricing-calc-java/v2.0` and `pricing-calc-java/v2.1` (prefixed because a monorepo has one tag namespace), so the demo can replay the bug.
- Contract tests run the shared golden vectors (see `amorphic-demo-env`) against both the core and the service.

### 3. `amorphic-demo-app`: the calling application
- Java 21, Spring Boot 3.x.
- A business endpoint, e.g. `GET /quote?amount=..&from=..&to=..`, which calls `PricingCalc` through the resolver.
- **Address table** is loaded from an external YAML file, e.g. `/config/addresses.yaml`. The app applies a new table **without a restart** through `POST /admin/address-table`. The table is resolved and checked before it is swapped in, so a table the running build can't honour (such as a `local` binding without core) is rejected and the current routing stays live. `GET /admin/address-table` shows the live table and its generation. `POST /evidence/reset` clears evidence without changing the table.
- **Resolver / router** (client-side, inside the app):
  - Resolves the capability `pricing-calc` to a champion, an optional shadow, and an optional canary with a weight.
  - `remote` binding means an HTTP client to the URL.
  - `local` binding means the in-process `pricing-calc-core` bean.
  - The shadow is called asynchronously. Its result is compared and never returned to the caller.
- **Evidence:**
  - One JSON log line per call: `timestamp, requestId, address_served, address_shadow, served_value, shadow_value, match`.
  - An in-memory counter. `GET /evidence` returns per-address-pair totals, matches, mismatches, consecutive matches, and the last mismatch example, plus served-call counts per address and binding (which is how stage 4 shows `local`). The exact shape is in `amorphic-demo-env/README.md`.
- **Fold-in packaging:** `pricing-calc-core` is an **optional** dependency, included only when built with `-PfoldIn=true` (otherwise it is `compileOnly`, so the app compiles against it but does not ship it). Without the flag, a `local` binding fails fast at startup with a clear error. This way stage 4 is a real rebuild that changes the deployment shape. *(Open decision: the alternative is to always include the jar and make fold-in a pure binding flip. The default here is the rebuild.)*

### 4. `amorphic-demo-env`: orchestration, stages and demo script
- `demo`: one command that drives every stage. `./demo stage <id>` renders that stage's address table, starts only the containers the stage needs, builds the Java image from its git tag (`pricing-calc-java/v2.0` or `v2.1`, so one checkout can replay the bug), and switches the app's routing without a restart. Only folding core in or out rebuilds the app. `./demo apply <file>` pushes any table to the running app. It also has `diff`, `quote`, `traffic`, `evidence`, `gate`, `status`, `logs` and `down`. Demo stages are run with this command, not by checking out different commits. `./demo diff <a> <b>` shows the governor's change as a few lines of YAML.
- `compose.yaml`: one compose file for all stages; `demo` selects the services.
- `addresses/stage-0.yaml`, `stage-1.yaml`, `stage-1b.yaml`, `stage-2.yaml`, `stage-3.yaml`, `stage-3b.yaml`, `stage-4.yaml`: one address table per stage in the table below. They contain no comments, so a diff between stages shows only the real change.
- `golden/vectors.json`: shared input/expected-output cases, consumed by the contract tests in `pricing-calc-py` and `pricing-calc-java`. **The golden set starts incomplete on purpose.** Its first version has `.xx5` cases, but only ones with an odd digit before the 5, where both rounding rules agree. So v2.0 passes its own build, the way a real port would, and the bug reaches stage 1. The case shadow catches is added to the golden set in v2.1, alongside the regression test, so live evidence feeds back into the contract.
- `scripts/traffic.sh`: steady request load with a configurable share of edge-case inputs, read from `traffic/edge-inputs.txt`. That list is kept separate from the golden vectors because it represents real-world traffic.
- `scripts/gate.sh`: queries `/evidence` and passes only if consecutive matches ≥ N (default 1000) and there are 0 mismatches since the last reset. Each applied address table is a new evidence generation, and that is the reset. Comparisons still running from the previous table are dropped rather than counted. This script is the governor's evidence gate.
- `README.md`: a step-by-step live demo script with the expected output at each stage, and the `/evidence` JSON shape the gate relies on.

### 5. `amorphic-inventory`: catalogue and inventory (governor phase 2)
- `amorphic-demo-env/catalogue.yaml`: static, in git, edited by hand. Lists every version of every capability (`kind: capability`, e.g. pricing-calc) and of the tooling (`kind: tooling`: catalogue, inventory, governor), where it may run (`remote`, `local`, `file`), its service URL, an optional `withdrawn` reason, and an optional `planned` note for anything not built yet. 2.0 is marked withdrawn. The inventory and governor may run `local` or `remote`. The inventory reports where unrouted tooling actually runs.
- `inventory-core`: plain-Java library (no Spring) so it can run in-process or behind its own service later. Its `Inventory` interface is remote-shaped: `observe(timeout)` returns a value, `Observed(snapshot)` or `Unavailable(reason)`, never an exception or shared state.
- Each observation reads the live address table, probes `GET /health` on every remote address in it (in parallel, within the deadline), reads `/evidence`, and checks the result against the catalogue. Nothing is stored.
- **Ready** = every remote address answers `/health`, the evidence generation matches the live table's, and there are at least N evidence records (default 100). **Problems** (a withdrawn version routed, a binding the catalogue doesn't allow, an address not in the catalogue) are reported but don't affect readiness.
- The address table routes it (phase 4): `local` (or no entry) runs it in the demo app; `remote` calls `inventory-service`, which observes the same things over HTTP (the app's `/admin/address-table` and `/evidence`). The call site, `GET /inventory` (`./demo inventory`), doesn't change. `RemoteInventory` is the client.
- Observation uses `/health` probes, not the Docker socket (agreed design): it works the same in-process or remote, and a probe can only ask.

### 6. `amorphic-governor`: the governor (governor phase 3)
- `governor-core`: plain-Java library. Its `Governor` interface is remote-shaped: `propose(timeout)` returns `Proposed`, `Waiting`, `Holding`, `NoChange` or `Unavailable`; `approve(id, timeout)` returns `Applied`, `NeedsRebuild` or `Rejected`.
- `amorphic-demo-env/governor-rules.yaml`: the rule table, tried in order, first match wins. Each rule names a situation, an optional evidence gate (consecutive matches with zero mismatches) and a proposal. From 1b the rules walk the stages: shadow → canary 10% (gate 1000) → 50% (gate 100, since only canary calls are compared) → promote (1000) → deprecate (1000) → fold-in. Any mismatch holds; a withdrawn shadow is replaced first.
- It proposes only when the inventory is ready, so after every move it waits for the inventory to rebuild. A proposal records the rule, the reason, the evidence, and the live and proposed tables in the stage-file layout.
- **A human approves every proposal.** Approval is refused if the table has changed since the proposal. A fold-in comes back as `NeedsRebuild`, and `./demo approve` rebuilds the app with it.
- It never proposes a change to the governor's or inventory's own entries (checked on proposal and again before applying). Those are human-only, via `./demo apply`.
- Stage 1 stays manual: 2.0 is withdrawn in the catalogue, so the governor never proposes it.
- The governor proposes tables; it doesn't run containers. `./demo approve` starts what a proposal routes to (waiting for `/health`) before approving, and stops what it no longer uses afterwards.
- The address table routes it (phase 4): `local` (or no entry) runs it in the demo app; `remote` calls `governor-service`, which reads the app's `/inventory` (routed in turn) and sends approved tables to `/admin/address-table`. The call sites, `/governor/...`, don't change. `RemoteGovernor` is the client, and `GovernorHttp` defines the shapes both sides use.
- Only a human moves the tooling (`./demo tooling local|remote [inventory|governor]`). Stage files hold only pricing-calc, and `./demo` adds the current tooling entries to every table it applies.

## Stages

| Stage | Champion | Shadow / Canary | Running containers | Exit criterion |
|---|---|---|---|---|
| 0 Baseline | `pricing-calc@1.0?lang=python` (remote) | none | app, py | quotes work |
| 1 Shadow | python (remote) | shadow: `pricing-calc@2.0?lang=java` (remote) | app, py, java | **gate fails**: mismatches on `.xx5` cases are visible in `/evidence` |
| 1b Fix | python (remote) | shadow: `@2.1?lang=java` (remote) | app, py, java | regression test for the caught mismatch passes in the build; gate passes |
| 2 Canary | python (remote) | canary: java @2.1, weight 10% → 50% (python compared on canary calls) | app, py, java | gate still passes with Java serving (at 10% with a lower bar, e.g. `./demo gate 100`, since only canary calls are compared) |
| 3 Promote | java @2.1 (remote) | shadow: `pricing-calc@1.0?lang=python` (remote) | app, py, java | gate passes with roles reversed (java served, python compared) |
| 3b Deprecate | java @2.1 (remote) | none | app, java (py scaled to 0) | quotes still correct; python stopped; `/evidence` shows no shadow |
| 4 Fold-in | `@2.1?lang=java` binding **local** | none | app only (rebuilt with core) | java service removed; quotes still correct; evidence shows `local` binding |

Example address table (stage 1):

```yaml
capabilities:
  pricing-calc:
    champion:
      address: pkg:amorphic/pricing-calc@1.0?lang=python
      binding: { type: remote, url: http://py-pricing:8000 }
    shadow:
      address: pkg:amorphic/pricing-calc@2.0?lang=java
      binding: { type: remote, url: http://java-pricing:8080 }
```

Stage 2 (canary; raise `weight` from 10 to 50 during the stage):

```yaml
capabilities:
  pricing-calc:
    champion:
      address: pkg:amorphic/pricing-calc@1.0?lang=python
      binding: { type: remote, url: http://py-pricing:8000 }
    canary:
      address: pkg:amorphic/pricing-calc@2.1?lang=java
      binding: { type: remote, url: http://java-pricing:8080 }
      weight: 10
```

Stage 3 (roles reversed; 3b is the same table with the `shadow` block removed):

```yaml
capabilities:
  pricing-calc:
    champion:
      address: pkg:amorphic/pricing-calc@2.1?lang=java
      binding: { type: remote, url: http://java-pricing:8080 }
    shadow:
      address: pkg:amorphic/pricing-calc@1.0?lang=python
      binding: { type: remote, url: http://py-pricing:8000 }
```

Stage 4:

```yaml
capabilities:
  pricing-calc:
    champion:
      address: pkg:amorphic/pricing-calc@2.1?lang=java
      binding: { type: local }
```

## Suggested build order

1. `amorphic-demo-env/golden/vectors.json`. Agree the contract and edge cases first.
2. `pricing-calc-py` with tests against the golden vectors.
3. `amorphic-demo-app` with a remote-only resolver, then stage 0 in compose.
4. `pricing-calc-java` core and service at v2.0 (with the bug). Tag `pricing-calc-java/v2.0`.
5. Shadow comparison, evidence log, `/evidence` and `gate.sh`. Run stage 1 and capture the mismatch from `/evidence`. Write v2.1: the HALF_EVEN fix plus a regression test from that mismatch. Tag `pricing-calc-java/v2.1`, then run stage 1b.
6. Canary weighting. Then stages 2, 3 and 3b.
7. Local binding plus the `foldIn` build flag. Then stage 4.
8. The README demo script, with expected output captured for each stage.

## Out of scope for the MLP

- An autonomous governor. The governor proposes and a human approves every move; stage 1 is still set up by hand.
- Service mesh, gateway and service discovery. (The catalogue is a static file. The inventory and governor can run as services, but they are found by a URL in the address table, not by discovery.)
- Writes and state migration (the capability is a pure function on purpose).
- Persistence of evidence beyond process memory and logs.
- Security and auth.

## Later, not now

- Persist evidence records as proper topology records.
- Promote the catalogue to a service with its own address.
- Reverse move: extract an in-process module back out to a remote service.
