# Haz visible cada contribución

Usa una tarea pequeña por entregable, una rama y una solicitud de cambios (PR) revisada. Consulta el [tablero compartido](https://github.com/Sebas-Leo/repairmatch-backend/issues/1). El README propone responsables por módulo; las asignaciones en GitHub siguen pendientes hasta que los integrantes compartan sus usuarios y acepten las invitaciones.

## Ruta diaria

1. Busca tareas existentes. Usa **Tarea de backend** para trabajo nuevo; define el problema, la solución, las dependencias y criterios de aceptación observables con casillas. Las tareas iniciales son propuestas, no trabajo aprobado.
2. Un responsable de mantenimiento revisa el alcance, asigna al colaborador y exactamente una etiqueta `module:*`, quita `status:needs-review` y agrega `status:approved`. Conserva la aprobación durante el trabajo; usa `status:in-progress`, `status:blocked` o `status:in-review` para indicar el estado actual (solo una a la vez).
3. Parte de `main` actualizada, crea `feature/<numero>-descripcion-breve` y realiza commits convencionales pequeños, por ejemplo `feat(proposals): validar propietario de la propuesta`. Incluye pruebas y documentación con cada comportamiento. Nunca subas credenciales ni datos de producción.
4. Abre una PR con título convencional. Incluye **una línea independiente `Closes #123` por cada tarea terminada**, o `Refs #123` para trabajo parcial de este repositorio. Sustituye las instrucciones de la plantilla por comandos, resultados reales y enlaces de evidencia sin datos sensibles.
5. Solicita la revisión de otro integrante. Espera su aprobación y las comprobaciones requeridas, resuelve los comentarios y combina con *squash* y un título de commit convencional. No envíes cambios directamente a `main`.
6. Revisa la tarea y el tablero después de combinar. `Closes` cierra las tareas terminadas al combinar en la rama predeterminada; `Refs` las mantiene abiertas. No cierres trabajo incompleto para mejorar el tablero.

## Qué hace la automatización (y qué no)

| Automatización | Comprobaciones y límites |
| --- | --- |
| **Comprobaciones de PR / Contribution contract** | Revisa el formato del título, referencias a tareas de este repositorio, `status:approved` en cada tarea y texto de verificación no vacío. **No** comprueba que la evidencia sea verdadera, que las pruebas pasen ni que exista aprobación de otro integrante. Cambiar solo la etiqueta de aprobación no repite esta comprobación: edita la descripción de la PR o vuelve a ejecutarla desde Actions. |
| **Progreso del equipo** | Se actualiza con eventos de tareas y PR, cambios en `main` o ejecución manual desde Actions. Muestra responsables propuestos y asignados, estados declarados, casillas de aceptación, PR vinculadas, integraciones en `main` y última actualización. Solo cuenta tareas `delivery`; excluye los seguimientos `planning-only`. |
| **Comprobaciones de PR / Workflow tests** | Ejecuta pruebas simuladas de la lógica de automatización en PR y cambios en `main`, con permisos de lectura y sin secretos. Comprueba el análisis de metadatos y los informes, no la funcionalidad del backend. |
| **Integración continua del backend** | `Backend verify` ejecuta `./mvnw --batch-mode --no-transfer-progress clean verify`. Las pruebas usan H2 aislada. La colección Postman y sus instrucciones están en `docs/REGISTRATION.md`; una comprobación de contribución exitosa **no** equivale a pruebas del backend aprobadas. |

El tablero distingue **cerradas sin evidencia de integración**, **cerradas con evidencia de integración** y **canceladas/no planificadas**. Una integración parcial con `Refs` es evidencia para revisar, no prueba de que toda la tarea funcione. Solo cuenta casillas de la sección `### Criterios de aceptación` (también acepta `### Acceptance criteria`), no las comprobaciones previas. Los conteos son registros, no porcentajes de finalización, notas, horas, esfuerzo ni clasificaciones de rendimiento personal. Revisa los cambios vinculados y sus pruebas en conjunto.

## Configuración y seguridad

- Las etiquetas y tareas se administran en GitHub. Elegir un módulo en el formulario **no** aplica su etiqueta automáticamente; debes asignarla durante la revisión inicial. La etiqueta de aprobación es una convención del equipo, no un permiso imposible de alterar.
- Configura la variable de Actions `PROGRESS_ISSUE_NUMBER` con el número de la tarea del tablero. Esta debe tener la etiqueta `dashboard`; el flujo rechaza otros destinos y nunca crea commits ni envía archivos. Para actualizar manualmente, usa **Actions → Progreso del equipo → Run workflow**.
- La protección actual de `main` exige una PR, **una aprobación de otro integrante**, conversaciones resueltas y las comprobaciones **`Contribution contract` y `Workflow tests`**. También se aplica a administradores; no permite envíos forzados ni eliminar la rama. Incorpora las comprobaciones reales del backend cuando existan.
- Los nombres técnicos de las comprobaciones requeridas y las claves de etiquetas (`module:*`, `status:*`, `delivery`, `planning-only`, `dashboard`) se conservan en inglés para mantener las reglas y automatizaciones. El tablero traduce sus valores conocidos para la lectura. Las PR anteriores con `## Verification` siguen siendo compatibles con la nueva sección `## Verificación`.
- El tablero usa `pull_request_target` solo para leer metadatos y actualizar la tarea designada: sin descargar código de la PR, ejecutarlo ni incluir credenciales en su contenido. Mantén este límite al editar los flujos. Las actualizaciones aparecen como actividad del bot de GitHub Actions, no como commits humanos de implementación.
- Comprobación local: `node --test tests/workflows.test.cjs`. Estas pruebas simuladas de metadatos no son pruebas de la aplicación.

## Evidencia que debe aportar cada módulo

Cada responsable debe aportar endpoints y persistencia, reglas de negocio y controles de propiedad, pruebas automatizadas de casos válidos e inválidos, solicitudes y resultados Postman sin datos sensibles y revisión de otro integrante. Acuerda los contratos de API y dependencias de integración antes de implementar en paralelo; revisa en equipo el límite entre selección y creación del servicio.
