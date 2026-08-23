/* ==========================================================================
   Bayo Bilingual Complex — tests d'exécution du site institutionnel.

   Ce que ces tests protègent : l'endroit fragile du site est la rencontre
   entre la bascule FR/EN (site.js réécrit le contenu des éléments) et les
   animations (animations.js découpe le titre en mots et pilote l'opacité).
   Une régression y rendrait la page vide ou muette, sans erreur visible.

   Lancer, depuis website/ :

     docker run --rm -v "$PWD":/site:ro -w /tmp node:20-alpine sh -c \
       "npm i --silent --no-fund --no-audit jsdom && cp /site/tests/animations.test.mjs . \
        && SITE_DIR=/site node animations.test.mjs"

   Sans Docker, avec Node et jsdom installés : SITE_DIR=.. node animations.test.mjs
   ========================================================================== */

import { JSDOM, VirtualConsole } from 'jsdom';
import { readFileSync } from 'node:fs';

const SITE = process.env.SITE_DIR || new URL('..', import.meta.url).pathname.replace(/\/$/, '');

const failures = [];
const ok = (cond, label, extra = '') => {
  console.log(`${cond ? 'OK ' : 'KO '} ${label}${extra ? ' — ' + extra : ''}`);
  if (!cond) failures.push(label);
};
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

// jsdom n'implémente pas window.matchMedia (lacune connue du moteur, pas du
// site). On la fournit pour exercer le vrai chemin d'animation ; un cas de
// test dédié plus bas la laisse absente pour vérifier la dégradation.
const mm = (matches) => (q) => ({
  media: q, matches, onchange: null,
  addListener() {}, removeListener() {},
  addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
});

async function load(page, { reduce = false, matchMedia = true, strip = [], lang = 'fr-FR' } = {}) {
  let html = readFileSync(`${SITE}/${page}`, 'utf8');
  for (const s of strip) html = html.replace(s, '');

  const errors = [];
  const vc = new VirtualConsole();
  // « Not implemented » = API que jsdom ne fournit pas (scrollTo…), pas un
  // défaut du site : on ne compte que les vraies erreurs.
  vc.on('jsdomError', (e) => { if (!/Not implemented:/.test(e.message)) errors.push(e.message); });

  const dom = new JSDOM(html, {
    url: `file://${SITE}/${page}`,
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: vc,
    beforeParse(win) {
      if (matchMedia) win.matchMedia = mm(reduce);
      // jsdom annonce en-US ; on choisit la langue testée comme le ferait un visiteur.
      Object.defineProperty(win.navigator, 'language', { value: lang, configurable: true });
    },
  });
  await wait(3200);
  return { win: dom.window, doc: dom.window.document, errors };
}

const op = (doc, sel) => {
  const el = doc.querySelector(sel);
  return el ? parseFloat(el.style.opacity || '1') : NaN;
};

console.log('=== index.html — bannière animée ===');
{
  const { win, doc, errors } = await load('index.html');
  ok(errors.length === 0, 'aucune erreur JavaScript', errors.slice(0, 2).join(' | '));
  ok(!!win.gsap, 'GSAP chargé', win.gsap ? 'v' + win.gsap.version : '');
  ok(!!win.ScrollTrigger, 'ScrollTrigger chargé et enregistré');
  ok(win.__bbcAnim?.ready === true, 'animations.js a pris la main (filet désarmé)');
  ok(!doc.documentElement.classList.contains('anim'), 'masquage CSS retiré du <html>');

  const words = doc.querySelectorAll('.hero h1 .wi');
  ok(words.length === 9, 'titre de bannière découpé en mots',
     [...words].map((w) => w.textContent).join(' · '));
  ok(!!doc.querySelector('.hero h1 em'), '<em> doré du titre préservé par le découpage');
  ok([...words].every((w) => parseFloat(w.style.opacity || '1') === 1), 'mots du titre arrivés à opacity 1');

  ok(doc.querySelectorAll('.hero .aurora .aurora__blob').length === 3, 'décor lumineux injecté dans la bannière');
  ok(!!doc.querySelector('.header .scroll-progress i'), 'barre de progression de lecture insérée');
  ok(doc.querySelectorAll('.ticker__item').length === 3, 'bandeau d’annonces : 3 messages');
  const tItems = [...doc.querySelectorAll('.ticker__item')];
  ok(op(doc, '.ticker__item') === 1, 'premier message du bandeau visible dès le chargement');
  ok(tItems.slice(1).every((i) => parseFloat(i.style.opacity) === 0), 'messages suivants en attente');

  for (const sel of ['.hero .pill', '.hero__lead', '.hero__actions', '.hero__card', '.hero__card li', '.stats > div']) {
    ok(op(doc, sel) === 1, `bannière visible après animation : ${sel}`, 'opacity=' + op(doc, sel));
  }
  const counters = [...doc.querySelectorAll('[data-count]')];
  ok(counters.length === 2 && counters.every((c) => c.textContent === c.getAttribute('data-count')),
     'compteurs arrivés à leur valeur finale', counters.map((c) => c.textContent).join(' / '));

  console.log('\n--- bascule FR → EN ---');
  doc.querySelector('[data-lang-btn="en"]').dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
  await wait(1800);
  const h1 = doc.querySelector('.hero h1');
  ok(/Two languages/i.test(h1.textContent), 'titre traduit', JSON.stringify(h1.textContent.trim().slice(0, 42)));
  const w2 = doc.querySelectorAll('.hero h1 .wi');
  ok(w2.length === 6, 'titre re-découpé après changement de langue',
     [...w2].map((w) => w.textContent).join(' · '));
  ok([...w2].every((w) => parseFloat(w.style.opacity || '1') === 1), 'mots revenus à opacity 1');
  ok(!!doc.querySelector('.hero h1 em'), '<em> doré toujours là en anglais');
  ok(doc.querySelector('.hero__card h3').textContent.startsWith('Why families'), 'reste de la page traduit');
  win.close();
}

