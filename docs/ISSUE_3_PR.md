# feat(identity): completar registro con ModelMapper, pruebas y evidencias

Refs #3

## Resumen

Completa la verificación del registro de cuentas CLIENT y TECHNICIAN. Los roles numéricos se rechazan como entradas inválidas y la unicidad del correo tiene una restricción nombrada. ModelMapper convierte User a UserResponseDto tanto en el registro como en la consulta de identidad, conservando la normalización del correo y BCrypt explícitos.

Incluye 39 casos de integración del registro, 8 solicitudes Postman en YAML y un ejecutor que lee esos mismos archivos. Organiza los YAML existentes del #4 sin cambiar sus peticiones. Resuelve la colisión de nombres EvidenceID/EvidenceId conservando la clase utilizada y combina las configuraciones de registro/JWT. Se excluyen archivos .env.

## Verificación

- Maven verify: 47 pruebas aprobadas (39 de registro, 7 de identidad, 1 de contexto), cero fallos/errores/omisiones.
- Newman sobre los 8 YAML actuales: 8 solicitudes y 26 aserciones aprobadas.
- Evidencias: docs/evidence/issue-3/README.md.
- Reproducción: docs/REGISTRATION.md.
- Entorno H2 aislado; esta ejecución no valida PostgreSQL ni concurrencia.

## Pendientes externos

- Confirmar con el responsable de perfiles cómo vinculará el UUID de User y cuándo creará Technician.
- Verificar status:approved antes de publicar el PR. La consulta sin autenticación devolvió 404; no se pudo comprobar la etiqueta.
- Obtener revisión de otro integrante y comprobaciones de CI aprobadas.
- Usar Closes #3 cuando estén confirmados todos los criterios. No cerrar el issue solo por completar esta verificación local.

Este archivo es la descripción preparada; todavía no se ha publicado un PR.
