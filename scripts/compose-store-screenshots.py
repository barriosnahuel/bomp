#!/usr/bin/env python3
# Composes the hybrid store screenshots: an opaque Ink marketing header strip (localizable headline
# + subtitle) over the real device capture (embedded as base64), rendered to the final 1080×2400
# PNG deliverables with rsvg-convert. The header covers the captured status bar + app top bar; the
# real cards + bottom nav stay visible — same hybrid approach as the existing store SVGs.
#
# Usage:
#   scripts/compose-store-screenshots.py                # all hybrid scenes, both locales
#   scripts/compose-store-screenshots.py 03-collections # one scene
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
# the Vault framed as a fingerprint access gate (no encryption over-claim).
SCENES = {
    "01-home": {
        "es-AR": ("Tu colección de voces.", "Las que te importan, siempre con vos."),
        "en-US": ("Your collection of voices.", "The ones that matter — always close."),
    },
    "03-collections": {
        "es-AR": ("Según de quién son.", "Familia, laburo, códigos — filtrá en un toque."),
        "en-US": ("By who they're from.", "Family, work, inside jokes — filter in a tap."),
    },
    "04-search": {
        "es-AR": ("Encontrá cualquier voz.", "Aunque tu colección se haga enorme."),
        "en-US": ("Find any voice, fast.", "Even when your collection gets big."),
    },
    "06-vault": {
        "es-AR": ("Bajo llave, solo tuyo.", "El Baúl se abre con tu huella."),
        "en-US": ("Locked, just for you.", "Your Vault opens with your fingerprint."),
    },
    "05-immersive": {
        "es-AR": ("Vos y la voz, nada más.", "Abrís una y el mundo se calla."),
        "en-US": ("Just you and the voice.", "Open one and the world goes quiet."),
    },
    "07-newbomp": {
        "es-AR": ("Apodalo. Tenelo cerca.", "Y sumalo a las colecciones que le pertenecen."),
        "en-US": ("Name it. Keep it.", "Drop it into the collections it belongs to."),
    },
}

SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 2400" width="1080" height="2400">
  <defs>
    <style>
      .header-h1 {{ font-family: Inter, Roboto, system-ui, sans-serif; font-weight: 700; font-size: 78px; fill: #FAFAF7; letter-spacing: -1.5px; }}
      .header-h2 {{ font-family: Inter, Roboto, system-ui, sans-serif; font-weight: 400; font-size: 38px; fill: #FAFAF7; opacity: 0.85; }}
    </style>
  </defs>
  <image x="0" y="0" width="1080" height="2400" preserveAspectRatio="xMidYMin slice" href="data:image/png;base64,{b64}"/>
  <rect x="0" y="0" width="1080" height="320" fill="#0B0B0C"/>
  <text x="64" y="172" class="header-h1">{h1}</text>
  <text x="64" y="244" class="header-h2">{h2}</text>
</svg>
"""


def compose(scene: str, locale: str) -> None:
    headline, subtitle = SCENES[scene][locale]
    raw_png = RAW / f"{scene}-{locale}.png"
    if not raw_png.exists():
        print(f"  ! missing capture {raw_png.name}, skipping")
        return
    b64 = base64.b64encode(raw_png.read_bytes()).decode("ascii")
    svg = SVG.format(b64=b64, h1=escape(headline), h2=escape(subtitle))
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
