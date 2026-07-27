/**
 * Captures d'écran du guide utilisateur BBC SMS.
 *
 * Parcourt l'application de démonstration écran par écran et enregistre une
 * image par étape de procédure (une image = une étape du guide).
 *
 *   docker run --rm --network host \
 *     -v /opt/BBC/tools/guide:/work -v /opt/BBC/frontend/public/guide/img:/out \
 *     ghcr.io/puppeteer/puppeteer:latest node /work/capture.js
 *
 * Variables d'environnement :
 *   BASE  (défaut http://localhost:8081)  origine de l'application
 *   LANG_UI (fr | en, défaut fr)          langue de l'interface capturée
 *   OUT   (défaut /out)                   dossier de sortie
 *   ONLY  (préfixe, optionnel)            ne capturer que les vues correspondantes
 */
const puppeteer = require('puppeteer');
const fs = require('node:fs');

const BASE = process.env.BASE || 'http://localhost:8081';
const LANG = (process.env.LANG_UI || 'fr').toLowerCase();
const OUT = process.env.OUT || '/out';
const ONLY = process.env.ONLY || '';
const W = 1440, H = 900;

const FR = LANG === 'fr';
const t = (fr, en) => (FR ? fr : en);

fs.mkdirSync(OUT, { recursive: true });

let browser, page;
const done = [];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Enregistre la vue courante sous <lang>-<name>.webp.
 *
 * L'application occupe exactement la hauteur de la fenêtre (le défilement a lieu
 * dans <main>), donc `fullPage` ne sert à rien : pour une vue longue on agrandit
 * temporairement la fenêtre à la hauteur du contenu.
 */
async function shot(name, { full = false, settle = 450, maxH = 2800 } = {}) {
  if (ONLY && !name.startsWith(ONLY)) return;
  await sleep(settle);
  if (full) {
    const h = await page.evaluate(() => {
      const main = document.querySelector('main');
      const inner = main ? main.firstElementChild : null;
      return Math.max(
        inner ? inner.scrollHeight + 130 : 0,
        main ? main.scrollHeight + 130 : 0,
        document.body.scrollHeight,
      );
    });
    await page.setViewport({ width: W, height: Math.min(Math.max(h, H), maxH) });
    await sleep(700);
  }
  const path = `${OUT}/${LANG}-${name}.webp`;
  await page.screenshot({ path, type: 'webp', quality: 88 });
  if (full) {
    await page.setViewport({ width: W, height: H });
    await sleep(350);
  }
  done.push(name);
  console.log('  ✓', `${LANG}-${name}.webp`);
}

