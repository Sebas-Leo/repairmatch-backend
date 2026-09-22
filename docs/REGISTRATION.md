# Registro de cuentas — issue #3

## Alcance y contrato de identidad

El registro crea una fila en users con UUID, nombre, correo normalizado, hash BCrypt y rol CLIENT o TECHNICIAN. Ambos roles comparten la misma identidad y la unicidad del correo. La respuesta expone id, name, email y role; nunca la contraseña o su hash.

El perfil Technician es responsabilidad de otro colaborador, según la distribución indicada por Ariana. Registrar una cuenta TECHNICIAN no crea un perfil, especialidades ni zonas y no implica que ya sea compatible para solicitudes. El módulo de perfiles debe referenciar el UUID del User existente. La relación JPA concreta, el momento de creación del perfil y sus restricciones deben confirmarse con ese responsable; este documento no declara su aprobación ni implementa su módulo. No se necesita MapStruct para esta separación.

El issue #4 (autenticación y consulta de identidad) está cerrado mediante el PR #31, según el estado de GitHub compartido por Ariana: https://github.com/Sebas-Leo/repairmatch-backend/pull/31. No forma parte del trabajo pendiente de esta entrega. El PR #31 ya está integrado en feature/3-pruebas-evidencia; las pruebas verifican conjuntamente registro, login e identidad. El siguiente issue será el #5 (permisos y propiedad). El endpoint de registro es público.

## API

POST /api/auth/register, Content-Type: application/json.

Ejemplo con datos ficticios:

~~~json
{"name":"Usuario Demo","email":"demo@example.com","password":"PruebaLocal2026!","role":"CLIENT"}
~~~

Para una cuenta técnica, usar TECHNICIAN. Respuestas: 201 para cuenta creada, 409 para correo duplicado y 400 para campos inválidos o JSON ilegible. Los errores usan ProblemDetail; la validación de campos agrega errors.

- name: obligatorio, no vacío, máximo 100 caracteres; se recortan espacios exteriores.
- email: obligatorio, formato de correo, máximo 254 caracteres; se convierte a minúsculas antes de buscar/guardar. Los espacios exteriores se rechazan en la validación de entrada.
- password: obligatoria, no en blanco, entre 8 y 72 caracteres y máximo 72 bytes UTF-8. No se recorta ni normaliza.
- role: cadena exacta CLIENT o TECHNICIAN; no se aceptan roles administrativos, números, valores nulos o campos ausentes.
- La restricción única de base de datos protege también frente a registros concurrentes. El nombre de la restricción es uk_users_email en esquemas nuevos; ddl-auto=update no es una migración de esquemas existentes.

## Requisitos y pruebas

Configurar JAVA_HOME a un JDK 17 (java del PATH puede apuntar a otra versión). En PowerShell:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
~~~

Las pruebas usan src/test/resources/application.properties y H2 en memoria en modo PostgreSQL. Verifican persistencia, roles, BCrypt, duplicados y entradas inválidas. Los reportes están en target/surefire-reports. H2 no sustituye una comprobación del despliegue PostgreSQL.

## Postman reproducible, sin datos de producción

La colección contiene 8 solicitudes YAML en postman/collections/RepairMatch -Registro #3. Abrirla en la vista local de Postman y ejecutarla completa en orden con Collection Runner. La variable baseUrl se define en .resources/definition.yaml y apunta a http://localhost:8080; cambiarla si la API usa otro puerto. No requiere archivos JSON ni variables globales.

Para levantar una API de pruebas con H2, en una terminal separada:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile spring-boot:test-run "-Dspring-boot.run.main-class=com.repairmatch.repairmatch_backend.RegistrationApiTestApplication" "-Dspring-boot.run.arguments=--server.port=18083 --server.address=127.0.0.1"
~~~

Cambiar la variable de colección baseUrl a http://127.0.0.1:18083 y ejecutar las 8 solicitudes desde Postman. Guardar el resultado del Runner como evidencia.

Detener la API con Ctrl+C al terminar; la base de pruebas desaparece. La API normal se inicia con spring-boot:run y necesita PostgreSQL. Su configuración usa src/main/resources/application.properties; proporcionar las credenciales mediante variables de entorno de Spring. Un archivo .env no se carga automáticamente por Spring Boot y no debe versionarse.

## Cierre y revisión

Adjuntar resultados reales de Maven y Postman al PR. Confirmar el contrato de identidad con el responsable de perfiles y la compatibilidad con la base del #2. Antes de abrir el PR, el responsable del repositorio debe otorgar status:approved. Usar Closes #3 solo cuando estén satisfechos los criterios, obtener revisión de otro integrante y combinar según TEAM_WORKFLOW.md. Esta entrega no acredita por sí sola acuerdos o aprobaciones externas.

## Comandos equivalentes en Git Bash (MINGW64)

Desde la carpeta del proyecto, configurar un JDK 17 instalado. En este equipo:

~~~bash
export JAVA_HOME='/c/Users/Hogar/.jdks/ms-17.0.20'
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw --batch-mode --no-transfer-progress verify
~~~

Para Postman, iniciar la API en otra terminal con el mismo JAVA_HOME:

~~~bash
./mvnw --batch-mode --no-transfer-progress test-compile spring-boot:test-run '-Dspring-boot.run.main-class=com.repairmatch.repairmatch_backend.RegistrationApiTestApplication' '-Dspring-boot.run.arguments=--server.port=18083 --server.address=127.0.0.1'
~~~

Luego ejecutar los 8 casos desde Collection Runner en Postman con baseUrl=http://127.0.0.1:18083.

Resultados guardados de esta entrega: [evidencias](evidence/issue-3/README.md). La restricción de roles numéricos utiliza EnumFeature de Jackson 3 mediante spring.jackson.datatype.enum.fail-on-numbers-for-enums; véase la [documentación de Jackson](https://javadoc.io/static/tools.jackson.core/jackson-databind/3.1.3/tools.jackson.databind/tools/jackson/databind/cfg/EnumFeature.html).

La colección se redujo a los 8 casos esenciales a petición de Ariana. Los casos adicionales permanecen en las pruebas Java. Las evidencias actuales verifican estos 8 YAML: 8 solicitudes y 26 aserciones aprobadas. Maven verifica 47 pruebas con JWT y ModelMapper.

## Conversión de entidades a DTO

El registro y GET /api/users/me utilizan el bean ModelMapper para convertir User a UserResponseDto, con coincidencia estricta y validación del mapeo al arrancar. Solo se exponen id, name, email y role. La normalización del correo y BCrypt permanecen explícitos en UserService. Referencia: https://modelmapper.org/getting-started/

## Ejecución automática de los YAML

Con la API de pruebas iniciada según las instrucciones anteriores, ejecutar desde la raíz:

~~~bash
npm install --prefix target/postman-runner --no-save --package-lock=false newman@6.2.1 yaml@2.8.1
node scripts/run-registration-postman.cjs http://127.0.0.1:18083
~~~

Las dependencias y reportes se guardan en target, que Git ignora. El script lee los YAML originales y ejecuta sus verificaciones en Newman; no requiere exportar ni mantener un JSON de colección.
