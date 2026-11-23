# Walking Skeleton — Plan

**Status: draft for discussion.** Decisions so far are under "Decided". Everything else is open until its "For discussion" item is settled. 16 November 2026.

## Intention

The MLP proved the story. A call site keeps working while what runs behind it changes shape, and it changes only on evidence. To get there quickly, the code was shaped around one capability, pricing-calc.

The next step is the framework itself. Every concept from the Amorphic Systems articles becomes a real, general component, in its thinnest form, wired end to end. The MLP stays as the demo: the first worked example that runs on the framework.

The framework is built **in this repo, beside the demo**, while its boundaries are still moving. Once they settle, the general parts move out into their own repository and the demo stays here, using them.

## What "walking skeleton" means here

- **Every part exists and is connected.** Each concept is as thin as it can be while still being real. Breadth comes before depth.
- **The skeleton knows nothing about pricing-calc.** Pricing-calc is a capability that runs on it.
- **The demo keeps working at every step.** `./demo tour` passing (stages 0 to 4, the same story) is the acceptance test for every slice.
- **Behaviour stays the same; the structure underneath changes.** If a slice changes what the tour shows, that change is deliberate and agreed beforehand.

## Layout and rules

- A new top-level directory, `amorphic-systems/`, named after the repository it will move to, so the move needs no renames. One Gradle project per concept, in packages `dev.amorphic.<concept>`.
- **Dependencies go one way.** The demo depends on the skeleton, never the reverse. A build check enforces this.
- **Where things belong:** if a file under `amorphic-systems/` mentions pricing, currency, a stage or the tour, it is in the wrong place.
- **Spec first, then build.** Each slice starts with a short pass-1 spec in The Knowledge (`realising/specs/`). That keeps the two-pass approach (general spec, then JVM build) without holding up the build.

## Where we start from

Some of the framework already exists in the MLP.

| Part | Today | In the skeleton |
|---|---|---|
| `inventory-core` | Plain Java, remote-shaped (`observe(timeout)`), nothing about pricing in its design | Moves in largely as it is |
| `governor-core` | Plain Java, remote-shaped (`propose`, `approve`) | The core moves in. The rule table stays in the demo |
| Resolver and `Bindings` (in the app) | The idea is general, but the code is tied to pricing-calc | Generalised in slice 2 |
| `catalogue.yaml` | The format is general, the entries are demo | The format and loader move in. The entries stay |
| `pricing-calc-*`, stage files, `governor-rules.yaml`, golden vectors, `./demo`, the tour | Demo | Stay in the demo |

## Concepts in the skeleton

| Concept (article) | MLP today | Skeleton: thinnest real form |
|---|---|---|
| Addressing (Consistent Addressing, spec 01) | PURL-style strings with `lang=`, compared as text | An `Address` type built to spec 01: kind, name, version or range, qualifiers; narrowing and broadening; best match by a stated strategy |
| Catalogue and inventory (Catalogue) | A hand-edited file, plus an inventory that probes `/health` | Two aspects of one addressed asset, with a resolver for each kind. The catalogue gets its own address and binding, as the inventory and governor already have |
| Envelope (Envelope) | None: plain `/convert?amount=` JSON | Primary purpose, a payload aspect and the required causation-links aspect. Nothing more |
| Uniform access (Uniform Access) | `local` and `remote` bindings, for pricing-calc only | One `invoke(address, envelope) → envelope` for every binding. An adapter binding wraps a plain REST service, so the Python incumbent is unchanged |
| Composition (Directory / Composer / Concierge) | `QuoteController` calls `PricingCalc` directly | `/quote` states an outcome, and a composer resolves a one-step plan |
| Topology (Topologies) | Evidence lines record what served and what shadowed | A fixed record for each call: addresses, versions, bindings and table generation. The envelope's causation aspect links to it |
| Evidence (The Governor) | Strong already: shadow, canary, the gate | Keyed by topology id instead of address pair. Otherwise unchanged |
| Governor (The Governor) | Rule table; a human approves every move | Reads the new shapes. Behaviour unchanged |

## Out of scope for the skeleton

