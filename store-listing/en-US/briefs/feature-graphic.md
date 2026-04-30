# Brief: Feature Graphic 1024×500 (Bomp — en-US)

## Play Store spec
- 1024 × 500 px
- 24-bit PNG (no transparency) or JPG
- Safe zone: 15% margin (≈154 px sides, ≈75 px top/bottom) — Play crops the edges on some surfaces.

## Composition
- **Background:** full-bleed `Ink1000` (`#0B0B0C`).
- **Left side (~50%):** the `bomp-mark.svg` blob in `Acid400`, with three concentric waves also in `Acid400` at 30 / 15 / 8 % opacity. Purpose of the waves: suggest that the audio plays for the owner — not that it's broadcast outwards.
- **Right side (~50%):** the canonical landing tagline on two lines, **`Paper` (`#FAFAF7`) Inter SemiBold 64 px**, anchored just past the wave halo (x≈460) so the eye follows `mark → halo → text`:
    > Voices
    > that matter.
- **No "Bomp" wordmark**: the app name already appears three times in the listing before the user reaches the feature graphic (icon, label below the icon, `title` field). Repeating it here is noise and wastes the promotional real estate.
- No human faces. No phone frames. No third-party app icons.

## Typography
- Declared stack: `Inter, Roboto, system-ui, sans-serif`. Inter is the canonical brand font — it ships zipped at `store-listing/brand/fonts/Inter.zip` and gets installed via the command documented in `CONTRIBUTING.md` § "Store listing". Without Inter installed, `rsvg-convert` falls back to Helvetica/SF Pro and the result drifts from the browser preview.

## Contrast (WCAG)
- Paper ↔ Ink1000 = 17.5:1 (AA+++ ✓) for wordmark and tagline.
- Acid400 ↔ Ink1000 = 13.5:1 (AA+++ ✓) for blob and waves.

## Vector deliverable
`store-listing/en-US/briefs/feature-graphic.svg` — composition ready to open in Inkscape, fine-tune typography, and export to PNG 1024×500.

## Export
```bash
rsvg-convert -w 1024 -h 500 \
  store-listing/en-US/briefs/feature-graphic.svg \
  -o store-listing/en-US/images/feature-graphic-1024x500-en-US.png
```
- Verify the final file does NOT exceed Play's allowed weight (~1 MB is safe). For typography tweaks, open the SVG in Inkscape, adjust, and re-run `rsvg-convert`.
