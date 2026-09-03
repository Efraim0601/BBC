/**
 * BBC SMS complete local-manual capture and UI smoke run.
 *
 * This script targets the production-shaped LOCAL stack only (default :8130).
 * It records the current page, the persona used, visible headings, and any
 * unexpected browser/API errors next to each PNG.  It never calls a VPS.
 *
 * Run with the bundled Codex Playwright runtime:
 *   NODE_PATH=<bundled node_modules> node tools/guide/capture-complete-current.js
 */
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');

const BASE = (process.env.BASE || 'http://localhost:8130').replace(/\/$/, '');
const OUT = path.resolve(process.env.OUT || 'output/platform-user-manual/screenshots');
const PASSWORD = process.env.QA_PASSWORD;
if (!PASSWORD) {
  throw new Error('QA_PASSWORD must be set to run the local QA capture.');
}
const VIEWPORT = { width: 1440, height: 1100 };
const CHROME_PATH = process.env.CHROME_PATH || chromium.executablePath();
const ONLY_SECTIONS = new Set((process.env.SECTIONS || '').split(',').map(clean => clean.trim()).filter(Boolean));
const manifest = [];
let page;
let currentPersona = 'public';
let currentErrors = [];

fs.mkdirSync(OUT, { recursive: true });
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));
const clean = value => String(value || '').replace(/\s+/g, ' ').trim();

async function bodyText() {
  return clean(await page.locator('body').innerText().catch(() => ''));
}

