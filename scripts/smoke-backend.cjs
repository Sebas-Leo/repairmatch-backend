// Executes the same public-API scenario exported to Postman; never seeds the database directly.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const steps = [
  ['Register client', 'POST', '/api/auth/register', 201, null,
    {name:'Smoke Client',email:'client-{{runId}}@example.com',password:'LocalSmoke2026!',role:'CLIENT'}, {clientId:'id'}],
  ['Register technician', 'POST', '/api/auth/register', 201, null,
    {name:'Smoke Technician',email:'tech-{{runId}}@example.com',password:'LocalSmoke2026!',role:'TECHNICIAN'}, {technicianId:'id'}],
  ['Login client', 'POST', '/api/auth/login', 200, null,
    {email:'client-{{runId}}@example.com',password:'LocalSmoke2026!'}, {clientToken:'accessToken'}],
  ['Login technician', 'POST', '/api/auth/login', 200, null,
    {email:'tech-{{runId}}@example.com',password:'LocalSmoke2026!'}, {techToken:'accessToken'}],
  ['Appliance catalog', 'GET', '/api/appliance-types', 200, 'clientToken', null, {applianceTypeId:'0.id'}],
  ['Technician specialties', 'PUT', '/api/technicians/me/profile', 200, 'techToken',
    {experienceYears:4,bio:'Local smoke fixture',applianceTypeIds:['{{applianceTypeId}}']}],
  ['Technician coverage', 'PUT', '/api/technicians/me/service-areas', 200, 'techToken',
    {latitude:-12.0464,longitude:-77.0428,maxRadiusKm:20}],
  ['Publish request', 'POST', '/api/requests', 201, 'clientToken',
    {applianceTypeId:'{{applianceTypeId}}',originalDescription:'Local smoke: appliance will not start',brand:'Demo',model:'Fixture',latitude:-12.0464,longitude:-77.0428}, {requestId:'id'}],
  ['Matching requests', 'GET', '/api/technicians/me/matching-requests?page=0&size=100', 200, 'techToken'],
  ['Submit proposal', 'POST', '/api/requests/{{requestId}}/proposals', 201, 'techToken',
    {diagnosticCost:45.50,availableAt:'{{availableAt}}',conditions:'Diagnostic visit only'}, {proposalId:'id'}],
  ['Reject duplicate offer', 'POST', '/api/requests/{{requestId}}/proposals', 409, 'techToken',
    {diagnosticCost:45.50,availableAt:'{{availableAt}}',conditions:'Duplicate'}],
  ['Compare proposals', 'GET', '/api/requests/{{requestId}}/proposals', 200, 'clientToken'],
  ['Reject technician comparison', 'GET', '/api/requests/{{requestId}}/proposals', 403, 'techToken'],
  ['Accept offer', 'POST', '/api/proposals/{{proposalId}}/accept', 201, 'clientToken', null, {serviceId:'id'}],
  ['Reject repeated acceptance', 'POST', '/api/proposals/{{proposalId}}/accept', 409, 'clientToken'],
  ['Reject premature review', 'POST', '/api/services/{{serviceId}}/review', 409, 'clientToken', {rating:5,comment:'Too early'}],
  ['Start service', 'PATCH', '/api/services/{{serviceId}}/status?status=EN_ATENCION', 200, 'techToken'],
  ['Complete service', 'PATCH', '/api/services/{{serviceId}}/status?status=COMPLETADO', 200, 'techToken'],
  ['Review service', 'POST', '/api/services/{{serviceId}}/review', 201, 'clientToken', {rating:5,comment:'Local verification completed'}],
  ['Reject duplicate review', 'POST', '/api/services/{{serviceId}}/review', 409, 'clientToken', {rating:5,comment:'Duplicate'}],
  ['Derived reputation', 'GET', '/api/technicians/{{technicianId}}/reputation', 200, 'clientToken']
];
const get = (object, key) => key.split('.').reduce((value, part) => value?.[part], object);
function interpolate(value, vars) {
  if (Array.isArray(value)) return value.map(item => interpolate(item, vars));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key,item]) => [key,interpolate(item,vars)]));
  if (typeof value !== 'string') return value;
  const exact = value.match(/^\{\{(\w+)\}\}$/);
  if (exact) { assert.notEqual(vars[exact[1]], undefined, 'Missing variable '+exact[1]); return vars[exact[1]]; }
  return value.replace(/\{\{(\w+)\}\}/g, (_, key) => { assert.notEqual(vars[key],undefined,'Missing variable '+key); return vars[key]; });
}
function exportCollection() {
  const collection = {
    info:{name:'RepairMatch - MVP local',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'},
    variable:[{key:'baseUrl',value:'http://127.0.0.1:8080'}],
    item:steps.map(([name,method,url,status,token,body,captures], index) => ({
      name, request:{method,url:'{{baseUrl}}'+url,header:[{key:'Content-Type',value:'application/json'},...(token?[{key:'Authorization',value:'Bearer {{'+token+'}}'}]:[])],
        ...(body?{body:{mode:'raw',raw:JSON.stringify(body,null,2).replace(/"\{\{applianceTypeId\}\}"/g,'{{applianceTypeId}}')}}:{})},
      event:[...(index===0?[{listen:'prerequest',script:{type:'text/javascript',exec:[
        "pm.collectionVariables.set('runId', Date.now()+'-'+Math.random().toString(16).slice(2));",
        "pm.collectionVariables.set('availableAt', new Date(Date.now()+86400000).toISOString().slice(0,19));"
      ]}}]:[]),{listen:'test',script:{type:'text/javascript',exec:[
        `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`,
        ...Object.entries(captures||{}).map(([key,p])=>`pm.collectionVariables.set('${key}', '${p}'.split('.').reduce((v,k)=>v[k], pm.response.json()));`),
        ...(name==='Derived reputation'?["pm.test('Derived rating', () => { pm.expect(pm.response.json().averageRating).to.eql(5); pm.expect(pm.response.json().reviewCount).to.eql(1); });"]:[])
      ]}}]
    }))
  };
  const destination=path.join(__dirname,'../postman/RepairMatch-MVP.postman_collection.json');
  fs.writeFileSync(destination,JSON.stringify(collection,null,2)+'\n');
  console.log('Exported '+destination);
}
async function run() {
  const base=process.argv[2]||'http://127.0.0.1:8080';
  const address=new URL(base);
  assert.ok(['localhost','127.0.0.1','[::1]'].includes(address.hostname),'Smoke runner only writes to a local API');
  const vars={runId:Date.now()+'-'+Math.random().toString(16).slice(2), availableAt:new Date(Date.now()+86400000).toISOString().slice(0,19)};
  for(const [name,method,url,status,token,body,captures] of steps) {
    const response=await fetch(base+interpolate(url,vars), {method,signal:AbortSignal.timeout(15000),headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+vars[token]}:{})},...(body?{body:JSON.stringify(interpolate(body,vars))}:{})});
    const text=await response.text();
    assert.equal(response.status,status, name+' failed: '+text);
    const data=text?JSON.parse(text):null;
    for(const [key,p] of Object.entries(captures||{})) { vars[key]=get(data,p); assert.notEqual(vars[key],undefined,name+': missing '+p); }
    if(name==='Compare proposals') assert.ok(data.some(item=>item.id===vars.proposalId));
    if(name==='Derived reputation') { assert.equal(data.averageRating,5); assert.equal(data.reviewCount,1); }
    console.log('PASS '+name);
  }
  console.log(steps.length+' API steps passed. Synthetic records retained in the local database.');
}
if(process.argv[2]==='--export') exportCollection();
else run().catch(error=>{console.error(error.message);process.exitCode=1;});