- Composition beyond one step, and attaching new demands to running work (DAG Amortisation).
- Planning by cost and value (MRP II).
- Expectations (Outcome to Expectation).
- Writes and state.
- Qualities as qualifiers (level 201 onward).
- Security, auth and service discovery, as in the MLP.

Two items from the MLP's "Later, not now" list come forward: the catalogue as a service with its own address, and evidence recorded as topology records.

## Slices

Each slice ends with the tour passing and the progress table below updated.

| # | Slice | Done when |
|---|---|---|
| 0 | **Groundwork.** Tag `mlp/102`, add a README line pointing Governor readers to that tag, create `amorphic-systems/`, move the inventory and governor (core and service) into it unchanged, and add the dependency check | The tag exists; the tour passes after the move; the build fails if a framework project depends on a demo project |
| 1 | **Addressing.** An `Address` type to spec 01, and a catalogue resolver. It must name every rung of the ladder (see the note below) | The demo resolves through `Address`; no string comparison of addresses remains |
| 2 | **Envelope and uniform access.** The envelope, `invoke`, and local, remote and adapter bindings | Every pricing-calc call goes through `invoke`; the Python service is unchanged |
| 3 | **Topology.** One record per call, linked from the envelope; evidence keyed on it | `/evidence` can show the topology behind any mismatch |
| 4 | **Composer.** An outcome in, a one-step plan out | `QuoteController` no longer names `PricingCalc` |
| 5 | **Catalogue as a service.** Addressed, bindable `local` or `remote`, like the inventory and governor | All three tooling parts fold in and out the same way |
| 6 | **Settle and tidy.** The inventory and governor work on the new shapes; `guide.md`, the tour narration and the MLP plan updated | A newcomer can follow the guide from a fresh clone |

### Note: addressing the capability and the implementation

Today, `pkg:amorphic/pricing-calc@2.1?lang=java` mixes the capability (`pricing-calc`) with an implementation (`@2.1?lang=java`). Where it runs, the `local` or `remote` binding, sits beside the address in the table and has no address of its own.

Spec 01's narrowing ladder covers every rung, and slice 1 must be able to name each one:

- `pricing-calc`: the capability (logical)
- `@2.1`: the version
- `?lang=java`: the implementation
- the running service, or the in-process bean: the manifestation (this one, here)

Slice 3 depends on this. A topology record must name exactly what ran, which is the manifestation. Without an address for it, the record falls back to the binding URL, which is a location, not an identity.

**Deferred to level 301:** the capability and runtime duality as behaviour, meaning evidence per binding, and two runtimes behind one address compared live. The skeleton only has to name both levels.

## Progress

| Slice | State |
|---|---|
| 0 Groundwork | Done, 16 November 2026. Tag `mlp/102` pushed |
| 1 Addressing | Done, 17 November 2026. `:addressing` built to spec 01; the inventory, governor and app resolve through it |
| 2 Envelope and uniform access | Done, 23 November 2026. `:envelope` and `:access` built to spec 02; every pricing-calc call goes through `invoke`; Java speaks the envelope, Python is reached through an adapter that records its timings |
| 3 to 6 | Not started |

## Slice 0 brief

A handover for doing slice 0 in Claude Code. It is a move, not a change: no code changes, no renamed Gradle projects or packages.

**Goal:** the current demo is tagged, the inventory and governor (core and service) live in `amorphic-systems/`, and the build enforces that framework projects never depend on demo projects. The tour behaves exactly as before.

### Steps

1. **Tag.** Tag the current `main` as `mlp/102`, annotated: "MLP at demo level 102, as described in The Governor". Push the tag only when Stephen says so.
2. **README pointer.** Add one line near the top of `README.md`: the code as described in The Governor article is at tag `mlp/102`; `main` is moving on to the walking skeleton.
3. **Move the four projects** with `git mv`, keeping their directory names flat for now (module names are still open, see "For discussion"):
   - `amorphic-inventory/inventory-core` → `amorphic-systems/inventory-core`
   - `amorphic-inventory/inventory-service` → `amorphic-systems/inventory-service`
   - `amorphic-governor/governor-core` → `amorphic-systems/governor-core`
   - `amorphic-governor/governor-service` → `amorphic-systems/governor-service`

   Then remove the empty `amorphic-inventory/` and `amorphic-governor/` directories.
