/* ==========================================================================
   Bayo Bilingual Complex — tests de la page de connexion.

   Ce qui est protégé ici : la page écrit elle-même la session que
   l'application relira. Si les clés de localStorage divergent de
   frontend/src/app/core/auth.service.ts, la connexion « réussit » puis
   l'application redemande les identifiants — sans erreur visible nulle part.
   Ces tests figent le contrat.

   Lancer, depuis website/ :

     docker run --rm -v "$PWD":/site:ro -w /tmp node:20-alpine sh -c \
       "npm i --silent --no-fund --no-audit jsdom && cp /site/tests/login.test.mjs . \
        && SITE_DIR=/site node login.test.mjs"
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

// Clés attendues par l'application (auth.service.ts). Toute divergence casse
// la reprise de session : c'est le cœur de ce fichier de tests.
const ACCESS = 'bbc-access';
const REFRESH = 'bbc-refresh';
const USER = 'bbc-user';
const EXPIRES = 'bbc-access-expires-at';

const TOKEN = {
  accessToken: 'jeton-acces-de-test',
  refreshToken: 'jeton-rafraichissement-de-test',
  expiresInMs: 28800000,
  user: { id: 'u-1', username: 'principal', displayName: 'Nadia Bayo', role: 'principal', schoolName: 'Bayo Bilingual Complex' },
};

const mm = () => ({
  matches: false, addListener() {}, removeListener() {},
  addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
});

/* Les scripts sont injectés depuis le disque plutôt que chargés par jsdom.
   Raison : localStorage n'existe pas sur une origine file:// (origine opaque),
   il faut donc une URL https ; mais avec une URL https, le chargeur de
   ressources de jsdom irait chercher les scripts sur le vrai site. L'ordre du
   document est conservé, donc l'ordre d'exécution aussi. */
function inlineScripts(html) {
  return html.replace(/<script src="([^"]+)"[^>]*><\/script>/g, (whole, src) => {
    const code = readFileSync(`${SITE}/${src}`, 'utf8');
    if (code.includes('</script')) throw new Error(`${src} contient une balise fermante`);
    return `<script>${code}</script>`;
  });
}

/** Charge connexion.html avec un fetch simulé ; renvoie aussi les appels observés. */
async function load({ respond, lang = 'fr-FR' } = {}) {
  const calls = [];
  const dom = new JSDOM(inlineScripts(readFileSync(`${SITE}/connexion.html`, 'utf8')), {
    url: 'https://bbcomplex.com/connexion.html',
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    virtualConsole: new VirtualConsole(),
    beforeParse(win) {
      win.matchMedia = mm;
      Object.defineProperty(win.navigator, 'language', { value: lang, configurable: true });
      win.fetch = (url, init) => {
        calls.push({ url, body: JSON.parse(init.body), method: init.method });
        return Promise.resolve(respond(url, JSON.parse(init.body)));
      };
    },
  });
  await wait(2600);
  return { win: dom.window, doc: dom.window.document, calls };
}

const jsonResponse = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  json: () => Promise.resolve(body),
});

const submit = (doc, win, selector) => {
  const form = doc.querySelector(selector);
  form.dispatchEvent(new win.Event('submit', { bubbles: true, cancelable: true }));
};

