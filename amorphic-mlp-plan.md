# Amorphic MLP Demo — Build Plan

Handover plan for building a minimal, runnable demo of an "amorphic" system. A capability starts life as an external Python service, is re-implemented in Java, earns trust through shadow and canary traffic, and is finally folded into the calling application. Routing, addressing and governance are deliberately dumb and fixed. Each stage is a manual configuration change, not automation.

## What the demo must show

1. One call site in the app, `PricingCalc.convert(...)`, whose behaviour changes without the call site changing. The address and binding decide what actually runs.
2. A new implementation is adopted only when it has **evidenced** correctness against the incumbent.
3. A planted divergence (rounding) is caught by shadow comparison, which blocks adoption until it is fixed.
4. The final deployment shape changes: the external Java service disappears because the module now runs in-process.

## Terms (used in code and docs)

- **Address**: a PURL-style identity for a capability version, qualified by language, e.g. `pkg:amorphic/pricing-calc@1.0?lang=python`.
- **Binding**: how an address is reached, either `remote` (an HTTP URL) or `local` (an in-process bean).
- **Role**: `champion` (its answer is served), `shadow` (called and compared, answer discarded), `canary` (served for a % of calls).
- **Governor**: whatever changes the address table between stages. For the MLP that is a human editing a YAML file.
- **Evidence record**: one JSON record per call stating what served, what shadowed, and whether they matched.

## Repositories

### 1. `pricing-calc-py`: incumbent Python service
- Python 3.12, FastAPI, uvicorn. Dockerfile (arm64-friendly).
- `GET /convert?amount=<decimal string>&from=GBP&to=USD` returns `{"amount": "<decimal string>", "currency": "USD"}`.
- Uses a fixed in-code rate table (a few currency pairs, with rates chosen to produce exact `.xx5` intermediate values for some inputs).
- Uses `decimal.Decimal` and `quantize(Decimal("0.01"))`, which applies the default context rounding, **ROUND_HALF_EVEN** (banker's rounding). This is the behaviour the Java port must match.
- `GET /health`.
- Unit tests that include the `.xx5` edge cases.

### 2. `pricing-calc-java`: challenger Java implementation (multi-module)
- Java 21, Gradle Kotlin DSL (switch to Maven if preferred).
- Module `pricing-calc-core` is a plain library with no Spring and no web layer. It defines `PricingCalc` behaviour and holds the same rate table. Publish it to `mavenLocal()`.
- Module `pricing-calc-service` is a thin Spring Boot REST wrapper over `core` that exposes the same `/convert` and `/health` contract as the Python service. It has a Dockerfile.
- **Planted bug (v2.0):** the core uses `setScale(2, RoundingMode.HALF_UP)`. The fix (v2.1) changes this to `RoundingMode.HALF_EVEN`. Keep both as git tags so the demo can replay the bug.
- Contract tests run the shared golden vectors (see repo 4) against both the core and the service.

### 3. `amorphic-demo-app`: the calling application
- Java 21, Spring Boot 3.x.
- A business endpoint, e.g. `GET /quote?amount=..&from=..&to=..`, which calls `PricingCalc` through the resolver.
- **Address table** is loaded from an external YAML file, e.g. `/config/addresses.yaml`. A restart per stage is acceptable. An optional `POST /admin/reload` is nice to have.
- **Resolver / router** (client-side, inside the app):
  - Resolves the capability `pricing-calc` to a champion, an optional shadow, and an optional canary with a weight.
  - `remote` binding means an HTTP client to the URL.
  - `local` binding means the in-process `pricing-calc-core` bean.
  - The shadow is called asynchronously. Its result is compared and never returned to the caller.
- **Evidence:**
  - One JSON log line per call: `timestamp, requestId, address_served, address_shadow, served_value, shadow_value, match`.
  - An in-memory counter. `GET /evidence` returns per-address-pair totals, matches, mismatches, consecutive matches, and the last mismatch example.
- **Fold-in packaging:** `pricing-calc-core` is an **optional** dependency, included only when built with a flag (e.g. `-PfoldIn=true`). Without the flag, a `local` binding fails fast at startup with a clear error. This way stage 4 is a real rebuild that changes the deployment shape. *(Open decision: the alternative is to always include the jar and make fold-in a pure binding flip. The default here is the rebuild.)*

### 4. `amorphic-demo-env`: orchestration, stages and demo script
- `docker-compose` files, one per stage (or a base file plus overrides).
- `addresses/stage-0.yaml` … `stage-4.yaml`: the address table for each stage.
- `golden/vectors.json`: shared input/expected-output cases, including the `.xx5` rounding cases. Consumed by the contract tests in repos 1 and 2.
- `scripts/traffic.sh` (or a small Python script): steady request load with a configurable share of edge-case inputs.
- `scripts/gate.sh`: queries `/evidence` and passes only if consecutive matches ≥ N (default 1000) and there are 0 mismatches since the last reset. This script is the governor's evidence gate.
- `README.md`: a step-by-step live demo script with the expected output at each stage.

## Stages

| Stage | Champion | Shadow / Canary | Running containers | Exit criterion |
|---|---|---|---|---|
| 0 Baseline | `pricing-calc@1.0?lang=python` (remote) | none | app, py | quotes work |
| 1 Shadow | python (remote) | shadow: `pricing-calc@2.0?lang=java` (remote) | app, py, java | **gate fails**: mismatches on `.xx5` cases are visible in `/evidence` |
| 1b Fix | python (remote) | shadow: `@2.1?lang=java` (remote) | app, py, java | gate passes |
| 2 Canary | python (remote) | canary: java @2.1, weight 10% → 50% | app, py, java | gate still passes with Java serving |
| 3 Deprecate | java @2.1 (remote) | shadow: python (optional, then removed) | app, java (py scaled to 0) | continued matches; python stopped |
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
4. `pricing-calc-java` core and service at v2.0 (with the bug), then v2.1 (the fix).
5. Shadow comparison, evidence log, `/evidence` and `gate.sh`. Then stages 1 and 1b.
6. Canary weighting. Then stages 2 and 3.
7. Local binding plus the `foldIn` build flag. Then stage 4.
8. The README demo script, with expected output captured for each stage.

## Out of scope for the MLP

- Automated governor (stages are advanced by hand).
- Service mesh, gateway, service discovery and catalogue service.
- Writes and state migration (the capability is a pure function on purpose).
- Persistence of evidence beyond process memory and logs.
- Security and auth.

## Later, not now

- Persist evidence records as proper topology records.
- Have the governor read `/evidence` and propose the next address table.
- Promote the address table into a catalogue/inventory service.
- Reverse move: extract an in-process module back out to a remote service.
