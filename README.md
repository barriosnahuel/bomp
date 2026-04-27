# Bomp — Hub público (GitHub Pages)

Esta rama (`gh-pages`) sirve el sitio público de Bomp en
`https://barriosnahuel.github.io/bomp/`.

**No contiene código de la app**. La app vive en `develop` (rama por defecto del repo).

## Contenido

| Archivo | Propósito |
|---|---|
| `index.html` | Landing V3 (sticker culture · manifesto · doble columna · footer) |
| `privacy-policy.html` | Política de privacidad — cumple Google Play User Data Policy |
| `data-safety.html` | Espejo legible del CSV `data_safety_export.csv` de Play Console |
| `404.html` | Mensaje + redirect a `/` |
| `assets/css/tokens.css` | Paleta Neo-Club, type scale, radii, elevación. **Espejo de `AppTheme.kt:14-103`** |
| `assets/css/base.css` | Reset, focus ring, skip link, `prefers-reduced-motion` |
| `assets/css/landing.css` | Componentes de la landing V3 (hero split, sticker hero, decoys, mocks, manifesto, footer) |
| `assets/css/legal.css` | Layout 80ch + ToC sticky + print stylesheet para las páginas legales |
| `assets/js/theme-toggle.js` | Toggle dark/light con persistencia en `localStorage["bomp-theme"]` |
| `assets/js/sticker-hero.js` | Audio real (`<audio>`) + progreso radial driven by `audio.currentTime` |
| `assets/audio/welcome.mp3` | **Placeholder silencioso de 1s** — TODO: producir audio definitivo antes del release a Play Store |

## Stack

HTML estático + CSS variables + JS vanilla. **Sin build, sin Jekyll, sin Actions.**
GitHub Pages sirve directamente esta rama (`Settings → Pages → Source: gh-pages branch`).

## Sincronización con la app

Cualquier cambio en `AppTheme.kt` (módulo `app`, rama `develop`) debe replicarse en
`assets/css/tokens.css` y viceversa. La paleta Neo-Club ya está pre-validada WCAG 2.2 AA —
no introducir colores nuevos sin revalidar contraste.

## TODOs bloqueantes para Play Store

- [ ] Reemplazar `assets/audio/welcome.mp3` por audio real (≤10s, español).
- [ ] Confirmar email ARCO en `privacy-policy.html`.
- [ ] Reemplazar el pill "Google Play" custom por el badge oficial cuando la app esté publicada.
- [ ] Confirmar URL pública (`barriosnahuel.github.io/bomp/`) tras el primer deploy y rectificar `og:url`, `<link rel="canonical">` y `sitemap.xml` si difiere.

## Riesgo a vigilar: rename del repo

Si el repo se renombra, la URL pública del sitio cambia. GitHub mantiene un redirect 301,
pero la URL ya pegada en la ficha de Play Store + en el menú About del APK debe actualizarse
manualmente. Mitigación robusta: comprar dominio custom (`bomp.app`) y agregar `CNAME`.
