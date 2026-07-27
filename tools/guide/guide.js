/* Guide utilisateur BBC SMS — comportements (injecté dans index.html). */
(function () {
  var html = document.documentElement;
  var KEY = 'bbc-guide-lang';

  /** Les captures existent en deux langues : fr-<vue>.webp / en-<vue>.webp. */
  function applyShots(lang) {
    document.querySelectorAll('img[data-shot]').forEach(function (img) {
      var next = 'img/' + lang + '-' + img.dataset.shot + '.webp';
      if (img.getAttribute('src') !== next) img.setAttribute('src', next);
    });
  }

  /** Chapitre visible à l'écran, sans le suffixe de langue. */
  function currentChapter() {
    var y = window.scrollY + 140, id = null;
    document.querySelectorAll('main section').forEach(function (s) {
      if (s.offsetParent !== null && s.offsetTop <= y) id = s.id;
    });
    return id ? id.replace(/-en$/, '') : null;
  }

  function setLang(lang, keepPlace) {
    var chapter = keepPlace ? currentChapter() : null;
    html.setAttribute('data-lang', lang);
    html.setAttribute('lang', lang);
    document.querySelectorAll('.langsw button').forEach(function (b) {
      b.setAttribute('aria-pressed', String(b.dataset.lang === lang));
    });
    try { localStorage.setItem(KEY, lang); } catch (e) { /* stockage indisponible */ }
    applyShots(lang);
    if (chapter) {
      // Rejoindre le même chapitre dans l'autre langue plutôt que de revenir en haut.
      var target = document.getElementById(lang === 'fr' ? chapter : chapter + '-en');
      if (target) target.scrollIntoView();
    }
  }

  document.querySelectorAll('.langsw button').forEach(function (b) {
    b.addEventListener('click', function () { setLang(b.dataset.lang, true); });
  });

  var saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) { /* ignore */ }
  setLang(saved === 'en' ? 'en' : 'fr', false);

  // -- menu latéral (mobile) -------------------------------------------------
  var toc = document.getElementById('toc');
  var btn = document.getElementById('menubtn');
  if (btn) btn.addEventListener('click', function () { toc.classList.toggle('open'); });
  toc.addEventListener('click', function (e) {
    if (e.target.tagName === 'A') toc.classList.remove('open');
  });

  // -- surlignage du chapitre courant ---------------------------------------
  function highlight() {
    var current = currentChapter();
    document.querySelectorAll('nav.toc a').forEach(function (a) {
      var href = a.getAttribute('href').slice(1).replace(/-en$/, '');
      a.classList.toggle('on', href === current);
    });
  }
  window.addEventListener('scroll', highlight, { passive: true });
  highlight();

  // -- agrandissement des captures ------------------------------------------
  var box = document.getElementById('lightbox');
  var boxImg = box.querySelector('img');
  document.addEventListener('click', function (e) {
    var img = e.target.closest('figure.shot img');
    if (img) {
      boxImg.src = img.currentSrc || img.src;
      boxImg.alt = img.alt;
      box.classList.add('on');
    } else if (e.target.closest('#lightbox')) {
      box.classList.remove('on');
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') box.classList.remove('on');
  });
})();
