# Backend MVP: ejecución y verificación local

Esta entrega conecta identidad, solicitudes, compatibilidad, propuestas, contratación, servicios y reseñas. El recorrido MVP está integrado en main mediante el PR #42; usar una base desechable para las verificaciones.

## Arranque

Requisitos: JDK 17 o superior, Node.js 18 o superior para la verificación HTTP y Docker Desktop para la instancia PostgreSQL aislada. Maven Wrapper fija las dependencias. No se requiere frontend.

En PowerShell, desde la raíz del repositorio:

```powershell
# Crear .env solo la primera vez. Está ignorado por Git; no compartirlo.
if (-not (Test-Path .env)) {
  $dbPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
  $jwtSecret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  @("DB_PASSWORD=$dbPassword", "JWT_SECRET=$jwtSecret") | Set-Content .env
}
.\mvnw.cmd --batch-mode --no-transfer-progress verify
docker compose up -d api
docker compose ps
```

El PostgreSQL instalado en Windows puede seguir usando **5432**. Docker publica el PostgreSQL de RepairMatch solo en **127.0.0.1:5433** y la API solo en **127.0.0.1:8080**. La API dentro de Docker se comunica con la base por la red privada del proyecto, no por 5433. Para detener ambos contenedores sin borrar datos: `docker compose down` (sin `-v`).

Si se prefiere ejecutar Spring directamente en Windows, configurar `DB_URL=jdbc:postgresql://127.0.0.1:5433/repairmatch`, `DB_USER=repairmatch`, `DB_PASSWORD` y `JWT_SECRET` en la sesión antes de `spring-boot:run`. Spring no carga `.env` automáticamente. Mantener una clave JWT estable durante una sesión; cambiarla invalida los tokens de acceso anteriores. No guardar credenciales en archivos versionados ni compartirlas.

## Recorrido reproducible

Con la API ejecutándose, en otra terminal:

```powershell
node scripts/smoke-backend.cjs http://127.0.0.1:8080
```

El script usa únicamente endpoints públicos de la API, genera cuentas ficticias distintas en cada ejecución y comprueba respuestas HTTP. Cubre registro, login, catálogo, perfil técnico, cobertura, solicitud con coordenadas, compatibilidad, propuesta, comparación, aceptación, atención, finalización, reseña y reputación. También rechaza duplicados, comparación no autorizada, aceptación repetida y reseña anticipada.

**Escribe datos sintéticos que permanecen en la base elegida.** Ejecutarlo solo contra una base local de prueba. El script restringe su destino a loopback y no imprime tokens.

Alternativa Postman: importar `postman/RepairMatch-MVP.postman_collection.json` y ejecutar toda la colección en orden, con `baseUrl` local. Esta colección deriva del mismo escenario, no de datos sembrados directamente en SQL:

```powershell
node scripts/smoke-backend.cjs --export
```

Vaciar tokens y variables de sesión antes de exportar o compartir resultados de Postman.

## Reglas de esta implementación local

| Tema | Política |
| --- | --- |
| Identidad | `sub` UUID del JWT y cuenta/rol persistidos; no confiar en identificadores de propietario enviados por el cliente. |
| Compatibilidad | Tipo atendido y distancia Haversine dentro del radio. La disponibilidad se expresa en la oferta, no es un filtro automático. |
| Coordenadas | Latitud y longitud válidas juntas. Las solicitudes antiguas sin ubicación no aparecen en matching; el propietario puede completar la ubicación mientras estén abiertas. |
| Propuestas | Técnico compatible, costo no negativo, disponibilidad futura y condiciones. Una oferta por técnico y solicitud; duplicados devuelven 409. |
| Selección | Solo propietario; una transacción acepta una propuesta, rechaza alternativas, cierra la solicitud y crea un servicio. Bloqueo de la solicitud y unicidad de base de datos protegen la concurrencia. Repetición: 409. |
| Servicios | Solo participantes pueden consultar. El técnico asignado inicia y completa; los participantes pueden cancelar un servicio programado. Estados finales no se reabren. |
| Reseñas | Solo cliente contratante, después de completar el servicio, nota de 1 a 5, una por servicio. |
| Reputación | Derivada de las reseñas vinculadas por servicio y propuesta al técnico real; sin reseñas, promedio 0 y cantidad 0. |
| Privacidad | Perfil público sin correo, teléfono ni coordenadas exactas. Matching devuelve solo los datos necesarios para ofertar. |

Estas reglas resuelven los puntos pendientes para poder probar el MVP localmente; el equipo debe revisarlas antes de publicar.

## Esquema y datos anteriores

El prototipo anterior de servicios/reseñas usaba IDs numéricos sin relaciones reales y un técnico fijo. Los nuevos contratos se guardan en `service_contracts` y `service_reviews`; las tablas antiguas `services` y `reviews` no se borran ni se copian automáticamente. Sus filas no se presentan como contratos válidos.

Se conserva `ddl-auto=update` para desarrollo. **No es una migración versionada ni una estrategia de despliegue de producción.** Si una base anterior tiene datos incompatibles con las restricciones nuevas, respaldarla y preparar una migración explícita; no resolverlo eliminando datos. Las pruebas JUnit usan H2 con `create-drop`; la prueba PostgreSQL debe ejecutarse sobre una base desechable porque inserta datos sintéticos y crea temporalmente un trigger.

## Verificación automatizada

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
node --test tests/workflows.test.cjs
```

Las pruebas estándar utilizan H2. `LocalBackendFlowIntegrationTests` comprueba el recorrido de propuesta, selección, servicio, reseña y reputación junto con permisos y un técnico incompatible. La validación de selección simultánea y rollback debe ejecutarse también contra PostgreSQL; consultar el resultado real de la sesión, no asumir que H2 demuestra el comportamiento de PostgreSQL.

Con la API y la base aislada de Docker en marcha, comprobar las garantías transaccionales reales:

```powershell
$env:REPAIRMATCH_DISPOSABLE_DB = '1'
node scripts/check-selection-postgres.cjs http://127.0.0.1:8080
```

El script exige el contenedor Compose del proyecto, crea datos sintéticos y usa temporalmente un trigger que impide crear un contrato para verificar el rollback. Retira el trigger al terminar; no ejecutarlo contra datos importantes. La selección concurrente debe terminar con un contrato y un conflicto.

## Límites

No se incluyen frontend, pagos, IA, geocodificación, notificaciones ni almacenamiento remoto de archivos. Las evidencias son referencias URL; el backend no descarga ni hospeda esos archivos. No se habilita ningún endpoint para fabricar servicios de prueba. La aceptación de una propuesta es la única vía HTTP que crea un contrato.
