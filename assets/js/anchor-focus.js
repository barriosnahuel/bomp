// Anchor focus management: cuando se activa un <a href="#..."> (skip link, nav,
// TOC, "volver arriba"), mover el foco del teclado al destino, no solo la viewport.
// Sin esto, Tab después del click vuelve al header en lugar de seguir dentro de
// la sección destino — rompe WCAG 2.4.1 (Bypass Blocks) y 2.4.3 (Focus Order).
(function () {
  document.addEventListener("click", function (e) {
    var link = e.target.closest('a[href^="#"]');
    if (!link) return;
    var href = link.getAttribute("href");
    if (!href || href === "#" || href.length < 2) return;
    var target;
    try { target = document.querySelector(href); } catch (err) { return; }
    if (!target) return;
    if (!target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1");
    target.focus({ preventScroll: false });
  });
})();
