# ADR 0002 — Manual `viewModelFactory` instead of Hilt

- **Status:** Accepted
- **Date:** 2026-05-05
- **Supersedes:** —

## Context

Google's official recommendation for production Android apps is Hilt for dependency
injection. This codebase deliberately diverges: ViewModels are constructed via
`viewModelFactory { initializer { ... } }` in companion objects (canonical example:
`SoundsViewModel.kt`).

The current dependency graph is small:

- One ViewModel (`SoundsViewModel`).
- Four Gradle modules (`app`, `model`, `commons_android`, `commons_file`).
- No scoped repositories beyond DataStore-backed stores (`SoundsRepository`,
  `WelcomeStickerStore`, `DataStoreFirstFlagStore`, `DataStoreCounterStore`).
- No use cases / interactors layer.
- No `@AssistedInject`-style construction needed today.

In a graph this size, the per-class factory boilerplate is a handful of lines per
ViewModel.

## Decision drivers

1. **Build / test tax of Hilt is real and not amortized at this scale.** Adopting
   Hilt brings KSP, the Hilt Gradle plugin, `HiltAndroidRule` for instrumented
   tests, `HiltTestApplication`, module mocking patterns (`@TestInstallIn`,
   `@BindValue`), and per-test setUp friction. Each one is small individually;
   together they slow CI and add reading load for new contributors.
2. **The "win" of Hilt is graph-shaped.** The framework pays back when there are
   many scoped objects, when constructor injection chains get deep, when
   `@AssistedInject` is needed, or when feature modules want `@InstallIn` scopes
   that match navigation graphs. None of those apply today.
3. **Manual factories are explicit and trivially testable.** A test substitutes
   the constructor argument directly — no annotation processor, no test module,
   no `@HiltAndroidTest`. The Robolectric smoke tests for Activities (see
   § *Activity smoke tests* in `CLAUDE.md`) demonstrate this — `mockkObject` on
   the singleton factory is enough.
4. **Reversibility.** If we change our minds, migrating from manual factories to
   Hilt is mechanical: each `viewModelFactory { ... }` becomes a constructor
   `@Inject` annotation and a Hilt module binding. There is no architectural
   debt being accumulated by the manual choice.

## Options considered

- **Hilt** — the official recommendation. Rejected today on the build/test tax
  vs. graph size argument above.
- **Koin** — service-locator-style DI, lighter than Hilt at the Gradle level.
  Rejected because it trades Hilt's compile-time graph validation for runtime
  resolution; the failure mode is a worse class of bug (missing binding
  surfaces only when the screen is reached).
- **Manual `viewModelFactory`** — chosen. Lines up with Compose-era guidance
  for tiny graphs and stays explicit.

## Decision

Use manual `viewModelFactory { initializer { ... } }` in a companion object
on each ViewModel. Constructor-inject dispatchers and stores. Do not introduce
Hilt, Koin, or any other DI framework without superseding this ADR.

## Consequences

- New ViewModels follow the `SoundsViewModel.kt` pattern. Constructor takes
  collaborators directly; the factory wires them from `Application` /
  `CreationExtras`.
- New stores instantiated lazily at the call site or held by a small
  bootstrap object — not in a global container.
- **Revisit when** any of these become true: ViewModel / repository /
  scoped-dependency count grows to where boilerplate factories become friction
  rather than self-documenting; a feature requires `@AssistedInject`-style
  construction (e.g. ViewModel taking a runtime ID); we add a use-case layer
  with non-trivial graph depth. At that point, a successor ADR captures the
  migration plan.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- No `import dagger.hilt.*`, `import org.koin.*`, or `import javax.inject.*` in any production sourceset (`app/src/main`, `commons_android/src/main`, `commons_file/src/main`, `model/src/main`). If a DI framework is genuinely needed, supersede this ADR.

## Cross-references

- `CLAUDE.md` § *Project-specific overrides* → "DI: manual factories, no Hilt".
- Canonical implementation: `app/src/main/java/.../ui/SoundsViewModel.kt`.
