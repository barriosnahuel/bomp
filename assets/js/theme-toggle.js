// Theme toggle: invert data-theme on <html>, persist in localStorage["bomp-theme"].
// Note: theme-init runs inline in <head> to avoid FOUC; this file only wires the button.
(function () {
  var btn = document.querySelector(".l-theme-toggle");
  if (!btn) return;

  function currentTheme() {
    var attr = document.documentElement.getAttribute("data-theme");
    if (attr === "light" || attr === "dark") return attr;
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function syncAria() {
    btn.setAttribute("aria-pressed", currentTheme() === "dark" ? "true" : "false");
  }

  syncAria();

  btn.addEventListener("click", function () {
    var next = currentTheme() === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try { localStorage.setItem("bomp-theme", next); } catch (e) { /* private mode */ }
    syncAria();
  });
})();
