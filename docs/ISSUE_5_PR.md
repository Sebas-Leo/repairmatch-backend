# feat(identity): proteger solicitudes y evidencias por rol y propietario

Refs #5

## Resumen

Un usuario autenticado podía leer evidencias de solicitudes ajenas; algunos rechazos de propiedad tampoco tenían un estado HTTP definido. Esta entrega exige CLIENT y propiedad persistida para consultar/adjuntar evidencias y cancelar solicitudes, con respuestas 401/403/404/409 según el caso. La identidad se resuelve desde el JWT en el servicio y se verifica contra la cuenta actual, sin aceptar identificadores proporcionados por el consumidor como autorización.

Se bloquea PATCH /api/requests/{id}/close: el contrato del proyecto exige cerrar la solicitud dentro de la aceptación de una propuesta y la creación atómica del servicio. Ese flujo sigue pendiente de su módulo.

## Verificación

- Maven verify: 67 pruebas aprobadas, incluyendo 20 de permisos; cero fallos/errores.
- Postman YAML mediante Newman: 12 solicitudes y 18 aserciones aprobadas.
- Evidencias: docs/evidence/issue-5/.
- Política y reproducción: docs/AUTHORIZATION.md.
- Base H2 aislada; PostgreSQL no verificado en esta ejecución.

## Alcance pendiente

Esta entrega NO completa el #5. Faltan los módulos de propuestas, servicios y reseñas y sus pruebas reales de propiedad. La matriz compartida documenta reglas propuestas que deben revisarse con los responsables, incluida la visibilidad de datos para técnicos compatibles y las transiciones de servicio. Mantener Refs #5; no usar Closes #5 todavía. Confirmar status:approved antes de abrir el PR y solicitar revisión independiente.
