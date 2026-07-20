# ADR 0023 — Monthly-sequential release tags (`vYYYY.MM.N`)

- **Status:** Accepted (the § *Trade-off accepted* aside that `versionName` keeps ADR 0021's no-counter rationale is amended by [ADR 0025](0025-versionname-carries-monthly-counter.md) — `versionName` now also carries the counter, `YYYY.MM.N`. The *release-title* clause of § *Decision* is amended by [ADR 0026](0026-release-title-carries-short-description.md) — the release title/name is now `vYYYY.MM.N - <short description>`; the tag and CHANGELOG header stay bare `vYYYY.MM.N`)
- **Date:** 2026-07-05
- **Amends:** [ADR 0021](0021-calver-versioning.md) — only the tag / release-title / CHANGELOG-header scheme (`vYYYY.MM.DD` → `vYYYY.MM.N`). The rest of ADR 0021 — `versionName` = `YYYY.MM`, small monotonic `versionCode`, the forward-only SemVer frontier — stands unchanged.

## Context

ADR 0021 chose **day-precision** tags (`vYYYY.MM.DD`) explicitly to avoid a sequential counter
("no false precision / no ceremony"): day precision made tags unique on its own, with a `-2`
suffix hack reserved for the rare same-day second release.

That choice has a side effect ADR 0021 did not weigh: **the tag name is unknowable until cut
day**. GitHub milestones are named after the release tag, so under day precision no milestone
can exist while the release's PRs are being opened. CLAUDE.md § *Labels and milestone* had to
compensate with "leave the milestone unset until a release exists", which loses per-release
traceability of PRs at exactly the moment it is cheapest to record — PR creation. Backfilling
milestones after the cut is manual toil that in practice does not happen.

A month-plus-sequential name (`v2026.07.1`) is knowable the moment the month starts (assuming
≥1 release/month), so the milestone can be created up front and assigned on every PR as it is
opened.

## Decision

- **GitHub tags + release titles + `CHANGELOG.md` headers become `vYYYY.MM.N`** — cut month
  plus a 1-based counter within the month (e.g. `v2026.07.1`; a second July release is
  `v2026.07.2`).
- **The month's milestone is created lazily — when its first PR opens — and assigned to every PR
  at creation** (rule in CLAUDE.md § *Labels and milestone*). The cut closes it.
- **A month that ends with no release renames its milestone to the next month** — GitHub keeps
  the associated PRs, so nothing is lost and no empty milestone accumulates.
- **The same-day `-2` disambiguation hack dies** — the counter handles any cadence.
- `versionName` (`YYYY.MM`) and `versionCode` are untouched; two same-month releases still share
  a `versionName` and stay distinct by `versionCode` *and now also by tag*.
- **No migration:** no CalVer cut ever happened (last tag is SemVer `v2.3.0`), so the day-precision
  scheme has zero real tags — changing it now is free.

## Trade-off accepted

This reverses ADR 0021's "no sequential counter" driver *for tags only*. What the counter buys —
a name knowable before the cut, hence milestone-at-creation traceability — outweighs what day
precision bought (the cut date embedded in the name), because git and GitHub already record the
tag's date as metadata; the day in the name was redundant, while the milestone linkage has no
substitute. The user-facing `versionName` keeps ADR 0021's no-counter rationale intact.

## Enforcement

Same as ADR 0021: `CONTRIBUTING.md` § *Versioning* is canonical, human-checked at cut via the
pre-release checklist. No grep guard — the scheme is low-frequency. The milestone-at-creation
rule is enforced socially at PR review (a PR without milestone is visible at a glance).

## Revisit criteria

- If months routinely end with no release, the rename chore recurs monthly → reconsider (e.g.
  quarterly milestones, or dropping milestone traceability and returning to day precision).
- If PR-to-release traceability ever stops mattering (e.g. releases become fully automated,
  one-PR-per-release), the driver disappears — day precision would again be the simpler scheme.
