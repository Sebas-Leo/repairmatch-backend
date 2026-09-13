# RepairMatch Backend

Backend project for **DBP, 2026-II**. RepairMatch connects people who need an appliance repaired with suitable technicians: publish a request, receive proposals, select a technician, complete the service, and leave a review.

**Status: initial planning only.** This repository does not yet contain a Spring Boot application, database migrations, automated tests, or a Postman collection. The assignments and implementation choices below are proposals for team agreement, not completed features.

## Scope and first milestone

Build a REST API with **Java and Spring Boot**, persist data in **PostgreSQL**, and validate the complete workflow through **Postman**. The current assignment is **backend-only**, overriding the web/mobile frontend scope mentioned in the original report.

The first milestone is one working, tested journey:

`Client -> Repair request -> Compatible technicians -> Proposals -> Selection -> Service -> Review`

No frontend, payments, escrow, automatic AI diagnosis, "fair price" estimation, GPS tracking, or complex guarantees are included in the initial scope.

## Five module owners

These are suggested assignments, not assessments of anyone's existing skills. **Everyone implements backend code.** Each owner delivers their module's controllers, DTOs and validation, services/business rules, persistence, automated tests, Postman examples, and API documentation. Integration and reviews are shared; nobody is assigned only documentation or testing.

| Owner | Module | Main responsibilities | Acceptance example |
| --- | --- | --- | --- |
| **Ariana Belen Blanco Anicama** | Identity and access | User registration/login, password protection, client/technician access rules, authenticated identity, shared authorization foundation. | Valid credentials authenticate; invalid credentials fail; a user cannot modify another user's protected resources. |
| **Camila Araceli Alfaro Chuquino** | Requests, evidence, and appliance types | Structured publication, original description and normalized fields, type catalog, request states, request-owned evidence, ownership checks. | Publish a valid request; reject invalid data; prevent evidence from existing without its parent request. |
| **Jairo Andre Cunya Villalta** | Technician profiles and matching | Technician specialization of User, experience/profile, supported appliance types, service areas/radius, deterministic eligibility queries. | A technician matches only a supported appliance type inside their radius; reject out-of-radius and unsupported-type matches. |
| **Royer Sebastian Ramos Vargas** | Proposals and transactional selection | Submit/list/compare proposals, visit/diagnosis cost and availability, selection authorization, atomic proposal acceptance + request closure + service creation. | Two competing accept operations cannot create two services; any failed selection rolls back all related changes. |
| **Adrian Luis Pacheco Sulluchuco** | Service lifecycle, reviews, and reputation | Manage an already-created service, permitted state transitions, completed-service reviews, one-review rule, derived technician reputation. | Reject an invalid transition or early/duplicate review; a valid completed-service review contributes to the correct technician's reputation. |

### Recommended learning role for Sebastian

**Own proposals and transactional selection.** This combines API design, relational modeling, JPA relationships, authorization, business validation, transactions, concurrency, and integration testing. It connects the modules rather than being isolated CRUD, making it a strong learning assignment for DBP.

Start with proposal creation and listing, then add selection, rollback behavior, and concurrent acceptance tests. The tradeoff is greater integration complexity: agree on contracts with Camila and Adrian before implementing selection, and ask for peer review rather than handling all integration alone.

**Ownership boundary:** Sebastian's selection use case creates the initial `Service` and closes its `Request` atomically. Adrian owns the service model contract and its subsequent lifecycle. Coordinate that contract together; do not implement a second service-creation path in Adrian's module.

## Proposed technical approach

Use a **modular monolith**: one Spring Boot application and one PostgreSQL database, organized by business module. This keeps a five-person course project manageable and allows selection to use one database transaction; the tradeoff is a shared deployment and the need to enforce module boundaries in code. Microservices are not planned.

