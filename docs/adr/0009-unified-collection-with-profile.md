# ADR 0009 — Unified `Collection` entity parametrized by `CollectionProfile`

- **Status:** Accepted
- **Date:** 2026-05-17
- **Supersedes:** —

## Context

Spec v2.4.0 (`backlog/10-v2.4.0-collections-and-vault.md`) replaces two previously
archived feature ideas — the legacy "Baúl" emotional vault (originally targeted at
v2.2.0) and "Smart Collections" (originally v2.4.0) — with a single feature called
**Collections & Vault**.

The two original specs would have shipped:

- **Vault** — biometric-gated, listen-only, immersive UI for a single hardcoded
  collection.
- **Smart Collections** — public, shareable, multi-tag groups with auto-categorization.

Both, on closer reading, are presets of the same thing: "a collection of audios
parametrized by access, metadata, playback UI, shareability, and
auto-categorization." Keeping them separate would have meant two data models,
two repositories, two tagging UIs, and two pieces of code for the same flow.

The unification has a real cost: the Vault, which was originally low-risk and
standalone-shippable in v2.2.0, now ships in v2.4.0 with the rest of the
collections work.

## Decision

A single `Collection` entity lives in `:model` (`Collection.kt`,
`CollectionProfile.kt`). Each collection's `profile` is the combination of five
orthogonal axes (`access`, `requiredMetadata`, `playbackUI`, `shareability`,
`autoCategorize`).

Two canonical presets are exposed as `CollectionProfile.GENERIC_PUBLIC` and
`CollectionProfile.VAULT`. The spec's "Recetas" / Kitchen Mode preset is *not*
shipped in the MVP (see `handoff/2026-05-17-*-decisiones-improvisadas-*.md` for
the deferral); the data model already supports it via the `playbackUI` axis
so it can ship without a migration.

Persistence is a single DataStore Preferences file (`collections.preferences_pb`)
serialized as a JSON-encoded list of `StoredCollection`. The audio↔collection
relation is **N:M** — `StoredCollection.audioIds` carries the link list inline
(no separate junction table), so a single decoded snapshot answers both "what
collections exist" and "which audios belong to each" in one read. The spec § 3.1
closes the original Smart Collections open question (1:1 vs 1:N → N:M).

## Decision drivers

1. **Two duplicate models cost more than one parametrized model.** The two
   archived specs would have shipped near-identical persistence + tagging UI for
   the same conceptual entity. Unifying eliminates duplicate code, duplicate tests,
   and the inevitable per-feature drift between them.

2. **The N:M relation falls out for free** when collections own their
   `audioIds` list. The alternative (separate `AudioCollection` junction
   table) is what classic SQL schemas would prescribe, but DataStore
   Preferences with JSON serialization makes the inline list cheaper to read
   and atomic to mutate.

3. **The five axes already cover known future presets.** Spec § 3.1 — `access`
   covers the Vault gating; `playbackUI` covers Kitchen Mode (deferred);
   `shareability` covers the listen-only restriction on Vault audios;
   `autoCategorize` covers the Gemini-Nano-based Recetas auto-tagging
   (deferred). Adding a new preset means a new combination, not a new entity.

4. **The Vault preset is hardcoded as `CollectionProfile.VAULT`**, so the system
   "Baúl" collection seeded on first read (id `system:baul`) is recognizable by
   id everywhere — repository, UI, analytics. The system flag protects deletion
   but not naming (rename is allowed; the user can call their Baúl whatever they
   want, but the system-seeded one cannot be removed).

## Consequences

- **Audio deletion sweeps stale ids via `CollectionsRepository.forgetAudio`.** Called
  from `SoundsViewModel.confirmDelete` after a user deletes a Bomp. Failure is
  non-fatal-tracked but not blocking; a stale id only manifests as a smaller
  visible audio count, never a crash.
- **Cross-preset interaction is well-defined.** An audio with tags in both a
  public and a private collection stays shareable from the public surface; the
  `listen-only` property operates at the playback site (immersive view), not at
  the audio entity level. Spec § 3.1 closes this as well.
- **One file to back up — instead of two.** Both scopes live in
  `collections.preferences_pb`, which is *included* in the backup XMLs: backup to
  Drive is the floor for every user and covers the whole archive (Collections +
  Vault). The Pro tier is realtime cross-device *sync*, not cloud backup — a
  separate concern that can ship later without touching the model.
  `BackupRulesTest` enforces the inclusion as a regression net.
- **The Kitchen Mode and auto-categorization paths stay decoupled from MVP risk.**
  The data model already supports them, so adding them later is an additive change.

## Revisit if

- **A future feature wants per-axis storage.** If `autoCategorize` ships and
  needs streaming-large keyword indices, the inline `audioIds` list inside the
  collection record might pair badly with frequent appends. Revisit by splitting
  off the audio↔keyword links into their own DataStore.
- **Cloud sync ships.** Realtime cross-device sync (the Pro tier) was explicitly
  deferred (spec § 5); backup to Drive already ships for everyone. A future Pro
  spec adds sync on top of the existing backup posture — the model itself does
  not need to change.
- **A collection profile axis fails to compose with another.** Today all five
  axes are orthogonal in code. If a future preset requires a combination that
  isn't expressible (e.g. "playbackUI=immersive but shareability=bompeable")
  but is meaningful for the user, revisit the closed-enum shape of the axes.

## Alternatives considered

1. **Keep the two original specs.** Two repos, two tagging UIs, two analytics
   surfaces, more migration debt as features evolve.
2. **Split public + private into two repositories** (and two DataStore files).
   Tempting if the backup posture ever differs per scope. Rejected because the
   sound↔collection relation crosses both scopes (an audio can live in both a
   public and a private collection), and splitting forces double bookkeeping
   that is easier to break. The single file is *included* in the backup XMLs
   (both scopes are part of the user's archive); if a future scope needs a
   different backup policy, a second file may make sense.