/** Clique le premier élément dont le texte contient `text`. */
async function clickText(text, sel = 'button, a, [role=button]') {
  const ok = await page.evaluate((sel, text) => {
    const norm = (s) => (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
    const needle = norm(text);
    const el = [...document.querySelectorAll(sel)].find((e) => norm(e.textContent).includes(needle));
    if (!el) return false;
    el.scrollIntoView({ block: 'center' });
    el.click();
    return true;
  }, sel, text);
  if (!ok) console.log('    · introuvable :', text);
  await sleep(500);
  return ok;
}

/** Sélectionne une valeur dans un <select> (par libellé d'option). */
async function pickOption(selectSel, labelPart, nth = 0) {
  const ok = await page.evaluate((selectSel, labelPart, nth) => {
    const sels = [...document.querySelectorAll(selectSel)];
    const sel = sels[nth];
    if (!sel) return false;
    const opt = [...sel.options].find((o) => (o.textContent || '').toLowerCase().includes(String(labelPart).toLowerCase()));
    if (!opt) return false;
    sel.value = opt.value;
    sel.dispatchEvent(new Event('change', { bubbles: true }));
    sel.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  }, selectSel, labelPart, nth);
  await sleep(700);
  return ok;
}

async function type(selector, value, nth = 0) {
  const handles = await page.$$(selector);
  const el = handles[nth];
  if (!el) return false;
  await el.click({ clickCount: 3 });
  await el.type(String(value), { delay: 12 });
  await sleep(200);
  return true;
}

async function go(route, settle = 1400) {
  await page.goto(`${BASE}/${route.replace(/^\//, '')}`, { waitUntil: 'networkidle2' });
  await sleep(settle);
}

/**
 * Sélecteur d'élève partagé (Correspondance, Parcours, Santé, Documents) :
 * choisir la classe puis le premier élève de la liste.
 */
async function pickStudent(className = '4ème', name = '') {
  await pickOption('bbc-student-class-picker select', className);
  await sleep(900);
  const ok = await page.evaluate((name) => {
    const rows = [...document.querySelectorAll('bbc-student-class-picker button')]
      .filter((x) => /BBC-\d/.test(x.textContent || ''));
    const b = name
      ? rows.find((x) => (x.textContent || '').toLowerCase().includes(name.toLowerCase())) || rows[0]
      : rows[0];
    if (!b) return false;
    b.scrollIntoView({ block: 'center' });
    b.click();
    return true;
  }, name);
  if (!ok) console.log('    · aucun élève dans le sélecteur');
  await sleep(1400);
  return ok;
}

async function scrollTo(text) {
  await page.evaluate((text) => {
    const norm = (s) => (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
    const el = [...document.querySelectorAll('h1,h2,h3,div,span,th,label')]
      .find((e) => norm(e.textContent).startsWith(norm(text)));
    if (el) el.scrollIntoView({ block: 'start' });
  }, text);
  await sleep(400);
}

/** Force la langue de l'UI avant la première navigation. */
async function primeStorage() {
  await page.evaluateOnNewDocument((lang) => {
    localStorage.setItem('bbc-lang', lang);
  }, LANG);
}

/** Ouvre une session pour un compte de démonstration (tous les parcours). */
async function loginAs(username, { allParcours = true } = {}) {
  const session = async () => page.evaluate(() => {
    try { return JSON.parse(localStorage.getItem('bbc-user') || 'null')?.username ?? null; } catch { return null; }
  });

  for (let attempt = 1; attempt <= 3; attempt++) {
    await go('login', 500);
    await page.evaluate(() => localStorage.clear());
    await page.reload({ waitUntil: 'networkidle2' });
    await page.waitForSelector('input[name=username]', { visible: true });
    await sleep(700);
    // Écriture directe + événement `input` : plus fiable que la frappe simulée,
    // qui se perdait quand Angular re-rendait le formulaire entre deux sessions.
    await page.evaluate((user) => {
      const set = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      const fill = (sel, value) => {
        const el = document.querySelector(sel);
        if (!el) return;
        set.call(el, value);
        el.dispatchEvent(new Event('input', { bubbles: true }));
      };
      fill('input[name=username]', user);
      fill('input[name=password]', 'password');
    }, username);
    await sleep(400);
    await clickText(t('Se connecter', 'Sign in'));
    // La session est écrite dans localStorage dès la réponse du serveur.
    for (let i = 0; i < 20 && !(await session()); i++) await sleep(400);
    if (await session()) break;
    console.log(`    · tentative ${attempt} échouée pour ${username}`);
  }

  if (allParcours) {
    for (let i = 0; i < 10 && !page.url().includes('/parcours'); i++) await sleep(300);
    if (page.url().includes('/parcours')) {
      await clickText(t('Tous les parcours', 'All parcours'), 'button');
      await sleep(2000);
    }
  } else {
    await sleep(2000);
  }
  console.log(`    session : ${(await session()) ?? 'échec'} (${page.url().replace(BASE, '')})`);
}

// --------------------------------------------------------------------------
async function captureAuth() {
  console.log('· Connexion & repères');
  await go('login');
  await shot('01-login');

  await clickText(t('Oublié', 'Forgot'));
  await shot('02-login-mot-de-passe-oublie');
  await clickText(t('Retour à la connexion', 'Back to sign in'));

  await type('input[name=username]', 'principal');
  await type('input[name=password]', 'password');
  await shot('03-login-rempli');
  await clickText(t('Se connecter', 'Sign in'));
  await sleep(2200);
  await shot('04-parcours-niveau');

  await clickText(t('Primaire', 'Primary'), 'button');
  await shot('05-parcours-section');
  await clickText(t('Retour aux parcours', 'Back to parcours'), 'button');
  await sleep(300);
  await clickText(t('Tous les parcours', 'All parcours'), 'button');
  await sleep(2200);
  await shot('06-accueil-applications');
  await shot('07-accueil-modules', { full: true });
}

async function captureSettings() {
  console.log('· Paramètres');
  await go('settings');
  await shot('10-parametres-scolarite-sections');
  await clickText(t('Nouvelle section', 'New section'));
  await shot('11-parametres-section-formulaire');
  await clickText(t('Annuler', 'Cancel'));

  await clickText('Classes', 'button');
  await shot('12-parametres-classes');
  await clickText(t('Nouvelle classe', 'New class'));
  await shot('13-parametres-classe-formulaire');
  await clickText(t('Annuler', 'Cancel'));
  // Panneau « enseignants de la classe » : bouton portant le compteur
  await page.evaluate(() => {
    const btn = [...document.querySelectorAll('td button')].find((b) => b.querySelector('svg'));
    if (btn) btn.click();
  });
  await sleep(900);
  await scrollTo(t('Enseignants de', 'Teachers of'));
  await shot('14-parametres-classe-enseignants');

  await clickText(t('Matières', 'Subjects'), 'button');
  await shot('15-parametres-matieres');
  await clickText(t('Nouvelle matière', 'New subject'));
  await shot('16-parametres-matiere-formulaire');
  await clickText(t('Annuler', 'Cancel'));
  await scrollTo(t('Coefficients par classe', 'Per-class coefficients'));
  await shot('17-parametres-coefficients');

  await clickText(t('Général', 'General'), 'button');
  await shot('18-parametres-general');

  await clickText(t('Calendrier', 'Calendar'), 'button');
  await shot('19-parametres-calendrier');

  await clickText('Discipline', 'button');
  await shot('20-parametres-catalogue-discipline');

  await clickText('Permissions', 'button');
  await shot('21-parametres-permissions', { full: true });

  await clickText(t('Rôles', 'Roles'), 'button');
  await shot('22-parametres-roles');

  await clickText(t('Messagerie', 'E-mail'), 'button');
  await shot('23-parametres-messagerie');
}

async function captureStudents() {
  console.log('· Élèves');
  await go('students');
  await shot('30-eleves-liste');
  await page.evaluate(() => {
    const row = document.querySelectorAll('tbody tr')[2] || document.querySelector('tbody tr');
    if (row) row.click();
  });
  await sleep(900);
  await scrollTo(t('Informations parent', 'Parent info'));
  await shot('31-eleves-fiche');
  await clickText(t('Ajouter', 'Add'));
  await shot('32-eleves-compte-parent');
  await clickText(t('Annuler', 'Cancel'));

  await page.evaluate(() => window.scrollTo(0, 0));
  await clickText(t('Nouvel élève', 'New student'));
  await shot('33-eleves-formulaire');
  await scrollTo(t('Famille / tuteur', 'Family / guardian'));
  await shot('34-eleves-formulaire-famille');
  await clickText(t('Annuler', 'Cancel'));

  await clickText(t('Importer', 'Import'));
  await shot('35-eleves-import');
  await clickText(t('Exemple', 'Sample'));
  await sleep(600);
  await scrollTo(t('Aperçu', 'Preview'));
  await shot('36-eleves-import-apercu');
  await page.evaluate(() => window.scrollTo(0, 0));
  await clickText(t('Annuler', 'Cancel'));
}

async function captureStaff() {
  console.log('· Personnel');
  await go('staff');
  await shot('40-personnel-annuaire');
  await page.evaluate(() => {
    const row = document.querySelectorAll('tbody tr')[1] || document.querySelector('tbody tr');
    if (row) row.click();
  });
  await sleep(800);
  await scrollTo(t('Compte de connexion', 'Login account'));
  await shot('41-personnel-fiche');

  await page.evaluate(() => window.scrollTo(0, 0));
  await clickText(t('Nouvel employé', 'New employee'));
  await shot('42-personnel-formulaire');
  await scrollTo(t('Contrat & rémunération', 'Contract & compensation'));
  await shot('43-personnel-contrat');
  await clickText(t('Annuler', 'Cancel'));

  await clickText(t('Importer', 'Import'));
  await shot('44-personnel-import');
  await clickText(t('Exemple', 'Sample'));
  await sleep(500);
  await shot('45-personnel-import-apercu', { full: true });
  await clickText(t('Annuler', 'Cancel'));

  await clickText(t('Candidatures', 'Applications'), 'button');
  await shot('46-personnel-portail-candidatures');
  await clickText(t('Départements', 'Departments'), 'button');
  await shot('47-personnel-departements');
  await clickText(t('Congés', 'Leave'), 'button');
  await shot('48-personnel-conges');
  await clickText(t('Masse salariale', 'Payroll'), 'button');
  await shot('49-personnel-masse-salariale');
}

async function capturePresence() {
  console.log('· Présence');
  await go('presence');
  await shot('50-presence-tableau');
  await scrollTo(t('Journal de présence', 'Presence journal'));
  await shot('51-presence-journal');
  await page.evaluate(() => {
    const input = document.querySelector('input[type=date]');
    if (!input) return;
    const d = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, d);
    input.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await sleep(1200);
  await shot('52-presence-historique');
}

async function captureAcademic() {
  console.log('· Académique');
  await go('academic');
  await shot('60-academique-choix-classe');
  await pickOption('select', '4ème');
  await sleep(1200);
  await shot('61-academique-liste-eleves');
  // Premier élève à jour de ses frais : son bulletin porte le bouton de validation.
  const roster = await page.evaluate(() =>
    [...document.querySelectorAll('button')].filter((b) => /BBC-\d/.test(b.textContent || '')).length);
  let blocked = -1;
  for (let i = 0; i < Math.min(roster, 8); i++) {
    await page.evaluate((i) => {
      const b = [...document.querySelectorAll('button')].filter((x) => /BBC-\d/.test(x.textContent || ''))[i];
      if (b) { b.scrollIntoView({ block: 'center' }); b.click(); }
    }, i);
    await sleep(1500);
    const state = await page.evaluate(() => {
      const txt = document.body.innerText;
      return { ok: /Valider le bulletin|Validate report card/i.test(txt), blocked: /verrouillé|locked/i.test(txt) };
    });
    if (state.blocked && blocked < 0) blocked = i;
    if (state.ok) break;
  }
  await shot('62-academique-bulletin', { full: true });
  if (blocked >= 0) {
    await page.evaluate((i) => {
      const b = [...document.querySelectorAll('button')].filter((x) => /BBC-\d/.test(x.textContent || ''))[i];
      if (b) { b.scrollIntoView({ block: 'center' }); b.click(); }
    }, blocked);
    await sleep(1500);
    await shot('64-academique-bulletin-bloque', { full: true });
  }
  await clickText(t('Procès-verbal', 'Master sheet'), 'button');
  await sleep(700);
  await clickText(t('Charger le PV', 'Load master sheet'));
  await sleep(1200);
  await shot('63-academique-pv', { full: true });
}

async function captureDiscipline() {
  console.log('· Discipline');
  await go('discipline');
  await shot('70-discipline-liste');
  await clickText(t('Nouvel incident', 'New incident'));
  await sleep(500);
  await pickOption('select', '4ème');
  await sleep(900);
  await shot('71-discipline-formulaire');
  await clickText(t('Annuler', 'Cancel'));
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button[title]')].find((x) => /notifi/i.test(x.title));
    if (b) b.click();
  });
  await sleep(600);
  await shot('72-discipline-notification');
}

async function captureCoursebook() {
  console.log('· Cahier de textes');
  await go('coursebook');
  await pickOption('select', '4ème');
  await sleep(1200);
  await shot('80-cahier-textes');
  await clickText(t('Nouvelle entrée', 'New entry'));
  await sleep(500);
  await shot('81-cahier-textes-formulaire');
}

async function captureFinance() {
  console.log('· Finance (compte économe — seul rôle en écriture)');
  await loginAs('econome');
  await go('finance');
  await shot('90-finance-encaissements');
  await clickText(t('Nouveau paiement', 'New payment'));
  await sleep(600);
  await pickOption('select', '4ème');
  await sleep(1000);
  await shot('91-finance-nouveau-paiement');
  await page.keyboard.press('Escape');
  await clickText(t('Annuler', 'Cancel'));
  await sleep(400);
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('button[title]')].find((x) => /reçu|receipt/i.test(x.title));
    if (b) b.click();
  });
  await sleep(800);
  await shot('92-finance-recu');
  await clickText(t('Annuler', 'Cancel'));

  await clickText(t('Débiteurs', 'Debtors'), 'button');
  await sleep(1200);
  await shot('93-finance-debiteurs');
  await clickText(t('Dépenses', 'Expenses'), 'button');
  await sleep(1200);
  await shot('94-finance-depenses');
  await clickText(t('Nouvelle dépense', 'New expense'));
  await sleep(500);
  await shot('95-finance-depense-formulaire');
  await clickText(t('Annuler', 'Cancel'));
  await clickText(t('Frais', 'Fees'), 'button');
  await sleep(1200);
  await shot('96-finance-grille-frais', { full: true });

  // Surcharge de grille sur une classe : le formulaire en mode « par classe »
  await clickText(t('Nouvelle grille', 'New grid'));
  await sleep(500);
  await clickText(t('Surcharge par classe', 'Per-class override'));
  await sleep(700);
  await shot('97-finance-grille-classe', { full: true });
  await clickText(t('Annuler', 'Cancel'));

  await clickText(t('Moyens de paiement', 'Payment methods'), 'button');
  await sleep(1200);
  await shot('98-finance-moyens-paiement', { full: true });
  await clickText(t('Coordonnées', 'Details'));
  await sleep(700);
  await shot('99-finance-canal-coordonnees', { full: true });
}

