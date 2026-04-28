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
      "head.title.index": "Bomp — La voz de los tuyos, a un toque",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Guardá los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompealos cuando los necesites. Como stickers, pero con la voz de tu gente.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Espejo legible del CSV de Data Safety de Google Play: qué datos recolecta Bomp, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">A un toque</span>.",
      "hero.sub": "Guardá los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompealos cuando los necesites. Como stickers, pero con la voz de tu gente.",
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
      "shot.caption.identity": "↳ La voz de los tuyos, siempre a un toque.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día.",
      "shot.caption.reception": "↳ Y del otro lado, alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Vos, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "El acto de mandar el audio que justo necesitaba ese momento. Conjugación: bompo, bompás, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te encantan y no querés perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo querés, guardalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo querés mejorar, romper, traducir o forkear: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "Si te alegró el día, invitá un café.",
      "section.donate.body": "Bomp es gratis y va a seguir siéndolo. Pero los servidores —y los cafés— se pagan. Cafecito (Argentina) o Ko-fi (internacional).",
      "donate.cafecito.alt": "Invitame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para Bompear.",
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
      "legal.translation.notice": "Este documento aún solo está disponible en español. Estamos trabajando en las traducciones."
    },

    // ── es-419 — neutral LATAM tuteo (no Cafecito; solo Ko-fi) ─
    "es-419": {
      "html.lang": "es-419",
      "head.title.index": "Bomp — La voz de los tuyos, a un toque",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompéalos cuando los necesites. Como stickers, pero con la voz de tu gente.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Espejo legible del CSV de Data Safety de Google Play: qué datos recolecta Bomp, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">A un toque</span>.",
      "hero.sub": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompéalos cuando los necesites. Como stickers, pero con la voz de tu gente.",
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
      "shot.caption.identity": "↳ La voz de los tuyos, siempre a un toque.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día.",
      "shot.caption.reception": "↳ Y del otro lado, alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Tú, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "El acto de mandar el audio que justo necesitaba ese momento. Conjugación: bompo, bompeas, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te encantan y no quieres perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo quieres, guárdalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo quieres mejorar, romper, traducir o hacerle un fork: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "Si te alegró el día, invita un café.",
      "section.donate.body": "Bomp es gratis y va a seguir siéndolo. Pero los servidores —y los cafés— se pagan. Si querés, invítanos uno por Ko-fi.",
      "donate.cafecito.alt": "Invítame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para Bompear.",
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
      "legal.translation.notice": "Este documento aún solo está disponible en español rioplatense. Estamos trabajando en las traducciones."
    },

    // ── es-ES — Spain Spanish (Cafecito hidden; only Ko-fi) ───
    "es-ES": {
      "html.lang": "es-ES",
      "head.title.index": "Bomp — La voz de los tuyos, a un toque",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompéalos cuando los necesites. Como stickers, pero con la voz de tu gente.",
      "head.description.privacy": "Política de privacidad de Bomp — qué datos se manejan, cómo se almacenan, qué hacen los terceros, derechos del usuario.",
      "head.description.dataSafety": "Espejo legible del CSV de Data Safety de Google Play: qué datos recolecta Bomp, por qué, si se comparten y si son opcionales.",
      "skip.link": "Saltar al contenido",
      "nav.howItWorks": "Cómo funciona",
      "nav.theApp": "La app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Cambiar tema",

      "hero.eyebrow": "Beta · Android · gratis",
      "hero.title.html": "La voz de los tuyos.<br><span class=\"acid\">A un toque</span>.",
      "hero.sub": "Guarda los audios que te llegan por WhatsApp, Telegram o WeChat, y Bompéalos cuando los necesites. Como stickers, pero con la voz de tu gente.",
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
      "shot.caption.identity": "↳ La voz de los tuyos, siempre a un toque.",
      "shot.caption.gift": "↳ Una broma de dos segundos puede salvar un día.",
      "shot.caption.reception": "↳ Y del otro lado, alguien se ríe en voz alta.",

      "section.glossary.num": "03 · Glosario",
      "section.glossary.title": "Mini diccionario del Bomper.",
      "gloss.bomper.pos": "/bom·per/ · sustantivo",
      "gloss.bomper.def": "Tú, ahora. La persona que guarda audios como otros guardan abrazos.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "El acto de mandar el audio que justo necesitaba ese momento. Conjugación: bompo, bompeas, bompea, bompeamos. (Sí, lo conjugamos.)",
      "gloss.bompeable.pos": "/bom·pe·á·ble/ · adjetivo",
      "gloss.bompeable.def": "Esos audios que te molan y no quieres perder: cortos o largos, graciosos o sentimentales. Útiles. Si lo quieres, guárdalo.",

      "manifesto.html": "<span>Un audio de los tuyos </span><em>no es un mensaje</em>, <strong>es un abrazo</strong> que se escucha.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Hecho a la luz, con licencia AGPL-3.0.",
      "section.openSource.body": "El código está completo en GitHub. Si lo quieres mejorar, romper, traducir o hacerle un fork: la puerta está abierta.",
      "cta.contributeGitHub": "Contribuir en GitHub",

      "section.donate.num": "05 · Donar",
      "section.donate.title": "Si te alegró el día, invita a un café.",
      "section.donate.body": "Bomp es gratis y va a seguir siéndolo. Pero los servidores —y los cafés— se pagan. Si te apetece, invítanos uno por Ko-fi.",
      "donate.cafecito.alt": "Invítame un café en cafecito.app",

      "footer.brand": "Las voces que te importan, listas para Bompear.",
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
      "legal.translation.notice": "Este documento solo está disponible en español rioplatense por ahora. Estamos trabajando en las traducciones."
    },

    // ── en — English ──────────────────────────
    "en": {
      "html.lang": "en",
      "head.title.index": "Bomp — The voices that matter, at a tap",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Save the audios you get on WhatsApp, Telegram or WeChat, and Bomp them when you need to. Like stickers, but with the voices of your people.",
      "head.description.privacy": "Bomp's privacy policy — what data is handled, how it is stored, what third parties do, user rights.",
      "head.description.dataSafety": "Readable mirror of Bomp's Google Play Data Safety CSV: what data is collected, why, whether shared, and whether optional.",
      "skip.link": "Skip to content",
      "nav.howItWorks": "How it works",
      "nav.theApp": "The app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Toggle theme",

      "hero.eyebrow": "Beta · Android · free",
      "hero.title.html": "The voices that matter.<br><span class=\"acid\">At a tap</span>.",
      "hero.sub": "Save the audios you get on WhatsApp, Telegram or WeChat, and Bomp them when you need to. Like stickers, but with the voices of your people.",
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
      "shot.caption.identity": "↳ The voices that matter, one tap away.",
      "shot.caption.gift": "↳ A two-second joke can save someone's day.",
      "shot.caption.reception": "↳ And on the other side, someone laughs out loud.",

      "section.glossary.num": "03 · Glossary",
      "section.glossary.title": "Mini Bomper dictionary.",
      "gloss.bomper.pos": "/bom·per/ · noun",
      "gloss.bomper.def": "You, right now. The person who saves audios the way others save hugs.",
      "gloss.bompear.pos": "/bom·peh·ar/ · verb",
      "gloss.bompear.def": "The act of sending the audio the moment was waiting for. Conjugation: I Bomp, you Bomp, they Bomp. (Yes, we conjugate it.)",
      "gloss.bompeable.pos": "/bom·peh·ah·bleh/ · adjective",
      "gloss.bompeable.def": "Those audios you love and don't want to lose: short or long, funny or sentimental. Useful. If you want it, save it.",

      "manifesto.html": "<span>An audio from one of yours </span><em>isn't a message</em>, <strong>it's a hug</strong> you can hear.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Made in the open, AGPL-3.0 licensed.",
      "section.openSource.body": "The full source is on GitHub. If you want to improve it, break it, translate it or fork it: the door is open.",
      "cta.contributeGitHub": "Contribute on GitHub",

      "section.donate.num": "05 · Donate",
      "section.donate.title": "If it brightened your day, buy us a coffee.",
      "section.donate.body": "Bomp is free and will stay free. But servers —and coffees— cost money. If you want, buy us one on Ko-fi.",
      "donate.cafecito.alt": "Buy me a coffee at cafecito.app",

      "footer.brand": "The voices you love, ready to Bomp.",
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
      "legal.translation.notice": "This document is only available in Spanish (Río de la Plata) for now. We're working on translations."
    },

    // ── pt-BR — Brazilian Portuguese ──────────
    "pt-BR": {
      "html.lang": "pt-BR",
      "head.title.index": "Bomp — A voz dos seus, num toque",
      "head.title.privacy": "Privacy Policy — Bomp",
      "head.title.dataSafety": "Data Safety — Bomp",
      "head.title.404": "404 — Bomp",
      "head.description.index": "Salve os áudios que chegam pelo WhatsApp, Telegram ou WeChat, e Bompe quando precisar. Como stickers, mas com a voz da sua gente.",
      "head.description.privacy": "Política de privacidade do Bomp — quais dados são tratados, como são armazenados, o que terceiros fazem, direitos do usuário.",
      "head.description.dataSafety": "Espelho legível do CSV de Data Safety do Google Play: quais dados o Bomp coleta, por quê, se compartilha e se é opcional.",
      "skip.link": "Pular para o conteúdo",
      "nav.howItWorks": "Como funciona",
      "nav.theApp": "O app",
      "nav.openSource": "Open source",
      "nav.privacyPolicy": "Privacy Policy",
      "nav.dataSafety": "Data Safety",
      "theme.toggle.aria": "Alternar tema",

      "hero.eyebrow": "Beta · Android · grátis",
      "hero.title.html": "A voz dos seus.<br><span class=\"acid\">Num toque</span>.",
      "hero.sub": "Salve os áudios que chegam pelo WhatsApp, Telegram ou WeChat, e Bompe quando precisar. Como stickers, mas com a voz da sua gente.",
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
      "shot.caption.identity": "↳ A voz da sua gente, a um toque.",
      "shot.caption.gift": "↳ Uma piada de dois segundos pode salvar um dia.",
      "shot.caption.reception": "↳ E do outro lado, alguém ri alto.",

      "section.glossary.num": "03 · Glossário",
      "section.glossary.title": "Mini dicionário do Bomper.",
      "gloss.bomper.pos": "/bom·per/ · substantivo",
      "gloss.bomper.def": "Você, agora. A pessoa que guarda áudios como outros guardam abraços.",
      "gloss.bompear.pos": "/bom·pe·ár/ · verbo",
      "gloss.bompear.def": "O ato de mandar o áudio que aquele momento estava esperando. Conjugação: eu bompo, você bompa, ele bompa, nós bompamos. (Sim, a gente conjuga.)",
      "gloss.bompeable.pos": "/bom·pe·á·vel/ · adjetivo",
      "gloss.bompeable.def": "Aqueles áudios que você ama e não quer perder: curtos ou longos, engraçados ou sentimentais. Úteis. Se quiser, salve.",

      "manifesto.html": "<span>Um áudio dos seus </span><em>não é uma mensagem</em>, <strong>é um abraço</strong> que se escuta.",
      "manifesto.sig": "— Bomp · /bomp/",

      "section.openSource.num": "04 · Open source",
      "section.openSource.title": "Feito à luz do dia, sob licença AGPL-3.0.",
      "section.openSource.body": "O código completo está no GitHub. Se quiser melhorar, quebrar, traduzir ou fazer fork: a porta está aberta.",
      "cta.contributeGitHub": "Contribuir no GitHub",

      "section.donate.num": "05 · Doar",
      "section.donate.title": "Se alegrou seu dia, paga um café.",
      "section.donate.body": "Bomp é grátis e vai continuar sendo. Mas servidores —e cafés— se pagam. Se quiser, paga um pelo Ko-fi.",
      "donate.cafecito.alt": "Me pague um café em cafecito.app",

      "footer.brand": "As vozes que importam, prontas pra Bompear.",
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
      "legal.translation.notice": "Este documento ainda só está disponível em espanhol rio-platense. Estamos trabalhando nas traduções."
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
