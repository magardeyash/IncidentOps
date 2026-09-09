# IncidentOps — CI/CD Pipeline Incident Lifecycle & Classification Engine

**IncidentOps** is a production-ready Spring Boot 3 backend that ingests GitHub Actions `workflow_run` webhooks, automatically detects build and deployment pipeline failures, creates and tracks incidents through a strict finite state machine, classifies root cause using deterministic heuristics, recommends recovery playbook actions, and generates automated postmortems. It also includes a client-side AI Advisor panel for privacy-first LLM analysis.

---

## Key Features

- **Webhook Ingestion & Idempotency**: `POST /webhooks/github` verifies `X-Hub-Signature-256` HMAC-SHA256 signatures and guarantees deduplication via `X-GitHub-Delivery` headers.
- **Async Deterministic Failure Classifier**: Executed on a dedicated `@Async` thread pool (`classificationTaskExecutor`). Categorizes failures into `BUILD_ERROR`, `TEST_FAILURE`, `DEPENDENCY_ERROR`, `TIMEOUT`, `INFRA_FLAKE`, or `UNKNOWN` based on pattern matching and file-change correlation.
- **Resilient External Integrations**: All calls to the GitHub API are executed via WebClient and protected with **Resilience4j CircuitBreakers & RateLimiters**.
- **Incident State Machine & Optimistic Locking**: Strict state transitions (`OPEN` → `INVESTIGATING` → `RESOLVED` / `IGNORED`) protected by JPA `@Version` optimistic locking to prevent race conditions.
- **Automated Postmortems**: Deterministic postmortem generation from incident audit event logs once resolved or ignored.
- **Client-Side AI Advisor Panel**: Static HTML/JS interface (`/advisor.html`) that calls `/incidents/{id}/context` and queries OpenAI directly from the browser. Your API key never touches the backend.

---

## Tech Stack & Architecture

- **Java 21**, **Spring Boot 3.3.4**
- **Build Tool**: Apache Maven
- **Database**: PostgreSQL with Spring Data JPA & Hibernate
- **Migrations**: Flyway schema migrations (`src/main/resources/db/migration/V1__init_schema.sql`)
- **Security**: Spring Security + JWT Bearer Tokens (`jjwt 0.12.6`)
- **Resilience**: Resilience4j (CircuitBreaker & RateLimiter)
- **Testing**: Testcontainers (PostgreSQL) & WireMock
- **OpenAPI**: springdoc-openapi (`/swagger-ui.html`)

---

## State Machine Rules

```
OPEN → INVESTIGATING → RESOLVED
                     ↘ IGNORED
```

- Any invalid state transition attempt is rejected with **409 Conflict**.
- Optimistic lock conflicts return **409 Conflict**.
- Every transition records an audit event of type `STATE_CHANGED`.

---

## Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/incidentops` | PostgreSQL JDBC Connection URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database Username |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database Password |
| `JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | 256-bit secret key for signing JWTs |
| `GITHUB_WEBHOOK_SECRET` | `default-webhook-secret-key-12345` | GitHub Webhook Secret Key for HMAC signature verification |
| `GITHUB_TOKEN` | `""` | GitHub Personal Access Token for API calls (optional) |

---

## Getting Started

### 1. Prerequisites
- Java 21 JDK installed
- Apache Maven 3.9+
- PostgreSQL database running (or Docker for Testcontainers integration tests)

### 2. Build & Run Tests
```bash
mvn clean test
```

### 3. Run Application
```bash
mvn spring-boot:run
```

Once running:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **AI Advisor Panel**: [http://localhost:8080/advisor.html](http://localhost:8080/advisor.html)

---

## API Documentation Quick Reference

### Auth & Webhooks
- `POST /api/auth/login` — Generate JWT Bearer Token.
- `POST /webhooks/github` — Receive GitHub Actions `workflow_run` events (HMAC verified).

### Incident Lifecycle (JWT Required)
- `GET /incidents` — List incidents (filters: status, classificationType, repository, date range; paginated).
- `GET /incidents/{id}` — Get full incident details and event timeline.
- `PATCH /incidents/{id}/status` — Transition state (`{ "newStatus": "INVESTIGATING" }`).
- `POST /incidents/{id}/notes` — Add manual notes.
- `GET /incidents/{id}/context` — Structured context bundle for AI consumption.

### Postmortems (JWT Required)
- `POST /incidents/{id}/postmortem` — Generate postmortem report (RESOLVED or IGNORED state required).
- `GET /incidents/{id}/postmortem` — Retrieve generated postmortem.
- `PATCH /incidents/{id}/postmortem` — Edit summary, root cause, or follow-up recommendations.