async function captureTimetable() {
  console.log('· Emploi du temps');
  await go('timetable');
  await pickOption('select', '4ème');
  await sleep(1400);
  await shot('100-emploi-du-temps');
  await page.evaluate(() => {
    const cells = [...document.querySelectorAll('button')].filter((b) => b.offsetWidth > 90 && b.offsetHeight > 40);
    const c = cells[2] || cells[0];
    if (c) c.click();
  });
  await sleep(800);
  await scrollTo(t('Modifier le créneau', 'Edit slot'));
  await shot('101-emploi-du-temps-creneau');
}

async function captureEvents() {
  console.log('· Événements');
  await go('events');
  await shot('110-evenements-liste');
  await clickText(t('Nouvel événement', 'New event'));
  await sleep(600);
  await shot('111-evenements-formulaire');
  await clickText(t('Annuler', 'Cancel'));
}

async function captureMessages() {
  console.log('· Correspondance');
  await go('messages');
  await pickStudent('4ème', 'FOTSO');   // élève disposant déjà de notes signées
  await shot('120-correspondance');
  await clickText(t('Nouvelle note', 'New notice'));
  await sleep(500);
  await shot('121-correspondance-formulaire');
}

async function captureClasskit() {
  console.log('· Fournitures & manuels');
  await go('classkit');
  await pickOption('select', '4ème');
  await sleep(1200);
  await shot('130-fournitures');
  await clickText(t('Manuels scolaires', 'School textbooks'), 'button');
  await sleep(1200);
  await shot('131-manuels');
}

