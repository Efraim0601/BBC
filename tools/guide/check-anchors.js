/** Vérifie que chaque lien du sommaire mène bien à son chapitre (les deux langues). */
const puppeteer = require('puppeteer');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const browser = await puppeteer.launch({
    headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'],
    defaultViewport: { width: 1400, height: 1000 },
  });
  const page = await browser.newPage();
  await page.goto('file:///guide/index.html', { waitUntil: 'networkidle2' });
  await sleep(1200);
  // Le défilement animé fausserait la mesure : on le neutralise le temps du test.
  await page.evaluate(() => { document.documentElement.style.scrollBehavior = 'auto'; });

  for (const lang of ['fr', 'en']) {
    await page.evaluate((l) => document.querySelector(`.langsw button[data-lang=${l}]`).click(), lang);
    await sleep(600);
    const links = await page.evaluate((l) =>
      [...document.querySelectorAll(`html[data-lang=${l}] nav.toc .l-${l} a`)].map((a) => a.getAttribute('href')), lang);
    let bad = 0;
    for (const href of links) {
      await page.evaluate((h) => { location.hash = '#none'; location.hash = h; }, href);
      await sleep(220);
      const ok = await page.evaluate((h) => {
        const el = document.getElementById(h.slice(1));
        if (!el) return 'absent';
        const top = el.getBoundingClientRect().top;
        return Math.abs(top) < 120 ? 'ok' : Math.round(top);
      }, href);
      if (ok !== 'ok') { console.log(`  ✗ ${lang} ${href} → ${ok}`); bad++; }
    }
    console.log(`${lang}: ${links.length - bad}/${links.length} ancres correctes`);
  }
  await browser.close();
})();
