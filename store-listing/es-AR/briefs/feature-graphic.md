# Brief: Feature Graphic 1024×500 (Bomp — es-AR)

## Spec Play Store
- 1024 × 500 px
- 24-bit PNG sin transparencia o JPG
- Safe zone: margen 15% (≈154 px laterales, ≈75 px arriba/abajo) — Play recorta los bordes en algunos surfaces.

## Composición
- **Fondo:** full-bleed `Ink1000` (`#0B0B0C`).
- **Lado izquierdo (~50%):** el blob `bomp-mark.svg` en `Acid400` con tres ondas concéntricas también en `Acid400` al 30 / 15 / 8 % de opacidad. El propósito de las ondas: sugerir que el sticker suena para el dueño — no que se transmite a otros.
- **Lado derecho (~50%):** la tagline canónica del landing en dos líneas, **`Paper` (`#FAFAF7`) Inter SemiBold 64px**, anclada justo después de la halo de ondas (x≈460) para que el ojo siga el flujo `mark → halo → texto`:
    > Las voces
    > de los tuyos.
- **Sin wordmark "Bomp"**: el nombre de la app ya aparece tres veces en el listing antes que el usuario llegue al feature graphic (icono, etiqueta debajo del icono, campo `title`). Repetirlo acá es ruido y desperdicia el real estate promocional.
- Sin caras humanas. Sin frames de teléfono. Sin íconos de apps de terceros.

## Tipografía
- Stack declarado: `Inter, Roboto, system-ui, sans-serif`. Inter es la fuente brand canónica — vive zippeada en `store-listing/brand/fonts/Inter.zip` y se instala con el comando documentado en `CONTRIBUTING.md` § "Store listing". Sin Inter instalada, `rsvg-convert` cae a Helvetica/SF Pro y el resultado se desvía del preview en navegador.

## Contraste (WCAG)
- Paper ↔ Ink1000 = 17.5:1 (AA+++ ✓) para wordmark y tagline.
- Acid400 ↔ Ink1000 = 13.5:1 (AA+++ ✓) para blob y ondas.

## Entregable vectorial
`store-listing/es-AR/briefs/feature-graphic.svg` — composición lista para abrir en Inkscape, ajustar tipografía y exportar a PNG 1024×500.

## Export
```bash
rsvg-convert -w 1024 -h 500 \
  store-listing/es-AR/briefs/feature-graphic.svg \
  -o store-listing/es-AR/images/feature-graphic-1024x500-es-AR.png
```
- Verificar que el archivo final NO supere el peso permitido por Play (~1 MB es seguro). Para tweaks de tipografía, abrir el SVG en Inkscape, ajustar y volver a correr `rsvg-convert`.
