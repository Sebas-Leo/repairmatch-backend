# RepairMatch Backend — informe de presentación

**Curso:** CS 2031 Desarrollo Basado en Plataforma · **Periodo:** 2026-II

**Corte de evidencia:** 28 de septiembre de 2026

RepairMatch es una API para conectar clientes que necesitan reparar electrodomésticos con técnicos compatibles. El recorrido previsto es: registro, solicitud, búsqueda de técnicos, propuesta, selección, servicio y reseña. La entrega académica es **solo backend**; no incluye frontend ni pagos.

## Estado comprobable

| Entrega | Estado al corte | Evidencia |
| --- | --- | --- |
| Base Spring Boot, identidad y módulos ya integrados | En `main` | Código, pruebas de CI y [README](../README.md) |
| Recorrido MVP completo | Integrado mediante el [PR #42](https://github.com/Sebas-Leo/repairmatch-backend/pull/42) | Maven `verify`: 110 pruebas correctas; Node: 8 pruebas correctas; [guía de ejecución](LOCAL_BACKEND.md) |
| Imagen Java 17 | Integrada mediante el [PR #46](https://github.com/Sebas-Leo/repairmatch-backend/pull/46) | Construcción Docker y arranque comprobados con PostgreSQL temporal |
| API y PostgreSQL con Compose local | Integrado mediante el [PR #41](https://github.com/Sebas-Leo/repairmatch-backend/pull/41) | CI correcto; instrucciones en el propio PR |

El MVP y Compose están integrados en `main`. Registrar el commit utilizado en la demostración y ejecutar las verificaciones sobre ese commit.

## Arquitectura y reglas de negocio

La aplicación es un monolito modular con Java 17, Spring Boot, API REST, Spring Data JPA y PostgreSQL. La autenticación usa JWT; la contraseña se almacena con BCrypt. Las pruebas automatizadas usan H2, pero las garantías de concurrencia requieren verificación adicional con PostgreSQL.

| Área | Regla que debe demostrarse |
| --- | --- |
| Identidad | El usuario autenticado y su rol se obtienen del token, no de un identificador de propietario enviado por el cliente. |
| Solicitudes | Una solicitud pertenece a un cliente y a un tipo de electrodoméstico; las evidencias dependen de la solicitud. |
| Compatibilidad | Coinciden el tipo atendido y la distancia Haversine dentro del radio del técnico. |
| Propuestas | Solo un técnico compatible puede ofertar; se rechaza una segunda oferta del mismo técnico para la misma solicitud. |
| Selección | Solo el propietario acepta; la transacción cierra la solicitud, decide las propuestas y crea un único contrato de servicio. |
| Servicios y reseñas | El servicio tiene transiciones autorizadas; solo el cliente contratante reseña una vez tras completarlo. |
| Reputación | Se deriva de las reseñas de servicios reales, no de un campo editable del perfil. |

Estas reglas corresponden al MVP integrado mediante el PR #42.

## Guion de demostración

1. Mostrar la rama y el commit que se va a presentar; no llamar `main` a una rama pendiente.
2. Ejecutar `./mvnw.cmd --batch-mode --no-transfer-progress verify` y registrar el resultado real.
3. Iniciar una base PostgreSQL **desechable** y la API con credenciales de prueba proporcionadas por variables de entorno. `JWT_SECRET` debe ser una clave Base64 de al menos 32 bytes decodificados.
4. En `main`, ejecutar `node scripts/smoke-backend.cjs http://127.0.0.1:8080`. El script comprueba el flujo HTTP y varios rechazos de autorización, duplicados y estados inválidos; escribe datos sintéticos, por lo que no debe apuntar a una base real.
5. Si se demuestra concurrencia, usar únicamente la base desechable y el procedimiento de `scripts/check-selection-postgres.cjs` documentado en la guía del PR #42.
6. Cerrar la demostración mostrando qué PR está integrado, cuál sigue pendiente y qué evidencia respalda cada afirmación.

## Límites para el despliegue

La imagen usa Java 17 y un usuario no privilegiado. El perfil `prod` valida el esquema, desactiva Swagger y expone salud operativa. La [guía de despliegue académico](AWS_DEPLOY.md) documenta las variables y el arranque; no se ha realizado un despliegue AWS. No hay migraciones versionadas ni una política verificada de migración de datos existentes. Antes de un entorno persistente se necesitan respaldo, migración explícita, secretos externos y comprobación de salud operativa. No incluir credenciales reales en Postman, capturas o repositorio.

No se incluyen frontend, pagos, diagnóstico por IA, notificaciones, geocodificación ni almacenamiento remoto de evidencias. Tampoco se declara una licencia de software: el repositorio no contiene un archivo `LICENSE`.

## Equipo y responsabilidades

| Integrante | Área principal |
| --- | --- |
| Ariana Belén Blanco Anicama | Identidad y acceso |
| Camila Araceli Alfaro Chuquino | Solicitudes, evidencias y tipos de electrodomésticos |
| Jairo André Cunya Villalta | Perfil técnico y compatibilidad |
| Royer Sebastian Ramos Vargas | Propuestas y selección |
| Adrian Luis Pacheco Sulluchuco | Ciclo del servicio y reseñas |

Las responsabilidades son de equipo; la integración y revisión cruzada deben apoyarse en código, pruebas y PR concretos, no en afirmaciones generales de finalización.
