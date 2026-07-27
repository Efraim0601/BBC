/**
 * Contrôle écran par écran : ouvre chaque module et relève les erreurs
 * JavaScript et les appels réseau en échec.
 *
 *   docker run --rm --network host -e BASE=http://localhost:8081 \
 *     -e NODE_PATH=/home/pptruser/node_modules -v "$PWD/tools/guide:/work:ro" \
 *     -w /home/pptruser --entrypoint node ghcr.io/puppeteer/puppeteer:latest /work/smoke-ui.js
 */
const puppeteer = require('puppeteer');

const BASE = process.env.BASE || 'http://localhost:8081';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const ROUTES = {
  principal: ['apps', 'dashboard', 'students', 'journey', 'health', 'documents', 'staff',
    'academic', 'presence', 'discipline', 'coursebook', 'timetable', 'events',
    'messages', 'classkit', 'alerts', 'reports', 'settings'],
  econome: ['apps', 'finance', 'students', 'reports', 'classkit'],
  parent1: ['parent'],
};

let failures = 0;

async function session(page, username) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'networkidle2' });
  await page.waitForSelector('input[name=username]', { visible: true });
  await page.evaluate((user) => {
    const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    const fill = (sel, v) => { const el = document.querySelector(sel); set.call(el, v); el.dispatchEvent(new Event('input', { bubbles: true })); };
    fill('input[name=username]', user);
    fill('input[name=password]', 'password');
    [...document.querySelectorAll('button')].find((b) => /se connecter|sign in/i.test(b.textContent)).click();
  }, username);
  for (let i = 0; i < 25; i++) {
    const ok = await page.evaluate(() => !!localStorage.getItem('bbc-user'));
    if (ok) break;
    await sleep(400);
  }
  await sleep(1200);
  if (page.url().includes('/parcours')) {
    await page.evaluate(() => {
      const b = [...document.querySelectorAll('button')].find((x) => /tous les parcours|all parcours/i.test(x.textContent));
      b && b.click();
    });
    await sleep(1500);
  }
}

(async () => {
  const browser = await puppeteer.launch({
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
    defaultViewport: { width: 1440, height: 900 },
  });

  for (const [user, routes] of Object.entries(ROUTES)) {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', (e) => errors.push('JS: ' + String(e).slice(0, 160)));
    page.on('console', (m) => { if (m.type() === 'error') errors.push('console: ' + m.text().slice(0, 160)); });
    page.on('response', (r) => {
      if (r.status() >= 400 && r.url().includes('/api/')) {
        errors.push(`HTTP ${r.status()} ${r.url().replace(BASE, '')}`);
      }
    });

    console.log(`\n${user}`);
    await session(page, user);

    for (const route of routes) {
      errors.length = 0;
      await page.goto(`${BASE}/${route}`, { waitUntil: 'networkidle2' });
      await sleep(1800);
      // Une page rendue affiche du texte : un écran blanc trahit un plantage.
      const text = await page.evaluate(() => (document.querySelector('main') || document.body).innerText.trim().length);
      const blank = text < 40;
      const bad = errors.length > 0 || blank;
      if (bad) failures++;
      console.log(`  ${bad ? '✗' : '✓'} /${route}${blank ? ' — écran vide' : ''}`);
      errors.slice(0, 3).forEach((e) => console.log('      ' + e));
    }
    await page.close();
  }

  console.log(failures ? `\n${failures} écran(s) en défaut` : '\nTous les écrans répondent, sans erreur');
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
