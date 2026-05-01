# Brief: Screenshots (Bomp — es-AR)

## Spec Play Store
- **Phone:** mínimo 4 caps. Aspect ratio 9:16 a 9:20. Los entregables actuales son **1080 × 2400** (9:20 — el que usan los Pixel modernos). Play acepta 9:16 mínimo histórico.
- **Tablet 7":** 1 cap. 1024 × 600 px o equivalente 16:9. **TODO post-launch — fuera del scope del primer release.**
- **Tablet 10":** 1 cap. 2560 × 1600 px o equivalente. **TODO post-launch — fuera del scope del primer release.**
- **Prohibido enmarcar la UI dentro de un frame de teléfono.**

## Workflow: 3 híbridas (PNG + SVG header) + 2 typography full-vector

5 SVGs en `briefs/screenshot-0X-*.svg`. Dos formatos:

**Híbridas (#1, #3, #4 — UI proof):**
- **Marketing header strip** Ink1000 vector (320–380px según largo de la headline) en el top con headline + subtítulo. La copy queda editable como texto, fácil de localizar.
- **PNG real** del app (`real-screenshots/Screenshot_*.png`, 1080×2400) **embebido como base64** dentro del SVG (`<image href="data:image/png;base64,...">`). Necesario porque `rsvg-convert` bloquea referencias `file://` externas por seguridad.
- El header opaco cubre la status bar + topbar de la captura, dejando visible cards + bottom nav del app real. UI 100% auténtica, cero risk de "misrepresent" para Play.

**Typography full-vector (#2, #5 — manifesto + cierre):**
- Sin PNG embebido, todo Ink1000 + texto Paper + acento Acid. Diseñadas para ASO (#2) y emotional close (#5).

Canvas final: **1080 × 2400** (9:20). Slot order optimizado para ASO — ver § "Arco narrativo y ASO" abajo.

| # | Archivo SVG | Tipo | Contenido | Subtítulo / tagline |
|---|---|---|---|---|
| 1 | `screenshot-01-home.svg` | Híbrida | `Screenshot_20260428_225250.png` — Home idle (5 cards) — **Tu colección de voces.** | Las que te importan, siempre con vos. |
| 2 | `screenshot-02-manifesto.svg` | Typography | 4 bullets densos: **Tuyo, primero.** / **Sin registro, sin email, sin número de teléfono.** / **Guardado en la nube.** / **Un abrazo que se escucha.** | Acid blob brand-anchor sutil al pie. Sin header textual — el contexto del listing ya identifica la app. |
| 3 | `screenshot-03-search.svg` | Híbrida | `Screenshot_20260428_225317.png` — Search overlay con query "ris" filtrando 2 resultados idle — **Encontrá rápido / la voz que querés.** | Cuando tu colección crezca. |
| 4 | `screenshot-04-playing.svg` | Híbrida | `Screenshot_20260428_225339.png` — Search overlay con "Risa de mi vieja" reproduciéndose (pause + halo + slider Acid) — **Suena en un toque.** | Sin esperas, sin pantalla cargando. |
| 5 | `screenshot-05-closing.svg` | Typography | Hero único curatorial: **"Para los que guardan momentos como otros guardan fotos."** | Acid blob brand-anchor al pie (mismo treatment que card 02). |

### Arco narrativo y ASO

`tu colección → manifesto → encontrá → activá → cierre`.

Cero implicancia de "mandar afuera" — el momento del valor es **escuchar**, no compartir.

ASO: el slot #2 (carousel de search results, alta visibilidad) carga el manifesto con 4 bullets densos en keywords ("sin registro", "guardado en la nube") + posicionamiento brand ("tuyo, primero") + invocación poética ("un abrazo que se escucha"). El slot #5 cierra con un hero curatorial único — define la audiencia ("los que guardan momentos como otros guardan fotos") con la misma fuerza visual que el manifesto. Ambas typography cards (02 y 05) llevan el mismo Acid blob al pie como brand anchor consistente.

### Re-render PNGs finales

Los SVG ya están compuestos. Para regenerar los PNGs entregables (`images/phone/0X-*.png`) corré:

```bash
for n in 01-home 02-manifesto 03-search 04-playing 05-closing; do
  rsvg-convert -w 1080 -h 2400 \
    store-listing/es-AR/briefs/screenshot-$n.svg \
    -o store-listing/es-AR/images/phone/$n-es-AR.png
done
```

Nota: las cards #2 y #5 son typography full-vector (no embeben PNG). Las #1, #3, #4 son híbridas (header SVG + PNG real base64). El comando aplica a todas por igual — `rsvg-convert` resuelve cada caso sin cambios.

### Re-capturar PNGs cuando cambie la UI

Cuando la UI in-app cambie (paleta, padding, card, etc.), las 3 capturas hay que re-tomarlas y los SVG hay que re-componer (el base64 está embebido):

1. Build de design QA con la BD sembrada con los nombres canónicos del brief.
2. Activar Pixel emulator con device en dark mode (`adb shell "cmd uimode night yes"`).
3. Capturar las 3 pantallas con `adb exec-out screencap -p > Screenshot_xxx.png` (resolución nativa 1080×2400).
4. Reemplazar los archivos en `real-screenshots/`.
5. Re-componer los SVG re-embebiendo los PNG como base64 (`base64 < Screenshot_xxx.png` y reemplazar el bloque `data:image/png;base64,...` dentro del SVG correspondiente).
6. Re-renderizar los entregables con el comando `rsvg-convert` de arriba.

La headline en SVG queda intacta — solo cambia el bloque `<image href="data:...">`.

### Nombres de stickers para la captura #1 (verbatim del ghpages)
- `Risa de mi vieja`
- `¡Che, capo!`
- `Llegué`
- `La frase del jefe`
- `Mamá dice qué`
- `Risa de Pedro`
- `Buen día, amor`
- `Volvé pronto`

## Contraste (WCAG)
- Paper ↔ Ink1000 (header strip) = 17.5:1 ✓
- Para los elementos de la UI capturada, los pares ya están cubiertos por `AppThemeContrastTest` en el repo.
