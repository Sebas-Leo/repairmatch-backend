# Verificación integrada de ajustes de rúbrica

Fecha: 2026-09-22. Java 17, JWT y H2 en memoria; no verifica PostgreSQL.

- Maven verify: 82 pruebas, 0 fallos y 0 errores.
- Newman: colección raíz postman_collection.json, 30 solicitudes y 54 aserciones, 0 fallos.
- Los reportes anteriores de cada issue son históricos; esta ejecución incluye registro, autenticación, renovación y permisos.

Arrancar PermissionApiTestApplication con spring-boot:test-run como indica docs/AUTHORIZATION.md, usando puerto 18085, y ejecutar:

~~~sh
node target/postman-runner/node_modules/newman/bin/newman.js run postman_collection.json --env-var baseUrl=http://127.0.0.1:18085 --reporters cli,junit --reporter-junit-export target/rubric-postman.xml
~~~

Usar una base H2 nueva para cada ejecución completa: los casos cancelan la solicitud de prueba. Datos ficticios. Las evidencias no contienen tokens ni contraseñas reales. La política de los módulos pendientes todavía necesita acuerdo e implementación; no cerrar #5.
