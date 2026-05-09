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
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") maneja los datos del usuario (\"vos\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importás viven en tu teléfono. La app no tiene cuentas ni nube de usuario; sí envía a Google un conjunto acotado de datos pseudónimos (logs de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva por <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que vos elegís importar</strong> desde otras apps usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si vos lo tenés activo en tu teléfono (Configuración del sistema → Sistema → Backup), Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. Lo gestiona Google, no Bomp. Tres cosas que conviene saber: (1) está sujeto a la <strong>cuota de Auto Backup</strong> que define Google (actualmente ~25 MB por app) — si tu colección supera ese tamaño, Google no respalda lo que excede; (2) si desinstalás Bomp, el backup queda accesible para una eventual reinstalación, pero Google lo purga tras un período prolongado de inactividad según su política; (3) podés desactivar Auto Backup en cualquier momento desde la configuración de tu teléfono. Para los audios que no querés perder, te recomendamos exportar copias propias por fuera de Bomp.",
      "pp.s02.li3": "Cuando vos compartís un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borrás un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalás la app, todos los audios se eliminan junto con los datos de la app; el backup en tu Google Drive (si tenés Auto Backup activo) queda accesible para una eventual reinstalación, pero Google lo purga tras un período prolongado de inactividad según su política.",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — infraestructura base de Google Play para distribución y actualización de la app.",
      "pp.s03.li2": "<strong>Diagnóstico de fallos</strong> — recopila <em>logs de fallos</em> de forma pseudónima cuando la app crashea, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Monitoreo de rendimiento</strong> — recopila <em>diagnósticos pseudónimos agregados</em> (tiempo de arranque, uso de memoria, latencias) con propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Analítica de uso</strong> — recopila <em>eventos agregados</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos pseudónimos que sí compartimos con Google (logs de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analítica agregada; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recolecta nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los pseudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y GDPR (UE) por diseño: no recolectamos datos directamente identificables. Si sos madre/padre/tutor y querés que purguemos el identificador de instalación pseudónimo asociado al dispositivo de un menor, escribinos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el GDPR (UE), tenés derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu teléfono: abrí la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombrá o re-importá audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: borralos desde la app, o desinstalá Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos pseudónimos por Google.",
      "pp.s05.body2": "Para los datos pseudónimos que viven en sistemas de Google (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstalá la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Reseteá tu Advertising ID</strong> desde Configuración de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escribinos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu identificador de instalación pseudónimo (te ayudamos a obtenerlo si lo necesitás) y borramos el registro asociado.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite pedir borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo GDPR Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu identificador de instalación), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, mandando ese identificador, es lo que habilita el borrado puntual.",
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
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que vos importás desde otras apps usando el sistema de \"Compartir\" de Android.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que vos elegís Bompear. Se almacenan solo en tu teléfono.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tenés es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los retiene hasta que vos los borres.",
      "ds.row2.col1": "<strong>Logs de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app crashea.</small>",
      "ds.row2.col2": "Diagnóstico de fallos. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row2.col4": "No — los logs pseudónimos se envían cuando hay un crash.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas pseudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento.",
      "ds.row3.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tenés activo en tu teléfono, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. Esto no aparece en la tabla de arriba porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: este backup va a tu cuenta de Google y lo gestiona Google, no Bomp. Está sujeto a los límites de Auto Backup (actualmente ~25 MB por app y purga tras un período prolongado de inactividad). Podés desactivarlo desde Configuración del sistema → Sistema → Backup. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usás email, no usás contraseña, no usás OAuth, no usás SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu teléfono y los borrás vos:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstalá la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos pseudónimos en sistemas de Google: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu teléfono se borran al desinstalar, y los datos pseudónimos en sistemas de Google no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial.",

      // ── Legal · terms of service ─────────────
      "head.title.terms": "Términos del servicio — Bomp",
      "head.description.terms": "Términos del servicio de Bomp — alcance, responsabilidad del usuario sobre el contenido grabado y compartido, licencia de uso, ley aplicable y jurisdicción.",
      "nav.termsOfService": "Términos del servicio",
      "tos.title": "Términos del servicio",
      "tos.meta.app": "App: <strong>Bomp</strong>",
      "tos.meta.version": "Versión: <strong>1.0</strong>",
      "tos.meta.effective": "Fecha de efectividad: <strong>2026-05-09</strong>",
      "tos.meta.operator": "Operador: <strong>Nahuel Barrios</strong> — desarrollador y administrador de Bomp. Contacto: <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a>",
      "tos.intro": "Estos Términos del Servicio rigen tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. El documento master es la versión en español de Argentina (<code>es-AR</code>); en caso de divergencia con otras versiones idiomáticas, prevalece esa versión.",
      "tos.toc.s01": "01 · Aceptación y alcance",
      "tos.toc.s02": "02 · Edad mínima y capacidad legal",
      "tos.toc.s03": "03 · Licencia de uso",
      "tos.toc.s04": "04 · Responsabilidad del Bomper sobre el contenido",
      "tos.toc.s05": "05 · Usos prohibidos",
      "tos.toc.s06": "06 · Propiedad intelectual",
      "tos.toc.s07": "07 · Sin garantías (\"tal cual\")",
      "tos.toc.s08": "08 · Limitación de responsabilidad",
      "tos.toc.s09": "09 · Suspensión y terminación",
      "tos.toc.s10": "10 · Cambios a los Términos",
      "tos.toc.s11": "11 · Ley aplicable y jurisdicción",
      "tos.toc.s12": "12 · Notificaciones legales y contacto",
      "tos.toc.s13": "13 · Divisibilidad y acuerdo total",
      "tos.s01.body": "Estos Términos del Servicio (en adelante, <strong>\"los Términos\"</strong>) regulan tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. La aplicación es desarrollada y operada por <strong>Nahuel Barrios</strong> (en adelante, <strong>\"el operador\"</strong> o, indistintamente, <strong>\"Bomp\"</strong>). Al instalar, abrir o usar la aplicación de cualquier modo, manifestás tu consentimiento expreso a estos Términos. Si no estás de acuerdo, no instales ni uses Bomp.",
      "tos.s02.body": "Para usar Bomp declarás que tenés la edad legal requerida en tu jurisdicción para celebrar contratos. Si sos menor de esa edad, debés contar con el consentimiento expreso de tu representante legal antes de instalar o usar la aplicación. Bomp puede suspender el acceso ante constancia de uso por menores sin la autorización requerida.",
      "tos.s03.body": "Bomp te otorga una licencia personal, no exclusiva, no transferible y revocable para instalar y usar la aplicación en dispositivos de tu titularidad o uso autorizado, exclusivamente con fines no comerciales. Esta licencia no transfiere ningún derecho de propiedad sobre la aplicación ni sus componentes. El código fuente se rige por sus propias licencias open source (BSL + AGPLv3) descritas en el repositorio público.",
      "tos.s04.intro": "Como Bomper sos el único responsable de los audios que grabás, editás, almacenás y compartís usando Bomp (en adelante, <strong>\"el Contenido\"</strong>). Al usar la aplicación declarás y garantizás que:",
      "tos.s04.liA": "<strong>(a)</strong> sos titular de todos los derechos sobre el Contenido o contás con las licencias y permisos necesarios para grabarlo, almacenarlo y distribuirlo;",
      "tos.s04.liB": "<strong>(b)</strong> cuando el Contenido incluya la voz, imagen o datos personales de terceros, contás con el consentimiento expreso e informado de esas personas conforme a las leyes aplicables, incluyendo el artículo 53 del Código Civil y Comercial de la Nación argentina sobre el derecho a la imagen y voz, la Ley 25.326 de Protección de Datos Personales, y normas equivalentes en otras jurisdicciones;",
      "tos.s04.liC": "<strong>(c)</strong> el Contenido no infringe derechos de propiedad intelectual, derechos personalísimos, normas contra la difamación, ni leyes penales aplicables;",
      "tos.s04.liD": "<strong>(d)</strong> no usás Bomp para grabar conversaciones o personas en contextos donde la grabación esté prohibida por la ley aplicable.",
      "tos.s04.indem": "Mantendrás indemne y a salvo a Bomp, sus colaboradores y asociados, frente a cualquier reclamo, demanda, sanción, costo, honorario o gasto derivado de Contenido generado, almacenado, editado o compartido por vos a través de Bomp.",
      "tos.s05.intro": "Está prohibido usar Bomp para:",
      "tos.s05.liA": "<strong>(a)</strong> almacenar o distribuir Contenido ilegal o que infrinja derechos de terceros;",
      "tos.s05.liB": "<strong>(b)</strong> hostigar, amenazar, acosar o intimidar a otras personas;",
      "tos.s05.liC": "<strong>(c)</strong> suplantar la identidad de otra persona o engañar sobre el origen de un audio;",
      "tos.s05.liD": "<strong>(d)</strong> realizar ingeniería inversa, descompilación o desensamblado de la aplicación más allá de lo permitido por las licencias open source aplicables;",
      "tos.s05.liE": "<strong>(e)</strong> usar la aplicación para difusión masiva automatizada o spam;",
      "tos.s05.liF": "<strong>(f)</strong> burlar las medidas técnicas de protección de la aplicación o de las plataformas a las que se conecta.",
      "tos.s05.body": "Bomp puede suspender o revocar el acceso a la aplicación frente a cualquier violación de esta cláusula.",
      "tos.s06.body1": "Bomp, el logotipo, el ícono, el <em>brand mark</em>, el <em>wordmark</em>, los textos, los gráficos y demás elementos visuales de la aplicación y del sitio web son propiedad de Nahuel Barrios o de sus licenciantes. El código fuente se rige por las licencias BSL + AGPLv3 descritas en el repositorio público.",
      "tos.s06.body2": "Vos retenés la titularidad sobre los audios que grabás y guardás en Bomp. Bomp <strong>no reclama</strong> ningún derecho sobre tu Contenido. La aplicación procesa los audios localmente en tu dispositivo conforme a lo descrito en la <a href=\"privacy-policy.html\">Política de Privacidad</a>.",
      "tos.s07.body": "La aplicación se entrega <em>\"tal cual\"</em> y <em>\"según disponibilidad\"</em>, sin garantías expresas ni implícitas sobre su funcionamiento, continuidad, ausencia de errores o defectos, idoneidad para un fin particular, ni resultados específicos derivados de su uso. Tus audios viven en tu teléfono: Bomp no opera servidores ni nube propia donde guardarlos. Si en tu sistema tenés activo el Auto Backup de Android, Google copia los datos de la app a <em>tu propio</em> Google Drive — ese mecanismo lo administra Google, no Bomp, y está sujeto a sus límites técnicos (actualmente ~25 MB por app y purga del backup tras un período prolongado de inactividad). Para los audios que no querés perder, mantené copias propias por fuera de la app. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "tos.s08.body": "Hasta donde la ley aplicable lo permita, Bomp no será responsable por daños indirectos, incidentales, emergentes, punitivos o consecuenciales derivados del uso o de la imposibilidad de uso de la aplicación, incluyendo —sin limitación— pérdida de audios, pérdida de oportunidades, pérdida de datos o daños reputacionales. Esta limitación no aplica frente a daños que las normas de orden público de tu jurisdicción dispongan como no excluibles, en particular las normas de la Ley 24.240 de Defensa del Consumidor de Argentina, el Texto Refundido de la Ley General para la Defensa de los Consumidores y Usuarios de España, el Código de Defesa do Consumidor de Brasil, y normas equivalentes.",
      "tos.s09.body": "Bomp puede suspender o terminar tu acceso a la aplicación, sin necesidad de aviso previo, ante una violación material de estos Términos. Bomp también podrá discontinuar la aplicación, en su totalidad o respecto de algún servicio asociado, mediando aviso razonable a través del sitio web o de los canales de Google Play Store. La terminación no afecta las obligaciones del Bomper devengadas antes de la terminación; las cláusulas 4, 6 y 8 sobreviven a la terminación.",
      "tos.s10.body": "Bomp puede modificar estos Términos en cualquier momento. Los cambios materiales se anunciarán mediante un banner visible en el sitio web durante al menos 30 días, junto con la actualización de la Fecha de efectividad en el encabezado del documento. Continuar usando la aplicación luego de la Fecha de efectividad implica la aceptación de los Términos modificados. Si no estás de acuerdo con los cambios, debés cesar el uso de Bomp.",
      "tos.s11.body": "Estos Términos se rigen por las leyes de la República Argentina. Cualquier controversia derivada de los mismos se someterá a los tribunales ordinarios con competencia en la Ciudad Autónoma de Buenos Aires, salvo que las normas de orden público de la jurisdicción del Bomper —en particular, las leyes de defensa del consumidor— le permitan iniciar la acción en su propio domicilio, en cuyo caso ese derecho prevalece.",
      "tos.s12.body": "Las notificaciones legales formales deben enviarse por correo electrónico a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> (la misma dirección usada para solicitudes de <a href=\"privacy-policy.html#arco\">Derechos del titular</a>). Bomp responderá las comunicaciones formales dentro de los plazos legales aplicables.",
      "tos.s13.body": "Si un tribunal competente declara nula, inválida o inejecutable cualquier cláusula de estos Términos, el resto de las cláusulas conservará plena vigencia. Estos Términos, junto con la <a href=\"privacy-policy.html\">Política de Privacidad</a> y la página de <a href=\"data-safety.html\">Seguridad de los Datos</a>, constituyen el acuerdo total entre el Bomper y Bomp en relación con la aplicación y reemplazan cualquier acuerdo previo.",
      "tos.closing": "<em>Disposición final.</em> En caso de divergencia entre las versiones idiomáticas de estos Términos, prevalecerá la versión en español de Argentina (<code>es-AR</code>) como master legal."
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
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") maneja los datos del usuario (\"tú\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importas viven en tu teléfono. La app no tiene cuentas ni nube de usuario; sí envía a Google un conjunto acotado de datos pseudónimos (logs de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva por <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que tú eliges importar</strong> desde otras apps usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu teléfono (Configuración del sistema → Sistema → Backup), Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. Lo gestiona Google, no Bomp. Tres cosas que conviene saber: (1) está sujeto a la <strong>cuota de Auto Backup</strong> que define Google (actualmente ~25 MB por app) — si tu colección supera ese tamaño, Google no respalda lo que excede; (2) si desinstalas Bomp, el backup queda accesible para una eventual reinstalación, pero Google lo purga tras un período prolongado de inactividad según su política; (3) puedes desactivar Auto Backup en cualquier momento desde la configuración de tu teléfono. Para los audios que no quieres perder, te recomendamos exportar copias propias por fuera de Bomp.",
      "pp.s02.li3": "Cuando tú compartes un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borras un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalas la app, todos los audios se eliminan junto con los datos de la app; el backup en tu Google Drive (si tienes Auto Backup activo) queda accesible para una eventual reinstalación, pero Google lo purga tras un período prolongado de inactividad según su política.",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — infraestructura base de Google Play para distribución y actualización de la app.",
      "pp.s03.li2": "<strong>Diagnóstico de fallos</strong> — recopila <em>logs de fallos</em> de forma pseudónima cuando la app crashea, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Monitoreo de rendimiento</strong> — recopila <em>diagnósticos pseudónimos agregados</em> (tiempo de arranque, uso de memoria, latencias) con propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Analítica de uso</strong> — recopila <em>eventos agregados</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos pseudónimos que sí compartimos con Google (logs de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analítica agregada; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recolecta nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los pseudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y GDPR (UE) por diseño: no recolectamos datos directamente identificables. Si eres madre/padre/tutor y quieres que purguemos el identificador de instalación pseudónimo asociado al dispositivo de un menor, escríbenos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el GDPR (UE), tienes derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu teléfono: abre la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombra o re-importa audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: bórralos desde la app, o desinstala Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos pseudónimos por Google.",
      "pp.s05.body2": "Para los datos pseudónimos que viven en sistemas de Google (logs de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstala la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Resetea tu Advertising ID</strong> desde Configuración de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escríbenos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu identificador de instalación pseudónimo (te ayudamos a obtenerlo si lo necesitas) y borramos el registro asociado.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite pedir borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo GDPR Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu identificador de instalación), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, mandando ese identificador, es lo que habilita el borrado puntual.",
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
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que tú importas desde otras apps usando el sistema de \"Compartir\" de Android.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que tú eliges Bompear. Se almacenan solo en tu teléfono.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tienes es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los retiene hasta que tú los borres.",
      "ds.row2.col1": "<strong>Logs de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app crashea.</small>",
      "ds.row2.col2": "Diagnóstico de fallos. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row2.col4": "No — los logs pseudónimos se envían cuando hay un crash.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas pseudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento.",
      "ds.row3.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recolectado). Procesado por Google como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Backup automático de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu teléfono, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. Esto no aparece en la tabla de arriba porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: este backup va a tu cuenta de Google y lo gestiona Google, no Bomp. Está sujeto a los límites de Auto Backup (actualmente ~25 MB por app y purga tras un período prolongado de inactividad). Puedes desactivarlo desde Configuración del sistema → Sistema → Backup. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usas email, no usas contraseña, no usas OAuth, no usas SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu teléfono y los borras tú:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstala la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos pseudónimos en sistemas de Google: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu teléfono se borran al desinstalar, y los datos pseudónimos en sistemas de Google no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial.",

      // ── Legal · terms of service ─────────────
      "head.title.terms": "Términos del servicio — Bomp",
      "head.description.terms": "Términos del servicio de Bomp — alcance, responsabilidad del usuario sobre el contenido grabado y compartido, licencia de uso, ley aplicable y jurisdicción.",
      "nav.termsOfService": "Términos del servicio",
      "tos.title": "Términos del servicio",
      "tos.meta.app": "App: <strong>Bomp</strong>",
      "tos.meta.version": "Versión: <strong>1.0</strong>",
      "tos.meta.effective": "Fecha de efectividad: <strong>2026-05-09</strong>",
      "tos.meta.operator": "Operador: <strong>Nahuel Barrios</strong> — desarrollador y administrador de Bomp. Contacto: <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a>",
      "tos.intro": "Estos Términos del Servicio rigen tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. El documento master es la versión en español de Argentina (<code>es-AR</code>); en caso de divergencia con otras versiones idiomáticas, prevalece esa versión.",
      "tos.toc.s01": "01 · Aceptación y alcance",
      "tos.toc.s02": "02 · Edad mínima y capacidad legal",
      "tos.toc.s03": "03 · Licencia de uso",
      "tos.toc.s04": "04 · Responsabilidad del Bomper sobre el contenido",
      "tos.toc.s05": "05 · Usos prohibidos",
      "tos.toc.s06": "06 · Propiedad intelectual",
      "tos.toc.s07": "07 · Sin garantías (\"tal cual\")",
      "tos.toc.s08": "08 · Limitación de responsabilidad",
      "tos.toc.s09": "09 · Suspensión y terminación",
      "tos.toc.s10": "10 · Cambios a los Términos",
      "tos.toc.s11": "11 · Ley aplicable y jurisdicción",
      "tos.toc.s12": "12 · Notificaciones legales y contacto",
      "tos.toc.s13": "13 · Divisibilidad y acuerdo total",
      "tos.s01.body": "Estos Términos del Servicio (en adelante, <strong>\"los Términos\"</strong>) regulan tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. La aplicación es desarrollada y operada por <strong>Nahuel Barrios</strong> (en adelante, <strong>\"el operador\"</strong> o, indistintamente, <strong>\"Bomp\"</strong>). Al instalar, abrir o usar la aplicación de cualquier modo, manifiestas tu consentimiento expreso a estos Términos. Si no estás de acuerdo, no instales ni uses Bomp.",
      "tos.s02.body": "Para usar Bomp declaras que tienes la edad legal requerida en tu jurisdicción para celebrar contratos. Si eres menor de esa edad, debes contar con el consentimiento expreso de tu representante legal antes de instalar o usar la aplicación. Bomp puede suspender el acceso si recibe constancia de uso por menores sin la autorización requerida.",
      "tos.s03.body": "Bomp te otorga una licencia personal, no exclusiva, no transferible y revocable para instalar y usar la aplicación en dispositivos de tu titularidad o uso autorizado, exclusivamente con fines no comerciales. Esta licencia no transfiere ningún derecho de propiedad sobre la aplicación ni sus componentes. El código fuente se rige por sus propias licencias open source (BSL + AGPLv3) descritas en el repositorio público.",
      "tos.s04.intro": "Como Bomper eres el único responsable de los audios que grabas, editas, almacenas y compartes usando Bomp (en adelante, <strong>\"el Contenido\"</strong>). Al usar la aplicación declaras y garantizas que:",
      "tos.s04.liA": "<strong>(a)</strong> eres titular de todos los derechos sobre el Contenido o cuentas con las licencias y permisos necesarios para grabarlo, almacenarlo y distribuirlo;",
      "tos.s04.liB": "<strong>(b)</strong> cuando el Contenido incluya la voz, imagen o datos personales de terceros, cuentas con el consentimiento expreso e informado de esas personas conforme a las leyes aplicables en tu jurisdicción, incluyendo las normas locales equivalentes al derecho a la imagen y voz y a la protección de datos personales;",
      "tos.s04.liC": "<strong>(c)</strong> el Contenido no infringe derechos de propiedad intelectual, derechos personalísimos, normas contra la difamación, ni leyes penales aplicables;",
      "tos.s04.liD": "<strong>(d)</strong> no usas Bomp para grabar conversaciones o personas en contextos donde la grabación esté prohibida por la ley aplicable.",
      "tos.s04.indem": "Mantendrás indemne y a salvo a Bomp, sus colaboradores y asociados, frente a cualquier reclamo, demanda, sanción, costo, honorario o gasto derivado de Contenido generado, almacenado, editado o compartido por ti a través de Bomp.",
      "tos.s05.intro": "Está prohibido usar Bomp para:",
      "tos.s05.liA": "<strong>(a)</strong> almacenar o distribuir Contenido ilegal o que infrinja derechos de terceros;",
      "tos.s05.liB": "<strong>(b)</strong> hostigar, amenazar, acosar o intimidar a otras personas;",
      "tos.s05.liC": "<strong>(c)</strong> suplantar la identidad de otra persona o engañar sobre el origen de un audio;",
      "tos.s05.liD": "<strong>(d)</strong> realizar ingeniería inversa, descompilación o desensamblado de la aplicación más allá de lo permitido por las licencias open source aplicables;",
      "tos.s05.liE": "<strong>(e)</strong> usar la aplicación para difusión masiva automatizada o spam;",
      "tos.s05.liF": "<strong>(f)</strong> burlar las medidas técnicas de protección de la aplicación o de las plataformas a las que se conecta.",
      "tos.s05.body": "Bomp puede suspender o revocar el acceso a la aplicación frente a cualquier violación de esta cláusula.",
      "tos.s06.body1": "Bomp, el logotipo, el ícono, el <em>brand mark</em>, el <em>wordmark</em>, los textos, los gráficos y demás elementos visuales de la aplicación y del sitio web son propiedad de Nahuel Barrios o de sus licenciantes. El código fuente se rige por las licencias BSL + AGPLv3 descritas en el repositorio público.",
      "tos.s06.body2": "Tú retienes la titularidad sobre los audios que grabas y guardas en Bomp. Bomp <strong>no reclama</strong> ningún derecho sobre tu Contenido. La aplicación procesa los audios localmente en tu dispositivo conforme a lo descrito en la <a href=\"privacy-policy.html\">Política de Privacidad</a>.",
      "tos.s07.body": "La aplicación se entrega <em>\"tal cual\"</em> y <em>\"según disponibilidad\"</em>, sin garantías expresas ni implícitas sobre su funcionamiento, continuidad, ausencia de errores o defectos, idoneidad para un fin particular, ni resultados específicos derivados de su uso. Tus audios viven en tu teléfono: Bomp no opera servidores ni nube propia donde guardarlos. Si en tu sistema tienes activo el Auto Backup de Android, Google copia los datos de la app a <em>tu propio</em> Google Drive — ese mecanismo lo administra Google, no Bomp, y está sujeto a sus límites técnicos (actualmente ~25 MB por app y purga del backup tras un período prolongado de inactividad). Para los audios que no quieres perder, mantén copias propias por fuera de la app. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "tos.s08.body": "Hasta donde la ley aplicable lo permita, Bomp no será responsable por daños indirectos, incidentales, emergentes, punitivos o consecuenciales derivados del uso o de la imposibilidad de uso de la aplicación, incluyendo —sin limitación— pérdida de audios, pérdida de oportunidades, pérdida de datos o daños reputacionales. Esta limitación no aplica frente a daños que las normas de orden público de tu jurisdicción dispongan como no excluibles, en particular las normas de defensa del consumidor aplicables en cada país.",
      "tos.s09.body": "Bomp puede suspender o terminar tu acceso a la aplicación, sin necesidad de aviso previo, ante una violación material de estos Términos. Bomp también podrá discontinuar la aplicación, en su totalidad o respecto de algún servicio asociado, mediando aviso razonable a través del sitio web o de los canales de Google Play Store. La terminación no afecta las obligaciones del Bomper devengadas antes de la terminación; las cláusulas 4, 6 y 8 sobreviven a la terminación.",
      "tos.s10.body": "Bomp puede modificar estos Términos en cualquier momento. Los cambios materiales se anunciarán mediante un banner visible en el sitio web durante al menos 30 días, junto con la actualización de la Fecha de efectividad en el encabezado del documento. Continuar usando la aplicación luego de la Fecha de efectividad implica la aceptación de los Términos modificados. Si no estás de acuerdo con los cambios, debes cesar el uso de Bomp.",
      "tos.s11.body": "Estos Términos se rigen por las leyes de la República Argentina. Cualquier controversia derivada de los mismos se someterá a los tribunales ordinarios con competencia en la Ciudad Autónoma de Buenos Aires, salvo que las normas de orden público de la jurisdicción del Bomper —en particular, las leyes de defensa del consumidor— le permitan iniciar la acción en su propio domicilio, en cuyo caso ese derecho prevalece.",
      "tos.s12.body": "Las notificaciones legales formales deben enviarse por correo electrónico a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> (la misma dirección usada para solicitudes de <a href=\"privacy-policy.html#arco\">Derechos del titular</a>). Bomp responderá las comunicaciones formales dentro de los plazos legales aplicables.",
      "tos.s13.body": "Si un tribunal competente declara nula, inválida o inejecutable cualquier cláusula de estos Términos, el resto de las cláusulas conservará plena vigencia. Estos Términos, junto con la <a href=\"privacy-policy.html\">Política de Privacidad</a> y la página de <a href=\"data-safety.html\">Seguridad de los Datos</a>, constituyen el acuerdo total entre el Bomper y Bomp en relación con la aplicación y reemplazan cualquier acuerdo previo.",
      "tos.closing": "<em>Disposición final.</em> En caso de divergencia entre las versiones idiomáticas, prevalecerá la versión en español de Argentina (<code>es-AR</code>) como master legal."
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
      "pp.intro": "Esta política describe cómo Bomp (\"nosotros\", \"la app\") trata los datos del usuario (\"tú\", \"el Bomper\"). Bomp es una app de soundboard local: los audios que importas viven en tu móvil. La app no tiene cuentas ni nube de usuario; sí envía a Google un conjunto acotado de datos seudónimos (registros de fallos, diagnósticos de rendimiento e interacciones agregadas) que se detalla en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidad vigente desde el 2026-04-26. Distribución exclusiva en <a href=\"https://play.google.com/\">Google Play</a> en Argentina y la región de habla hispana.",
      "pp.s02.intro": "Bomp guarda <strong>audios que tú decides importar</strong> desde otras apps usando el sistema de \"Compartir\" de Android. La app necesita acceso a archivos de audio compartidos para esa operación.",
      "pp.s02.li1": "Los audios se almacenan en el almacenamiento interno asignado a Bomp por Android. Bomp <strong>no los sube a servidores del desarrollador</strong> ni los comparte automáticamente con terceros.",
      "pp.s02.li2": "<strong>Copia de seguridad automática de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu móvil (Ajustes del sistema → Sistema → Copia de seguridad), Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. La gestiona Google, no Bomp. Tres cosas que conviene saber: (1) está sujeta a la <strong>cuota de Auto Backup</strong> que define Google (actualmente ~25 MB por app) — si tu colección supera ese tamaño, Google no respalda lo que excede; (2) si desinstalas Bomp, la copia queda accesible para una eventual reinstalación, pero Google la purga tras un período prolongado de inactividad según su política; (3) puedes desactivar Auto Backup en cualquier momento desde los ajustes de tu móvil. Para los audios que no quieres perder, te recomendamos exportar copias propias fuera de Bomp.",
      "pp.s02.li3": "Cuando tú compartes un audio desde Bomp hacia otra app, la transferencia ocurre a través del sistema de \"Compartir\" de Android; Bomp no tiene visibilidad sobre qué hace la app receptora.",
      "pp.s02.li4": "Cuando borras un audio desde la app, el archivo se elimina del almacenamiento local. Cuando desinstalas la app, todos los audios se eliminan junto con los datos de la app; la copia en tu Google Drive (si tienes Auto Backup activo) queda accesible para una eventual reinstalación, pero Google la purga tras un período prolongado de inactividad según su política.",
      "pp.s03.intro": "Bomp utiliza servicios de Google integrados en el ecosistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — infraestructura base de Google Play para distribución y actualización de la app.",
      "pp.s03.li2": "<strong>Diagnóstico de fallos</strong> — recopila <em>registros de fallos</em> de forma seudónima cuando la app falla, para que podamos arreglar el bug. No se recopila contenido del usuario (audios, nombres de botones).",
      "pp.s03.li3": "<strong>Monitorización de rendimiento</strong> — recopila <em>diagnósticos seudónimos agregados</em> (tiempo de arranque, uso de memoria, latencias) con el propósito de detectar regresiones release a release.",
      "pp.s03.li4": "<strong>Analítica de uso</strong> — recopila <em>eventos agregados</em> (cantidad de Bomps, sesiones, interacciones con la UI) sin vincularlos a ningún usuario identificado, con el propósito de entender patrones de uso y priorizar mejoras.",
      "pp.s03.body": "Bomp <strong>no usa</strong>: redes de publicidad, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>Bomp no vende datos.</strong> Los datos seudónimos que sí compartimos con Google (registros de fallos, diagnósticos de rendimiento, interacciones agregadas) se procesan con propósito de diagnóstico y analítica agregada; el detalle exhaustivo y el cifrado en tránsito están en <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp está diseñado para uso general y no se dirige a menores de 13 años. La app no requiere creación de cuenta, no pide email, ni recoge nombre, edad ni datos que identifiquen directamente al usuario. Si un menor utiliza la app, los únicos datos que salen del dispositivo son los seudónimos descritos en <a href=\"data-safety.html\">Data Safety</a> (registros de fallos, diagnósticos de rendimiento, interacciones agregadas), enviados a Google sin asociarse a información identificable.",
      "pp.s04.body2": "Bomp se alinea con los lineamientos de COPPA (USA) y RGPD (UE) por diseño: no recopilamos datos directamente identificables. Si eres madre/padre/tutor y quieres que purguemos el identificador de instalación seudónimo asociado al dispositivo de un menor, escríbenos por el canal de <a href=\"#arco\">Derechos del usuario (ARCO)</a>.",
      "pp.s05.intro": "Bajo la Ley 25.326 (Argentina) y el RGPD (UE), tienes derecho a:",
      "pp.s05.li1": "<strong>Acceder</strong> a los datos en tu móvil: abre la app y vas a ver todos los audios y botones que importaste.",
      "pp.s05.li2": "<strong>Rectificarlos</strong>: renombra o re-importa audios desde la app.",
      "pp.s05.li3": "<strong>Cancelarlos</strong>: bórralos desde la app, o desinstala Bomp para borrarlos todos.",
      "pp.s05.li4": "<strong>Oponerte</strong> al tratamiento de los datos seudónimos por Google.",
      "pp.s05.body2": "Para los datos seudónimos que viven en sistemas de Google (registros de fallos, diagnósticos de rendimiento, interacciones agregadas), Bomp no almacena tu nombre, email ni teléfono — solo un identificador opaco de instalación que Google asigna al instalar la app. Para ejercer derechos sobre esos datos:",
      "pp.s05.li5": "<strong>Desinstala la app</strong>: corta la recolección y Google purga eventualmente los datos asociados al identificador.",
      "pp.s05.li6": "<strong>Restablece tu Advertising ID</strong> desde Ajustes de Android → Privacidad → Anuncios. Esto desvincula sesiones futuras.",
      "pp.s05.li7": "<strong>Solicitud manual</strong>: escríbenos a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> con tu identificador de instalación seudónimo (te ayudamos a obtenerlo si lo necesitas) y borramos el registro asociado.",
      "pp.s05.li8": "<strong>Vía Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> te permite solicitar el borrado a Google directamente, ya que actúa como sub-procesador.",
      "pp.s05.body3": "Bajo el RGPD Art. 11, como Bomp no puede identificarte sin tu cooperación (no almacenamos información que te conecte con tu identificador de instalación), no estamos obligados a mantener un mecanismo de identificación adicional. Tu cooperación, enviando ese identificador, es lo que habilita el borrado puntual.",
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
      "ds.row1.col1": "<strong>Otros archivos de audio</strong><br><small>Los audios que tú importas desde otras apps usando el sistema de \"Compartir\" de Android.</small>",
      "ds.row1.col2": "Funcionalidad de la app: son los audios que tú decides Bompear. Se almacenan solo en tu móvil.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — el control que tienes es decidir qué archivos importar voluntariamente; una vez importados, el almacenamiento local de Bomp los conserva hasta que tú los borres.",
      "ds.row2.col1": "<strong>Registros de fallos</strong><br><small>Stack traces y estado del dispositivo cuando la app falla.</small>",
      "ds.row2.col2": "Diagnóstico de fallos. Nos permite arreglar bugs.",
      "ds.row2.col3": "No (solo recopilado). Procesado por Google como sub-procesador.",
      "ds.row2.col4": "No — los registros seudónimos se envían cuando hay un fallo.",
      "ds.row3.col1": "<strong>Diagnósticos de rendimiento</strong><br><small>Métricas seudónimas de uso de memoria, tiempo de arranque, latencias.</small>",
      "ds.row3.col2": "Detección de regresiones de rendimiento.",
      "ds.row3.col3": "No (solo recopilado). Procesado por Google como sub-procesador.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>Interacciones con la app</strong><br><small>Eventos agregados (cantidad de Bomps, sesiones).</small>",
      "ds.row4.col2": "Entender patrones de uso para priorizar mejoras. Sin asociar a ningún usuario identificado.",
      "ds.row4.col3": "No (solo recopilado). Procesado por Google como sub-procesador.",
      "ds.row4.col4": "No.",
      "ds.title": "Seguridad de los datos",
      "ds.s01.autoBackup": "<strong>Copia de seguridad automática de Android.</strong> Bomp tiene habilitado el <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup de Android</a>: si lo tienes activo en tu móvil, Google respalda los datos de la app (incluidos los audios importados) en <em>tu propio</em> Google Drive, en una zona privada accesible solo por la app. Esto no aparece en la tabla de arriba porque el formulario de Data Safety de Play declara solo lo que la app comparte con el desarrollador o con terceros: esta copia va a tu cuenta de Google y la gestiona Google, no Bomp. Está sujeta a los límites de Auto Backup (actualmente ~25 MB por app y purga tras un período prolongado de inactividad). Puedes desactivarla desde Ajustes del sistema → Sistema → Copia de seguridad. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "ds.s02.body1": "<strong>Bomp no requiere creación de cuenta.</strong> No usas email, no usas contraseña, no usas OAuth, no usas SIM. La app abre y funciona.",
      "ds.s02.body2": "<strong>Eliminación de datos.</strong> Como no hay cuenta, no hay un flujo \"borrar mi cuenta\". Los datos viven en tu móvil y los borras tú:",
      "ds.s02.li1": "Borrar audios uno a uno desde la lista de Bomp.",
      "ds.s02.li2": "Borrar todos los audios: desinstala la app desde el sistema. Android limpia el almacenamiento asignado a Bomp.",
      "ds.s02.li3": "Para los datos seudónimos en sistemas de Google: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre el flujo de borrado en Play.</strong> En la ficha de Bomp en Play declaramos que la app no provee un flujo de auto-servicio de eliminación de datos. La razón: los datos en tu móvil se borran al desinstalar, y los datos seudónimos en sistemas de Google no están asociados a una cuenta que se pueda \"cerrar\"; el procedimiento manual está en <a href=\"privacy-policy.html#arco\">Privacy Policy → Derechos ARCO</a>.",
      "ds.s03.body": "Toda la comunicación entre Bomp y los servidores de Google utiliza HTTPS (TLS 1.2 o superior). Las llamadas a Google Play Services siguen el estándar de cifrado del SDK oficial.",

      // ── Legal · terms of service ─────────────
      "head.title.terms": "Términos del servicio — Bomp",
      "head.description.terms": "Términos del servicio de Bomp — alcance, responsabilidad del usuario sobre el contenido grabado y compartido, licencia de uso, legislación aplicable y jurisdicción.",
      "nav.termsOfService": "Términos del servicio",
      "tos.title": "Términos del servicio",
      "tos.meta.app": "App: <strong>Bomp</strong>",
      "tos.meta.version": "Versión: <strong>1.0</strong>",
      "tos.meta.effective": "Fecha de efectividad: <strong>2026-05-09</strong>",
      "tos.meta.operator": "Operador: <strong>Nahuel Barrios</strong> — desarrollador y administrador de Bomp. Contacto: <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a>",
      "tos.intro": "Estos Términos del Servicio rigen tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. El documento master es la versión en español de Argentina (<code>es-AR</code>); en caso de divergencia con otras versiones idiomáticas, prevalece esa versión.",
      "tos.toc.s01": "01 · Aceptación y alcance",
      "tos.toc.s02": "02 · Edad mínima y capacidad legal",
      "tos.toc.s03": "03 · Licencia de uso",
      "tos.toc.s04": "04 · Responsabilidad del Bomper sobre el contenido",
      "tos.toc.s05": "05 · Usos prohibidos",
      "tos.toc.s06": "06 · Propiedad intelectual",
      "tos.toc.s07": "07 · Sin garantías (\"tal cual\")",
      "tos.toc.s08": "08 · Limitación de responsabilidad",
      "tos.toc.s09": "09 · Suspensión y resolución",
      "tos.toc.s10": "10 · Cambios en los Términos",
      "tos.toc.s11": "11 · Legislación aplicable y jurisdicción",
      "tos.toc.s12": "12 · Notificaciones legales y contacto",
      "tos.toc.s13": "13 · Divisibilidad y acuerdo total",
      "tos.s01.body": "Estos Términos del Servicio (en adelante, <strong>\"los Términos\"</strong>) regulan tu uso de la aplicación móvil <strong>Bomp</strong> y de los sitios web asociados. La aplicación es desarrollada y operada por <strong>Nahuel Barrios</strong> (en adelante, <strong>\"el operador\"</strong> o, indistintamente, <strong>\"Bomp\"</strong>). Al instalar, abrir o usar la aplicación de cualquier modo, manifiestas tu consentimiento expreso a estos Términos. Si no estás de acuerdo, no instales ni uses Bomp.",
      "tos.s02.body": "Para usar Bomp declaras tener la edad legal requerida en tu jurisdicción para celebrar contratos. Si eres menor de esa edad, deberás contar con el consentimiento expreso de tu representante legal antes de instalar o usar la aplicación. Bomp podrá suspender el acceso si recibe constancia de uso por menores sin la autorización requerida.",
      "tos.s03.body": "Bomp te otorga una licencia personal, no exclusiva, no transferible y revocable para instalar y usar la aplicación en dispositivos de tu titularidad o uso autorizado, exclusivamente con fines no comerciales. Esta licencia no transfiere ningún derecho de propiedad sobre la aplicación ni sus componentes. El código fuente se rige por sus propias licencias open source (BSL + AGPLv3) descritas en el repositorio público.",
      "tos.s04.intro": "Como Bomper eres el único responsable de los audios que grabas, editas, almacenas y compartes usando Bomp (en adelante, <strong>\"el Contenido\"</strong>). Al usar la aplicación declaras y garantizas que:",
      "tos.s04.liA": "<strong>(a)</strong> eres titular de todos los derechos sobre el Contenido o cuentas con las licencias y permisos necesarios para grabarlo, almacenarlo y distribuirlo;",
      "tos.s04.liB": "<strong>(b)</strong> cuando el Contenido incluya la voz, imagen o datos personales de terceros, cuentas con el consentimiento expreso e informado de esas personas conforme al Reglamento (UE) 2016/679 (RGPD), la Ley Orgánica 3/2018 de Protección de Datos Personales y garantía de los derechos digitales (LOPDGDD), y las normas aplicables sobre derecho a la propia imagen;",
      "tos.s04.liC": "<strong>(c)</strong> el Contenido no infringe derechos de propiedad intelectual, derechos al honor, a la intimidad personal y familiar y a la propia imagen, ni normas penales aplicables;",
      "tos.s04.liD": "<strong>(d)</strong> no usas Bomp para grabar conversaciones o personas en contextos donde la grabación esté prohibida por la ley aplicable.",
      "tos.s04.indem": "Mantendrás indemne y a salvo a Bomp, sus colaboradores y asociados, frente a cualquier reclamación, demanda, sanción, coste, honorario o gasto derivado de Contenido generado, almacenado, editado o compartido por ti a través de Bomp.",
      "tos.s05.intro": "Está prohibido usar Bomp para:",
      "tos.s05.liA": "<strong>(a)</strong> almacenar o distribuir Contenido ilegal o que infrinja derechos de terceros;",
      "tos.s05.liB": "<strong>(b)</strong> hostigar, amenazar, acosar o intimidar a otras personas;",
      "tos.s05.liC": "<strong>(c)</strong> suplantar la identidad de otra persona o engañar sobre el origen de un audio;",
      "tos.s05.liD": "<strong>(d)</strong> realizar ingeniería inversa, descompilación o desensamblado de la aplicación más allá de lo permitido por las licencias open source aplicables;",
      "tos.s05.liE": "<strong>(e)</strong> usar la aplicación para difusión masiva automatizada o spam;",
      "tos.s05.liF": "<strong>(f)</strong> eludir las medidas técnicas de protección de la aplicación o de las plataformas a las que se conecta.",
      "tos.s05.body": "Bomp podrá suspender o revocar el acceso a la aplicación ante cualquier infracción de esta cláusula.",
      "tos.s06.body1": "Bomp, el logotipo, el ícono, el <em>brand mark</em>, el <em>wordmark</em>, los textos, los gráficos y demás elementos visuales de la aplicación y del sitio web son propiedad de Nahuel Barrios o de sus licenciantes. El código fuente se rige por las licencias BSL + AGPLv3 descritas en el repositorio público.",
      "tos.s06.body2": "Tú conservas la titularidad sobre los audios que grabas y guardas en Bomp. Bomp <strong>no reclama</strong> ningún derecho sobre tu Contenido. La aplicación procesa los audios localmente en tu dispositivo según se describe en la <a href=\"privacy-policy.html\">Política de Privacidad</a>.",
      "tos.s07.body": "La aplicación se entrega <em>\"tal cual\"</em> y <em>\"según disponibilidad\"</em>, sin garantías expresas ni implícitas sobre su funcionamiento, continuidad, ausencia de errores o defectos, idoneidad para un fin particular, ni resultados específicos derivados de su uso. Tus audios viven en tu móvil: Bomp no opera servidores ni nube propia donde guardarlos. Si en tu sistema tienes activa la Copia de seguridad automática de Android (Auto Backup), Google copia los datos de la app a <em>tu propio</em> Google Drive — ese mecanismo lo gestiona Google, no Bomp, y está sujeto a sus límites técnicos (actualmente ~25 MB por app y purga de la copia tras un período prolongado de inactividad). Para los audios que no quieres perder, mantén copias propias fuera de la app. El detalle completo está en la <a href=\"privacy-policy.html#datos-audio\">Política de privacidad</a>.",
      "tos.s08.body": "Hasta donde la legislación aplicable lo permita, Bomp no será responsable de daños indirectos, incidentales, emergentes, punitivos o consecuenciales derivados del uso o imposibilidad de uso de la aplicación, incluyendo —sin limitación— pérdida de audios, pérdida de oportunidades, pérdida de datos o daños reputacionales. Esta limitación no se aplica a los daños que el Texto Refundido de la Ley General para la Defensa de los Consumidores y Usuarios (TRLGDCU) o normas concordantes consideren no excluibles, ni a los supuestos de dolo o culpa grave.",
      "tos.s09.body": "Bomp podrá suspender o resolver tu acceso a la aplicación, sin necesidad de aviso previo, ante incumplimiento material de estos Términos. Bomp también podrá discontinuar la aplicación, en su totalidad o respecto de algún servicio asociado, mediando aviso razonable a través del sitio web o de los canales de Google Play Store. La resolución no afecta las obligaciones del Bomper devengadas antes de la misma; las cláusulas 4, 6 y 8 sobreviven a la resolución.",
      "tos.s10.body": "Bomp podrá modificar estos Términos en cualquier momento. Los cambios sustanciales se anunciarán mediante un banner visible en el sitio web durante al menos 30 días, junto con la actualización de la Fecha de efectividad en el encabezado del documento. Continuar usando la aplicación tras la Fecha de efectividad supone la aceptación de los Términos modificados. Si no estás conforme con los cambios, deberás cesar el uso de Bomp.",
      "tos.s11.body": "Estos Términos se rigen por las leyes de la República Argentina. Cualquier controversia se someterá a los tribunales ordinarios con competencia en la Ciudad Autónoma de Buenos Aires, salvo que las normas de orden público de tu jurisdicción —en particular, las normas de defensa del consumidor aplicables en España— te permitan ejercer tus acciones ante los tribunales de tu domicilio, en cuyo caso ese derecho prevalece.",
      "tos.s12.body": "Las notificaciones legales formales deben enviarse por correo electrónico a <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> (la misma dirección usada para solicitudes de <a href=\"privacy-policy.html#arco\">derechos del interesado</a>). Bomp responderá las comunicaciones formales dentro de los plazos legales aplicables.",
      "tos.s13.body": "Si un tribunal competente declara nula, inválida o no exigible cualquier cláusula de estos Términos, el resto de las cláusulas conservarán plena vigencia. Estos Términos, junto con la <a href=\"privacy-policy.html\">Política de Privacidad</a> y la página de <a href=\"data-safety.html\">Seguridad de los Datos</a>, constituyen el acuerdo total entre el Bomper y Bomp respecto de la aplicación y sustituyen cualquier acuerdo anterior.",
      "tos.closing": "<em>Disposición final.</em> En caso de divergencia entre las versiones lingüísticas de estos Términos, prevalecerá la versión en español de Argentina (<code>es-AR</code>) como master legal."
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
      "pp.intro": "This policy describes how Bomp (\"we\", \"the app\") handles user data (\"you\", \"the Bomper\"). Bomp is a local soundboard app: the audios you import live on your phone. The app has no user accounts or cloud; it does send Google a limited set of pseudonymous data (crash logs, performance diagnostics, and aggregated interactions) detailed in <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Privacy policy in effect since 2026-04-26. Distributed exclusively through <a href=\"https://play.google.com/\">Google Play</a> in Argentina and the Spanish-speaking region.",
      "pp.s02.intro": "Bomp stores <strong>audios you choose to import</strong> from other apps using Android's \"Share\" system. The app needs access to shared audio files for that operation.",
      "pp.s02.li1": "Audios are stored in Android's internal storage assigned to Bomp. Bomp <strong>does not upload them to developer servers</strong> nor share them automatically with third parties.",
      "pp.s02.li2": "<strong>Android Auto Backup.</strong> Bomp has <a href=\"https://developer.android.com/identity/data/autobackup\">Android Auto Backup</a> enabled: if you have it active on your phone (System settings → System → Backup), Google backs up the app's data (including imported audios) to <em>your own</em> Google Drive, in a private area accessible only to the app. Google manages it, not Bomp. Three things worth knowing: (1) it is subject to the <strong>Auto Backup quota</strong> that Google defines (currently ~25 MB per app) — if your collection exceeds that size, Google does not back up what's over the limit; (2) if you uninstall Bomp, the backup remains accessible for a possible reinstall, but Google purges it after a prolonged period of inactivity according to its policy; (3) you can disable Auto Backup at any time from your phone's settings. For audios you don't want to lose, we recommend exporting your own copies outside Bomp.",
      "pp.s02.li3": "When you share an audio from Bomp to another app, the transfer happens through Android's \"Share\" system; Bomp has no visibility into what the receiving app does.",
      "pp.s02.li4": "When you delete an audio from the app, the file is removed from local storage. When you uninstall the app, all audios are removed along with the app's data; the backup on your Google Drive (if you have Auto Backup active) remains accessible for a possible reinstall, but Google purges it after a prolonged period of inactivity according to its policy.",
      "pp.s03.intro": "Bomp uses Google services integrated into the Android ecosystem:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — Google Play's core infrastructure for app distribution and updates.",
      "pp.s03.li2": "<strong>Crash diagnostics</strong> — collects <em>crash logs</em> pseudonymously when the app crashes, so we can fix the bug. No user content is collected (audios, button names).",
      "pp.s03.li3": "<strong>Performance monitoring</strong> — collects <em>aggregated pseudonymous diagnostics</em> (startup time, memory usage, latencies) to detect regressions release over release.",
      "pp.s03.li4": "<strong>Usage analytics</strong> — collects <em>aggregated events</em> (number of Bomps, sessions, UI interactions) without linking them to any identified user, to understand usage patterns and prioritize improvements.",
      "pp.s03.body": "Bomp <strong>does not use</strong>: ad networks, tracking pixels, tracking cookies, device fingerprinting. <strong>Bomp does not sell data.</strong> The pseudonymous data we do share with Google (crash logs, performance diagnostics, aggregated interactions) is processed for diagnostic and aggregated analytics purposes; the exhaustive detail and in-transit encryption are in <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "Bomp is designed for general use and does not target children under 13. The app does not require account creation, does not ask for email, nor collect name, age, or data that directly identify the user. If a minor uses the app, the only data leaving the device are the pseudonymous ones described in <a href=\"data-safety.html\">Data Safety</a> (crash logs, performance diagnostics, aggregated interactions), sent to Google without being linked to identifiable information.",
      "pp.s04.body2": "Bomp aligns with COPPA (USA) and GDPR (EU) guidelines by design: we do not collect directly identifiable data. If you are a parent/guardian and want us to purge the pseudonymous installation identifier associated with a minor's device, contact us through <a href=\"#arco\">User rights (ARCO)</a>.",
      "pp.s05.intro": "Under Argentina's Law 25.326 and the GDPR (EU), you have the right to:",
      "pp.s05.li1": "<strong>Access</strong> the data on your phone: open the app and you'll see all imported audios and buttons.",
      "pp.s05.li2": "<strong>Rectify them</strong>: rename or re-import audios from the app.",
      "pp.s05.li3": "<strong>Cancel them</strong>: delete them from the app, or uninstall Bomp to delete them all.",
      "pp.s05.li4": "<strong>Object</strong> to the processing of pseudonymous data by Google.",
      "pp.s05.body2": "For pseudonymous data living in Google's systems (crash logs, performance diagnostics, aggregated interactions), Bomp does not store your name, email, or phone number — only an opaque installation identifier that Google assigns when installing the app. To exercise rights over that data:",
      "pp.s05.li5": "<strong>Uninstall the app</strong>: cuts collection and Google eventually purges the data associated with the identifier.",
      "pp.s05.li6": "<strong>Reset your Advertising ID</strong> from Android Settings → Privacy → Ads. This unlinks future sessions.",
      "pp.s05.li7": "<strong>Manual request</strong>: write to us at <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> with your pseudonymous installation identifier (we'll help you find it if needed) and we'll delete the associated record.",
      "pp.s05.li8": "<strong>Via Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> lets you request deletion from Google directly, since it acts as a sub-processor.",
      "pp.s05.body3": "Under GDPR Art. 11, since Bomp cannot identify you without your cooperation (we don't store information that links you to your installation identifier), we are not obliged to maintain an additional identification mechanism. Your cooperation, sending that identifier, is what enables targeted deletion.",
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
      "ds.row1.col1": "<strong>Other audio files</strong><br><small>The audios you import from other apps using Android's \"Share\" system.</small>",
      "ds.row1.col2": "App functionality: they are the audios you choose to Bomp. Stored only on your phone.",
      "ds.row1.col3": "No.",
      "ds.row1.col4": "No — the control you have is deciding which files to import voluntarily; once imported, Bomp's local storage retains them until you delete them.",
      "ds.row2.col1": "<strong>Crash logs</strong><br><small>Stack traces and device state when the app crashes.</small>",
      "ds.row2.col2": "Crash diagnostics. Lets us fix bugs.",
      "ds.row2.col3": "No (collected only). Processed by Google as sub-processor.",
      "ds.row2.col4": "No — pseudonymous logs are sent when there's a crash.",
      "ds.row3.col1": "<strong>Performance diagnostics</strong><br><small>Pseudonymous metrics on memory usage, startup time, latencies.</small>",
      "ds.row3.col2": "Performance regression detection.",
      "ds.row3.col3": "No (collected only). Processed by Google as sub-processor.",
      "ds.row3.col4": "No.",
      "ds.row4.col1": "<strong>App interactions</strong><br><small>Aggregated events (number of Bomps, sessions).</small>",
      "ds.row4.col2": "Understand usage patterns to prioritize improvements. Not associated with any identified user.",
      "ds.row4.col3": "No (collected only). Processed by Google as sub-processor.",
      "ds.row4.col4": "No.",
      "ds.title": "Data Safety",
      "ds.s01.autoBackup": "<strong>Android Auto Backup.</strong> Bomp has <a href=\"https://developer.android.com/identity/data/autobackup\">Android Auto Backup</a> enabled: if you have it active on your phone, Google backs up the app's data (including imported audios) to <em>your own</em> Google Drive, in a private area accessible only to the app. This does not appear in the table above because Play's Data Safety form only declares what the app shares with the developer or with third parties: this backup goes to your Google account and is managed by Google, not Bomp. It is subject to Auto Backup's limits (currently ~25 MB per app and purge after a prolonged period of inactivity). You can disable it from System settings → System → Backup. Full detail in the <a href=\"privacy-policy.html#datos-audio\">Privacy Policy</a>.",
      "ds.s02.body1": "<strong>Bomp does not require account creation.</strong> No email, no password, no OAuth, no SIM. The app opens and works.",
      "ds.s02.body2": "<strong>Data deletion.</strong> Since there is no account, there is no \"delete my account\" flow. Data lives on your phone and you delete it:",
      "ds.s02.li1": "Delete audios one by one from Bomp's list.",
      "ds.s02.li2": "Delete all audios: uninstall the app from the system. Android cleans up Bomp's assigned storage.",
      "ds.s02.li3": "For pseudonymous data in Google's systems: see <a href=\"privacy-policy.html#arco\">Privacy Policy → ARCO Rights</a>.",
      "ds.s02.body3": "<strong>About Play's deletion flow.</strong> In Bomp's Play listing we declare that the app does not provide a self-service data deletion flow. The reason: data on your phone is deleted on uninstall, and pseudonymous data in Google's systems is not associated with an account that can be \"closed\"; the manual procedure is in <a href=\"privacy-policy.html#arco\">Privacy Policy → ARCO Rights</a>.",
      "ds.s03.body": "All communication between Bomp and Google servers uses HTTPS (TLS 1.2 or higher). Calls to Google Play Services follow the official SDK's encryption standard.",

      // ── Legal · terms of service ─────────────
      "head.title.terms": "Terms of Service — Bomp",
      "head.description.terms": "Bomp Terms of Service — scope, user responsibility for recorded and shared content, license to use, governing law and jurisdiction.",
      "nav.termsOfService": "Terms of Service",
      "tos.title": "Terms of Service",
      "tos.meta.app": "App: <strong>Bomp</strong>",
      "tos.meta.version": "Version: <strong>1.0</strong>",
      "tos.meta.effective": "Effective Date: <strong>2026-05-09</strong>",
      "tos.meta.operator": "Operator: <strong>Nahuel Barrios</strong> — developer and administrator of Bomp. Contact: <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a>",
      "tos.intro": "These Terms of Service govern your use of the <strong>Bomp</strong> mobile application and associated websites. The legal master is the Argentine Spanish version (<code>es-AR</code>); in case of divergence between language versions, that version prevails.",
      "tos.toc.s01": "01 · Acceptance and scope",
      "tos.toc.s02": "02 · Minimum age and legal capacity",
      "tos.toc.s03": "03 · License to use",
      "tos.toc.s04": "04 · Bomper's responsibility for content",
      "tos.toc.s05": "05 · Prohibited uses",
      "tos.toc.s06": "06 · Intellectual property",
      "tos.toc.s07": "07 · No warranties (\"as is\")",
      "tos.toc.s08": "08 · Limitation of liability",
      "tos.toc.s09": "09 · Suspension and termination",
      "tos.toc.s10": "10 · Changes to the Terms",
      "tos.toc.s11": "11 · Governing law and jurisdiction",
      "tos.toc.s12": "12 · Legal notices and contact",
      "tos.toc.s13": "13 · Severability and entire agreement",
      "tos.s01.body": "These Terms of Service (hereafter, <strong>\"the Terms\"</strong>) govern your use of the <strong>Bomp</strong> mobile application and associated websites. The application is developed and operated by <strong>Nahuel Barrios</strong> (hereafter, <strong>\"the operator\"</strong> or, interchangeably, <strong>\"Bomp\"</strong>). By installing, opening, or otherwise using the application, you expressly consent to these Terms. If you do not agree, do not install or use Bomp.",
      "tos.s02.body": "By using Bomp, you represent that you are of the legal age required in your jurisdiction to enter into binding contracts. If you are below that age, you must have the express consent of your legal representative before installing or using the application. Bomp may suspend access if it becomes aware of use by minors without the required authorization.",
      "tos.s03.body": "Bomp grants you a personal, non-exclusive, non-transferable, and revocable license to install and use the application on devices you own or are authorized to use, solely for non-commercial purposes. This license transfers no ownership rights in the application or its components. The source code is governed by its own open source licenses (BSL + AGPLv3) as described in the public repository.",
      "tos.s04.intro": "As a Bomper, you are solely responsible for the audio recordings you create, edit, store, and share using Bomp (hereafter, <strong>\"the Content\"</strong>). By using the application, you represent and warrant that:",
      "tos.s04.liA": "<strong>(a)</strong> you own all rights to the Content or hold the licenses and permissions necessary to record, store, and distribute it;",
      "tos.s04.liB": "<strong>(b)</strong> when the Content includes the voice, image, or personal data of third parties, you have obtained the express, informed consent of those persons in accordance with applicable laws — including, where applicable, Article 53 of the Argentine Civil and Commercial Code on the right to one's voice and image, Argentine Law 25,326 on Personal Data Protection, the EU GDPR, the Brazilian LGPD, and equivalent norms in other jurisdictions;",
      "tos.s04.liC": "<strong>(c)</strong> the Content does not infringe intellectual property rights, personal rights, anti-defamation laws, or applicable criminal laws;",
      "tos.s04.liD": "<strong>(d)</strong> you do not use Bomp to record conversations or persons in contexts where such recording is prohibited by applicable law.",
      "tos.s04.indem": "You shall indemnify and hold harmless Bomp, its collaborators, and affiliates from any claim, demand, sanction, cost, fee, or expense arising from Content created, stored, edited, or shared by you through Bomp.",
      "tos.s05.intro": "You may not use Bomp to:",
      "tos.s05.liA": "<strong>(a)</strong> store or distribute illegal Content or Content that infringes the rights of third parties;",
      "tos.s05.liB": "<strong>(b)</strong> harass, threaten, stalk, or intimidate other persons;",
      "tos.s05.liC": "<strong>(c)</strong> impersonate another person or deceive about the origin of an audio;",
      "tos.s05.liD": "<strong>(d)</strong> reverse-engineer, decompile, or disassemble the application beyond what is permitted by the applicable open source licenses;",
      "tos.s05.liE": "<strong>(e)</strong> use the application for automated mass distribution or spam;",
      "tos.s05.liF": "<strong>(f)</strong> circumvent technical protection measures of the application or of platforms it connects to.",
      "tos.s05.body": "Bomp may suspend or revoke access to the application for any breach of this clause.",
      "tos.s06.body1": "Bomp, the logo, the icon, the <em>brand mark</em>, the <em>wordmark</em>, the texts, graphics, and other visual elements of the application and the website are owned by Nahuel Barrios or his licensors. The source code is governed by the BSL + AGPLv3 licenses described in the public repository.",
      "tos.s06.body2": "You retain ownership of the audio recordings you create and store in Bomp. Bomp <strong>does not claim</strong> any rights over your Content. The application processes those audio recordings locally on your device as described in the <a href=\"privacy-policy.html\">Privacy Policy</a>.",
      "tos.s07.body": "The application is provided <em>\"as is\"</em> and <em>\"as available\"</em>, without express or implied warranties of any kind regarding its operation, continuity, freedom from errors or defects, fitness for a particular purpose, or specific results from its use. Your audios live on your phone: Bomp operates no servers or cloud where they could be stored. If you have Android Auto Backup enabled on your system, Google copies the app's data to <em>your own</em> Google Drive — that mechanism is managed by Google, not Bomp, and is subject to its technical limits (currently ~25 MB per app and backup purge after a prolonged period of inactivity). For audios you don't want to lose, keep your own copies outside the app. Full detail in the <a href=\"privacy-policy.html#datos-audio\">Privacy Policy</a>.",
      "tos.s08.body": "To the maximum extent permitted by applicable law, Bomp shall not be liable for any indirect, incidental, consequential, punitive, or special damages arising from the use or inability to use the application, including — without limitation — loss of audio, loss of opportunity, loss of data, or reputational harm. This limitation does not apply to damages that mandatory laws of your jurisdiction deem non-excludable, including consumer protection statutes applicable to you, nor does it apply in cases of intentional misconduct or gross negligence.",
      "tos.s09.body": "Bomp may suspend or terminate your access to the application, without prior notice, in the event of a material breach of these Terms. Bomp may also discontinue the application, in whole or with respect to any associated service, with reasonable notice via the website or Google Play Store channels. Termination does not affect Bomper obligations accrued prior to termination; clauses 4, 6, and 8 survive termination.",
      "tos.s10.body": "Bomp may modify these Terms at any time. Material changes will be announced via a banner visible on the website for at least 30 days, together with an update to the Effective Date in the document header. Continuing to use the application after the Effective Date constitutes acceptance of the modified Terms. If you do not agree to the changes, you must stop using Bomp.",
      "tos.s11.body": "These Terms are governed by the laws of the Argentine Republic. Any dispute arising hereunder shall be submitted to the ordinary courts having jurisdiction in the Autonomous City of Buenos Aires, except where mandatory consumer-protection laws of your jurisdiction allow you to bring proceedings before the courts of your domicile, in which case that right prevails.",
      "tos.s12.body": "Formal legal notices must be sent by email to <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> (the same address used for <a href=\"privacy-policy.html#arco\">data subject requests</a>). Bomp will respond to formal communications within applicable legal time frames.",
      "tos.s13.body": "If any clause of these Terms is held by a competent court to be void, invalid, or unenforceable, the remaining clauses shall remain in full effect. These Terms, together with the <a href=\"privacy-policy.html\">Privacy Policy</a> and the <a href=\"data-safety.html\">Data Safety</a> page, constitute the entire agreement between the Bomper and Bomp regarding the application and supersede any prior agreement.",
      "tos.closing": "<em>Closing provision.</em> In case of divergence between language versions of these Terms, the Argentine Spanish (<code>es-AR</code>) version prevails as the legal master."
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
      "pp.intro": "Esta política descreve como o Bomp (\"nós\", \"o app\") trata os dados do usuário (\"você\", \"o Bomper\"). O Bomp é um app de soundboard local: os áudios que você importa ficam no seu celular. O app não tem contas nem nuvem de usuário; envia ao Google um conjunto restrito de dados pseudônimos (logs de falhas, diagnósticos de desempenho e interações agregadas) detalhado em <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s01.body": "<strong>Bomp v1.0</strong>. Política de privacidade vigente desde 2026-04-26. Distribuição exclusiva pelo <a href=\"https://play.google.com/\">Google Play</a> na Argentina e na região hispano-falante.",
      "pp.s02.intro": "O Bomp guarda <strong>áudios que você decide importar</strong> de outros apps usando o sistema de \"Compartilhar\" do Android. O app precisa de acesso aos arquivos de áudio compartilhados para essa operação.",
      "pp.s02.li1": "Os áudios ficam no armazenamento interno do Android atribuído ao Bomp. O Bomp <strong>não os envia para servidores do desenvolvedor</strong> nem os compartilha automaticamente com terceiros.",
      "pp.s02.li2": "<strong>Backup automático do Android.</strong> O Bomp tem habilitado o <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup do Android</a>: se você tem ativo no celular (Configurações do sistema → Sistema → Backup), o Google faz backup dos dados do app (incluindo os áudios importados) no <em>seu próprio</em> Google Drive, numa área privada acessível só pelo app. É o Google quem gerencia, não o Bomp. Três coisas que convém saber: (1) está sujeito à <strong>cota do Auto Backup</strong> que o Google define (atualmente ~25 MB por app) — se sua coleção ultrapassar esse tamanho, o Google não respalda o que excede; (2) se você desinstalar o Bomp, o backup permanece acessível para uma eventual reinstalação, mas o Google o purga após um período prolongado de inatividade conforme sua política; (3) você pode desativar o Auto Backup a qualquer momento pelas configurações do celular. Para os áudios que você não quer perder, recomendamos exportar cópias próprias fora do Bomp.",
      "pp.s02.li3": "Quando você compartilha um áudio do Bomp para outro app, a transferência ocorre pelo sistema de \"Compartilhar\" do Android; o Bomp não tem visibilidade do que o app receptor faz.",
      "pp.s02.li4": "Quando você apaga um áudio pelo app, o arquivo é eliminado do armazenamento local. Quando você desinstala o app, todos os áudios são apagados junto com os dados do app; o backup no seu Google Drive (se você tem Auto Backup ativo) permanece acessível para uma eventual reinstalação, mas o Google o purga após um período prolongado de inatividade conforme sua política.",
      "pp.s03.intro": "O Bomp usa serviços do Google integrados ao ecossistema Android:",
      "pp.s03.li1": "<strong>Google Play Services</strong> — infraestrutura base do Google Play para distribuição e atualização do app.",
      "pp.s03.li2": "<strong>Diagnóstico de falhas</strong> — coleta <em>logs de falhas</em> de forma pseudônima quando o app trava, para que possamos corrigir o bug. Não coleta conteúdo do usuário (áudios, nomes de botões).",
      "pp.s03.li3": "<strong>Monitoramento de desempenho</strong> — coleta <em>diagnósticos pseudônimos agregados</em> (tempo de inicialização, uso de memória, latências) para detectar regressões release a release.",
      "pp.s03.li4": "<strong>Análise de uso</strong> — coleta <em>eventos agregados</em> (quantidade de Bomps, sessões, interações com a UI) sem vinculá-los a nenhum usuário identificado, para entender padrões de uso e priorizar melhorias.",
      "pp.s03.body": "O Bomp <strong>não usa</strong>: redes de publicidade, tracking pixels, cookies de tracking, fingerprinting de dispositivo. <strong>O Bomp não vende dados.</strong> Os dados pseudônimos que sim compartilhamos com o Google (logs de falhas, diagnósticos de desempenho, interações agregadas) são processados com finalidade de diagnóstico e analytics agregados; o detalhe completo e a criptografia em trânsito estão em <a href=\"data-safety.html\">Data Safety</a>.",
      "pp.s04.body1": "O Bomp foi pensado para uso geral e não se destina a menores de 13 anos. O app não exige criação de conta, não pede e-mail, e não coleta nome, idade ou dados que identifiquem diretamente o usuário. Se um menor usar o app, os únicos dados que saem do dispositivo são os pseudônimos descritos em <a href=\"data-safety.html\">Data Safety</a> (logs de falhas, diagnósticos de desempenho, interações agregadas), enviados ao Google sem se associarem a informações identificáveis.",
      "pp.s04.body2": "O Bomp se alinha com COPPA (EUA) e GDPR (UE) por design: não coletamos dados diretamente identificáveis. Se você é mãe/pai/responsável e quer que purguemos o identificador de instalação pseudônimo associado ao dispositivo de um menor, fale conosco pelo canal de <a href=\"#arco\">Direitos do usuário (ARCO)</a>.",
      "pp.s05.intro": "Sob a Lei 25.326 (Argentina) e o GDPR (UE), você tem direito a:",
      "pp.s05.li1": "<strong>Acessar</strong> os dados no seu celular: abra o app e você verá todos os áudios e botões que importou.",
      "pp.s05.li2": "<strong>Retificá-los</strong>: renomeie ou re-importe áudios pelo app.",
      "pp.s05.li3": "<strong>Cancelá-los</strong>: apague-os pelo app, ou desinstale o Bomp para apagar todos.",
      "pp.s05.li4": "<strong>Opor-se</strong> ao tratamento dos dados pseudônimos pelo Google.",
      "pp.s05.body2": "Para os dados pseudônimos que vivem em sistemas do Google (logs de falhas, diagnósticos de desempenho, interações agregadas), o Bomp não armazena seu nome, e-mail nem telefone — só um identificador opaco de instalação que o Google atribui ao instalar o app. Para exercer direitos sobre esses dados:",
      "pp.s05.li5": "<strong>Desinstale o app</strong>: corta a coleta e o Google purga eventualmente os dados associados ao identificador.",
      "pp.s05.li6": "<strong>Reset seu Advertising ID</strong> em Configurações do Android → Privacidade → Anúncios. Isso desvincula sessões futuras.",
      "pp.s05.li7": "<strong>Pedido manual</strong>: escreva-nos em <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> com seu identificador de instalação pseudônimo (te ajudamos a obtê-lo se precisar) e apagamos o registro associado.",
      "pp.s05.li8": "<strong>Via Google</strong>: <a href=\"https://safety.google/privacy/data/\">safety.google/privacy/data</a> permite pedir o apagamento ao Google diretamente, já que ele atua como sub-processador.",
      "pp.s05.body3": "Sob o GDPR Art. 11, como o Bomp não pode te identificar sem sua cooperação (não armazenamos informações que conectem você ao seu identificador de instalação), não estamos obrigados a manter um mecanismo de identificação adicional. Sua cooperação, enviando esse identificador, é o que viabiliza o apagamento pontual.",
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
      "ds.row1.col1": "<strong>Outros arquivos de áudio</strong><br><small>Os áudios que você importa de outros apps usando o sistema de \"Compartilhar\" do Android.</small>",
      "ds.row1.col2": "Funcionalidade do app: são os áudios que você decide Bompear. Ficam só no seu celular.",
      "ds.row1.col3": "Não.",
      "ds.row1.col4": "Não — o controle que você tem é decidir quais arquivos importar voluntariamente; uma vez importados, o armazenamento local do Bomp os mantém até que você os apague.",
      "ds.row2.col1": "<strong>Logs de falhas</strong><br><small>Stack traces e estado do dispositivo quando o app trava.</small>",
      "ds.row2.col2": "Diagnóstico de falhas. Permite corrigir bugs.",
      "ds.row2.col3": "Não (apenas coletado). Processado pelo Google como sub-processador.",
      "ds.row2.col4": "Não — os logs pseudônimos são enviados quando há um crash.",
      "ds.row3.col1": "<strong>Diagnósticos de desempenho</strong><br><small>Métricas pseudônimas de uso de memória, tempo de inicialização, latências.</small>",
      "ds.row3.col2": "Detecção de regressões de desempenho.",
      "ds.row3.col3": "Não (apenas coletado). Processado pelo Google como sub-processador.",
      "ds.row3.col4": "Não.",
      "ds.row4.col1": "<strong>Interações com o app</strong><br><small>Eventos agregados (quantidade de Bomps, sessões).</small>",
      "ds.row4.col2": "Entender padrões de uso para priorizar melhorias. Sem associar a nenhum usuário identificado.",
      "ds.row4.col3": "Não (apenas coletado). Processado pelo Google como sub-processador.",
      "ds.row4.col4": "Não.",
      "ds.title": "Segurança dos dados",
      "ds.s01.autoBackup": "<strong>Backup automático do Android.</strong> O Bomp tem habilitado o <a href=\"https://developer.android.com/identity/data/autobackup\">Auto Backup do Android</a>: se você tem ativo no celular, o Google faz backup dos dados do app (incluindo os áudios importados) no <em>seu próprio</em> Google Drive, numa área privada acessível só pelo app. Isso não aparece na tabela acima porque o formulário de Data Safety do Play declara apenas o que o app compartilha com o desenvolvedor ou com terceiros: este backup vai para sua conta do Google e é gerenciado pelo Google, não pelo Bomp. Está sujeito aos limites do Auto Backup (atualmente ~25 MB por app e purga após um período prolongado de inatividade). Você pode desativá-lo em Configurações do sistema → Sistema → Backup. O detalhe completo está na <a href=\"privacy-policy.html#datos-audio\">Política de privacidade</a>.",
      "ds.s02.body1": "<strong>O Bomp não exige criação de conta.</strong> Não usa e-mail, não usa senha, não usa OAuth, não usa SIM. O app abre e funciona.",
      "ds.s02.body2": "<strong>Exclusão de dados.</strong> Como não há conta, não há um fluxo \"apagar minha conta\". Os dados ficam no seu celular e você os apaga:",
      "ds.s02.li1": "Apagar áudios um a um pela lista do Bomp.",
      "ds.s02.li2": "Apagar todos os áudios: desinstale o app pelo sistema. O Android limpa o armazenamento atribuído ao Bomp.",
      "ds.s02.li3": "Para os dados pseudônimos nos sistemas do Google: ver <a href=\"privacy-policy.html#arco\">Privacy Policy → Direitos ARCO</a>.",
      "ds.s02.body3": "<strong>Sobre o fluxo de exclusão no Play.</strong> Na ficha do Bomp no Play declaramos que o app não provê um fluxo de auto-serviço de exclusão de dados. A razão: os dados no seu celular são apagados ao desinstalar, e os dados pseudônimos nos sistemas do Google não estão associados a uma conta que possa ser \"fechada\"; o procedimento manual está em <a href=\"privacy-policy.html#arco\">Privacy Policy → Direitos ARCO</a>.",
      "ds.s03.body": "Toda comunicação entre o Bomp e os servidores do Google usa HTTPS (TLS 1.2 ou superior). As chamadas ao Google Play Services seguem o padrão de criptografia do SDK oficial.",

      // ── Legal · terms of service ─────────────
      "head.title.terms": "Termos de Serviço — Bomp",
      "head.description.terms": "Termos de Serviço do Bomp — escopo, responsabilidade do usuário sobre o conteúdo gravado e compartilhado, licença de uso, lei aplicável e jurisdição.",
      "nav.termsOfService": "Termos de Serviço",
      "tos.title": "Termos de Serviço",
      "tos.meta.app": "App: <strong>Bomp</strong>",
      "tos.meta.version": "Versão: <strong>1.0</strong>",
      "tos.meta.effective": "Data de Vigência: <strong>2026-05-09</strong>",
      "tos.meta.operator": "Operador: <strong>Nahuel Barrios</strong> — desenvolvedor e administrador do Bomp. Contato: <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a>",
      "tos.intro": "Estes Termos de Serviço regem seu uso do aplicativo móvel <strong>Bomp</strong> e dos sites associados. O documento master é a versão em espanhol da Argentina (<code>es-AR</code>); em caso de divergência entre versões idiomáticas, prevalece essa versão.",
      "tos.toc.s01": "01 · Aceitação e escopo",
      "tos.toc.s02": "02 · Idade mínima e capacidade legal",
      "tos.toc.s03": "03 · Licença de uso",
      "tos.toc.s04": "04 · Responsabilidade do Bomper sobre o conteúdo",
      "tos.toc.s05": "05 · Usos proibidos",
      "tos.toc.s06": "06 · Propriedade intelectual",
      "tos.toc.s07": "07 · Sem garantias (\"no estado em que se encontra\")",
      "tos.toc.s08": "08 · Limitação de responsabilidade",
      "tos.toc.s09": "09 · Suspensão e rescisão",
      "tos.toc.s10": "10 · Alterações nos Termos",
      "tos.toc.s11": "11 · Lei aplicável e jurisdição",
      "tos.toc.s12": "12 · Notificações legais e contato",
      "tos.toc.s13": "13 · Divisibilidade e acordo integral",
      "tos.s01.body": "Estes Termos de Serviço (doravante, <strong>\"os Termos\"</strong>) regem seu uso do aplicativo móvel <strong>Bomp</strong> e dos sites associados. O aplicativo é desenvolvido e operado por <strong>Nahuel Barrios</strong> (doravante, <strong>\"o operador\"</strong> ou, indistintamente, <strong>\"Bomp\"</strong>). Ao instalar, abrir ou usar o aplicativo de qualquer forma, você manifesta seu consentimento expresso a estes Termos. Se você não concorda, não instale nem use Bomp.",
      "tos.s02.body": "Ao usar Bomp, você declara ter a idade legal exigida em sua jurisdição para celebrar contratos. Se você for menor de idade, deverá obter o consentimento expresso de seu representante legal antes de instalar ou usar o aplicativo. Bomp poderá suspender o acesso se receber constatação de uso por menores sem a autorização requerida.",
      "tos.s03.body": "Bomp concede a você uma licença pessoal, não exclusiva, intransferível e revogável para instalar e usar o aplicativo em dispositivos de sua titularidade ou uso autorizado, exclusivamente para fins não comerciais. Esta licença não transfere nenhum direito de propriedade sobre o aplicativo nem seus componentes. O código-fonte rege-se por suas próprias licenças open source (BSL + AGPLv3), descritas no repositório público.",
      "tos.s04.intro": "Como Bomper, você é o único responsável pelos áudios que grava, edita, armazena e compartilha usando Bomp (doravante, <strong>\"o Conteúdo\"</strong>). Ao usar o aplicativo, você declara e garante que:",
      "tos.s04.liA": "<strong>(a)</strong> é titular de todos os direitos sobre o Conteúdo ou possui as licenças e permissões necessárias para gravá-lo, armazená-lo e distribuí-lo;",
      "tos.s04.liB": "<strong>(b)</strong> quando o Conteúdo incluir a voz, imagem ou dados pessoais de terceiros, você obteve o consentimento expresso e informado dessas pessoas em conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018 — LGPD), o Marco Civil da Internet (Lei nº 12.965/2014), o Código Civil (em particular, no que se refere ao direito à imagem e à voz) e demais normas aplicáveis;",
      "tos.s04.liC": "<strong>(c)</strong> o Conteúdo não infringe direitos de propriedade intelectual, direitos da personalidade, normas contra a difamação, nem leis penais aplicáveis;",
      "tos.s04.liD": "<strong>(d)</strong> você não usa Bomp para gravar conversas ou pessoas em contextos em que a gravação seja proibida pela lei aplicável.",
      "tos.s04.indem": "Você manterá Bomp, seus colaboradores e parceiros indenes e a salvo de qualquer reclamação, demanda, sanção, custo, honorário ou despesa decorrente de Conteúdo gerado, armazenado, editado ou compartilhado por você por meio de Bomp.",
      "tos.s05.intro": "É proibido usar Bomp para:",
      "tos.s05.liA": "<strong>(a)</strong> armazenar ou distribuir Conteúdo ilegal ou que infrinja direitos de terceiros;",
      "tos.s05.liB": "<strong>(b)</strong> assediar, ameaçar, perseguir ou intimidar outras pessoas;",
      "tos.s05.liC": "<strong>(c)</strong> se passar pela identidade de outra pessoa ou enganar sobre a origem de um áudio;",
      "tos.s05.liD": "<strong>(d)</strong> realizar engenharia reversa, descompilação ou desmontagem do aplicativo além do permitido pelas licenças open source aplicáveis;",
      "tos.s05.liE": "<strong>(e)</strong> usar o aplicativo para difusão massiva automatizada ou spam;",
      "tos.s05.liF": "<strong>(f)</strong> burlar as medidas técnicas de proteção do aplicativo ou das plataformas às quais ele se conecta.",
      "tos.s05.body": "Bomp poderá suspender ou revogar o acesso ao aplicativo diante de qualquer violação desta cláusula.",
      "tos.s06.body1": "Bomp, o logotipo, o ícone, o <em>brand mark</em>, o <em>wordmark</em>, os textos, gráficos e demais elementos visuais do aplicativo e do site são de propriedade de Nahuel Barrios ou de seus licenciantes. O código-fonte rege-se pelas licenças BSL + AGPLv3 descritas no repositório público.",
      "tos.s06.body2": "Você retém a titularidade sobre os áudios que grava e armazena em Bomp. Bomp <strong>não reivindica</strong> nenhum direito sobre seu Conteúdo. O aplicativo processa esses áudios localmente em seu dispositivo conforme descrito na <a href=\"privacy-policy.html\">Política de Privacidade</a>.",
      "tos.s07.body": "O aplicativo é fornecido <em>\"no estado em que se encontra\"</em> e <em>\"conforme disponibilidade\"</em>, sem garantias expressas ou implícitas sobre seu funcionamento, continuidade, ausência de erros ou defeitos, adequação a um propósito específico, ou resultados específicos decorrentes de seu uso. Seus áudios vivem no seu celular: Bomp não opera servidores nem nuvem própria para guardá-los. Se você tem o Auto Backup do Android ativo no seu sistema, o Google copia os dados do app para o <em>seu próprio</em> Google Drive — esse mecanismo é gerenciado pelo Google, não por Bomp, e está sujeito aos seus limites técnicos (atualmente ~25 MB por app e purga do backup após um período prolongado de inatividade). Para os áudios que você não quer perder, mantenha cópias próprias fora do app. O detalhe completo está na <a href=\"privacy-policy.html#datos-audio\">Política de privacidade</a>.",
      "tos.s08.body": "Até onde a lei aplicável permitir, Bomp não será responsável por danos indiretos, incidentais, consequenciais, punitivos ou especiais decorrentes do uso ou da impossibilidade de uso do aplicativo, incluindo — sem limitação — perda de áudios, perda de oportunidades, perda de dados ou danos reputacionais. Esta limitação não se aplica aos danos que normas de ordem pública de sua jurisdição considerem não excluíveis, incluindo as disposições do Código de Defesa do Consumidor (Lei nº 8.078/1990) que sejam aplicáveis a você, nem a hipóteses de dolo ou culpa grave.",
      "tos.s09.body": "Bomp poderá suspender ou rescindir seu acesso ao aplicativo, sem aviso prévio, diante de violação material destes Termos. Bomp também poderá descontinuar o aplicativo, no todo ou em parte, mediante aviso razoável por meio do site ou dos canais da Google Play Store. A rescisão não afeta as obrigações do Bomper exigíveis antes da rescisão; as cláusulas 4, 6 e 8 sobrevivem à rescisão.",
      "tos.s10.body": "Bomp poderá modificar estes Termos a qualquer momento. Mudanças materiais serão anunciadas por meio de um banner visível no site por pelo menos 30 dias, juntamente com a atualização da Data de Vigência no cabeçalho do documento. Continuar usando o aplicativo após a Data de Vigência implica aceitação dos Termos modificados. Se você não concorda com as mudanças, deve cessar o uso de Bomp.",
      "tos.s11.body": "Estes Termos regem-se pelas leis da República Argentina. Qualquer controvérsia será submetida aos tribunais ordinários com competência na Cidade Autônoma de Buenos Aires, salvo quando as normas de ordem pública de sua jurisdição — em particular, as normas de proteção ao consumidor aplicáveis no Brasil, incluindo o foro do domicílio do consumidor previsto no Código de Defesa do Consumidor — permitirem ajuizar a ação em seu próprio domicílio, em cujo caso esse direito prevalece.",
      "tos.s12.body": "As notificações legais formais devem ser enviadas por correio eletrônico para <a href=\"mailto:barrios.nahuel+bomp@gmail.com\"><code>barrios.nahuel+bomp@gmail.com</code></a> (o mesmo endereço usado para solicitações dos <a href=\"privacy-policy.html#arco\">direitos do titular</a>). Bomp responderá às comunicações formais dentro dos prazos legais aplicáveis.",
      "tos.s13.body": "Se um tribunal competente declarar nula, inválida ou inexequível qualquer cláusula destes Termos, as demais cláusulas permanecerão em pleno vigor. Estes Termos, em conjunto com a <a href=\"privacy-policy.html\">Política de Privacidade</a> e a página de <a href=\"data-safety.html\">Segurança dos Dados</a>, constituem o acordo integral entre o Bomper e Bomp em relação ao aplicativo e substituem qualquer acordo anterior.",
      "tos.closing": "<em>Disposição final.</em> Em caso de divergência entre as versões idiomáticas destes Termos, prevalecerá a versão em espanhol da Argentina (<code>es-AR</code>) como master legal."
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