async function captureStudentCentric() {
  console.log('· Parcours / Santé / Documents');
  for (const [route, name] of [['journey', '140-parcours-scolaire'], ['health', '141-sante'], ['documents', '142-documents']]) {
    await go(route);
    await pickStudent('4ème');
    await shot(name, { full: true });
  }
}

async function captureSteering() {
  console.log('· Pilotage');
  await go('dashboard');
  await shot('150-tableau-de-bord', { full: true });
  await go('alerts');
  await shot('151-alertes');
  await go('reports');
  await shot('152-rapports', { full: true });
}

async function captureParent() {
  console.log('· Portail parent');
  await loginAs('parent1', { allParcours: false });
  await sleep(1200);
  await shot('160-parent-accueil');
  await clickText(t('Frais & paiements', 'Fees & payments'), 'button');
  await sleep(1400);
  await shot('164-parent-frais', { full: true });

  await clickText(t('Notes', 'Grades'), 'button');
  await sleep(900);
  await shot('161-parent-notes');
  await clickText(t('Fournitures & manuels', 'Supplies & textbooks'), 'button');
  await sleep(1100);
  await shot('162-parent-fournitures');
  await clickText(t('Boîte à suggestions', 'Suggestion box'), 'button');
  await sleep(900);
  await shot('163-parent-suggestions');
}

