const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
function script(name) {
  const yaml = readFileSync(join(__dirname, '../.github/workflows', name + '.yml'), 'utf8').replace(/\r\n/g, '\n');
  const block = yaml.split('          script: |\n')[1].split('\n');
  const end = block.findIndex(line => line.trim() && !line.startsWith('            '));
  return block.slice(0, end < 0 ? undefined : end).map(line => line.replace(/^ {12}/, '')).join('\n');
}
const labels = names => names.map(name => ({ name }));
async function contract(body, approved = true, title = 'feat(proposals): add acceptance') {
  let failure = '';
  const context = { repo: { owner: 'Sebas-Leo', repo: 'repairmatch-backend' }, payload: { pull_request: { title, body } } };
  const github = { rest: { issues: { get: async args => {
    assert.equal(args.owner, 'Sebas-Leo');
    assert.equal(args.repo, 'repairmatch-backend');
    assert.equal(args.issue_number, 1);
    return { data: { labels: labels(approved ? ['status:approved'] : []) } };
  } } } };
  await new AsyncFunction('context', 'github', 'core', script('pr-checks'))(context, github, { setFailed: x => failure = x, info() {} });
  return failure;
}
test('contract accepts approved issue and substantive verification', async () => {
  for (const heading of ['Verification', 'Verificación'])
    assert.equal(await contract(`Closes #1\n\n## ${heading}\n\nnode --test: pruebas aprobadas\n\n## Lista de comprobación\n- [ ] revisión`), '');
});
test('contract rejects missing approval, placeholders and foreign references', async () => {
  assert.match(await contract('Refs #1\n## Verification\nActual result', false), /status:approved/);
  for (const heading of ['Verification', 'Verificación'])
    for (const verification of ['', '<!-- template only -->', 'TODO', 'TBD', 'N/A', 'none', 'pending', 'pendiente', 'NINGUNA.', 'por completar', 'no aplica!'])
      assert.match(await contract(`Refs #1\n## ${heading}\n${verification}\n## Lista de comprobación\n- [x] listo`), /Completa ## Verificación/);
  for (const reference of ['Closes other/repo#1', 'Refs https://github.com/other/repo/issues/1', '<!-- Closes #1 -->'])
    assert.match(await contract(`${reference}\n## Verification\nActual result`), /exacta Closes/);
  assert.match(await contract('Refs #1\n## Verification\nActual result', true, 'random title'), /título convencional/);
});
async function progress({ issueState = 'closed', reason = 'completed', reference = 'Closes #10', target = '99', dashboardLabels = ['dashboard'], base = 'main', acceptanceHeading = 'Acceptance criteria', module = 'proposals', statuses = [], merged = true } = {}) {
  let updated;
  const issue = { number: 1, state: issueState, state_reason: reason, labels: labels(['delivery', `module:${module}`, ...statuses]), assignees: [],
    body: `### Comprobaciones previas\n- [x] revisado\n### ${acceptanceHeading}\n- [x] primero\n- [ ] segundo\n### Evidencia\n- [x] no contar`,
    html_url: 'https://github.com/Sebas-Leo/repairmatch-backend/issues/1', updated_at: '2026-09-13T00:00:00Z' };
  const github = { rest: { issues: { listForRepo: 'issues', get: async () => ({ data: { labels: labels(dashboardLabels), body: '' } }), update: async args => updated = args }, pulls: { list: 'pulls' } },
    paginate: async (method, args) => { assert.equal(args.per_page, 100); assert.equal(args.owner, 'Sebas-Leo'); assert.equal(args.repo, 'repairmatch-backend'); return method === 'issues' ? [issue] : [{ number: 2, body: reference, state: 'closed', merged_at: merged ? '2026-09-13' : null, base: { ref: base }, html_url: 'https://github.com/Sebas-Leo/repairmatch-backend/pull/2' }]; } };
  const summary = { addRaw() { return this; }, async write() {} };
  await new AsyncFunction('context', 'github', 'core', 'process', script('team-progress'))({ repo: { owner: 'Sebas-Leo', repo: 'repairmatch-backend' } }, github, { summary }, { env: { PROGRESS_ISSUE_NUMBER: target } });
  return updated.body;
}
test('dashboard does not confuse #1 with #10; counts acceptance only', async () => {
  for (const acceptanceHeading of ['Acceptance criteria', 'Criterios de aceptación']) {
    const body = await progress({ acceptanceHeading });
    assert.match(body, /Cerrada sin evidencia de integración/);
    assert.match(body, /\| 1\/2 \| Ninguna \|/);
    assert.match(body, /Cerradas con evidencia de integración: 0/);
    assert.match(body, /Estado declarado.*Casillas de aceptación \(declaradas\).*Evidencia observada/);
    assert.doesNotMatch(body, /\d+%/);
  }
});
test('dashboard reports merged evidence, partial work and cancellations honestly', async () => {
  assert.match(await progress({ reference: 'Closes #1' }), /Cerradas con evidencia de integración: 1/);
  assert.match(await progress({ reference: 'Refs #1', issueState: 'open' }), /Abierta; evidencia de integración parcial/);
  assert.match(await progress({ reference: 'Closes #1', reason: 'not_planned' }), /Canceladas: 1/);
  for (const options of [{ base: 'feature' }, { merged: false }, { reference: 'Closes other/repo#1' }, { reference: '<!-- Closes #1 -->' }])
    assert.match(await progress({ reference: 'Closes #1', ...options }), /Cerradas con evidencia de integración: 0/);
});
test('dashboard refuses invalid variable or unrelated issue', async () => {
  await assert.rejects(progress({ target: '0' }), /Configura la variable/);
  await assert.rejects(progress({ dashboardLabels: ['delivery'] }), /se rechaza sobrescribirlo/);
});

test('dashboard translates known modules and statuses without changing label keys', async () => {
  for (const [module, name] of Object.entries({ identity: 'Identidad', requests: 'Solicitudes', matching: 'Emparejamiento', proposals: 'Propuestas', services: 'Servicios', shared: 'Compartido', unknown: 'Sin clasificar' })) {
    const body = await progress({ module, statuses: ['status:needs-review', 'status:approved', 'status:in-progress', 'status:blocked', 'status:in-review'] });
    assert.ok(body.includes(`| ${name} /`));
    assert.match(body, /Pendiente de aprobación, Aprobada, En curso, Bloqueada, En revisión/);
  }
});
test('Spanish templates stay compatible with their workflow parsers', async () => {
  const form = readFileSync(join(__dirname, '../.github/ISSUE_TEMPLATE/feature_request.yml'), 'utf8');
  const template = readFileSync(join(__dirname, '../.github/pull_request_template.md'), 'utf8');
  const acceptanceHeading = form.match(/label: (Criterios de aceptación)/)[1];
  assert.match(await progress({ acceptanceHeading }), /\| 1\/2 \|/);
  assert.match(form, /labels: \[delivery, "status:needs-review"\]/);
  assert.match(await contract(template.replaceAll('Refs #123', 'Refs #1')), /Completa ## Verificación/);
  assert.equal(await contract(template.replaceAll('Refs #123', 'Refs #1').replace('## Verificación', '## Verificación\nnode --test: pruebas aprobadas')), '');
});
test('workflow security boundaries and protected check names remain stable', () => {
  const checks = readFileSync(join(__dirname, '../.github/workflows/pr-checks.yml'), 'utf8');
  const dashboard = readFileSync(join(__dirname, '../.github/workflows/team-progress.yml'), 'utf8');
  assert.match(checks, /name: Contribution contract/);
  assert.match(checks, /name: Workflow tests/);
  assert.match(checks, /  pull_request:/);
  assert.match(checks, /persist-credentials: false/);
  assert.doesNotMatch(checks, /: write|secrets\.|pull_request_target/);
  assert.match(dashboard, /pull_request_target:/);
  assert.match(dashboard, /issues: write/);
  assert.doesNotMatch(dashboard, /actions\/checkout|\brun:|secrets\.|contents: write/);
  for (const workflow of [checks, dashboard]) {
    for (const action of workflow.matchAll(/uses: ([^\r\n]+)/g)) assert.match(action[1], /@[a-f0-9]{40}(?: |$)/);
    assert.doesNotMatch(workflow.split('          script: |')[1], /\$\{\{/);
  }
});
