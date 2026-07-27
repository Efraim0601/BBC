/** Rend le guide généré en images, pour relecture visuelle (fichier local). */
const puppeteer = require('puppeteer');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage', '--allow-file-access-from-files'],
    defaultViewport: { width: 1400, height: 1000 },
  });
  const page = await browser.newPage();
  await page.goto('file:///guide/index.html', { waitUntil: 'networkidle2' });
  await sleep(1500);
  await page.screenshot({ path: '/out/preview-top.webp', type: 'webp', quality: 85 });

  for (const [anchor, name] of [['#parametres', 'settings'], ['#eleves', 'students'], ['#finance', 'finance'], ['#faq', 'faq']]) {
    await page.evaluate((a) => { location.hash = a; }, anchor);
    await sleep(900);
    await page.evaluate(() => window.scrollBy(0, 380));
    await sleep(600);
    await page.screenshot({ path: `/out/preview-${name}.webp`, type: 'webp', quality: 85 });
  }

  // Version anglaise
  await page.evaluate(() => document.querySelector('.langsw button[data-lang=en]').click());
  await sleep(1200);
  await page.evaluate(() => { location.hash = '#academique'; });
  await sleep(1000);
  await page.screenshot({ path: '/out/preview-en.webp', type: 'webp', quality: 85 });

  console.log('ok');
  await browser.close();
})();