async function captureStaffPortal() {
  console.log('· Portail inscription personnel');
  const url = process.env.PORTAL_URL;
  if (!url) return;
  await page.goto(url, { waitUntil: 'networkidle2' });
  await sleep(1500);
  await shot('170-portail-personnel', { full: true });
}

// --------------------------------------------------------------------------
(async () => {
  browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--font-render-hinting=none'],
    defaultViewport: { width: W, height: H, deviceScaleFactor: 1 },
  });
  page = await browser.newPage();
  page.setDefaultTimeout(30000);
  await primeStorage();

  // SECTIONS=finance,parent limite la campagne à certaines sections.
  const wanted = (process.env.SECTIONS || '').split(',').map((s) => s.trim()).filter(Boolean);
  const run = async (key, fn) => {
    if (wanted.length && !wanted.includes(key)) return;
    await fn();
  };

  try {
    // Les sections supposent une session ouverte : si la séquence de connexion
    // n'est pas rejouée (campagne partielle), on se connecte silencieusement.
    if (wanted.length && !wanted.includes('auth')) await loginAs('principal');

    await run('auth', captureAuth);
    await run('settings', captureSettings);
    await run('students', captureStudents);
    await run('staff', captureStaff);
    await run('presence', capturePresence);
    await run('academic', captureAcademic);
    await run('discipline', captureDiscipline);
    await run('coursebook', captureCoursebook);
    await run('timetable', captureTimetable);
    await run('events', captureEvents);
    await run('messages', captureMessages);
    await run('classkit', captureClasskit);
    await run('studentcentric', captureStudentCentric);
    await run('steering', captureSteering);
    await run('staffportal', captureStaffPortal);
    await run('finance', captureFinance);   // bascule sur le compte économe
    await run('parent', captureParent);     // puis sur un compte parent
  } catch (e) {
    console.error('✗ interruption :', e.message);
  } finally {
    console.log(`\n${done.length} captures — ${OUT}`);
    await browser.close();
  }
})();
