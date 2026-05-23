# ADR 0010 — Button typology & the `secondary`-role contrast trap

- **Status:** Accepted
- **Date:** 2026-05-19
- **Supersedes:** —

## Context

The Neo-Club palette (`AppTheme.kt`, see `CLAUDE.md` § *Design system*) maps the
M3 `secondary` / `secondaryContainer` roles to the **Ink** scale because their
only job is the always-dark bars (TopAppBar, bottom navigation). They are *not*
a neutral "grey button" tier:

| Role | Light | Dark |
|---|---|---|
| `secondary` | Ink1000 | Ink900 |
| `secondaryContainer` | Ink100 | Ink800 |

M3's stock `FilledTonalButton` and `OutlinedButton` default to the `secondary`
family for their container / accent. On a `surface` background that produces
near-invisible controls in this theme:

- Dark: `secondaryContainer` = Ink800 (`#1C1C1D`) on `surface` = Ink1000
  (`#0B0B0C`) → **~1.3:1**. The button melts into the background.
- Light: Ink100 (`#E5E4DE`) on Paper (`#FAFAF7`) → **~1.1:1**. Same problem,
  just less noticed because authors test in light first.

This bit the Vault "Search your Vault too" CTA: a `FilledTonalButton` with stock
colors was invisible in dark mode. The instinct to "make it a secondary button"
fails because this theme has no light-grey secondary — its only accent is acid.

The codebase had already, implicitly, settled on a small set of button shapes;
this ADR makes that set explicit so future UI work doesn't reach for an
`OutlinedButton` / stock `FilledTonalButton` and reintroduce the trap (or open a
visual tier nobody decodes as hierarchy).

## Decision drivers

1. **Contrast is non-negotiable (WCAG 2.2 AA, `CLAUDE.md` § *Accessibility*).**
   A control a sighted user can't see is an accessibility failure, not a style
   nitpick.
2. **Fewer tiers = a more coherent system.** The difference a user actually
   decodes is filled-primary vs. text vs. chip. An "outlined primary" sitting
   between filled and text adds a tier whose meaning is ambiguous; the border
   reads as decoration, not hierarchy.
3. **Hierarchy must survive both themes.** The only role that adapts for
   *text/accent legibility on surface in both modes* is `primary` (AcidDark in
   light = 7.4:1 on Paper; Acid400 in dark = 15.2:1 on Ink). Text-tier controls
   must use it.

## Options considered

- **Add an "outlined primary" tier for the Vault CTA** — rejected. Opens a
  fourth visual tier for one call-site; the border carries no hierarchy meaning
  the user reads. Affordance can come from the *container* (a bottom action bar
  with a divider) instead of a per-button border.
- **Keep `FilledTonalButton` with stock colors** — rejected. That *is* the bug
  (secondary on surface).
- **Three sanctioned tiers + FAB, text-tier uses `primary`, affordance via
  container when needed** — chosen.

## Decision

Sanctioned button typology for `:app` UI. Use the smallest tier that fits the
action's hierarchy:

1. **Filled primary** — `Button` (or the FAB family) with
   `containerColor = primaryContainer`, `contentColor = onPrimaryContainer`
   (acid fill, ink text). The **single most important action** on a screen:
   Save in a sheet, the screen FAB, "Unlock the Vault" on the Vault gate.
2. **Text** — `TextButton`, color `primary` **inherited** (do not override).
   Secondary actions: "+ New collection" in lists, Cancel in sheets, the
   "Search your Vault too" CTA. Pair with a leading `Icon` (18 dp) for
   create/access affordance. Canonical: `SectionCreateButton`
   (`ManageCollectionsScreen.kt`).
3. **Chip** — `FilterChip` (collection filters) / `AssistChip` (the "+ New"
   chip *inside a chip row*, e.g. New Bomp's assign section). Use a chip only
   when the control lives among other chips; in a vertical list use the Text
   tier instead.

The **FAB family** (`FloatingActionButton`, `ExtendedFloatingActionButton`) is
filled-primary specialized for the screen's floating action.

**Forbidden:** `OutlinedButton`, `ElevatedButton`, and `FilledTonalButton` — the
last regardless of colors, because the typology's filled-primary tier is `Button`.
If a filled button is needed, use `Button` with `primaryContainer` colors.

**The `secondary`-on-`surface` rule:** never use the `secondary` /
`secondaryContainer` roles as the fill or accent of a control rendered on
`surface` / `background`. They are Ink (dark-bar only) and collapse to ~1.1–1.3:1.
When a text-tier control needs visual separation from scrolling content behind
it, give it a **container** (a `Surface` + `HorizontalDivider` "action bar"),
not a border or a tonal fill.

## Consequences

- The Vault search CTA is a `TextButton` (`primary`) inside a divider-topped
  bottom bar — AA in both themes, no new tier. See `SearchOverlay.kt`
  (`VaultUnlockCta`).
- Future authors have a table to pick from and an explicit "don't reach for
  outlined/tonal" so the trap can't silently reappear.
- `GratitudeSection.kt` migrated to `Button` (same `primaryContainer` colors),
  clearing the one recolored-tonal exception so the name-ban has no false positives.
- **Grep-enforced** by `scripts/check-adr-invariants.sh` (CI job `adr-invariants`):
  a name-ban on `OutlinedButton` / `ElevatedButton` / `FilledTonalButton` in the
  component dirs (`feature/`, `ui/`; `ui/theme/` exempt; escape hatch `// button-ok`).
  The earlier "no honest regex" objection assumed a *recolored* tonal button had to
  stay legal; migrating `GratitudeSection.kt` to `Button` removed that case, so the
  simple name-ban is honest. `AppThemeContrastTest` still guards the role *pairs*;
  the name-ban guards *which composable* an author reaches for.

## Cross-references

- `CLAUDE.md` § *Design system* (role table + button typology SSOT) and
  § *Accessibility* (WCAG 2.2 AA contrast thresholds).
- `AppTheme.kt` (role → hex mappings), `AppThemeContrastTest.kt` (pair guards).
- Canonical call-sites: `SectionCreateButton` (`ManageCollectionsScreen.kt`),
  `VaultUnlockCta` (`SearchOverlay.kt`), the Save button in `CollectionSheetHost.kt`.
- M3 button guidance: https://m3.material.io/components/buttons/guidelines.
