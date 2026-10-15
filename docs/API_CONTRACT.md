# RepairMatch API contract (frontend reference)

Base URL (local): `http://localhost:8080/api`. All bodies are `application/json`. Machine-readable spec (dev/test only; disabled in `prod`): `/v3/api-docs`, UI at `/swagger-ui.html`.

## Conventions

- **Auth**: `Authorization: Bearer <accessToken>` on everything except the public endpoints marked "public".
- **Dates**: ISO-8601 strings, no timezone (`LocalDateTime`, e.g. `2026-10-14T22:02:19.123`). Send `availableAt` in the same format.
- **Enums**: strings only (numbers are rejected). `Role`: `CLIENT | TECHNICIAN`. Request status: `PUBLICADA | CON_PROPUESTAS | CERRADA | CANCELADA | EXPIRADA`. Proposal status: `PENDING | ACCEPTED | REJECTED`. Service status: `PROGRAMADO | EN_ATENCION | COMPLETADO | CANCELADO`. Request/proposal `status` fields are plain strings with those values.
- **Money**: `diagnosticCost` is a decimal number (max 10 integer + 2 fraction digits).
- **IDs**: users/technicians are UUID strings; requests, proposals, services, appliance types are numbers.
- **Lists**: plain JSON arrays, no envelope, no pagination, always scoped to the authenticated user (`GET /technicians/{uuid}/reviews` and `GET /appliance-types` are readable by any authenticated account). Only `GET /technicians/me/matching-requests` takes `page` (default 0) / `size` (default 20, max 100) and still returns a plain array.
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
| GET `/requests/{id}` | C owner, or T with a proposal on the request | - | Request (for a technician `latitude`/`longitude` are `null` unless that technician's proposal was accepted) | 200, 403, 404 |
| GET `/requests/{id}/service` | C owner | - | Service created by accepting a proposal on this request | 200, 403, 404 (unknown request, or no service yet) |
| PATCH `/requests/{id}/location` | C owner | `{ latitude*, longitude* }` | Request (only while `PUBLICADA`) | 200, 400, 403, 404, 409 |
| PATCH `/requests/{id}/cancel` | C owner | - | Request | 200, 403, 404, 409 |
| PATCH `/requests/{id}/close` | - | blocked (closing happens only via accepting a proposal) | - | 403 for any authenticated caller, 401 without a token |
| POST `/requests/{id}/evidences` | C owner | `{ mediaUrl* (http/https URL, <=500), mediaType* (<=50, e.g. image/jpeg) }` | Evidence | 201, 400, 403, 404 |
| GET `/requests/{id}/evidences` | C owner, or T with a proposal on the request | - | `[Evidence]` by `evidenceNumber` | 200, 403, 404 |

`Request` = `{ id, clientId, applianceTypeId, applianceTypeName, originalDescription, brand, model, status, latitude, longitude, createdAt }` (nullable fields serialize as `null`).

#### Request lifecycle

| From | To | Triggered by | Who |
|---|---|---|---|
| (new) | `PUBLICADA` | `POST /requests` | owner (C) |
| `PUBLICADA` | `CON_PROPUESTAS` | first `POST /requests/{id}/proposals` | compatible technician (T) |
| `PUBLICADA`, `CON_PROPUESTAS` | `CERRADA` | `POST /proposals/{id}/accept` (same transaction as the service creation) | owner (C) |
| `PUBLICADA`, `CON_PROPUESTAS` | `CANCELADA` | `PATCH /requests/{id}/cancel` | owner (C) |

- `CERRADA`, `CANCELADA` and `EXPIRADA` are final: cancel, location change, new proposals and acceptance all answer `409`.
- There is no standalone close: `PATCH /requests/{id}/close` is always `403`.
- **`EXPIRADA` is reserved.** Nothing in the current API or a scheduler sets it (no expiry window has been defined). The UI must still render it, because the value exists in the enum.
- Location can only change while `PUBLICADA` (`409` once proposals exist). Cancelling leaves existing proposals as `PENDING`; read the request `status` before offering to act on a proposal.

**Evidence is metadata only**: the API stores an external URL + media type string. There is no file upload, size limit or content-type check server-side; the frontend must host the file elsewhere and send its public URL. `Evidence` = `{ requestId, evidenceNumber, mediaUrl, mediaType }`; the owning client can read evidences, and so can a technician who submitted a proposal on that request (other technicians get 403). Only the owning client can add evidences.

### Proposals / services

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| POST `/requests/{id}/proposals` | T | `{ diagnosticCost* (>=0), availableAt* (future ISO datetime), conditions* (<=1000) }` | Proposal | 201, 400 (validation, or no technician profile yet), 403 (incompatible: `message` says why), 404, 409 (closed / duplicate) |
| GET `/requests/{id}/proposals` | C owner | - | `[Proposal]` ordered by cost, then availability | 200, 403, 404 |
| POST `/proposals/{id}/accept` | C owner | - | Service (rejects other proposals, closes request) | 201, 403, 404, 409 |
| GET `/services` | A | - | `[Service]` where the caller is the client or the technician, newest first (a caller only ever matches one side because an account has one role) | 200 |
| GET `/services/{id}` | participant | - | Service | 200, 403, 404 |
| PATCH `/services/{id}/status?status=EN_ATENCION\|COMPLETADO\|CANCELADO` | participant | query param (no body) | Service | 200, 400, 403, 404, 409 |
| POST `/services/{id}/review` | C owner | `{ rating* (1-5), comment* (<=2000) }` | Review (the created review) | 201, 400, 403, 404, 409 |
| GET `/services/{id}/review` | participant | - | Review | 200, 403, 404 (unknown service, or not reviewed yet) |

Status rules: `PROGRAMADO -> EN_ATENCION -> COMPLETADO` only by the service's technician; `CANCELADO` from `PROGRAMADO` by either participant (a client sending any status other than `CANCELADO` gets `403`, because the technician-only check runs first); any other transition by a participant allowed to attempt it, including leaving a final state, is `409`. Reviews only on `COMPLETADO`, once per service (`409` when early or repeated), written by the client of that service only.

`Proposal` = `{ id, requestId, technicianId, technicianName, diagnosticCost, availableAt, conditions, createdAt, status }`.
`Service` = `{ id, proposalId, status, createdAt, requestId, clientId, technicianId, technicianName }`; the last four are derived through Service -> Proposal -> Technician / Request, so the UI can link a service to its request and technician without extra calls.
`Review` = `{ id, serviceId, rating, comment, createdAt }` (the reviewer is deliberately not exposed). `POST /services/{id}/review` used to answer `201` with an empty body; it now returns the Review (clients that ignored the body are unaffected).

#### Selection and concurrency

Accepting a proposal runs in one transaction: it takes a pessimistic write lock on the request row (`SELECT ... FOR UPDATE`), re-checks that the request is still open and the proposal still `PENDING` with a future `availableAt`, marks the chosen proposal `ACCEPTED` and every other one `REJECTED`, sets the request `CERRADA` and inserts the service. Cancellation and proposal submission take the same lock, so they serialize with it. The database backs this up with `UNIQUE(service_contracts.request_id)`, `UNIQUE(service_contracts.proposal_id)`, `UNIQUE(service_reviews.service_id)` and `UNIQUE(proposals.request_id, proposals.technician_id)`. Whoever loses a race (second accept, accept after cancel) gets `409`; any failure rolls the whole selection back. Automated tests cover this on H2; the PostgreSQL check is `scripts/check-selection-postgres.cjs` (needs a disposable database).

### Technicians

| Method & path | Role | Request | Response | Status |
|---|---|---|---|---|
| GET `/technicians/{uuid}` | A | - | public profile (email, phone, coordinates omitted) | 200, 404 |
| GET `/technicians/{uuid}/reputation` | A | - | `{ technicianId, averageRating, reviewCount }` | 200, 400 (bad uuid), 404 |
| GET `/technicians/{uuid}/reviews` | A | - | `[Review]` of that technician's jobs, newest first (`[]` when none) | 200, 400, 404 |
| GET `/technicians/me/profile` | T | - | full own profile; before the first save only `{ id, fullName, email, applianceTypes: [] }` (nothing is created by the GET) | 200 |
| GET `/technicians/me/proposals` | T | - | `[Proposal]` submitted by the caller, newest first, whatever the state of their request | 200 |
| PUT `/technicians/me/profile` | T | `{ experienceYears (0-70), bio (<=1000), phone (<=20), applianceTypeIds [number] }` (omitted/null fields keep current value) | full own profile | 200, 400, 404 |
| PUT `/technicians/me/service-areas` | T | `{ latitude*, longitude*, maxRadiusKm* (1-300) }` | full own profile | 200, 400 |
| GET `/technicians/me/matching-requests?page&size` | T | - | `[{ requestId, applianceTypeName, brand, model, description, status, distanceKm }]` nearest first | 200, 400, 404 (no profile), 400 (area not configured) |

Profile = `{ id, fullName, email, experienceYears, bio, phone, latitude, longitude, maxRadiusKm, applianceTypes: [name] }` (null fields omitted). `GET /technicians/{uuid}` is the public view of the same profile without email, phone and coordinates.

**Compatibility rule** (one policy shared by the opportunity list and proposal submission): the request is open (`PUBLICADA` or `CON_PROPUESTAS`), has a location, its appliance type is one the technician serves, and the great-circle (Haversine) distance to the technician's centre is `<=` `maxRadiusKm` (a request exactly at the radius is included). Availability is not a filter; it is expressed in the proposal. The list answers an empty array when the technician serves no types. Submitting a proposal on an incompatible request is `403` with `message` ending in the reason: `Technician service area is not configured`, `Request has no location`, `Technician does not cover this appliance type` or `Request is outside the technician's service radius`. `page`/`size` slice the full in-memory result, so every page costs a full scan; fine for the MVP.

**Reputation**: `averageRating` is the unrounded mean of the ratings of reviews on that technician's services (`0.0` and `reviewCount: 0` when there are none). It is derived from Service -> Proposal -> Technician and cannot be edited; any authenticated account can read it, and the reviews, but reviewers are never identified.

### Ops

`GET /actuator/health` is public (no CORS).