async function apiJson(apiPath, { method = 'GET', body } = {}) {
  return page.evaluate(async ({ base, apiPath, method, body }) => {
    const token = localStorage.getItem('bbc-access');
    const response = await fetch(`${base}${apiPath}`, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    if (!response.ok) throw new Error(`${method} ${apiPath} returned ${response.status}`);
    return response.status === 204 ? null : response.json();
  }, { base: BASE, apiPath, method, body });
}

async function waitStable(ms = 900) {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  await sleep(ms);
}

async function go(route, ms = 900) {
  currentErrors = [];
  const url = route.startsWith('http') ? route : `${BASE}/${route.replace(/^\//, '')}`;
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await waitStable(ms);
}

async function clickText(text, selector = 'button,a,[role="button"]', exact = false) {
  const candidates = page.locator(selector).filter({ hasText: text });
  const count = await candidates.count();
  for (let i = 0; i < count; i++) {
    const candidate = candidates.nth(i);
    const label = clean(await candidate.innerText().catch(() => ''));
    if ((!exact && label.toLowerCase().includes(text.toLowerCase())) ||
        (exact && label.toLowerCase() === text.toLowerCase())) {
      await candidate.scrollIntoViewIfNeeded().catch(() => {});
      await candidate.click();
      await waitStable(500);
      return true;
    }
  }
  return false;
}

async function selectContaining(index, text) {
  const select = page.locator('select').nth(index);
  if (!(await select.count())) return false;
  const options = await select.locator('option').allTextContents();
  const label = options.find(x => clean(x).toLowerCase().includes(text.toLowerCase()));
  if (!label) return false;
  await select.selectOption({ label });
  await waitStable(750);
  return true;
}

async function scrollToText(text) {
  const locator = page.getByText(text, { exact: false }).first();
  if (await locator.count()) {
    await locator.scrollIntoViewIfNeeded().catch(() => {});
    await sleep(350);
    return true;
  }
  return false;
}

async function resetScroll() {
  await page.evaluate(() => {
    window.scrollTo(0, 0);
    const main = document.querySelector('main');
    if (main) main.scrollTop = 0;
  }).catch(() => {});
}

async function shot(name, title, { tall = false, notes = '', focusText = '' } = {}) {
  await waitStable(350);
  if (!focusText) await resetScroll();
  let height = VIEWPORT.height;
  if (tall) {
    height = await page.evaluate(() => {
      const main = document.querySelector('main');
      const inner = main?.firstElementChild;
      return Math.min(2200, Math.max(1100, inner?.scrollHeight || 0, main?.scrollHeight || 0, document.body.scrollHeight || 0));
    }).catch(() => 1500);
    await page.setViewportSize({ width: VIEWPORT.width, height });
    await sleep(300);
  }
  if (focusText) {
    const focus = page.getByText(focusText, { exact: false }).first();
    if (await focus.count()) {
      await focus.scrollIntoViewIfNeeded().catch(() => {});
      await sleep(350);
    }
  }
  const file = `${name}.png`;
  const target = path.join(OUT, file);
  await page.screenshot({ path: target, animations: 'disabled' });
  const headings = await page.locator('h1,h2,h3').allTextContents().catch(() => []);
  const text = await bodyText();
  const errors = [...new Set(currentErrors)].filter(x => !x.includes('/api/auth/refresh'));
  manifest.push({
    order: manifest.length + 1,
    file,
    title,
    persona: currentPersona,
    url: page.url(),
    headings: headings.map(clean).filter(Boolean),
    bodyCharacters: text.length,
    notes,
    errors,
    passed: text.length > 80 && !errors.some(x => /^JS |^HTTP 5/.test(x)),
  });
  currentErrors = [];
  console.log(`${manifest.at(-1).passed ? '✓' : '✗'} ${file} — ${title}`);
  if (tall) await page.setViewportSize(VIEWPORT);
}

async function login(username, scope = { all: true }) {
  currentPersona = username;
  await go('/login', 250);
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(PASSWORD);
  await page.getByRole('button', { name: /Se connecter|Sign in/i }).click();
  await page.waitForFunction(() => !!localStorage.getItem('bbc-user'), null, { timeout: 12000 });
  await waitStable(900);
  if (page.url().includes('/parcours')) {
    if (scope.all) {
      await clickText('Tous les parcours', 'button');
    } else if (scope.level) {
      const labels = { primary: 'Primaire', secondary: 'Secondaire', maternelle: 'Maternelle' };
      await clickText(labels[scope.level], 'button');
      if (page.url().includes('/parcours') && /Choisissez la section/i.test(await bodyText())) {
        await clickText(scope.subsystem === 'EN' ? 'Anglophone' : 'Francophone', 'button');
      }
    }
  }
  await waitStable(900);
}

async function chooseClassAndStudent(classText = 'CE1 A') {
  const selects = page.locator('select');
  const count = await selects.count();
  for (let i = 0; i < count; i++) {
    const options = await selects.nth(i).locator('option').allTextContents();
    if (options.some(x => clean(x).toLowerCase().includes(classText.toLowerCase()))) {
      const label = options.find(x => clean(x).toLowerCase().includes(classText.toLowerCase()));
      await selects.nth(i).selectOption({ label });
      await waitStable(900);
      break;
    }
  }
  const student = page.locator('button').filter({ hasText: /BBC-\d+/ }).first();
  if (await student.count()) {
    await student.click();
    await waitStable(1100);
    return true;
  }
  return false;
}

async function section(label, fn) {
  if (ONLY_SECTIONS.size && label !== 'Connexion' && !ONLY_SECTIONS.has(label)) return;
  console.log(`\n# ${label}`);
  try { await fn(); }
  catch (error) {
    console.error(`✗ ${label}:`, error.message);
    manifest.push({ order: manifest.length + 1, file: null, title: label, persona: currentPersona,
      url: page?.url?.() || '', headings: [], bodyCharacters: 0, notes: error.message,
      errors: [String(error.stack || error)], passed: false });
  }
}

async function adminBasics() {
  await go('/apps');
  await shot('001-applications', 'Accueil — trouver un module');
  await go('/dashboard');
  await shot('002-dashboard', 'Tableau de bord — indicateurs de l’établissement', { tall: true });
}

async function students() {
  await go('/students');
  await shot('010-students-list', 'Élèves — rechercher, filtrer, exporter et ouvrir une fiche', { tall: true });
  const studentLink = page.locator('a[href*="/students/"]').filter({ hasNot: page.locator('[href*="/students/new"]') }).first();
  if (await studentLink.count()) await studentLink.click();
  else await page.locator('tbody tr').first().click();
  await waitStable(1100);
  await shot('011-student-detail', 'Élève — fiche détaillée, famille et scolarité', { tall: true });

  await go('/students/new');
  await shot('012-student-create-identity', 'Inscription — identité et date au format JJ/MM/AAAA');
  await clickText('Continuer', 'button');
  await shot('013-student-create-schooling', 'Inscription — parcours, classe et scolarité');

  await go('/students/import-family');
  await shot('014-student-family-import', 'Import en masse — élèves et familles', { tall: true });
}

async function studentLife() {
  await go('/journey');
  await chooseClassAndStudent('CE1 A');
  await shot('020-journey', 'Parcours scolaire — historique pluriannuel de l’élève', { tall: true });
  await go('/promotion');
  await shot('021-promotion', 'Passage de classe — décisions et clôture annuelle', { tall: true });
  await go('/pathways');
  await shot('022-pathways', 'Orientation — choix manuel du parcours pour la session suivante', { tall: true });
  await go('/journey/promotions');
  await shot('023-promotion-register', 'Registre de promotion — suivi et traçabilité', { tall: true });

  await go('/health');
  await chooseClassAndStudent('CE1 A');
  await shot('024-health', 'Santé — dossier médical et passages à l’infirmerie', { tall: true });
  await go('/documents');
  await chooseClassAndStudent('CE1 A');
  await shot('025-student-documents', 'Documents élève — déposer, consulter et télécharger', { tall: true });
}

async function staff() {
  await go('/staff');
  await shot('030-staff-list', 'Personnel — annuaire, filtres et export', { tall: true });
  // This local fixture contains image and PDF documents, so the guide shows
  // the real preview and download experience instead of an empty profile.
  const documentedEmployeeId = '78c6a776-2a4d-456d-b5c7-343e2f3fc31c';
  await go(`/staff/${documentedEmployeeId}`, 1200);
  await shot('031-staff-detail', 'Personnel — fiche dédiée et documents', { tall: true });
  const previewButton = page.locator('button').filter({ hasText: /WhatsApp Image|eleves-2026/i }).first();
  if (await previewButton.count()) {
    await previewButton.click();
    await waitStable(1500);
    await shot('031b-staff-document-preview', 'Personnel — consulter un document dans la fiche', { tall: true });
  }
  await go(`/staff/${documentedEmployeeId}/edit`, 1200);
  await shot('032-staff-edit', 'Personnel — modifier une fiche et ses documents', { tall: true });
  await go('/staff/create');
  await shot('033-staff-create', 'Personnel — créer un employé', { tall: true });
  await clickText('Ajouter une catégorie', 'button,a,[role="button"]');
  await shot('034-staff-document-categories', 'Personnel — joindre plusieurs catégories de documents', { tall: true });
  await go('/staff');
  for (const [text, file, title] of [
    ['Candidatures', '035-staff-applications', 'Personnel — candidatures'],
    ['Départements', '036-staff-departments', 'Personnel — départements'],
    ['Congés', '037-staff-leave', 'Personnel — demandes de congé'],
    ['Masse salariale', '038-staff-salary', 'Personnel — synthèse de la masse salariale'],
  ]) {
    await clickText(text, 'button');
    await shot(file, title, { tall: true });
  }
}

async function publicStaffApplication() {
  const original = await apiJson('/api/staff/portal');
  let current = original;
  try {
    if (!current.enabled || !current.publicPath) {
      current = await apiJson('/api/staff/portal', { method: 'PUT', body: { enabled: true } });
    }
    await go(current.publicPath, 1000);
    await shot('039-public-staff-application', 'Candidature publique — envoyer une demande d’emploi', { tall: true });
  } finally {
    if (current.enabled !== original.enabled) {
      await apiJson('/api/staff/portal', { method: 'PUT', body: { enabled: original.enabled } });
    }
  }
}

async function academicsAdmin() {
  await go('/academic', 1800);
  await selectContaining(0, '6ème A');
  await waitStable(3500);
  await shot('040-academic-report-card-roster', 'Académique — choisir un élève et consulter son bulletin', { tall: true });
  await clickText('Saisie des notes', 'button', true);
  await waitStable(3500);
  await shot('041-academic-grade-entry', 'Académique — feuille de notes avec la liste des élèves', { tall: true });
  await clickText('Assiduité & conseil', 'button', true);
  await waitStable(1800);
  await shot('042-academic-council', 'Académique — assiduité, période de dates et conseil de classe', { tall: true });
  await clickText('Vue de classe', 'button');
  await waitStable(1800);
  await shot('043-academic-class-overview', 'Académique — vue consolidée de la classe', { tall: true });
  await clickText('Procès-verbal', 'button', true);
  await waitStable(1300);
  await clickText('Charger', 'button');
  await waitStable(1800);
  await shot('044-academic-master-sheet', 'Académique — procès-verbal / master sheet', { tall: true });
  const batch = page.getByRole('button', { name: /Génération en lot|Batch/i });
  if (await batch.count()) {
    await batch.click(); await waitStable(700);
    await shot('045-academic-batch', 'Académique — génération de bulletins en lot', { tall: true });
  }
}

async function attendanceAdmin() {
  await go('/presence');
  const date = page.locator('input[type="date"]').first();
  if (await date.count()) await date.fill('2026-09-01');
  await selectContaining(0, 'CE1 A');
  await waitStable(2000);
  await shot('050-attendance-daily', 'Présence primaire — appel quotidien et motif facultatif', { tall: true });
  await clickText('Analyses', 'button', true);
  await waitStable(1200);
  await shot('051-attendance-analytics', 'Présence — analyses et suivi', { tall: true });
  const devices = page.getByRole('button', { name: /Lecteurs|Devices/i });
  if (await devices.count()) {
    await devices.click(); await waitStable(1200);
    await shot('052-attendance-devices', 'Présence — lecteurs biométriques et rapprochement', { tall: true });
  }
}

async function pedagogyOperations() {
  await go('/discipline');
  await shot('060-discipline-list', 'Discipline — incidents, filtres et notifications', { tall: true });
  await clickText('Nouvel incident', 'button');
  await waitStable(500);
  await shot('061-discipline-create', 'Discipline — enregistrer un incident', { tall: true });

  await go('/coursebook');
  await selectContaining(0, 'CE1 A');
  await waitStable(1000);
  await shot('062-coursebook', 'Cahier de textes — cours et devoirs de la classe', { tall: true });
  await clickText('Nouvelle entrée', 'button');
  await shot('063-coursebook-create', 'Cahier de textes — ajouter une entrée', { tall: true });

  await go('/timetable', 1500);
  await selectContaining(0, '6ème A');
  await waitStable(1800);
  await shot('064-timetable-class', 'Emploi du temps — planning d’une classe', { tall: true });
  for (const [text, file, title] of [
    ['Vue maître', '065-timetable-master', 'Emploi du temps — vue maître'],
    ['Planning des enseignants', '066-timetable-teachers', 'Emploi du temps — planning des enseignants'],
    ['Salles', '067-timetable-rooms', 'Emploi du temps — salles'],
    ['Remplacements', '068-timetable-substitutions', 'Emploi du temps — remplacements'],
    ['Règles enseignants', '069-timetable-rules', 'Emploi du temps — règles et disponibilités des enseignants'],
    ['Périodes horaires', '070-timetable-periods', 'Emploi du temps — périodes horaires'],
  ]) {
    if (await clickText(text, 'button', true)) await shot(file, title, { tall: true });
  }
}

async function communicationAndResources() {
  await go('/events');
  await shot('080-events', 'Événements — calendrier et annonces', { tall: true });
  await clickText('Nouvel événement', 'button');
  await shot('081-events-create', 'Événements — créer une annonce', { tall: true });
  await go('/messages');
  await chooseClassAndStudent('CE1 A');
  await shot('082-correspondence', 'Correspondance — fil école / parents', { tall: true });
  await clickText('Nouvelle note', 'button');
  await shot('083-correspondence-create', 'Correspondance — rédiger une note', { tall: true });
  await go('/library');
  await shot('084-library', 'Ressources — documents partagés', { tall: true });
  await go('/classkit');
  await selectContaining(0, 'CE1 A');
  await shot('085-classkit-supplies', 'Fournitures — liste de la classe', { tall: true });
  await clickText('Manuels scolaires', 'button');
  await shot('086-classkit-books', 'Manuels scolaires — liste de la classe', { tall: true });
}

async function settings() {
  await go('/settings', 1300);
  await shot('090-settings-sections', 'Configuration académique — sections', { tall: true });
  for (const [text, file, title] of [
    ['Classes', '091-settings-classes', 'Configuration académique — classes'],
    ['Matières', '092-settings-subjects', 'Configuration académique — matières'],
    ['Matières par classe', '093-settings-curriculum', 'Configuration académique — matières et enseignants par classe'],
    ['Associer classes bilingues', '094-settings-bilingual', 'Configuration — associer les classes bilingues'],
    ['Exceptions d’accès', '095-settings-academic-exceptions', 'Configuration — délégations académiques temporaires'],
    ['Évaluations', '096-settings-assessments', 'Configuration — évaluations et coefficients'],
    ['Modèles / marque', '097-settings-branding', 'Configuration — modèles de documents et identité visuelle'],
    ['Années & périodes', '098-settings-sessions', 'Configuration — années, trimestres et séquences'],
    ['Général', '099-settings-general', 'Configuration — informations générales'],
    ['Calendrier', '100-settings-calendar', 'Configuration — calendrier scolaire'],
    ['Discipline', '101-settings-discipline', 'Configuration — catalogue de discipline'],
  ]) {
    if (await clickText(text, 'button', true)) await shot(file, title, { tall: true });
  }
}

async function steering() {
  await go('/alerts');
  await shot('110-alerts', 'Alertes — risques et actions de suivi', { tall: true });
  await go('/reports');
  await shot('111-reports', 'Rapports — finances, effectifs et présence', { tall: true });
}

async function accountantFinance() {
  await login('qa.accountant.global', { all: true });
  await go('/finance');
  await shot('120-finance-overview', 'Finance — tableau de bord et historique des paiements', { tall: true });
  const receiptButton = page.locator('button[title="Reçu"],button[title="Receipt"]').first();
  if (await receiptButton.count()) {
    await receiptButton.click();
    await waitStable(900);
    await shot('120b-finance-payment-receipt', 'Finance — reçu nominatif à imprimer ou télécharger', { tall: true });
    await clickText('Fermer', 'button', true);
  }
  await clickText('Nouveau paiement', 'button');
  await shot('121-finance-payment', 'Finance — enregistrer un paiement', { tall: true });
  await page.keyboard.press('Escape').catch(() => {});
  await go('/finance/student-accounts');
  await selectContaining(0, 'CE1 A');
  await waitStable(1700);
  await shot('122-finance-student-accounts', 'Finance — rechercher les comptes élèves par classe', { tall: true });
  await selectContaining(0, 'CE2 A');
  const accountSearch = page.locator('input[aria-label="Student search"]');
  if (await accountSearch.count()) {
    await accountSearch.fill('BBC-1226');
    await clickText('Afficher', 'button', true);
  }
  await waitStable(1300);
  const accountRow = page.locator('button.result-row').filter({ hasText: /BBC-1226/ }).first();
  if (await accountRow.count()) {
    await accountRow.click();
    await waitStable(1300);
    await shot('122b-finance-student-history', 'Finance — solde et historique complet d’un élève', { tall: true, focusText: 'Tous les versements' });
    if (await clickText('Préparer le reçu consolidé', 'button', true)) {
      await waitStable(1000);
      await shot('122c-finance-consolidated-receipt', 'Finance — reçu consolidé de tous les versements', { tall: true });
      await clickText('Fermer', 'button', true);
    }
  }
  await go('/finance/treasury');
  await shot('123-finance-treasury', 'Finance — banques, caisse, dépôts, retraits et transferts', { tall: true });
  await go('/finance/fee-types');
  await shot('124-finance-fee-types', 'Finance — types de frais', { tall: true });
  await go('/finance/plans');
  await shot('125-finance-plans', 'Finance — plans de frais et échéanciers', { tall: true });
  await go('/finance/charges');
  await shot('126-finance-charges', 'Finance — génération et contrôle des créances', { tall: true });
  await go('/finance/collections');
  await shot('127-finance-collections', 'Finance — encaissement et allocation par tranche', { tall: true });
  await go('/finance/documents');
  await shot('128-finance-documents', 'Finance — factures et reçus générés', { tall: true });
  await go('/finance/payroll');
  await shot('129-finance-payroll', 'Finance — paie du personnel et bulletins', { tall: true });
  await go('/finance/accounting');
  await shot('130-finance-accounting', 'Finance — comptabilité, journaux et rapprochement', { tall: true });
  for (const [text, file, title] of [
    ['Comptes', '131-finance-chart', 'Comptabilité — plan comptable'],
    ['Mappings', '132-finance-mappings', 'Comptabilité — règles de comptabilisation'],
    ['Périodes', '133-finance-periods', 'Comptabilité — périodes et clôture'],
    ['Journaux', '134-finance-journals', 'Comptabilité — journaux'],
    ['Balance', '135-finance-trial-balance', 'Comptabilité — balance'],
    ['Grand livre', '136-finance-ledger', 'Comptabilité — grand livre'],
    ['Rapprochement', '137-finance-reconciliation', 'Comptabilité — rapprochement et exceptions'],
  ]) {
    if (await clickText(text, 'button', true)) await shot(file, title, { tall: true });
  }
  await go('/finance/reports');
  await shot('138-finance-reports', 'Finance — rapports contextualisés et export', { tall: true });
}

async function teacherExamples() {
  await login('qa.primary.fr', { all: false, level: 'primary', subsystem: 'FR' });
  await go('/presence');
  const date = page.locator('input[type="date"]').first();
  if (await date.count()) await date.fill('2026-09-01');
  await selectContaining(0, 'CE1 A');
  await waitStable(1800);
  await shot('140-primary-attendance', 'Enseignant primaire — appel de la classe bilingue', { tall: true });
  await go('/academic', 5200);
  await selectContaining(0, 'CE1 A');
  await waitStable(4500);
  await clickText('Saisie des notes', 'button', true);
  await waitStable(4200);
  await shot('141-primary-grade-entry', 'Enseignant primaire — feuille complète de notes avec élèves', { tall: true });

  await login('qa.sec.subject', { all: false, level: 'secondary', subsystem: 'FR' });
  await go('/academic', 5200);
  await selectContaining(0, '6ème A');
  await waitStable(4500);
  await shot('142-secondary-subject-grade', 'Enseignant secondaire — uniquement la matière affectée', { tall: true });

  await login('qa.sec.titulaire', { all: false, level: 'secondary', subsystem: 'FR' });
  await go('/academic', 5200);
  await selectContaining(0, '6ème A');
  await waitStable(4200);
  await shot('143-secondary-homeroom-reports', 'Titulaire secondaire — supervision des bulletins de sa classe', { tall: true });
}

async function parentPortal() {
  await login('qa.parent.local', { all: false });
  await go('/parent', 1600);
  await shot('150-parent-home', 'Espace parent — enfants rattachés', { tall: true });
  for (const [text, file, title] of [
    ['Parcours officiel', '150b-parent-journey', 'Espace parent — parcours et décisions officielles'],
    ['School life', '150c-parent-school-life', 'Espace parent — présence, discipline et messages'],
    ['Frais & paiements', '151-parent-fees', 'Espace parent — frais et paiements'],
    ['Notes', '152-parent-grades', 'Espace parent — notes'],
    ['Fournitures & manuels', '153-parent-supplies', 'Espace parent — fournitures et manuels'],
    ['Documents de l’école', '153b-parent-library', 'Espace parent — documents partagés par l’école'],
    ['Boîte à suggestions', '154-parent-suggestions', 'Espace parent — envoyer une suggestion'],
  ]) {
    if (await clickText(text, 'button', true)) await shot(file, title, { tall: true });
  }
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: CHROME_PATH });
  const context = await browser.newContext({ viewport: VIEWPORT, locale: 'fr-FR', timezoneId: 'Africa/Douala' });
  page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror', error => currentErrors.push(`JS ${clean(error.message)}`));
  page.on('response', response => {
    if (response.status() >= 500 && response.url().includes('/api/'))
      currentErrors.push(`HTTP ${response.status()} ${response.url().replace(BASE, '')}`);
  });
  try {
    await section('Connexion', async () => {
      await go('/login');
      await shot('000-login', 'Connexion à BBC SMS');
      await login('admin', { all: true });
    });
    await section('Accueil et tableau de bord', adminBasics);
    await section('Élèves', students);
    await section('Parcours, promotion, santé et documents', studentLife);
    await section('Personnel', staff);
    await section('Candidature publique', publicStaffApplication);
    await section('Académique', academicsAdmin);
    await section('Présence', attendanceAdmin);
    await section('Discipline, cahier de textes et emploi du temps', pedagogyOperations);
    await section('Communication et ressources', communicationAndResources);
    await section('Configuration hors accès/rôles/e-mail', settings);
    await section('Pilotage', steering);
    await section('Finance', accountantFinance);
    await section('Exemples enseignants', teacherExamples);
    await section('Portail parent', parentPortal);
  } finally {
    const result = {
      generatedAt: new Date().toISOString(),
      base: BASE,
      total: manifest.length,
      passed: manifest.filter(x => x.passed).length,
      failed: manifest.filter(x => !x.passed).length,
      captures: manifest,
    };
    fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(result, null, 2));
    console.log(`\n${result.passed}/${result.total} captures passed; ${result.failed} flagged.`);
    await browser.close();
    if (result.failed) process.exitCode = 1;
  }
})();
