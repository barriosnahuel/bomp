---
name: exec-report
description: Generate Bomp's weekly executive report from BigQuery (business / product-funnel / quality / growth / performance), delivered in Spanish. Single source of truth for the weekly scheduled report's queries, methodology, and output format. Invoked by the Friday scheduled task (which is a thin pointer to this skill).
---

# exec-report

You are a product analyst. Generate the WEEKLY EXECUTIVE REPORT of the "Bomp" app (package
`com.github.barriosnahuel.vossosunboton`, Android — a soundboard where the user creates audio buttons)
and deliver it as a message in this conversation, **in Spanish**, in an executive and concise tone.
Do not invent numbers: if a source has no data, say so explicitly.

## Data source

Use the "Google Cloud BigQuery" connector (`execute_sql_readonly`, `list_dataset_ids`, `list_table_ids`,
`get_table_info`). Run ALL queries with `projectId = "bomp-prod"`. ALWAYS use `SAFE_DIVIDE`. Time-windowed
queries use `CURRENT_TIMESTAMP()`.

## Datasets and tables

- **Sessions (engagement):** `bomp-prod.firebase_sessions.com_github_barriosnahuel_vossosunboton_ANDROID`
  — version in `application.display_version` (name) and `application.build_version` (code).
- **Performance:** `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID` —
  version in `app_display_version` and `app_build_version`.
- **Quality (Crashlytics):** dataset `bomp-prod.firebase_crashlytics` — `list_table_ids` first; if there
  are no tables, mark "sin crashes registrados aún (buena señal preliminar)". Version in
  `application.display_version` / `application.build_version`.
- **Business (Play Console):** dataset `bomp-prod.play_console` — `list_table_ids` first; tables with the
  `bomp` suffix (`Installs_overview_bomp`, `Installs_app_version_bomp`, `Installs_country_bomp`,
  `Store_Performance_country_bomp`, `Store_Performance_traffic_source_bomp`, etc.). Play identifies
  version by CODE in `App_Version_Code`. If empty, "datos de Play aún no disponibles".
- **Product / custom events (GA4):** `list_dataset_ids` and look for a dataset starting with `analytics_`.
  If it does NOT exist, mark Product as pending GA4 → BigQuery and don't invent. If it exists, tables
  `analytics_XXX.events_*`. Version in `app_info.version`. Fields: `event_name`, `event_timestamp`,
  `user_pseudo_id`, `event_params` (repeated: key/value), `user_properties` (repeated). Date filter with
  `_TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', ...)`. Read a param with
  `(SELECT value.string_value FROM UNNEST(event_params) WHERE key='source')` or `value.int_value` per type.

## Versions (cross-cutting dimension)

