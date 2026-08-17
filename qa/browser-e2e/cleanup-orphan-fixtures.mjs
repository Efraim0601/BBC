const API = process.env.BBC_E2E_API_URL ?? 'http://localhost:8101/api';
const MAILPIT = process.env.BBC_E2E_MAILPIT_URL ?? 'http://localhost:8125/api/v1';
const RUN_ID = `CLEANUP-${Date.now()}`;
const fixtures = JSON.parse(process.env.BBC_E2E_ORPHANS ?? '[]');

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
    throw new Error(`${method} ${url} -> HTTP ${response.status}: ${JSON.stringify(parsed).slice(0, 500)}`);
  }
  return { status: response.status, body: parsed };
}

function recipients(message) {
  return (message.To ?? message.to ?? []).map((item) =>
    typeof item === 'string' ? item : (item.Address ?? item.address ?? '')).map((value) => value.toLowerCase());
}

async function passwordFromMailpit(email) {
  const listing = await request('GET', `${MAILPIT}/messages?limit=200`, { expected: [200] });
  const messages = (listing.body?.messages ?? []).filter((message) =>
    recipients(message).includes(email.toLowerCase()) &&
    String(message.Subject ?? message.subject ?? '').toLowerCase().includes('identifiant'));
  messages.sort((a, b) => String(b.Created ?? b.created ?? '').localeCompare(String(a.Created ?? a.created ?? '')));
  const selected = messages[0];
  if (!selected) throw new Error(`no credential message found for ${email}`);
  const id = selected.ID ?? selected.id;
  const raw = await fetch(`${MAILPIT}/message/${id}/raw`);
  const text = await raw.text();
  const match = text.match(/Mot de passe\s*:\s*([^\s<]+)/i) ?? text.match(/Password\s*:\s*([^\s<]+)/i);
  if (!match) throw new Error(`credential message for ${email} had no password label`);
  return match[1];
}

async function login(username, password) {
  return (await request('POST', `${API}/auth/login`, {
    body: { username, password },
    expected: [200],
  })).body.accessToken;
}

async function main() {
  if (!fixtures.length) throw new Error('BBC_E2E_ORPHANS must contain at least one fixture object');
  const adminToken = await login('admin', process.env.BBC_E2E_ADMIN_PASSWORD ?? 'admin');
  const registrarPassword = process.env.BBC_E2E_REGISTRAR_PASSWORD
    ?? await passwordFromMailpit('registrar@bbc-e2e.example.test');
  const registrarToken = await login('registraire.elodie.nkoue', registrarPassword);

  const me = await request('GET', `${API}/auth/me`, { token: adminToken, expected: [200] });
  const adminId = me.body.id;
  const workspace = await request('GET', `${API}/access/users/${adminId}`, { token: adminToken, expected: [200] });
  const original = (workspace.body.overrides ?? []).map((rule) => ({
    actionCode: rule.actionCode, effect: rule.effect, scopeMode: rule.scopeMode,
    scopePayload: rule.scopePayload, effectiveFrom: rule.effectiveFrom,
    effectiveTo: rule.effectiveTo, permanent: rule.permanent, reason: rule.reason,
  }));
  const mutation = {
    expectedPolicyVersion: workspace.body.policyVersion,
    reason: `Section 28.3 orphan cleanup ${RUN_ID}`,
    rules: [...original, {
      actionCode: 'STUDENT_PROFILE_DEACTIVATE', effect: 'ALLOW', scopeMode: 'CLASS_SET',
      scopePayload: { classIds: [...new Set(fixtures.map((fixture) => fixture.classId).filter(Boolean))] },
      effectiveFrom: null, effectiveTo: null, permanent: true,
      reason: `Section 28.3 orphan cleanup ${RUN_ID}`,
    }],
    confirmHighRisk: true, separationOfDutiesOverride: false, separationOfDutiesReason: null,
  };
  await request('POST', `${API}/access/users/${adminId}/preview`, { token: adminToken, body: mutation, expected: [200] });
  await request('PUT', `${API}/access/users/${adminId}`, { token: adminToken, body: mutation, expected: [200] });

  const results = [];
  try {
    for (const fixture of fixtures) {
      const end = await request('DELETE', `${API}/student-guardian-relationships/${fixture.relationshipId}?reason=${encodeURIComponent(`Section 28.3 orphan cleanup ${RUN_ID}`)}`, {
        token: registrarToken, expected: [204, 404],
      });
      const guardian = await request('POST', `${API}/guardians/${fixture.guardianId}/deactivate`, {
        token: registrarToken, body: { reason: `Section 28.3 orphan cleanup ${RUN_ID}` }, expected: [200, 204, 404],
      });
      const student = await request('DELETE', `${API}/students/${fixture.studentId}`, {
        token: adminToken, expected: [204, 404],
      });
      results.push({ studentId: fixture.studentId, endRelationship: end.status, deactivateGuardian: guardian.status, softDeleteStudent: student.status });
    }
  } finally {
    const current = await request('GET', `${API}/access/users/${adminId}`, { token: adminToken, expected: [200] });
    const restore = {
      expectedPolicyVersion: current.body.policyVersion,
      reason: `Section 28.3 restore orphan cleanup policy ${RUN_ID}`,
      rules: original, confirmHighRisk: true, separationOfDutiesOverride: false, separationOfDutiesReason: null,
    };
    const restored = await request('PUT', `${API}/access/users/${adminId}`, { token: adminToken, body: restore, expected: [200] });
    console.log(JSON.stringify({ results, policyRestore: restored.status }, null, 2));
  }
}

await main();
