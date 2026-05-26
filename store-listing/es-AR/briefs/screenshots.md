# Brief: Screenshots (Bomp — es-AR)

## Spec Play Store
- **Phone:** mínimo 2, **máximo 8** caps. Aspect ratio 9:16 a 9:20. Entregables: **1080 × 2400** (9:20, lo que usan los Pixel modernos).
- **Tablet 7" / 10":** TODO post-launch — fuera del scope.
- **Prohibido** enmarcar la UI dentro de un frame de teléfono.

## Pipeline (automatizado)

Dos pasos reproducibles desde scripts — ya no es captura manual + Inkscape:

1. **Captura** — `scripts/capture-store-screenshots.sh`. Siembra un board realista (`DebugSoundSeeder` + `DebugSeedCorpus`), navega la app real a cada escena (destrabando el Baúl vía `VaultSessionState`, sin biometría) y guarda PNGs full-screen 1080×2400 en `real-screenshots/<escena>-<locale>.png`. Como la app usa **system locale**, flipea el locale del emulador + reboot por idioma. Necesita un emulador booteado y **rooteable** (imagen Google APIs).
2. **Composición** — `scripts/compose-store-screenshots.py`. Arma cada SVG híbrido (franja Ink opaca + headline/subtítulo localizable + la captura embebida en base64) y renderiza con `rsvg-convert` a `images/phone/`. La copy de cada escena vive en el dict `SCENES` del script.

**Híbridas** = header Ink1000 opaco (320px, tapa status bar + topbar) sobre la captura real → UI 100% auténtica, cero risk de "misrepresent". **Typography** (#5 manifiesto, #6 cierre) = full-vector, sin captura embebida.

## Escenas (6)

Producto primero: las 4 pantallas reales ocupan los slots que se ven en resultados de búsqueda; los 2 de texto cierran. `02-collections` es la pantalla **Gestionar colecciones** (overflow ⋮ → Gestionar), visualmente distinta de Home, para que el carrusel no repita el listado de `01-home`.

| # | Escena | Tipo | Headline / subtítulo |
|---|---|---|---|
| 1 | `01-home` | Híbrida | **Las voces que importan.** / La risa de tu vieja, el audio del amigo. |
| 2 | `02-collections` | Híbrida | **Cada voz en su lugar.** / Familia, laburo, el grupo de siempre. |
| 3 | `03-vault` | Híbrida | **Solo vos entrás.** / El Baúl, detrás de tu huella. |
| 4 | `04-immersive` | Híbrida | **Vos y la voz, nada más.** / Abrís una y el mundo se calla. |
| 5 | `05-manifesto` | Typography | 4 bullets: Tuyo, primero · Sin registro, sin email, sin número · Guardado en la nube · Un abrazo que se escucha |
| 6 | `06-closing` | Typography | Hero curatorial: "Para los que guardan momentos como otros guardan fotos." |

**Arco ASO:** colección → colecciones → Baúl → escuchar (inmersivo) → manifiesto → cierre. Cero implicancia de "mandar afuera" — el momento de valor es **escuchar**. Sin escena de búsqueda: una ZRP vacía no comunica nada en los caps de mayor visibilidad, y a un usuario con cero audios no le sirve "encontrá entre muchos".

## Regenerar

```bash
./scripts/capture-store-screenshots.sh          # 1. capturas crudas → real-screenshots/
python3 scripts/compose-store-screenshots.py     # 2. composición → images/phone/
```

Las typography (#5, #6) no se capturan (full-vector); para re-renderizarlas:
```bash
rsvg-convert -w 1080 -h 2400 store-listing/es-AR/briefs/screenshot-05-manifesto.svg -o store-listing/es-AR/images/phone/05-manifesto-es-AR.png
rsvg-convert -w 1080 -h 2400 store-listing/es-AR/briefs/screenshot-06-closing.svg   -o store-listing/es-AR/images/phone/06-closing-es-AR.png
```

## Contraste (WCAG)
- Paper ↔ Ink1000 (header strip) = 17.5:1 ✓
- Para la UI capturada, los pares ya están cubiertos por `AppThemeContrastTest` en el repo.