console.log('=== connexion réussie ===');
{
  const { win, doc, calls } = await load({ respond: () => jsonResponse(200, TOKEN) });

  // La navigation est bloquée : jsdom ne sait pas naviguer, et on veut
  // inspecter la session écrite.
  let redirect = null;
  doc.addEventListener('bbc:authenticated', (e) => { redirect = e.detail.target; e.preventDefault(); });

  doc.querySelector('#lg-user').value = '  principal  ';
  doc.querySelector('#lg-pwd').value = 'password';
  submit(doc, win, '[data-login-form]');
  await wait(400);

  ok(calls.length === 1, 'un seul appel réseau', `${calls.length} appel(s)`);
  if (!calls.length) { console.log('KO  interruption : aucun appel observé'); process.exit(1); }
  ok(calls[0].url === '/api/auth/login', 'appelle le même endpoint que l’application', calls[0].url);
  ok(calls[0].method === 'POST', 'en POST');
  ok(calls[0].body.username === 'principal', 'identifiant débarrassé de ses espaces', JSON.stringify(calls[0].body.username));
  ok(calls[0].body.password === 'password', 'mot de passe transmis tel quel');
  ok(!('password' in calls[0].body) === false && Object.keys(calls[0].body).join(',') === 'username,password',
     'aucun champ superflu envoyé', Object.keys(calls[0].body).join(','));

  const ls = win.localStorage;
  ok(ls.getItem(ACCESS) === TOKEN.accessToken, `session écrite : ${ACCESS}`);
  ok(ls.getItem(REFRESH) === TOKEN.refreshToken, `session écrite : ${REFRESH}`);
  ok(ls.getItem(USER) === JSON.stringify(TOKEN.user), `session écrite : ${USER}`);
  const exp = Number(ls.getItem(EXPIRES));
  ok(exp > Date.now() && exp <= Date.now() + TOKEN.expiresInMs + 5000,
     `session écrite : ${EXPIRES} (date absolue d’expiration)`, new Date(exp).toISOString());

  ok(redirect === '/app/parcours', 'un rôle non parent est envoyé au choix du parcours', String(redirect));
  ok(doc.querySelector('[data-login-error]').hidden, 'aucune erreur affichée');
  win.close();
}

console.log('\n=== un parent va sur son portail ===');
{
  const parent = { ...TOKEN, user: { ...TOKEN.user, username: 'parent1', role: 'parent' } };
  const { win, doc } = await load({ respond: () => jsonResponse(200, parent) });
  let redirect = null;
  doc.addEventListener('bbc:authenticated', (e) => { redirect = e.detail.target; e.preventDefault(); });
  doc.querySelector('#lg-user').value = 'parent1';
  doc.querySelector('#lg-pwd').value = 'password';
  submit(doc, win, '[data-login-form]');
  await wait(400);
  ok(redirect === '/app/parent', 'redirection vers le portail parent', String(redirect));
  win.close();
}

console.log('\n=== identifiants refusés ===');
{
  const { win, doc, calls } = await load({ respond: () => jsonResponse(401, { message: 'Bad credentials' }) });
  doc.querySelector('#lg-user').value = 'principal';
  doc.querySelector('#lg-pwd').value = 'mauvais';
  submit(doc, win, '[data-login-form]');
  await wait(400);

  const err = doc.querySelector('[data-login-error]');
  ok(!err.hidden, 'un message d’erreur est affiché');
  ok(/incorrect/i.test(err.textContent), 'message générique, sans révéler si le compte existe', JSON.stringify(err.textContent));
  ok(win.localStorage.getItem(ACCESS) === null, 'aucune session écrite');
  ok(doc.querySelector('#lg-pwd').value === '', 'le mot de passe est vidé du champ');
  ok(calls.length === 1, 'pas de nouvelle tentative automatique');
  ok(!doc.querySelector('[type="submit"]').disabled, 'le bouton redevient actif');
  win.close();
}

console.log('\n=== serveur injoignable (promesse rejetée, cas réel) ===');
{
  const { win, doc } = await load({ respond: () => Promise.reject(new Error('network down')) });
  doc.querySelector('#lg-user').value = 'principal';
  doc.querySelector('#lg-pwd').value = 'password';
  submit(doc, win, '[data-login-form]');
  await wait(400);
  const err = doc.querySelector('[data-login-error]');
  ok(!err.hidden && /injoignable/i.test(err.textContent), 'message « serveur injoignable »', JSON.stringify(err.textContent));
  ok(win.localStorage.getItem(ACCESS) === null, 'aucune session écrite');
  ok(!doc.querySelector('[data-login-form] [type="submit"]').disabled, 'le bouton n’est pas resté bloqué');
  win.close();
}

