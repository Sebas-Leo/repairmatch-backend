# Verificación del issue #3 — 22/09/2026

Resultados de la rama feature/3-pruebas-evidencia después de integrar el PR #31 y usar ModelMapper en las respuestas de registro e identidad.

| Comprobación | Resultado |
| --- | --- |
| Maven verify con Java 17 y ModelMapper | 47 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS |
| Registro | 39 casos aprobados |
| Autenticación e identidad JWT (#4) | 7 casos aprobados |
| Contexto Spring | 1 caso aprobado |
| Ocho solicitudes YAML actuales | 8 solicitudes y 26 aserciones, 0 fallos |

Ejecución HTTP: 2026-09-22T18:38:30.275Z. El script scripts/run-registration-postman.cjs lee directamente los YAML y pasa los mismos cuerpos y scripts a Newman 6.2.1 en memoria. No se ejecutó la interfaz gráfica de Postman ni se mantuvo una colección JSON duplicada.

Entorno: Java 17.0.20, Spring Boot 4.1.1, ModelMapper 3.2.4, H2 en memoria en modo PostgreSQL y API local 127.0.0.1:18083. Solo se utilizaron datos ficticios. Los TXT de Surefire, el resumen Maven y el reporte JUnit de Newman conservan los resultados reales. Se omiten logs completos de Spring y propiedades del sistema.

Reproducción en [REGISTRATION.md](../../REGISTRATION.md). Los casos Postman cubren cliente, técnico, duplicado, correo inválido, nombre vacío, contraseña corta, contraseña de más de 72 bytes y rol desconocido. Los casos adicionales siguen cubiertos en Java.

Límites: no se verificó PostgreSQL ni concurrencia en esta ejecución. No se atribuye aprobación a otro integrante ni se declara cerrado el issue. Quedan por confirmar el contrato de perfiles, status:approved y la revisión del PR.
