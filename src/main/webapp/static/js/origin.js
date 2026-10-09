// Origin page behaviour. Every page works without this file; it only adds the finishing touches.
(function () {
  "use strict";

  function clamp(n) { return Math.max(0, Math.min(100, Number(n) || 0)); }

  // Bars and the scale pin read their value from data attributes (the CSP forbids inline styles).
  function fillBars() {
    document.querySelectorAll("[data-w]").forEach(function (el) {
      el.style.setProperty("--w", clamp(el.dataset.w) + "%");
    });
    document.querySelectorAll("[data-at]").forEach(function (el) {
      el.style.setProperty("--at", clamp(el.dataset.at) + "%");
    });
  }

  // Tag suggestions under the tag field: click to add or remove.
  function tagPicker() {
    var input = document.getElementById("tags");
    if (!input) return;
    var picks = document.querySelectorAll(".tag-pick");

    function current() {
      return input.value.split(/[,;]/).map(function (t) { return t.trim().toLowerCase(); }).filter(Boolean);
    }
    function sync() {
      var have = current();
      picks.forEach(function (b) { b.classList.toggle("picked", have.indexOf(b.dataset.tag) >= 0); });
    }
    picks.forEach(function (b) {
      b.addEventListener("click", function () {
        var have = current();
        var i = have.indexOf(b.dataset.tag);
        if (i >= 0) have.splice(i, 1); else have.push(b.dataset.tag);
        input.value = have.join(", ");
        sync();
      });
    });
    input.addEventListener("input", sync);
    sync();
  }

  // Character counters on long fields.
  function counters() {
    document.querySelectorAll("textarea[data-max]").forEach(function (ta) {
      var out = document.createElement("div");
      out.className = "counter";
      ta.after(out);
      function update() { out.textContent = ta.value.length + " / " + ta.dataset.max; }
      ta.addEventListener("input", update);
      update();
    });
  }

  // Result page: while the background comparison runs, ask for news every few seconds, then reload once.
  function poll() {
    var el = document.querySelector("[data-poll]");
    if (!el) return;
    var tries = 0;
    function tick() {
      tries++;
      fetch(el.dataset.poll, { credentials: "same-origin", headers: { "Accept": "application/json" } })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (j) {
          if (j && j.status && j.status !== "PENDING") { window.location.reload(); return; }
          if (tries < 24) setTimeout(tick, 2500); else el.textContent = "The deeper comparison is taking longer than usual. Refresh later.";
        })
        .catch(function () { if (tries < 24) setTimeout(tick, 4000); });
    }
    setTimeout(tick, 2000);
  }

  document.addEventListener("DOMContentLoaded", function () {
    requestAnimationFrame(fillBars);
    tagPicker();
    counters();
    poll();
  });
})();
