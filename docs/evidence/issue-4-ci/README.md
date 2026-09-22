# Corrección de CI de autenticación

Fecha: 2026-09-22. Integración de main 8702ab0 en la rama del #4.

El registro de CI mostraba cuatro errores en ProposalComparisonIntegrationTests.cleanDatabase: intentaba borrar usuarios referenciados por refresh_tokens creados por otra suite. La base H2 compartida hacía que el resultado dependiera del orden de las clases de prueba.

Las suites de solicitudes y comparación de propuestas usan ahora bases H2 independientes. Se conservan las claves foráneas y las comprobaciones funcionales. Esta corrección no cambia el comportamiento de la API.

Verificación local con Java 17: clean verify -Dsurefire.runOrder=alphabetical, 70 pruebas aprobadas, sin fallos ni errores. El orden alfabético ejecuta identidad antes que propuestas y reproduce el orden relevante para la dependencia que fallaba. No se verificó PostgreSQL ni se afirma que la ejecución remota ya haya terminado.
