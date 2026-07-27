/** Rend quelques diapositives de l'atelier, pour relecture visuelle. */
const puppeteer = require('puppeteer');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const SHOTS = process.env.SLIDES ? process.env.SLIDES.split(',').map(Number) : [1, 2, 4, 6, 9, 10, 45, 47, 70];

(async () => {
  const b = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const p = await b.newPage();
  await p.setViewport({ width: 1600, height: 900 });
  for (const n of SHOTS) {
    await p.goto(`file:///guide/atelier.html#${n}`, { waitUntil: 'networkidle2' });
    await sleep(900);
    await p.screenshot({ path: `/out/slide-${String(n).padStart(2, '0')}.webp`, type: 'webp', quality: 88 });
  }
  console.log('diapositives rendues :', SHOTS.join(', '));
  await b.close();
})();
