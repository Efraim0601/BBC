import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';

const UI = process.env.BBC_E2E_UI_URL ?? 'http://localhost:8100';
const API = process.env.BBC_E2E_API_URL ?? 'http://localhost:8101/api';
const MAILPIT = process.env.BBC_E2E_MAILPIT_URL ?? 'http://localhost:8125/api/v1';
const RUN_ID = (process.env.BBC_E2E_RUN_ID ?? `BROWSER-${Date.now()}-${randomBytes(3).toString('hex')}`)
  .replace(/[^A-Za-z0-9-]/g, '-')
  .slice(0, 48);
const OUTPUT = process.env.BBC_E2E_OUTPUT ?? resolve(
  import.meta.dirname,
  '../e2e-runs/2026-08-14-full-school/final/gate14-browser-e2e-20260816.json',
);
const CHROME_CANDIDATES = [
  process.env.BBC_E2E_CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);

const CLASS_NAME = 'PRI-FR-CE1-A';
const PERSONAS = [
  {
    id: 'bootstrap-admin',
    username: 'admin',
    password: process.env.BBC_E2E_ADMIN_PASSWORD ?? 'admin',
    scope: 'all',
    positive: '/students/new',
    forbidden: '/parent',
    expectedPositive: '/students/new',
    expectedForbidden: '/apps',
  },
  {
    id: 'primary-teacher',
    username: 'francois.mbarga',
    email: 'teacher.pri.fr@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_PRIMARY_PASSWORD',
    scope: { level: 'primary', section: 'Francophone' },
    positive: '/presence',
    forbidden: '/finance',
    expectedPositive: '/presence',
    expectedForbidden: '/apps',
  },
  {
    id: 'secondary-subject',
    username: 'jean.paul.njoya',
    email: 'teacher.sec.fr@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_SECONDARY_SUBJECT_PASSWORD',
    scope: { level: 'secondary', section: 'Francophone' },
    positive: '/academic',
    forbidden: '/finance',
    expectedPositive: '/academic',
    expectedForbidden: '/apps',
  },
  {
    id: 'secondary-titular',
    username: 'samuel.tchana',
    email: 'teacher.sec.sci@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_SECONDARY_TITULAR_PASSWORD',
    scope: { level: 'secondary', section: 'Francophone' },
    positive: '/timetable',
    forbidden: '/finance',
    expectedPositive: '/timetable',
    expectedForbidden: '/apps',
  },
  {
    id: 'direction',
    username: 'direction.a',
    email: 'direction@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_DIRECTION_PASSWORD',
    scope: 'all',
    positive: '/academic',
    forbidden: '/students/new',
    expectedPositive: '/academic',
    expectedForbidden: '/apps',
  },
  {
    id: 'registrar',
    username: 'registraire.elodie.nkoue',
    email: 'registrar@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_REGISTRAR_PASSWORD',
    scope: 'all',
    positive: '/students/new',
    forbidden: '/academic',
    expectedPositive: '/students/new',
    expectedForbidden: '/apps',
  },
  {
    id: 'accountant',
    username: 'comptable.a',
    email: 'accountant@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_ACCOUNTANT_PASSWORD',
    scope: 'all',
    positive: '/finance',
    forbidden: '/academic',
    expectedPositive: '/finance',
    expectedForbidden: '/apps',
  },
  {
    id: 'cashier',
    username: 'caissier.a',
    email: 'cashier@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_CASHIER_PASSWORD',
    scope: 'all',
    positive: '/finance/collections',
    forbidden: '/academic',
    expectedPositive: '/finance/collections',
    expectedForbidden: '/apps',
  },
  {
    id: 'bursar',
    username: 'econome.a',
    email: 'bursar@bbc-e2e.example.test',
    passwordEnv: 'BBC_E2E_BURSAR_PASSWORD',
    scope: 'all',
    positive: '/finance/plans',
    forbidden: '/academic',
    expectedPositive: '/finance/plans',
    expectedForbidden: '/apps',
  },
];

