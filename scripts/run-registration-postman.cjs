// Executes the checked-in Postman YAML files; no duplicate JSON collection.
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const root = process.cwd();
const runnerRequire = createRequire(path.join(root, 'target/postman-runner/package.json'));
const YAML = runnerRequire('yaml');
const newman = runnerRequire('newman');
const folder = path.join(root, 'postman/collections/RepairMatch -Registro #3');
const definition = YAML.parse(fs.readFileSync(path.join(folder, '.resources/definition.yaml'), 'utf8'));
const requests = fs.readdirSync(folder).filter(file => file.endsWith('.request.yaml'))
  .map(file => YAML.parse(fs.readFileSync(path.join(folder, file), 'utf8')))
  .sort((a, b) => a.order - b.order);
if (requests.length !== 8) throw new Error('Expected eight registration requests');
const collection = {
  info: { name: 'RepairMatch -Registro #3 (YAML)', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' },
  variable: Object.entries(definition.variables).map(([key, value]) => ({ key, value })),
  item: requests.map(request => ({
    name: request.name,
    event: request.scripts.map(script => {
      if (!['beforeRequest', 'afterResponse'].includes(script.type)) throw new Error('Unsupported script type');
      return { listen: script.type === 'beforeRequest' ? 'prerequest' : 'test', script: { type: script.language, exec: script.code.split('\n') } };
    }),
    request: { method: request.method, url: request.url,
      header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: request.body.content } }
  }))
};
newman.run({ collection, envVar: [{ key: 'baseUrl', value: process.argv[2] || 'http://127.0.0.1:18083' }],
  reporters: ['cli', 'junit'], reporter: { junit: { export: path.join(root, 'target/postman-registration.xml') } }
}, (error, summary) => {
  if (error) { console.error(error.message); process.exitCode = 1; return; }
  const result = { date: new Date().toISOString(), source: '8 checked-in YAML requests',
    requests: summary.run.stats.requests.total, assertions: summary.run.stats.assertions.total,
    failures: summary.run.failures.length };
  fs.writeFileSync(path.join(root, 'target/postman-registration-summary.json'), JSON.stringify(result, null, 2));
  process.exitCode = result.failures ? 1 : 0;
});