| Technology | Role | Status |
| --- | --- | --- |
| Java, Spring Boot, REST | Backend application and HTTP API | Required stack; not scaffolded |
| Spring Data JPA, PostgreSQL | Persistence and relational constraints | From the report; not configured |
| Spring Security | Authentication and authorization | From the report; authentication mechanism pending |
| Postman | Shared requests, environments, and API validation | Required by current assignment; collection pending |
| Maven | Reproducible build and dependency management | Recommended supporting tool |
| JUnit and Mockito | Automated business-rule tests and isolated collaborators | Recommended supporting tools |
| OpenAPI | Shared API contracts and discoverable documentation | Recommended supporting tool |

Select compatible Java/Spring Boot/tool versions when bootstrapping; no version or runnable setup is claimed here. Postman validation complements, rather than replaces, automated tests. Transaction and concurrency behavior must also be verified against PostgreSQL.

## Domain rules to preserve

The source report defines the following model and semantic rules (pp. 3-4):

- **Technician is a specialization of User:** every technician is a user, but not every user is a technician. The concrete JPA mapping remains to be agreed.
- A `Request` belongs to one publishing user and one `ApplianceType`; technicians can support multiple appliance types, and each type can have multiple technicians.
- Do not introduce a persistent `Appliance` entity in the MVP. Brand, model, symptom, and other equipment details belong to the request.
- `Evidence` is a weak, request-owned entity identified by **(`requestId`, `evidenceNumber`)**. Its partial number is local to the request; it cannot exist independently.
- Each `Proposal` belongs to one technician and one request. **At most one proposal per request can be accepted and originate a service.** Enforce the invariant in transaction/concurrency handling and database constraints, not only in controller checks.
- Each `Service` originates from exactly one accepted proposal; a proposal originates at most one service. Derive the responsible technician through `Service -> Proposal -> Technician`, not a redundant direct relationship.
- Derive the serviced appliance type through `Service -> Proposal -> Request -> ApplianceType`; get brand/model and other equipment details from the request.
- Each `Review` has one author and evaluates one service; a service has at most one review. Review creation follows completion. Reviewer eligibility and rating limits require an explicit API rule before implementation.
- Technician reputation is **derived from reviews of services originating from that technician's proposals**. It is not an independently editable profile value; a cache is optional later.
- Preserve the original request description alongside any normalized matching fields. AI, if added later, only structures text; it must not diagnose, and the user must confirm/correct its interpretation before saving.

### Requests and services have different states

These are the report's state labels, not finalized API enum spellings:

| Entity | States in the report | Critical transition |
| --- | --- | --- |
| Request | `PUBLICADA`, `CON_PROPUESTAS`, `CERRADA`, `CANCELADA`, `EXPIRADA` | Accepting a proposal closes the request. |
| Service | `PROGRAMADO`, `EN_ATENCIÓN`, `COMPLETADO`, `CANCELADO` | Selection creates a scheduled service; its lifecycle then proceeds separately. |

**Proposed selection contract:** verify that the authenticated requester may select the proposal and the request is eligible; atomically accept that proposal, close the request, and create one scheduled service. A transaction alone does not prevent competing selections: add an agreed concurrency guard and database constraints. Repeated or competing selections must never produce a second contract. Agree on repeat-request responses and cancellation/expiration rules before coding.

### Matching clarification required

The detailed rule on **p. 4** uses **compatible appliance type AND distance within the technician's radius**, with availability expressed in the request/proposal. The feature summary on **p. 2** and formula on **p. 5** also include availability in matching.

**Provisional planning assumption:** follow the detailed p. 4 rule for the MVP; display availability for proposal comparison without making it an automatic eligibility filter. Confirm this inconsistency with the team/docent before implementation. Matching is deterministic, not AI-based.

## Proposed API surface

These routes are contract discussion starters, **not implemented endpoints**. Agree on payloads, pagination, error format, permissions, and final names together.

