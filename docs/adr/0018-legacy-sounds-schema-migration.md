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

Add a **read-time migration** in front of strict decoding. `decodeSafely` parses the raw payload to a `JsonElement`, runs `migrateLegacySchema`, then strict-decodes the healed tree:

- For each array element missing a usable `id`, derive `id = "custom:<name>"` from the element's pre-0008 name identity (ADR 0007) — a stable, deterministic key reconstructed from the only stable field a legacy record has. This is **not** the production custom-id format (those are random UUIDs minted at save time); it intentionally reuses the old name-as-identity as the recovery key (the test helpers in `TestSounds` happen to use the same `custom:<name>` shape). Old-schema names were unique (the pre-0008 name-as-identity invariant), so derived ids do not collide with each other, with new UUID-minted custom ids, or with `bundled:<rawRes>` ids.
- An element missing **both** `id` and `name` is unrecoverable and is dropped; its siblings survive.
- A non-array root is passed through untouched for strict decode to accept or reject.
- Migration is **silent** (no `onError`): a successful recovery is expected behavior, not a non-fatal to investigate (per the error-tracking contract — `Tracker.track` is reserved for things ops should chase). Healed ids persist to disk on the next `mutate` write-back; until then every read re-heals.

This is exactly ADR 0008's rejected Option A, now warranted because the window it relied on ("the app has no users") has closed.

## Consequences

- **Custom audio is recovered, not lost.** The user's files (`file != null`) reappear with a stable id and remain addressable by rename/delete/pin.
- **Bundled stubs are not fully reconstructable (accepted limitation).** A pre-0008 pin/duration stub on a *bundled* sound stored only its `name`, not its `rawRes`. The migration assigns it `custom:<name>`, which will not match the bundled sound's real `bundled:<rawRes>` id in `mergeWithBundled`, so that single pin/duration is silently dropped. This is a strict improvement over the prior behavior (which dropped everything) and affects only re-creatable bundled flags, never user content. Reconstructing them would require a `name → rawRes` reverse lookup against `PackagedAudios`; deliberately out of scope.
- **The legacy path is permanent.** As ADR 0008 warned, migration code ages into the codebase. It is small, pure, and fully unit-tested (`SoundsRepositoryTest`), so the maintenance cost is low.

## Revisit criteria

- If a future schema change adds another required field, extend `migrateLegacySchema` (or introduce a versioned payload envelope) rather than catching-and-wiping.
- If bundled pins/durations are observed lost at meaningful scale, add the `name → rawRes` reverse lookup for bundled stubs.
- If the legacy population reaches zero (verifiable once healed ids have persisted across the install base), the migration may be retired behind a one-shot guard like `migrateVisibilityIfNeeded` (ADR 0012).
