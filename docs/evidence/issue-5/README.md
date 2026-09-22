# Evidencias parciales del issue #5 — 22/09/2026

Rama: feature/5-permisos-reglas. Java 17.0.20, Spring Boot 4.1.1, base H2 en memoria, JWT firmados y datos ficticios.

| Verificación | Resultado |
| --- | --- |
| Maven verify | 67 pruebas aprobadas, 0 fallos, 0 errores, 0 omisiones; BUILD SUCCESS |
| PermissionIntegrationTests | 20 casos aprobados |
| Colección YAML de permisos | 12 solicitudes, 18 aserciones, 0 fallos |

Ejecución HTTP: 2026-09-22T19:38:31.168Z. scripts/run-permissions-postman.cjs lee directamente los YAML y ejecuta sus cuerpos y scripts con Newman 6.2.1. No se usó la interfaz gráfica de Postman ni una colección JSON mantenida por separado. Las cuentas de prueba se crearon con PermissionApiTestApplication exclusivamente en H2; no se tocó PostgreSQL.

Se verificaron accesos del propietario, otro cliente, técnico y usuario sin token; rechazos sin cambios en los recursos; bloqueo del cierre directo; cambio de rol y cuenta eliminada. Las pruebas previas de registro y JWT también pasaron.

Estos resultados solo acreditan solicitudes y evidencias. Propuestas, servicios y reseñas siguen pendientes en el proyecto; no se simularon como si existieran. El #5 no se puede cerrar con esta entrega parcial. Las reglas para los módulos futuros están propuestas en [AUTHORIZATION.md](../../AUTHORIZATION.md) y requieren coordinación con sus responsables.
