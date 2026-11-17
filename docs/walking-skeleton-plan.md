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
| 2 to 6 | Not started |

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

## For discussion

1. **Envelope on the wire.** Should the Java service speak the envelope natively while Python goes through an adapter? Or should both go through adapters at first, so slice 2 changes only the app?
2. **Composition depth.** Is one step enough to show the composer exists, or does the skeleton need two (for example, quote = convert, then add a fee) to show that composition is real?
3. **Where topology records live.** In memory and logs only, like evidence today, or in a simple append-only file?
4. **Address grammar.** Should the address parse with purl-spec, or with an own grammar to spec 01 that reads PURL as one form? (Shared grammar, not shared standard.) *Settled for slice 1: see Decided.*
5. **Slice order.** Should topology come before the envelope, since evidence is already most of the way to a topology record?
6. **Module names.** One project per concept inside `amorphic-systems/`: what is each one called? *Settled for slice 1: see Decided.*
