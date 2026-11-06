# amorphic-mlp

The source of truth for scope and stages is `docs/amorphic-mlp-plan.md`. Each of the plan's "repositories" is a top-level directory here.

- Java: one root Gradle build (`./gradlew`), toolchain Java 21. Projects are `:pricing-calc-core`, `:pricing-calc-service` and `:amorphic-demo-app`. The app pulls in core as a project dependency, not via mavenLocal.
- Fold-in: `-PfoldIn=true` puts core in the app's runtime classpath. Without it, core is `compileOnly`, so a `local` binding must fail fast at startup.
- Python: `pricing-calc-py` uses uv (`uv run pytest`) on Python 3.12.
- Golden vectors live at `amorphic-demo-env/golden/vectors.json`. Java tests get the path from the `golden.vectors` system property; Python tests get it from the `golden_vectors_path` fixture.
- Monorepo tags carry a prefix: `pricing-calc-java/v2.0` (HALF_UP bug) and `pricing-calc-java/v2.1` (HALF_EVEN fix plus a regression test from the caught mismatch).
- Stages are 0, 1, 1b, 2, 3, 3b and 4, with one `amorphic-demo-env/addresses/stage-<id>.yaml` each. The stage table in the plan is canonical.
