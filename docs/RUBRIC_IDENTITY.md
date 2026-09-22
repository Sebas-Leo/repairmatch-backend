# Ajustes de identidad para la rúbrica

## Issue #3

Contraseña: mínimo 8 caracteres, máximo 72 caracteres y 72 bytes UTF-8; mayúscula, minúscula, número y símbolo. BCrypt no cambia. No se modifica la política de login para bloquear cuentas antiguas.

Errores de API: ErrorResponseDto expone timestamp, status, error, message y path, más errors para validación. detail se conserva como alias de message por compatibilidad con clientes anteriores. No se incluyen entradas rechazadas, contraseñas ni causas internas. Excepciones de dominio diferenciadas para correo duplicado, contraseña inválida, credenciales inválidas y cuenta no disponible.

Los ajustes del #4 y #5 se integrarán en sus ramas. La colección JSON de entrega se generará en la raíz desde los YAML, conservando estos como fuente editable.
