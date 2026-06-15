# Custom store listing — Argentina, temporada fútbol ("El grito")

Paquete **temporal** para un **custom store listing** de Google Play targeteado a **Argentina** (es-AR).
No es el evento promocional (ese está gated por *premium growth tools* a esta escala) ni toca la ficha por
defecto: es un listing alternativo que se activa para AR y se revierte borrándolo.

## Por qué un custom listing (y no swap de la ficha principal)

- **No tiene gate premium** — disponible para cualquier cuenta ([doc](https://support.google.com/googleplay/android-developer/answer/9867158?hl=en)).
- **Targetea Argentina** con su propia descripción + feature graphic; la ficha por defecto queda **neutra**.
- **Reversible limpio:** al terminar el torneo, borrás el custom listing y todo vuelve solo. Sin pisar nada global.
- **Requiere estar en producción** (igual que tener una ficha por defecto publicada). Es el único gate.

> **Brasil queda afuera por ahora:** su locale es **pt-BR** y no tenemos copy en portugués. Un listing para Brasil
> con texto en español/inglés se vería a medias. Hacerlo bien necesita copy pt-BR nativo (trabajo aparte).

## Contenido (todo es-AR, DNA-checked, sin trademark del torneo)

| Archivo | Campo Play | Límite | Actual |
|---|---|---|---|
| `title.txt` | App name | ≤30 | 29 — **sin cambio** (se mantiene la marca) |
| `short_description.txt` | Short description | ≤80 | 73 |
| `full_description.txt` | Full description | ≤4000 | 3040 |
| `feature-graphic-1024x500-es-AR.png` | Feature graphic | 1024×500 | **neutro (recomendado)** |
| `feature-graphic-ar-accent-1024x500-es-AR.png` | Feature graphic | 1024×500 | variante con acento AR sutil |

- **Icono y screenshots:** se **reusan los de la ficha por defecto** (no hace falta regenerarlos). El custom listing
  hereda lo que no redefinís.
- **Feature graphic — dos variantes para evaluar en vivo:**
  - **Neutro** (recomendado): brand mark de Bomp + energía "El grito". Identidad intacta, sin patriotismo.
  - **Acento AR** (`-ar-accent-`): igual, + una tri-franja celeste/blanco/celeste bajo la tagline (guiño argentino
    **sin bandera literal**, para no romper el registro íntimo de la marca). Subí una sola; compará y elegí.

## Cargar el custom listing (paso a paso)

1. **Play Console** → app **Bomp** → **Grow** → **Store presence** → **Custom store listings**.
2. **Create listing** → **Add listing** → tipo **By country/region** (o "Targeted to countries").
3. Targeteá **Argentina** y nombrá el listing (interno) p. ej. `Fútbol 2026 — AR`.
4. Pegá los textos:
   - **App name** → `title.txt` (sin cambio, mantiene "Bomp - Las voces de los tuyos").
   - **Short description** → `short_description.txt`.
   - **Full description** → `full_description.txt`.
5. **Graphics → Feature graphic:** subí UNA de las dos variantes. Icono y screenshots: dejá heredar la ficha default.
6. **Save** → mandar a **review** (cambio de ficha; suele tardar poco).
7. **Revertir al terminar el torneo:** Custom store listings → borrar/desactivar `Fútbol 2026 — AR`. Argentina vuelve
   a ver la ficha por defecto, sin tocar nada más.

## Regenerar imágenes

```bash
rsvg-convert -w 1024 -h 500 feature-graphic.svg           -o feature-graphic-1024x500-es-AR.png
rsvg-convert -w 1024 -h 500 feature-graphic-ar-accent.svg -o feature-graphic-ar-accent-1024x500-es-AR.png
```

## Checklist DNA (pasa)

- ✔ Sin marca/trademark del torneo, FIFA, trofeo, escudos ni selección (la franja celeste es bandera nacional, no IP de FIFA).
- ✔ Heart-first: abre por la emoción del grito de los tuyos, no por compartir.
- ✔ Sin anti-tags (viralizá / amigos-seguidores como CTA / soundboard-botonera), sin términos reservados, sin claims de permanencia.
- ✔ Manifiesto invariante cierra la full description; glosario Bomp/Bomper/Bompeable según brand-dna §4.
