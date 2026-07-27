/* Atelier pratique BBC SMS — navigation du support projeté. */
(function () {
  var slides = [].slice.call(document.querySelectorAll('.slide'));
  var pos = document.getElementById('pos');
  var bar = document.getElementById('bar');
  var overlay = document.getElementById('overlay');
  var index = 0;

  /** La diapositive courante vit dans l'URL : rafraîchir ne perd pas sa place. */
  function show(i, push) {
    index = Math.max(0, Math.min(slides.length - 1, i));
    slides.forEach(function (s, n) { s.classList.toggle('on', n === index); });
    pos.textContent = String(index + 1);
    bar.style.width = ((index + 1) / slides.length * 100) + '%';
    if (push !== false) history.replaceState(null, '', '#' + (index + 1));
    overlay.classList.remove('on');
  }

  function fromHash() {
    var n = parseInt((location.hash || '').replace('#', ''), 10);
    return isNaN(n) ? 0 : n - 1;
  }

  document.getElementById('next').addEventListener('click', function () { show(index + 1); });
  document.getElementById('prev').addEventListener('click', function () { show(index - 1); });
  document.getElementById('menu').addEventListener('click', function () { overlay.classList.toggle('on'); });

  overlay.addEventListener('click', function (e) {
    var b = e.target.closest('button[data-go]');
    if (b) show(parseInt(b.dataset.go, 10));
    else if (e.target === overlay) overlay.classList.remove('on');
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'ArrowRight' || e.key === 'PageDown' || e.key === ' ') { e.preventDefault(); show(index + 1); }
    else if (e.key === 'ArrowLeft' || e.key === 'PageUp') { e.preventDefault(); show(index - 1); }
    else if (e.key === 'Home') show(0);
    else if (e.key === 'End') show(slides.length - 1);
    else if (e.key === 'Escape') overlay.classList.remove('on');
    else if (e.key === 's' || e.key === 'S') overlay.classList.toggle('on');
    else if (e.key === 'p' || e.key === 'P') window.print();
  });

  // Balayage tactile, pour animer depuis une tablette.
  var x0 = null;
  document.addEventListener('touchstart', function (e) { x0 = e.changedTouches[0].clientX; }, { passive: true });
  document.addEventListener('touchend', function (e) {
    if (x0 === null) return;
    var dx = e.changedTouches[0].clientX - x0;
    if (Math.abs(dx) > 60) show(index + (dx < 0 ? 1 : -1));
    x0 = null;
  }, { passive: true });

  // Lien profond : #12 ouvre la douzième diapositive, y compris après coup.
  window.addEventListener('hashchange', function () { show(fromHash(), false); });

  show(fromHash(), false);
})();
