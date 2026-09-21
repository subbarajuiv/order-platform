# Progress

## Current phase
Phase 1: walking skeleton (complete)

## Done
- Repository layout created
- Parent POM (`order-platform-parent`, Spring Boot 4.1.1, Java 21); only `services/order-service` is a module so far
- `order-service`: Web, Validation, Actuator, JDBC, Flyway, PostgreSQL; Testcontainers (Postgres 16) tests
- `POST /orders` with `Idempotency-Key` (ADR 001)
  - first request 201 + Location; same key and payload 200 with the identical body
  - same key, different payload 422 (problem+json); missing/blank key or invalid body 400
  - one `INSERT ... ON CONFLICT DO NOTHING` on a unique key column; 20-thread same-key test creates exactly one order
- `docker-compose.yml` with Postgres 16 only (no Kafka yet)
- `mvn verify`: 14 tests green (also checked by hand against the compose Postgres with curl)

## Notes
- Boot 4 differs from 3.x: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, Jackson 3, Testcontainers 2.x artifact names.
- The JVM runs in UTC (`-Duser.timezone=UTC` for tests, `TimeZone.setDefault` in `main`): the JDBC driver sends the JVM zone id and `postgres:16` rejects legacy ids such as `Asia/Calcutta`.
- Idempotency keys are globally unique and never expire; scope per caller and add retention before real traffic (see ADR 001).
- Local Maven/Testcontainers runs occasionally stall: a Maven JVM sometimes wedges at startup with one thread at 100% CPU and never builds (kill it and re-run), and two test runs showed multi-minute durations. Cause not found.

## ADRs still to write
- 002: JdbcClient (plain JDBC) instead of JPA for persistence
- 003: Spring Boot 4.1.x (not 3.5.x) and the UTC JVM timezone workaround
- Before Phase 2 code: use Kafka (local Compose, MSK in the cloud) and the transactional outbox

## Next
- Decide Phase 2 scope (candidates: transactional outbox + Kafka in compose, `libs/event-contracts`, `libs/messaging-support`)
- Add the `libs/*` modules to the parent POM when they contain code