4. **Update every place that names the old paths:**
   - `settings.gradle.kts`: the four `projectDir` lines.
   - `amorphic-demo-env/compose.yaml`: the `dockerfile:` lines for `inventory` and `governor`.
   - `amorphic-systems/inventory-service/Dockerfile` and `amorphic-systems/governor-service/Dockerfile`: the `cp .../build/libs/...` line in each.
   - `README.md`: the four rows of the component table.
   - `CLAUDE.md`: the paths in the Inventory and Governor bullets.
   - `docs/amorphic-mlp-plan.md`: the paths in the headings of sections 5 and 6 only. The rest of that plan is the MLP's history and stays as written.
5. **Dependency check.** Add a task to the root build, wired into `check`. A framework project is any project whose directory is under `amorphic-systems/`. The task fails if a framework project has a project dependency, in any configuration, on a project outside `amorphic-systems/`. Prove it works once: add a dependency from `:inventory-core` on `:pricing-calc-core`, see the build fail with a clear message, then remove it.
6. **Check nothing still points at the old paths.** `grep -rn "amorphic-inventory\|amorphic-governor"` finds nothing outside `.git/` and build output.
7. **Verify:**
   - `./gradlew build`, and `./gradlew :amorphic-demo-app:bootJar -PfoldIn=true` (the two CI steps).
   - `docker compose -f amorphic-demo-env/compose.yaml config --quiet`.
   - Both service images build from the new paths, for example by moving the tooling remote (`./demo tooling remote`) and back (`./demo tooling local`).
   - `./demo tour` runs stages 0 to 4 with the same story and output as before.
8. **Close the slice.** Mark slice 0 done in the progress table, with the date. In IntelliJ, reload the Gradle project so the modules pick up the new paths.

### Not in slice 0

- Changing code, Gradle project names or packages.
- The text check for demo words.
- Touching the catalogue, the rules, the stage files or the tour.

## Slice 1 brief

A handover for doing slice 1 in Claude Code. The spec is "01 - Consistent Addressing" in The Knowledge (`realising/specs/`), settled on 16 November 2026 apart from section 9, which slice 1 doesn't need. Read its Decisions list first.

**Goal:** one general `Address` type, and a resolver, in a new framework project. Every place that compares, parses or orders addresses by hand goes through them instead. The tour behaves exactly as before: every address on disk and on screen is unchanged.

### What goes in `amorphic-systems/addressing`

A new Gradle project, `:addressing`, at `amorphic-systems/addressing`, package `dev.amorphic.addressing`. Plain Java, with no dependencies. It is a framework project, so it must not mention pricing: its tests use neutral examples, such as `pkg:amorphic/greeter@1.0.1?lang=java` from the notes.

1. **`Address`**: kind, name, an optional version or range, and qualifiers.
   - Parses the PURL form `pkg:<kind>/<name>[@<version or range>][?<key>=<value>&...]`. In the demo the kind is always `amorphic`.
   - Prints the canonical form, with qualifiers sorted by key. Equality is by value.
   - Says which rung it is on: capability, range, version or implementation (an implementation has at least one qualifier).
   - `narrows(other)` and `broaden()`. Broadening drops one dimension: qualifiers, then version, then range.
   - Clear errors for bad input, in the style of today's `'x' is not a valid address (expected ...)`.
2. **`Version` and `Range`**: a version is a scheme value with one scheme, dotted numbers, which is also the default. Versions compare part by part as numbers, with a missing part read as 0. A range is a wildcard: `2.*` or `*`.
3. **`Instance`**: names a manifestation, with an identity, the `Address` it realises, and an optional host `Instance`. It is the type only, with tests for the three cases in the spec: an artefact identified by its hash, a deployed service identified by a deployment id, and an in-process part identified by its host plus the part's name. It has no text form yet. Slice 3 decides that, when topology records need it.
4. **`Resolver`**: chooses one candidate for a requested address.
   - The highest version that satisfies the request, with qualifiers matching exactly.
   - A floating request skips withdrawn and planned candidates. A pinned request still resolves to a withdrawn candidate, with a warning in the result.
   - No match returns an error naming what was asked for and what is available. It never falls back.
   - Candidates are anything with an `Address` and a withdrawn or planned state, so the resolver doesn't depend on the catalogue.
   - It takes the caller's preference as a parameter, with one preference for now (highest version).

