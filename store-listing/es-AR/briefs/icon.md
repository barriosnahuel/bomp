# Brief: Icono 512×512 (Bomp — es-AR)

## Spec Play Store
- 512 × 512 px
- 32-bit PNG
- ≤ 1024 KB
- Fondo opaco (sin transparencia)

## Composición

- **Fondo:** full-bleed `Acid400` (`#D7FF3A`).
- **Foreground:** play triangle Ink1000 (`#0B0B0C`) centrado en el viewport, ocupando ~33% del ancho dentro del inner safe zone (66dp circle). ViewBox 108 alineado al adaptive icon foreground (`app/src/main/res/drawable/app_ic_launcher_foreground.xml`) — misma geometría exacta entre launcher y Play Store: path `M40,34 L40,75 L76,54 Z`.
- Sin blob. Sin texto. Sin sombras propias (Play aplica las suyas).

## Por qué Acid es el contenedor (y no Ink + blob)

El brand mark canónico (el blob orgánico de `bomp-mark.svg`) **funciona en surfaces sin system mask**: favicon del web, `.l-wordmark__mark` del ghpages, feature graphic 1024×500. Ahí el border-radius irregular dibuja la silueta directamente sobre el fondo.

En launcher (mask circle/squircle/teardrop según OEM) y Play Store (rounded square), el sistema **impone su propio contenedor** y la asimetría del blob queda peleando contra los corners del mask. Lecturas que vimos en preview:

- **Play Console editor:** la forma irregular metida en un rounded square se lee como "una mancha rara dentro de un cuadrado", no como una marca.
- **Pixel launcher:** la circle mask recorta el blob a un círculo casi perfecto, anulando la silueta orgánica que ES la marca.

Solución: el color de marca **es** el contenedor. Acid400 full-bleed deja al sistema cropear lo que quiera — siempre vemos un mask sólido en color de marca con el play triangle adentro. Surface-divergent brand: el blob vive en el web, el icono es una compresión.

## Contraste (WCAG)
- Ink1000 ↔ Acid400 = 13.5:1 (AA+++ ✓)

## SVG fuente y export

Master vectorial: `icon-512.svg` (en este mismo directorio). Comando de export:

```bash
rsvg-convert -w 512 -h 512 \
  store-listing/es-AR/briefs/icon-512.svg \
  -o store-listing/es-AR/images/icon-512-es-AR.png
```

El mismo viewBox 108 + path se usan en `store-listing/brand/launcher-fallback.svg` para los raster fallbacks de Android < 8 (`app/src/main/res/mipmap-*dpi/app_ic_launcher.png`). Cualquier cambio futuro al icono debe replicarse en ambos SVG fuente para mantener consistencia entre Play Store y launcher.