The app can have several active versions and new releases are key events. Mapping: GA4/Firebase use the
NAME (2.1.0, 2.2.0); Play uses the CODE (`App_Version_Code` = Firebase's `build_version`). Rules:

1. Compute the share of active users per version (GA4, distinct users by `app_info.version`; if a user
   appears in two versions, count them in the most recent one).
2. ALWAYS CUT BY VERSION in QUALITY and PERFORMANCE (crashes/ANRs attributed; startup and slow/frozen
   frames new vs previous; mark regressions/improvements).
3. For the other axes: aggregated by default; open by version only if notable (≥~2× or an event/crash
   exclusive to one version).
4. If there's a new version in the window, its release health is a first-level topic.

**Methodology for QUALITY and PERFORMANCE version cuts (anti-false-alarm — this is the lesson of
[`docs/perf-investigations/0001`](../../../docs/perf-investigations/0001-2.2.0-startup-false-alarm.md);
see the `/perf-report-triage` skill):** the field sample is small, so a raw mean over a handful of events
is dominated by a single bad-actor device and manufactures fake regressions. Therefore, for startup and
frame metrics (and version-cut quality):

- Use the **median** (`APPROX_QUANTILES(...)[OFFSET(1)]`), not `AVG`.
- **Segment by `device_name`**, not just by version — the only honest comparison is the *same device
  model across versions*. A version delta that doesn't reproduce on the same model is device-mix, not a
  regression.
- **Flag single-device domination:** if one `device_name` carries most of a cell's samples, also report
  the figure excluding it.
- **Minimum-sample guard:** if a version×device cell has too few samples to be meaningful, label it
  "muestra insuficiente" instead of reporting a delta.
- A single bad device must not become a release-regression headline. Only call a regression real when the
  *same device model* regresses across versions.

## Window

Compare the last 7 days vs the previous 7 days whenever there is data.

## Content — axes

(The event names below are for building queries, NOT for showing in the body.)

1. **BUSINESS (Play):** new and net installs, uninstalls, active devices, rating and reviews, store-listing
   conversion (store performance), subscriptions/revenue if present.

2. **PRODUCT / FUNNEL (GA4):** if `analytics_*` exists, compute:
   a) ACTIVATION: `first_open` → `first_sound_add` → `first_sound_play` → `first_share`. % conversion
      between steps.
   b) CREATION CHANNELS: `sound_add` broken down by param `source` — above all `import` vs `record`
      (import a file vs record in-app), plus others. How users create Bomps.
   c) IMPORT HUB FUNNEL: `import_hub_opened` → (`import_hub_import_selected` | `import_hub_record_selected`)
      → `sound_add` with `source=import/record`. Conversion and drop-off per stage. Treat
      `source=onboarding_finish` as a separate cohort (don't put it in the denominator of proactive intent
      `fab`/`my_sounds_empty_state`).
   d) RECORDER FUNNEL (new feature, ADR 0019): `import_hub_record_selected` → `record_permission_result`
      (mic GRANT RATE = granted true/total) → `recording_completed` → `sound_add` with `source=record`.
      Mark where people fall off. Draft recovery: `recording_draft_banner_shown` → `recording_draft_resumed`
      vs `recording_draft_discarded` (resume rate).
   e) ONBOARDING TOUR (3 steps, `step_key` ∈ import/organize/bompear): `onboarding_opened` →
      `onboarding_step_viewed` → `onboarding_completed` vs `onboarding_dismissed`. Completion rate =
      completed/opened. Drop-off step = last `step_key` of the dismissed. NOTE: `onboarding_step_viewed` is
      re-emitted on back/re-entry → use `COUNT(DISTINCT user_pseudo_id)` per `step_key`, not `COUNT(*)`.
      Build the funnel over `step_key` (not over the step index). Separate by `source` (import_hub vs
      my_sounds_empty_state).
   f) WELCOME STICKER (passive onboarding): `welcome_sticker_shown` → `welcome_sticker_play` →
      `welcome_sticker_completed` vs `welcome_sticker_dismissed`.
   g) FRICTION: `sound_add_abandoned_after_error` (+ param `reason`); rate of `sound_add` with
      `name_hit_limit=true`; `duplicate_name_hint_shown` vs `duplicate_name_hint_play`.
   h) ENGAGEMENT: `sound_play` and `sound_add` totals and per active user; `pin_toggle`, `visibility_toggle`;
      milestones (`event_name LIKE 'milestone_sounds_%'`).
   i) VIRALITY: `share` and `first_share` (sharers / actives rate).
   j) FEATURE ADOPTION: Collections (`collection_create` by `scope=public/private` and by `source`;
      `collection_audio_toggle`, `collection_filter_apply`); Vault (`vault_unlock` rate `granted=true`;
      `vault_unprotected_warning_shown`; `vault_search_unlock_cta_shown`).
   k) UNMET DEMAND: `search_zero_results` (+ `query_length`).
   l) MONETIZATION: `about_gratitude_cafecito_open` + `about_gratitude_kofi_open`.
   m) NAVIGATION: `screen_view` by param `screen_name` (my_sounds, explore_sounds, vault, vault_listen,
      about, search_sound, add_sound, edit_sound, manage_collections, onboarding, record_sound).

   Available user properties (to segment if useful): `current_sounds`, `current_pinned`,
   `current_public_colls`, `current_private_colls`, `current_audios_in_colls`, `current_public_default`,
   `current_public_custom`, `current_vault_default`, `current_vault_custom`, `lifetime_shares`,
   `lifetime_plays`, `lifetime_coll_creates`, `lifetime_coll_deletes`, `lifetime_coll_renames`,
   `lifetime_coll_assigns`, `lifetime_vault_unlocks`.

   GA4 reference query: `SELECT event_name, COUNT(*) n, COUNT(DISTINCT user_pseudo_id) usuarios FROM
   `bomp-prod.analytics_XXX.events_*` WHERE _TABLE_SUFFIX BETWEEN
   FORMAT_DATE('%Y%m%d',DATE_SUB(CURRENT_DATE(),INTERVAL 7 DAY)) AND
   FORMAT_DATE('%Y%m%d',CURRENT_DATE()) GROUP BY event_name ORDER BY n DESC`.

   Prioritize funnels with signal; if a funnel has 0 events, summarize it in one line, don't break it down.

