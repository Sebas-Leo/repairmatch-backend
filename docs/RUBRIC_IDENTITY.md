# Ajustes de identidad para la rúbrica

## Issue #3

Contraseña: mínimo 8 caracteres, máximo 72 caracteres y 72 bytes UTF-8; mayúscula, minúscula, número y símbolo. BCrypt no cambia. No se modifica la política de login para bloquear cuentas antiguas.

Errores de API: ErrorResponseDto expone timestamp, status, error, message y path, más errors para validación. detail se conserva como alias de message por compatibilidad con clientes anteriores. No se incluyen entradas rechazadas, contraseñas ni causas internas. Excepciones de dominio diferenciadas para correo duplicado, contraseña inválida, credenciales inválidas y cuenta no disponible.

Esta rama integra los ajustes del #3, #4 y #5. La colección JSON de entrega se genera en la raíz desde los YAML, conservando estos como fuente editable.

## Issue #4

Login usa DatabaseUserDetailsService y DaoAuthenticationProvider. El JWT contiene sub, userId, email, role y roles; se mantiene el filtro BearerTokenAuthenticationFilter de Spring Resource Server, con validación HS256, emisor y vencimiento. Este filtro del framework cumple la extracción y validación; no se añade un segundo filtro manual llamado JwtAuthenticationFilter. Confirmar con el docente si exige esa clase literal.

Login devuelve accessToken, tokenType, expiresIn, refreshToken y refreshExpiresIn. POST /api/auth/refresh rota el refresh token una sola vez mediante bloqueo de fila transaccional. Solo se guarda SHA-256 del token aleatorio de 256 bits. POST /api/auth/logout revoca el refresh token; el access token ya emitido sigue válido hasta vencer. No se usan cookies. Configurar JWT_SECRET (Base64 de al menos 32 bytes) y CORS_ALLOWED_ORIGINS. Orígenes locales predeterminados: localhost:3000 y localhost:5173; producción debe fijar su lista explícita. Refresh dura 7 días y access 1 hora por defecto.

Los errores 401 y 403 del filtro usan el mismo ErrorResponseDto. CORS permite preflight solo para orígenes configurados. Añadir migración de refresh_tokens al integrar la estrategia común de esquema; en desarrollo el proyecto aún usa ddl-auto=update.

## Issue #5

EnableMethodSecurity y PreAuthorize protegen los métodos de lectura/escritura de evidencias, cancelación y cierre interno de solicitudes con CLIENT. La propiedad continúa comprobándose contra Request.client en AccountAccess y RequestService; una anotación de rol no la sustituye. Se añaden excepciones de recurso no encontrado, operación prohibida, estado inválido y autenticación requerida, usadas en los controles reales.

La colección postman_collection.json en la raíz se genera con node scripts/export-postman.cjs a partir de las colecciones YAML. Incluye descripciones, variables, autorización y ejemplos ilustrativos; los resultados reales se guardan como evidencias. Requiere instalar las dependencias de verificación en target como indica docs/AUTHORIZATION.md. La colección contiene todos los endpoints actualmente implementados, no los módulos futuros.

El #5 sigue parcial: ya se integra la comparación de propuestas para el cliente propietario, pero faltan el resto del ciclo de propuestas, servicios y reseñas y el acuerdo de sus políticas. Estos cambios no completan los requisitos globales de eventos, correo, despliegue ni el informe de 1000–2000 palabras, que corresponden al equipo.
