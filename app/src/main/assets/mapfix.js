/* Прибирає спливаючі вікна й промо-банери з map.ukrainealarm.com, щоб було видно лише мапу. */
(function () {
  if (window.__vitalnyaFix) return;
  window.__vitalnyaFix = true;

  var TEXTS = ["Оновлена карта", "Віджет мапи", "ЗАВАНТАЖИТИ ВІДЖЕТ", "Завантажити віджет", "cookie", "Cookie"];

  function isMap(el) {
    return !!el.querySelector("canvas, .leaflet-container, .mapboxgl-map, .maplibregl-map");
  }
  function hide(el) {
    if (el && el !== document.body && el !== document.documentElement && !isMap(el)) {
      el.style.setProperty("display", "none", "important");
    }
  }
  function posOf(el) {
    try { return getComputedStyle(el).position; } catch (e) { return ""; }
  }

  function run() {
    // 1) вікна з відомим текстом — ховаємо найвищий fixed/absolute контейнер, що не містить мапу
    var nodes = document.querySelectorAll("div, section, aside, dialog");
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.children.length > 6) continue;
      var t = el.textContent || "";
      var hit = false;
      for (var k = 0; k < TEXTS.length; k++) { if (t.indexOf(TEXTS[k]) >= 0) { hit = true; break; } }
      if (!hit) continue;
      var target = null, cur = el;
      while (cur && cur !== document.body) {
        var p = posOf(cur);
        if ((p === "fixed" || p === "absolute" || cur.getAttribute("role") === "dialog" || cur.tagName === "DIALOG") && !isMap(cur)) target = cur;
        cur = cur.parentElement;
      }
      hide(target);
    }
    // 2) напівпрозорі «затемнення» на весь екран без мапи всередині
    var all = document.body ? document.body.getElementsByTagName("*") : [];
    var W = window.innerWidth, H = window.innerHeight;
    for (var j = 0; j < all.length; j++) {
      var e = all[j];
      if (posOf(e) !== "fixed") continue;
      var r = e.getBoundingClientRect();
      if (r.width >= W * 0.9 && r.height >= H * 0.9 && !isMap(e) && (e.textContent || "").trim().length < 400) hide(e);
    }
    // 3) кнопки «закрити» у діалогах
    var btns = document.querySelectorAll("[role=dialog] button, dialog button, [aria-modal=true] button");
    for (var b = 0; b < btns.length; b++) {
      var lab = (btns[b].getAttribute("aria-label") || "") + (btns[b].textContent || "");
      if (/close|закрити|×|✕/i.test(lab)) { try { btns[b].click(); } catch (x) {} }
    }
    try { document.body.style.overflow = "hidden"; } catch (x) {}
  }

  run();
  var n = 0;
  var timer = setInterval(function () { run(); if (++n > 40) clearInterval(timer); }, 750);
})();
