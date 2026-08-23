/* ==========================================================================
   Bayo Bilingual Complex — comportements du site institutionnel
   Aucune dépendance externe : le site doit rester lisible même si le JS
   échoue (le contenu français est écrit en dur dans le HTML).
   ========================================================================== */
(function () {
  'use strict';

  var STORAGE_KEY = 'bbc-site-lang';
  var DEFAULT_LANG = 'fr';

  /* ---------------------------------------------------------------- i18n --
     Chaque texte traduisible porte ses deux versions dans le HTML :
       <span data-fr="Accueil" data-en="Home"></span>
     Variantes acceptées :
       data-fr-html / data-en-html         → contenu riche (innerHTML)
       data-fr-placeholder / data-en-…     → attribut placeholder
       data-fr-aria / data-en-aria         → attribut aria-label
       data-fr-content / data-en-content   → attribut content (balises meta)
     Le titre de la page vient de <html data-title-fr="…" data-title-en="…">.
     Écrire les traductions dans le HTML plutôt que dans un dictionnaire JS
     permet au secrétariat de corriger un texte sans ouvrir ce fichier.
  ---------------------------------------------------------------------- */

  function readLang() {
    try {
      var stored = window.localStorage.getItem(STORAGE_KEY);
      if (stored === 'fr' || stored === 'en') return stored;
    } catch (e) { /* navigation privée : on ignore */ }
    var nav = (navigator.language || DEFAULT_LANG).slice(0, 2).toLowerCase();
    return nav === 'en' ? 'en' : DEFAULT_LANG;
  }

  function saveLang(lang) {
    try { window.localStorage.setItem(STORAGE_KEY, lang); } catch (e) { /* idem */ }
  }

  function applyLang(lang) {
    var other = lang === 'fr' ? 'en' : 'fr';
    var root = document.documentElement;

    root.setAttribute('lang', lang);

    var title = root.getAttribute('data-title-' + lang);
    if (title) document.title = title;

    var nodes = document.querySelectorAll(
      '[data-' + lang + '], [data-' + lang + '-html], [data-' + lang + '-placeholder],' +
      '[data-' + lang + '-aria], [data-' + lang + '-content],' +
      '[data-' + other + '], [data-' + other + '-html], [data-' + other + '-placeholder],' +
      '[data-' + other + '-aria], [data-' + other + '-content]'
    );

    Array.prototype.forEach.call(nodes, function (el) {
      var html = el.getAttribute('data-' + lang + '-html');
      if (html !== null) { el.innerHTML = html; return; }

      var text = el.getAttribute('data-' + lang);
      if (text !== null) { el.textContent = text; }

      var ph = el.getAttribute('data-' + lang + '-placeholder');
      if (ph !== null) { el.setAttribute('placeholder', ph); }

      var aria = el.getAttribute('data-' + lang + '-aria');
      if (aria !== null) { el.setAttribute('aria-label', aria); }

      var content = el.getAttribute('data-' + lang + '-content');
      if (content !== null) { el.setAttribute('content', content); }
    });

    Array.prototype.forEach.call(document.querySelectorAll('[data-lang-btn]'), function (btn) {
      btn.setAttribute('aria-pressed', String(btn.getAttribute('data-lang-btn') === lang));
    });

    document.dispatchEvent(new CustomEvent('bbc:langchange', { detail: { lang: lang } }));
  }

  function currentLang() {
    return document.documentElement.getAttribute('lang') === 'en' ? 'en' : 'fr';
  }

  /* --------------------------------------------------------- navigation -- */
  function setupNav() {
    var burger = document.querySelector('[data-burger]');
    var nav = document.querySelector('[data-nav]');
    if (!burger || !nav) return;

    burger.addEventListener('click', function () {
      var open = nav.classList.toggle('is-open');
      burger.setAttribute('aria-expanded', String(open));
    });

    nav.addEventListener('click', function (event) {
      if (event.target.closest('a')) {
        nav.classList.remove('is-open');
        burger.setAttribute('aria-expanded', 'false');
      }
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && nav.classList.contains('is-open')) {
        nav.classList.remove('is-open');
        burger.setAttribute('aria-expanded', 'false');
        burger.focus();
      }
    });
  }

  /* ------------------------------------------------- lien de page active -- */
  function markActiveLink() {
    var page = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
    Array.prototype.forEach.call(document.querySelectorAll('[data-nav] a'), function (link) {
      var href = (link.getAttribute('href') || '').split('#')[0].split('/').pop().toLowerCase();
      if (href && href === page) {
        link.classList.add('is-active');
        link.setAttribute('aria-current', 'page');
      }
    });
  }

  /* ------------------------------------------------------- formulaires --
     Le site est 100 % statique : il n'a pas de serveur d'application pour
     recevoir un POST. Les formulaires composent donc un e-mail prérempli
     vers le secrétariat. Pour brancher un vrai traitement, remplacer ce
     gestionnaire par un fetch() vers l'endpoint retenu (voir website/README.md).
  ---------------------------------------------------------------------- */
  function setupMailForms() {
    Array.prototype.forEach.call(document.querySelectorAll('form[data-mailto]'), function (form) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        if (typeof form.reportValidity === 'function' && !form.reportValidity()) return;

        var fr = currentLang() === 'fr';
        var lines = [];

        Array.prototype.forEach.call(form.elements, function (el) {
          if (!el.name || el.type === 'submit' || el.type === 'button') return;
          var label = el.getAttribute('data-label-' + currentLang()) || el.name;
          lines.push(label + ' : ' + (el.value || '—'));
        });

        lines.push('');
        lines.push(fr
          ? 'Message envoyé depuis le site bayobilingual.cm'
          : 'Sent from the bayobilingual.cm website');

        var subject = form.getAttribute('data-subject-' + currentLang())
          || form.getAttribute('data-subject-fr')
          || 'Bayo Bilingual Complex';

        window.location.href = 'mailto:' + form.getAttribute('data-mailto')
          + '?subject=' + encodeURIComponent(subject)
          + '&body=' + encodeURIComponent(lines.join('\n'));

        var note = form.querySelector('[data-form-feedback]');
        if (note) {
          note.hidden = false;
          note.textContent = fr
            ? 'Votre logiciel de messagerie s’ouvre avec le message prérempli. Vérifiez puis envoyez.'
            : 'Your mail application opens with the message pre-filled. Review it, then send.';
        }
      });
    });
  }

  /* -------------------------------------------------- année du copyright -- */
  function setupYear() {
    var year = String(new Date().getFullYear());
    Array.prototype.forEach.call(document.querySelectorAll('[data-year]'), function (el) {
      el.textContent = year;
    });
  }

  /* ------------------------------------------------------------ amorçage -- */
  function init() {
    applyLang(readLang());

    Array.prototype.forEach.call(document.querySelectorAll('[data-lang-btn]'), function (btn) {
      btn.addEventListener('click', function () {
        var lang = btn.getAttribute('data-lang-btn');
        saveLang(lang);
        applyLang(lang);
      });
    });

    setupNav();
    markActiveLink();
    setupMailForms();
    setupYear();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
