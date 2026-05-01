# Brief: Screenshots (Bomp — en-US)

## Play Store spec
- **Phone:** at least 4 caps. Aspect ratio 9:16 to 9:20. Current deliverables are **1080 × 2400** (9:20 — what modern Pixels use). Play accepts the historic 9:16 minimum.
- **Tablet 7":** 1 cap. 1024 × 600 px or equivalent 16:9. **TODO post-launch — out of scope for the first release.**
- **Tablet 10":** 1 cap. 2560 × 1600 px or equivalent. **TODO post-launch — out of scope for the first release.**
- **Do not frame the UI inside a phone frame.**

## Workflow: 3 hybrid (PNG + SVG header) + 2 typography full-vector

5 SVGs in `briefs/screenshot-0X-*.svg`. Two formats:

**Hybrid (#1, #3, #4 — UI proof):**
- **Marketing header strip** in vector Ink1000 (320–380 px depending on headline length) at the top, with headline + subtitle. The copy stays editable as text, easy to localize.
- **Real PNG** of the app (`real-screenshots/Screenshot_*.png`, 1080×2400) **embedded as base64** inside the SVG (`<image href="data:image/png;base64,...">`). Required because `rsvg-convert` blocks external `file://` references for security.
- The opaque header covers the captured status bar + topbar, leaving the real cards + bottom nav visible. 100% authentic UI, zero "misrepresent" risk for Play.

**Typography full-vector (#2, #5 — manifesto + close):**
- No embedded PNG, all Ink1000 + Paper text + Acid accent. Designed for ASO impact (#2) and emotional close (#5).

Final canvas: **1080 × 2400** (9:20). Slot order optimized for ASO — see § "Narrative arc and ASO" below.

| # | SVG file | Type | Content | Subtitle / tagline |
|---|---|---|---|---|
| 1 | `screenshot-01-home.svg` | Hybrid | `Screenshot_20260428_225250.png` — Home idle (5 cards) — **Your collection of voices.** | The ones that matter — always close. |
| 2 | `screenshot-02-manifesto.svg` | Typography | 4 dense bullets: **Yours, first.** / **No signup, no email, no phone number.** / **Saved to the cloud.** / **A hug you can hear.** | Subtle Acid blob brand-anchor at the bottom. No textual header — the listing context already identifies the app. |
| 3 | `screenshot-03-search.svg` | Hybrid | `Screenshot_20260428_225317.png` — Search overlay with query "ris" filtering 2 idle results — **Find any voice, / in a second.** | Even when your collection gets big. |
| 4 | `screenshot-04-playing.svg` | Hybrid | `Screenshot_20260428_225339.png` — Search overlay with "Risa de mi vieja" playing (pause + halo + Acid slider) — **Tap. Play. Done.** | No waiting. No loading screens. |
| 5 | `screenshot-05-closing.svg` | Typography | Single curatorial hero: **"For people who keep moments the way others keep photos."** | Acid blob brand-anchor at the bottom (same treatment as card 02). |

### Narrative arc and ASO

`your collection → manifesto → find → activate → close`.

Zero implication of "send out" — the moment of value is **listening**, not sharing.

ASO: slot #2 (search-results carousel, high visibility) carries the manifesto with 4 keyword-dense bullets ("no signup", "saved to the cloud") + brand positioning ("yours, first") + poetic invocation ("a hug you can hear"). Slot #5 closes with a single curatorial hero — defines the audience ("people who keep moments the way others keep photos") with the same visual weight as the manifesto. Both typography cards (02 and 05) carry the same Acid blob anchor at the bottom for consistent brand presence.

> Note: the embedded screenshots show the in-app sticker names exactly as captured on the emulator (Spanish, e.g. "Risa de mi vieja"). Sticker names are user-authored and stay in the user's own language — they are not localized in marketing assets. Re-capturing with an English-locale corpus is a future option if Play feedback flags it.

### Re-render final PNGs

The SVGs are already composed. To regenerate the PNG deliverables (`images/phone/0X-*.png`) run:

```bash
for n in 01-home 02-manifesto 03-search 04-playing 05-closing; do
  rsvg-convert -w 1080 -h 2400 \
    store-listing/en-US/briefs/screenshot-$n.svg \
    -o store-listing/en-US/images/phone/$n-en-US.png
done
```

Note: cards #2 and #5 are typography full-vector (no embedded PNG). Cards #1, #3, #4 are hybrid (SVG header + real PNG base64). The command applies to all five equally — `rsvg-convert` handles each case unchanged.

### Re-capture PNGs when the UI changes

When the in-app UI changes (palette, padding, card, etc.), the 3 captures must be re-taken and the SVGs re-composed (the base64 is embedded):

1. Design QA build with the DB seeded with the canonical names from the brief.
2. Boot the Pixel emulator with the device in dark mode (`adb shell "cmd uimode night yes"`).
3. Capture the 3 screens with `adb exec-out screencap -p > Screenshot_xxx.png` (native 1080×2400).
4. Replace the files in `real-screenshots/`.
5. Re-compose the SVGs by re-embedding the PNGs as base64 (`base64 < Screenshot_xxx.png` and replace the `data:image/png;base64,...` block inside the corresponding SVG).
6. Re-render the deliverables with the `rsvg-convert` command above.

The SVG headline stays intact — only the `<image href="data:...">` block changes.

### Sticker names captured in screenshot #1 (verbatim from the seeded emulator, Spanish)
- `Risa de mi vieja`
- `¡Che, capo!`
- `Llegué`
- `La frase del jefe`
- `Mamá dice qué`
- `Risa de Pedro`
- `Buen día, amor`
- `Volvé pronto`

These are user-authored placeholder data shown only to illustrate the home list density. They are not localized in marketing.

## Contrast (WCAG)
- Paper ↔ Ink1000 (header strip) = 17.5:1 ✓
- For elements of the captured UI, the pairs are already covered by `AppThemeContrastTest` in the repo.
