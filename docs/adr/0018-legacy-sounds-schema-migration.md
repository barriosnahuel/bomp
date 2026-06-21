# ADR 0018 — Migrate pre-stable-id `sounds_json` payloads instead of degrading to empty

- **Status:** Accepted
- **Date:** 2026-06-21
- **Amends:** [ADR 0008](0008-stable-sound-id.md) decision #5 ("No data migration") and its rejected Option A ("Migrate persisted data instead of breaking the schema"). The rest of ADR 0008 — the id scheme, structural `equals`, call-site identity — stands unchanged.

## Context

ADR 0008 made `StoredSound.id` a required field and explicitly shipped **no migration**, justified by a single premise: *"The app has no users yet."* Old-schema JSON (identity was the display `name`, no `id` field) was expected to degrade to an empty list via `decodeSafely`, and a test encoded that as the desired behavior.

The premise was false. Production Crashlytics (`bomp-prod.firebase_crashlytics`, app 2.1.0) recorded a real user (moto g(20), Android 11) whose persisted payload predates the id field:

```
RuntimeException: Malformed sounds_json payload, recovering with empty list
  caused by MissingFieldException: Field 'id' is required for type with serial
  name '…model.data.StoredSound', but it was missing at path: $[0]
```

`kotlinx.serialization` aborts decoding the **whole list** on the first element missing `id`. `decodeSafely` catches that `SerializationException` and returns `emptyList()` — so one legacy element silently wipes every saved sound the user has, including valid ones, on every read. The user is left without access to their content; the failure recurs on each launch until they re-create everything from scratch.

## Decision

Recover legacy/partially-corrupt data at read time instead of wiping the list. `decodeSafely` works in two tiers:

- **Fast path (the overwhelming majority).** Try a single streaming `decodeFromString`. A clean modern payload decodes with no intermediate JSON tree and is returned directly — new installs pay nothing for the migration.
- **Element-wise recovery (slow path).** Reached only when the fast path throws *or* decodes a blank `id` (conditions only legacy/corrupt data produces). Parse the list to a tree once and decode **each element independently**, so a single bad record can never abort the whole list:
  - For an element missing or blank `id`, derive `id = "custom:<name>"` from the element's pre-0008 name identity (ADR 0007) — a stable, deterministic key reconstructed from the only stable field a legacy record has. This is **not** the production custom-id format (those are random UUIDs minted at save time); it intentionally reuses the old name-as-identity as the recovery key (the test helpers in `TestSounds` happen to use the same `custom:<name>` shape).
  - An element that is a non-object, has no `name` to derive an id from, or still fails strict decode (e.g. a non-string `id`, a missing required `name`) is **dropped**; its siblings survive.
  - Derived ids are **de-duplicated keep-first.** Old-schema names were unique (the pre-0008 name-as-identity upsert invariant), so collisions should not arise from pristine data — but a restored/merged Auto Backup or a hand-edited payload could reintroduce same-name records, and two identical `"custom:<name>"` ids would crash the `id`-keyed Compose lists (`LazyColumn(key = sound.id)`). Keep-first degrades that worst case (a launch crash, *worse* than the wipe this ADR fixes) to dropping the duplicate. A non-blank but meaningless sentinel `id` (e.g. the literal string `"null"`) is treated as a real id and not re-keyed — only null/blank triggers healing.
  - Only a **corrupt root** (not valid JSON, or a non-array) degrades to an empty list with an `onError`.
- **Recovery is observable but does not alarm.** A heal/drop is not an `onError` (that surfaces a Crashlytics non-fatal, reserved for things ops should chase). Instead the slow path invokes an injected `onLegacyRecovery` callback — decoupled from analytics exactly as `onError` is from Crashlytics — which `:app` gates one-shot per install (`markFiredOnce`) into the `legacy_sounds_recovered` analytics event. That keeps the per-read recovery quiet while making the legacy population countable (the retirement criterion below). Healed ids persist to disk on the next `mutate` write-back; until then every read re-heals.

This is exactly ADR 0008's rejected Option A, now warranted because the window it relied on ("the app has no users") has closed.

## Consequences

- **Custom audio is recovered, not lost.** The user's files (`file != null`) reappear with a stable id and remain addressable by rename/delete/pin.
- **Bundled stubs are not fully reconstructable (accepted limitation).** A pre-0008 pin/duration stub on a *bundled* sound stored only its `name`, not its `rawRes`. The migration assigns it `custom:<name>`, which will not match the bundled sound's real `bundled:<rawRes>` id in `mergeWithBundled`, so that single pin/duration is silently dropped (`file == null` stubs are filtered out of the rendered list, so it never surfaces as a dead/unplayable row). It does, however, get re-persisted as a file-less `custom:<name>` record on the next write — harmless dead weight that accumulates one row per legacy bundled stub; never user content. Reconstructing the pin would require a `name → rawRes` reverse lookup against `PackagedAudios`; deliberately out of scope.
- **The legacy path is permanent.** As ADR 0008 warned, migration code ages into the codebase. It is small, pure, and fully unit-tested (`SoundsRepositoryTest`), so the maintenance cost is low.

## Revisit criteria

- If a future schema change adds another required field, the element-wise decode already degrades that to dropping the offending element rather than wiping the list; add a field-specific healer (like the `id` one) when the field is reconstructable.
- If bundled pins/durations are observed lost at meaningful scale, add the `name → rawRes` reverse lookup for bundled stubs (and prune the accumulated file-less rows).
- Once `legacy_sounds_recovered` drops to ~zero new installs/day, the legacy population is effectively migrated and the slow-path healers may be retired behind a one-shot guard like `migrateVisibilityIfNeeded` (ADR 0012).
