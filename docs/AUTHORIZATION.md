# Roles y propiedad — issue #5

## Estado y alcance

Política elaborada a partir del README y los contratos actuales del proyecto. Solo existen identidad, solicitudes y evidencias en esta rama. Propuestas, servicios, reseñas y perfiles técnicos siguen pendientes, según Ariana. Las reglas para esos módulos son un contrato propuesto; no se presentan como controles ya implementados ni como un acuerdo aprobado por todos los responsables. El #5 debe permanecer abierto hasta integrar y verificar esos módulos.

## Reglas compartidas

- Roles actuales: CLIENT y TECHNICIAN. El registro no admite ADMIN ni permisos enviados por el usuario.
- La identidad viene del sub del JWT validado por Spring Security. Nunca de clientId, userId, technicianId o authorId enviados por el consumidor.
- El rol por sí solo no concede acceso a un recurso ajeno. El servidor comprueba el propietario o participante almacenado en la base de datos.
- AccountAccess exige una cuenta existente, el rol actual en la base y la autoridad del token. Para cambiar a otro rol se requiere un nuevo token; una cuenta eliminada pierde acceso a estos recursos protegidos.
- Las respuestas usan DTO y no exponen contraseñas, hashes ni credenciales.
- 401: sin autenticación válida o cuenta no disponible. 403: rol o relación insuficiente. 404: recurso inexistente para una consulta autorizada por rol. 409: transición incompatible con el estado. Una denegación no debe modificar datos.
- La visibilidad pública de perfiles y reputación no permite exponer identidad privada, ubicación exacta o evidencias. Se definirán DTO públicos separados cuando existan esos módulos.

## Matriz de permisos

| Recurso / acción | Permitido | Restricción | Estado |
| --- | --- | --- | --- |
| Registro y login | Público | Validaciones y credenciales del #3/#4 | Existente |
| GET /api/users/me | Cuenta autenticada | Solo identidad del propio JWT | Existente #4 |
| Leer evidencias de solicitud | CLIENT propietario | Un cliente ajeno y TECHNICIAN reciben 403 | Implementado #5 |
| Adjuntar evidencia | CLIENT propietario | Propiedad obtenida de Request.client | Implementado #5 |
| Cancelar solicitud | CLIENT propietario | Se rechazan CERRADA, CANCELADA y EXPIRADA | Implementado #5 |
| PATCH /api/requests/{id}/close | Ningún consumidor HTTP | Cierre exclusivamente dentro de selección y creación atómicas del servicio | Bloqueado #5 |
| Crear o editar solicitud | CLIENT propietario | Propietario asignado desde JWT, no desde el cuerpo | Endpoint pendiente |
| Buscar solicitudes compatibles | TECHNICIAN con perfil compatible | Tipo de electrodoméstico y zona/radio, con datos mínimos para ofertar | Módulo pendiente |
| Ver evidencia desde compatibilidad | Solo lo que acuerde el módulo | No se otorga acceso a toda evidencia por ser técnico | Pendiente de acuerdo |
| Crear propuesta | TECHNICIAN compatible | Técnico obtenido desde la identidad y perfil; solicitud elegible | Módulo pendiente |
| Editar o retirar propuesta | Técnico autor | Solo mientras el estado permita esa operación | Módulo pendiente |
| Leer propuesta | Técnico autor o cliente dueño de su solicitud | Otros técnicos no acceden a ofertas ajenas | Módulo pendiente |
| Aceptar propuesta | CLIENT dueño de la solicitud | Aceptar, cerrar solicitud y crear servicio en una operación atómica | Módulo pendiente |
| Leer servicio | Cliente y técnico vinculados mediante propuesta/solicitud | Otros usuarios reciben rechazo | Módulo pendiente |
| Cambiar estado del servicio | Participante habilitado para esa transición | Matriz de transiciones por acordar con el responsable; denegar las no definidas | Pendiente de acuerdo |
| Crear reseña | Propuesta: CLIENT contratante | Servicio completado, una reseña por servicio, autor desde JWT | Módulo y acuerdo pendientes |
| Modificar/eliminar reseña | Ninguna operación concedida por defecto | Si se incorpora, solo autor y bajo reglas acordadas | Pendiente de acuerdo |
| Reputación | Cálculo del servidor | No editable directamente por cliente o técnico | Módulo pendiente |

