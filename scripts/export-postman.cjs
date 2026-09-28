const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'postman/postman_collection.json');
JSON.parse(fs.readFileSync(source, 'utf8'));
fs.copyFileSync(source, path.join(root, 'postman_collection.json'));
console.log('Copia para la entrega creada en postman_collection.json');
