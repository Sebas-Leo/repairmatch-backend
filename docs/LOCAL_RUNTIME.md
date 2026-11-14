# Ejecutar el backend localmente con Docker

Esta configuración ejecuta la API y su propia base PostgreSQL sin modificar otra instalación que ya utilice el puerto 5432. Ambos puertos publicados aceptan conexiones únicamente desde el equipo local.

## Iniciar

Desde la raíz del repositorio en PowerShell, con JDK 17 o superior y Docker Desktop en ejecución:

```powershell
if (-not (Test-Path .env)) {
  $dbPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
  $jwtSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  @("DB_PASSWORD=$dbPassword", "JWT_SECRET=$jwtSecret") | Set-Content .env
}
.\mvnw.cmd --batch-mode --no-transfer-progress verify
docker compose up -d api
docker compose ps
```

El archivo `.env` generado permanece en este equipo y Git lo ignora. No copies `.env.example` sin cambiar sus valores: son marcadores de posición. La compilación Maven debe terminar antes de iniciar `api`, porque Compose monta el JAR generado. Después de reconstruir el JAR, ejecuta `docker compose up -d --force-recreate api` para usar la nueva versión.

| Servicio | Dirección | Uso |
| --- | --- | --- |
| API | `http://127.0.0.1:8080` | Acceso HTTP local |
| PostgreSQL | `127.0.0.1:5433` | Acceso local a la base desde Windows |

Dentro de Compose, la API se conecta a `postgres:5432` por la red privada del proyecto. No se modifica el PostgreSQL que ya utiliza el puerto 5432 del equipo. Abre `http://127.0.0.1:8080/v3/api-docs` para confirmar que la API responde después del arranque.

Para detener los servicios sin borrar sus datos:

```powershell
docker compose down
```

No agregues `-v` salvo que quieras eliminar el volumen de la base del proyecto. La configuración actual `ddl-auto=update` sirve para desarrollo local, no sustituye migraciones de producción.
