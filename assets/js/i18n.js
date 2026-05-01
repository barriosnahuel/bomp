// ─────────────────────────────────────────────
// Bomp i18n — static, dependency-free.
//
// Supported locales (BCP 47):
//   es-AR  Spanish (Argentina) — DEFAULT, baked into HTML
//   es-419 Spanish (Latin America)
//   es-ES  Spanish (Spain)
//   en     English
//   pt-BR  Portuguese (Brazil)
//
// Detection order:
//   1. URL ?hl=<locale>   (Google's standard "hl" parameter)
//   2. localStorage["bomp-locale"]
//   3. navigator.languages / navigator.language
//   4. fallback "es-AR"
//
// Strings live in the LANG dictionary below. HTML elements opt in via:
//   data-i18n="key"           → element.textContent
//   data-i18n-html="key"      → element.innerHTML (markup-safe; only our own strings)
//   data-i18n-attr="attr:key" → element.setAttribute(attr, value)
//   data-i18n-pages="page"    → "index" | "legal" | "404" — limits which strings load
//
// Brand DNA proper nouns (Bomp, Bomper, Bompear, Bompeable) NEVER translate.
// Pronunciations (/bomp/, /bom·per/, etc.) are universal and stay verbatim.
// ─────────────────────────────────────────────

(function () {
  var SUPPORTED = ["es-AR", "es-419", "es-ES", "en", "pt-BR"];
  var DEFAULT = "es-AR";

  // ── Dictionary ─────────────────────────────
  var LANG = {
    "es-AR": {
      "html.lang": "es-AR",
      "head.title.index": "Bomp — Las voces de los tuyos",
      "head.title.privacy": "Política de privacidad — Bomp",
      "head.title.dataSafety": "Seguridad de los datos — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Coleccioná las voces que te importan: la risa de tu vieja, el audio del amigo, la frase del jefe. Tuyas, primero. Para mandar, después.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Detalle de qué datos recolecta Bomp en su ficha de Google Play, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Política de privacidad",
      "nav.dataSafety": "Seguridad de los datos",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">Siempre con vos</span>.",
      "hero.sub": "Guardá los audios que te llegan por WhatsApp, Telegram o WeChat. Apodalos para que sean tuyos. Tocalos cuando los necesites — y si querés, mandalos.",
      "hero.playBadge.aria": "Resérvalo en Google Play (próximamente)",

      "decoy.1": "Risa de mi vieja",
      "decoy.2": "¡Che, capo!",
      "decoy.3": "Llegué",
      "decoy.4": "La frase del jefe",

      "sticker.aria": "Tocá para escuchar a Bomp",
      "sticker.label.idle": "tocá",
      "sticker.label.playing": "sonando",
      "sticker.caption.playing": "Bompeando…",
      "sticker.caption.fallback": "Tu navegador está en silencio. Tocá para activar la voz de Bomp.",

      "section.howItWorks.num": "01 · Cómo funciona",
      "section.howItWorks.title": "Guardá. Apodá. Bompeá.",
      "section.howItWorks.lede": "Tres toques. Cero cuentas, cero nube, cero amigos a los que tengas que sumar.",
      "step.1.num": "01 · Guardar",
      "step.1.title": "Mandá el audio a Bomp.",
      "step.1.body": "Desde WhatsApp, Telegram o WeChat: tocás \"compartir\" y elegís Bomp. El audio queda guardado en tu teléfono — solo en tu teléfono.",
      "step.2.num": "02 · Apodar",
      "step.2.title": "Hacelo tuyo. Ponele el apodo que solo vos vas a entender.",
      "step.2.body": "El que te haga reír cuando lo veas en la lista. Bomp no opina ni corrige.",
      "step.2.chip.1": "Risa de mi vieja",
      "step.2.chip.2": "Che capo",
      "step.3.num": "03 · Bompear",
      "step.3.title": "Un toque, suena.",
      "step.3.body": "Sin esperas, sin \"abriendo audio…\", sin pantalla cargando. Bomp.",

      "section.app.num": "02 · La app",
      "section.app.title": "Tus voces, en orden.",
      "shot.list.title": "Mis audios",
      "shot.list.label1": "MAMÁ DICE QUÉ",
      "shot.list.label2": "RISA DE PEDRO",
      "shot.list.label3": "LLEGUÉ",
      "shot.list.label4": "LA FRASE DEL JEFE",
      "shot.share.title": "Compartir",
      "shot.share.audioName": "Risa de Pedro",
      "shot.share.audioDuration": "0:02",
      "shot.share.app1": "WhatsApp",
      "shot.share.app2": "Telegram",
      "shot.share.app3": "Más",
      "shot.chat.contact": "Lucía",
      "shot.chat.status": "en línea",
      "shot.chat.audioDuration": "0:02",
      "shot.chat.reaction": "JAJAJAJA no puedo",
      "shot.caption.identity": "↳ Las voces que te importan, siempre con vos.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día — el tuyo, primero.",
      "shot.caption.reception": "↳ Y si querés, del otro lado alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Vos, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "Activar un Bomp: escucharlo o mandarlo. Primero para vos, después para los otros. Conjugación: bompo, bompás, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te encantan y no querés perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo querés, guardalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo querés mejorar, romper, traducir o forkear: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "¿Te alegró el día? Invitame un café virtual.",
      "section.donate.body": "Bomp es gratis y sin publicidad. Si lo disfrutaste y querés decirme gracias, dejame un café virtual desde acá. No hace falta — con que lo uses y lo compartas ya suma.",
      "donate.cafecito.alt": "Invitame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para vos.",
      "footer.product": "Producto",
      "footer.product.howItWorks": "Cómo funciona",
      "footer.product.theApp": "La app",
      "footer.product.googlePlay": "Google Play",
      "footer.legal": "Legales",
      "footer.community": "Comunidad",
      "footer.copyright": "© __YEAR__ · Bomp · AGPL-3.0",
      "footer.pron": "Bomp /bomp/",
      "footer.lang.label": "Idioma",

      "404.num": "404 · Página no encontrada",
      "404.title.html": "Esta puerta no <em>abre</em>.",
      "404.sub": "Volvé al hub. Te redirigimos en 5 segundos.",
      "404.cta": "← Volver al hub",

      "legal.eyebrow": "Documento legal",
      "legal.toc.heading": "Contenido",
      "legal.back.hub": "← Volver al hub",
      "legal.back.top": "↑ Volver arriba",
      "footer.embeds.notice": "Los botones de donación de este sitio cargan recursos de Cafecito y Ko-fi (CDNs externos). Cada uno se rige por su propia política de privacidad.",

      // ── Legal · privacy policy ────────────────
      "pp.meta.app": "App: <strong>Bomp</strong>",
      "pp.meta.version": "Versión política: <strong>1.0</strong>",
      "pp.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "pp.toc.s01": "01 · Encabezado de confianza",
      "pp.toc.s02": "02 · Datos sensibles (audio)",
      "pp.toc.s03": "03 · Ecosistema de terceros",
      "pp.toc.s04": "04 · Política de menores",
      "pp.toc.s05": "05 · Derechos del usuario (ARCO)",
      "pp.toc.s06": "06 · Vigencia y cambios",
      "pp.title": "Política de privacidad",
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") maneja los datos del usuario (\"vos\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importás viven en tu teléfono. La app no tiene cuentas ni nube de usuario; sí envía a Google Firebase un conjunto acotado de datos pseudónimos (logs de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva por <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que vos elegís importar</strong> desde otras apps (WhatsApp, Telegram, WeChat) usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si vos lo tenés activo en tu teléfono (Configuración del sistema → Sistema → Backup), Google respalda los datos de Bomp (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Es Google quien gestiona ese backup; Bomp no lo controla ni accede a él, y podés desactivarlo desde la configuración de tu teléfono. Si preferís que tus audios no se respalden, desactivá el backup antes de importarlos.",
      "pp.s02.li3": "Cuando vos compartís un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borrás un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalás la app, todos los audios se eliminan junto con los datos de la app (el backup en tu Google Drive se purga según la política de Google Backup).",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — sistema base de Google Play para distribución de la app y entrega de feature modules dinámicos (la pantalla \"Agregar botón\" se descarga bajo demanda).",
      "pp.s03.li2": "<strong>Firebase Crashlytics</strong> — diagnóstico de fallos. Recopila <em>logs de fallos</em> de forma pseudónima cuando la app crashea, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Firebase Performance Monitoring</strong> — recopila <em>diagnósticos de rendimiento</em> agregados (tiempo de arranque, uso de memoria, latencias) con propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Firebase Analytics</strong> — recopila <em>eventos agregados de uso</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos pseudónimos que sí compartimos con Google Firebase (logs de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analytics agregados; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recolecta nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los pseudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google Firebase sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y GDPR (UE) por diseño: no recolectamos datos directamente identificables. Si sos madre/padre/tutor y querés que purguemos el identificador de instalación pseudónimo asociado al dispositivo de un menor, escribinos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el GDPR (UE), tenés derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu teléfono: abrí la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombrá o re-importá audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: borralos desde la app, o desinstalá Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos pseudónimos en Firebase.",
      "pp.s05.body2": "Para los datos pseudónimos que viven en Google Firebase (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstalá la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Reseteá tu Advertising ID</strong> desde Configuración de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escribinos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu Firebase Installation ID y borramos el registro asociado en Firebase Console.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite pedir borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo GDPR Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu Installation ID), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, mandando el Installation ID, es lo que habilita el borrado puntual.",
      "pp.s06.body1": "Esta política entra en vigencia el 2026-04-26. Si modificamos términos significativos, actualizaremos la fecha de \"Última actualización\" arriba y publicaremos un changelog en <a href=\"https://github.com/barriosnahuel/bomp/releases\">GitHub Releases</a>.",
      "pp.s06.body2": "El código fuente de Bomp está disponible bajo licencia <a href=\"https://www.gnu.org/licenses/agpl-3.0.html\">AGPL-3.0</a> en <a href=\"https://github.com/barriosnahuel/bomp\">github.com/barriosnahuel/bomp</a>.",

      // ── Legal · data safety ───────────────────
      "ds.meta.app": "App: <strong>Bomp</strong>",
      "ds.meta.source": "Fuente: <strong>ficha de Bomp en Google Play</strong>",
      "ds.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "ds.toc.s01": "01 · Datos recolectados",
      "ds.toc.s02": "02 · Cuentas y eliminación",
      "ds.toc.s03": "03 · Cifrado en tránsito",
      "ds.intro": "Esta página replica las declaraciones de Bomp en el formulario de Data Safety de Google Play. La fuente de verdad pública es la <a href=\"https://play.google.com/store/apps/details?id=com.github.barriosnahuel.vossosunboton\">ficha de Bomp en Google Play</a>; si hubiera un conflicto entre esta página y lo que figura ahí, gana Play.",
      "ds.s01.intro": "Estos son los tipos de datos que Bomp declara recolectar en su ficha de Data Safety:",
      "ds.table.col1": "Tipo de dato",
      "ds.table.col2": "¿Por qué se recolecta?",
      "ds.table.col3": "¿Se comparte?",
      "ds.table.col4": "¿Es opcional?",
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que vos importás desde WhatsApp, Telegram o WeChat.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que vos elegís Bompear. Se almacenan solo en tu teléfono.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tenés es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los retiene hasta que vos los borres.",
      "ds.row2.col1": "<strong>Logs de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app crashea.</small>",
      "ds.row2.col2": "Diagnóstico de fallos vía Firebase Crashlytics. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row2.col4": "No — los logs pseudónimos se envían cuando hay un crash.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas pseudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento vía Firebase Performance Monitoring.",
      "ds.row3.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras vía Firebase Analytics. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tenés activo en tu teléfono, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Esto no aparece en la tabla porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: el backup va a tu cuenta de Google y es gestionado por Google, no por Bomp. Podés desactivarlo desde Configuración del sistema → Sistema → Backup.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usás email, no usás contraseña, no usás OAuth, no usás SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu teléfono y los borrás vos:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstalá la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos pseudónimos en Firebase: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu teléfono se borran al desinstalar, y los datos pseudónimos en Firebase no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google Firebase utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial."
    },

    // ── es-419 — neutral LATAM tuteo (no Cafecito; solo Ko-fi) ─
    "es-419": {
      "html.lang": "es-419",
      "head.title.index": "Bomp — Las voces de los tuyos",
      "head.title.privacy": "Política de privacidad — Bomp",
      "head.title.dataSafety": "Seguridad de los datos — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Colecciona las voces que te importan: la risa de mamá, el audio del amigo, la frase del jefe. Tuyas, primero. Para mandar, después.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Detalle de qué datos recolecta Bomp en su ficha de Google Play, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Política de privacidad",
      "nav.dataSafety": "Seguridad de los datos",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">Siempre contigo</span>.",
      "hero.sub": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat. Apódalos para que sean tuyos. Tócalos cuando los necesites — y si quieres, mándalos.",
      "hero.playBadge.aria": "Resérvalo en Google Play (próximamente)",

      "decoy.1": "Risa de mi mamá",
      "decoy.2": "¡Hey wey!",
      "decoy.3": "Llegué",
      "decoy.4": "La frase del jefe",

      "sticker.aria": "Toca para escuchar a Bomp",
      "sticker.label.idle": "toca",
      "sticker.label.playing": "sonando",
      "sticker.caption.playing": "Bompeando…",
      "sticker.caption.fallback": "Tu navegador está en silencio. Toca para activar la voz de Bomp.",

      "section.howItWorks.num": "01 · Cómo funciona",
      "section.howItWorks.title": "Guarda. Apoda. Bompea.",
      "section.howItWorks.lede": "Tres toques. Cero cuentas, cero nube, cero amigos a los que tengas que sumar.",
      "step.1.num": "01 · Guardar",
      "step.1.title": "Manda el audio a Bomp.",
      "step.1.body": "Desde WhatsApp, Telegram o WeChat: tocas \"compartir\" y eliges Bomp. El audio queda guardado en tu teléfono — solo en tu teléfono.",
      "step.2.num": "02 · Apodar",
      "step.2.title": "Hazlo tuyo. Ponle el apodo que solo tú entiendas.",
      "step.2.body": "El que te haga reír cuando lo veas en la lista. Bomp no opina ni corrige.",
      "step.2.chip.1": "Risa de mi mamá",
      "step.2.chip.2": "Hey wey",
      "step.3.num": "03 · Bompear",
      "step.3.title": "Un toque, suena.",
      "step.3.body": "Sin esperas, sin \"abriendo audio…\", sin pantalla cargando. Bomp.",

      "section.app.num": "02 · La app",
      "section.app.title": "Tus voces, en orden.",
      "shot.list.title": "Mis audios",
      "shot.list.label1": "MI MAMÁ DIJO QUÉ",
      "shot.list.label2": "RISA DE PEDRO",
      "shot.list.label3": "LLEGUÉ",
      "shot.list.label4": "LA FRASE DEL JEFE",
      "shot.share.title": "Compartir",
      "shot.share.audioName": "Risa de Pedro",
      "shot.share.audioDuration": "0:02",
      "shot.share.app1": "WhatsApp",
      "shot.share.app2": "Telegram",
      "shot.share.app3": "Más",
      "shot.chat.contact": "Lucía",
      "shot.chat.status": "en línea",
      "shot.chat.audioDuration": "0:02",
      "shot.chat.reaction": "JAJAJAJA no puedo",
      "shot.caption.identity": "↳ Las voces que te importan, siempre contigo.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día — el tuyo, primero.",
      "shot.caption.reception": "↳ Y si quieres, del otro lado alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Tú, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "Activar un Bomp: escucharlo o mandarlo. Primero para ti, después para los otros. Conjugación: bompo, bompeas, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te encantan y no quieres perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo quieres, guárdalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo quieres mejorar, romper, traducir o hacerle un fork: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "¿Te alegró el día? Invítame un café virtual.",
      "section.donate.body": "Bomp es gratis y sin publicidad. Si lo disfrutaste y quieres decirme gracias, déjame un café virtual desde aquí. No hace falta — con que lo uses y lo compartas ya suma.",
      "donate.cafecito.alt": "Invítame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para ti.",
      "footer.product": "Producto",
      "footer.product.howItWorks": "Cómo funciona",
      "footer.product.theApp": "La app",
      "footer.product.googlePlay": "Google Play",
      "footer.legal": "Legales",
      "footer.community": "Comunidad",
      "footer.copyright": "© __YEAR__ · Bomp · AGPL-3.0",
      "footer.pron": "Bomp /bomp/",
      "footer.lang.label": "Idioma",

      "404.num": "404 · Página no encontrada",
      "404.title.html": "Esta puerta no <em>abre</em>.",
      "404.sub": "Vuelve al hub. Te redirigimos en 5 segundos.",
      "404.cta": "← Volver al hub",

      "legal.eyebrow": "Documento legal",
      "legal.toc.heading": "Contenido",
      "legal.back.hub": "← Volver al hub",
      "legal.back.top": "↑ Volver arriba",
      "footer.embeds.notice": "El botón de donación de este sitio carga recursos de Ko-fi (CDN externo). Se rige por la política de privacidad de Ko-fi.",

      // ── Legal · privacy policy ────────────────
      "pp.meta.app": "App: <strong>Bomp</strong>",
      "pp.meta.version": "Versión política: <strong>1.0</strong>",
      "pp.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "pp.toc.s01": "01 · Encabezado de confianza",
      "pp.toc.s02": "02 · Datos sensibles (audio)",
      "pp.toc.s03": "03 · Ecosistema de terceros",
      "pp.toc.s04": "04 · Política de menores",
      "pp.toc.s05": "05 · Derechos del usuario (ARCO)",
      "pp.toc.s06": "06 · Vigencia y cambios",
      "pp.title": "Política de privacidad",
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") maneja los datos del usuario (\"tú\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importas viven en tu teléfono. La app no tiene cuentas ni nube de usuario; sí envía a Google Firebase un conjunto acotado de datos pseudónimos (logs de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva por <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que tú eliges importar</strong> desde otras apps (WhatsApp, Telegram, WeChat) usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu teléfono (Configuración del sistema → Sistema → Backup), Google respalda los datos de Bomp (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Es Google quien gestiona ese backup; Bomp no lo controla ni accede a él, y puedes desactivarlo desde la configuración de tu teléfono. Si prefieres que tus audios no se respalden, desactiva el backup antes de importarlos.",
      "pp.s02.li3": "Cuando tú compartes un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borras un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalas la app, todos los audios se eliminan junto con los datos de la app (el backup en tu Google Drive se purga según la política de Google Backup).",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — sistema base de Google Play para distribución de la app y entrega de feature modules dinámicos (la pantalla \"Agregar botón\" se descarga bajo demanda).",
      "pp.s03.li2": "<strong>Firebase Crashlytics</strong> — diagnóstico de fallos. Recopila <em>logs de fallos</em> de forma pseudónima cuando la app crashea, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Firebase Performance Monitoring</strong> — recopila <em>diagnósticos de rendimiento</em> agregados (tiempo de arranque, uso de memoria, latencias) con propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Firebase Analytics</strong> — recopila <em>eventos agregados de uso</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos pseudónimos que sí compartimos con Google Firebase (logs de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analytics agregados; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recolecta nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los pseudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google Firebase sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y GDPR (UE) por diseño: no recolectamos datos directamente identificables. Si eres madre/padre/tutor y quieres que purguemos el identificador de instalación pseudónimo asociado al dispositivo de un menor, escríbenos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el GDPR (UE), tienes derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu teléfono: abre la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombra o re-importa audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: bórralos desde la app, o desinstala Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos pseudónimos en Firebase.",
      "pp.s05.body2": "Para los datos pseudónimos que viven en Google Firebase (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstala la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Resetea tu Advertising ID</strong> desde Configuración de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escríbenos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu Firebase Installation ID y borramos el registro asociado en Firebase Console.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite pedir borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo GDPR Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu Installation ID), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, mandando el Installation ID, es lo que habilita el borrado puntual.",
      "pp.s06.body1": "Esta política entra en vigencia el 2026-04-26. Si modificamos términos significativos, actualizaremos la fecha de \"Última actualización\" arriba y publicaremos un changelog en <a href=\"https://github.com/barriosnahuel/bomp/releases\">GitHub Releases</a>.",
      "pp.s06.body2": "El código fuente de Bomp está disponible bajo licencia <a href=\"https://www.gnu.org/licenses/agpl-3.0.html\">AGPL-3.0</a> en <a href=\"https://github.com/barriosnahuel/bomp\">github.com/barriosnahuel/bomp</a>.",

      // ── Legal · data safety ───────────────────
      "ds.meta.app": "App: <strong>Bomp</strong>",
      "ds.meta.source": "Fuente: <strong>ficha de Bomp en Google Play</strong>",
      "ds.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "ds.toc.s01": "01 · Datos recolectados",
      "ds.toc.s02": "02 · Cuentas y eliminación",
      "ds.toc.s03": "03 · Cifrado en tránsito",
      "ds.intro": "Esta página replica las declaraciones de Bomp en el formulario de Data Safety de Google Play. La fuente de verdad pública es la <a href=\"https://play.google.com/store/apps/details?id=com.github.barriosnahuel.vossosunboton\">ficha de Bomp en Google Play</a>; si hubiera un conflicto entre esta página y lo que figura ahí, gana Play.",
      "ds.s01.intro": "Estos son los tipos de datos que Bomp declara recolectar en su ficha de Data Safety:",
      "ds.table.col1": "Tipo de dato",
      "ds.table.col2": "¿Por qué se recolecta?",
      "ds.table.col3": "¿Se comparte?",
      "ds.table.col4": "¿Es opcional?",
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que tú importas desde WhatsApp, Telegram o WeChat.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que tú eliges Bompear. Se almacenan solo en tu teléfono.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tienes es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los retiene hasta que tú los borres.",
      "ds.row2.col1": "<strong>Logs de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app crashea.</small>",
      "ds.row2.col2": "Diagnóstico de fallos vía Firebase Crashlytics. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row2.col4": "No — los logs pseudónimos se envían cuando hay un crash.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas pseudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento vía Firebase Performance Monitoring.",
      "ds.row3.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras vía Firebase Analytics. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recolectado). Procesado por Google Firebase como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu teléfono, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Esto no aparece en la tabla porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: el backup va a tu cuenta de Google y es gestionado por Google, no por Bomp. Puedes desactivarlo desde Configuración del sistema → Sistema → Backup.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usas email, no usas contraseña, no usas OAuth, no usas SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu teléfono y los borras tú:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstala la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos pseudónimos en Firebase: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu teléfono se borran al desinstalar, y los datos pseudónimos en Firebase no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google Firebase utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial."
    },

    // ── es-ES — Spain Spanish (Cafecito hidden; only Ko-fi) ───
    "es-ES": {
      "html.lang": "es-ES",
      "head.title.index": "Bomp — Las voces de los tuyos",
      "head.title.privacy": "Política de privacidad — Bomp",
      "head.title.dataSafety": "Seguridad de los datos — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Colecciona las voces que te importan: la risa de mamá, el audio del amigo, la frase del jefe. Tuyas, primero. Para mandar, después.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Detalle de qué datos recolecta Bomp en su ficha de Google Play, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Política de privacidad",
      "nav.dataSafety": "Seguridad de los datos",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">Siempre contigo</span>.",
      "hero.sub": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat. Apódalos para que sean tuyos. Tócalos cuando los necesites — y si quieres, mándalos.",
      "hero.playBadge.aria": "Resérvalo en Google Play (próximamente)",

      "decoy.1": "Risa de mi madre",
      "decoy.2": "¡Tío!",
      "decoy.3": "Ya estoy aquí",
      "decoy.4": "La frase del jefe",

      "sticker.aria": "Toca para escuchar a Bomp",
      "sticker.label.idle": "toca",
      "sticker.label.playing": "sonando",
      "sticker.caption.playing": "Bompeando…",
      "sticker.caption.fallback": "Tu navegador está en silencio. Toca para activar la voz de Bomp.",

      "section.howItWorks.num": "01 · Cómo funciona",
      "section.howItWorks.title": "Guarda. Apoda. Bompea.",
      "section.howItWorks.lede": "Tres toques. Cero cuentas, cero nube, cero amigos a los que sumar.",
      "step.1.num": "01 · Guardar",
      "step.1.title": "Manda el audio a Bomp.",
      "step.1.body": "Desde WhatsApp, Telegram o WeChat: tocas \"compartir\" y eliges Bomp. El audio se guarda en tu teléfono — solo en tu teléfono.",
      "step.2.num": "02 · Apodar",
      "step.2.title": "Hazlo tuyo. Ponle el mote que solo tú entiendas.",
      "step.2.body": "El que te haga reír cuando lo veas en la lista. Bomp no opina ni corrige.",
      "step.2.chip.1": "Risa de mi madre",
      "step.2.chip.2": "Tío",
      "step.3.num": "03 · Bompear",
      "step.3.title": "Un toque, suena.",
      "step.3.body": "Sin esperas, sin \"abriendo audio…\", sin pantalla cargando. Bomp.",

      "section.app.num": "02 · La app",
      "section.app.title": "Tus voces, ordenadas.",
      "shot.list.title": "Mis audios",
      "shot.list.label1": "MI MADRE DIJO QUÉ",
      "shot.list.label2": "RISA DE PEDRO",
      "shot.list.label3": "YA ESTOY AQUÍ",
      "shot.list.label4": "LA FRASE DEL JEFE",
      "shot.share.title": "Compartir",
      "shot.share.audioName": "Risa de Pedro",
      "shot.share.audioDuration": "0:02",
      "shot.share.app1": "WhatsApp",
      "shot.share.app2": "Telegram",
      "shot.share.app3": "Más",
      "shot.chat.contact": "Lucía",
      "shot.chat.status": "en línea",
      "shot.chat.audioDuration": "0:02",
      "shot.chat.reaction": "JAJAJAJA no puedo",
      "shot.caption.identity": "↳ Las voces que te importan, siempre contigo.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día — el tuyo, primero.",
      "shot.caption.reception": "↳ Y si quieres, del otro lado alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Tú, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "Activar un Bomp: escucharlo o mandarlo. Primero para ti, después para los otros. Conjugación: bompo, bompeas, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te molan y no quieres perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo quieres, guárdalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo quieres mejorar, romper, traducir o hacerle un fork: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "¿Te alegró el día? Invítame un café virtual.",
      "section.donate.body": "Bomp es gratis y sin publicidad. Si lo disfrutaste y quieres darme las gracias, déjame un café virtual desde aquí. No hace falta — con que lo uses y lo compartas ya suma.",
      "donate.cafecito.alt": "Invítame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para ti.",
      "footer.product": "Producto",
      "footer.product.howItWorks": "Cómo funciona",
      "footer.product.theApp": "La app",
      "footer.product.googlePlay": "Google Play",
      "footer.legal": "Legales",
      "footer.community": "Comunidad",
      "footer.copyright": "© __YEAR__ · Bomp · AGPL-3.0",
      "footer.pron": "Bomp /bomp/",
      "footer.lang.label": "Idioma",

      "404.num": "404 · Página no encontrada",
      "404.title.html": "Esta puerta no <em>abre</em>.",
      "404.sub": "Vuelve al hub. Te redirigimos en 5 segundos.",
      "404.cta": "← Volver al hub",

      "legal.eyebrow": "Documento legal",
      "legal.toc.heading": "Contenido",
      "legal.back.hub": "← Volver al hub",
      "legal.back.top": "↑ Volver arriba",
      "footer.embeds.notice": "El botón de donación de este sitio carga recursos de Ko-fi (CDN externo). Se rige por la política de privacidad de Ko-fi.",

      // ── Legal · privacy policy ────────────────
      "pp.meta.app": "App: <strong>Bomp</strong>",
      "pp.meta.version": "Versión política: <strong>1.0</strong>",
      "pp.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "pp.toc.s01": "01 · Encabezado de confianza",
      "pp.toc.s02": "02 · Datos sensibles (audio)",
      "pp.toc.s03": "03 · Ecosistema de terceros",
      "pp.toc.s04": "04 · Política de menores",
      "pp.toc.s05": "05 · Derechos del usuario (ARCO)",
      "pp.toc.s06": "06 · Vigencia y cambios",
      "pp.title": "Política de privacidad",
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") trata los datos del usuario (\"tú\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importas viven en tu móvil. La app no tiene cuentas ni nube de usuario; sí envía a Google Firebase un conjunto acotado de datos seudónimos (registros de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva en <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que tú decides importar</strong> desde otras apps (WhatsApp, Telegram, WeChat) usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Copia de seguridad automática de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu móvil (Ajustes del sistema → Sistema → Copia de seguridad), Google respalda los datos de Bomp (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Es Google quien gestiona esa copia; Bomp no la controla ni accede a ella, y puedes desactivarla desde los ajustes de tu móvil. Si prefieres que tus audios no se respalden, desactiva la copia antes de importarlos.",
      "pp.s02.li3": "Cuando tú compartes un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borras un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalas la app, todos los audios se eliminan junto con los datos de la app (la copia en tu Google Drive se purga según la política de Google Backup).",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — sistema base de Google Play para distribución de la app y entrega de feature modules dinámicos (la pantalla \"Añadir botón\" se descarga bajo demanda).",
      "pp.s03.li2": "<strong>Firebase Crashlytics</strong> — diagnóstico de fallos. Recopila <em>registros de fallos</em> de forma seudónima cuando la app falla, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Firebase Performance Monitoring</strong> — recopila <em>diagnósticos de rendimiento</em> agregados (tiempo de arranque, uso de memoria, latencias) con el propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Firebase Analytics</strong> — recopila <em>eventos agregados de uso</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con el propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos seudónimos que sí compartimos con Google Firebase (registros de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analítica agregada; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recoge nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los seudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (registros de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google Firebase sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y RGPD (UE) por diseño: no recopilamos datos directamente identificables. Si eres madre/padre/tutor y quieres que purguemos el identificador de instalación seudónimo asociado al dispositivo de un menor, escríbenos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el RGPD (UE), tienes derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu móvil: abre la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombra o re-importa audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: bórralos desde la app, o desinstala Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos seudónimos en Firebase.",
      "pp.s05.body2": "Para los datos seudónimos que viven en Google Firebase (registros de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstala la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Restablece tu Advertising ID</strong> desde Ajustes de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Solicitud manual</strong>: escríbenos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu Firebase Installation ID y borramos el registro asociado en Firebase Console.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite solicitar el borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo el RGPD Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu Installation ID), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, enviando el Installation ID, es lo que habilita el borrado puntual.",
      "pp.s06.body1": "Esta política entra en vigor el 2026-04-26. Si modificamos términos significativos, actualizaremos la fecha de \"Última actualización\" arriba y publicaremos un changelog en <a href=\"https://github.com/barriosnahuel/bomp/releases\">GitHub Releases</a>.",
      "pp.s06.body2": "El código fuente de Bomp está disponible bajo licencia <a href=\"https://www.gnu.org/licenses/agpl-3.0.html\">AGPL-3.0</a> en <a href=\"https://github.com/barriosnahuel/bomp\">github.com/barriosnahuel/bomp</a>.",

      // ── Legal · data safety ───────────────────
      "ds.meta.app": "App: <strong>Bomp</strong>",
      "ds.meta.source": "Fuente: <strong>ficha de Bomp en Google Play</strong>",
      "ds.meta.lastUpdated": "Última actualización: <strong>2026-04-26</strong>",
      "ds.toc.s01": "01 · Datos recopilados",
      "ds.toc.s02": "02 · Cuentas y eliminación",
      "ds.toc.s03": "03 · Cifrado en tránsito",
      "ds.intro": "Esta página replica las declaraciones de Bomp en el formulario de Data Safety de Google Play. La fuente de verdad pública es la <a href=\"https://play.google.com/store/apps/details?id=com.github.barriosnahuel.vossosunboton\">ficha de Bomp en Google Play</a>; si hubiera un conflicto entre esta página y lo que figura allí, gana Play.",
      "ds.s01.intro": "Estos son los tipos de datos que Bomp declara recopilar en su ficha de Data Safety:",
      "ds.table.col1": "Tipo de dato",
      "ds.table.col2": "¿Por qué se recopila?",
      "ds.table.col3": "¿Se comparte?",
      "ds.table.col4": "¿Es opcional?",
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que tú importas desde WhatsApp, Telegram o WeChat.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que tú decides Bompear. Se almacenan solo en tu móvil.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tienes es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los conserva hasta que tú los borres.",
      "ds.row2.col1": "<strong>Registros de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app falla.</small>",
      "ds.row2.col2": "Diagnóstico de fallos vía Firebase Crashlytics. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recopilado). Procesado por Google Firebase como sub-procesador.",
      "ds.row2.col4": "No — los registros seudónimos se envían cuando hay un fallo.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas seudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento vía Firebase Performance Monitoring.",
      "ds.row3.col3": "No (solo recopilado). Procesado por Google Firebase como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras vía Firebase Analytics. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recopilado). Procesado por Google Firebase como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Copia de seguridad automática de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu móvil, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app, hasta 25 MB. Esto no aparece en la tabla porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: la copia va a tu cuenta de Google y la gestiona Google, no Bomp. Puedes desactivarla desde Ajustes del sistema → Sistema → Copia de seguridad.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usas email, no usas contraseña, no usas OAuth, no usas SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu móvil y los borras tú:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstala la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos seudónimos en Firebase: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu móvil se borran al desinstalar, y los datos seudónimos en Firebase no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google Firebase utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial."
    },

    // ── en — English ──────────────────────────
    "en": {
      "html.lang": "en",
      "head.title.index": "Bomp — The voices of your people",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Collect the voices that matter to you — your mom's laugh, your friend's audio, your boss's catchphrase. Yours, first. To share, after.",
      "head.description.privacy": "Bomp's privacy policy — what data is handled, how it is stored, what third parties do, user rights.",
      "head.description.dataSafety": "What data Bomp collects in its Google Play listing, why, whether shared, and whether optional.",
      "skip.link": "Skip to content",
      "nav.howItWorks": "How it works",
      "nav.theApp": "The app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Toggle theme",

      "hero.eyebrow": "Beta · Android · free",
      "hero.title.html": "The voices that matter.<br><span class=\"acid\">Always with you</span>.",
      "hero.sub": "Save the audios that come in through WhatsApp, Telegram, or WeChat. Nickname them so they feel yours. Play them when you need them — and if you want, share them.",
      "hero.playBadge.aria": "Pre-register on Google Play (coming soon)",

      "decoy.1": "Mom's laugh",
      "decoy.2": "Yo dude!",
      "decoy.3": "I'm here",
      "decoy.4": "Boss said it again",

      "sticker.aria": "Tap to hear Bomp",
      "sticker.label.idle": "tap",
      "sticker.label.playing": "playing",
      "sticker.caption.playing": "Bomping…",
      "sticker.caption.fallback": "Your browser is muted. Tap to activate Bomp's voice.",

      "section.howItWorks.num": "01 · How it works",
      "section.howItWorks.title": "Save. Name. Bomp.",
      "section.howItWorks.lede": "Three taps. Zero accounts, zero cloud, zero friends to add.",
      "step.1.num": "01 · Save",
      "step.1.title": "Send the audio to Bomp.",
      "step.1.body": "From WhatsApp, Telegram or WeChat: tap \"share\" and pick Bomp. The audio is saved on your phone — only on your phone.",
      "step.2.num": "02 · Name",
      "step.2.title": "Make it yours. Give it the nickname only you'd get.",
      "step.2.body": "Whatever makes you laugh when you see it in the list. Bomp doesn't judge or correct.",
      "step.2.chip.1": "Mom's laugh",
      "step.2.chip.2": "Yo dude",
      "step.3.num": "03 · Bomp",
      "step.3.title": "One tap, it plays.",
      "step.3.body": "No waits, no \"opening audio…\", no loading screen. Bomp.",

      "section.app.num": "02 · The app",
      "section.app.title": "Your voices, in order.",
      "shot.list.title": "My audios",
      "shot.list.label1": "MOM SAID WHAT",
      "shot.list.label2": "PEDRO LAUGHING",
      "shot.list.label3": "I'M HERE",
      "shot.list.label4": "BOSS SAID IT AGAIN",
      "shot.share.title": "Share",
      "shot.share.audioName": "Pedro laughing",
      "shot.share.audioDuration": "0:02",
      "shot.share.app1": "WhatsApp",
      "shot.share.app2": "Telegram",
      "shot.share.app3": "More",
      "shot.chat.contact": "Lucía",
      "shot.chat.status": "online",
      "shot.chat.audioDuration": "0:02",
      "shot.chat.reaction": "LOLOLOL I can't",
      "shot.caption.identity": "↳ The voices that matter to you, always with you.",
      "shot.caption.gift": "↳ A two-second joke can save a day — yours, first.",
      "shot.caption.reception": "↳ And if you want, on the other side someone laughs out loud.",

      "section.glossary.num": "03 · Glossary",
      "section.glossary.title": "Mini Bomper dictionary.",
      "gloss.bomper.pos": "/bom·per/ · noun",
      "gloss.bomper.def": "You, right now. The person who saves audios the way others save hugs.",
      "gloss.bompear.pos": "/bom·peh·ar/ · verb",
      "gloss.bompear.def": "To activate a Bomp: play it or send it. For you first, for others after. Conjugation: I Bomp, you Bomp, they Bomp. (Yes, we conjugate it.)",
      "gloss.bompeable.pos": "/bom·peh·ah·bleh/ · adjective",
      "gloss.bompeable.def": "Those audios you love and don't want to lose: short or long, funny or sentimental. Useful. If you want it, save it.",

      "manifesto.html": "<span>An audio from one of yours </span><em>isn't a message</em>, <strong>it's a hug</strong> you can hear.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Made in the open, AGPL-3.0 licensed.",
      "section.openSource.body": "The full source is on GitHub. If you want to improve it, break it, translate it or fork it: the door is open.",
      "cta.contributeGitHub": "Contribute on GitHub",

      "section.donate.num": "05 · Donate",
      "section.donate.title": "Brightened your day? Buy me a virtual coffee.",
      "section.donate.body": "Bomp is free and ad-free. If you enjoyed it and want to say thanks, leave me a virtual coffee from here. No pressure — using it and sharing it already counts.",
      "donate.cafecito.alt": "Buy me a coffee at cafecito.app",

      "footer.brand": "The voices that matter to you, ready for you.",
      "footer.product": "Product",
      "footer.product.howItWorks": "How it works",
      "footer.product.theApp": "The app",
      "footer.product.googlePlay": "Google Play",
      "footer.legal": "Legal",
      "footer.community": "Community",
      "footer.copyright": "© __YEAR__ · Bomp · AGPL-3.0",
      "footer.pron": "Bomp /bomp/",
      "footer.lang.label": "Language",

      "404.num": "404 · Page not found",
      "404.title.html": "This door doesn't <em>open</em>.",
      "404.sub": "Back to the hub. Redirecting in 5 seconds.",
      "404.cta": "← Back to the hub",

      "legal.eyebrow": "Legal document",
      "legal.toc.heading": "Contents",
      "legal.back.hub": "← Back to the hub",
      "legal.back.top": "↑ Back to top",
      "footer.embeds.notice": "The donation button on this site loads resources from Ko-fi (external CDN). Subject to Ko-fi's privacy policy.",

      // ── Legal · privacy policy ────────────────
      "pp.meta.app": "App: <strong>Bomp</strong>",
      "pp.meta.version": "Policy version: <strong>1.0</strong>",
      "pp.meta.lastUpdated": "Last updated: <strong>2026-04-26</strong>",
      "pp.toc.s01": "01 · Trust header",
      "pp.toc.s02": "02 · Sensitive data (audio)",
      "pp.toc.s03": "03 · Third-party ecosystem",
      "pp.toc.s04": "04 · Children's policy",
      "pp.toc.s05": "05 · User rights (ARCO)",
      "pp.toc.s06": "06 · Effective date and changes",
      "pp.title": "Privacy Policy",
      "pp.intro": "This policy describes how Bomp (\"we\", \"the app\") handles user data (\"you\", \"the Bomper\"). Bomp is a local soundboard app: the audios you import live on your phone. The app has no user accounts or cloud; it does send Google Firebase a limited set of pseudonymous data (crash logs, performance diagnostics, and aggregated interactions) detailed in <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Privacy policy in effect since 2026-04-26. Distributed exclusively through <a href=\"https://play.google.com/\">Google Play</a> in Argentina and the Spanish-speaking region.",
      "pp.s02.intro": "Bomp stores <strong>audios you choose to import</strong> from other apps (WhatsApp, Telegram, WeChat) using Android's \"Share\" system. The app needs access to shared audio files for that operation.",
      "pp.s02.li1": "Audios are stored in Android's internal storage assigned to Bomp. Bomp <strong>does not upload them to developer servers</strong> nor share them automatically with third parties.",
      "pp.s02.li2": "<strong>Android Auto Backup.</strong> Bomp has <a href=\"https://developer.android.com/identity/data/autobackup\">Android Auto Backup</a> enabled: if you have it active on your phone (System settings → System → Backup), Google backs up Bomp's data (including imported audios) to <em>your own</em> Google Drive, in a private area accessible only to the app, up to 25 MB. Google manages that backup; Bomp does not control or access it, and you can disable it from your phone's settings. If you prefer your audios not be backed up, disable backup before importing them.",
      "pp.s02.li3": "When you share an audio from Bomp to another app, the transfer happens through Android's \"Share\" system; Bomp has no visibility into what the receiving app does.",
      "pp.s02.li4": "When you delete an audio from the app, the file is removed from local storage. When you uninstall the app, all audios are removed along with the app's data (the backup in your Google Drive is purged according to Google Backup's policy).",
      "pp.s03.intro": "Bomp uses Google services integrated into the Android ecosystem:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — Google Play's core system for app distribution and dynamic feature module delivery (the \"Add button\" screen is downloaded on demand).",
      "pp.s03.li2": "<strong>Firebase Crashlytics</strong> — crash diagnostics. Collects <em>crash logs</em> pseudonymously when the app crashes, so we can fix the bug. No user content is collected (audios, button names).",
      "pp.s03.li3": "<strong>Firebase Performance Monitoring</strong> — collects aggregated <em>performance diagnostics</em> (startup time, memory usage, latencies) to detect regressions release over release.",
      "pp.s03.li4": "<strong>Firebase Analytics</strong> — collects <em>aggregated usage events</em> (number of Bomps, sessions, UI interactions) without linking them to any identified user, to understand usage patterns and prioritize improvements.",
      "pp.s03.body": "Bomp <strong>does not use</strong>: ad networks, tracking pixels, tracking cookies, device fingerprinting. <strong>Bomp does not sell data.</strong> The pseudonymous data we do share with Google Firebase (crash logs, performance diagnostics, aggregated interactions) is processed for diagnostic and aggregated analytics purposes; the exhaustive detail and in-transit encryption are in <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp is designed for general use and does not target children under 13. The app does not require account creation, does not ask for email, nor collect name, age, or data that directly identify the user. If a minor uses the app, the only data leaving the device are the pseudonymous ones described in <a href=\"data-safety.html\">Data Safety</a> (crash logs, performance diagnostics, aggregated interactions), sent to Google Firebase without being linked to identifiable information.",
      "pp.s04.body2": "Bomp aligns with COPPA (USA) and GDPR (EU) guidelines by design: we do not collect directly identifiable data. If you are a parent/guardian and want us to purge the pseudonymous installation identifier associated with a minor's device, contact us through <a href=\"#arco\">User rights (ARCO)</a>.",
      "pp.s05.intro": "Under Argentina's Law 25.326 and the GDPR (EU), you have the right to:",
      "pp.s05.li1": "<strong>Access</strong> the data on your phone: open the app and you'll see all imported audios and buttons.",
      "pp.s05.li2": "<strong>Rectify them</strong>: rename or re-import audios from the app.",
      "pp.s05.li3": "<strong>Cancel them</strong>: delete them from the app, or uninstall Bomp to delete them all.",
      "pp.s05.li4": "<strong>Object</strong> to the processing of pseudonymous data in Firebase.",
      "pp.s05.body2": "For pseudonymous data living in Google Firebase (crash logs, performance diagnostics, aggregated interactions), Bomp does not store your name, email, or phone number — only an opaque installation identifier that Google assigns when installing the app. To exercise rights over that data:",
      "pp.s05.li5": "<strong>Uninstall the app</strong>: cuts collection and Google eventually purges the data associated with the identifier.",
      "pp.s05.li6": "<strong>Reset your Advertising ID</strong> from Android Settings → Privacy → Ads. This unlinks future sessions.",
      "pp.s05.li7": "<strong>Manual request</strong>: write to us at <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> with your Firebase Installation ID and we'll delete the associated record in Firebase Console.",
      "pp.s05.li8": "<strong>Via Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> lets you request deletion from Google directly, since it acts as a sub-processor.",
      "pp.s05.body3": "Under GDPR Art. 11, since Bomp cannot identify you without your cooperation (we don't store information that links you to your Installation ID), we are not obliged to maintain an additional identification mechanism. Your cooperation, sending the Installation ID, is what enables targeted deletion.",
      "pp.s06.body1": "This policy takes effect on 2026-04-26. If we modify significant terms, we'll update the \"Last updated\" date above and publish a changelog at <a href=\"https://github.com/barriosnahuel/bomp/releases\">GitHub Releases</a>.",
      "pp.s06.body2": "Bomp's source code is available under <a href=\"https://www.gnu.org/licenses/agpl-3.0.html\">AGPL-3.0</a> license at <a href=\"https://github.com/barriosnahuel/bomp\">github.com/barriosnahuel/bomp</a>.",

      // ── Legal · data safety ───────────────────
      "ds.meta.app": "App: <strong>Bomp</strong>",
      "ds.meta.source": "Source: <strong>Bomp's Google Play listing</strong>",
      "ds.meta.lastUpdated": "Last updated: <strong>2026-04-26</strong>",
      "ds.toc.s01": "01 · Data collected",
      "ds.toc.s02": "02 · Accounts and deletion",
      "ds.toc.s03": "03 · In-transit encryption",
      "ds.intro": "This page mirrors Bomp's declarations in Google Play's Data Safety form. The public source of truth is <a href=\"https://play.google.com/store/apps/details?id=com.github.barriosnahuel.vossosunboton\">Bomp's Google Play listing</a>; if there is any conflict between this page and what's on Play, Play wins.",
      "ds.s01.intro": "These are the data types Bomp declares it collects in its Data Safety listing:",
      "ds.table.col1": "Data type",
      "ds.table.col2": "Why is it collected?",
      "ds.table.col3": "Is it shared?",
      "ds.table.col4": "Is it optional?",
      "ds.row1.col1": "<strong>Other audio files</strong><br><small>The audios you import from WhatsApp, Telegram, or WeChat.</small>",
      "ds.row1.col2": "App functionality: they are the audios you choose to Bomp. Stored only on your phone.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — the control you have is deciding which files to import voluntarily; once imported, Bomp's local storage retains them until you delete them.",
      "ds.row2.col1": "<strong>Crash logs</strong><br><small>Stack traces and device state when the app crashes.</small>",
      "ds.row2.col2": "Crash diagnostics via Firebase Crashlytics. Lets us fix bugs.",
      "ds.row2.col3": "No (collected only). Processed by Google Firebase as sub-processor.",
      "ds.row2.col4": "No — pseudonymous logs are sent when there's a crash.",
      "ds.row3.col1": "<strong>Performance diagnostics</strong><br><small>Pseudonymous metrics on memory usage, startup time, latencies.</small>",
      "ds.row3.col2": "Performance regression detection via Firebase Performance Monitoring.",
      "ds.row3.col3": "No (collected only). Processed by Google Firebase as sub-processor.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>App interactions</strong><br><small>Aggregated events (number of Bomps, sessions).</small>",
      "ds.row4.col2": "Understand usage patterns to prioritize improvements via Firebase Analytics. Not associated with any identified user.",
      "ds.row4.col3": "No (collected only). Processed by Google Firebase as sub-processor.",
      "ds.row4.col4": "No.",
      "ds.title": "Data Safety",
      "ds.s01.autoBackup": "<strong>Android Auto Backup.</strong> Bomp has <a href=\"https://developer.android.com/identity/data/autobackup\">Android Auto Backup</a> enabled: if you have it active on your phone, Google backs up the app's data (including imported audios) to <em>your own</em> Google Drive, in a private area accessible only to the app, up to 25 MB. This does not appear in the table because Play's Data Safety form only declares what the app shares with the developer or with third parties: the backup goes to your Google account and is managed by Google, not by Bomp. You can disable it from System settings → System → Backup.",
      "ds.s02.body1": "<strong>Bomp does not require account creation.</strong> No email, no password, no OAuth, no SIM. The app opens and works.",
      "ds.s02.body2": "<strong>Data deletion.</strong> Since there is no account, there is no \"delete my account\" flow. Data lives on your phone and you delete it:",
      "ds.s02.li1": "Delete audios one by one from Bomp's list.",
      "ds.s02.li2": "Delete all audios: uninstall the app from the system. Android cleans up Bomp's assigned storage.",
      "ds.s02.li3": "For pseudonymous data in Firebase: see <a href=\"privacy-policy.html#arco\">Privacy Policy → ARCO Rights</a>.",
      "ds.s02.body3": "<strong>About Play's deletion flow.</strong> In Bomp's Play listing we declare that the app does not provide a self-service data deletion flow. The reason: data on your phone is deleted on uninstall, and pseudonymous data in Firebase is not associated with an account that can be \"closed\"; the manual procedure is in <a href=\"privacy-policy.html#arco\">Privacy Policy → ARCO Rights</a>.",
      "ds.s03.body": "All communication between Bomp and Google Firebase servers uses HTTPS (TLS 1.2 or higher). Calls to Google Play Services follow the official SDK's encryption standard."
    },

    // ── pt-BR — Brazilian Portuguese ──────────
    "pt-BR": {
      "html.lang": "pt-BR",
      "head.title.index": "Bomp — As vozes da sua gente",
      "head.title.privacy": "Política de privacidade — Bomp",
      "head.title.dataSafety": "Segurança dos dados — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Colecione as vozes que importam pra você: a risada da sua mãe, o áudio do amigo, a frase do chefe. Suas, primeiro. Pra mandar, depois.",
      "head.description.privacy": "Política de privacidade do Bomp — quais dados são tratados, como são armazenados, o que terceiros fazem, direitos do usuário.",
      "head.description.dataSafety": "Detalhes dos dados que o Bomp coleta em sua ficha do Google Play: quais, por quê, se compartilha e se é opcional.",
      "skip.link": "Pular para o conteúdo",
      "nav.howItWorks": "Como funciona",
      "nav.theApp": "O app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Política de privacidade",
      "nav.dataSafety": "Segurança dos dados",
      "theme.toggle.aria": "Alternar tema",

      "hero.eyebrow": "Beta · Android · grátis",
      "hero.title.html": "A voz da sua gente.<br><span class=\"acid\">Sempre com você</span>.",
      "hero.sub": "Salve os áudios que chegam pelo WhatsApp, Telegram ou WeChat. Apelide-os pra que sejam seus. Toque-os quando precisar — e se quiser, mande-os.",
      "hero.playBadge.aria": "Reserve no Google Play (em breve)",

      "decoy.1": "Risada da mãe",
      "decoy.2": "E aí mano!",
      "decoy.3": "Cheguei!",
      "decoy.4": "Frase do chefe",

      "sticker.aria": "Toque para ouvir o Bomp",
      "sticker.label.idle": "toque",
      "sticker.label.playing": "tocando",
      "sticker.caption.playing": "Bompeando…",
      "sticker.caption.fallback": "Seu navegador está em silêncio. Toque para ativar a voz do Bomp.",

      "section.howItWorks.num": "01 · Como funciona",
      "section.howItWorks.title": "Salve. Apelide. Bompe.",
      "section.howItWorks.lede": "Três toques. Zero contas, zero nuvem, zero amigos pra adicionar.",
      "step.1.num": "01 · Salvar",
      "step.1.title": "Mande o áudio pro Bomp.",
      "step.1.body": "Do WhatsApp, Telegram ou WeChat: você toca \"compartilhar\" e escolhe o Bomp. O áudio fica salvo no seu celular — só no seu celular.",
      "step.2.num": "02 · Apelidar",
      "step.2.title": "Faça dele seu. Dá um apelido que só você entende.",
      "step.2.body": "O que te fizer rir quando aparecer na lista. O Bomp não opina nem corrige.",
      "step.2.chip.1": "Risada da mãe",
      "step.2.chip.2": "E aí mano",
      "step.3.num": "03 · Bompear",
      "step.3.title": "Um toque, toca.",
      "step.3.body": "Sem espera, sem \"abrindo áudio…\", sem tela carregando. Bomp.",

      "section.app.num": "02 · O app",
      "section.app.title": "Suas vozes, em ordem.",
      "shot.list.title": "Meus áudios",
      "shot.list.label1": "MÃE FALOU QUÊ",
      "shot.list.label2": "RISADA DE PEDRO",
      "shot.list.label3": "CHEGUEI",
      "shot.list.label4": "FRASE DO CHEFE",
      "shot.share.title": "Compartilhar",
      "shot.share.audioName": "Risada de Pedro",
      "shot.share.audioDuration": "0:02",
      "shot.share.app1": "WhatsApp",
      "shot.share.app2": "Telegram",
      "shot.share.app3": "Mais",
      "shot.chat.contact": "Lucía",
      "shot.chat.status": "online",
      "shot.chat.audioDuration": "0:02",
      "shot.chat.reaction": "KKKKKK não aguento",
      "shot.caption.identity": "↳ As vozes que importam pra você, sempre com você.",
      "shot.caption.gift": "↳ Uma piada de dois segundos pode salvar um dia — o seu, primeiro.",
      "shot.caption.reception": "↳ E se quiser, do outro lado alguém ri alto.",

      "section.glossary.num": "03 · Glossário",
      "section.glossary.title": "Mini dicionário do Bomper.",
      "gloss.bomper.pos": "/bom·per/ · substantivo",
      "gloss.bomper.def": "Você, agora. A pessoa que guarda áudios como outros guardam abraços.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "Ativar um Bomp: ouvi-lo ou mandá-lo. Primeiro pra você, depois pros outros. Conjugação: eu bompo, você bompa, ele bompa, nós bompamos. (Sim, a gente conjuga.)",
      "gloss.bompeable.pos": "/bom·pe·á·vel/ · adjetivo",
      "gloss.bompeable.def": "Aqueles áudios que você ama e não quer perder: curtos ou longos, engraçados ou sentimentais. Úteis. Se quiser, salve.",

      "manifesto.html": "<span>Um áudio dos seus </span><em>não é uma mensagem</em>, <strong>é um abraço</strong> que se escuta.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Feito à luz do dia, sob licença AGPL-3.0.",
      "section.openSource.body": "O código completo está no GitHub. Se quiser melhorar, quebrar, traduzir ou fazer fork: a porta está aberta.",
      "cta.contributeGitHub": "Contribuir no GitHub",

      "section.donate.num": "05 · Doar",
      "section.donate.title": "Alegrou seu dia? Me paga um café virtual.",
      "section.donate.body": "Bomp é grátis e sem publicidade. Se gostou e quer me agradecer, deixa um café virtual aqui. Sem pressão — usar e compartilhar já conta.",
      "donate.cafecito.alt": "Me pague um café em cafecito.app",

      "footer.brand": "As vozes que importam pra você, prontas pra você.",
      "footer.product": "Produto",
      "footer.product.howItWorks": "Como funciona",
      "footer.product.theApp": "O app",
      "footer.product.googlePlay": "Google Play",
      "footer.legal": "Legal",
      "footer.community": "Comunidade",
      "footer.copyright": "© __YEAR__ · Bomp · AGPL-3.0",
      "footer.pron": "Bomp /bomp/",
      "footer.lang.label": "Idioma",

      "404.num": "404 · Página não encontrada",
      "404.title.html": "Esta porta não <em>abre</em>.",
      "404.sub": "Volte para o hub. Redirecionando em 5 segundos.",
      "404.cta": "← Voltar pro hub",

      "legal.eyebrow": "Documento legal",
      "legal.toc.heading": "Conteúdo",
      "legal.back.hub": "← Voltar pro hub",
      "legal.back.top": "↑ Voltar ao topo",
      "footer.embeds.notice": "O botão de doação deste site carrega recursos do Ko-fi (CDN externo). Sujeito à política de privacidade do Ko-fi.",

      // ── Legal · privacy policy ────────────────
      "pp.meta.app": "App: <strong>Bomp</strong>",
      "pp.meta.version": "Versão da política: <strong>1.0</strong>",
      "pp.meta.lastUpdated": "Última atualização: <strong>2026-04-26</strong>",
      "pp.toc.s01": "01 · Cabeçalho de confiança",
      "pp.toc.s02": "02 · Dados sensíveis (áudio)",
      "pp.toc.s03": "03 · Ecossistema de terceiros",
      "pp.toc.s04": "04 · Política para menores",
      "pp.toc.s05": "05 · Direitos do usuário (ARCO)",
      "pp.toc.s06": "06 · Vigência e mudanças",
      "pp.title": "Política de privacidade",
      "pp.intro": "Esta política descreve como o Bomp (\"nós\", \"o app\") trata os dados do usuário (\"você\", \"o Bomper\"). O Bomp é um app de soundboard local: os áudios que você importa ficam no seu celular. O app não tem contas nem nuvem de usuário; envia ao Google Firebase um conjunto restrito de dados pseudônimos (logs de falhas, diagnósticos de desempenho e interações agregadas) detalhado em <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidade vigente desde 2026-04-26. Distribuição exclusiva pelo <a href=\"https://play.google.com/\">Google Play</a> na Argentina e na região hispano-falante.",
      "pp.s02.intro": "O Bomp guarda <strong>áudios que você decide importar</strong> de outros apps (WhatsApp, Telegram, WeChat) usando o sistema de \"Compartilhar\" do Android. O app precisa de acesso aos arquivos de áudio compartilhados para essa operação.",
      "pp.s02.li1": "Os áudios ficam no armazenamento interno do Android atribuído ao Bomp. O Bomp <strong>não os envia para servidores do desenvolvedor</strong> nem os compartilha automaticamente com terceiros.",
      "pp.s02.li2": "<strong>Backup automático do Android.</strong> O Bomp tem habilitado o <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup do Android</a>: se você tem ativo no celular (Configurações do sistema → Sistema → Backup), o Google faz backup dos dados do Bomp (incluindo os áudios importados) no <em>seu próprio</em> Google Drive, numa área privada acessível só pelo app, até 25 MB. É o Google quem gerencia esse backup; o Bomp não controla nem acessa, e você pode desativar pelas configurações do celular. Se prefere que seus áudios não sejam respaldados, desative o backup antes de importá-los.",
      "pp.s02.li3": "Quando você compartilha um áudio do Bomp para outro app, a transferência ocorre pelo sistema de \"Compartilhar\" do Android; o Bomp não tem visibilidade do que o app receptor faz.",
      "pp.s02.li4": "Quando você apaga um áudio pelo app, o arquivo é eliminado do armazenamento local. Quando você desinstala o app, todos os áudios são apagados junto com os dados do app (o backup no seu Google Drive é purgado conforme a política do Google Backup).",
      "pp.s03.intro": "O Bomp usa serviços do Google integrados ao ecossistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — sistema base do Google Play para distribuição do app e entrega de feature modules dinâmicos (a tela \"Adicionar botão\" é baixada sob demanda).",
      "pp.s03.li2": "<strong>Firebase Crashlytics</strong> — diagnóstico de falhas. Coleta <em>logs de falhas</em> de forma pseudônima quando o app trava, para que possamos corrigir o bug. Não coleta conteúdo do usuário (áudios, nomes de botões).",
      "pp.s03.li3": "<strong>Firebase Performance Monitoring</strong> — coleta <em>diagnósticos de desempenho</em> agregados (tempo de inicialização, uso de memória, latências) para detectar regressões release a release.",
      "pp.s03.li4": "<strong>Firebase Analytics</strong> — coleta <em>eventos agregados de uso</em> (quantidade de Bomps, sessões, interações com a UI) sem vinculá-los a nenhum usuário identificado, para entender padrões de uso e priorizar melhorias.",
      "pp.s03.body": "O Bomp <strong>não usa</strong>: redes de publicidade, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>O Bomp não vende dados.</strong> Os dados pseudônimos que sim compartilhamos com o Google Firebase (logs de falhas, diagnósticos de desempenho, interações agregadas) são processados com finalidade de diagnóstico e analytics agregados; o detalhe completo e a criptografia em trânsito estão em <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "O Bomp foi pensado para uso geral e não se destina a menores de 13 anos. O app não exige criação de conta, não pede e-mail, e não coleta nome, idade ou dados que identifiquem diretamente o usuário. Se um menor usar o app, os únicos dados que saem do dispositivo são os pseudônimos descritos em <a href=\"data-safety.html\">Data Safety</a> (logs de falhas, diagnósticos de desempenho, interações agregadas), enviados ao Google Firebase sem se associarem a informações identificáveis.",
      "pp.s04.body2": "O Bomp se alinha com COPPA (EUA) e GDPR (UE) por design: não coletamos dados diretamente identificáveis. Se você é mãe/pai/responsável e quer que purguemos o identificador de instalação pseudônimo associado ao dispositivo de um menor, fale conosco pelo canal de <a href=\"#arco\">Direitos do usuário (ARCO)</a>.",
      "pp.s05.intro": "Sob a Lei 25.326 (Argentina) e o GDPR (UE), você tem direito a:",
      "pp.s05.li1": "<strong>Acessar</strong> os dados no seu celular: abra o app e você verá todos os áudios e botões que importou.",
      "pp.s05.li2": "<strong>Retificá-los</strong>: renomeie ou re-importe áudios pelo app.",
      "pp.s05.li3": "<strong>Cancelá-los</strong>: apague-os pelo app, ou desinstale o Bomp para apagar todos.",
      "pp.s05.li4": "<strong>Opor-se</strong> ao tratamento dos dados pseudônimos no Firebase.",
      "pp.s05.body2": "Para os dados pseudônimos que vivem no Google Firebase (logs de falhas, diagnósticos de desempenho, interações agregadas), o Bomp não armazena seu nome, e-mail nem telefone — só um identificador opaco de instalação que o Google atribui ao instalar o app. Para exercer direitos sobre esses dados:",
      "pp.s05.li5": "<strong>Desinstale o app</strong>: corta a coleta e o Google purga eventualmente os dados associados ao identificador.",
      "pp.s05.li6": "<strong>Reset seu Advertising ID</strong> em Configurações do Android → Privacidade → Anúncios. Isso desvincula sessões futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escreva-nos em <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> com seu Firebase Installation ID e apagamos o registro associado no Firebase Console.",
      "pp.s05.li8": "<strong>Via Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> permite pedir o apagamento ao Google diretamente, já que ele atua como sub-processador.",
      "pp.s05.body3": "Sob o GDPR Art. 11, como o Bomp não pode te identificar sem sua cooperação (não armazenamos informações que conectem você ao seu Installation ID), não estamos obrigados a manter um mecanismo de identificação adicional. Sua cooperação, enviando o Installation ID, é o que viabiliza o apagamento pontual.",
      "pp.s06.body1": "Esta política entra em vigor em 2026-04-26. Se modificarmos termos significativos, atualizaremos a data de \"Última atualização\" acima e publicaremos um changelog em <a href=\"https://github.com/barriosnahuel/bomp/releases\">GitHub Releases</a>.",
      "pp.s06.body2": "O código-fonte do Bomp está disponível sob licença <a href=\"https://www.gnu.org/licenses/agpl-3.0.html\">AGPL-3.0</a> em <a href=\"https://github.com/barriosnahuel/bomp\">github.com/barriosnahuel/bomp</a>.",

      // ── Legal · data safety ───────────────────
      "ds.meta.app": "App: <strong>Bomp</strong>",
      "ds.meta.source": "Fonte: <strong>ficha do Bomp no Google Play</strong>",
      "ds.meta.lastUpdated": "Última atualização: <strong>2026-04-26</strong>",
      "ds.toc.s01": "01 · Dados coletados",
      "ds.toc.s02": "02 · Contas e exclusão",
      "ds.toc.s03": "03 · Criptografia em trânsito",
      "ds.intro": "Esta página replica as declarações do Bomp no formulário de Data Safety do Google Play. A fonte de verdade pública é a <a href=\"https://play.google.com/store/apps/details?id=com.github.barriosnahuel.vossosunboton\">ficha do Bomp no Google Play</a>; se houver conflito entre esta página e o que está no Play, vale o Play.",
      "ds.s01.intro": "Estes são os tipos de dados que o Bomp declara coletar na ficha de Data Safety:",
      "ds.table.col1": "Tipo de dado",
      "ds.table.col2": "Por que é coletado?",
      "ds.table.col3": "É compartilhado?",
      "ds.table.col4": "É opcional?",
      "ds.row1.col1": "<strong>Outros arquivos de áudio</strong><br><small>Os áudios que você importa do WhatsApp, Telegram ou WeChat.</small>",
      "ds.row1.col2": "Funcionalidade do app: são os áudios que você decide Bompear. Ficam só no seu celular.",
      "ds.row1.col3": "Não.",
      "ds.row1.col4": "Não — o controle que você tem é decidir quais arquivos importar voluntariamente; uma vez importados, o armazenamento local do Bomp os mantém até que você os apague.",
      "ds.row2.col1": "<strong>Logs de falhas</strong><br><small>Stack traces e estado do dispositivo quando o app trava.</small>",
      "ds.row2.col2": "Diagnóstico de falhas via Firebase Crashlytics. Permite corrigir bugs.",
      "ds.row2.col3": "Não (apenas coletado). Processado pelo Google Firebase como sub-processador.",
      "ds.row2.col4": "Não — os logs pseudônimos são enviados quando há um crash.",
      "ds.row3.col1": "<strong>Diagnósticos de desempenho</strong><br><small>Métricas pseudônimas de uso de memória, tempo de inicialização, latências.</small>",
      "ds.row3.col2": "Detecção de regressões de desempenho via Firebase Performance Monitoring.",
      "ds.row3.col3": "Não (apenas coletado). Processado pelo Google Firebase como sub-processador.",
      "ds.row3.col4": "Não.",
      "ds.row4.col1": "<strong>Interações com o app</strong><br><small>Eventos agregados (quantidade de Bomps, sessões).</small>",
      "ds.row4.col2": "Entender padrões de uso para priorizar melhorias via Firebase Analytics. Sem associar a nenhum usuário identificado.",
      "ds.row4.col3": "Não (apenas coletado). Processado pelo Google Firebase como sub-processador.",
      "ds.row4.col4": "Não.",
      "ds.title": "Segurança dos dados",
      "ds.s01.autoBackup": "<strong>Backup automático do Android.</strong> O Bomp tem habilitado o <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup do Android</a>: se você tem ativo no celular, o Google faz backup dos dados do app (incluindo os áudios importados) no <em>seu próprio</em> Google Drive, numa área privada acessível só pelo app, até 25 MB. Isso não aparece na tabela porque o formulário de Data Safety do Play declara apenas o que o app compartilha com o desenvolvedor ou com terceiros: o backup vai para sua conta do Google e é gerenciado pelo Google, não pelo Bomp. Você pode desativá-lo em Configurações do sistema → Sistema → Backup.",
      "ds.s02.body1": "<strong>O Bomp não exige criação de conta.</strong> Não usa e-mail, não usa senha, não usa OAuth, não usa SIM. O app abre e funciona.",
      "ds.s02.body2": "<strong>Exclusão de dados.</strong> Como não há conta, não há um fluxo \"apagar minha conta\". Os dados ficam no seu celular e você os apaga:",
      "ds.s02.li1": "Apagar áudios um a um pela lista do Bomp.",
      "ds.s02.li2": "Apagar todos os áudios: desinstale o app pelo sistema. O Android limpa o armazenamento atribuído ao Bomp.",
      "ds.s02.li3": "Para os dados pseudônimos no Firebase: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Direitos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre o fluxo de exclusão no Play.</strong> Na ficha do Bomp no Play declaramos que o app não provê um fluxo de auto-serviço de exclusão de dados. A razão: os dados no seu celular são apagados ao desinstalar, e os dados pseudônimos no Firebase não estão associados a uma conta que possa ser \"fechada\"; o procedimento manual está em <a href=\"privacy-policy.html#arco\">Privacy Policy → Direitos ARCO</a>.",
      "ds.s03.body": "Toda comunicação entre o Bomp e os servidores do Google Firebase usa HTTPS (TLS 1.2 ou superior). As chamadas ao Google Play Services seguem o padrão de criptografia do SDK oficial."
    }
  };

  // Locale → Google Play badge image (es-AR uses the LATAM badge, no AR-specific asset).
  var BADGE = {
    "es-AR":  "assets/img/google-play/preregister-es-419.png",
    "es-419": "assets/img/google-play/preregister-es-419.png",
    "es-ES":  "assets/img/google-play/preregister-es-ES.png",
    "en":     "assets/img/google-play/preregister-en.png",
    "pt-BR":  "assets/img/google-play/preregister-pt-BR.png"
  };

  // Display labels for the language switcher (in the language itself).
  var LABEL = {
    "es-AR":  "Español (Argentina)",
    "es-419": "Español (Latinoamérica)",
    "es-ES":  "Español (España)",
    "en":     "English",
    "pt-BR":  "Português (Brasil)"
  };

  // ── Detection ──────────────────────────────
  function fromUrl() {
    try {
      var p = new URLSearchParams(window.location.search).get("hl");
      return normalize(p);
    } catch (e) { return null; }
  }
  function fromStorage() {
    try { return normalize(localStorage.getItem("bomp-locale")); }
    catch (e) { return null; }
  }
  function fromBrowser() {
    var langs = navigator.languages && navigator.languages.length
      ? navigator.languages
      : (navigator.language ? [navigator.language] : []);
    for (var i = 0; i < langs.length; i++) {
      var match = mapBrowserTag(langs[i]);
      if (match) return match;
    }
    return null;
  }
  function normalize(tag) {
    if (!tag) return null;
    // case-insensitive match against SUPPORTED list
    var t = String(tag).trim();
    for (var i = 0; i < SUPPORTED.length; i++) {
      if (SUPPORTED[i].toLowerCase() === t.toLowerCase()) return SUPPORTED[i];
    }
    return null;
  }
  function mapBrowserTag(tag) {
    if (!tag) return null;
    var t = tag.toLowerCase();
    // exact matches first
    var exact = normalize(tag);
    if (exact) return exact;
    // language-region patterns
    if (t === "es-ar" || t.indexOf("es-ar") === 0) return "es-AR";
    if (t === "es-es" || t.indexOf("es-es") === 0) return "es-ES";
    if (t.indexOf("es") === 0) return "es-419";
    if (t.indexOf("pt") === 0) return "pt-BR";
    if (t.indexOf("en") === 0) return "en";
    return null;
  }

  function detect() {
    return fromUrl() || fromStorage() || fromBrowser() || DEFAULT;
  }

  // ── DOM walker ─────────────────────────────
  function apply(locale) {
    var dict = LANG[locale] || LANG[DEFAULT];
    document.documentElement.setAttribute("lang", dict["html.lang"] || locale);

    // textContent
    var nodes = document.querySelectorAll("[data-i18n]");
    for (var i = 0; i < nodes.length; i++) {
      var key = nodes[i].getAttribute("data-i18n");
      var v = lookup(dict, key);
      if (v != null) nodes[i].textContent = expand(v);
    }
    // innerHTML (only used for keys that hold our own markup)
    var htmlNodes = document.querySelectorAll("[data-i18n-html]");
    for (var j = 0; j < htmlNodes.length; j++) {
      var k2 = htmlNodes[j].getAttribute("data-i18n-html");
      var v2 = lookup(dict, k2);
      if (v2 != null) htmlNodes[j].innerHTML = expand(v2);
    }
    // attributes — "aria-label:hero.playBadge.aria;alt:donate.cafecito.alt"
    var attrNodes = document.querySelectorAll("[data-i18n-attr]");
    for (var n = 0; n < attrNodes.length; n++) {
      var spec = attrNodes[n].getAttribute("data-i18n-attr");
      var pairs = spec.split(";");
      for (var p = 0; p < pairs.length; p++) {
        var kv = pairs[p].split(":");
        if (kv.length !== 2) continue;
        var attr = kv[0].trim();
        var key3 = kv[1].trim();
        var v3 = lookup(dict, key3);
        if (v3 != null) attrNodes[n].setAttribute(attr, expand(v3));
      }
    }

    // <title> if it carries data-i18n
    if (document.title && document.querySelector("title[data-i18n]")) {
      // already handled above (title is queried via [data-i18n])
    }

    // Per-locale Google Play badge swap.
    var badge = document.querySelector("[data-i18n-badge]");
    if (badge && BADGE[locale]) {
      badge.setAttribute("src", BADGE[locale]);
    }

    // Locale-gated visibility. data-i18n-locale-only="es-AR,es-419" hides the
    // element unless `locale` is in the comma list. Used for region-specific
    // CTAs (e.g. Cafecito only makes sense in Argentina).
    var gated = document.querySelectorAll("[data-i18n-locale-only]");
    for (var g = 0; g < gated.length; g++) {
      var allowed = gated[g].getAttribute("data-i18n-locale-only").split(",").map(function (s) { return s.trim(); });
      var match = allowed.indexOf(locale) !== -1;
      gated[g].hidden = !match;
    }

    // Propagate locale across internal page navigation by appending ?hl=<locale>
    // to <a href="*.html"> links. Skips external (http/https/mailto/etc.) and
    // pure anchors (#foo). Preserves existing query/hash. Idempotent — overrides
    // any existing hl param so the locale always reflects the current page.
    var anchors = document.querySelectorAll("a[href]");
    for (var a = 0; a < anchors.length; a++) {
      var href = anchors[a].getAttribute("href");
      if (!href) continue;
      if (/^[a-z]+:/i.test(href)) continue;          // external scheme
      if (href.charAt(0) === "#") continue;          // pure anchor
      var parts = href.match(/^([^?#]+\.html)(\?[^#]*)?(#.*)?$/);
      if (!parts) continue;
      var base = parts[1];
      var existingQuery = (parts[2] || "").replace(/^\?/, "");
      var hash = parts[3] || "";
      var params = new URLSearchParams(existingQuery);
      params.set("hl", locale);
      anchors[a].setAttribute("href", base + "?" + params.toString() + hash);
    }
  }

  function lookup(dict, key) {
    if (key in dict) return dict[key];
    // Fallback to default locale
    var fb = LANG[DEFAULT];
    return fb && (key in fb) ? fb[key] : null;
  }

  function expand(s) {
    // Token expansion: __YEAR__ → current year.
    return String(s).replace(/__YEAR__/g, String(new Date().getFullYear()));
  }

  // ── Footer language switcher ───────────────
  function bindSwitcher(currentLocale) {
    var sel = document.querySelector("[data-i18n-switcher]");
    if (!sel) return;
    // Populate options
    sel.innerHTML = "";
    for (var i = 0; i < SUPPORTED.length; i++) {
      var loc = SUPPORTED[i];
      var opt = document.createElement("option");
      opt.value = loc;
      opt.textContent = LABEL[loc];
      if (loc === currentLocale) opt.selected = true;
      sel.appendChild(opt);
    }
    sel.addEventListener("change", function () {
      var next = sel.value;
      if (!normalize(next)) return;
      try { localStorage.setItem("bomp-locale", next); } catch (e) {}
      // Update URL (?hl=) and reload so dynamic widgets (Ko-fi) re-render clean.
      var url = new URL(window.location.href);
      url.searchParams.set("hl", next);
      window.location.href = url.toString();
    });
  }

  // ── Boot ──────────────────────────────────
  function boot() {
    var locale = detect();
    apply(locale);
    bindSwitcher(locale);
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }

  // Expose for sticker-hero.js to read locale-specific captions.
  window.BompI18n = {
    locale: function () { return detect(); },
    t: function (key) {
      var dict = LANG[detect()] || LANG[DEFAULT];
      var v = lookup(dict, key);
      return v == null ? key : expand(v);
    },
    SUPPORTED: SUPPORTED.slice()
  };
})();
