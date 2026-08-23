/* ==========================================================================
   Bayo Bilingual Complex — animations (GSAP 3.13, ScrollTrigger).
   GSAP est servi depuis assets/js/vendor/ et non depuis un CDN : la CSP du
   site n'autorise que 'self' pour les scripts, et une connexion instable ne
   doit pas priver la page de sa bannière.

   Principe : rien n'est masqué par le CSS seul. anim-init.js pose la classe
   `anim` avant le premier rendu, ce fichier reprend la main immédiatement et
   pilote tout en styles en ligne. Si GSAP manque, ou si le visiteur a demandé
   moins d'animations, la page s'affiche normalement — jamais vide.
   ========================================================================== */
(function () {
  'use strict';

  var root = document.documentElement;
  var cfg = window.__bbcAnim || { hidden: '', ready: false };

  // Mouvement réduit demandé : anim-init.js n'a rien masqué, il n'y a rien à faire.
  if (!root.classList.contains('anim')) { cfg.ready = true; return; }

  // GSAP absent : on démasque et on s'arrête là.
  if (!window.gsap) { cfg.ready = true; root.classList.remove('anim'); return; }

  var gsap = window.gsap;
  cfg.ready = true; // GSAP est là : on désarme le filet de sécurité d'anim-init.js

  // ScrollTrigger a besoin de window.matchMedia. Un navigateur ancien, un
  // webview restreint ou un moteur de rendu sans cette API ne doit pas faire
  // tomber toute la couche d'animation : on continue sans le défilement.
  var ST = window.ScrollTrigger || null;
  if (ST) {
    try { gsap.registerPlugin(ST); }
    catch (e) { ST = null; }
  }

  function all(sel, ctx) { return Array.prototype.slice.call((ctx || document).querySelectorAll(sel)); }
  function one(sel, ctx) { return (ctx || document).querySelector(sel); }

  var claimed = [];
  function claim(els) {
    els = els || [];
    for (var i = 0; i < els.length; i++) if (claimed.indexOf(els[i]) === -1) claimed.push(els[i]);
    return els;
  }

  /* ---------------------------------------------------------- décor animé --
     Les taches lumineuses de la bannière sont créées ici plutôt qu'écrites
     dans chaque page : ce sont des éléments décoratifs, ils n'ont rien à
     faire dans le HTML éditorial que le secrétariat relit.
  ------------------------------------------------------------------------ */
  function buildAurora(host, count) {
    if (!host || one('.aurora', host)) return [];
    var wrap = document.createElement('div');
    wrap.className = 'aurora';
    wrap.setAttribute('aria-hidden', 'true');
    var blobs = [];
    for (var i = 1; i <= count; i++) {
      var b = document.createElement('span');
      b.className = 'aurora__blob aurora__blob--' + i;
      wrap.appendChild(b);
      blobs.push(b);
    }
    host.insertBefore(wrap, host.firstChild);
    return blobs;
  }

  function driftAurora(blobs) {
    blobs.forEach(function (b, i) {
      gsap.to(b, {
        xPercent: (i % 2 ? -1 : 1) * (10 + i * 6),
        yPercent: (i % 2 ? 1 : -1) * (8 + i * 5),
        scale: 1.12,
        duration: 11 + i * 4,
        repeat: -1,
        yoyo: true,
        ease: 'sine.inOut'
      });
      gsap.to(b, { opacity: 0.75, duration: 5 + i * 2, repeat: -1, yoyo: true, ease: 'sine.inOut' });
    });
  }

  /* ------------------------------------------------------ titre en mots --
     Découpe le titre en mots sans casser les éléments qu'il contient (le
     <em> doré du titre d'accueil), pour les faire monter un à un.
  ------------------------------------------------------------------------ */
  function wrapWords(node) {
    Array.prototype.slice.call(node.childNodes).forEach(function (child) {
      if (child.nodeType === 3) {
        var text = child.nodeValue;
        if (!text.trim()) return;
        var frag = document.createDocumentFragment();
        text.split(/(\s+)/).forEach(function (part) {
          if (!part) return;
          if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(part)); return; }
          var outer = document.createElement('span');
          outer.className = 'w';
          var inner = document.createElement('span');
          inner.className = 'wi';
          inner.textContent = part;
          outer.appendChild(inner);
          frag.appendChild(outer);
        });
        node.replaceChild(frag, child);
      } else if (child.nodeType === 1 && !child.classList.contains('w')) {
        wrapWords(child);
      }
    });
    return all('.wi', node);
  }

  function playHeadline(h, delay) {
    var words = wrapWords(h);
    if (!words.length) return null;
    gsap.set(h, { opacity: 1 });
    return gsap.fromTo(words,
      { yPercent: 115, rotate: 3, opacity: 0 },
      { yPercent: 0, rotate: 0, opacity: 1, duration: 0.9, ease: 'power3.out',
        stagger: 0.045, delay: delay || 0 });
  }

  /* -------------------------------------------------------- compteurs -- */
  function countUp(el) {
    var target = parseFloat(el.getAttribute('data-count'));
    if (isNaN(target)) return;
    var box = { v: 0 };
    gsap.to(box, {
      v: target, duration: 1.3, ease: 'power2.out',
      onUpdate: function () { el.textContent = String(Math.round(box.v)); }
    });
  }

  /* ------------------------------------------------- bannière d'accueil -- */
  function heroIntro() {
    var hero = one('.hero');
    if (!hero) return false;

    driftAurora(buildAurora(hero, 3));

    var pill = one('.hero .pill');
    var h1 = one('.hero h1');
    var lead = one('.hero__lead');
    var actions = one('.hero__actions');
    var card = one('.hero__card');
    var items = all('.hero__card li');
    var stats = all('.stats > div');

    claim([pill, lead, actions, card].filter(Boolean));
    claim(items); claim(stats);
    if (h1) claim([h1]);

    gsap.set(items, { opacity: 0, x: -16 });

    var tl = gsap.timeline({ defaults: { ease: 'power3.out', duration: 0.9 } });
    if (pill)    tl.to(pill,    { opacity: 1, y: 0, duration: 0.6 }, 0.05);
    if (h1)      tl.add(playHeadline(h1), 0.15);
    if (lead)    tl.to(lead,    { opacity: 1, y: 0 }, 0.45);
    if (actions) tl.to(actions, { opacity: 1, y: 0 }, 0.6);
    if (card)    tl.to(card,    { opacity: 1, y: 0, duration: 1 }, 0.4);
    if (items.length) tl.to(items, { opacity: 1, x: 0, duration: 0.6, stagger: 0.09 }, 0.75);
    if (stats.length) {
      tl.to(stats, { opacity: 1, y: 0, duration: 0.6, stagger: 0.09 }, 0.9);
      tl.add(function () { all('[data-count]').forEach(countUp); }, 1.0);
    }

    // Parallaxe : le décor glisse plus lentement que le texte au défilement.
    if (ST) {
      gsap.to('.hero .aurora', {
        yPercent: 22, ease: 'none',
        scrollTrigger: { trigger: hero, start: 'top top', end: 'bottom top', scrub: 0.4 }
      });
    }
    return true;
  }

  /* --------------------------------------- bandeau des pages intérieures -- */
  function pageheadIntro() {
    var head = one('.pagehead');
    if (!head) return false;

    driftAurora(buildAurora(head, 2));

    var crumb = one('.pagehead .breadcrumb');
    var h1 = one('.pagehead h1');
    var intro = one('.pagehead > .wrap > p');
    claim([crumb, h1, intro].filter(Boolean));

    var tl = gsap.timeline({ defaults: { ease: 'power3.out', duration: 0.8 } });
    if (crumb) tl.to(crumb, { opacity: 1, y: 0, duration: 0.5 }, 0.05);
    if (h1)    tl.add(playHeadline(h1), 0.12);
    if (intro) tl.to(intro, { opacity: 1, y: 0 }, 0.4);

    if (ST) {
      gsap.to('.pagehead .aurora', {
        yPercent: 18, ease: 'none',
        scrollTrigger: { trigger: head, start: 'top top', end: 'bottom top', scrub: 0.4 }
      });
    }
    return true;
  }

  /* ------------------------------------------- apparition au défilement -- */
  function scrollReveals() {
    var rest = all(cfg.hidden).filter(function (el) { return claimed.indexOf(el) === -1; });
    if (!rest.length) return;

    if (!ST) { gsap.to(rest, { opacity: 1, y: 0, duration: 0.5 }); return; }

    ST.batch(rest, {
      start: 'top 88%',
      once: true,
      onEnter: function (batch) {
        gsap.to(batch, {
          opacity: 1, y: 0, duration: 0.75, ease: 'power2.out',
          stagger: 0.08, overwrite: true
        });
      }
    });
    claim(rest);
  }

  /* ----------------------------------------------- messages défilants --
     Le bandeau supérieur fait tourner les annonces de l'établissement.
     Sans JavaScript, le CSS n'en montre qu'une : rien ne se superpose.
  ------------------------------------------------------------------------ */
  function ticker() {
    var box = one('[data-ticker]');
    if (!box) return;
    var items = all('.ticker__item', box);
    if (items.length < 2) return;

    gsap.set(items, { opacity: 0, yPercent: 100 });
    gsap.set(items[0], { opacity: 1, yPercent: 0 });

    var i = 0;
    var tl = gsap.timeline({ repeat: -1, defaults: { duration: 0.55, ease: 'power2.inOut' } });
    items.forEach(function () {
      var current = items[i];
      var next = items[(i + 1) % items.length];
      // immediateRender:false — sans lui, GSAP applique tout de suite l'état
      // de départ de CHAQUE fromTo, y compris celui qui ramène le premier
      // message : le bandeau resterait vide jusqu'au premier tour complet.
      tl.to({}, { duration: 4.5 })
        .to(current, { opacity: 0, yPercent: -100 })
        .fromTo(next,
          { opacity: 0, yPercent: 100 },
          { opacity: 1, yPercent: 0, immediateRender: false }, '<0.15');
      i++;
    });
  }

  /* ------------------------------------------------- en-tête et progrès -- */
  function headerBehaviour() {
    var header = one('.header');
    if (!header) return;

    var bar = document.createElement('div');
    bar.className = 'scroll-progress';
    bar.setAttribute('aria-hidden', 'true');
    var fill = document.createElement('i');
    bar.appendChild(fill);
    header.appendChild(bar);

    if (ST) {
      gsap.fromTo(fill, { scaleX: 0 }, {
        scaleX: 1, ease: 'none', transformOrigin: 'left center',
        scrollTrigger: { start: 0, end: 'max', scrub: 0.3 }
      });
    }

    var ticking = false;
    function update() {
      header.classList.toggle('is-scrolled', (window.scrollY || window.pageYOffset) > 12);
      ticking = false;
    }
    window.addEventListener('scroll', function () {
      if (!ticking) { ticking = true; window.requestAnimationFrame(update); }
    }, { passive: true });
    update();
  }

  /* ---------------------------------------------- boutons et cartes --
     Micro-interactions : assez pour que la page réponde, pas assez pour
     distraire un parent qui cherche un numéro de téléphone.
  ------------------------------------------------------------------------ */
  function microInteractions() {
    all('.btn--primary, .btn--dark').forEach(function (btn) {
      btn.addEventListener('mouseenter', function () {
        gsap.to(btn, { y: -2, duration: 0.25, ease: 'power2.out' });
      });
      btn.addEventListener('mouseleave', function () {
        gsap.to(btn, { y: 0, duration: 0.3, ease: 'power2.out' });
      });
    });

    all('.card__icon').forEach(function (icon) {
      var card = icon.closest('.card');
      if (!card) return;
      card.addEventListener('mouseenter', function () {
        gsap.to(icon, { scale: 1.08, rotate: -4, duration: 0.35, ease: 'back.out(2)' });
      });
      card.addEventListener('mouseleave', function () {
        gsap.to(icon, { scale: 1, rotate: 0, duration: 0.35, ease: 'power2.out' });
      });
    });
  }

  /* ---------------------------------------------------------- amorçage -- */
  function init() {
    var hidden = all(cfg.hidden);
    // On passe du masquage CSS au masquage en ligne : à partir d'ici, c'est
    // GSAP qui décide, et la classe `anim` peut disparaître sans risque.
    gsap.set(hidden, { opacity: 0, y: 22 });
    root.classList.remove('anim');

    // Une décoration ne vaut jamais une page vide : si quoi que ce soit
    // échoue ici, on rend tout visible et on abandonne les animations.
    try {
      heroIntro();
      pageheadIntro();
      scrollReveals();
      ticker();
      headerBehaviour();
      microInteractions();
    } catch (e) {
      gsap.set(hidden, { opacity: 1, y: 0 });
      gsap.set(all('.hero__card li'), { opacity: 1, x: 0 });
      if (window.console && console.warn) console.warn('[bbc] animations desactivees :', e);
      return;
    }

    // Rejouer le titre quand le visiteur change de langue : site.js réécrit
    // le contenu du <h1>, les mots découpés disparaissent avec lui.
    document.addEventListener('bbc:langchange', function () {
      var h = one('.hero h1') || one('.pagehead h1');
      if (h) playHeadline(h);
    });

    if (ST) window.addEventListener('load', function () { ST.refresh(); });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
