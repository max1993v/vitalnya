/* Лишає на map.ukrainealarm.com тільки мапу: ховає шапку, меню, кнопки, банери й вікна. */
(function () {
  if (window.__vitalnyaFix) return;
  window.__vitalnyaFix = true;

  function area(el) {
    var r = el.getBoundingClientRect();
    return r.width * r.height;
  }

  // шукаємо найбільший елемент мапи: svg з багатьма контурами, canvas або контейнер картографічної бібліотеки
  function findMap() {
    var best = null, bestA = 0;
    var c = document.querySelectorAll("svg, canvas, .leaflet-container, .maplibregl-map, .mapboxgl-map");
    for (var i = 0; i < c.length; i++) {
      var el = c[i];
      if (el.tagName.toLowerCase() === "svg" && el.querySelectorAll("path").length < 20) continue;
      var a = area(el);
      if (a > bestA) { bestA = a; best = el; }
    }
    if (!best || bestA < 40000) return null;
    // піднімаємось до контейнера такого ж розміру — щоб зберегти позначки цілей поверх мапи
    var cur = best;
    while (cur.parentElement && cur.parentElement !== document.body && area(cur.parentElement) <= bestA * 1.2) cur = cur.parentElement;
    return cur;
  }

  function isolate(map) {
    // ховаємо всіх «сусідів» на шляху від мапи до body
    var cur = map;
    while (cur && cur !== document.body && cur.parentElement) {
      var sib = cur.parentElement.children;
      for (var i = 0; i < sib.length; i++) {
        var s = sib[i];
        if (s !== cur && !/^(script|style|link)$/i.test(s.tagName)) s.style.setProperty("display", "none", "important");
      }
      cur = cur.parentElement;
    }
    // всередині контейнера мапи ховаємо кнопки, легенди й банери (усе, що не є частиною самої мапи)
    var inner = map.querySelectorAll("button, [role=button], [class*='control'], [class*='Control'], [class*='banner'], [class*='Banner'], [class*='legend'], [class*='Legend'], [class*='attribution']");
    for (var j = 0; j < inner.length; j++) inner[j].style.setProperty("display", "none", "important");
    // розтягуємо мапу на все вікно
    var st = map.style;
    st.setProperty("position", "fixed", "important");
    st.setProperty("left", "0", "important");
    st.setProperty("top", "0", "important");
    st.setProperty("width", "100vw", "important");
    st.setProperty("height", "100vh", "important");
    st.setProperty("margin", "0", "important");
    st.setProperty("transform", "none", "important");
    st.setProperty("z-index", "2147483000", "important");
    var svg = map.tagName.toLowerCase() === "svg" ? map : map.querySelector(":scope > svg");
    if (svg) { svg.style.setProperty("width", "100%", "important"); svg.style.setProperty("height", "100%", "important"); }
    document.documentElement.style.background = "#1b1d22";
    document.body.style.background = "#1b1d22";
    document.body.style.overflow = "hidden";
    try { window.dispatchEvent(new Event("resize")); } catch (e) {}
  }

  var n = 0;
  var timer = setInterval(function () {
    var map = findMap();
    if (map) isolate(map);
    if (++n > 60) clearInterval(timer);   // ~45 с: сайт може домальовувати шари й вікна
  }, 750);
})();
