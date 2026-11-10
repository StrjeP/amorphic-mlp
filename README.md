# amorphic-mlp

A minimal, runnable demo of an "amorphic" capability. `pricing-calc` starts out as a Python service, gets re-implemented in Java, earns trust through shadow and canary traffic, and is then folded into the calling app. The full plan is in [docs/amorphic-mlp-plan.md](docs/amorphic-mlp-plan.md). For the high-level narrative, see [docs/story.md](docs/story.md).

## Where it stands

- **Done:** stages 0 to 4 end to end, including the planted bug and its fix. Address tables apply live (hot reload). An inventory observes what is running, and a rule-based governor proposes every move from 1b to 4, each approved by a human. The inventory and governor have their own addresses in the table, so either runs in-process or as its own service.
- **Planned:** a step-by-step guide for recreating the story yourself.

The demo walkthrough, with real captured output, is in [amorphic-demo-env/README.md](amorphic-demo-env/README.md). The progress table in the plan has the detail.

## Articles

- [What this demo shows](docs/articles/what-this-demo-shows.md): the isomorphic, polymorphic and amorphic framing, with a storyboard of every stage.
- [The Governor: Letting Evidence Reshape a System](docs/articles/the-governor.md): the wider argument the demo is a first step towards.

## Layout

| Path | What it is | Tooling |
|---|---|---|
| `pricing-calc-py/` | Incumbent Python service (v1.0, HALF_EVEN) | Python 3.12, FastAPI, uv |
| `pricing-calc-java/pricing-calc-core/` | Plain-Java library (v2.0 has the planted HALF_UP bug, v2.1 has the fix and a regression test) | Java 21, Gradle |
| `pricing-calc-java/pricing-calc-service/` | Spring Boot REST wrapper over core | Java 21, Gradle |
| `amorphic-demo-app/` | Calling app: `/quote`, resolver, shadow/canary, `/evidence` | Java 21, Spring Boot 3, Gradle |
| `amorphic-inventory/inventory-core/` | The inventory: observes the live table, `/health` and `/evidence` against the catalogue | Java 21, Gradle |
| `amorphic-inventory/inventory-service/` | The inventory as its own service, for a `remote` binding | Java 21, Spring Boot 3, Gradle |
| `amorphic-governor/governor-core/` | The governor: applies the rule table to the inventory and proposes the next address table; a human approves | Java 21, Gradle |
| `amorphic-governor/governor-service/` | The governor as its own service, for a `remote` binding | Java 21, Spring Boot 3, Gradle |
| `amorphic-demo-env/` | Compose files, per-stage address tables, catalogue, governor rules, golden vectors, traffic and gate scripts | docker compose, bash |

The Java modules share one Gradle build at the repo root. The demo app depends on `:pricing-calc-core` as a project, so `mavenLocal()` isn't needed.

## Common commands

```bash
./gradlew build                                # all Java modules
./gradlew :amorphic-demo-app:bootJar -PfoldIn=true # stage 4: core in-process
cd pricing-calc-py && uv run pytest            # Python tests
```

## Versions and tags

There is only one tag namespace in a monorepo, so the Java bug/fix tags carry a prefix: `pricing-calc-java/v2.0` and `pricing-calc-java/v2.1`.

## Stages

`0` baseline → `1` shadow (java 2.0, gate fails) → `1b` fix (java 2.1 plus regression test, gate passes) → `2` canary → `3` promote (java champion, python shadow) → `3b` deprecate (python stopped) → `4` fold-in (core in-process). Each stage has one address table at `amorphic-demo-env/addresses/stage-<id>.yaml`. See the stage table in the plan for exit criteria.
