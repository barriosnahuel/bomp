# CLAUDE.md — rama `gh-pages`

> Esta es **una rama huérfana** que solo aloja el sitio público de Bomp en
> `https://barriosnahuel.github.io/bomp/`.
> No hay código de la app acá. La app vive en `develop` (rama default).
> Worktree convencional: `/Users/barrios.nahuel/Workspace/bomp-ghpages/`
> (la carpeta local actual puede seguir llamándose `push-me-ghpages` si nunca se renombró —
> el nombre del directorio es independiente del nombre del repo).

## Reglas de oro

1. **No correr `./gradlew` en esta rama.** No tiene `app/`, `model/`, ni Gradle.
   Si necesitás trabajar en la app, salí del worktree y entrá al checkout de `bomp` (develop).
2. **Stack:** HTML estático + CSS variables + JS vanilla. Sin build, sin Jekyll, sin Actions.
   GitHub Pages sirve directamente esta rama (`Settings → Pages → Source: gh-pages branch`).
3. **No agregar dependencias runtime sin razón estructural.** Si vas a sumar un script o un
   paquete, primero pensá si una hoja de CSS o un SVG inline lo resuelven.

## Fuente de verdad — qué se sincroniza con qué

El sitio es un **espejo coordinado** de tres fuentes externas. Cualquier cambio en esas fuentes
debe reflejarse acá manualmente. Los puntos de sincronización están listados a continuación
para que sea trivial cazar drift.

### A. Tokens de diseño · paleta Neo-Club

- **Fuente:** `AppTheme.kt` en el módulo `app` (rama `develop` del repo `bomp`).
  El paquete Java/Kotlin del Android source puede o no haber sido renombrado todavía
  (originalmente `com.github.barriosnahuel.vossosunboton`); buscar el archivo por nombre
  evita acoplarse al package literal mientras se hace la transición.
- **Espejo:** `assets/css/tokens.css` en esta rama.
- **Cambio típico:** se modifica un color en `AppTheme.kt` (e.g. `Acid400`).
  Acción: replicar el cambio en `assets/css/tokens.css` *con el mismo valor hex*, regenerar
  `AppThemeContrastTest` en la app, validar contraste WCAG 2.2 AA acá también.
- **Caveat:** `tokens.css` ya lleva el comentario "SOURCE OF TRUTH: AppTheme.kt".
  No introducir colores nuevos sin actualizar primero `AppTheme.kt`.

### B. Brand DNA · vocabulario / pronunciación / ADN

- **Fuente:** `../push-me-backlog/docs/brand-dna.md` (sibling repo, no incluido en este worktree).
- **Espejo:** los strings ES de `index.html` (`l-eyebrow`, `l-hero__title`, glosario `l-gloss__item`,
  manifesto `l-manifesto__inner`, footer `l-footer__brand`).
- **Lenguaje sistémico (canon):**
  - **Bomp** /bomp/ — la app.
  - **Bomper** /bom·per/ — sustantivo, el usuario.
  - **Bompear** /bom·pe·ár/ — verbo (bompo, bompás, bompea, bompeamos).
  - **Bompeable** /bom·pe·á·ble/ — adjetivo, audio que vale la pena guardar.
- **Cambio típico:** brand-dna.md introduce un término nuevo o refina la definición.
  Acción: reflejar en el glosario de `index.html` y, si es vocabulario de UI, también en
  los strings de la app (`app/src/main/res/values/strings.xml`).

### C. Privacy Policy + Data Safety

- **Fuente:**
  - `../push-me-backlog/google-play-console-policies/data_safety_export.csv` —
    exportado de Play Console, manda sobre los hechos declarados en `data-safety.html`.
  - Texto legal en `privacy-policy.html` lo escribe el redactor humano (placeholder presente).
- **Espejo:** `data-safety.html` y `privacy-policy.html` en esta rama.
- **Cambio típico:** se actualiza el CSV (Play Console pide reconfirmar Data Safety cada release).
  Acción: ejecutar diff entre el CSV actual y la última versión declarada acá; cualquier `true`
  nuevo o eliminado debe reflejarse en la tabla de `data-safety.html`.

## Diseño · contrato visual

El sitio implementa **V3 — Sticker culture · manifesto · doble columna · footer**, basado en el
design file que produjo Claude Design (claude.ai/design) para Bomp. Reglas:

- **No introducir colores hardcodeados.** Todo va vía variables de `tokens.css`.
- **No usar `prefers-color-scheme` dentro de componentes.** Si un componente necesita comportarse
  distinto en light/dark, el rol semántico de `tokens.css` debe diferir entre ambos modos.
  Excepción justificada y documentada: `.l-manifesto` está bloqueado en ink/paper porque la
  inversión por tema rompe AA.
- **Sticker-culture treatments** (rotaciones leves, sombras `6px 6px 0 ink`, decoy stickers,
  border-radius orgánicos): son seña de identidad de V3, no decoración. No "limpiar" sin razón.
- **Tipografía:** system fonts, escala Material 3 ya tokenizada en `tokens.css` (`--type-*`).
- **Animaciones:** breathe, halo, caret-blink. Todas se desactivan automáticamente con
  `prefers-reduced-motion: reduce` (handled in `assets/css/base.css`).

## Accesibilidad — WCAG 2.2 AA es obligatorio