console.log('\n=== fetch qui lève au lieu de rejeter (API absente) ===');
{
  const { win, doc } = await load({ respond: () => { throw new Error('fetch indisponible'); } });
  doc.querySelector('#lg-user').value = 'principal';
  doc.querySelector('#lg-pwd').value = 'password';
  submit(doc, win, '[data-login-form]');
  await wait(400);
  const err = doc.querySelector('[data-login-error]');
  ok(!err.hidden, 'une erreur est tout de même affichée', JSON.stringify(err.textContent));
  ok(!doc.querySelector('[data-login-form] [type="submit"]').disabled,
     'le bouton ne reste pas bloqué sur « Connexion en cours… »');
  win.close();
}

console.log('\n=== champs vides ===');
{
  const { win, doc, calls } = await load({ respond: () => jsonResponse(200, TOKEN) });
  submit(doc, win, '[data-login-form]');
  await wait(300);
  ok(calls.length === 0, 'aucun appel réseau sans identifiant ni mot de passe');
  ok(!doc.querySelector('[data-login-error]').hidden, 'l’utilisateur est prévenu');
  win.close();
}

console.log('\n=== réponse incomplète du serveur ===');
{
  const { win, doc } = await load({ respond: () => jsonResponse(200, { accessToken: 'seul' }) });
  doc.querySelector('#lg-user').value = 'principal';
  doc.querySelector('#lg-pwd').value = 'password';
  submit(doc, win, '[data-login-form]');
  await wait(400);
  ok(win.localStorage.getItem(ACCESS) === null, 'session partielle refusée plutôt qu’à moitié écrite');
  ok(!doc.querySelector('[data-login-error]').hidden, 'erreur affichée');
  win.close();
}

console.log('\n=== mot de passe oublié ===');
{
  const { win, doc, calls } = await load({
    respond: () => jsonResponse(200, { ok: true, message: 'Si ce compte existe, un message vient d’être envoyé.' }),
  });
  doc.querySelector('#fg-user').value = 'principal';
  submit(doc, win, '[data-forgot-form]');
  await wait(400);
  ok(calls.length === 1 && calls[0].url === '/api/auth/forgot-password',
     'appelle l’endpoint de réinitialisation de l’application', calls[0] && calls[0].url);
  const note = doc.querySelector('[data-forgot-feedback]');
  ok(!note.hidden && note.textContent.length > 10, 'le message du serveur est affiché', JSON.stringify(note.textContent));
  ok(win.localStorage.getItem(ACCESS) === null, 'aucune session ouverte au passage');
  win.close();
}

console.log('\n=== page en anglais ===');
{
  const { win, doc } = await load({ respond: () => jsonResponse(401, {}), lang: 'en-GB' });
  ok(doc.documentElement.getAttribute('lang') === 'en', 'page basculée en anglais');
  ok(doc.querySelector('label[for="lg-user"] span').textContent === 'Username', 'libellé traduit');
  doc.querySelector('#lg-user').value = 'principal';
  doc.querySelector('#lg-pwd').value = 'mauvais';
  submit(doc, win, '[data-login-form]');
  await wait(400);
  ok(/Incorrect username or password/.test(doc.querySelector('[data-login-error]').textContent),
     'erreur affichée en anglais');
  win.close();
}

console.log('\n=== afficher / masquer le mot de passe ===');
{
  const { win, doc } = await load({ respond: () => jsonResponse(200, TOKEN) });
  const input = doc.querySelector('#lg-pwd');
  const toggle = doc.querySelector('[data-toggle-password]');
  ok(input.type === 'password', 'masqué par défaut');
  toggle.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
  ok(input.type === 'text' && toggle.getAttribute('aria-pressed') === 'true', 'affiché après clic');
  toggle.dispatchEvent(new win.MouseEvent('click', { bubbles: true }));
  ok(input.type === 'password' && toggle.getAttribute('aria-pressed') === 'false', 'masqué à nouveau');
  win.close();
}

console.log('\n' + (failures.length ? `ECHECS (${failures.length}) : ` + failures.join(' | ') : '>>> TOUS LES CONTROLES PASSENT'));
process.exit(failures.length ? 1 : 0);