| Module | Proposed routes |
| --- | --- |
| Identity | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/me` |
| Requests | `GET /api/appliance-types`, `POST /api/requests`, `GET /api/requests/{id}`, `POST /api/requests/{id}/evidence` |
| Technicians | `GET /api/technicians/{id}`, `PUT /api/technicians/me/profile`, `PUT /api/technicians/me/service-areas`, `GET /api/technicians/me/matching-requests` |
| Proposals | `POST /api/requests/{id}/proposals`, `GET /api/requests/{id}/proposals`, `POST /api/proposals/{id}/accept` |
| Services and reviews | `GET /api/services/{id}`, `PATCH /api/services/{id}/status`, `POST /api/services/{id}/review`, `GET /api/technicians/{id}/reputation` |

An authenticated role alone is insufficient: apply ownership checks to requests, proposals, services, and reviews. Final visibility of user/location/evidence data must be agreed before exposing it.

## Delivery roadmap and dependencies

1. **Agree on contracts:** confirm assignments, settle matching ambiguity, model tables/constraints, state transitions, authorization, and request/response examples. Review the selection boundary first.
2. **Bootstrap together:** create the Spring Boot project, database configuration, repeatable schema setup, test foundation, shared errors, and a safe Postman environment. Never commit secrets or real credentials.
3. **Enable publication and matching:** Ariana supplies identity; Camila supplies request/type contracts; Jairo uses those contracts for technician eligibility. Use known coordinates initially; choose real geocoding later.
4. **Complete hiring:** Sebastian consumes identity, request, and technician contracts; coordinate with Adrian to create a service atomically. Demonstrate competing selections and rollback with database-backed tests.
5. **Close the loop:** Adrian implements subsequent service transitions, reviews, and derived reputation; the team validates the whole journey and unauthorized/invalid flows.
6. **Add supporting integrations only after the core works:** real evidence storage, maps/geocoding, notifications, asynchronous processing, and automatic expiration. Keep provider boundaries explicit; do not present test substitutes as production integrations. Optional text-structuring AI is not required for the core MVP.

## Shared completion checklist

- [ ] Each member has delivered a substantive backend module, tests, Postman examples, documentation, and a peer review.
- [ ] The client-to-review journey works with persisted data through Postman.
- [ ] Validation, authorization, ownership, missing-resource, and invalid-transition cases are tested.
- [ ] Atomic selection, rollback, and competing acceptance preserve one service per request.
- [ ] Database relationships, evidence identity, and derived values match the agreed model.
- [ ] API contracts and a sanitized Postman collection/environment are committed.
- [ ] Setup instructions reflect an actually runnable application, with no secrets in the repository.

## Decisions still open

- Team/docent confirmation of module assignments and the availability matching rule.
- Exact state-transition permissions, cancellation/expiration policies, duplicate acceptance responses, proposal statuses, and review eligibility/rating scale.
- Authentication mechanism, JPA mapping for User/Technician, database concurrency strategy, compatible versions, and repeatable schema tooling.
- Location precision/radius units, evidence restrictions/access/storage, integration providers, and whether deferred asynchronous features are required for the first graded delivery.

## Source and current progress

Based on **"RepairMatch - Propuesta de Proyecto, DBP (Semana 3)"**, supplied as `RepairMatch_Propuesta_DBP_Semana3_corregida.pdf`: members/problem (p. 1), MVP (p. 2), data model and semantics (pp. 3-4), technologies/integrations (pp. 4-5), exclusions (p. 6). The report is not included in this repository. Backend-only delivery and Postman validation come from the team's current request.

Supporting official references: [Spring transactions](https://spring.io/guides/gs/managing-transactions/), [Spring Boot SQL/JPA support](https://docs.spring.io/spring-boot/reference/data/sql.html), and [Postman test examples](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-examples).

- [x] Documented the initial scope, five proposed module owners, learning recommendation, and implementation dependencies.
- [ ] Confirm the open decisions with the team.
- [ ] Bootstrap and implement the backend after approval to continue.
