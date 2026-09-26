# Informe de Backend (Semana 7) - RepairMatch

## Portada
* **Título del Proyecto:** RepairMatch Backend API — Plataforma Integral de Conexión y Gestión de Servicios Técnicos de Reparación de Electrodomésticos.
* **Nombre del Curso:** CS 2031 Desarrollo Basado en Plataforma.
* **Nombres de los Integrantes:**
  - Ariana Belén Blanco Anicama
  - Camila Araceli Alfaro Chuquino
  - Jairo André Cunya Villalta
  - Royer Sebastian Ramos Vargas
  - Adrian Luis Pacheco Sulluchuco

---

## Índice
1. [Introducción](#introducción)
   - [Contexto](#contexto)
   - [Objetivos del Proyecto](#objetivos-del-proyecto)
2. [Identificación del Problema o Necesidad](#identificación-del-problema-o-necesidad)
   - [Descripción del Problema](#descripción-del-problema)
   - [Justificación](#justificación)
3. [Descripción de la Solución](#descripción-de-la-solución)
   - [Funcionalidades Implementadas](#funcionalidades-implementadas)
   - [Tecnologías Utilizadas](#tecnologías-utilizadas)
4. [Modelo de Entidades](#modelo-de-entidades)
   - [Diagrama de Entidades](#diagrama-de-entidades)
   - [Descripción de Entidades](#descripción-de-entidades)
5. [Manejo de Errores](#manejo-de-errores)
6. [Medidas de Seguridad Implementadas](#medidas-de-seguridad-implementadas)
   - [Seguridad de Datos](#seguridad-de-datos)
   - [Prevención de Vulnerabilidades](#prevención-de-vulnerabilidades)
7. [Eventos y Asincronía](#eventos-y-asincronía)
8. [GitHub & Management](#github--management)
   - [Gestión de Tareas y Asignación](#gestión-de-tareas-y-asignación)
   - [Flujo de CI/CD con GitHub Actions](#flujo-de-cicd-con-github-actions)
9. [Conclusión](#conclusión)
   - [Logros del Proyecto](#logros-del-proyecto)
   - [Aprendizajes Clave](#aprendizajes-clave)
   - [Trabajo Futuro](#trabajo-futuro)
10. [Apéndices](#apéndices)
    - [Licencia](#licencia)
11. [Referencias](#referencias)

---

## Introducción

### Contexto
En el sector de reparaciones del hogar y servicio técnico para electrodomésticos, la informalidad y la falta de transparencia dificultan el acceso a un servicio rápido, seguro y con precios justos. Los clientes suelen enfrentarse a cobros excesivos, falta de garantía en los repuestos y la incertidumbre de no conocer el perfil profesional del técnico que ingresa a sus hogares. Por otro lado, los técnicos independientes y de pequeñas empresas adolecen de canales eficientes para promocionar sus servicios dentro de sus zonas de cobertura geográfica. En este entorno surge **RepairMatch**, una solución tecnológica diseñada para mediar el proceso completo de diagnóstico, cotización, ejecución y valoración de servicios técnicos.

### Objetivos del Proyecto
* **Objetivo General:** Desarrollar e implementar un backend monolítico modular robusto, seguro y escalable utilizando Java y Spring Boot que gestione el flujo transaccional entre clientes y técnicos de electrodomésticos.
* **Objetivos Específicos:**
  * Implementar un sistema de autenticación de doble token (Access Token JWT y Refresh Token en BD con hash SHA-256) y control de acceso basado en roles (`CLIENT` y `TECHNICIAN`).
  * Diseñar un módulo de publicación de solicitudes con soporte para evidencias multimedia usando claves compuestas para asegurar la integridad débil de las entidades.
  * Desarrollar un algoritmo determinístico de búsqueda e interconexión (*matching*) que filtre técnicos según el tipo de electrodoméstico atendido y su radio de cobertura geolocalizado en kilómetros.
  * Implementar un mecanismo de selección transaccional con concurrencia mediante bloqueo pesimista (*Pessimistic Write*) para prevenir la doble contratación y garantizar atomicidad en la creación del servicio.
  * Proveer la trazabilidad del ciclo de vida del servicio (PROGRAMADO, EN_ATENCION, COMPLETADO, CANCELADO) y un módulo de reputación derivado de reseñas verificadas.

---

## Identificación del Problema o Necesidad

### Descripción del Problema
Actualmente, el mercado de reparación de electrodomésticos presenta tres barreras operativas principales:
1. **Asimetría de Información y Cotizaciones Desproporcionadas:** El usuario desconoce el costo real del diagnóstico inicial, lo que da lugar a cobros arbitrarios.
2. **Incompatibilidad Geográfica y Operativa:** Muchos técnicos aceptan solicitudes fuera de sus áreas operativas o para tipos de electrodomésticos sobre los cuales no poseen experiencia real.
3. **Falta de Trazabilidad y Garantía:** Las coordinaciones suelen perderse en chats informales donde no queda constancia formal del estado de la reparación ni de la veracidad de las calificaciones otorgadas a los técnicos.

### Justificación
La digitalización de este proceso mediante una API REST estructurada permite estandarizar las reglas del dominio. Al estructurar solicitudes con campos obligatorios, restringir la selección de propuestas a transacciones atómicas de base de datos y calcular la reputación de los técnicos a partir de reseñas únicas vinculadas exclusivamente a servicios completados, se eliminan los fraudes, se optimiza el tiempo de desplazamiento de los técnicos y se eleva la confianza del consumidor.

---

## Descripción de la Solución

### Funcionalidades Implementadas

* **Gestión de Identidad y Cuentas:** Registro segregado y autenticación de usuarios. Permite a los clientes publicar solicitudes y a los técnicos configurar sus perfiles con años de experiencia, biografía, teléfono, coordenadas geográficas (latitud/longitud), radio de cobertura en kilómetros y catálogo de tipos de electrodomésticos atendidos.
* **Módulo de Solicitudes y Evidencias:** Permite crear solicitudes especificando marca, modelo, descripción y tipo de electrodoméstico. Soporta la anexión secuencial de evidencias multimedia mediante la entidad débil `Evidence` estructurada con una clave compuesta (`requestId`, `evidenceNumber`).
* **Búsqueda y Matching Determinístico de Técnicos:** Consulta los perfiles de técnicos compatibles calculando la distancia haversine en kilómetros respecto a la ubicación de la solicitud y comprobando el cruce con el tipo de electrodoméstico solicitado.
* **Propuestas y Selección Atómica:** Los técnicos envían propuestas detallando costo de diagnóstico, tiempo de disponibilidad y condiciones. El cliente puede listar y comparar las propuestas de su solicitud. A través de transacciones aisladas, la aceptación de una propuesta cambia la solicitud a `CERRADA` y crea de manera atómica el registro en `ServiceEntity`.
* **Ciclo de Servicio y Reputación:** Control de transiciones de estado del servicio y registro de reseñas post-servicio. La reputación del técnico se calcula de forma dinámica agregando el promedio de calificaciones de los servicios completados.

### Tecnologías Utilizadas

* **Lenguaje y Framework:** Java 17 / Spring Boot 3.x.
* **Capa de Datos y Persistencia:** Spring Data JPA, Hibernate, PostgreSQL (Base de datos relacional) y H2 Database (para pruebas aisladas).
* **Seguridad y Cifrado:** Spring Security, Spring OAuth2 Resource Server (JWT con firma HMAC SHA-256), BCrypt Password Encoder para contraseñas y digest SHA-256 para Refresh Tokens.
* **Herramientas de Construcción y Utilidades:** Apache Maven, Lombok, Jakarta Validation.
* **Pruebas y Documentación API:** Postman (Colecciones y Entornos), JUnit 5 y Mockito.

---

## Modelo de Entidades


### Diagrama de Entidades
*(Diagrama Relacional del Modelo de Dominio)*

### Descripción de Entidades

1. **`User`**: Almacena las credenciales principales (email único, hash de contraseña cifrado con BCrypt) y asigna el rol del sistema (`CLIENT` o `TECHNICIAN`).
2. **`Technician`**: Extiende la información del usuario mediante una relación `@OneToOne` compartiendo la misma clave primaria (`UUID`). Registra el radio de servicio (`max_radius_km`), las coordenadas base de operaciones y mantiene una relación `@ManyToMany` con `ApplianceType`.
3. **`ApplianceType`**: Catálogo general de categorías de electrodomésticos (ej. Lavadoras, Refrigeradoras, Microondas).
4. **`Request`**: Solicitud creada por un cliente. Contiene la descripción del fallo, marca, modelo, ubicación geográfica y un estado dinámico (`PUBLICADA`, `CON_PROPUESTAS`, `CERRADA`, `CANCELADA`, `EXPIRADA`).
5. **`Evidence`**: Entidad débil cuya clave primaria es compuesta (`EvidenceId`: `requestId` y `evidenceNumber`). Representa imágenes o videos adjuntos a la solicitud.
6. **`Proposal`**: Oferta formal enviada por un técnico para atender una solicitud específica, incluyendo costo de diagnóstico y disponibilidad.
7. **`ServiceEntity`**: Registra la contratación formal derivada de una propuesta aceptada. Controla el ciclo de ejecución (`PROGRAMADO`, `EN_ATENCION`, `COMPLETADO`, `CANCELADO`).
8. **`ReviewEntity`**: Calificación única (1 a 5 estrellas) y comentario emitido por el cliente sobre un servicio completado. Garantiza mediante restricción `UNIQUE` que solo exista una reseña por servicio.
9. **`RefreshToken`**: Almacena el digest SHA-256 de los tokens de refresco, estado de revocación y fecha de expiración para mantener la sesión segura sin exponer el secreto.

---

## Manejo de Errores

El proyecto implementa una arquitectura centralizada para la captura y formateo de excepciones HTTP. A través de controladores que extienden las excepciones del dominio y componentes de respuesta de seguridad, la API entrega una estructura homogénea bajo el DTO `ErrorResponseDto`:

```json
{
  "timestamp": "2026-09-25T22:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "La solicitud especificada no existe",
  "path": "/api/requests/99",
  "errors": {}
}
```

* **Excepciones de Negocio Personalizadas:** Se lanzan excepciones específicas como `ResourceNotFoundException` (HTTP 404), `InvalidStateException` (HTTP 400), `ForbiddenOperationException` (HTTP 403) y `AuthenticationRequiredException` (HTTP 401).
* **Manejo de Errores de Seguridad:** El componente `SecurityErrorHandler` implementa `AuthenticationEntryPoint` y `AccessDeniedHandler`, capturando intentos de acceso no autenticados o faltas de permisos para responder en formato JSON UTF-8 estandarizado.

---

## Medidas de Seguridad Implementadas

### Seguridad de Datos

- **Cifrado de Contraseñas:** Se utiliza el algoritmo `BCryptPasswordEncoder` para aplicar hash a las contraseñas antes de persistirlas en la base de datos, incluyendo una validación máxima de 72 bytes para mitigar ataques DoS.
- **Autenticación Basada en Tokens JWT y Refresh Token:** Los Tokens de Acceso son firmados digitalmente mediante algoritmos simétricos `HS256` con un tiempo de vida corto (1 hora). Los Refresh Tokens de larga duración se almacenan en la base de datos previa aplicación de un algoritmo de hashing SHA-256 (`HexFormat`), previniendo la exposición del token original incluso si la base de datos es compromised.
- **Control de Acceso Fine-Grained (RBAC & Ownership):** La clase `AccountAccess` valida que el `subject` extraído del token coincida con el usuario en la base de datos, verificando además la propiedad de los recursos (`requireOwner`) para impedir que un usuario modifique datos de otros clientes o técnicos.

### Prevención de Vulnerabilidades
- **Prevención de Inyección SQL**: Se utiliza Spring Data JPA e Hibernate mediante consultas parametrizadas y @Query declarativas, eliminando por completo la concatenación manual de cadenas SQL.
- **Control de Concurrencia y Doble Aceptación**: Para prevenir condiciones de carrera (ej. dos clientes aceptando o agregando evidencias simultáneamente), RequestRepository y RefreshTokenRepository emplean bloqueos pesimistas de escritura (LockModeType.PESSIMISTIC_WRITE).
- **Protección CSRF y Stateless Session**s: Al ser una API REST sin estado basada en JWT, la protección CSRF es explícitamente deshabilitada en la configuración de Spring Security, ya que no se utilizan cookies de sesión vulnerables a ataques entre sitios.

---

## Eventos y Asincronía
En la arquitectura del backend, la gestión del ciclo de vida de las solicitudes y servicios requiere un desacoplamiento entre las operaciones HTTP sincrónicas y las tareas en segundo plano.
- **Uso de Transiciones de Estado Sincrónicas**: Operaciones críticas como la aceptación de una propuesta o el registro de una evidencia se procesan dentro de la misma transacción utilizando @Transactional.
- **Importancia de la Procesación Asincrónica (Proyección de Roadmap)**: Las tareas pesadas como la notificación push al técnico tras la publicación de una solicitud compatible, el envío de correos electrónicos de confirmación y la expiración automática de solicitudes en estado PUBLICADA después de transcurrido un tiempo límite se conciben mediante eventos asincrónicos (@EventListener / @Async). Esto evita bloquear el hilo de ejecución principal del servidor HTTP, garantizando un tiempo de respuesta optimizado.

---

## GitHub & Management
### Gestión de Tareas y Asignación (GitHub Issues & Projects)
La organización del desarrollo se gestionó mediante GitHub Issues y GitHub Projects, vinculados al Milestone principal MVP del backend - 2026-II.
- **Estandarización de Issues**: Cada tarea se registró siguiendo una convención semántica estricta (feat, test, chore) especificada por módulo (module:requests, module:proposals, module:services, module:matching, module:identity, module:shared).
- **Flujo de Revisión por Etiquetas**: Se emplearon etiquetas de estado (status:approved, status:needs-review, status:blocked, delivery) para visibilizar el avance de las entregas y las dependencias entre módulos.
- **Asignación de Responsabilidades**: Cada integrante asumió el control de los issues de su módulo, desde la definición del contrato hasta la ejecución de pruebas de integración y revisión cruzada por pares.

### Flujo de CI/CD con GitHub Actions
Se configuró un flujo de integración continua en .github/workflows/ ejecutado automáticamente ante cada pull request o push a la rama principal:
- **Verificación de Código**: Ejecución automatizada de compilación con Maven (mvn clean compile).
- **Pruebas de Integración**: Ejecución de la suite de pruebas JUnit 5 sobre la base de datos en memoria para validar las reglas de dominio y restricciones sin afectar el entorno de desarrollo.

---

## Conclusión
### Logros del Proyecto
Se logró la implementación completa del núcleo de backend para la plataforma RepairMatch, entregando una API REST funcional, segura e integrable. Se resolvieron exitosamente desafíos complejos como la selección atómica de propuestas con aislamiento transaccional pesimista, la modelación de claves compuestas para evidencias de solicitudes, el cálculo determinístico de distancias mediante la fórmula de Haversine para el matching técnico y el esquema seguro de autenticación JWT con revocación de tokens.
### Aprendizajes Clave
- Configuración avanzada de seguridad en Spring Boot 3 empleando autenticación de Resource Server JWT y componentes personalizados para el manejo de excepciones de acceso.
- Control estricto de concurrencia y prevención de vulnerabilidades de datos en bases de datos relacionales mediante transacciones de Spring y el uso de Pessimistic Write.
- Aplicación práctica de patrones de diseño como DTO, Mapper, Repository y Service para mantener una clara separación de responsabilidades en una arquitectura monolítica modular.
### Trabajo Futuro
- Integrar un motor de IA para la extracción y estructuración automática de marcas, modelos y síntomas a partir del texto descriptivo ingresado por el cliente.
- Implementar servicios de procesamiento asincrónico para el procesamiento de imágenes en segundo plano y notificaciones en tiempo real vía WebSockets.
- Incorporar una pasarela de pago para la custodia temporal de fondos hasta que el servicio pase al estado COMPLETADO.

---

## Apéndices
### Licencia
Este proyecto se distribuye bajo la licencia MIT License. Puedes hacer uso, copia, modificación y distribución del software respetando los términos de la licencia original.

---

## Referencias
- Spring Boot Documentation. Spring Data JPA & Transaction Management. Disponible en: https://docs.spring.io/spring-boot/reference/data/sql.html
- Spring Security Reference. OAuth2 Resource Server JWT. Disponible en: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Postman Docs. Writing tests and scripts in Postman. Disponible en: https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-examples/
