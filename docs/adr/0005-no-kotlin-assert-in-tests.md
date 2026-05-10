# ADR 0005 — No bare `kotlin.assert(...)` in tests

- **Status:** Accepted
- **Date:** 2026-05-09
- **Supersedes:** —

## Context

Test names in this codebase read like a spec (see `CLAUDE.md` § *Test naming
convention*) and reports list them verbatim, so the assertions inside them
must fail loudly with informative output the moment a regression appears.

`kotlin.assert(cond) { msg }` is the global builtin from the Kotlin prelude.
The JVM only evaluates its condition when assertions are enabled (`-ea`).
The Android instrumented runner turns them on, so a misused `assert` *appears*
to fire locally — but the same code in any environment without `-ea` (a
contributor's IDE config, a JVM unit-test task without explicit flags, a future
runner change) is a silent no-op and the failure-message lambda never runs.
Latent bugs ride along until something flips the flag.

PR #1117 fixed a real instance of this trap: an `EXTERNAL_LEGAL_ITEMS` count
that had been wrong for one release because a `kotlin.assert` in a test masked
the regression — the test "passed" without ever evaluating the assertion.

`assert` is also a path of least resistance: it needs no import, so it sneaks
in mid-flow inside `.let { ... }` chains and inline lambdas, especially when
the author is in a hurry.

## Decision drivers

1. **Tests must fail under any runner.** A test that "passes" because
   assertions are disabled is worse than a missing test — it ships false
   confidence.
2. **Better failure output is already on offer.** Truth (`assertThat`) prints
   formatted expected-vs-actual; the Compose UI Test API (`assertCountEquals`)
   names the matched semantics tree on failure. Both beat `assert`'s "no
   message at all when -ea is off, opaque AssertionError when on".
3. **Guard before the human spot-checks.** Code review catches some `assert`
   sneak-ins; a grep-enforced invariant catches all of them, on every push,
   without depending on reviewer attention.

## Options considered

- **Leave it to author judgment** — rejected. The failure mode is silent;
  authors who don't already know the trap (PR #1117) will reintroduce it.
- **Enforce only in CI** — rejected. The CI job (`test-assertion-guard`) is
  the last line of defense, but contributors shouldn't have to wait for CI to
  learn they used the wrong assertion API. A local script + an ADR raise the
  rule's visibility before the push.
- **Adopt a third-party assertion library (Kotest, AssertK)** — rejected. We
  already have Truth + JUnit + Compose UI Test on the classpath; adding a
  fourth assertion vocabulary increases cognitive load without a payback that
  matches the cost.
- **Enforce via grep, in CI and locally, with the rule pinned in an ADR** —
  chosen.

## Decision

Bare `kotlin.assert(...)` is forbidden in any test sourceset across all four
modules:

- `app/src/test/`, `app/src/androidTest/`
- `commons_android/src/test/`
- `commons_file/src/test/`
- `model/src/test/`

Use one of these instead, in this order of preference:

1. **Truth** — `assertThat(actual).isEqualTo(expected)`,
   `assertThat(collection).isEmpty()`, etc. Best failure output for value
   comparisons.
2. **Compose UI Test API** — `node.assertCountEquals(0)`,
   `node.assertIsDisplayed()`, etc. Best output when asserting on a semantics
   tree; lists the matched nodes on failure.
3. **JUnit** — `assertEquals`, `assertTrue`, `assertNotNull`, ... when the
   test is pure JVM and Truth would be overkill.

## Consequences

- Tests fail loudly under any runner — IDE, `./gradlew test`,
  `connectedDebugAndroidTest`, CI — regardless of `-ea` configuration.
- New contributors learn the rule on first push (CI fail) or first local run
  (script fail), not after a regression ships.
- The grep is intentionally permissive: it matches `assert(` with any leading
  whitespace, catching `kotlin.assert(...)` and bare `assert(...)`. False
  positives (a hypothetical local helper *named* `assert`) are vanishingly
  rare in this codebase and would be addressed by renaming the helper.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`)
and mirrored by the dedicated CircleCI job `test-assertion-guard`:

- The regex `(^|[^[:alnum:]_])assert[[:space:]]*\(` must return zero hits when
  applied with `grep -rnE --include='*.kt'` to
  `app/src/test app/src/androidTest commons_android/src/test commons_file/src/test model/src/test`.
  Any hit fails the check. Renaming or moving a test sourceset requires
  updating the search paths in the script (and updating this ADR
  accordingly).

## Cross-references

- `CLAUDE.md` § *Test assertions* (rule SSOT) and § *Test naming convention*
  (why descriptive output matters).
- CI mirror: `.circleci/config.yml` job `test-assertion-guard`.
- Local enforcement: `scripts/check-adr-invariants.sh`.
- Truth: https://truth.dev. Compose UI Test API:
  https://developer.android.com/jetpack/compose/testing.
