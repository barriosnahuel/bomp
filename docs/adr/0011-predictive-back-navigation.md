# ADR 0011 — Predictive back for in-app navigation surfaces

- **Status:** Accepted (with explicit revisit criteria)
- **Date:** 2026-05-21
- **Supersedes:** —
- **Amended:** 2026-07-05 by [ADR 0024](0024-jetpack-navigation-3.md) (§ Revisit criteria — the
  Navigation 3 criterion fired: surfaces migrating to Nav3 destinations drop the manual machinery;
  the convention stays for non-route overlays)

## Context

`targetSdk` 37 opts the app into the `OnBackInvokedDispatcher` by default (the OS
enabled the opt-in from API 35), so the back gesture can be a continuous,
cancelable *preview* — Material 3 predictive back — instead of a binary close.
Android's manual API is `PredictiveBackHandler`, which delivers a
`Flow<BackEventCompat>` with `progress` (0f→1f). References:

- https://developer.android.com/develop/ui/compose/system/predictive-back
- https://developer.android.com/develop/ui/compose/system/predictive-back-progress

The app has **no `NavHost` today**: every surface is an `Activity` or a
manually-managed root Composable, swapped by a flag/state in `LandingScreen`
(`isAboutVisible`, `manageRequest`, `isSearchVisible`). The Jetpack Navigation 3
migration — which would give predictive back *between destinations* for free —
is a separate, much larger effort (`../push-me-backlog/backlog/08-...nav3`),
deliberately scheduled **after** this work. Predictive back is wired manually in
the meantime (backlog `03-predictive-back-navigation`).

## Decision

1. **New visually-discrete navigation surfaces** (overlays, full-screen
   Composables reached by a manual flag/state) use
   `Modifier.predictiveBackTransition` (`ui/PredictiveBackTransition.kt`) instead
   of a plain `BackHandler`. It runs `onBack` **only on gesture completion**,
   drives a `graphicsLayer` (translate + scale-down + fade) from `progress`, and
   degrades to an instant close when the system "Remove animations" setting is on
   (`rememberReduceMotionEnabled`). On API < 33 it falls back to a binary back —
   no regression.

2. **Navigation must be built so the gesture can reveal what is behind.** For the
   preview to show the destination, that destination has to be **composed behind**
   the surface being dismissed (*layering*): render the base screen always + the
   surface as an **opaque overlay** on top (as `SearchOverlay`, `AboutScreen` and
   `ManageCollectionsScreen` do in `LandingScreen`). An exclusive `when` that
   *removes* the base from composition makes the gesture reveal the bare window
   background (near-black) — that is the bug fixed in PR #1171. While an overlay
   is open, **clear the occluded base's semantics** (`Modifier.clearAndSetSemantics {}`)
   so TalkBack and UI tests neither reach nor double-match nodes hidden behind it
   (drawing is untouched, so the reveal still works visually).

3. **Back-swallowers keep a plain `BackHandler`.** A surface whose contract is to
   *consume* the back (e.g. `SaveSuccessOverlay` while its success animation runs)
   must not animate out — `BackHandler { /* swallow */ }` stays correct.

4. **The `LandingScreen` tab-pop stays a binary `BackHandler`.** Driving it with
   the gesture needs an `AnimatedContent` mediating tab content that the current
   `when`-based body does not have; adding it is out of scope here.

This is a **documented convention, not a CI-grep invariant** — `BackHandler` is
legitimately used by swallowers, so a blanket grep guard would over-match.

## Revisit criteria

When the **Navigation 3 migration (backlog `003-nav3-migration.md`)** lands, predictive back
between destinations becomes automatic — decided by [ADR 0024](0024-jetpack-navigation-3.md);
this criterion fired. At that point the manual
`predictiveBackTransition`, the layering, the `clearAndSetSemantics` occlusion,
and the per-surface handlers in `SearchOverlay` / `AboutScreen` /
`ManageCollectionsScreen` are simplified or removed. Re-evaluate this ADR then.

## Consequences

- New full-screen / overlay navigation surfaces inherit a consistent,
  OS-native-feeling back gesture from day one.
- The base screen stays composed (and its state retained) behind an open
  sub-screen — slightly more work, but it is occluded and its semantics are
  cleared, so there is no visual, input, or a11y change at rest.
- Result-level coverage (back closes the surface; the occluded base is absent
  from the semantics tree) lives in the local instrumented suite (ADR 0001),
  which runs with animations disabled — exercising the reduce-motion branch.
  The gesture animation itself is verified on-device, not in automated tests.
