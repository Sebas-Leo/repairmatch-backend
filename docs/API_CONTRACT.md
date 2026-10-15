# RepairMatch API contract (frontend reference)

Base URL (local): `http://localhost:8080/api`. All bodies are `application/json`. Machine-readable spec (dev/test only; disabled in `prod`): `/v3/api-docs`, UI at `/swagger-ui.html`.

## Conventions

- **Auth**: `Authorization: Bearer <accessToken>` on everything except the public endpoints marked "public".
- **Dates**: ISO-8601 strings, no timezone (`LocalDateTime`, e.g. `2026-10-14T22:02:19.123`). Send `availableAt` in the same format.
- **Enums**: strings only (numbers are rejected). `Role`: `CLIENT | TECHNICIAN`. Request status: `PUBLICADA | CON_PROPUESTAS | CERRADA | CANCELADA | EXPIRADA`. Proposal status: `PENDING | ACCEPTED | REJECTED`. Service status: `PROGRAMADO | EN_ATENCION | COMPLETADO | CANCELADO`. Request/proposal `status` fields are plain strings with those values.
- **Money**: `diagnosticCost` is a decimal number (max 10 integer + 2 fraction digits).
- **IDs**: users/technicians are UUID strings; requests, proposals, services, appliance types are numbers.
- **Lists**: plain JSON arrays, no envelope, no pagination, always scoped to the authenticated user. Only `GET /technicians/me/matching-requests` takes `page` (default 0) / `size` (default 20, max 100) and still returns a plain array.
- **Messages are mixed Spanish/English** (server-side text). Show `message` as-is or map by `status`.

## Error shape (every error path)

```json
{ "timestamp": "2026-10-14T22:02:19.123Z", "status": 400, "error": "Bad Request",
  "message": "Revisa los campos enviados", "path": "/api/requests",
  "errors": { "originalDescription": "La descripción de la falla es obligatoria" },
  "detail": "Revisa los campos enviados" }
```

`message` is always present (`detail` duplicates it, kept for compatibility). `errors` is `{}` except validation (400, field -> message) and invalid query parameters (`{ param: ... }`). Used for 400, 401, 403, 404, 405, 415, 409, 500, including Spring Security 401/403 (`WWW-Authenticate: Bearer` on 401). Stack traces are never returned.

| Status | When |
|---|---|
| 400 | Bean validation, malformed JSON / unknown enum value in body, bad query param (e.g. `?status=BOGUS`), incomplete technician profile |
| 401 | Missing/invalid/expired access token, bad credentials, invalid refresh token |
| 403 | Wrong role, not the owner/participant, technician incompatible with request |
| 404 | Unknown resource or unknown path |
| 405 / 415 | Wrong HTTP method / non-JSON `Content-Type` |
| 409 | Invalid state transition, duplicate email, duplicate proposal/review, DB integrity violation |
| 500 | Unexpected error (generic message) |

## Auth flow

- `POST /auth/login` and `/auth/refresh` return `{ accessToken, tokenType: "Bearer", expiresIn (s, 3600), refreshToken, refreshExpiresIn (s, 604800) }`.
- Refresh tokens are **single-use**: `/auth/refresh` revokes the sent token and returns a new pair. On a 401 from `/auth/refresh`, force re-login.
- `/auth/logout` revokes the given refresh token (204). The access token stays valid until it expires (stateless JWT).
- JWT claim `role` drives authorization; role is re-checked against the DB on every protected call.

## CORS

