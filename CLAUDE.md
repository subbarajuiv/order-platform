



\# Order Platform



Multi-region, event-driven order platform. Portfolio project that demonstrates

resilience and scalability patterns. Quality and documented trade-offs matter

more than feature count.



\## Stack

\- Java 21, Spring Boot (latest stable release that supports Java 21), Maven multi-module

\- Kafka (local: Docker Compose; cloud: MSK), PostgreSQL 16 (cloud: Aurora), Flyway

\- Resilience4j, Micrometer + OpenTelemetry

\- Tests: JUnit 5, Testcontainers, AssertJ, Awaitility

\- Infra (later phases): Terraform, EKS, GitHub Actions



\## Layout

\- libs/: shared code (event-contracts, messaging-support)

\- services/: one module per service

\- docs/adr/: architecture decision records (numbered, e.g. 001-use-kafka.md)

\- docs/PROGRESS.md: current phase, what works, what is next



\## Conventions

\- Base package: com.subbtech.orders.<service>  (change once, then keep consistent)

\- Events are immutable records; every event has eventId, eventType,

&#x20; aggregateId, version, occurredAt, payload

\- Database changes only through Flyway migrations

\- Constructor injection only; no field injection

\- Configuration through application.yml and environment variables, never hard-coded



\## Testing rules

\- For outbox, idempotency, saga and resilience code: write the failing test first

\- Integration tests use Testcontainers, not mocks, for Postgres and Kafka

\- `mvn verify` must pass before any commit



\## Hard rules

\- Never commit secrets, credentials, .env files or Terraform state

\- Never run `terraform apply`, `terraform destroy`, or any command that

&#x20; creates or deletes cloud resources. Show the plan and ask me

\- Never use --force, `git reset --hard`, or rewrite pushed history

\- Do not add dependencies without saying why

\- Show a plan before changing more than 3 files



\## Workflow

Plan, failing test, implement one slice, run tests, summarize the diff.

Record any significant design decision as an ADR in docs/adr/.

At the end of each phase, update docs/PROGRESS.md.

