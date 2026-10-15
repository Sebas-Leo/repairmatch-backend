const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const root = path.resolve(__dirname, '..');
function run(folder) {
  const newman = createRequire(path.join(root, 'target/postman-runner/package.json'))('newman');
  const collection = JSON.parse(fs.readFileSync(path.join(root, 'postman/postman_collection.json'), 'utf8'));
  if (folder) collection.item = collection.item.filter(item => item.name.includes(folder));
  if (!collection.item.length) throw new Error('Carpeta no encontrada: ' + folder);
  newman.run({collection, envVar:[{key:'baseUrl', value:process.argv[2] || 'http://localhost:8080'}],
    reporters:['cli','junit'], reporter:{junit:{export:path.join(root, 'target/postman-json-results.xml')}}}, (error, summary) => {
    if (error) { console.error(error.message); process.exitCode=1; return; }
    const result={date:new Date().toISOString(), source:'postman/postman_collection.json', folder:folder || 'all',
      requests:summary.run.stats.requests.total, assertions:summary.run.stats.assertions.total, failures:summary.run.failures.length};
    fs.writeFileSync(path.join(root, 'target/postman-json-summary.json'), JSON.stringify(result,null,2)+'\n');
    process.exitCode=result.failures ? 1 : 0;
  });
}
module.exports = run;
if (require.main === module) run();