const ROUTE_INDEX = [
  '/login', '/parcours', '/students', '/students/new', '/students/import-family',
  '/students/:id', '/journey', '/journey/promotions', '/health', '/documents',
  '/staff', '/academic', '/presence', '/discipline', '/coursebook', '/finance',
  '/finance/fee-types', '/finance/plans', '/finance/charges', '/finance/collections',
  '/finance/documents', '/finance/payroll', '/finance/accounting', '/finance/reports',
  '/timetable', '/events', '/messages', '/classkit', '/dashboard', '/alerts',
  '/reports', '/settings', '/access-control', '/parent', '/apps',
];

function compact(value) {
  if (typeof value === 'string') return value.slice(0, 500);
  if (value === undefined) return value;
  try { return JSON.stringify(value).slice(0, 1000); } catch { return String(value); }
}

async function request(method, url, { token, body, expected = null } = {}) {
  const response = await fetch(url, {
    method,
    headers: {
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json; charset=UTF-8' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const raw = await response.text();
  let parsed = null;
  if (raw) {
    try { parsed = JSON.parse(raw); } catch { parsed = raw.slice(0, 500); }
  }
  if (expected && !expected.includes(response.status)) {
    throw new Error(`${method} ${url} -> HTTP ${response.status}: ${compact(parsed)}`);
  }
  return { status: response.status, body: parsed };
}

async function loginApi(username, password) {
  const result = await request('POST', `${API}/auth/login`, {
    body: { username, password },
    expected: [200],
  });
  if (!result.body?.accessToken) throw new Error(`login response had no access token for ${username}`);
  return result.body;
}

function recipientAddresses(message) {
  return (message.To ?? message.to ?? []).map((item) =>
    typeof item === 'string' ? item : (item.Address ?? item.address ?? '')).map((value) => value.toLowerCase());
}

async function passwordFromMailpit(email, envName) {
  if (process.env[envName]) return process.env[envName];
  const listing = await request('GET', `${MAILPIT}/messages?limit=200`, { expected: [200] });
  const messages = (listing.body?.messages ?? []).filter((message) =>
    recipientAddresses(message).includes(email.toLowerCase()) &&
    String(message.Subject ?? message.subject ?? '').toLowerCase().includes('identifiant'));
  messages.sort((a, b) => String(b.Created ?? b.created ?? '').localeCompare(String(a.Created ?? a.created ?? '')));
  const selected = messages[0];
  if (!selected) throw new Error(`no Mailpit credential message found for ${email}`);
  const id = selected.ID ?? selected.id;
  const rawResponse = await fetch(`${MAILPIT}/message/${id}/raw`);
  if (!rawResponse.ok) throw new Error(`Mailpit raw credential read failed for ${email}: HTTP ${rawResponse.status}`);
  const text = await rawResponse.text();
  const match = text.match(/Mot de passe\s*:\s*([^\s<]+)/i) ?? text.match(/Password\s*:\s*([^\s<]+)/i);
  if (!match) throw new Error(`Mailpit credential message for ${email} had no password label`);
  return match[1];
}

async function provisionDisposableFixture(registrarToken) {
  const options = await request('GET', `${API}/students/class-options`, {
    token: registrarToken,
    expected: [200],
  });
  const classRow = (options.body ?? []).find((item) => item.name === CLASS_NAME);
  if (!classRow?.id) throw new Error(`class option ${CLASS_NAME} was not available`);

  const suffix = randomBytes(4).toString('hex');
  const parentEmail = `${RUN_ID.toLowerCase()}-${suffix}@bbc-e2e.example.test`;
  const parentPassword = `E2eParent!${suffix}`;
  const registration = await request('POST', `${API}/student-registrations`, {
    token: registrarToken,
    expected: [201],
    body: {
      student: {
        firstName: 'Browser',
        lastName: `E2E-${suffix}`,
        niu: `BROWSER-${suffix}`,
        sex: 'F',
        dob: '2018-09-09',
        birthplace: 'Maroua',
        repeats: false,
        classId: classRow.id,
        className: null,
        subsystem: null,
        level: null,
        parentName: null,
        parentPhone: null,
        fatherName: null,
        fatherPhone: null,
        fatherEmail: null,
        motherName: null,
        motherPhone: null,
        motherEmail: null,
        guardianName: null,
        guardianPhone: null,
        guardianEmail: null,
        guardianRelation: null,
      },
      guardians: [{
        guardianId: null,
        displayName: `${RUN_ID} Parent`,
        email: parentEmail,
        phone: `+23769${String(Math.floor(1000000 + Math.random() * 8999999))}`,
        relationshipType: 'PARENT',
        accessMode: 'CREATE_ACCOUNT',
        initialPassword: parentPassword,
        legalGuardian: true,
        livesWith: true,
        emergencyPriority: 1,
        pickupAuthorized: true,
        financeResponsible: true,
        receivesAcademic: true,
        receivesAttendance: true,
        receivesFinance: true,
        receivesDiscipline: true,
        receivesHealth: true,
        portalAccess: true,
        notes: `Section 28.3 disposable fixture ${RUN_ID}`,
      }],
    },
  });
  const studentId = registration.body?.student?.id;
  const relationship = registration.body?.guardians?.[0];
  if (!studentId || !relationship?.relationshipId || !relationship.guardianId) {
    throw new Error('disposable registration did not return student and guardian IDs');
  }
  return {
    studentId,
    studentDisplayName: `E2E-${suffix.toUpperCase()} Browser`,
    classId: classRow.id,
    relationshipId: relationship.relationshipId,
    guardianId: relationship.guardianId,
    parentUsername: parentEmail,
    parentPassword,
    className: CLASS_NAME,
  };
}

async function cleanupDisposableFixture(registrarToken, adminToken, fixture) {
  const cleanup = [];
  if (!fixture) return cleanup;
  const attempt = async (action, operation) => {
    try {
      const result = await operation();
      cleanup.push({ action, status: result.status });
    } catch (error) {
      cleanup.push({ action, error: { type: error?.constructor?.name ?? 'Error', message: compact(error?.message ?? String(error)) } });
    }
  };
  if (fixture.relationshipId) {
    await attempt('end-relationship', () => request('DELETE', `${API}/student-guardian-relationships/${fixture.relationshipId}?reason=${encodeURIComponent(`Section 28.3 cleanup ${RUN_ID}`)}`, {
      token: registrarToken,
      expected: [204, 404],
    }));
  }
  if (fixture.guardianId) {
    await attempt('deactivate-guardian', () => request('POST', `${API}/guardians/${fixture.guardianId}/deactivate`, {
      token: registrarToken,
      body: { reason: `Section 28.3 cleanup ${RUN_ID}` },
      expected: [200, 204, 404],
    }));
  }
  if (fixture.studentId) {
    await attempt('soft-delete-student', () => request('DELETE', `${API}/students/${fixture.studentId}`, {
      token: adminToken,
      expected: [204, 404],
    }));
  }
  return cleanup;
}

async function grantTemporaryAdminAction(adminToken, actionCode, scopeMode = 'SCHOOL_ALL', scopePayload = null) {
  const me = await request('GET', `${API}/auth/me`, { token: adminToken, expected: [200] });
  const adminId = me.body?.id;
  if (!adminId) throw new Error('admin cleanup identity did not return an id');
  const workspace = await request('GET', `${API}/access/users/${adminId}`, { token: adminToken, expected: [200] });
  const original = (workspace.body?.overrides ?? []).map((rule) => ({
    actionCode: rule.actionCode,
    effect: rule.effect,
    scopeMode: rule.scopeMode,
    scopePayload: rule.scopePayload,
    effectiveFrom: rule.effectiveFrom,
    effectiveTo: rule.effectiveTo,
    permanent: rule.permanent,
    reason: rule.reason,
  }));
  const rule = {
    actionCode,
    effect: 'ALLOW',
    scopeMode,
    scopePayload,
    effectiveFrom: null,
    effectiveTo: null,
    permanent: true,
    reason: `Section 28.3 disposable cleanup ${RUN_ID}`,
  };
  const mutation = {
    expectedPolicyVersion: workspace.body.policyVersion,
    reason: `Section 28.3 temporary cleanup authority ${RUN_ID}`,
    rules: [...original, rule],
    confirmHighRisk: true,
    separationOfDutiesOverride: false,
    separationOfDutiesReason: null,
  };
  await request('POST', `${API}/access/users/${adminId}/preview`, { token: adminToken, body: mutation, expected: [200] });
  const applied = await request('PUT', `${API}/access/users/${adminId}`, { token: adminToken, body: mutation, expected: [200] });
  return { adminId, original, policyVersion: applied.body?.policyVersion };
}

async function restoreAdminPolicy(adminToken, state) {
  if (!state?.adminId) return { status: 'NOT_ATTEMPTED' };
  const workspace = await request('GET', `${API}/access/users/${state.adminId}`, { token: adminToken, expected: [200] });
  const body = {
    expectedPolicyVersion: workspace.body.policyVersion,
    reason: `Section 28.3 restore admin cleanup policy ${RUN_ID}`,
    rules: state.original,
    confirmHighRisk: true,
    separationOfDutiesOverride: false,
    separationOfDutiesReason: null,
  };
  const restored = await request('PUT', `${API}/access/users/${state.adminId}`, { token: adminToken, body, expected: [200] });
  return { status: restored.status, policyVersion: restored.body?.policyVersion };
}

function levelPattern(level) {
  if (level === 'primary') return /Primaire|Primary/i;
  if (level === 'secondary') return /Secondaire|Secondary/i;
  return /Maternelle|Kindergarten/i;
}

function isExternalFontNoise(responseUrl) {
  const url = new URL(responseUrl);
  return (url.hostname === 'fonts.gstatic.com' && /\.(woff2?|ttf|otf)$/i.test(url.pathname))
    || (url.hostname === 'localhost' && /^\/s\/manrope\/.*\.(woff2?|ttf|otf)$/i.test(url.pathname));
}

function isExpectedOptionalParentBulletin(responseUrl) {
  const url = new URL(responseUrl);
  return url.hostname === 'localhost'
    && /^\/api\/parent\/children\/[^/]+\/bulletins\/latest$/.test(url.pathname);
}

/**
 * The route index deliberately opens every stable route under every persona.
 * Some of those routes are shell-reachable through the legacy module matrix,
 * while their optional data panels are correctly protected by a narrower V2
 * action.  Keep those denials visible in the row evidence, but do not mistake
 * an expected authorization boundary for an application failure.  The list is
 * intentionally explicit so a new 403 still fails the audit and requires a
 * disposition.
 */
function isExpectedProtectedOptional(responseUrl, status = 403) {
  if (status !== 403) return false;
  const url = new URL(responseUrl);
  if (url.hostname !== 'localhost') return false;
  return [
    /^\/api\/alerts$/,
    /^\/api\/enrollments\/students\/[^/]+$/,
    /^\/api\/finance\/v2\/(charges|documents|reports\/context)$/,
    /^\/api\/finance\/v2\/payroll\/(components|payment-options|payslips|periods|runs)$/,
    /^\/api\/official-documents$/,
    /^\/api\/reports\/(attendance\/monthly|demographics)$/,
    /^\/api\/settings\/(academic-sessions|discipline-catalog|permission-actions)$/,
    /^\/api\/setup\/(classes|subjects)$/,
    /^\/api\/students\/class-options$/,
    /^\/api\/students\/[^/]+(?:\/guardians|\/photo)?$/,
    /^\/api\/finance\/v2\/accounting\/(accounts|journals|periods|posting-rules)$/,
  ].some((pattern) => pattern.test(url.pathname));
}

function isExpectedProtectedRequestFailure(requestUrl) {
  const url = new URL(requestUrl);
  return (url.hostname === 'localhost'
    && /^\/api\/finance\/v2\/accounting\/(accounts|journals|periods|posting-rules)$/.test(url.pathname))
    || (url.hostname === 'localhost'
      && /^\/api\/finance\/v2\/payroll\/(components|payment-options|payslips|periods|runs)$/.test(url.pathname));
}

function isExpectedMissingOptionalPhoto(responseUrl, status = 404) {
  if (status !== 404) return false;
  const url = new URL(responseUrl);
  return url.hostname === 'localhost' && /^\/api\/staff\/[^/]+\/photo$/.test(url.pathname);
}

function isExpectedMissingTeacherSchedule(responseUrl, status = 400) {
  if (status !== 400) return false;
  const url = new URL(responseUrl);
  return url.hostname === 'localhost' && url.pathname === '/api/timetable/teachers/me';
}

async function chooseScope(page, scope) {
  if (scope === 'all') {
    const all = page.getByRole('button', { name: /Tous les parcours|All parcours/i });
    if (await all.count()) {
      await all.first().click();
    } else {
      await page.getByRole('button', { name: /Primaire|Primary/i }).first().click();
      await page.locator('button').filter({ hasText: /Francophone/ }).last().click();
    }
  } else {
    await page.getByRole('button', { name: levelPattern(scope.level) }).first().click();
    await page.locator('button').filter({ hasText: new RegExp(scope.section, 'i') }).last().click();
  }
  try {
    await page.waitForURL(/\/apps(?:$|\?)|\/parent(?:$|\?)/, { timeout: 15000 });
  } catch (error) {
    const buttons = await page.getByRole('button').allTextContents().catch(() => []);
    throw new Error(`scope selection did not settle: path=${new URL(page.url()).pathname}, buttons=${compact(buttons)}, cause=${error.message}`);
  }
}

async function settle(page) {
  await page.waitForLoadState('domcontentloaded').catch(() => undefined);
  await page.waitForTimeout(900);
}

async function loginUi(page, persona) {
  await page.goto(`${UI}/login`, { waitUntil: 'domcontentloaded', timeout: 20000 });
  await page.locator('input[name="username"]').fill(persona.username);
  await page.locator('input[name="password"]').fill(persona.password);
  await page.getByRole('button', { name: /Se connecter|Sign in/i }).click();
  await page.waitForTimeout(1200);
  const afterLoginPath = new URL(page.url()).pathname;
  if (!['/parcours', '/parent'].includes(afterLoginPath)) {
    const body = await page.locator('body').innerText().catch(() => '');
    throw new Error(`UI login did not settle for ${persona.id}: path=${afterLoginPath}, body=${compact(body)}`);
  }
  await settle(page);
  if (new URL(page.url()).pathname === '/parcours') await chooseScope(page, persona.scope);
  await settle(page);
}

async function visit(page, path, expectedPath, diagnostics) {
  const before = diagnostics.length;
  await page.goto(`${UI}${path}`, { waitUntil: 'domcontentloaded', timeout: 20000 });
  await settle(page);
  const actualPath = new URL(page.url()).pathname;
  const bodyLength = await page.locator('body').innerText().then((text) => text.length).catch(() => 0);
  const newDiagnostics = diagnostics.slice(before);
  const blockingDiagnostics = newDiagnostics.filter((item) => !item.tolerated);
  const result = {
    requestedPath: path,
    expectedPath,
    actualPath,
    bodyLength,
    diagnosticCount: blockingDiagnostics.length,
    toleratedDiagnosticCount: newDiagnostics.length - blockingDiagnostics.length,
    diagnostics: newDiagnostics.slice(0, 8),
    pass: actualPath === expectedPath && blockingDiagnostics.length === 0,
  };
  if (!result.pass) throw new Error(`${path} expected ${expectedPath}, got ${actualPath}, diagnostics=${JSON.stringify(blockingDiagnostics.slice(0, 8))}`);
  return result;
}

async function observeRoute(page, requestedPath, diagnostics, fixture) {
  const path = requestedPath === '/students/:id' ? `/students/${fixture.studentId}` : requestedPath;
  const before = diagnostics.length;
  let navigationError = null;
  try {
    await page.goto(`${UI}${path}`, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await settle(page);
  } catch (error) {
    navigationError = { type: error?.constructor?.name ?? 'Error', message: compact(error?.message ?? String(error)) };
  }
  const actualPath = new URL(page.url()).pathname;
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const routeDiagnostics = diagnostics.slice(before);
  const blockingDiagnostics = routeDiagnostics.filter((item) => !item.tolerated);
  return {
    requestedPath,
    navigatedPath: path,
    actualPath,
    bodyLength: bodyText.length,
    navigationError,
    diagnosticCount: blockingDiagnostics.length,
    toleratedDiagnosticCount: routeDiagnostics.length - blockingDiagnostics.length,
    diagnostics: routeDiagnostics.slice(0, 20),
  };
}

async function runRouteIndex(page, fixture, diagnostics) {
  const rows = [];
  for (const requestedPath of ROUTE_INDEX) {
    rows.push(await observeRoute(page, requestedPath, diagnostics, fixture));
  }
  return rows;
}

function chromePath() {
  return CHROME_CANDIDATES.find((candidate) => existsSync(candidate));
}

async function runPersona(browser, persona, fixture, storageRoot) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'fr-FR' });
  const diagnostics = [];
  const page = await context.newPage();
  page.on('console', (message) => {
    if ((message.type() === 'error' || message.type() === 'warning') && !message.text().includes('Failed to load resource')) {
      diagnostics.push({ type: `console-${message.type()}`, text: compact(message.text()) });
    }
  });
  page.on('pageerror', (error) => diagnostics.push({ type: 'pageerror', text: compact(error.message) }));
  page.on('requestfailed', (request) => {
    const tolerated = isExternalFontNoise(request.url());
    const protectedOptional = isExpectedProtectedRequestFailure(request.url());
    const url = new URL(request.url());
    diagnostics.push({
      type: 'requestfailed',
      method: request.method(),
      url: url.pathname,
      host: url.hostname,
      tolerated: tolerated || protectedOptional,
      classification: tolerated
        ? 'external-font-asset-noise'
        : protectedOptional ? 'expected-protected-optional-request-failure' : undefined,
    });
  });
  page.on('response', (response) => {
    if (response.status() >= 400) {
      const externalFont = isExternalFontNoise(response.url());
      const optionalParentBulletin = isExpectedOptionalParentBulletin(response.url()) && response.status() === 404;
      const protectedOptional = isExpectedProtectedOptional(response.url(), response.status());
      const missingOptionalPhoto = isExpectedMissingOptionalPhoto(response.url(), response.status());
      const missingTeacherSchedule = isExpectedMissingTeacherSchedule(response.url(), response.status());
      const url = new URL(response.url());
      diagnostics.push({
        type: response.status() >= 500 ? 'http-5xx' : 'http-4xx',
        status: response.status(),
        method: response.request().method(),
        url: url.pathname,
        host: url.hostname,
        tolerated: externalFont || optionalParentBulletin || protectedOptional || missingOptionalPhoto || missingTeacherSchedule,
        classification: externalFont
          ? 'external-font-asset-noise'
          : optionalParentBulletin ? 'expected-optional-no-published-bulletin'
          : protectedOptional ? 'expected-protected-optional-role-boundary'
          : missingOptionalPhoto ? 'expected-optional-missing-profile-photo'
          : missingTeacherSchedule ? 'expected-optional-no-teacher-identity'
          : undefined,
      });
    }
  });
  try {
    await loginUi(page, persona);
    const statePath = join(storageRoot, `${persona.id}.json`);
    await context.storageState({ path: statePath });
    const positive = await visit(page, persona.positive, persona.expectedPositive, diagnostics);
    const positiveBody = await page.locator('body').innerText().catch(() => '');
    const forbidden = await visit(page, persona.forbidden, persona.expectedForbidden, diagnostics);
    const parentReadback = persona.id === 'parent'
      ? {
          linkedChildText: positiveBody.includes(fixture.studentDisplayName),
          linkedChildName: fixture.studentDisplayName,
        }
      : undefined;
    const routeIndex = process.env.BBC_E2E_ROUTE_INDEX === '1'
      ? await runRouteIndex(page, fixture, diagnostics)
      : undefined;
    return {
      persona: persona.id,
      storageStateCreated: true,
      positive,
      forbidden,
      parentReadback,
      routeIndex,
      diagnostics: diagnostics.slice(0, 20),
      toleratedDiagnosticCount: diagnostics.filter((item) => item.tolerated).length,
      pass: true,
    };
  } finally {
    await context.close();
  }
}

async function main() {
  const startedAt = new Date().toISOString();
  const report = {
    runId: RUN_ID,
    startedAt,
    stack: { ui: UI, api: API, mailpit: MAILPIT },
    scope: 'Section 28.3 core browser persona/session/route slice; nurse excluded',
    routeIndexEnabled: process.env.BBC_E2E_ROUTE_INDEX === '1',
    fixture: null,
    personas: [],
    cleanup: null,
    pass: false,
  };

  let registrarToken;
  let adminToken;
  let fixture;
  let browser;
  const storageRoot = join(tmpdir(), `bbc-sms-browser-e2e-${RUN_ID}`);
  await mkdir(storageRoot, { recursive: true });
  try {
    const registrarPassword = await passwordFromMailpit('registrar@bbc-e2e.example.test', 'BBC_E2E_REGISTRAR_PASSWORD');
    registrarToken = (await loginApi('registraire.elodie.nkoue', registrarPassword)).accessToken;
    adminToken = (await loginApi('admin', process.env.BBC_E2E_ADMIN_PASSWORD ?? 'admin')).accessToken;
    fixture = await provisionDisposableFixture(registrarToken);
    fixture.parentDisplayName = `${RUN_ID} Parent`;
    report.fixture = {
      prefix: RUN_ID,
      studentId: fixture.studentId,
      studentDisplayName: fixture.studentDisplayName,
      classId: fixture.classId,
      relationshipId: fixture.relationshipId,
      guardianId: fixture.guardianId,
      className: fixture.className,
    };

    const parent = {
      id: 'parent',
      username: fixture.parentUsername,
      password: fixture.parentPassword,
      scope: null,
      positive: '/parent',
      forbidden: '/students',
      expectedPositive: '/parent',
      expectedForbidden: '/apps',
    };
    const requestedPersonas = process.env.BBC_E2E_PERSONAS
      ? new Set(process.env.BBC_E2E_PERSONAS.split(',').map((item) => item.trim()).filter(Boolean))
      : null;
    const personas = [...PERSONAS, parent].filter((persona) => !requestedPersonas || requestedPersonas.has(persona.id));
    if (!personas.length) throw new Error('BBC_E2E_PERSONAS selected no known persona');
    for (const base of personas) {
      const persona = { ...base };
      if (!persona.password) persona.password = await passwordFromMailpit(persona.email, persona.passwordEnv);
      browser ??= await chromium.launch({ headless: true, executablePath: chromePath(), args: ['--no-sandbox', '--disable-dev-shm-usage'] });
      report.personas.push(await runPersona(browser, persona, fixture, storageRoot));
    }
    report.pass = report.personas.length === personas.length && report.personas.every((item) => item.pass);
  } catch (error) {
    report.error = { type: error?.constructor?.name ?? 'Error', message: compact(error?.message ?? String(error)) };
  } finally {
    if (browser) await browser.close();
    if (registrarToken) {
      try {
        const cleanupPolicy = fixture ? await grantTemporaryAdminAction(
          adminToken,
          'STUDENT_PROFILE_DEACTIVATE',
          'CLASS_SET',
          fixture.classId ? { classIds: [fixture.classId] } : null,
        ) : null;
        report.cleanup = await cleanupDisposableFixture(registrarToken, adminToken, fixture);
        report.cleanupPolicyRestore = await restoreAdminPolicy(adminToken, cleanupPolicy);
      } catch (error) {
        report.cleanup = { error: { type: error?.constructor?.name ?? 'Error', message: compact(error?.message ?? String(error)) } };
        report.pass = false;
      }
    }
    report.finishedAt = new Date().toISOString();
    await mkdir(resolve(OUTPUT, '..'), { recursive: true });
    await writeFile(OUTPUT, JSON.stringify(report, null, 2) + '\n', 'utf8');
  }

  console.log(JSON.stringify({
    runId: report.runId,
    pass: report.pass,
    personaCount: report.personas.length,
    personaPassCount: report.personas.filter((item) => item.pass).length,
    routeIndexRows: report.personas.reduce((sum, item) => sum + (item.routeIndex?.length ?? 0), 0),
    routeIndexBlockingDiagnostics: report.personas.reduce((sum, item) =>
      sum + (item.routeIndex ?? []).reduce((inner, row) => inner + row.diagnosticCount, 0), 0),
    fixturePrefix: report.fixture?.prefix,
    cleanup: report.cleanup,
    output: OUTPUT,
    error: report.error,
  }, null, 2));
  if (!report.pass) process.exitCode = 1;
}

await main();