Igual que en la app:
- **Contraste:** ≥ 4.5:1 texto normal, ≥ 3:1 texto grande / componentes UI no-textuales.
- **Foco visible:** `:focus-visible` global con outline 2px + offset (en `base.css`).
- **Touch targets ≥ 24×24 dp** (preferimos 48×48 dp para acciones primarias).
- **Skip link** primer foco en cada página (`<a href="#main" class="skip-link">`).
- **`aria-pressed`** en toggles (theme, sticker hero).
- **`role="status"` + `aria-live="polite"`** para mensajes dinámicos (sticker hero caption).
- **Tipografía relativa**: `font-size` siempre en `rem`/`em`, nunca en `px` (criterio 1.4.4
  Resize Text — Android lo da gratis vía `fontScale`, web no). Aplica a CSS files y `<style>`
  inline. Borders/hairlines en `px` está bien. Verificable mecánicamente:
  `grep -rn "font-size:[^;]*[0-9]px" assets/css/ *.html` debe devolver 0 líneas.
- **Cualquier cambio de paleta requiere revalidar contraste**. Lo más rápido: open
  https://webaim.org/resources/contrastchecker/ con el par afectado.

## Pre-merge checklist (correr antes de commit + push)

1. **Guard mecánico de tipografía relativa** (cero tolerancia, corre antes que cualquier
   smoke humano):
   ```bash
   grep -rn "font-size:[^;]*[0-9]px" assets/css/ *.html
   ```
   0 líneas = pasa. Cualquier match bloquea — convertir el `px` a `rem` antes de seguir.
   Cubre WCAG 1.4.4 Resize Text (ver sección Accesibilidad).
2. Smoke local: levantar un server local desde la raíz del worktree y probar las páginas
   en `http://localhost:8000/`:
   ```bash
   python3 -m http.server 8000
   ```
   **Por qué no `file://`:** Chrome trata cada archivo `file://` como *unique security origin*
   y dispara warnings tipo `Unsafe attempt to load URL ... from frame with URL ...` cuando
   el `<audio preload="metadata">` o el widget de Ko-fi hacen sub-resource requests. Sirviendo
   por `http://` esos warnings desaparecen y el smoke refleja mejor el entorno real (GitHub
   Pages sirve por `https://`). Páginas a probar:
   - `http://localhost:8000/` (landing V3)
   - `http://localhost:8000/privacy-policy.html`
   - `http://localhost:8000/data-safety.html`
   - `404.html` queda fuera del smoke local: `python3 -m http.server` no tiene fallback a
     `404.html` para rutas inexistentes (responde con su propia página 404 plain). El
     comportamiento "ruta inexistente → `404.html` con tu chrome" es responsabilidad de
     GitHub Pages y se valida en deploy, no localmente.
3. Probar **light mode + dark mode + toggle manual con persistencia** (sin FOUC).
4. **Tap del sticker hero**: audio reproduce, anillo radial se llena, `aria-pressed=true`.
5. **DevTools → Disable JavaScript**: las 3 páginas siguen siendo navegables y los links
   funcionan; el toggle de tema y el sticker hero quedan inactivos pero no rompen la página.
6. **DevTools → Rendering → `prefers-reduced-motion: reduce`**: 0 animaciones (sin breathe,
   sin halo, sin caret blink).
7. **Zoom a 200%** (Cmd/Ctrl + + hasta el indicador del browser marque 200%, en mobile width
   ≤ 720px): el texto se agranda proporcionalmente, ningún bloque queda truncado, no aparece
   scroll horizontal en la página. Cubre WCAG 1.4.4 Resize Text + 1.4.10 Reflow.
8. **Lighthouse** (mobile, 4G simulado): A11y ≥ 95 bloqueante; resto: Perf ≥ 90, Best Pr ≥ 95,
   SEO ≥ 90.
9. **Print stylesheet** (`Cmd+P` en privacy-policy y data-safety): se imprime sin chrome
   (sin header, sin footer, sin sticky ToC).

## TODOs bloqueantes para Play Store

Antes del primer push de la ficha en Play, resolver:

- [ ] Reemplazar el pill custom en `.l-cta-row` por el badge oficial **Pre-register on Google
      Play** (es-419 SVG) descargado del Partner Marketing Hub:
      https://partnermarketinghub.withgoogle.com/brands/google-play/visual-identity/badge-guidelines/
- [x] Email de contacto definitivo: `barrios.nahuel+bomp@gmail.com` (usado en `pp.s05.li7`,
      `tos.s12.body`, `tos.meta.operator` × 5 locales + fallbacks estáticos en
      `privacy-policy.html` y `terms-of-service.html`).
- [ ] Confirmar URL pública (`barriosnahuel.github.io/bomp/`) tras el primer deploy y rectificar
      `og:url`, `<link rel="canonical">` y `sitemap.xml` si difiere.

## Donaciones — terceros

`index.html` embebe dos servicios externos en la sección `#donar`:

- **Cafecito** (`cafecito.app/barriosnahuel`) — botón de imagen servido por
  `cdn.cafecito.app`. Privacidad regida por su propia política.
- **Ko-fi** (`ko-fi.com/Y8Y31YIHWK`) — widget JS servido por `storage.ko-fi.com`. Inyecta el
  botón inline vía `kofiwidget2.draw()`. No correr con `defer` o `async` o pierde la posición
  de inserción.

Ambos cargan recursos desde dominios de terceros. Si el operador de Bomp deja de querer alguno
de los dos, basta con borrar el bloque correspondiente del `<div class="l-donate-buttons">` —
el otro sigue funcionando en solitario. Cualquier cambio en la lista de proveedores debe
reflejarse en `privacy-policy.html` (sección Ecosistema de Terceros).

## Riesgo conocido: rename del repo

GitHub Pages mantiene un redirect 301 cuando renombrás el repo, pero la URL pública del sitio
cambia. La URL ya pegada en la ficha de Play Store (Privacy Policy + Data Safety + listing)
debe actualizarse manualmente. **Mitigación robusta:** comprar dominio custom (`bomp.app`,
etc.) y agregar `CNAME` desde el día 1.
