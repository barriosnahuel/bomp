# Bomp — Store Listing

Assets and copy for the Google Play listing, organized by locale.

## Structure

```
store-listing/
├── README.md                       # this file
├── brand/
│   ├── bomp-mark.svg               # vector master of the brand mark (organic blob) — used on the web
│   ├── launcher-fallback.svg       # source for mipmap-*/app_ic_launcher.png (Android < 8)
│   └── fonts/
│       └── Inter.zip               # zipped brand font (SIL OFL, install steps in CONTRIBUTING.md)
├── real-screenshots/               # native 1080×2400 PNGs captured on the Pixel emulator
├── es-AR/                          # first locale shipped
│   ├── title.txt                   # ≤30 chars
│   ├── short_description.txt       # ≤80 chars
│   ├── full_description.txt        # ≤4000 chars
│   ├── changelog-6.txt             # ≤500 chars; matches the current versionCode
│   ├── briefs/
│   │   ├── icon.md                 # 512×512 icon spec
│   │   ├── icon-512.svg            # vector composition of the Play Store icon
│   │   ├── feature-graphic.md      # 1024×500 feature graphic spec
│   │   ├── feature-graphic.svg     # vector composition ready for export
│   │   ├── screenshots.md          # hybrid workflow spec (real PNG + SVG header)
│   │   ├── screenshot-01-home.svg      # hybrid SVG — Home / UI hero
│   │   ├── screenshot-02-manifesto.svg # hybrid SVG — brand manifesto
│   │   ├── screenshot-03-search.svg    # hybrid SVG — Search overlay
│   │   ├── screenshot-04-playing.svg   # hybrid SVG — playing
│   │   ├── screenshot-05-closing.svg   # hybrid SVG — emotional close
│   │   └── preview-video.md        # optional video script
│   └── images/                     # deliverable PNGs (generated with rsvg-convert; see CONTRIBUTING.md § "Store listing")
│       ├── icon-512-<locale>.png
│       ├── feature-graphic-1024x500-<locale>.png
│       ├── phone/                  # 01-home, 02-manifesto, 03-search, 04-playing, 05-closing — *-<locale>.png
│       ├── tablet-7/               # empty — TODO post-launch
│       └── tablet-10/              # empty — TODO post-launch
├── en-US/                          # same tree as es-AR; copy and headlines in English
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── changelog-6.txt
│   ├── briefs/                     # icon-512.svg + feature-graphic.svg + 5 screenshot-*.svg + .md briefs
│   └── images/                     # same deliverable PNGs as es-AR, re-rendered from their English SVGs
└── (future) es-419/, es-ES/, pt-BR/
```

## Positioning

Brand-DNA positioning charter, in es-AR (the source locale — embedded as a locale example):

> **El Bomp es tuyo, primero.** Bomp es una colección personal de voces que te importan. Compartir existe — es derivado — pero el audio primero te tiene que servir a vos.

Before touching copy in any locale, read `../push-me-backlog/docs/brand-dna.md` §5.

Rules that follow from this positioning:

- **Zero "audio stickers" in official copy** (listing, ads, video). If a user makes the analogy spontaneously, fine — we don't use it as a tagline.
- **Argentine voseo** in es-AR (`apodá`, `guardá`, `bompeá`, `tocá`). Tuteo for es-419 / es-ES.
- **Manifesto invariant:** _"Un audio de los tuyos no es un mensaje, es un abrazo que se escucha."_ — closes every full description.
- **Glossary invariants:** Bomper / Bompear / Bompeable. Definitions track brand-dna §4.

## How to upload to Play Console

1. Log in to Play Console → Bomp app → Store presence → Main store listing.
2. For each supported language:
   - **Title:** copy the contents of `<locale>/title.txt`.
   - **Short description:** copy `<locale>/short_description.txt`.
   - **Full description:** copy `<locale>/full_description.txt`.
   - **What's new:** copy `<locale>/changelog-<versionCode>.txt`.
3. Upload the images from `<locale>/images/`:
   - `icon-512-<locale>.png` → "App icon".
   - `feature-graphic-1024x500-<locale>.png` → "Feature graphic".
   - `phone/*-<locale>.png` → "Phone screenshots".
   - `tablet-7/*-<locale>.png` → "7-inch tablet screenshots".
   - `tablet-10/*-<locale>.png` → "10-inch tablet screenshots".

> The `<locale>` suffix is required so the Play console can distinguish files across languages (it uses the filename as the visible label on upload). Use `es-AR`, `en-US`, etc. — same identifier as the parent directory.
4. Submit for review.

Tip: do an internal dry run before publishing — Play supports a private preview of the listing.

## How to add a new locale

1. Clone the `es-AR/` tree into `<new-locale>/`.
2. Translate each `.txt` respecting the positioning rules above. For Spanish ↔ other languages, the full dictionary lives at `../push-me-ghpages/assets/js/i18n.js` and serves as a lexical source.
3. Re-export the PNGs (icon + feature graphic + screenshots) if the on-image copy changes.
4. PR.
