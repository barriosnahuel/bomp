# Brief: Icon 512×512 (Bomp — en-US)

## Play Store spec
- 512 × 512 px
- 32-bit PNG
- ≤ 1024 KB
- Opaque background (no transparency)

## Composition

- **Background:** full-bleed `Acid400` (`#D7FF3A`).
- **Foreground:** Ink1000 (`#0B0B0C`) play triangle centered in the viewport, occupying ~33% of the width inside the inner safe zone (66 dp circle). ViewBox 108 aligned with the adaptive icon foreground (`app/src/main/res/drawable/app_ic_launcher_foreground.xml`) — same exact geometry between launcher and Play Store: path `M40,34 L40,75 L76,54 Z`.
- No blob. No text. No self-cast shadows (Play applies its own).

## Why Acid is the container (and not Ink + blob)

The canonical brand mark (the organic blob in `bomp-mark.svg`) **works on surfaces with no system mask**: web favicon, `.l-wordmark__mark` on ghpages, the 1024×500 feature graphic. There the irregular border-radius draws the silhouette directly on the background.

On launcher (mask circle/squircle/teardrop depending on the OEM) and on Play Store (rounded square), the system **imposes its own container**, and the asymmetry of the blob ends up fighting the corners of the mask. Readings we saw in preview:

- **Play Console editor:** the irregular shape stuffed into a rounded square reads as "an awkward stain inside a square", not as a brand mark.
- **Pixel launcher:** the circle mask crops the blob into a near-perfect circle, killing the organic silhouette that IS the mark.

Solution: the brand color **is** the container. Full-bleed Acid400 lets the system crop whatever it wants — we always see a solid mask in the brand color with the play triangle inside. Surface-divergent brand: the blob lives on the web; the icon is a compression.

## Contrast (WCAG)
- Ink1000 ↔ Acid400 = 13.5:1 (AA+++ ✓)

## Source SVG and export

Vector master: `icon-512.svg` (in this same directory). Export command:

```bash
rsvg-convert -w 512 -h 512 \
  store-listing/en-US/briefs/icon-512.svg \
  -o store-listing/en-US/images/icon-512-en-US.png
```

The same viewBox 108 + path is used in `store-listing/brand/launcher-fallback.svg` for the Android < 8 raster fallbacks (`app/src/main/res/mipmap-*dpi/app_ic_launcher.png`). Any future change to the icon must be replicated in both source SVGs to keep Play Store and launcher in sync.
