# ADR 0024 — Jetpack Navigation 3 as the in-app navigation backbone

- **Status:** Accepted
- **Date:** 2026-07-05
- **Supersedes:** Amends [ADR 0011](0011-predictive-back-navigation.md) (its revisit
  criterion — "when the Navigation 3 migration lands" — fires with this epic: the manual
  `predictiveBackTransition` machinery is simplified or removed for every surface that becomes a
  Nav3 destination; the convention survives only for non-route overlays). Reaffirms
  [ADR 0002](0002-no-hilt-manual-viewmodel-factory.md) (Nav3 scopes ViewModels without a DI
  framework). Does not touch [ADR 0003](0003-channel-for-one-shot-ui-events.md) (one-shot event
  model unchanged). Informs [ADR 0019](0019-in-app-bomp-recorder.md) (`RecordingActivity` is
  remodeled as a graph destination in sub-spec `003c`; recorder behavior — capture, bounds, draft
  recovery — stands).

## Context

The app has **no `NavHost` today**. Navigation is hand-rolled state in `LandingScreen.kt`:

- **Tabs:** `AppTab { MY_SOUNDS, VAULT, EXPLORE_SOUNDS }` selected via a `StateFlow` in
  `SoundsViewModel`, plus a manual `tabBackStack = mutableStateListOf<AppTab>()` popped by a
  `BackHandler` — rudimentary multi-tab history, not a per-tab stack.
- **Seven overlays** (About, ManageCollections, ImmersiveListen, OnboardingTour, BringFromApps,
  ImportHub bottom sheet, Search) toggled by booleans / `rememberSaveable` state, most wiring
  predictive back manually per ADR 0011 (layering + `clearAndSetSemantics` + per-surface handlers).
- **Three Activities:** `LandingActivity` (launcher + `push-me://open` deep link),
  `AddButtonActivity` (creation/edit hub with 4 entry points, one external via `ACTION_SEND`),
  `RecordingActivity` (recorder, hands off to `AddButtonActivity`).
- **Deep links:** a manual `when (uri.path)` in `LandingActivity.handleDeeplink` with a closed
  allowlist and a fall-back-to-Home security invariant (CLAUDE.md § *Deep link path allowlist*).

