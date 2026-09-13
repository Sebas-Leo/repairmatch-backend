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
    assert.equal(args.repo, 'repairmatch-backend');
    assert.equal(args.issue_number, 1);
    return { data: { labels: labels(approved ? ['status:approved'] : []) } };
  } } } };
  await new AsyncFunction('context', 'github', 'core', script('pr-checks'))(context, github, { setFailed: x => failure = x, info() {} });
  return failure;
}
test('contract accepts approved issue and substantive verification', async () => {
  assert.equal(await contract('Closes #1\n\n## Verification\n\nnode --test: 8 tests passed\n\n## Checklist\n- [ ] review'), '');
});
test('contract rejects missing approval, placeholders and foreign references', async () => {
  assert.match(await contract('Refs #1\n## Verification\nActual result', false), /status:approved/);
  for (const verification of ['', '<!-- template only -->', 'TODO', 'N/A'])
    assert.match(await contract(`Refs #1\n## Verification\n${verification}\n## Checklist\n- [x] done`), /Fill ## Verification/);
  assert.match(await contract('Closes other/repo#1\n## Verification\nActual result'), /exact Closes/);
  assert.match(await contract('<!-- Closes #1 -->\n## Verification\nActual result'), /exact Closes/);
  assert.match(await contract('Refs #1\n## Verification\nActual result', true, 'random title'), /conventional title/);
});
async function progress({ issueState = 'closed', reason = 'completed', reference = 'Closes #10', target = '99', dashboardLabels = ['dashboard'], base = 'main' } = {}) {
  let updated;
  const issue = { number: 1, state: issueState, state_reason: reason, labels: labels(['delivery', 'module:proposals']), assignees: [],
    body: '### Pre-flight\n- [x] checked\n### Acceptance criteria\n- [x] first\n- [ ] second\n### Evidence\n- [x] not counted',
    html_url: 'https://github.com/Sebas-Leo/repairmatch-backend/issues/1', updated_at: '2026-09-13T00:00:00Z' };
  const github = { rest: { issues: { listForRepo: 'issues', get: async () => ({ data: { labels: labels(dashboardLabels), body: '' } }), update: async args => updated = args }, pulls: { list: 'pulls' } },
    paginate: async (method, args) => { assert.equal(args.per_page, 100); return method === 'issues' ? [issue] : [{ number: 2, body: reference, state: 'closed', merged_at: '2026-09-13', base: { ref: base }, html_url: 'https://github.com/Sebas-Leo/repairmatch-backend/pull/2' }]; } };
  const summary = { addRaw() { return this; }, async write() {} };
  await new AsyncFunction('context', 'github', 'core', 'process', script('team-progress'))({ repo: { owner: 'Sebas-Leo', repo: 'repairmatch-backend' } }, github, { summary }, { env: { PROGRESS_ISSUE_NUMBER: target } });
  return updated.body;
}
test('dashboard does not confuse #1 with #10; counts acceptance only', async () => {
  const body = await progress();
  assert.match(body, /Closed without merged evidence/);
  assert.match(body, /\| 1\/2 \| None \|/);
  assert.match(body, /Closed with merged evidence: 0/);
});
test('dashboard reports merged evidence, partial work and cancellations honestly', async () => {
  assert.match(await progress({ reference: 'Closes #1' }), /Closed with merged evidence: 1/);
  assert.match(await progress({ reference: 'Refs #1', issueState: 'open' }), /Open; partial merged evidence/);
  assert.match(await progress({ reference: 'Closes #1', reason: 'not_planned' }), /Canceled: 1/);
  assert.match(await progress({ reference: 'Closes #1', base: 'feature' }), /Closed with merged evidence: 0/);
});
test('dashboard refuses invalid variable or unrelated issue', async () => {
  await assert.rejects(progress({ target: '0' }), /Set repository variable/);
  await assert.rejects(progress({ dashboardLabels: ['delivery'] }), /refusing to overwrite/);
});
