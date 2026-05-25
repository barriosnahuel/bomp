# Brief: Screenshots (Bomp — en-US)

## Play Store spec
- **Phone:** min 2, **max 8** caps. Aspect ratio 9:16 to 9:20. Deliverables: **1080 × 2400** (9:20, what modern Pixels use).
- **Tablet 7" / 10":** TODO post-launch — out of scope.
- **Do not** frame the UI inside a phone frame.

## Pipeline (automated)

Two reproducible script steps — no longer manual capture + Inkscape:

1. **Capture** — `scripts/capture-store-screenshots.sh`. Seeds a realistic board (`DebugSoundSeeder` + `DebugSeedCorpus`), drives the real app to each scene (bypassing the Vault biometric via `VaultSessionState`), and writes full-screen 1080×2400 PNGs to `real-screenshots/<scene>-<locale>.png`. Because the app reads the **system** locale, it flips the emulator locale + reboots per language; it also sets a screen-lock PIN so the New Bomp private-collections CTA renders polished (not the "no screen lock" warning). Needs a booted, **rootable** emulator (Google APIs image).
2. **Compose** — `scripts/compose-store-screenshots.py`. Builds each hybrid SVG (opaque Ink header strip + localizable headline/subtitle + the capture embedded as base64) and renders it to `images/phone/` with `rsvg-convert`. Per-scene copy lives in the script's `SCENES` dict.

**Hybrid** = opaque Ink1000 header strip (320px, covers the captured status bar + top bar) over the real capture → 100% authentic UI, zero "misrepresent" risk. **Typography** (#2 manifesto, #8 closing) = full-vector, no embedded capture.

## Scenes (8)

| # | Scene | Type | Headline / subtitle |
|---|---|---|---|
| 1 | `01-home` | Hybrid | **Your collection of voices.** / The ones that matter — always close. |
| 2 | `02-manifesto` | Typography | 4 bullets: Yours, first · No signup, no email, no phone number · Saved to the cloud · A hug you can hear |
| 3 | `03-collections` | Hybrid | **By who they're from.** / Family, work, inside jokes — filter in a tap. |
| 4 | `04-search` | Hybrid | **Find any voice, fast.** / Even when your collection gets big. |
| 5 | `05-immersive` | Hybrid | **Just you and the voice.** / Open one and the world goes quiet. |
| 6 | `06-vault` | Hybrid | **Locked, just for you.** / Your Vault opens with your fingerprint. |
| 7 | `07-newbomp` | Hybrid | **Name it. Keep it.** / Drop it into the collections it belongs to. |
| 8 | `08-closing` | Typography | Curatorial hero: "For people who keep moments the way others keep photos." |

**ASO arc:** collection → manifesto → collections → search → listen (immersive) → Vault → save → close. Zero implication of "sending out" — the moment of value is **listening**.

## Regenerate

```bash
./scripts/capture-store-screenshots.sh          # 1. raw captures → real-screenshots/
python3 scripts/compose-store-screenshots.py     # 2. compose → images/phone/
```

The typography cards (#2, #8) are not captured (full-vector); to re-render them:
```bash
rsvg-convert -w 1080 -h 2400 store-listing/en-US/briefs/screenshot-02-manifesto.svg -o store-listing/en-US/images/phone/02-manifesto-en-US.png
rsvg-convert -w 1080 -h 2400 store-listing/en-US/briefs/screenshot-08-closing.svg   -o store-listing/en-US/images/phone/08-closing-en-US.png
```

## Contrast (WCAG)
- Paper ↔ Ink1000 (header strip) = 17.5:1 ✓
- For the captured UI, the pairs are already covered by `AppThemeContrastTest` in the repo.