La cancelación y el cierre conservan sus condiciones de estado. Los métodos internos transitionToHasProposals y expireRequest no son rutas HTTP; deben invocarse únicamente desde procesos internos autorizados. closeRequest queda como operación de servicio para la futura selección transaccional; no debe llamarse como acción independiente. El #5 no implementa esa selección ni crea el servicio.

## Integración para los otros módulos

1. Obtener la cuenta con AccountAccess.requireRole; no aceptar la identidad del consumidor como argumento de autorización.
2. Cargar la relación persistida: propuesta -> técnico/solicitud; servicio -> propuesta -> solicitud/técnico; reseña -> servicio/autor.
3. Comparar con la cuenta autenticada antes de devolver información o escribir. Para recursos de un único dueño puede usarse requireOwner con el UUID autenticado y el dueño persistido.
4. Validar estados dentro de la transacción y acordar las transiciones con el responsable. La autenticación no sustituye esas reglas.
5. Agregar pruebas reales de dos cuentas por rol, intentos sobre recursos ajenos y comprobación de que las denegaciones no cambian datos.

## Verificación local y Postman

Ejecutar ./mvnw verify con JAVA_HOME apuntando a Java 17. PermissionIntegrationTests usa JWT firmados y recursos reales en H2; verifica aislamiento entre clientes, rechazo de técnicos, 401 sin token, cancelación propia, lectura/escritura propia, cierre directo bloqueado, rol cambiado, cuenta eliminada y servicio invocado sin autenticación.

La colección postman/collections/RepairMatch -Permisos #5 tiene 12 solicitudes: tres logins de preparación y nueve casos de permisos. Ejecutar en orden. Las cuentas y recursos de ejemplo se crean exclusivamente mediante PermissionApiTestApplication, una clase de src/test que no se incluye en el JAR de producción. Solo admite H2 en memoria.

En Git Bash, levantar la API aislada:

~~~bash
export JAVA_HOME='/c/Users/Hogar/.jdks/ms-17.0.20'
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw --batch-mode --no-transfer-progress test-compile spring-boot:test-run '-Dspring-boot.run.main-class=com.repairmatch.repairmatch_backend.PermissionApiTestApplication' '-Dspring-boot.run.arguments=--server.port=18085 --server.address=127.0.0.1'
~~~

Las variables de colección usan baseUrl=http://127.0.0.1:18085 y requestId=1 para esta base recién creada; el identificador real también se escribe en target/permissions-fixture.json. No usar estos datos de ejemplo en una base real. Reiniciar la aplicación antes de repetir la colección: el último caso cancela la solicitud de prueba.

Ejecutar los mismos YAML automáticamente:

~~~bash
npm install --prefix target/postman-runner --no-save --package-lock=false newman@6.2.1 yaml@2.8.1
node scripts/run-permissions-postman.cjs
~~~

Conservar las variables de token vacías al guardar la colección. La configuración compartida no contiene JWT reales. Las evidencias de ejecución no sustituyen el acuerdo ni la revisión del equipo.

Resultados actuales: 67 pruebas Java aprobadas (20 de permisos), 12 solicitudes YAML y 18 aserciones aprobadas. Ver [evidencias](evidence/issue-5/README.md). El cierre del #5 sigue pendiente de los otros módulos y del acuerdo compartido.

## Ajuste de rúbrica

Los métodos sensibles incorporan @PreAuthorize además de las comprobaciones de propiedad persistida. Los errores de API y de los filtros de seguridad usan ErrorResponseDto. La entrega incluye postman_collection.json en la raíz, regenerable desde los YAML. Consultar docs/RUBRIC_IDENTITY.md.
