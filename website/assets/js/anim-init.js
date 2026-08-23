/* ==========================================================================
   Bayo Bilingual Complex — amorçage des animations.
   Ce fichier est chargé SANS defer dans le <head> : il doit s'exécuter avant
   le premier rendu pour masquer les éléments qui vont apparaître, sinon la
   page s'afficherait puis disparaîtrait avant de se ré-animer.
   Il est minuscule et ne dépend de rien.
   ========================================================================== */
(function () {
  'use strict';

  var root = document.documentElement;

  // Liste unique des éléments animés à l'apparition. animations.js la relit
  // pour garantir qu'aucun d'eux ne reste invisible : la source est ici, et
  // ici seulement.
  var HIDDEN = [
    '.hero .pill', '.hero h1', '.hero__lead', '.hero__actions', '.hero__card',
    '.stats > div',
    '.pagehead .breadcrumb', '.pagehead h1', '.pagehead > .wrap > p',
    '.section .eyebrow', '.section h2', '.section .lead',
    '.card', '.step', '.panel', '.quote', '.table-scroll', 'details.qa',
    '.checklist li', '.cta', '.contact-list li', '.map-card'
  ].join(',');

  window.__bbcAnim = { hidden: HIDDEN, ready: false };

  // Le visiteur qui a demandé moins d'animations voit la page telle quelle.
  var reduce = false;
  try {
    reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch (e) { /* navigateur ancien : on anime */ }
  if (reduce) return;

  var style = document.createElement('style');
  style.id = 'bbc-anim-init';
  style.textContent = 'html.anim ' + HIDDEN.split(',').join(', html.anim ') + '{opacity:0}';
  document.head.appendChild(style);
  root.classList.add('anim');

  // Filet de sécurité : si GSAP ou animations.js ne se chargent pas (réseau
  // coupé, fichier absent), le contenu ne doit pas rester masqué.
  window.setTimeout(function () {
    if (!window.__bbcAnim.ready) root.classList.remove('anim');
  }, 2000);
})();