console.log('\n=== admissions.html — bandeau intérieur et formulaire ===');
{
  const { win, doc, errors } = await load('admissions.html');
  ok(errors.length === 0, 'aucune erreur JavaScript', errors.slice(0, 2).join(' | '));
  ok(doc.querySelectorAll('.pagehead .aurora__blob').length === 2, 'bandeau de page intérieure animé');
  ok(doc.querySelectorAll('.pagehead h1 .wi').length >= 2, 'titre de page découpé');
  ok(op(doc, '.pagehead h1') === 1 && op(doc, '.pagehead .breadcrumb') === 1, 'bandeau intérieur visible');
  ok(doc.querySelectorAll('label .req').length === 6, 'astérisques « champ requis » présents');
  doc.querySelector('[data-lang-btn="en"]').dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
  await wait(1000);
  ok(doc.querySelectorAll('label .req').length === 6, 'astérisques conservés après changement de langue');
  ok(doc.querySelector('label[for="pi-child"] span').textContent.includes('full name'), 'libellé traduit');
  win.close();
}

console.log('\n=== mouvement réduit demandé ===');
{
  const { win, doc } = await load('index.html', { reduce: true });
  ok(!doc.getElementById('bbc-anim-init'), 'aucun masquage injecté');
  ok(!doc.documentElement.classList.contains('anim'), 'classe de masquage absente');
  ok(doc.querySelectorAll('.aurora').length === 0, 'aucun décor animé créé');
  ok(doc.querySelectorAll('.hero h1 .wi').length === 0, 'titre laissé intact');
  ok(doc.querySelector('.hero h1').textContent.includes('excellence'), 'contenu présent et lisible');
  ok(doc.querySelector('.hero__card li').style.opacity === '', 'aucun style d’animation appliqué');
  win.close();
}

console.log('\n=== GSAP absent (fichier manquant / réseau coupé) ===');
{
  const { doc, win } = await load('index.html', {
    strip: ['<script src="assets/js/vendor/gsap.min.js" defer></script>',
            '<script src="assets/js/vendor/ScrollTrigger.min.js" defer></script>'],
  });
  ok(!doc.documentElement.classList.contains('anim'), 'masquage levé malgré l’absence de GSAP');
  ok(doc.querySelector('.hero h1').textContent.length > 20, 'titre toujours présent');
  ok(doc.querySelectorAll('.ticker__item').length === 3, 'annonces toujours dans le document');
  ok(!!doc.querySelector('[data-lang-btn="en"]'), 'bascule de langue intacte');
  win.close();
}

console.log('\n=== window.matchMedia absente (navigateur ancien) ===');
{
  const { doc, win } = await load('index.html', { matchMedia: false });
  ok(!doc.documentElement.classList.contains('anim'), 'masquage levé');
  ok(doc.querySelectorAll('.hero h1 .wi').length === 9, 'bannière animée quand même (sans ScrollTrigger)');
  ok(doc.querySelectorAll('.hero .aurora__blob').length === 3, 'décor présent');
  for (const sel of ['.hero .pill', '.hero__card', '.stats > div', '.card', '.cta']) {
    ok(op(doc, sel) === 1, `contenu révélé sans ScrollTrigger : ${sel}`, 'opacity=' + op(doc, sel));
  }
  win.close();
}

console.log('\n' + (failures.length ? `ECHECS (${failures.length}) : ` + failures.join(' | ') : '>>> TOUS LES CONTROLES PASSENT'));
process.exit(failures.length ? 1 : 0);
