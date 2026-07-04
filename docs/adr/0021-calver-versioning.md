# ADR 0021 — Calendar Versioning for the app + GitHub releases

- **Status:** Accepted
- **Date:** 2026-07-04
- **Supersedes:** the implicit Semantic Versioning convention used through v2.3.0 (never recorded as an ADR)

## Context

Through `v2.3.0` the app used Semantic Versioning (`MAJOR.MINOR.PATCH`). SemVer's contract
protects consumers of a public API against breaking changes — a soundboard app for end users has
no such API and no compatibility contract, so `2.3.0` communicates nothing to the Bomper. It is
library ceremony applied to a consumer product. What a user *does* care about is **how fresh the
app is**, which a date conveys directly.

The `versionCode` is a separate, Play-mandated monotonic integer that users never see; it is
unaffected by the `versionName` scheme.

## Decision drivers

1. **The visible version should mean something to a user** — a date reads itself ("2026.07"); a
   SemVer triple does not.
2. **No false precision / no ceremony** — no sequential patch counter to hand-manage.
3. **Reversibility of the `versionCode` choice** — `versionCode` can only ever increase, so any
   scheme that jumps it high (e.g. date-derived `YYYYMMDD` ≈ 20M) is a one-way door. Keep the
   option open.

## Decision

- **`versionName` = `YYYY.MM`** (cut month, e.g. `2026.07`). No `MAJOR.MINOR.PATCH`, no sequential
  counter. Two releases in the same month share a `versionName` — Play allows it; they stay
  distinct by `versionCode`.
- **GitHub tags + release titles + `CHANGELOG.md` headers = `vYYYY.MM.DD`** (`v`-prefixed cut date,
  day precision). Day precision keeps tags unique without a sequential counter; a same-day second
  release disambiguates the **tag only** (`v2026.07.15-2`) at cut.
- **`versionCode` = small monotonic integer, +1 per release, bumped at cut** (not pre-bumped on
  `develop`). `develop` carries the last released code; the release commit bumps it. Deliberately
  **not** date-derived: date-derived is irreversible (can't return to small codes) and can be
  adopted later at any cut (a jump up is always legal), so there is no reason to burn the runway now.
- **Forward-only frontier:** `v2.0.0`…`v2.3.0` stay as the SemVer versions they shipped as, in
  tags / CHANGELOG / history. CalVer governs from the first cut after this ADR onward.

## Enforcement

- Convention documented in `CONTRIBUTING.md` § *Versioning* (canonical) with pointers from
  `CHANGELOG.md`, `CLAUDE.md` § *Changelog*, and `app/build.gradle`. No grep guard — the scheme is
  low-frequency and human-checked at cut via the pre-release checklist.

## Out of scope

- **Deriving `versionCode` from the date or from CI** — viable future option (CI build number, or
  `YYYYMMDD` aligned with CalVer), deferred because it is irreversible and adds build
  non-determinism for a solo, low-cadence project. Revisit if release cadence rises.
- **The backlog planning-name convention** — lives in `../push-me-backlog` (its own `CLAUDE.md`),
  not here. This ADR governs only the shipped app + GitHub releases.

## Revisit criteria

- Release cadence rises enough that manual `versionCode` bumping becomes error-prone → adopt
  CI/date-derived `versionCode`.
- The app ever exposes a real compatibility contract (public API, plugin surface) → reconsider a
  semantic component.