### Where it replaces string handling

5. **Root build:** add `:addressing` to `settings.gradle.kts`. `:inventory-core`, `:governor-core` and `:amorphic-demo-app` depend on it.
6. **`inventory-core`:**
   - `Catalogue` parses each version's address when it loads, so a bad address fails the load with its file and the reason.
   - `Catalogue.find` matches by `Address`, not text, and gains a `resolve(request)` that uses the `Resolver`.
   - `ObservedInventory` matches routed roles to catalogue entries by `Address`.
   - `Tooling`'s three addresses become `Address` values.
7. **`governor-core`:**
   - `RuleGovernor.newest()` asks the catalogue to resolve `pkg:amorphic/<capability>@*`. The "can run remote" check stays in the governor: it is about where a version runs, not what it is.
   - Every `Tables.compareVersions` becomes a `Version` comparison, and `compareVersions` is deleted.
8. **The demo app:**
   - Delete `dev.amorphic.demo.pricing.Address` and use `dev.amorphic.addressing.Address`.
   - `Bindings` reads the `lang` qualifier and the version from it.
   - The app's tests gain a check that every address in the stage files and the catalogue reads and prints back exactly. That check lives in the demo, not the framework.
9. **On the wire:** the HTTP and JSON shapes (`RoutingView`, `EvidenceView`, `InventorySnapshot`, the evidence log) keep addresses as canonical text. They are parsed at the edges, and nothing compares the text.

### Verify

- `./gradlew build` and `./gradlew :amorphic-demo-app:bootJar -PfoldIn=true`. `checkFrameworkDependencies` passes with `:addressing` included.
- `git grep -n "compareVersions\|address().equals\|indexOf('@')"` finds nothing in main code.
- `./demo tour` runs stages 0 to 4 with the same story, and the governor sequence in the guide's Part 2 proposes the same six steps.

### Not in slice 1

- Resolving a kind alias to its specification's address (spec 4.1). The kind is held as text for now.
- A text form for `Instance`, and anything that records what ran: that is slice 3.
- Other version schemes, comparison ranges and other caller preferences (spec section 10).
- Changing any address on disk, the catalogue format, or the evidence keys.

## Slice 2 brief

A handover for doing slice 2 in Claude Code. Read the slice 2 decisions under "Decided" first.

**Goal:** every pricing-calc call goes through one `invoke(address, envelope)`, whatever the binding. The Java service speaks the envelope natively; the Python service is reached through an adapter and is unchanged. The tour behaves exactly as before: same stages, same numbers, same addresses and stage files.

### Step 0: spec

1. **Done, 23 November 2026.** Spec "02 - Envelope and Uniform Access" in The Knowledge (`realising/specs/`) is settled. Read its Decisions list first. It follows the notes: a header plus named aspects, not a flat shape.

### What goes in `amorphic-systems/`