3. **QUALITY:** crash-free, number and top crashes (Crashlytics), ANRs (Play) — ALWAYS broken down by
   version, applying the anti-false-alarm methodology above (median + per-device + min-N guard; a single
   device shouldn't define release health).

4. **GROWTH/ENGAGEMENT (Firebase sessions):** sessions, unique devices (DAU proxy), cold starts
   (`session_index=0`), 7d vs prior 7d trend, geography. If GA4 is enabled, real users/retention come from
   the Product axis.

5. **PERFORMANCE:** startup (`_app_start`, `trace_info.duration_us` µs→ms); `slow_frame_ratio` and
   `frozen_frame_ratio` per screen (SCREEN_TRACE, `trace_info.screen_info`); network latency and error %
   (NETWORK_REQUEST, `response_code>=400`, `SAFE_DIVIDE`) — ALWAYS comparing new vs previous version, and
   ALWAYS with the anti-false-alarm methodology: **median (not mean), segmented by `device_name`, with the
   single-device-outlier flag and the min-N guard.** Report startup as the per-version median plus the
   same-device check; never headline a startup/frame regression that doesn't reproduce on the same device
   model.

## Reference queries (validated)

- Engagement 7d vs 7d: `SELECT COUNTIF(event_timestamp>=TIMESTAMP_SUB(CURRENT_TIMESTAMP(),INTERVAL 7 DAY))
  last7, COUNTIF(event_timestamp<TIMESTAMP_SUB(CURRENT_TIMESTAMP(),INTERVAL 7 DAY) AND
  event_timestamp>=TIMESTAMP_SUB(CURRENT_TIMESTAMP(),INTERVAL 14 DAY)) prev7 FROM
  `bomp-prod.firebase_sessions.com_github_barriosnahuel_vossosunboton_ANDROID` WHERE
  event_type='SESSION_START';`

- Performance per version (MEDIAN + sample size for the min-N guard):
  `SELECT app_display_version ver, COUNTIF(event_name='_app_start') n_start,
  ROUND(APPROX_QUANTILES(IF(event_name='_app_start',trace_info.duration_us,NULL),2)[OFFSET(1)]/1000,1)
  appstart_p50_ms,
  ROUND(APPROX_QUANTILES(IF(event_name='_st_LandingActivity',trace_info.screen_info.slow_frame_ratio,NULL),2)[OFFSET(1)],3)
  landing_slow_p50,
  ROUND(APPROX_QUANTILES(IF(event_name='_st_LandingActivity',trace_info.screen_info.frozen_frame_ratio,NULL),2)[OFFSET(1)],3)
  landing_frozen_p50 FROM `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID`
  GROUP BY ver ORDER BY ver;`

- Performance — DECISIVE same-device check (a version delta is only real if the same device model
  regresses; otherwise it's device-mix). If one device dominates a version, recompute its median excluding
  that device:
  `SELECT device_name, app_build_version code, COUNT(*) n,
  ROUND(APPROX_QUANTILES(trace_info.duration_us,2)[OFFSET(1)]/1000,1) appstart_p50_ms FROM
  `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID` WHERE
  event_type='DURATION_TRACE' AND event_name='_app_start' GROUP BY device_name, code ORDER BY device_name,
  code;`

- GA4 version share: `SELECT app_info.version ver, COUNT(DISTINCT user_pseudo_id) usuarios FROM
  `bomp-prod.analytics_XXX.events_*` WHERE _TABLE_SUFFIX BETWEEN
  FORMAT_DATE('%Y%m%d',DATE_SUB(CURRENT_DATE(),INTERVAL 7 DAY)) AND
  FORMAT_DATE('%Y%m%d',CURRENT_DATE()) GROUP BY ver ORDER BY usuarios DESC;`

- Network (hardened): `SELECT COUNT(*) total, COUNTIF(network_info.response_code>=400) errors,
  ROUND(SAFE_DIVIDE(COUNTIF(network_info.response_code>=400),COUNT(*))*100,1) err_pct FROM
  `bomp-prod.firebase_performance.com_github_barriosnahuel_vossosunboton_ANDROID` WHERE
  event_type='NETWORK_REQUEST';`

## Output style & format

Render the report EXACTLY per the spec below. **The output is in Spanish** and the format below is the
output contract — keep it as-is.

=== ESTILO Y FORMATO (CRÍTICO — leerse de un vistazo, insight primero) ===

Reglas de redacción:

- INSIGHT PRIMERO, en castellano y negrita. NO abras con el nombre del evento ni una métrica cruda.

- NÚMEROS como sub-bullets de apoyo, en lenguaje natural. NUNCA escribas en el cuerpo cosas como `first_sound_add=0`, `filter_apply=true` ni nombres de eventos/params en formato código.

- PROHIBIDO varias métricas por línea separadas por punto y coma. Una idea por bloque; cada cifra en su sub-bullet.

- Lo técnico (nombres de eventos, params sin poblar, gaps de export, mapeo de versión, legacy_sounds_recovered) va SOLO en una sección final "*Nota técnica (instrumentación):*", separada de los insights.

Semáforos: 🟢 ok · 🟡 atención · 🔴 alerta · ⚪ sin datos aún.

Estructura:

- Encabezado: "# 📊 Reporte Ejecutivo — Bomp — <fecha>" + línea de leyenda de semáforos.

- "## 📡 Estado de fuentes": una viñeta por fuente con semáforo y nombre en negrita; opcional blockquote de contexto (ej. volumen bajo → leer en absolutos).

- "## 🆕 Adopción de versión <sem>": share de usuarios por versión (nueva vs anterior), velocidad de adopción, y una línea de salud del release (si la nueva mejora/empeora algo notorio, con remisión al eje). Si hay una sola versión activa, decilo en una línea.

- Un "## " por eje con ícono + semáforo: "## 🏢 Negocio · Play <sem>", "## 🧭 Producto / Funnel <sem>", "## 🛡️ Calidad <sem>", "## 📈 Crecimiento / Engagement <sem>", "## ⚡ Performance <sem>". En Calidad y Performance, mostrá el corte por versión.

- Dentro de cada eje agrupá por sub-tema; en ejes ricos (sobre todo Producto) usá subheadings "### <sem> <sub-tema en lenguaje natural>"; en ejes chicos, titular en negrita. Bajo cada sub-tema: 1 línea de insight en negrita + sub-bullets con cifras (resaltá el número clave).

- "## 🎯 Top 3 Action Items": numerados, prefijados con badge 🔴 **Alta** / 🟡 **Media** (o **Media-Alta**) / 🟢 **Baja**. Formato: "N. <badge> **<Prioridad> — <acción>.** *Por qué:* <justificación con números>". Una regresión notoria de release escala al Top 3. Prioridades según datos.

- Al final "*Nota técnica (instrumentación):*" si hay algo técnico (mapeo de versión ambiguo, params sin poblar, legacy recovery, gaps de export).

- Ejecutivo: sin relleno; emojis limitados a semáforos, íconos de heading y badges.
