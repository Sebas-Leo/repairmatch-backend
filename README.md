# RepairMatch Backend

Proyecto de backend para **DBP, 2026-II**. RepairMatch conecta a personas que necesitan reparar un electrodoméstico con técnicos adecuados: el cliente publica una solicitud, recibe propuestas, selecciona un técnico, completa el servicio y deja una reseña.

Para la exposición académica, consulta el [informe de presentación](docs/PRESENTATION_REPORT.md). El README sigue siendo la guía compartida del proyecto.

La [guía de despliegue académico](docs/AWS_DEPLOY.md) documenta la imagen, el perfil `prod`, las variables requeridas y las limitaciones de una base sin migraciones versionadas.

**Estado: base Spring Boot y registro de cuentas implementados en esta rama.** Incluye pruebas de integración, BCrypt y una colección Postman. Consulta el [contrato y verificación del registro](docs/REGISTRATION.md). Los demás módulos descritos siguen siendo propuestas o entregas independientes; no se incluyen migraciones versionadas.

## Flujo de trabajo y seguimiento del avance

Consulta la [guía de trabajo del equipo](docs/TEAM_WORKFLOW.md) y el [tablero compartido de avance](https://github.com/Sebas-Leo/repairmatch-backend/issues/1). Las tareas, sus aprobaciones, las solicitudes de integración (pull requests o PR) revisadas y las evidencias de verificación permiten identificar las contribuciones. GitHub Actions comprueba los datos de los PR y actualiza el tablero; ejecuta la verificación Maven del backend; la ejecución de Postman se describe en la guía de registro. Los estados y las listas de verificación son declarados por el equipo; los PR y las integraciones registradas son evidencias observables, no calificaciones ni pruebas de que una funcionalidad esté terminada.

## Alcance y primer hito

Desarrollar una API REST con **Java y Spring Boot**, almacenar los datos en **PostgreSQL** y validar el flujo completo mediante **Postman**. La entrega actual incluye **únicamente el backend**, reemplazando el alcance de frontend web y móvil mencionado en el informe original.

El primer hito consiste en completar y probar este recorrido:

`Cliente -> Solicitud de reparación -> Técnicos compatibles -> Propuestas -> Selección -> Servicio -> Reseña`

El alcance inicial no incluye frontend, pagos, custodia de fondos, diagnóstico automático con IA, estimación de un "precio justo", seguimiento GPS ni un sistema complejo de garantías.

## Roles del equipo

Cada integrante es responsable de un módulo del backend: controladores, DTO y validaciones, servicios y reglas de negocio, persistencia, pruebas automatizadas, ejemplos de Postman y documentación de la API. La integración y la revisión entre compañeros son responsabilidades compartidas.

| Responsable | Módulo | Responsabilidades principales | Ejemplo de criterio de aceptación |
| --- | --- | --- | --- |
| **Ariana Belen Blanco Anicama** | Identidad y acceso | Registro e inicio de sesión, protección de contraseñas, permisos de clientes y técnicos, identidad autenticada y base común de autorización. | Las credenciales válidas permiten autenticarse; las inválidas se rechazan; un usuario no puede modificar recursos protegidos de otro. |
| **Camila Araceli Alfaro Chuquino** | Solicitudes, evidencias y tipos de electrodomésticos | Publicación estructurada, descripción original y campos normalizados, catálogo de tipos, estados de la solicitud, evidencias asociadas y validación del propietario. | Se publica una solicitud válida, se rechazan datos incorrectos y se impide que una evidencia exista sin su solicitud. |
| **Jairo Andre Cunya Villalta** | Perfiles de técnicos y búsqueda de técnicos compatibles | Especialización de Usuario en Técnico, experiencia y perfil, tipos de electrodomésticos atendidos, zonas y radio de servicio, consultas determinísticas de compatibilidad. | Un técnico solo es compatible si atiende el tipo de electrodoméstico y la ubicación está dentro de su radio; se descartan los demás casos. |
| **Royer Sebastian Ramos Vargas** | Propuestas y selección transaccional | Envío, consulta y comparación de propuestas, costo de visita o diagnóstico, disponibilidad, autorización de la selección y aceptación atómica junto con el cierre de la solicitud y la creación del servicio. | Dos aceptaciones simultáneas no pueden crear dos servicios; si la selección falla, se revierten todos los cambios relacionados. |
| **Adrian Luis Pacheco Sulluchuco** | Ciclo del servicio, reseñas y reputación | Gestión del servicio creado, transiciones de estado permitidas, reseñas de servicios completados, restricción de una reseña por servicio y reputación derivada del técnico. | Se rechaza una transición inválida o una reseña anticipada o duplicada; una reseña válida afecta la reputación del técnico correspondiente. |

### Coordinación entre módulos

El módulo de propuestas crea el `Service` inicial y cierra su `Request` de forma atómica cuando se acepta una oferta. El módulo de servicios define el contrato del modelo de servicio y gestiona su ciclo posterior. Ambos módulos deben compartir un único contrato de creación; no debe existir una segunda vía para crear servicios.

## Propuesta técnica

Utilizar un **monolito modular**: una aplicación Spring Boot y una base de datos PostgreSQL, organizadas por módulos de negocio. Esta estructura mantiene manejable un proyecto académico de cinco integrantes y permite que la selección se realice en una sola transacción de base de datos. Como contrapartida, todos los módulos comparten el despliegue y sus límites deben respetarse en el código. No se contemplan microservicios.

| Tecnología | Uso | Estado |
| --- | --- | --- |
| Java, Spring Boot, REST | Aplicación backend y API HTTP | Base implementada; versiones en `pom.xml` |
| Spring Data JPA, PostgreSQL | Persistencia y restricciones relacionales | Configuradas; pruebas aisladas con H2 |
| Spring Security | Autenticación y autorización | Incluida en el informe; mecanismo de autenticación por definir |
| Postman | Solicitudes, entornos compartidos y validación de la API | Colección de registro en `postman/` |
| Maven | Compilación reproducible y gestión de dependencias | Herramienta de apoyo recomendada |
| JUnit y Mockito | Pruebas automatizadas de reglas de negocio y aislamiento de dependencias | Herramientas de apoyo recomendadas |
| OpenAPI | Contratos compartidos y documentación consultable de la API | Herramienta de apoyo recomendada |

La base requiere Java 17; `pom.xml` y Maven Wrapper fijan las versiones. Consulta la guía de registro para ejecutar la aplicación y verificarla. La validación con Postman complementa las pruebas automatizadas, no las reemplaza. Las transacciones y la concurrencia también deben verificarse con PostgreSQL.

## Reglas del dominio

El informe original define el siguiente modelo y sus reglas semánticas (páginas 3 y 4). Los identificadores técnicos se conservan en inglés para facilitar su relación con la implementación:

- **Técnico es una especialización de Usuario:** todo técnico es usuario, pero no todo usuario es técnico. El registro crea `User` con rol `TECHNICIAN`; el perfil lo implementa el responsable del módulo de técnicos. El contrato de identidad y los puntos de coordinación están en [REGISTRATION.md](docs/REGISTRATION.md).
- Una `Request` pertenece al usuario que la publica y a un `ApplianceType`. Un técnico puede atender varios tipos de electrodomésticos y cada tipo puede ser atendido por varios técnicos.
- No se incorpora una entidad persistente `Appliance` en el producto mínimo viable (MVP). La marca, el modelo, el síntoma y los demás datos del equipo pertenecen a la solicitud.
- `Evidence` es una entidad débil de la solicitud, identificada por **(`requestId`, `evidenceNumber`)**. Su número parcial es local a cada solicitud; no puede existir de forma independiente.
- Cada `Proposal` pertenece a un técnico y a una solicitud. **Como máximo una propuesta por solicitud puede ser aceptada y originar un servicio.** Esta regla debe garantizarse mediante transacciones, control de concurrencia y restricciones de base de datos, no solo mediante validaciones en el controlador.
- Cada `Service` nace de exactamente una propuesta aceptada; una propuesta puede originar como máximo un servicio. El técnico responsable se obtiene mediante `Service -> Proposal -> Technician`, sin una relación directa redundante.
- El tipo de electrodoméstico atendido se obtiene mediante `Service -> Proposal -> Request -> ApplianceType`. La marca, el modelo y los demás datos se consultan en la solicitud.
- Cada `Review` tiene un autor y evalúa un servicio; un servicio puede tener como máximo una reseña. La reseña se registra después de completar el servicio. Antes de implementar, se deben definir las reglas sobre quién puede escribirla y el rango de calificación.
- La reputación del técnico **se deriva de las reseñas de los servicios originados por sus propuestas**. No es un valor editable de forma independiente en su perfil; más adelante podría almacenarse en caché.
- Se conserva la descripción original de la solicitud junto con los campos normalizados para la búsqueda de técnicos compatibles. Si posteriormente se incorpora IA, solo estructurará texto: no realizará diagnósticos y el usuario deberá confirmar o corregir la interpretación antes de guardar.

### Las solicitudes y los servicios tienen estados diferentes

Estos son los estados del informe, no los nombres definitivos de las enumeraciones de la API:

| Entidad | Estados del informe | Transición principal |
| --- | --- | --- |
| Solicitud (`Request`) | `PUBLICADA`, `CON_PROPUESTAS`, `CERRADA`, `CANCELADA`, `EXPIRADA` | Aceptar una propuesta cierra la solicitud. |
| Servicio (`Service`) | `PROGRAMADO`, `EN_ATENCIÓN`, `COMPLETADO`, `CANCELADO` | La selección crea un servicio programado; su ciclo continúa por separado. |

**Contrato de selección propuesto:** comprobar que el solicitante autenticado puede seleccionar la propuesta y que la solicitud cumple las condiciones; aceptar la propuesta, cerrar la solicitud y crear un servicio programado de forma atómica. Una transacción por sí sola no evita selecciones simultáneas: se necesita un mecanismo de concurrencia acordado y restricciones de base de datos. Los intentos repetidos o simultáneos nunca deben crear un segundo contrato. Las respuestas a solicitudes repetidas y las reglas de cancelación y expiración deben definirse antes de programar.

### Aclaración pendiente sobre la búsqueda de técnicos compatibles

La regla detallada de la **página 4** utiliza **tipo de electrodoméstico compatible Y distancia dentro del radio del técnico**, con la disponibilidad expresada en la solicitud y la propuesta. El resumen de funcionalidades de la **página 2** y la fórmula de la **página 5** también incluyen la disponibilidad como filtro de compatibilidad.

**Supuesto provisional de planificación:** seguir la regla detallada de la página 4 para el MVP y mostrar la disponibilidad al comparar propuestas, sin convertirla en un filtro automático. Esta diferencia debe confirmarse con el equipo y el docente antes de implementar. La búsqueda de técnicos compatibles (matching) es determinística, no utiliza IA.

## Rutas propuestas de la API

`POST /api/auth/register` está implementado en esta rama. El issue #4 de autenticación e identidad está cerrado mediante el [PR #31](https://github.com/Sebas-Leo/repairmatch-backend/pull/31), según el estado compartido por Ariana; revisar su integración en esta rama antes del #5. Las demás rutas de la tabla son contratos de sus respectivos módulos y no se dan por implementadas por esta entrega del #3. El equipo debe definir los datos de entrada y salida, la paginación, el formato de errores, los permisos y los nombres definitivos.

| Módulo | Rutas propuestas |
| --- | --- |
| Identidad | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/me` |
| Solicitudes | `GET /api/appliance-types`, `POST /api/requests`, `GET /api/requests/{id}`, `POST /api/requests/{id}/evidence` |
| Técnicos | `GET /api/technicians/{id}`, `PUT /api/technicians/me/profile`, `PUT /api/technicians/me/service-areas`, `GET /api/technicians/me/matching-requests` |
| Propuestas | `POST /api/requests/{id}/proposals`, `GET /api/requests/{id}/proposals`, `POST /api/proposals/{id}/accept` |
| Servicios y reseñas | `GET /api/services/{id}`, `PATCH /api/services/{id}/status`, `POST /api/services/{id}/review`, `GET /api/technicians/{id}/reputation` |

El rol autenticado no es suficiente: también se debe comprobar la propiedad o relación del usuario con las solicitudes, propuestas, servicios y reseñas. La visibilidad de los datos personales, la ubicación y las evidencias debe acordarse antes de exponerlos.

## Etapas de entrega y dependencias

1. **Acordar los contratos:** confirmar responsabilidades, resolver la diferencia sobre compatibilidad, modelar tablas y restricciones, definir transiciones de estado, autorización y ejemplos de entrada y salida. Revisar primero los límites de la selección.
2. **Crear la base del proyecto en equipo:** configurar Spring Boot, la base de datos, un esquema reproducible, la estructura de pruebas, los errores compartidos y un entorno seguro de Postman. Nunca guardar secretos ni credenciales reales en el repositorio.
3. **Habilitar la publicación y la compatibilidad:** Ariana proporciona la identidad; Camila, los contratos de solicitudes y tipos; Jairo utiliza esos contratos para determinar técnicos compatibles. Empezar con coordenadas conocidas y elegir después un servicio real de geocodificación.
4. **Completar la contratación:** Sebastian utiliza los contratos de identidad, solicitudes y técnicos, y coordina con Adrian la creación atómica del servicio. Demostrar el comportamiento ante selecciones simultáneas y la reversión de cambios mediante pruebas con base de datos.
5. **Completar el recorrido:** Adrian implementa las transiciones posteriores del servicio, las reseñas y la reputación derivada. El equipo valida el flujo completo y los casos inválidos o no autorizados.
6. **Agregar integraciones de apoyo cuando funcione el núcleo:** almacenamiento real de evidencias, mapas y geocodificación, notificaciones, procesamiento asíncrono y expiración automática. Mantener claros los límites de cada proveedor y no presentar sustitutos de prueba como integraciones de producción. La IA opcional para estructurar texto no es necesaria para el núcleo del MVP.

## Lista compartida de finalización

- [ ] Cada integrante entregó un módulo sustancial del backend, pruebas, ejemplos de Postman, documentación y una revisión de otro compañero.
- [ ] El recorrido del cliente hasta la reseña funciona con datos persistidos y se valida mediante Postman.
- [ ] Existen pruebas de validación, autorización, propiedad de recursos, recursos inexistentes y transiciones inválidas.
- [ ] La selección atómica, la reversión de cambios y las aceptaciones simultáneas mantienen un único servicio por solicitud.
- [ ] Las relaciones de base de datos, la identificación de evidencias y los valores derivados coinciden con el modelo acordado.
- [ ] Los contratos de la API y una colección y un entorno de Postman sin credenciales sensibles están versionados.
- [ ] Las instrucciones de configuración corresponden a una aplicación realmente ejecutable, sin secretos en el repositorio.

## Decisiones pendientes

- Confirmación de las responsabilidades y de la regla de disponibilidad en la búsqueda de técnicos compatibles con el equipo y el docente.
- Permisos exactos para las transiciones, políticas de cancelación y expiración, respuestas ante aceptaciones repetidas, estados de las propuestas y reglas de autoría y escala de las reseñas.
- Mecanismo de autenticación, mapeo JPA de Usuario/Técnico, estrategia de concurrencia, versiones compatibles y herramienta para gestionar el esquema de forma reproducible.
- Precisión de ubicación y unidades del radio, restricciones y almacenamiento de evidencias, proveedores externos y confirmación de si las funciones asíncronas aplazadas se requieren en la primera entrega calificada.

## Fuente y avance actual

Basado en **"RepairMatch - Propuesta de Proyecto, DBP (Semana 3)"**, proporcionado como `RepairMatch_Propuesta_DBP_Semana3_corregida.pdf`: integrantes y problema (página 1), MVP (página 2), modelo de datos y reglas semánticas (páginas 3 y 4), tecnologías e integraciones (páginas 4 y 5) y exclusiones (página 6). El informe no está incluido en este repositorio. La entrega exclusiva del backend y la validación con Postman corresponden al alcance actual solicitado por el equipo.

Referencias oficiales de apoyo: [transacciones en Spring](https://spring.io/guides/gs/managing-transactions/), [soporte SQL y JPA en Spring Boot](https://docs.spring.io/spring-boot/reference/data/sql.html) y [ejemplos de pruebas en Postman](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-examples).

- [x] Se documentaron el alcance inicial, los roles, el flujo compartido y las dependencias de implementación.
- [ ] Confirmar las decisiones pendientes con el equipo.
- [ ] Crear e implementar el backend después de autorizar la siguiente etapa.

## Permisos y propiedad — avance del #5

La [política de autorización](docs/AUTHORIZATION.md) define los controles de solicitudes y evidencias implementados y las reglas propuestas para los módulos pendientes. La entrega es parcial: no cierra el #5. Ver [evidencias](docs/evidence/issue-5/README.md).
