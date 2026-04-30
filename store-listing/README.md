# Bomp — Store Listing

Assets y copy para la ficha de Google Play, organizados por locale.

## Estructura

```
store-listing/
├── README.md                       # este archivo
├── brand/
│   ├── bomp-mark.svg               # master vectorial de marca (blob orgánico) — usado en web
│   ├── launcher-fallback.svg       # source para mipmap-*/app_ic_launcher.png (Android < 8)
│   └── fonts/
│       └── Inter.zip               # font brand zippeada (SIL OFL, instalación en CONTRIBUTING.md)
├── real-screenshots/               # PNGs nativos 1080×2400 capturados en Pixel emulator
├── es-AR/                          # primer locale entregado
│   ├── title.txt                   # ≤30 chars
│   ├── short_description.txt       # ≤80 chars
│   ├── full_description.txt        # ≤4000 chars
│   ├── changelog-5.txt             # ≤500 chars; matchea versionCode actual
│   ├── briefs/
│   │   ├── icon.md                 # spec del icono 512×512
│   │   ├── icon-512.svg            # composición vector del icono Play Store
│   │   ├── feature-graphic.md      # spec del feature graphic 1024×500
│   │   ├── feature-graphic.svg     # composición vector lista para export
│   │   ├── screenshots.md          # spec del workflow híbrido (PNG real + SVG header)
│   │   ├── screenshot-01-home.svg  # SVG híbrido — escena Home
│   │   ├── screenshot-02-search.svg# SVG híbrido — Search overlay
│   │   ├── screenshot-03-playing.svg# SVG híbrido — playing
│   │   └── preview-video.md        # guion del video opcional
│   └── images/                     # PNGs entregables (generados con rsvg-convert; ver CONTRIBUTING.md § "Store listing")
│       ├── icon-512-<locale>.png
│       ├── feature-graphic-1024x500-<locale>.png
│       ├── phone/                  # 01-home-<locale>.png, 02-search-<locale>.png, 03-playing-<locale>.png
│       ├── tablet-7/               # vacío — TODO post-launch
│       └── tablet-10/              # vacío — TODO post-launch
├── en-US/                          # idéntico árbol que es-AR; copy y headlines en inglés
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── changelog-5.txt
│   ├── briefs/                     # icon-512.svg + feature-graphic.svg + 3 screenshot-*.svg + .md briefs
│   └── images/                     # mismos PNGs entregables que es-AR, re-renderizados desde sus SVG en inglés
└── (futuro) es-419/, es-ES/, pt-BR/
```

## Posicionamiento

> **El Bomp es tuyo, primero.** Bomp es una colección personal de voces que te importan. Compartir existe — es derivado — pero el audio primero te tiene que servir a vos. Antes de tocar el copy de cualquier locale, leer `../push-me-backlog/docs/brand-dna.md` §5.

Reglas que nacen de ese posicionamiento:

- **Cero "stickers de audio" en copy oficial** (ficha, ads, video). Si el usuario hace la analogía espontáneamente, está bien — no la usamos como tagline.
- **Voseo argentino** en es-AR (`apodá`, `guardá`, `bompeá`, `tocá`). Tuteo en es-419 / es-ES.
- **Manifesto invariante:** _"Un audio de los tuyos no es un mensaje, es un abrazo que se escucha."_ — cierre de toda full description.
- **Glosario invariante:** Bomper / Bompear / Bompeable. Las definiciones se actualizan al brand-dna §4.

## Cómo subir a Play Console

1. Login en Play Console → app Bomp → Store presence → Main store listing.
2. Por cada idioma soportado:
   - **Title:** copiar contenido de `<locale>/title.txt`.
   - **Short description:** copiar `<locale>/short_description.txt`.
   - **Full description:** copiar `<locale>/full_description.txt`.
   - **What's new:** copiar `<locale>/changelog-<versionCode>.txt`.
3. Subir las imágenes desde `<locale>/images/`:
   - `icon-512-<locale>.png` → "App icon".
   - `feature-graphic-1024x500-<locale>.png` → "Feature graphic".
   - `phone/*-<locale>.png` → "Phone screenshots".
   - `tablet-7/*-<locale>.png` → "7-inch tablet screenshots".
   - `tablet-10/*-<locale>.png` → "10-inch tablet screenshots".

> El sufijo `<locale>` es necesario para distinguir los archivos entre idiomas en la consola de Play (que usa el filename como label visible al subir). Usar `es-AR`, `en-US`, etc. — mismo identificador que el directorio padre.
4. Submit para review.

Tip: tener un dry-run interno antes de publicar — Play permite preview privado de la ficha.

## Cómo agregar un locale nuevo

1. Clonar el árbol `es-AR/` a `<nuevo-locale>/`.
2. Traducir cada `.txt` respetando las reglas de posicionamiento de arriba. Para Spanish ↔ otros idiomas hay un dictionary completo en `../push-me-ghpages/assets/js/i18n.js` que sirve de fuente léxica.
3. Re-exportar los PNG (icon + feature graphic + screenshots) si el copy on-image cambia.
4. PR.
