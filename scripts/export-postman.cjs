const fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module');
const root=process.cwd();
const YAML=createRequire(path.join(root,'target/postman-runner/package.json'))('yaml');
const base=path.join(root,'postman/collections');
const variables=new Map([['baseUrl','http://localhost:8080']]);
const folders=[];
for(const folder of fs.readdirSync(base).sort()){
 const directory=path.join(base,folder),definition=path.join(directory,'.resources/definition.yaml');
 if(!fs.existsSync(definition))continue;
 const config=YAML.parse(fs.readFileSync(definition,'utf8'));
 const vars=Array.isArray(config.variables)?config.variables:Object.entries(config.variables||{}).map(([key,value])=>({key,value}));
 for(const {key,value} of vars)if(key!=='baseUrl')variables.set(key,/token/i.test(key)?'':(value||''));
 const requests=fs.readdirSync(directory).filter(f=>f.endsWith('.request.yaml')).map(f=>YAML.parse(fs.readFileSync(path.join(directory,f),'utf8'))).sort((a,b)=>a.order-b.order);
 folders.push({name:folder,description:'Ejecutar en orden. Los casos de permisos requieren los datos H2 descritos en docs/AUTHORIZATION.md.',item:requests.map(r=>{
  const scripts=r.scripts||[];
  const expected=Number(scripts.map(s=>s.code).join('\n').match(/\.status\((\d+)\)/)?.[1]||200);
  const request={method:r.method,url:r.url.replace(/^http:\/\/localhost:\d+/,'{{baseUrl}}'),description:(r.description||r.name)+'. Verifica el contrato y el estado HTTP '+expected+'. Usar únicamente cuentas de demostración.',header:r.body?[{key:'Content-Type',value:'application/json'}]:[],auth:r.auth?.type==='bearer'?{type:'bearer',bearer:[{key:'token',value:r.auth.credentials.token,type:'string'}]}:{type:'noauth'},...(r.body?{body:{mode:'raw',raw:r.body.content,options:{raw:{language:'json'}}}}:{})};
  const example=expected>=400?{timestamp:'2026-09-22T00:00:00Z',status:expected,error:({400:'Bad Request',401:'Unauthorized',403:'Forbidden',409:'Conflict'})[expected]||'Error',message:'Ejemplo ilustrativo de rechazo; consultar las evidencias para resultados reales.',path:r.url.replace(/^.*?\/api/,'/api')}:r.url.endsWith('/register')||r.url.endsWith('/me')?{id:'00000000-0000-0000-0000-000000000001',name:'Usuario Demo',email:'demo@example.com',role:'CLIENT'}:r.url.endsWith('/login')||r.url.endsWith('/refresh')?{accessToken:'<token generado al ejecutar>',tokenType:'Bearer',expiresIn:3600,refreshToken:'<token de renovación>',refreshExpiresIn:604800}:r.method==='GET'?[]:{status:'Respuesta de ejemplo; ejecutar para obtener el recurso real'};
  return {name:r.name||r.url,event:scripts.map(s=>({listen:s.type==='beforeRequest'?'prerequest':'test',script:{type:s.language||'text/javascript',exec:s.code.split('\n')}})),request,response:[{name:'Ejemplo ilustrativo HTTP '+expected,originalRequest:request,status:({200:'OK',201:'Created',204:'No Content',400:'Bad Request',401:'Unauthorized',403:'Forbidden',409:'Conflict'})[expected]||'Response',code:expected,header:expected===204?[]:[{key:'Content-Type',value:'application/json'}],body:expected===204?'':JSON.stringify(example,null,2)}]};
 })});
}
const collection={info:{name:'RepairMatch API — entrega DBP',schema:'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',description:'Colección generada desde postman/collections. Contiene los endpoints disponibles y ejemplos ilustrativos, no respuestas de producción. Configurar baseUrl. JWT y refresh tokens deben permanecer vacíos al versionar. Ver docs/RUBRIC_IDENTITY.md y docs/AUTHORIZATION.md.'},variable:[...variables].map(([key,value])=>({key,value,type:'string'})),item:folders};
fs.writeFileSync(path.join(root,'postman_collection.json'),JSON.stringify(collection,null,2)+'\n');
console.log('Exported '+folders.reduce((sum,f)=>sum+f.item.length,0)+' requests to postman_collection.json');
