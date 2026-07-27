/** Détecte un débordement horizontal du guide sur écran étroit. */
const puppeteer = require('puppeteer');
const BASE = process.env.BASE || 'http://localhost:8081';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  for (const width of [420, 768, 1024]) {
    const page = await browser.newPage();
    await page.setViewport({ width, height: 900 });
    await page.goto(`${BASE}/guide/`, { waitUntil: 'networkidle2' });
    await sleep(1200);
    const info = await page.evaluate(() => {
      const doc = document.documentElement;
      const wide = [];
      document.querySelectorAll('main *').forEach((el) => {
        if (el.scrollWidth > doc.clientWidth + 2 && el.offsetParent !== null) {
          wide.push(el.tagName + '.' + (el.className || '').toString().slice(0, 30) + ' → ' + el.scrollWidth);
        }
      });
      return { page: doc.scrollWidth, view: doc.clientWidth, wide: wide.slice(0, 6) };
    });
    console.log(`${width}px → page ${info.page}px / fenêtre ${info.view}px`);
    info.wide.forEach((w) => console.log('   large :', w));
    if (width === 420) await page.screenshot({ path: '/out/mobile-420.webp', type: 'webp', quality: 85 });
    await page.close();
  }
  await browser.close();
})();
