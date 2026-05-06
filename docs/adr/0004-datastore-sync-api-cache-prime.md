# ADR 0004 — Synchronous read API on top of DataStore via in-memory cache + async write-back

- **Status:** Accepted (scoped exception, do not generalize)
- **Date:** 2026-05-05
- **Supersedes:** —

## Context

Jetpack DataStore Preferences is the persistence mechanism for this codebase
(see `CLAUDE.md` § *Persistence*). DataStore's public API is suspending /
`Flow`-based by design — every read is async.

A small number of call sites need a **synchronous** read:

- The analytics tracker (`AnalyticsTrackerProvider.createTracker`) primes
  `is_first_<event>` flags and lifetime counters before the app dispatches its
  first analytics event. The first event can fire within hundreds of
  milliseconds of `Application.onCreate` — well before any suspending init has
  resolved.
- Several events fire **immediately before navigating away to a chooser /
  browser / external Activity** (e.g. share intent dispatched, "open source
  code" button). If the analytics call suspends and the OS suspends our
  process during navigation, the event never reaches Firebase and the funnel
  breaks. Firebase's own SDK uses an internal buffer to mitigate this; we
  need the same property at our layer above DataStore.

Mocking DataStore as suspend-only would force the tracker call sites to either
(a) suspend (incompatible with click handlers that immediately launch an
external Intent) or (b) `runBlocking` at the call site (way worse than
`runBlocking` in a one-time bootstrap path).

## Decision drivers

1. **Event durability beats threading purity at this specific seam.** Losing
   funnel events because we refused to block at boot is the wrong trade-off.
   Firebase Analytics' own internal architecture (sync `logEvent` API + async
   buffer) takes the same call.
2. **The cost is bounded and one-time.** The cache prime happens once per
   process, in the store constructor, on a small number of keys (booleans
   and ints for counters). It does not happen per call.
3. **The risk of generalizing is large.** "Sync API on top of async storage"
   is a tempting pattern that, applied broadly, reintroduces all the reasons
   `runBlocking` is forbidden in this codebase. Documenting the exception
   *and* its scope explicitly is the only way to keep new contributors from
   replicating the pattern outside the analytics tracker.

## Options considered

- **All analytics writes via suspend functions, refactor call sites** —
  rejected. Click handlers that launch external Activities cannot suspend
  without losing the user gesture; introducing a "fire and forget" coroutine
  loses the event when the OS suspends our process.
- **`first()` blocking call inside DataStore at every read** — rejected.
  Pays the cost on every event instead of once at boot.
- **Cache + async write-back, prime via `runBlocking(IO)` in store
  constructor, called from the `StrictMode.allowThreadDiskReads` block in
  `AnalyticsTrackerProvider.createTracker`, with `MainApplication.onCreate`
  dispatching an early warm-up onto a background coroutine so the prime
  rarely blocks main in practice** — chosen.

## Decision

Use the in-memory cache + async write-back pattern, with these specifics:

- Pattern lives in `commons_android/.../DataStoreFirstFlagStore.kt` and
  `commons_android/.../DataStoreCounterStore.kt`. Mirror these for any future
  store that needs the same property (no new sync seams expected today).
- The cache prime uses `runBlocking(IO)` inside the store constructor.
- Construction of the analytics tracker happens inside a
  `StrictMode.allowThreadDiskReads { ... }` block in
  `AnalyticsTrackerProvider.createTracker` — without this, StrictMode in
  debug builds (see `CLAUDE.md` § *StrictMode debug audit*) crashes the
  process at boot.
- `MainApplication.onCreate` dispatches a warm-up onto a background coroutine
  *before* the first analytics event can fire, so in practice the prime
  rarely blocks main.

## Consequences

- This is the **only** documented exception to the
  "no-`runBlocking`-in-production" rule declared in `CLAUDE.md`
  § *Project-specific overrides* → Threading.
- **Do not generalize.** New persistent stores default to suspend / `Flow`
  reads. If a new call site claims it needs sync, the bar is: produce a
  failure mode that costs durability the way the chooser-navigation race
  does, and supersede this ADR with one that widens the scope deliberately.
- Tests substitute via `clearForTest()` (declared on every store) and / or
  a fake tracker (`FakeAnalyticsTracker`); the production cache-prime path
  does not run in unit tests.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- `runBlocking(...)` / `runBlocking { ... }` must only appear in the three allowlisted files: `commons_android/.../analytics/DataStoreFirstFlagStore.kt`, `commons_android/.../analytics/DataStoreCounterStore.kt`, `commons_android/.../analytics/AnalyticsTrackerProvider.kt`. Any other file that introduces `runBlocking` fails the check. Renaming or moving an allowlisted file requires updating the allowlist in the script (and updating this ADR accordingly).

## Cross-references

- `CLAUDE.md` § *Persistence* (rule SSOT) and § *Project-specific overrides*
  → Threading (the no-`runBlocking` rule and its exception).
- Implementations: `commons_android/.../DataStoreFirstFlagStore.kt`,
  `commons_android/.../DataStoreCounterStore.kt`,
  `commons_android/.../analytics/AnalyticsTrackerProvider.kt`.
- StrictMode interaction: `app/src/debug/.../StrictModeConfigurator.kt`.
