# ADR 0017 — Binary `allWarningsAsErrors` for Kotlin compilation

- **Status:** Accepted
- **Date:** 2026-06-16
- **Supersedes:** —

## Context

Kotlin compiler warnings (`w:`) carry real signal — deprecations, always-true
conditions, unused expressions, reflection-not-on-classpath — but they do not
fail the build, so they accumulate silently. By the time a deprecation becomes a
removal in a later Kotlin/AGP bump, the warning has been ignored for months.

With the Compose test-rule `junit4.v2` migration merged and the residual
production + analytics-test warnings cleared, the whole compile log
(`compileDebugKotlin`, `compileReleaseKotlin`, `compileDebugUnitTestKotlin`,
`compileDebugAndroidTestKotlin` across all modules) reached **zero** `w:`. That
is the precondition for turning warnings into errors: the gate is all-or-nothing
and would fail on every pre-existing warning otherwise.

## Decision drivers

1. **Warnings should not rot.** A warning nobody is forced to read is a
   deprecation discovered too late.
2. **Simplicity first.** The smallest mechanism that holds the line, with a known
   escape if it ever becomes counterproductive.
3. **Uniform across modules and variants** — main, unit test, and androidTest, in
   every module, with one declaration.

## Options considered

- **CI-property gate** (`allWarningsAsErrors.set(providers.gradleProperty(...))`,
  `-PwarningsAsErrors=true` only in CI). Lets an upgrade PR flip the gate off while
  fixing newly-introduced warnings. Rejected as the *starting* point: it is more
  machinery than the current need, and a local build that passes while CI fails on
  warnings is a worse feedback loop. Kept as the documented fallback.
- **Per-module `kotlin { compilerOptions { ... } }`.** Same effect, four edit
  sites that drift. Rejected for the single root declaration.
- **Binary, unconditional, in the root `subprojects` block.** Chosen.

## Decision

Enable `allWarningsAsErrors` **binary and unconditional** in the root
`build.gradle`, applied to every `KotlinCompilationTask` in every subproject:

```groovy
subprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask).configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }
}
```

Any new Kotlin warning now fails the build locally and in CI alike. Keeping the
compile log at zero `w:` is a hard precondition for any change.

## Consequences

- A Kotlin/AGP/library bump that introduces new warnings on otherwise-untouched
  code will redden the upgrade PR. That is intended: the warnings get fixed (or
  suppressed with a justified `@Suppress`) in the same PR that raises the version.
- **Documented escape — the upgrade treadmill.** If bumps start reddening
  untouched-code PRs often enough that it is net-negative, switch to the
  CI-property gate:
  `allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false))`,
  pass `-PwarningsAsErrors=true` only in CI, and flip it off in the upgrade PR
  while fixing the new warnings. This is the fallback, not day one.
- A deliberately-kept warning needs an explicit, local `@Suppress` with a reason —
  visible in review — instead of silently joining a warning pile.
- No new CI job: the existing `compile` / `test` / `lint` jobs already invoke the
  Kotlin compiler, so the gate rides along. A standalone grep guard for the build
  flag was considered and skipped — removing the flag is self-evident in review and
  would immediately let warnings back in.

## Cross-references

- `CLAUDE.md` § *Project-specific overrides* (build policy).
- Verified at landing: full build green at zero warnings; a deliberately
  reintroduced warning failed the build with `e: warnings found and -Werror
  specified`, then reverted.
- Kotlin compiler options (`allWarningsAsErrors`): https://kotlinlang.org/docs/gradle-compiler-options.html