Allowed origins come from `CORS_ALLOWED_ORIGINS` (comma-separated, no `*`; default `http://localhost:3000,http://localhost:5173`). Applies to `/api/**`. Methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`; request headers `Authorization, Content-Type`; exposed header `WWW-Authenticate`; credentials **disabled** (use the Bearer header, not cookies); preflight cached 1 h and answered before authentication. In production set `CORS_ALLOWED_ORIGINS` to the deployed frontend origin. OpenAPI/Swagger and Actuator are not CORS-enabled.

## Endpoints

Role: P = public, A = any authenticated, C = CLIENT, T = TECHNICIAN.

### Auth / users

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| POST `/auth/register` | P | `{ name* (<=100), email* (email, <=254), password* (8-72, upper+lower+digit+symbol), role* (CLIENT\|TECHNICIAN) }` | `{ id, name, email, role }` | 201, 400, 409 |
| POST `/auth/login` | P | `{ email*, password* }` | token pair | 200, 400, 401 |
| POST `/auth/refresh` | P | `{ refreshToken* (<=512) }` | token pair | 200, 400, 401 |
| POST `/auth/logout` | P | `{ refreshToken* }` | empty | 204, 400, 401 |
| GET `/users/me` | A | - | `{ id, name, email, role }` | 200, 401 |

### Appliance types

| GET `/appliance-types` | A | - | `[{ id, name, description }]` sorted by name | 200 |
|---|---|---|---|---|

### Requests (client)

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| POST `/requests` | C | `{ applianceTypeId* (number), originalDescription* (<=5000), brand (<=100), model (<=100), latitude (-90..90), longitude (-180..180) }` (lat/lng both or neither) | Request | 201, 400, 404 (unknown type) |
| GET `/requests` | C | - | `[Request]` own, newest first | 200 |
| GET `/requests/{id}` | C owner | - | Request | 200, 403, 404 |
| PATCH `/requests/{id}/location` | C owner | `{ latitude*, longitude* }` | Request (only while `PUBLICADA`) | 200, 400, 403, 404, 409 |
| PATCH `/requests/{id}/cancel` | C owner | - | Request | 200, 403, 404, 409 |
| PATCH `/requests/{id}/close` | - | blocked (closing happens only via accepting a proposal) | - | 403 always |
| POST `/requests/{id}/evidences` | C owner | `{ mediaUrl* (http/https URL, <=500), mediaType* (<=50, e.g. image/jpeg) }` | Evidence | 201, 400, 403, 404 |
| GET `/requests/{id}/evidences` | C owner, or T with a proposal on the request | - | `[Evidence]` by `evidenceNumber` | 200, 403, 404 |

`Request` = `{ id, clientId, applianceTypeId, applianceTypeName, originalDescription, brand, model, status, latitude, longitude, createdAt }` (nullable fields serialize as `null`).

**Evidence is metadata only**: the API stores an external URL + media type string. There is no file upload, size limit or content-type check server-side; the frontend must host the file elsewhere and send its public URL. `Evidence` = `{ requestId, evidenceNumber, mediaUrl, mediaType }`; the owning client can read evidences, and so can a technician who submitted a proposal on that request (other technicians get 403). Only the owning client can add evidences.

### Proposals / services

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| POST `/requests/{id}/proposals` | T | `{ diagnosticCost* (>=0), availableAt* (future ISO datetime), conditions* (<=1000) }` | Proposal | 201, 400, 403 (incompatible), 404, 409 (closed / duplicate) |
| GET `/requests/{id}/proposals` | C owner | - | `[Proposal]` ordered by cost, then availability | 200, 403, 404 |
| POST `/proposals/{id}/accept` | C owner | - | Service (rejects other proposals, closes request) | 201, 403, 404, 409 |
| GET `/services` | A | - | `[Service]` where caller is client or technician | 200 |
| GET `/services/{id}` | participant | - | Service | 200, 403, 404 |
| PATCH `/services/{id}/status?status=EN_ATENCION\|COMPLETADO\|CANCELADO` | participant | query param (no body) | Service | 200, 400, 403, 404, 409 |
| POST `/services/{id}/review` | C owner | `{ rating* (1-5), comment* (<=2000) }` | empty body | 201, 400, 403, 404, 409 |

Status rules: `PROGRAMADO -> EN_ATENCION -> COMPLETADO` only by the service's technician; `CANCELADO` from `PROGRAMADO` by either participant; reviews only on `COMPLETADO`, once per service.

`Proposal` = `{ id, requestId, technicianId, technicianName, diagnosticCost, availableAt, conditions, createdAt, status }`. `Service` = `{ id, proposalId, status, createdAt }`.

### Technicians

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| GET `/technicians/{uuid}` | A | - | public profile (email, phone, coordinates omitted) | 200, 404 |
| GET `/technicians/{uuid}/reputation` | A | - | `{ technicianId, averageRating, reviewCount }` | 200, 404 |
| PUT `/technicians/me/profile` | T | `{ experienceYears (0-70), bio (<=1000), phone (<=20), applianceTypeIds [number] }` (omitted/null fields keep current value) | full own profile | 200, 400, 404 |
| PUT `/technicians/me/service-areas` | T | `{ latitude*, longitude*, maxRadiusKm* (1-300) }` | full own profile | 200, 400 |
| GET `/technicians/me/matching-requests?page&size` | T | - | `[{ requestId, applianceTypeName, brand, model, description, status, distanceKm }]` nearest first | 200, 400, 404 (no profile), 400 (area not configured) |

Profile = `{ id, fullName, email, experienceYears, bio, phone, latitude, longitude, maxRadiusKm, applianceTypes: [name] }` (null fields omitted).

### Ops

`GET /actuator/health` is public (no CORS).
