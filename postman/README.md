x|# Postman: registro #3, identidad #4 y permisos #5

## Importar y ejecutar

1. Mantener el backend encendido en `http://localhost:8080`.
2. En Postman, seleccionar **Import** y abrir `postman/postman_collection.json`.
3. Seleccionar **No environment**. Abrir la colección → **Run** → marcar las tres carpetas completas → **Run RepairMatch**. Mantener su orden.

Resultado verificado contra el backend local: **44 solicitudes, 73 comprobaciones, 0 fallos** (25 de septiembre de 2026). Incluye los 39 casos originales y cinco solicitudes de preparación. Los rechazos esperados (400, 401, 403 y 409) cuentan como pruebas aprobadas cuando coinciden con las aserciones.

El JSON genera correos únicos, registra las cuentas, inicia sesión, guarda tokens, crea una solicitud y adjunta la evidencia inicial. Se puede volver a ejecutar sin copiar tokens ni identificadores. Los ejemplos guardados son ilustrativos; los tests verifican respuestas reales.

## Base de datos y variables

La base local quedó preparada con el tipo de electrodoméstico `1`, llamado `Lavadora Postman`. `applianceTypeId` debe existir antes de ejecutar la colección. Para otro equipo o una base vacía, ejecutar `postman/setup-demo.sql` en PostgreSQL y poner el ID devuelto en la variable `applianceTypeId` de la colección. No hay una API de creación de catálogo en esta versión.

`baseUrl` vale `http://localhost:8080`; cambiarlo si el backend usa otro puerto. Evitar variables de entorno con nombres iguales. Cada Run crea cuentas y solicitudes de demostración: usar la base de desarrollo.

## Ejecutar sin interfaz

```powershell
npm.cmd install --prefix target/postman-runner --no-save --package-lock=false newman@6.2.1
node scripts/run-json-postman.cjs http://127.0.0.1:8080
```

Los resultados quedan en `target/postman-json-summary.json` y `target/postman-json-results.xml`, sin guardar tokens. Los ejecutores de registro y permisos también usan el mismo JSON. Los YAML de las tres carpetas fueron reemplazados.

La rúbrica pide una copia en la raíz: para generarla desde esta colección, ejecutar `node scripts/export-postman.cjs`. Importar solo una de las copias en Postman.
