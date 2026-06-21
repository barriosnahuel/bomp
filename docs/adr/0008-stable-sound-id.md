# ADR 0008 — Sound identity is a stable internal id, not the display name

- **Status:** Accepted (decision #5 / Option A amended by [ADR 0018](0018-legacy-sounds-schema-migration.md))
- **Date:** 2026-05-15
- **Supersedes:** The "Why `Sound.name` as the cache key" section of [ADR 0007](0007-sound-playback-pause-resume.md) and the name-as-identity weakness it called out. The rest of ADR 0007 — pause/resume semantics, position cache, listener changes — stands; the cache key migrates from `Sound.name` to `Sound.id`.

## Context

`Sound` used its display `name` as the de-facto primary key. `SoundsRepository.save()` upserted by name (last-write-wins); `PlayerControllerImpl.savedSoundPositions`, `SoundsViewModel._pausedProgress`, `_soundDurations`, and `repo.durations` were all keyed by name; `LazyColumn(key = sound.name)` keyed Compose list identity; ~23 identity comparisons did `it.name == sound.name`.

Consequences that surfaced repeatedly:

- **Two Bomps cannot share a name.** Saving a duplicate silently destroys the first record. Captured in `handoff/2026-05-13-2235-sound-stable-id-refactor.md` and prerequisite for the non-blocking duplicate-name hint feature (`handoff/2026-05-13-2235-addbutton-duplicate-name-hint.md`).
- **Rename orphans the caches.** `savedSoundPositions["bell"]` doesn't follow when "bell" is renamed to "doorbell"; the position quietly disappears.
- **Cross-session collisions.** Delete "bell" and create a new "bell": the new sound's first play could `seekTo` the prior bell's stale position. ADR 0007 mitigated this with `forgetSound(sound)` from `deleteSound` — but the structural fix is the one this ADR ships.

The app **has no users yet** — the `StoredSound` JSON schema can be broken without a migration. That window justifies the change now rather than after a painful migration later.

## Decision drivers

1. **Identity decoupled from display.** The name is for humans; identity is for the system. They must be separate concerns.
2. **Stable across installs for bundled sounds.** Reinstalling the app must not invalidate a pinned-bundled flag persisted via the new id.
3. **Minimum-churn migration to call sites.** The convenience constructor `Sound(name, rawRes)` for bundled sounds must keep its signature so `PackagedAudios.java` and `welcomeSticker()` need no edits. The new id is derived inside that constructor.
4. **No regressions in `StateFlow` recomposition.** The VM intentionally re-emits the same logical sound with flipped `isPlaying`/`isPinned`. Identity must not bleed into structural equality (see *Why structural `equals` is preserved* below).
5. **No data migration.** No-users window; old-schema JSON degrades to empty list via `decodeSafely`. **(Amended by [ADR 0018](0018-legacy-sounds-schema-migration.md): the no-users premise proved false — production Crashlytics shows a real user whose pre-id payload degraded to empty, silently wiping their saved audio. A read-time migration now derives the missing id instead of dropping the list.)**

## Decision

`Sound` and `StoredSound` gain a non-nullable `id: String`:

- **Bundled sounds** — id is `"bundled:$rawRes"`, derived inside the `Sound(name, rawRes)` convenience constructor. Stable cross-install because `rawRes` is the R.raw identifier (build-stable across installs from the same APK).
- **Custom sounds** — id is `UUID.randomUUID().toString()`, minted **once at first save** in `AddButtonFeature.saveNewButtonAsync`. Not in `SoundsRepository.save` — that path is also the upsert path and must stay a pure upsert keyed by the id it is given.
- **`StoredSound`** — `id` is required (no default). Persisted in the JSON list under the same `sounds_json` DataStore key. Old-format JSON missing `id` triggers `MissingFieldException → SerializationException`, caught by `decodeSafely` and degraded to an empty list (test in `SoundsRepositoryTest`).

Identity moves to explicit `.id ==` at call sites: repository upsert/delete/rename/pin/duration lookups, `_pausedProgress` / `_soundDurations` / `savedSoundPositions` map keys, `LazyColumn(key = sound.id)`. **Textual / display concerns stay on `name`:** search `contains`, sort `thenBy { it.name.lowercase() }`, the `validateName` length/blank checks, the duplicate-name *hint* (separate scope).

`Sound` becomes `@Parcelize Parcelable` (kotlin-parcelize plugin added to `:model`). `LandingActivity.editIntent` and `AddButtonActivity` reconstruction collapse from four separate string/boolean extras (`EXTRA_EDIT_SOUND_NAME/FILE/FAVORITE/DATE_ADDED`) to a single `EXTRA_EDIT_SOUND` Parcelable extra carrying the full entity, id included.

`ShareFeature.resolveBundledFileForSharing` migrates its cache filename from `sound.name + ".mp3"` to `"bundled_${sound.rawRes}.mp3"` — collision-free now that names aren't unique, and reachable only for bundled sounds.

The two `require(... !in bundledNames(context))` guards in `SoundsRepository.save` and `rename` are **removed**. With id-keyed upsert, a custom sound legitimately sharing a name with a bundled one is no longer an identity collision; same-name coexistence is the precondition the duplicate-name-hint feature relies on. `bundledNames()` itself is deleted.

## Why structural `equals` is preserved

`Sound` stays a `data class` with auto-generated equals/hashCode over all fields, including `id`. We do **not** override to id-only equality.

- `repo.sounds` and `_sounds` are `StateFlow`s with `distinctUntilChanged` semantics: a new emission only propagates if it is `!equals` to the previous.
- `SoundsViewModel` intentionally re-emits the *same logical sound* with flipped `isPlaying` (`onPlayerStart/Stop/Pause`) and `isPinned` (`togglePin`). With **id-only equality** those `.copy(...)` results would be `equals` to the originals → the emission would be swallowed → the play icon would never flip, the pin would never animate. That is a correctness regression, not a perf nuance.
- With **all-fields equality (data class default, unchanged)**: a `.copy(isPlaying = true)` produces a non-equal instance → emission propagates → recomposition happens. `id` rides along as another field; two structurally-different states of the same logical sound stay `!equals`, exactly as today.

The discipline shifts entirely to call sites: *"are these the same logical sound?"* is `a.id == b.id`; *"did this sound's content change?"* stays `a != b`. The Compose `LazyColumn(key = sound.id)` is a separate concern from `equals` — item identity for animations / recomposition stability uses id; structural diff for content changes uses the data-class equals.

## Why `"bundled:$rawRes"` for bundled sounds

- **Cross-install stable.** `R.raw.app_welcome_sticker` is the build-stable resource id; a reinstall of the same APK yields the same integer. A pinned-bundled flag persisted under `"bundled:${R.raw.app_welcome_sticker}"` survives uninstall+reinstall.
- **Derived in one place.** The `Sound(name, rawRes)` convenience constructor synthesizes the id, so `PackagedAudios.java` and `WelcomeSticker.kt` need no edits. The bundled id rule lives in one location and cannot be skewed by a forgetful caller.
- **Unique within the catalogue.** Bundled rawRes values are unique across `PackagedAudios.get()` and the welcome sticker, so the derived ids are unique too — no need for an explicit catalogue key.

If `rawRes` ever becomes non-stable across builds (e.g. an R8 / resource-shrinker pass renumbers raw resources), the scheme breaks — see *Revisit criteria*.

## Why `UUID.randomUUID()` for custom sounds, minted at save time

- **No external state required.** UUID generation is local, deterministic in shape, and collision-free in practice.
- **Minted in `AddButtonFeature.saveNewButtonAsync`, not in `SoundsRepository.save`.** The repository is the upsert path: it must accept an id from the caller and use it as the upsert key, otherwise *every* call site (including resave-with-new-flags) would mint a fresh random id and break upsert. Identity originates at the **creation** site; mutation sites preserve it via `.copy()`.
- **Survives Activity recreation.** Carried through the edit flow via the Parcelable `Sound` extra.
- **No backend reconciliation today.** Reconsider if a cross-device sync surface appears — see *Revisit criteria*.

## Options considered (and rejected)

- **(A) Migrate persisted data instead of breaking the schema.** Read old JSON, mint ids on first read, re-encode. Rejected: the app has no users; migration code is non-trivial, adds a permanent legacy path, and ages worse than a clean break taken now. The window closes the moment we ship.

- **(B) Override `Sound.equals`/`hashCode` to id-only and rely on Compose's separate keying for diffs.** Rejected: traced through `StateFlow.distinctUntilChanged` and the intentional `.copy(isPlaying = ...)` / `.copy(isPinned = ...)` re-emissions in `SoundsViewModel`; id-only equality silently breaks play-icon and pin recomposition. See *Why structural equals is preserved*.

- **(C) Use content-hash ids (`sha256(file bytes)` for custom, `sha256(rawRes payload)` for bundled).** Rejected: an order of magnitude more work at save time, deduplicates real duplicates the user explicitly wants to keep distinct (e.g. two recordings of the same prank), and offers no value over UUID + `"bundled:$rawRes"` for the soundboard use case.

- **(D) Keep the four separate Intent extras and add a 5th `EXTRA_EDIT_SOUND_ID` string.** Less invasive than Parcelable; pulls no plugin into `:model`. Rejected: compounds an already-fragile reconstruction (`AddButtonActivity` currently silently drops `rawRes`, `isPlaying`, `isPinned` — a class of "forgot an extra" bugs that grows with every new field). Parcelable carries the full entity in a single extra; `:model` already hosts `StoredSound` serialization, so a parcelize plugin there is on-theme.

- **(E) Mint UUIDs in `SoundsRepository.save` if the input `Sound` has an empty id.** Rejected: makes `save` impure (read-modify-write with an implicit "if missing, mint" branch), and a re-save path with flag changes would either need to remember the id externally or accidentally mint a new one. Caller responsibility is clearer and tests are simpler.

## Invariants (preserved or strengthened)

- **`StateFlow.distinctUntilChanged` over `Sound` continues to use structural equality.** New emissions of a logically-same-sound-with-flipped-flag still propagate. (See *Why structural equals is preserved*.)
- **Bundled sound ids are deterministic-from-`rawRes`** — no scattered string literals; the derivation lives in the `Sound(name, rawRes)` convenience constructor.
- **Persistence identity is the id.** `SoundsRepository.save/savePin/saveDuration/rename/delete` all key by id. The display name is metadata stored alongside, never the lookup key.
- **`forgetSound(sound)` from ADR 0007 still fires from `deleteSound` unconditionally.** With id keying the staleness window it guards is much narrower (a re-save with the same id would actually want the position; today re-save mints a fresh id), but the call is defense-in-depth.

## Grep invariants (enforced by `scripts/check-adr-invariants.sh`)

Two narrow, structural invariants — the persistence-identity surface is the riskiest place to silently regress:

1. **`StoredSound.kt` declares `val id: String`.** Catches an accidental schema revert.
2. **No name-keyed identity idioms in `SoundsRepository.kt`** — specifically the three patterns that *are* the identity-by-name shape: `associateBy { it.name`, `filterNot { it.name ==`, `firstOrNull { it.name ==`. Textual `name` use (validation, display, search) does not take these shapes and is intentionally untouched.

The invariant is deliberately narrow: it guards the persistence identity, not every `name` reference. A blanket `it.name ==` ban would false-positive on legitimate textual logic.

## Consequences

**Positive:**

- Two Bomps with the same name coexist as different records. Unblocks the non-blocking duplicate-name hint (`handoff/2026-05-13-2235-addbutton-duplicate-name-hint.md`).
- Rename preserves duration / playback position via the stable id.
- Delete-then-recreate-same-name no longer revives a stale playback position (the new sound has a new UUID).
- `ShareFeature` bundled cache is collision-free.
- Intent edit flow carries the full `Sound` — no more "forgot an extra" silent drops.
- `PackagedAudios.java` (Java) needs zero changes — the derivation lives in the Kotlin convenience constructor.

**Negative / accepted costs:**

- One-time schema break with no migration. Acceptable because there are no users yet; the window closes at first release.
- `:model` picks up the `kotlin-parcelize` plugin. Low overhead; the module already hosts `kotlinx-serialization`, so serialization concerns are on-theme there.
- The discipline shifts to call sites: contributors must remember "id for identity, name for display" rather than getting it for free from structural equality.

## Welcome-sticker stub-row orphan (pre-existing, called out for the next ADR)

`WelcomeStickerStore.welcomeSticker(context)` is built fresh on every call and is *never* in `PackagedAudios.get()`. `savePin`/`saveDuration` for the welcome sticker write a `StoredSound(id = "bundled:${R.raw.app_welcome_sticker}", ...)` stub that `mergeWithBundled` cannot surface (neither in `customSounds` nor `bundledSounds`). The stub persists as an orphan: invisible, untouched, never cleaned. **This is pre-existing behavior** (today it's orphaned by name; this ADR keeps it orphaned by id) — surfaced here so the welcome-sticker persistence design can address it cleanly when next revisited.

## Revisit criteria

Open a follow-up ADR if any of the following becomes true:

1. The app gains real users *before* a future `StoredSound` schema change — at that point a real migration is required, no more free breaks.
2. Bundled `rawRes` values become non-stable across builds (resource-shrinking renumbers them, or an R8 pass remaps raw resource ids). The `"bundled:$rawRes"` cross-install stability assumption no longer holds; switch to an explicit catalogue key or a content hash.
3. A sync / cloud-backup surface for custom sounds appears (Saved Games / Drive sync). UUIDs would need server-side reconciliation; consider switching to server-issued ids or pairing UUIDs with an `installation_id` namespace.
4. Welcome-sticker persistence is redesigned (see the orphan note above) and either includes welcome in `PackagedAudios.get()` or stops writing stub rows for it.