That model is at its load limit: back semantics are re-derived per surface, overlay payloads need
hand-written `Saver`s, and every upcoming feature (adaptive layouts spec `001`, trimmer `009`,
smart naming `010`, re-record `011`) would grow the boolean lattice. Meanwhile Jetpack
Navigation 3 reached GA (stable `1.1.x`; `1.1.4` at this ADR's date) with a Compose-first,
owner-held back stack model: predictive back between destinations is automatic, destinations are
plain `@Serializable` keys, and the Scenes API provides the list-detail/two-pane layouts the
adaptive spec needs.

## Decision

Migrate in-app navigation to **Jetpack Navigation 3** (`androidx.navigation3:navigation3-runtime`
+ `navigation3-ui`, stable `1.1.x`). Four sub-decisions:

### D1 — Navigation 3, not Compose Navigation 2 nor third-party

Nav3 gives natively what this codebase wires by hand: automatic predictive back between
destinations (retires most of ADR 0011's manual machinery), a typed back stack the app owns
(`NavBackStack` of `@Serializable` keys — the `tabBackStack` + booleans model, formalized),
`SavedStateHandle`/ViewModel integration, and Scenes for adaptive layouts (spec `001` consumes
them). Compose Navigation 2 is in maintenance orbit — Google's investment moved to Nav3, and
migrating twice (2 → 3) would cost more than adopting 3 directly. Third-party routers
(Decompose, Voyager, Circuit) add a non-platform dependency and idioms without a compensating
capability. Google's official `navigation-3` skill — linked at `.claude/skills/navigation-3/`
(CLAUDE.md § *Sources of truth*) — is the prescribed technical source.

### D2 — Multi-back-stack for the tabs

Each of the three tabs keeps its own stack (recipe `multiple-backstacks`); back pops within the
active tab before crossing tabs. This is the platform-native expectation, Nav3 provides it
directly, and the future multi-step flows (`009`/`011`) land inside a tab's stack without a
second migration. The current single `tabBackStack` list is retired.

### D3 — `SoundsViewModel` stays shared; ephemeral ViewModels go per-destination

`SoundsViewModel` is scoped to the Activity/NavHost level: Search and cross-tab counters share
`SoundsRepository` state, and the tabs are views over the same catalogue — per-destination copies
would fragment it. Screen-lifetime ViewModels (recorder, future trimmer) scope to their
destination via Nav3's ViewModel integration. Both continue through manual `viewModelFactory`
(ADR 0002 reaffirmed — no DI framework needed).

### D4 — Hybrid for the creation Activities

**Internal** creation/edit flows (Edit from My Sounds, Import → name, Record → preview → name)
become graph destinations: continuous predictive back and coherent step-wise back semantics
(name → back → preview → back → record), which Activities can't give without faking it. The
**external** `ACTION_SEND audio/*` entry stays a thin **trampoline Activity** delegating to the
same naming destination/logic: the share-sheet contract (`excludeFromRecents`, back returns to
the source app, no ghost entry in Recents) is a task/manifest-level guarantee that a graph
destination inside Landing's task cannot honor. Full fusion was rejected for exactly that
contract; keeping full Activities for internal flows was rejected because it forfeits the epic's
user-visible payoff. Inbound-URI validation (CLAUDE.md § *Security boundaries*) is untouched —
the same validator runs regardless of who invokes it.

### Deep links: declarative, same allowlist

The `push-me://open` allowlist moves into the graph as declarative mappings (`/home` → Home,
`/explore` → Explore) with the **unknown-path → Home fallback preserved as the `else`** — the
security invariant of CLAUDE.md § *Deep link path allowlist* survives the API change verbatim,
including the extra "Explore without bundled audios → Home" guard.

### Execution: three PRs

This ADR (docs) → `003b` Landing/NavHost core (adds the dependency, migrates tabs + overlays +
deep links) → `003c` creation flows + trampoline. Split because the Landing migration verifies
against existing behavior ("no screen changes") while the creation remodel has its own blast
radius (share-sheet contract, back semantics). Rationale lives in the master spec's
*Descomposición en PRs*.

## Options considered (and rejected)

- **Compose Navigation 2** — mature, but string/route-centric with predictive back and typed
  stacks retrofitted; guaranteed second migration once Nav3 is the default. Rejected: pay the
  migration once.
- **Third-party (Decompose / Voyager / Circuit)** — capable, but off-platform idioms, extra
  dependency risk, and no Scenes/Material-adaptive alignment. Rejected.
- **Single unified back stack (vs D2)** — simpler mental model but loses per-tab history, against
  platform convention, and re-opens when multi-step flows land. Rejected.
- **Full fusion of creation flows (vs D4)** — everything a destination, including the share-sheet
  entry. Rejected: breaks `excludeFromRecents` + back-to-source-app, i.e. Bomp would appear
  "open" in Recents after a share.
- **Do nothing** — the boolean lattice keeps compounding; every future feature re-derives back
  semantics; adaptive spec `001` would build Scenes-like behavior by hand. Rejected.

## Consequences

- `androidx.navigation3` (`navigation3-runtime`, `navigation3-ui`) enters the production
  dependency graph in `003b` (with `app_third_party_notices.txt` updated there — this PR is
  docs-only).
- ADR 0011's manual predictive-back machinery (layering, `clearAndSetSemantics` occlusion,
  per-surface handlers) is removed or simplified for every surface that becomes a destination.
  The `predictiveBackTransition` convention remains valid for non-route overlays (Search stays
  one deliberately — UI layered over a destination, not a place you navigate to).
- Overlay payloads currently carried by hand-written `Saver`s (e.g. the `ManageRequest` sealed
  class) become typed route arguments; the back stack itself is saveable/restorable across
  process death (`rememberNavBackStack` + `@Serializable` keys).
- `VaultSessionState` keeps its process-scoped, non-persisted semantics: Vault access is modeled
  as conditional navigation, and a restored back stack must re-request biometrics.
- Once internal navigation goes through the graph, `startActivity` stops being a legitimate
  internal-navigation mechanism; a grep invariant for it is added to
  `scripts/check-adr-invariants.sh` by `003b`/`003c` (where the code changes), not by this PR.
- `DeepLinkTest` is rewritten against Nav3's API in `003b`; the asserted behavior (allowlist +
  Home fallback) is the invariant, not the API shape.

## Revisit criteria

Reopen this decision when **any** of:

1. **Nav3 stagnates or a needed capability regresses** (e.g. Scenes or predictive back breaks on
   a stable release without a fix path) while Compose Navigation or a successor reactivates.
2. **The trampoline proves unable to preserve the share-sheet contract** in `003c` prototyping —
   D4's hybrid premise fails and the creation-flow split must be redrawn.
3. **A future feature needs navigation semantics Nav3 cannot express** (deep-link shapes, task
   manipulation) forcing parallel manual routing back in — the single-backbone premise breaks.

## Cross-references

- Specs: `../push-me-backlog/backlog/003-nav3-migration.md` (+ sub-specs `003a`–`003c`; decision
  log in its § 3).
- Skill: `.claude/skills/navigation-3/` (linked skill, CLAUDE.md § *Sources of truth*; recipes
  `multiple-backstacks`, `conditional`, `deeplinks-advanced`, `bottomsheet`, `passingarguments`,
  `results-event`, `migration-guide`).
- Navigation 3 guide: https://developer.android.com/guide/navigation/navigation-3
- Release notes: https://developer.android.com/jetpack/androidx/releases/navigation3
