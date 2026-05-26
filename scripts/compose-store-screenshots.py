#!/usr/bin/env python3
# Composes the hybrid store screenshots: an opaque Ink marketing header strip (localizable headline
# + subtitle) over the real device capture (embedded as base64), rendered to the final 1080×2400
# PNG deliverables with rsvg-convert. The header covers the captured status bar + app top bar; the
# real cards + bottom nav stay visible — same hybrid approach as the existing store SVGs. The Vault
# scene also gets a translucent fingerprint overlay so the biometric gate reads at a glance.
#
# Usage:
#   scripts/compose-store-screenshots.py                # all hybrid scenes, both locales
#   scripts/compose-store-screenshots.py 02-collections # one scene
import base64
import pathlib
import subprocess
import sys
from xml.sax.saxutils import escape

ROOT = pathlib.Path(__file__).resolve().parent.parent / "store-listing"
RAW = ROOT / "real-screenshots"
LOCALES = ("es-AR", "en-US")

# scene -> { locale: (headline, subtitle) }. Headlines stay short enough for one line at 78px
# (~21 chars); detail moves to the 38px subtitle. Brand-DNA: affective, voseo es-AR, no anti-tags,
# the Vault framed as a fingerprint access gate (no encryption over-claim). No "filter"/"filtrar" —
# people search/find or want things in their place, not the technical verb.
SCENES = {
    "01-home": {
        "es-AR": ("Las voces que importan.", "La risa de tu vieja, el audio del amigo."),
        "en-US": ("The voices that matter.", "Mom's laugh, a friend's voice note."),
    },
    "02-collections": {
        "es-AR": ("Cada voz en su lugar.", "Familia, laburo, el grupo de siempre."),
        "en-US": ("All in its place.", "Family, work, the group chat."),
    },
    "03-vault": {
        "es-AR": ("Solo vos entrás.", "El Baúl, detrás de tu huella."),
        "en-US": ("Only you get in.", "The Vault, behind your fingerprint."),
    },
    "04-immersive": {
        "es-AR": ("Vos y la voz, nada más.", "Abrís una y el mundo se calla."),
        "en-US": ("Just you and the voice.", "Open one and the world goes quiet."),
    },
}

# A "show, don't tell" fingerprint overlay for the Vault scene: a translucent scrim dims the (still
# visible) vault content, with concentric Acid ridges + a hint reading the biometric gate at a
# glance. Per locale for the hint line.
VAULT_HINT = {"es-AR": "Tocá para entrar", "en-US": "Touch to unlock"}
FINGERPRINT = (
    '<g fill="none" stroke="#D7FF3A" stroke-width="18" stroke-linecap="round">'
    '<path d="M 508 1006 A 34 38 0 1 1 572 1006"/>'
    '<path d="M 484 1026 A 58 64 0 1 1 596 1026"/>'
    '<path d="M 458 1046 A 86 94 0 1 1 622 1046"/>'
    '<path d="M 430 1066 A 116 126 0 1 1 650 1066"/>'
    '<path d="M 400 1086 A 148 160 0 1 1 680 1086"/>'
    "</g>"
)


def overlay_for(scene: str, locale: str) -> str:
    if scene != "03-vault":
        return ""
    return (
        '<rect x="0" y="320" width="1080" height="2080" fill="#0B0B0C" opacity="0.62"/>'
        + FINGERPRINT
        + f'<text x="540" y="1300" text-anchor="middle" class="hint">{escape(VAULT_HINT[locale])}</text>'
    )


SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 2400" width="1080" height="2400">
  <defs>
    <style>
      .header-h1 {{ font-family: Inter, Roboto, system-ui, sans-serif; font-weight: 700; font-size: 78px; fill: #FAFAF7; letter-spacing: -1.5px; }}
      .header-h2 {{ font-family: Inter, Roboto, system-ui, sans-serif; font-weight: 400; font-size: 38px; fill: #FAFAF7; opacity: 0.85; }}
      .hint {{ font-family: Inter, Roboto, system-ui, sans-serif; font-weight: 600; font-size: 44px; fill: #FAFAF7; opacity: 0.92; letter-spacing: 1px; }}
    </style>
  </defs>
  <image x="0" y="{img_y}" width="1080" height="2400" preserveAspectRatio="xMidYMin slice" href="data:image/png;base64,{b64}"/>
  {overlay}
  <rect x="0" y="0" width="1080" height="320" fill="#0B0B0C"/>
  <text x="64" y="172" class="header-h1">{h1}</text>
  <text x="64" y="244" class="header-h2">{h2}</text>
</svg>
"""


# Per-scene downward shift (px) of the embedded capture, for a screen whose content sits too high
# under the marketing header. The slice revealed at the seam is the app top bar's solid Ink bottom
# (#0B0B0C, identical to the strip) below its title + back arrow, so it blends seamlessly. Keep
# ≤ ~70 so the chrome (title text / back arrow) stays hidden behind the 320px strip.
SCENE_IMAGE_SHIFT = {"02-collections": 64}


def compose(scene: str, locale: str) -> None:
    headline, subtitle = SCENES[scene][locale]
    raw_png = RAW / f"{scene}-{locale}.png"
    if not raw_png.exists():
        print(f"  ! missing capture {raw_png.name}, skipping")
        return
    b64 = base64.b64encode(raw_png.read_bytes()).decode("ascii")
    svg = SVG.format(
        b64=b64,
        img_y=SCENE_IMAGE_SHIFT.get(scene, 0),
        overlay=overlay_for(scene, locale),
        h1=escape(headline),
        h2=escape(subtitle),
    )
    svg_path = ROOT / locale / "briefs" / f"screenshot-{scene}.svg"
    svg_path.write_text(svg, encoding="utf-8")
    out_png = ROOT / locale / "images" / "phone" / f"{scene}-{locale}.png"
    out_png.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["rsvg-convert", "-w", "1080", "-h", "2400", str(svg_path), "-o", str(out_png)],
        check=True,
    )
    print(f"  ✓ {locale}/images/phone/{out_png.name}")


def main() -> None:
    scenes = sys.argv[1:] or list(SCENES)
    for scene in scenes:
        print(scene)
        for locale in LOCALES:
            compose(scene, locale)


if __name__ == "__main__":
    main()
