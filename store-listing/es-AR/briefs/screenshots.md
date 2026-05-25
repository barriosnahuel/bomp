# Brief: Screenshots (Bomp — es-AR)

## Spec Play Store
- **Phone:** mínimo 2, **máximo 8** caps. Aspect ratio 9:16 a 9:20. Entregables: **1080 × 2400** (9:20, lo que usan los Pixel modernos).
- **Tablet 7" / 10":** TODO post-launch — fuera del scope.
- **Prohibido** enmarcar la UI dentro de un frame de teléfono.

## Pipeline (automatizado)

Dos pasos reproducibles desde scripts — ya no es captura manual + Inkscape:

1. **Captura** — `scripts/capture-store-screenshots.sh`. Siembra un board realista (`DebugSoundSeeder` + `DebugSeedCorpus`), navega la app real a cada escena (destrabando el Baúl vía `VaultSessionState`, sin biometría) y guarda PNGs full-screen 1080×2400 en `real-screenshots/<escena>-<locale>.png`. Como la app usa **system locale**, flipea el locale del emulador + reboot por idioma; y setea un PIN para que el CTA privado de New Bomp salga pulido (no la advertencia de "sin bloqueo"). Necesita un emulador booteado y **rooteable** (imagen Google APIs).
2. **Composición** — `scripts/compose-store-screenshots.py`. Arma cada SVG híbrido (franja Ink opaca + headline/subtítulo localizable + la captura embebida en base64) y renderiza con `rsvg-convert` a `images/phone/`. La copy de cada escena vive en el dict `SCENES` del script.

**Híbridas** = header Ink1000 opaco (320px, tapa status bar + topbar) sobre la captura real → UI 100% auténtica, cero risk de "misrepresent". **Typography** (#2 manifiesto, #8 cierre) = full-vector, sin captura embebida.

## Escenas (8)

| # | Escena | Tipo | Headline / subtítulo |
|---|---|---|---|
| 1 | `01-home` | Híbrida | **Tu colección de voces.** / Las que te importan, siempre con vos. |
| 2 | `02-manifesto` | Typography | 4 bullets: Tuyo, primero · Sin registro, sin email, sin número · Guardado en la nube · Un abrazo que se escucha |
| 3 | `03-collections` | Híbrida | **Según de quién son.** / Familia, laburo, códigos — filtrá en un toque. |
| 4 | `04-search` | Híbrida | **Encontrá cualquier voz.** / Aunque tu colección se haga enorme. |
| 5 | `05-immersive` | Híbrida | **Vos y la voz, nada más.** / Abrís una y el mundo se calla. |
| 6 | `06-vault` | Híbrida | **Bajo llave, solo tuyo.** / El Baúl se abre con tu huella. |
| 7 | `07-newbomp` | Híbrida | **Apodalo. Tenelo cerca.** / Y sumalo a las colecciones que le pertenecen. |
| 8 | `08-closing` | Typography | Hero curatorial: "Para los que guardan momentos como otros guardan fotos." |

**Arco ASO:** colección → manifiesto → colecciones → buscar → escuchar (inmersivo) → Baúl → guardar → cierre. Cero implicancia de "mandar afuera" — el momento de valor es **escuchar**.

## Regenerar

```bash
./scripts/capture-store-screenshots.sh          # 1. capturas crudas → real-screenshots/
python3 scripts/compose-store-screenshots.py     # 2. composición → images/phone/
```

Las typography (#2, #8) no se capturan (full-vector); para re-renderizarlas:
```bash
rsvg-convert -w 1080 -h 2400 store-listing/es-AR/briefs/screenshot-02-manifesto.svg -o store-listing/es-AR/images/phone/02-manifesto-es-AR.png
rsvg-convert -w 1080 -h 2400 store-listing/es-AR/briefs/screenshot-08-closing.svg   -o store-listing/es-AR/images/phone/08-closing-es-AR.png
```

## Contraste (WCAG)
- Paper ↔ Ink1000 (header strip) = 17.5:1 ✓
- Para la UI capturada, los pares ya están cubiertos por `AppThemeContrastTest` en el repo.
