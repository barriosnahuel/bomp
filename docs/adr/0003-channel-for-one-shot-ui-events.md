# ADR 0003 — `Channel<T>` + `receiveAsFlow()` for one-shot UI events

- **Status:** Accepted (with explicit revisit criteria)
- **Date:** 2026-05-05
- **Supersedes:** —

## Context

When a ViewModel needs to surface a one-shot event to the UI — "button saved",
"share dispatched", "URL opened" — there are two mainstream patterns in modern
Android:

1. **`Channel<T>` + `receiveAsFlow()`.** ViewModel writes to a private
   `Channel`, exposes it as a `Flow`, the screen `LaunchedEffect`s on
   `lifecycleOwner.repeatOnLifecycle` and reacts. This codebase uses this
   pattern today: see `SoundsViewModel._buttonSavedEvent: Channel<String>`.
2. **Event-as-state.** The event becomes a field on `UiState` (e.g.
   `pendingFeedback: FeedbackId?`), the screen reads it from `state`, calls
   the ViewModel's `onEventConsumed()` after rendering. This is the pattern
   the official Compose / app-architecture guidance now leans toward — see
   https://developer.android.com/topic/architecture/ui-layer/events.

Both work. They differ in delivery guarantees and in test ergonomics.

## Decision drivers

1. **Consistency over local optimum.** The codebase already standardised on
   `Channel`. Migrating one ViewModel to event-as-state makes the codebase
   harder to read (two patterns to learn) for a benefit that is not currently
   needed.
2. **Real failure mode of `Channel` is well understood.** A `Channel` event
   emitted while the app is backgrounded between `emit` and `collect` *can* be
   dropped. None of the events emitted today (`buttonSavedEvent`) carry
   correctness — they trigger snackbars or analytics, both tolerant of loss.
3. **Event-as-state pays back when delivery matters.** If we add an event
   that a user must see exactly once and never miss (e.g. a payment receipt
   confirmation, an irreversible deletion notice), event-as-state with explicit
   `onEventConsumed()` is the right pattern — the event lives in state until
   acknowledged, no race.

## Options considered

- **Migrate everything to event-as-state now** — rejected on consistency and
  on the absence of a real lost-event bug today.
- **Keep `Channel` and never revisit** — rejected because the platform
  guidance has moved; pretending otherwise is exactly the "answer from
  training data" failure mode that `CLAUDE.md` § *Sources of truth* warns
  against.
- **Keep `Channel` with explicit revisit criteria** — chosen.

## Decision

Reuse the existing `Channel<T>` + `receiveAsFlow()` pattern for new one-shot
events for now. Mirror the `SoundsViewModel` shape: private mutable `Channel`,
public read-only `Flow`, screen consumes via `LaunchedEffect` /
`repeatOnLifecycle`.

Do not introduce a `SharedFlow` "for events" — that path has more sharp edges
than either of the two patterns above (replay semantics, subscriber
buffering).

## Consequences

- **Revisit when** any of these become true: a real "lost event in background"
  report lands; a new flow needs guaranteed delivery (e.g. payment, irreversible
  state change); the team agrees that the broader codebase should align with
  the official event-as-state guidance and budgets the migration as a single
  pass. At that point, the ADR is superseded and the migration plan covers
  every existing `Channel`-based event.
- New `Channel`-based events should still document delivery tolerance in the
  ViewModel KDoc — "loss-tolerant" or "must-arrive" — so the next person to
  audit knows which way to flip if a delivery bug surfaces.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- The token `MutableSharedFlow` must NOT appear in any production sourceset (`app/src/main`, `commons_android/src/main`, `commons_file/src/main`, `model/src/main`). The check is intentionally permissive — even a kdoc mention triggers it, on the grounds that you should be reading this ADR before introducing the term. If `SharedFlow`-based events are genuinely warranted (replay semantics, ongoing-stream broadcast), supersede this ADR.

## Cross-references

- `CLAUDE.md` § *Project-specific overrides* → "One-shot UI events: `Channel<T>`".
- Canonical implementation: `app/src/main/java/.../ui/SoundsViewModel.kt`
  (`_buttonSavedEvent`).
- Public guidance: https://developer.android.com/topic/architecture/ui-layer/events.
