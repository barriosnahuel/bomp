# ADR 0013 — Single-SDK default for Robolectric tests; matrix only for real SDK branches

- **Status:** Accepted
- **Date:** 2026-05-24
- **Supersedes:** —

## Context

Robolectric runs a JVM unit test against a simulated Android framework at a
chosen API level, configured via `@Config(sdk = [...])`. Listing several levels
runs the whole test once per level (a matrix). When `sdk` is unset, Robolectric's
documented default is the module's `targetSdk`.

The `:app` and `:model` `AbstractRobolectricTest` base classes pinned a
three-level matrix — `[M, TIRAMISU, VANILLA_ICE_CREAM]` — inherited by every
subclass that did not override it, and two `:commons_android` DataStore tests
plus one `:model` repository test repeated the same matrix standalone. None of
the production code under those tests branches on `Build.VERSION.SDK_INT`: it is
persistence, analytics-wrapper and ViewModel logic that runs identical bytecode
on every API level. The matrix tripled their execution time for zero extra
coverage.

The only production code that *does* branch on the SDK had the inverse problem:
`ui/haptics/Haptics.kt` (`>= R`) had no test at all, and
`AddButtonActivity.handleIntent` (`>= TIRAMISU` — typed vs deprecated
`getParcelableExtra`) was pinned single-SDK at TIRAMISU, so its legacy branch
never executed.

This surfaced while diagnosing a CI hang (PR #1186): a racy test inside a matrix
class hit `no_output_timeout`, and the matrix made the failure three times more
expensive to reproduce. The matrix had been cargo-culted — copied from one test
to the next without a per-test reason.

## Decision drivers

1. **Run cost should buy coverage.** A second or third SDK run that executes
   identical bytecode is pure CI time with no signal.
2. **SDK branches that exist must be exercised on both sides.** A `>= X` branch
   tested only at `>= X` ships its fallback untested.
3. **Greppable and enforced.** The rule must be self-documenting and guarded, so
   the cargo-cult does not regrow.

## Options considered

- **Inherit `targetSdk` (drop `sdk`).** Most production-faithful and
  Robolectric's documented default — but `targetSdk` is 37, above Robolectric
  4.16's ceiling (36, Baklava), and no test currently runs at 36. Flipping the
  whole suite to an unproven level in one change is the riskier move; deferred to
  the revisit criterion below.
- **Keep the matrix as the default.** Rejected — it is the status quo whose cost
  this ADR removes.
- **Single-SDK default at TIRAMISU, opt-in matrix only on a real `SDK_INT`
  branch.** Chosen.

## Decision

The default for a Robolectric test is a **single SDK**. The base classes pin
`[TIRAMISU]`, the level on which the entire suite is already proven green (it was
a member of every prior matrix), so the collapse changes no test's pass/fail
outcome.

A **multi-SDK** `@Config(sdk = [a, b, ...])` is allowed **only** when the code
under test has a real `Build.VERSION.SDK_INT` branch, and must be justified
inline with a `// sdk-boundary:` comment naming that branch. The matrix should
straddle the boundary (one level below, one at/above). Canonical:
`HapticsTest` (`[M, R]`), `AddButtonActivitySdkBoundaryTest` (`[M, TIRAMISU]`).

A single SDK other than the default (a one-off pin) needs no marker — it is
already the conservative case.

## Consequences

- SDK-independent tests run once; CI time on those classes drops ~3x.
- The two real SDK branches now execute on both sides (the haptics fallback and
  the deprecated `getParcelableExtra` path were previously never run).
- **TIRAMISU is a transitional floor, not a target.** Revisit when Robolectric
  supports `targetSdk` (or the suite is validated at 36): bump the base-class SDK
  toward the level real devices run, or drop `sdk` to inherit `targetSdk`.
- DataStore tests lose their per-SDK runs. If a future need to guard
  DataStore-the-library across API levels appears, it returns as an explicit
  `// sdk-boundary:` matrix stating that reason — not as an unexplained default.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- A line matching a multi-element `sdk = [ ... , ... ]` array (two or more
  levels) in any test sourceset
  (`app/src/test app/src/androidTest commons_android/src/test commons_file/src/test model/src/test`)
  must carry a `// sdk-boundary:` comment, or the check fails. Single-element
  `sdk = [X]` and the base-class default are unrestricted.
- The match is per-line, so it assumes the array is on one line — the form ktlint
  produces today (a fully-qualified three-element array stays under the 150-char
  limit). A hand-wrapped multi-line array would slip the guard; revisit if ktlint
  argument wrapping ever makes that shape possible.

## Cross-references

- `CLAUDE.md` § *Robolectric SDK config* (rule SSOT).
- Robolectric default SDK: https://robolectric.org/configuring/ ("run your code
  against the `targetSdk`").
- Robolectric 4.16 supported SDKs (ceiling = 36, Baklava).
- Canonical boundary tests: `HapticsTest`, `AddButtonActivitySdkBoundaryTest`.
