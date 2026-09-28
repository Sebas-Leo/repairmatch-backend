# RepairMatch Backend — informe de presentación

**Curso:** CS 2031 Desarrollo Basado en Plataforma · **Periodo:** 2026-II

**Corte de evidencia:** 28 de septiembre de 2026

RepairMatch es una API para conectar clientes que necesitan reparar electrodomésticos con técnicos compatibles. El recorrido previsto es: registro, solicitud, búsqueda de técnicos, propuesta, selección, servicio y reseña. La entrega académica es **solo backend**; no incluye frontend ni pagos.

## Estado comprobable

| Entrega | Estado al corte | Evidencia |
| --- | --- | --- |
| Base Spring Boot, identidad y módulos ya integrados | En `main` | Código, pruebas de CI y [README](../README.md) |
| Recorrido MVP completo | Implementado en la rama del [PR #42](https://github.com/Sebas-Leo/repairmatch-backend/pull/42), pendiente de revisión e integración | Maven `verify`: 110 pruebas correctas; Node: 8 pruebas correctas; [guía de ejecución](https://github.com/Sebas-Leo/repairmatch-backend/blob/feat/local-backend-completion/docs/LOCAL_BACKEND.md) |
| Imagen Java 17 | Integrada mediante el [PR #46](https://github.com/Sebas-Leo/repairmatch-backend/pull/46) | Construcción Docker y arranque comprobados con PostgreSQL temporal |
| API y PostgreSQL con Compose local | Pendiente de revisión en el [PR #41](https://github.com/Sebas-Leo/repairmatch-backend/pull/41) | CI correcto; instrucciones en el propio PR |

Que un PR tenga pruebas correctas **no equivale a estar fusionado**. Para exponer funcionalidades del MVP todavía no integradas, usar explícitamente la rama `feat/local-backend-completion` y explicar esa diferencia.

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

Estas reglas describen el MVP del PR #42 y deben presentarse como integradas en `main` únicamente después de que ese PR sea fusionado y verificado allí.

## Guion de demostración

1. Mostrar la rama y el commit que se va a presentar; no llamar `main` a una rama pendiente.
2. Ejecutar `./mvnw.cmd --batch-mode --no-transfer-progress verify` y registrar el resultado real.
3. Iniciar una base PostgreSQL **desechable** y la API con credenciales de prueba proporcionadas por variables de entorno. `JWT_SECRET` debe ser una clave Base64 de al menos 32 bytes decodificados.
4. En la rama del PR #42, ejecutar `node scripts/smoke-backend.cjs http://127.0.0.1:8080`. El script comprueba el flujo HTTP y varios rechazos de autorización, duplicados y estados inválidos; escribe datos sintéticos, por lo que no debe apuntar a una base real.
5. Si se demuestra concurrencia, usar únicamente la base desechable y el procedimiento de `scripts/check-selection-postgres.cjs` documentado en la guía del PR #42.
6. Cerrar la demostración mostrando qué PR está integrado, cuál sigue pendiente y qué evidencia respalda cada afirmación.

## Límites para el despliegue

El Dockerfile del PR #46 produce una imagen ejecutable, pero eso **no demuestra por sí solo** un despliegue listo para producción. La configuración actual usa actualización automática del esquema; no hay migraciones versionadas ni una política verificada de migración de datos existentes. Antes de un entorno persistente se necesitan respaldo, migración explícita, secretos externos y comprobación de salud operativa. No incluir credenciales reales en Postman, capturas o repositorio.

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
