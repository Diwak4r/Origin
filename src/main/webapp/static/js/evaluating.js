// The evaluation screen: shows the stages Origin actually went through, then opens the result.
// The result is already saved when this page loads. The page holds for about two and a half seconds
// so every stage can be read, and there is always a plain link to skip ahead.
(function () {
  "use strict";

  var root = document.querySelector("[data-next]");
  if (!root) return;
  var next = root.dataset.next;
  var items = Array.prototype.slice.call(document.querySelectorAll("#stages .stage"));
  var bar = document.getElementById("eval-bar");
  var reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function go() { window.location.replace(next); }
  function progress(p) { bar.style.setProperty("--w", p + "%"); }

  if (reduce) {
    items.forEach(function (li) { li.className = "stage done"; });
    progress(100);
    go();
    return;
  }

  var STEP_MS = 430;      // five stages take about 2.2 seconds
  var i = 0;
  function tick() {
    if (i > 0) items[i - 1].className = "stage done";
    if (i < items.length) {
      items[i].className = "stage run";
      progress(Math.round(((i + 1) / items.length) * 100));
      i++;
      setTimeout(tick, STEP_MS);
    } else {
      setTimeout(go, 350);
    }
  }
  tick();
})();
