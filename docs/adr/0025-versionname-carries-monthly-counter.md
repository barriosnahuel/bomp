# ADR 0025 — `versionName` carries the monthly counter (`YYYY.MM.N`)

- **Status:** Accepted
- **Date:** 2026-07-15
- **Amends:** [ADR 0021](0021-calver-versioning.md) — only the `versionName` scheme (`YYYY.MM` → `YYYY.MM.N`). The rest of ADR 0021 — the CalVer rationale, the small monotonic `versionCode`, the forward-only SemVer frontier — stands. Also amends the aside in [ADR 0023](0023-monthly-sequential-release-tags.md) § *Trade-off accepted* that the user-facing `versionName` "keeps ADR 0021's no-counter rationale intact".

## Context

ADR 0021 set `versionName` = `YYYY.MM` (no counter), while [ADR 0023](0023-monthly-sequential-release-tags.md) later gave the tag / release-title / CHANGELOG-header a monthly counter (`vYYYY.MM.N`). That left **two different version strings for one release**: the tag, title, milestone and CHANGELOG header all read `v2026.07.1`, but the string the Bomper sees in Play / "Acerca de" read only `2026.07`.

The split was chosen for false-precision reasons (ADR 0021 driver #2), but it actively misleads. It surfaced at the very first CalVer cut: `v2026.07.1` shipped with `versionName '2026.07'`, and the mismatch read as a mistake rather than a decision — the maintainer flagged it as one. If the split confuses the person who wrote the ADRs, it will confuse a reader correlating an "Acerca de" string with a GitHub release.

## Decision drivers

1. **One release, one version string.** The Bomper reading `2026.07.1` in "Acerca de" can match it 1:1 to the GitHub release, tag, milestone and CHANGELOG header. No mental mapping.
2. **The counter already exists and is already hand-managed.** ADR 0023 introduced `N` and manages it at cut; carrying the same value into `versionName` is ~zero marginal cost — no new ceremony ADR 0021's driver #2 was trying to avoid.
3. **Freshness still reads.** `2026.07.1` still tells the Bomper how fresh the app is (the whole point of CalVer); the `.N` only disambiguates same-month cuts, exactly as it does on the tag.

## Decision

- **`versionName` = `YYYY.MM.N`** — the same cut month + 1-based monthly counter as the tag / release title / CHANGELOG header (e.g. `2026.07.1`; a second July release is `2026.07.2`). It now matches the release identity string everywhere except the `v` prefix (tags keep the `v`, `versionName` cannot — Play rejects a leading non-digit).
- **`versionCode`** is unchanged — the small monotonic integer, +1 per release, Play's true build identity (ADR 0021).
- **The two same-month releases no longer share a `versionName`** — they differ by `.N`, matching how they already differ by tag and `versionCode`. This reverses ADR 0021's "two same-month releases share a `versionName`" clause.

## Enforcement

Same as ADR 0021 / ADR 0023: `CONTRIBUTING.md` § *Versioning* is canonical, human-checked at cut via the pre-release checklist. No grep guard — the scheme is low-frequency, and `versionName` now derives trivially from the tag the cut already picks.

## Revisit criteria

- If `versionName` and the tag ever need to diverge (e.g. a hotfix that re-uses a tag but ships a distinct build) → reconsider; today they are 1:1 by construction.
- Anything that revisits ADR 0021's CalVer decision wholesale (a real compatibility contract appears) supersedes this too.
