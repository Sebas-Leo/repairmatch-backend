# Identidad y autenticación JWT — Issue #4

## Configuración local

1. Crear un archivo `.env` en la raíz del proyecto:
   DB_PASSWORD=contraseña_local

2. Iniciar PostgreSQL:
   docker compose up -d postgres

3. Configurar las variables de entorno de la aplicación en IntelliJ:
    - DB_PASSWORD: la misma contraseña del archivo `.env`.
    - JWT_SECRET: una clave aleatoria de al menos 32 bytes,
      codificada en Base64.

4. Ejecutar RepairmatchBackendApplication con esa configuración.

La API utiliza el puerto 8080.
PostgreSQL en Docker utiliza localhost:5433.

No subir `.env`, claves reales ni tokens al repositorio.
Spring Boot no carga automáticamente el archivo `.env` de Docker Compose.

## Inicio de sesión

POST /api/auth/login
Content-Type: application/json

Enviar email y password de una cuenta registrada.

Respuesta correcta: 200 OK, con:
- accessToken
- tokenType: Bearer
- expiresIn: duración en segundos

Las credenciales incorrectas devuelven 401 Unauthorized
con el mensaje "Credenciales inválidas".

## Perfil autenticado

GET /api/users/me
Authorization: Bearer <accessToken>

Devuelve id, name, email y role de la cuenta autenticada.
No devuelve contraseña ni hash.

La identidad se obtiene del token, no de un identificador
enviado por el cliente.

## Tokens

Se firman con HS256 y duran 3600 segundos por defecto.
Se valida la firma, el emisor y el vencimiento.
El validador admite una tolerancia de reloj de 60 segundos.

La API no utiliza sesiones para guardar la autenticación.
Se implementa rotación de refresh tokens y revocación mediante logout. Los access tokens ya emitidos conservan su validez hasta vencer; ver docs/RUBRIC_IDENTITY.md.

## Pruebas automatizadas

Ejecutar desde Git Bash:
./mvnw.cmd test

Las pruebas usan H2 y una clave ficticia exclusiva de pruebas.

Casos cubiertos:
- Arranque de la aplicación.
- Registro, login y consulta del perfil.
- Acceso sin token.
- Token inválido.
- Contraseña incorrecta.
- Correo inexistente.
- Token vencido.
- Aislamiento del perfil entre dos usuarios.

Última ejecución reportada: 8 pruebas, 0 fallos y 0 errores.

## Postman

Las peticiones están en:
postman/collections/RepairMatch -Registro #4

Se guardan en el formato local de Postman, con archivos YAML.
Debe conservarse también la carpeta `.resources`.

El login guarda el token en la variable accessToken.
La consulta autenticada usa {{accessToken}}.
Mantener esa variable vacía en los archivos versionados.
## Renovación y CORS (rúbrica)

POST /api/auth/refresh recibe refreshToken y devuelve un nuevo par; el anterior no se reutiliza. POST /api/auth/logout revoca el refresh token y devuelve 204. CORS_ALLOWED_ORIGINS configura la lista de orígenes permitidos. La colección incluye los casos de rotación y revocación. Ver docs/RUBRIC_IDENTITY.md.