2. **`:envelope`** (`amorphic-systems/envelope`, package `dev.amorphic.envelope`). Depends on `:addressing` and Jackson only.
   - `Envelope`: a header, an `id` and a `purpose` (the `Address` of the envelope's own specification), plus `aspects`, keyed by name. Each `Aspect` has a `schema` (an `Address`) and a `body` (a JSON tree). Nothing else at the top level.
   - Two purposes, as constants: `pkg:envelope/request@1.0` and `pkg:envelope/reply@1.0`. Each must contain a `payload` and a `causation` aspect (`causedBy`: a list of ids); building or reading one without them is an error. Extra aspects are kept and ignored.
   - A reply's causation holds the request envelope's id. A failure is a reply whose payload is an error (`{"error": "..."}`) under its own schema.
   - Reads and writes the JSON form, with addresses as canonical text, parsed at the edges. Example, from spec 02:

     ```json
     {"id": "e-7f3a", "purpose": "pkg:envelope/request@1.0",
      "aspects": {
        "payload":   {"schema": "pkg:schema/greeting-request@1.0", "body": {"name": "Ada"}},
        "causation": {"schema": "pkg:schema/causation@1.0", "body": {"causedBy": ["r-0042"]}}}}
     ```
3. **`:access`** (`amorphic-systems/access`, package `dev.amorphic.access`). Depends on `:addressing` and `:envelope`.
   - `Invoker`: `Reply invoke(Address address, Envelope request)`. `Reply` holds the reply envelope and its `Timing` (total time; for the adapter, also the time spent in the service, so adapter time = total minus service). Timings stay with the binding and go to evidence; they are not an aspect (spec 02).
   - **`LocalBinding`**: calls a `Handler` (envelope in, envelope out) in the same process.
   - **`RemoteBinding`**: `POST {url}/invoke` with the envelope as JSON; the reply is an envelope.
   - **`AdapterBinding`**: wraps a plain REST service. Its `AdapterSpec` says the method and path, which payload fields become query parameters, and which response fields become the reply payload. Error responses become error replies. It times the service call.
   - Framework tests use neutral examples (`greeter`), with a tiny in-test HTTP server for the remote and adapter bindings.
4. **Schemas in the catalogue.** The catalogue format gains a `schemas:` section: each entry is an address and a file path relative to the catalogue. `Catalogue.schema(Address)` resolves one with the `Resolver`. A schema file is JSON Schema. The framework's own schemas (the two purposes and `causation@1.0`) ship as resources in `:envelope`, and the catalogue resolves them too. Each capability version says how it is reached: `access: envelope` or `access: adapter`, with the adapter's spec.

### In the demo

5. **Schemas.** `amorphic-demo-env/schemas/` holds `pricing-calc-convert-request@1.0` and `-reply@1.0` as JSON Schema files (failures use the framework's built-in `pkg:schema/error@1.0`), listed in `catalogue.yaml`. Python 1.0 gets `access: adapter` (`GET /convert`, `amount`, `from`, `to` → query, `amount`, `currency` ← body). Java 2.0 and 2.1 get `access: envelope`.
6. **Java service.** `ConvertController` gains `POST /invoke`, using `:envelope`. `GET /convert` and `/health` stay, because the tour asks both services directly for 732.98.
7. **Java images.** `build_java_image` in `./demo` builds from the current tree, with `pricing-calc-java/pricing-calc-core` taken from the tag (`git archive` HEAD, then overlay core from `refs/tags/pricing-calc-java/<version>`). So 2.0 keeps HALF_UP and its version, and the service shell speaks the envelope. Say this in a comment beside the function.
8. **The app.**
   - `PricingEndpoint`, `RemotePricingEndpoint` and `LocalPricingEndpoint` give way to `Invoker`s. `Bindings` picks one from the table's binding and the catalogue's `access`: `local` → `LocalBinding` around a demo `Handler` that calls `PricingCalcCore`; `remote` → `RemoteBinding` or `AdapterBinding`. The stage files and binding types don't change.
   - `RoutingPricingCalc` builds one request envelope per call (`causation` = the request id) and sends the same envelope to champion and shadow. An error reply becomes `PricingRejectedException`, as today.
   - Fail fast at startup if a routed address's schema or adapter spec can't be resolved, in the style of today's `AddressTableException`.
9. **Evidence.** Each record gains `served_ms` and, on the adapter path, `served_service_ms` (and the same for the shadow). The log line carries them; `/evidence` and the tour stay as they are. This is the data for the future story "the adapter costs, so go native".

### Verify

- `./gradlew build` and `./gradlew :amorphic-demo-app:bootJar -PfoldIn=true`; `checkFrameworkDependencies` passes with `:envelope` and `:access`.
- `git diff main --stat -- pricing-calc-py` is empty.
- A Java 2.0 image built the new way still answers 916.23 for 732.98, and `POST /invoke` returns a reply envelope.
- `./demo tour` runs stages 0 to 4 with the same story; the governor sequence in the guide's Part 2 proposes the same six steps.
- The evidence log shows `served_service_ms` on Python calls.

### Not in slice 2

- A telemetry aspect: timings travel beside the envelope, not in it.
- Topology records. `causation` holds the request id only; slice 3 links it to a topology record.
- Validating payloads against their schemas at run time. Schemas are named and resolved; validating comes later.
- A schema registry (Apicurio): to revisit, not a must-have.
- Python speaking the envelope natively, and the "rewrite to native" story.
- New Java versions or tags, and any change to addresses, stage files, the tour or the guide's output.

## Slice 3 brief

A handover for doing slice 3 in Claude Code. Read the slice 3 decisions under "Decided" first. **Draft, 23 November 2026:** the points marked *Proposed* are open until settled with Stephen, one at a time.

**Goal:** every call leaves a fixed record of exactly what ran, so `/evidence` can show the topology behind any mismatch. The tour behaves as before: same stages, same numbers, same addresses and stage files.

**Why (from "Framework to manage processing through the use of topologies").** A topology is a fixed definition of the resources an activity used: the exact versions and instances. It is like a prepared query against an ad hoc one: the ad hoc call resolves the best route now, and the record keeps what that route was. With it you can replay a call on exactly the same resources, and when a defect is found in one instance, you can find everything it produced.

### Step 0: spec

1. Draft spec "03 - Topology" in The Knowledge (`realising/specs/`), from the note above and this brief, and settle it with Stephen before building. It builds on spec 01 (instances) and spec 02 (causation).

### The shape

2. **A topology is the resolved shape, not the call.** For one call it lists each role (champion, shadow, canary) with its requested address, the address it resolved to, the binding, and the instance that ran it, plus the table generation and the caller (the app's instance). **Settled:** its id is a hash of that content, so calls that ran on the same shape share one topology, and evidence keyed by topology id still counts like today's address pairs, just more exactly.
   - **A topology has an address,** like everything else: kind `topology`, named after the capability, with the hash as a qualifier, for example `pkg:topology/pricing-calc?hash=5f2c1a9e`. This needs no change to the grammar or `Address`. Broadening it to `pkg:topology/pricing-calc` asks for every topology pricing-calc has run on, which is the question to ask once a defect is found. A topology is always looked up by its exact address, not through the `Resolver` (which picks among versions).
   - When the composer lands (slice 4), a topology covers a whole plan, and the name becomes the outcome (for example `quote`).
   - **Later:** the hash as a version under a second version scheme (for example `pkg:topology/pricing-calc@#5f2c1a9e`). That is the second scheme spec 01 defers until one is needed (section 5's symbol idea); it changes spec 01, `Version` and the parser, so not now.
3. **A call record names its topology.** One line per call: the call's envelope id, its `causedBy`, the topology's address, and the timestamp. The topology itself is written once, the first time it is seen. This is how "causation links to a topology record" works: the record holds the envelope id, so nothing extra rides in the envelope.
4. **Instances in text.** *Proposed:* no new address syntax. An instance is written as a small JSON object, `{"identity", "realises", "host"}`, with `realises` as a canonical address and `host` as the host's identity. PURL has nowhere for "realises" or "host" (discussion item 7), and this avoids inventing a form before one is needed.
5. **Where identities come from (spec 01, section 8).** Whoever deploys a thing assigns its identity.
   - *Proposed:* `./demo` gives each service it starts an identity made from its name and image id (for example `java-pricing/5f2c1a9e`), so a restart keeps it and a new image makes a new one. It writes them, keyed by binding URL, to `.run/instances.yaml`, and gives the app its own as `AMORPHIC_INSTANCE`.
   - A folded-in core is a part of the app: `Instance.part(app, "pricing-calc")`.
   - If a binding has no known instance, the record says so (`"instance": null`) rather than falling back to the URL. A URL is a location, not an identity.

### What goes in `amorphic-systems/`

6. **`:topology`** (`amorphic-systems/topology`, package `dev.amorphic.topology`). Depends on `:addressing` and Jackson only.
   - `Topology` (generation, caller, roles) with a content-hash `id()` and an `address()` (`pkg:topology/<name>?hash=<id>`), and `CallRecord` (envelope id, `causedBy`, topology address, time).
   - `TopologyLog`: append-only JSON lines in one file. `record(call, topology)` writes the topology once and the call every time; `topology(address)` and `calls(address)` read back, and `topologies(name)` lists every topology under a broader address. It survives a restart: on start it reads the file to learn which topologies it has already written.
   - `InstanceJson` for the text form in point 4, read and written at the edges.

### In the demo

7. **The app.** `RoutingPricingCalc` builds the topology for each call from the resolved targets and their instances, and hands it to `TopologyLog` with the call. `GET /topology?address=<address>` returns a topology, or, for a broader address such as `pkg:topology/pricing-calc`, the list of them; `GET /topology/calls?address=<address>` returns a topology's calls.
8. **Evidence.** Counters are keyed by topology id. `/evidence` keeps today's fields (served and shadow addresses come from the topology), and each comparison and `lastMismatch` gains `topology`, its address. The log line gains `topology` too.
9. **`./demo`.** Writes `.run/instances.yaml` and `AMORPHIC_INSTANCE` as in point 5. The app's records go to `.run/records/topology.jsonl` (a writable mount; the app's view of `.run/` itself stays read-only). Restarting the app keeps the file; `./demo down` removes it with the rest of `.run/`.

### Verify

- `./gradlew build` and `./gradlew :amorphic-demo-app:bootJar -PfoldIn=true`; `checkFrameworkDependencies` passes with `:topology`.
- `./demo tour` runs stages 0 to 4 with the same story.
- At stage 1, `/evidence`'s `lastMismatch.topology` resolves through `/topology` to Java 2.0's instance as the shadow, by identity, not URL.
- After `docker compose restart app`, the same topology address still resolves.
- Rebuilding the Java image gives a new instance identity and so a new topology address; restarting the container keeps both.

### Not in slice 3

- Replaying a call from its topology, and finding everything a defective instance produced. The records make both possible; neither is built.
- The inventory or governor reading topology records. Evidence's governor-facing shape stays the same.
- A text address form for instances (discussion item 7), and realms (item 8).
- Carrying the topology id inside the envelope (an aspect). The record points at the envelope instead.
- Rotating or pruning the records file.

## Moving out

**When:** the first time something other than the pricing demo needs the skeleton. Likely candidates are the level 202 Quarkus capability or the reference implementation itself. A second sign is that slices stop changing the skeleton's interfaces.

**How:** move `amorphic-systems/` into the `amorphic-systems` repository, and switch this build to `includeBuild`. Publish artefacts later, if they're needed at all. The demo stays here.

## Decided

16 November 2026, slice 0:

- **Name.** The framework lives in `amorphic-systems/`, named after the repository it will move to. Packages are `dev.amorphic.<concept>`. Only `dev.amorphic.demo` belongs to the demo, so the rest of the namespace is free.
- **Tooling moves now.** The inventory and governor move into `amorphic-systems/` in slice 0, both the cores and the services (`inventory-core`, `inventory-service`, `governor-core`, `governor-service`), as a straight relocation with no code change.
- **Dependency rule.** A small Gradle check in the root build fails if any framework project depends on a demo project. A text check for demo words comes later; ArchUnit once there is more code.
- **Governor readers.** Tag `mlp/102` and add one README line pointing to it. The article stays as it is for now.

Found while checking the move: none of the four projects depends on a demo project. `governor-core` mentions pricing-calc and stage files only in comments (`Tables.java`, `Proposal.java`); the rest mention neither. The demo's files (catalogue, rules, address table) reach the services as configuration, not code. `governor-core`'s version comparison parses address strings by hand, which slice 1 replaces.

16 November 2026, slice 1:

- **Grammar.** An own grammar to spec 01, with PURL read and written as one form of it. No purl library: PURL argues for the concept but is aimed elsewhere (versions are opaque, ranges live in the separate `vers:` spec, and there is no narrowing).
- **Versions.** A version is a scheme value. One named scheme for now (dotted numbers), used for both versions and ranges, so more can be added without changing `Address`.
- **Manifestation.** It gets its own address, pointing to the logical address it realises and to the host it runs in, which keeps "it is a" apart from "it is the". So the host (the app) needs an address too. Slice 1 only names these; slice 3 records what ran.
- **Best match.** The highest version that satisfies the range, with qualifiers matching exactly.
- **Module.** `amorphic-systems/addressing`, package `dev.amorphic.addressing`.

23 November 2026, slice 2:

- **Order.** The envelope comes before topology (discussion item 5).
- **Envelope.** JSON, with addresses as canonical text.
- **Schemas.** Addressed assets, kept as files listed in the catalogue for now. A registry such as Apicurio can be a store behind the same addresses later; it isn't a must-have.
- **On the wire.** The Java service speaks the envelope natively; the Python service is reached through an adapter and is unchanged (discussion item 1). The adapter path records timings.
- **Envelope shape (spec 02).** A header (id, purpose) plus named aspects, each with its own schema, as in the notes. The purpose is the envelope's own specification; the payload's schema names the operation, and the address passed to `invoke` names the capability.
- **Timings.** Kept by the binding and handed to evidence. A telemetry aspect is for later.
- **Java images.** Built from the core at the tag and the service shell from the current tree. The version names the core's behaviour (the rounding); the shell is how it is reached. A later version of the story will revisit this (see "Future stories").

23 November 2026, slice 3:

- **Topology records** live in a simple append-only file (JSON lines), so a record that is meant to be fixed outlives the process (discussion item 3).
- **Topology identity and address.** The id is a hash of the topology's content; the topology is addressed like anything else, with the hash as a qualifier (`pkg:topology/pricing-calc?hash=…`). The hash as a second version scheme is noted for later.

## For discussion

1. **Envelope on the wire.** Should the Java service speak the envelope natively while Python goes through an adapter? Or should both go through adapters at first, so slice 2 changes only the app? *Settled for slice 2: see Decided.*
2. **Composition depth.** Is one step enough to show the composer exists, or does the skeleton need two (for example, quote = convert, then add a fee) to show that composition is real?
3. **Where topology records live.** In memory and logs only, like evidence today, or in a simple append-only file? *Settled for slice 3: see Decided.*
4. **Address grammar.** Should the address parse with purl-spec, or with an own grammar to spec 01 that reads PURL as one form? (Shared grammar, not shared standard.) *Settled for slice 1: see Decided.*
5. **Slice order.** Should topology come before the envelope, since evidence is already most of the way to a topology record? *Settled: envelope first.*
6. **Module names.** One project per concept inside `amorphic-systems/`: what is each one called? *Settled for slice 1: see Decided.*
7. **PURL is one form, not the grammar.** PURL got slice 1 going, but it will get in the way. Its type slot is a short word, not an alias for a spec. It has `@` and nothing else for versions, so it can't declare a scheme. It has nowhere to put an instance's "realises" or "host". And it is flat, so it can't hold a composition. Today only `Address` reads or writes the `pkg:` text; the code uses `Address` objects everywhere else, but the data files (stage files, catalogue, rules) all hold `pkg:` strings. So the cost of changing the form grows with the data. *Due when kind-as-alias or instance addresses land.*
8. **The address carries its realm.** In `pkg:amorphic/greeter`, `pkg` is a realm (PURL's) and `amorphic` is a type in it. Today `Address` treats `pkg:` as fixed punctuation, so the realm can't be seen. Making it explicit fits "a catalogue defines its aliases": the realm says whose aliases the kind is looked up in, and a hierarchy is a chain of those lookups that ends at the root kind. Examples: `pkg.amorphic/greeter@1.0` (today, made explicit), `pkg.maven/org.foo/bar@2.1` (an ordinary PURL), `amorphic.service/greeter@1.0` (our own realm). In code, `Address` gains a `realm()` or a kind path, and its callers keep asking for `kind()`.

## Future stories

Stories the framework should be able to tell later. Nothing here is in a slice yet.

1. **The adapter costs, so go native.** The adapter path records its timings (slice 2). The system notices what the adapter costs and proposes rewriting the service to speak the envelope natively. The build proves the rewrite gives the same answers (golden vectors) and is faster, and, if feasible, estimates the saving in total cost of ownership.
2. **Versions name the behaviour spec, not the code.** A version should name the specification of how the capability behaves, not a release of one implementation. Retold that way: Python implements pricing-calc 1.0, and the first Java implementation is also 1.0. The rounding mismatch causes a spec update, 1.1 (round HALF_EVEN), with new tests, and Java 1.1 follows. The API contract stays stable; only the behaviour spec changes. Python can then prove it implements 1.1 too. The open question is how it shows that: claim a range (`1.*`), or point to evidence, the commit that adds the tests (the evidence producer) and the passing run, which is what proves it implements 1.1. Evidence is the leaning. This changes the demo's addresses and story, so it is a new version of the story, not a slice.
