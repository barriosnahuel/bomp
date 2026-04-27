// Sticker hero — drives the radial progress ring from a real <audio> element.
// Behavior:
//   tap -> audio.play(); ring fills via timeupdate; data-state="playing" toggles CSS.
//   tap during play -> pauses + resets.
//   audio.play() rejection (autoplay block, missing file) -> caption shows fallback.
//   ended -> reset to idle.
//
// CSS keeps the breathe + halo animations; reduced-motion is handled in base.css.
(function () {
  var btn = document.querySelector(".sticker-hero__btn");
  var audio = document.getElementById("bomp-hero-audio");
  var ring = document.querySelector(".sticker-hero__ring circle");
  var caption = document.querySelector(".sticker-hero__caption");
  var labelEl = document.querySelector(".sticker-hero__label");
  var iconEl = document.querySelector(".sticker-hero__icon");

  if (!btn || !audio || !ring) return;

  // Match the ring geometry baked into the SVG (r=49).
  var C = 2 * Math.PI * 49;
  ring.setAttribute("stroke-dasharray", String(C));
  ring.setAttribute("stroke-dashoffset", String(C));

  function setProgress(p) {
    // p in [0, 1]; ring fills as p grows.
    var offset = C * (1 - Math.max(0, Math.min(1, p)));
    ring.setAttribute("stroke-dashoffset", String(offset));
  }

  function tr(key, fallback) {
    if (window.BompI18n && typeof window.BompI18n.t === "function") {
      var v = window.BompI18n.t(key);
      if (v && v !== key) return v;
    }
    return fallback;
  }

  function setIdle() {
    btn.setAttribute("data-state", "idle");
    btn.setAttribute("aria-pressed", "false");
    setProgress(0);
    if (labelEl) labelEl.textContent = tr("sticker.label.idle", "tocá");
    if (iconEl) iconEl.dataset.icon = "play";
    swapIcon("play");
  }

  function setPlaying() {
    btn.setAttribute("data-state", "playing");
    btn.setAttribute("aria-pressed", "true");
    if (labelEl) labelEl.textContent = tr("sticker.label.playing", "sonando");
    swapIcon("mic");
    if (caption) caption.textContent = tr("sticker.caption.playing", "Bompeando…");
  }

  function swapIcon(which) {
    if (!iconEl) return;
    var play = iconEl.querySelector("[data-icon-play]");
    var mic = iconEl.querySelector("[data-icon-mic]");
    if (play) play.style.display = which === "play" ? "" : "none";
    if (mic) mic.style.display = which === "mic" ? "" : "none";
  }

  setIdle();

  audio.addEventListener("timeupdate", function () {
    if (!audio.duration || isNaN(audio.duration)) return;
    setProgress(audio.currentTime / audio.duration);
  });

  audio.addEventListener("ended", function () {
    setIdle();
    if (caption) caption.textContent = "";
  });

  audio.addEventListener("pause", function () {
    if (!audio.ended) {
      // Manual pause; UI already updated by the click handler.
    }
  });

  btn.addEventListener("click", function () {
    if (!audio.paused) {
      audio.pause();
      audio.currentTime = 0;
      setIdle();
      if (caption) caption.textContent = "";
      return;
    }
    var promise = audio.play();
    if (promise && typeof promise.then === "function") {
      promise.then(function () {
        setPlaying();
      }).catch(function () {
        setIdle();
        if (caption) caption.textContent = tr(
          "sticker.caption.fallback",
          "Tu navegador está en silencio. Tocá para activar la voz de Bomp."
        );
      });
    } else {
      setPlaying();
    }
  });
})();
