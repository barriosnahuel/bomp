# ADR 0012 — Explicit `isVisibleInMySounds` flag for the My Sounds botonera

- **Status:** Accepted
- **Date:** 2026-05-23
- **Supersedes:** —
- **Amends:** `push-me-backlog/done-specs/v2.1.0-07-collections-and-vault.md` §3.1, §6

## Context

A Bomp's presence in the **My Sounds "Todo"** botonera used to be *derived*: a Sound showed
there unless it was "Vault-only", i.e. tagged to ≥1 private collection and 0 public collections
(`inPrivate - inPublic`, in `SoundsViewModel.privateOnlyAudioIds()`).

That derivation conflated two intents the user cannot tell apart:

1. **An intimate audio born in the Vault** — should stay hidden from the botonera. Correct.
2. **A default/already-public Bomp the user *also* files into a private collection** — under the
   N:M model (§6 of the spec, "audio en pública Y privada: permitido") this should be allowed to
   live in **both** places. But because "public presence" for a default Bomp was *implicit*
   (absence of a private tag), adding any private tag silently flipped it to Vault-only and
   **removed it from My Sounds with no visible control ever changing**. Two Bomps that looked
   identical in the botonera behaved differently depending on whether their public-ness was
   implicit (default, no collection) or explicit (member of a public collection) — an invisible
   asymmetry, and a loss of the multi-tag promise for default Bomps.

The UX fix (designed in the `Assign Flow` handoff) makes botonera presence an **explicit, user-controlled
state**, surfaced as a "Visible en Mis Sonidos" switch in the assign sheet.

## Decision

Add `Sound.isVisibleInMySounds: Boolean` (persisted in `StoredSound`, default `true`). It is the
**single input** to the My Sounds "Todo" surface:

```
shown in "Todo"  ⟺  isVisibleInMySounds == true   (and not bundled)
```

- **Adding a private tag never changes the flag** → a Bomp can be visible in My Sounds *and* live
  in the Vault at the same time (the additive model, non-destructive default).
- A Bomp **born only in the Vault** is the only born-`false` case (no such creation path exists in
  the app today; the assign-sheet switch covers "move an existing public Bomp fully into the Vault").
- **Transactional sheet:** the assign sheet **stages** collection + visibility edits and commits
  them in one transaction on "Listo"; **backing out discards** everything. The coach / "moved to
  Vault" feedback fire on apply, not on close. This mirrors the New Bomp assign section (which
  stages and commits at Save) and makes "Listo" a real confirmation rather than a live free-for-all.
- **Anti-orphan:** turning the switch OFF requires the Bomp to be (staged) in ≥1 collection —
  **public OR private** (`isReachableOutsideMySounds`). A public member is reachable via its filter
  chip; a Vault member via the Vault. "Reachable somewhere", NOT "in the Vault" — a Bomp filed only
  publicly can be hidden from "Todo" and still found via its chip. Enforced twice: a UI gate (switch
  stays **always enabled**; an invalid turn-off is rejected with a reject haptic + a transient inline
  hint, taught at the moment of intent) and a safety net in `applyAssignment` (coerces visible back
  on if the audio is in zero collections), so a Bomp can never end up reachable from nowhere.

### Public-collection chips are an independent axis

Selecting a public collection chip filters **by membership over the full catalog** — it surfaces a
member even when its `isVisibleInMySounds` is `false`. "Todo" respects the flag; the chips respect
membership. A Bomp therefore never disappears from its *own* collection's filter. (`mySoundsProjection`.)

### Privacy carve-out — the search/Vault gate stays membership-based (the important one)

`isVisibleInMySounds` means "appears in the botonera", **not** "is public". The two gates that
enforce **Vault privacy** must NOT switch to the flag, or they would leak:

- **Search with the Vault locked** (`recomputeSearchResults` → `collectionsAudioIdsSnapshot`): a
  cross-tagged Bomp (private + visible-in-My-Sounds) must still be hidden from search until the
  user authenticates. If search keyed off the flag, a Vault audio kept visible in the botonera
  would surface in search to anyone holding the phone.
- **The Vault tab** (`recomputeVaultAudios`): membership in private collections, orthogonal to the
  flag.

Both remain derived from collection membership. Only the My Sounds "Todo" projection moved to the
explicit flag. This carve-out is the conscious resolution of a conflict the design handoff could
not see (it only covered the assign sheet, not search/Vault).

### Migration

One-time, guarded (`SoundsRepository.migrateVisibilityIfNeeded`, key `visibility_migrated_v1`):
existing audios that were Vault-only under the old derived rule (`privateOnlyAudioIds()`) are
seeded to `isVisibleInMySounds = false`; everything else keeps the `true` default. `encodeDefaults =
false` keeps the default off disk, so payloads written before the field decode as `true`. Runs
before the first `loadSounds` so a pre-existing Vault-only audio never flashes into the botonera.

### Coach + feedback

- **Dual-home coach** (`DualHomeCoachStore`, backed up): the first time a user *applies* an
  assignment ("Listo") leaving a visible Bomp that is also in a private collection, a one-shot
  snackbar teaches the additive model. Never repeats.
- **"Moved to the Vault" undo**: applying with the switch turned OFF (Bomp stays private) shows a
  snackbar whose Undo re-enables visibility.

## Consequences

- `privateOnlyAudioIds()` is now used **only** by the migration seed (the pre-flag rule). The live
  My Sounds projection no longer derives from it.
- A new persisted boolean + a new DataStore (`dual-home-coach`) are backed up (`BackupRulesTest`).
- New analytics `visibility_toggle`.
- **Revisit when**: analytics show confusion (e.g. high undo ratio on the move-to-Vault snackbar,
  or users repeatedly toggling the switch), or a Vault-origin creation path ships and needs the
  born-`false` default wired through the Add flow. If the search-privacy carve-out ever needs to
  track the flag instead of membership, that is a privacy-sensitive change and supersedes this ADR.

## Invariants

No new `check-adr-invariants.sh` grep (this is a feature decision, not a banned-token override).
The privacy carve-out is protected by tests (`SoundsViewModelSearchTest` Vault-lock cases) and the
KDoc on `recomputeSearchResults` / `privateOnlyAudioIds`, not by a grep.

## Cross-references

- Domain model: `model/.../Sound.kt`, `model/.../data/StoredSound.kt`, `SoundsRepository`
  (`saveVisibility`, `migrateVisibilityIfNeeded`).
- Projection: `SoundsViewModel.mySoundsProjection`; UI: `feature/collections/AssignCollectionSheet.kt`
  (`VisibleInMySoundsRow`), `DualHomeCoachStore`.
- Design handoff: `Assign Flow` (Variante A — switch). Spec amended: v2.1.0-07 §3.1, §6.
