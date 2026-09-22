# Compatibilidad con main

Fecha: 2026-09-22. Integración de origin/main 8702ab0 (PR #32 y #33) con identidad #3, autenticación #4 y permisos #5.

- Maven verify: 104 pruebas, 0 fallos, 0 errores; 34 pruebas de permisos.
- Newman: 39 solicitudes, 68 aserciones, 0 fallos en postman_collection.json.
- Java 17, JWT y H2 en memoria. No se verificó PostgreSQL.

Se resolvieron los conflictos de RequestController y RequestService. Se conservan creación, listado y consulta de solicitudes y comparación de propuestas. Los servicios obtienen la identidad autenticada y verifican la cuenta actual, rol y propiedad. Las pruebas cubren además cuentas eliminadas y cambios de rol sobre los nuevos endpoints. Las suites de solicitudes y propuestas usan bases aisladas para que su limpieza no borre fixtures de otras suites.

El cierre directo sigue bloqueado con 403, conforme al contrato de selección y creación atómicas del servicio del #5. La prueba recibida esperaba 409 y se ajustó a ese contrato; no se habilita un cierre independiente.

Para reproducir: ejecutar Maven verify. Iniciar PermissionApiTestApplication sobre H2 nueva en el puerto 18085, según docs/AUTHORIZATION.md, y ejecutar:

~~~sh
node scripts/export-postman.cjs
node target/postman-runner/node_modules/newman/bin/newman.js run postman_collection.json --env-var baseUrl=http://127.0.0.1:18085 --reporters cli,junit --reporter-junit-export target/main-integration-postman.xml
~~~

Datos ficticios, sin tokens ni credenciales reales en los reportes. La colección de permisos incluye 21 solicitudes. El #5 permanece abierto para los flujos y módulos pendientes y el acuerdo de políticas con el equipo.
