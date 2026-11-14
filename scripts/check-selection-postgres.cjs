// Destructive test fixtures are limited to the project's disposable local PostgreSQL container.
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');

const base = process.argv[2] || 'http://127.0.0.1:8080';
const address = new URL(base);
assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(address.hostname), 'API must be local');
assert.equal(process.env.REPAIRMATCH_DISPOSABLE_DB, '1', 'Set REPAIRMATCH_DISPOSABLE_DB=1 for the dedicated test database');

const container = 'repairmatch-backend-postgres-1';
const labels = execFileSync('docker', ['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}/{{index .Config.Labels "com.docker.compose.service"}}', container], { encoding: 'utf8' }).trim();
assert.equal(labels, 'repairmatch-backend/postgres', 'Refusing to alter another PostgreSQL container');

function sql(statement) {
  return execFileSync('docker', ['exec', '-i', container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'repairmatch', '-d', 'repairmatch'],
    { input: statement, encoding: 'utf8' });
}

async function api(method, route, token, body, expected) {
  const response = await fetch(base + route, {
    method,
    signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
  const content = await response.text();
  const data = content ? JSON.parse(content) : null;
  if (expected !== undefined) assert.equal(response.status, expected, `${method} ${route}: ${content}`);
  return { status: response.status, data };
}

async function account(role, runId) {
  const email = `${role.toLowerCase()}-${runId}-${Math.random().toString(16).slice(2)}@example.com`;
  const password = 'LocalSelection2026!';
  const { data: user } = await api('POST', '/api/auth/register', null,
    { name: `Selection ${role}`, email, password, role }, 201);
  const { data: login } = await api('POST', '/api/auth/login', null, { email, password }, 200);
  return { id: user.id, token: login.accessToken };
}

async function fixture(runId) {
  const owner = await account('CLIENT', runId);
  const first = await account('TECHNICIAN', runId);
  const second = await account('TECHNICIAN', runId);
  const { data: catalog } = await api('GET', '/api/appliance-types', owner.token, null, 200);
  const type = catalog[0].id;
  for (const technician of [first, second]) {
    await api('PUT', '/api/technicians/me/profile', technician.token,
      { experienceYears: 3, bio: 'PostgreSQL selection test', applianceTypeIds: [type] }, 200);
    await api('PUT', '/api/technicians/me/service-areas', technician.token,
      { latitude: -12.0464, longitude: -77.0428, maxRadiusKm: 20 }, 200);
  }
  const { data: request } = await api('POST', '/api/requests', owner.token,
    { applianceTypeId: type, originalDescription: 'PostgreSQL selection test', brand: 'Fixture', model: 'One', latitude: -12.0464, longitude: -77.0428 }, 201);
  const availableAt = new Date(Date.now() + 86400000).toISOString().slice(0, 19);
  const proposalIds = [];
  for (const technician of [first, second]) {
    const { data: proposal } = await api('POST', `/api/requests/${request.id}/proposals`, technician.token,
      { diagnosticCost: 50, availableAt, conditions: 'Local test' }, 201);
    proposalIds.push(proposal.id);
  }
  return { owner, requestId: request.id, proposalIds };
}

async function checkConcurrency(runId) {
  const { owner, requestId, proposalIds } = await fixture(runId);
  const results = await Promise.all(proposalIds.map(id => api('POST', `/api/proposals/${id}/accept`, owner.token)));
  assert.deepEqual(results.map(result => result.status).sort(), [201, 409]);
  const { data: services } = await api('GET', '/api/services', owner.token, null, 200);
  assert.equal(services.length, 1);
  const { data: proposals } = await api('GET', `/api/requests/${requestId}/proposals`, owner.token, null, 200);
  assert.deepEqual(proposals.map(proposal => proposal.status).sort(), ['ACCEPTED', 'REJECTED']);
  console.log('PASS concurrent acceptance: one contract, one conflict');
}

async function checkRollback(runId) {
  const { owner, requestId, proposalIds } = await fixture(runId);
  // A trigger fails only this synthetic request after the service INSERT is attempted.
  sql(`BEGIN; CREATE OR REPLACE FUNCTION repairmatch_test_reject_contract() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN IF NEW.request_id = ${requestId} THEN RAISE EXCEPTION 'selection rollback test'; END IF; RETURN NEW; END $$;
    CREATE TRIGGER repairmatch_test_reject_contract BEFORE INSERT ON service_contracts
    FOR EACH ROW EXECUTE FUNCTION repairmatch_test_reject_contract(); COMMIT;`);
  try {
    const result = await api('POST', `/api/proposals/${proposalIds[0]}/accept`, owner.token);
    assert.ok(result.status >= 400, `Expected failed selection, got ${result.status}`);
    const { data: request } = await api('GET', `/api/requests/${requestId}`, owner.token, null, 200);
    assert.equal(request.status, 'CON_PROPUESTAS');
    const { data: proposals } = await api('GET', `/api/requests/${requestId}/proposals`, owner.token, null, 200);
    assert.deepEqual(proposals.map(proposal => proposal.status), ['PENDING', 'PENDING']);
    const { data: services } = await api('GET', '/api/services', owner.token, null, 200);
    assert.equal(services.length, 0);
    console.log('PASS failed contract insert: request and proposals rolled back');
  } finally {
    sql('DROP TRIGGER IF EXISTS repairmatch_test_reject_contract ON service_contracts; DROP FUNCTION IF EXISTS repairmatch_test_reject_contract();');
  }
}

(async () => {
  const runId = Date.now();
  await checkConcurrency(runId);
  await checkRollback(runId);
})().catch(error => { console.error(error.message); process.exitCode = 1; });
