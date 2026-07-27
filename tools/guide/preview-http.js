/** Contrôle du guide tel qu'il est servi par l'application (HTTP, pas file://). */
const puppeteer = require('puppeteer');
const BASE = process.env.BASE || 'http://localhost:8081';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
    defaultViewport: { width: 1400, height: 1000 },
  });
  const page = await browser.newPage();
  const failed = [];
  page.on('requestfailed', (r) => failed.push(r.url()));
  page.on('response', (r) => { if (r.status() >= 400) failed.push(r.status() + ' ' + r.url()); });

  await page.goto(`${BASE}/guide/`, { waitUntil: 'networkidle2' });
  await sleep(1500);
  await page.screenshot({ path: '/out/http-top.webp', type: 'webp', quality: 85 });

  await page.evaluate(() => { document.documentElement.style.scrollBehavior = 'auto'; location.hash = '#eleves'; });
  await sleep(900);
  await page.evaluate(() => window.scrollBy(0, 1500));
  await sleep(900);
  await page.screenshot({ path: '/out/http-students.webp', type: 'webp', quality: 85 });

  // Version mobile
  await page.setViewport({ width: 420, height: 860 });
  await sleep(800);
  await page.screenshot({ path: '/out/http-mobile.webp', type: 'webp', quality: 85 });

  console.log(failed.length ? 'requêtes en échec :\n' + failed.join('\n') : 'aucune requête en échec');
  await browser.close();
})();
