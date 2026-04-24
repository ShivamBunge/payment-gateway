title: "ASPay: Payment Gateway — README"
date: "2026-03-01"
author: "Software Engineer | Pune"
description: "Compact, clear README for the example payment gateway (TransactionService + NotificationService)."
---

# ASPay: Payment Gateway — README (updated)

This repo implements a compact, example payment platform composed of two Spring Boot services:

- TransactionService — HTTP payment API that enforces client-side idempotency and persists both business data (payments) and event intent (outbox) in a single DB transaction.
- NotificationService — a Kafka consumer that sends customer notifications (email/SMS) and uses Redis to deduplicate events.

This README focuses on the business logic first, problems solved, architecture, developer setup, code notes, and planned next PRs (including adding an API Gateway and a Users service).

Table of Contents
- Business logic & problems solved
- High-level architecture
- Key code areas and implementation notes
- Local dev & quick start (PowerShell)
- Next PRs (short-term roadmap)
- Plan to introduce Gateway + Users service

Business logic & problems solved
--------------------------------
Primary business flow
- Client submits a payment via POST /api/v1/payments with a unique `X-Idempotency-Key`.
- TransactionService enforces idempotency (Redis) to avoid duplicate payments from retries.
- A PaymentEntity (business write) and OutboxEntity (event intent) are saved atomically in the relational DB.
- A background OutboxRelay publishes outbox rows to Kafka (topic `payment-events`).
- NotificationService consumes `payment-events` and sends notifications while using Redis to prevent duplicate notifications on consumer redelivery.

Problems this repository addresses
- Dual-write / lost-event problem: transactional outbox ensures event intent is stored with the business write in the same DB transaction.
- Client retries and duplicate requests: idempotency via Redis prevents duplicate payment creation.
- Consumer redelivery: consumer-side deduplication in NotificationService avoids duplicate notifications.

High-level architecture
------------------------
Client -> TransactionService (HTTP + idempotency) -> Relational DB (Payment + Outbox) -> OutboxRelay -> Kafka (payment-events) -> NotificationService -> External notification providers

Infrastructure used in examples
- Relational DB (JPA/Hibernate) — example Postgres
- Kafka for asynchronous event delivery
- Redis for short-lived keys used for idempotency and consumer deduplication

Key code areas and notes
------------------------
- `TransactionService`
  - Controller: `src/main/java/.../controllers/PaymentController.java`
    - Accepts `X-Idempotency-Key` and `PaymentRequest`.
    - Uses `IdempotencyService` to guard duplicate processing.
  - Service: `src/main/java/.../services/PaymentService.java`
    - Persists `PaymentEntity` and `OutboxEntity` in the same transaction.
  - Outbox: `src/main/java/.../models/OutboxEntity.java`, `scheduler/OutboxRelay.java`, `repositories/OutboxRepository.java`.
    - Background relay publishes events to Kafka.
  - Infrastructure: `config/RedisConfig.java`, `config/SecurityConfig.java`.

- `NotificationService`
  - Consumer: `src/main/java/.../consumers/PaymentConsumer.java` — consumes `payment-events`, deduplicates with Redis, and performs routing (email/SMS).
  - DTO: `src/main/java/.../dto/PaymentEvent.java`.

Implementation notes and improvement areas (summary)
- Outbox publishing must mark rows processed only after Kafka ACK to avoid message loss — currently the repo used a naive approach; upcoming PRs fix that.
- Use BigDecimal for monetary amounts (avoid Double).
- Harden idempotency lifecycle (avoid deleting keys on transient failures; record FAILED state and reasons).
- Add tracing (correlation id propagation), metrics, and structured logging.
- Add schema governance for events (schema registry) to support evolution.

Local developer quick start (PowerShell)
--------------------------------------
Prereqs: Java 17+, Docker (for infra), Maven wrapper is included.

1) Build services (skip tests to speed up during iteration):

```powershell
./mvnw -pl TransactionService -am -DskipTests=true package ; ./mvnw -pl NotificationService -am -DskipTests=true package
```

2) Start infrastructure (Postgres, Kafka, Zookeeper, Redis) using docker-compose if present:

```powershell
docker-compose up -d
```

3) Run services locally:

```powershell
cd TransactionService ; ../mvnw spring-boot:run
# new shell
cd NotificationService ; ../mvnw spring-boot:run
```

4) Example request:

```powershell
curl -X POST http://localhost:8080/api/v1/payments -H "Content-Type: application/json" -H "X-Idempotency-Key: my-unique-key-123" -d '{"amount":100.00,"currency":"USD","sourceAccount":"A","destinationAccount":"B"}'
```

Next PRs (short-term roadmap)
-----------------------------
The repository will be improved in small, reviewable PRs. Prioritized list:

PR 1 — Outbox reliability and metadata (current)
- Ensure events are only marked processed after Kafka confirms send (avoid lost events).
- Add metadata on outbox rows (attempts, lastError, processedAt) to support visibility and retries.

PR 2 — Money correctness
- Replace `Double` with `BigDecimal` for amount fields across DTOs, entities, and events.

PR 3 — Idempotency lifecycle hardening
- Avoid deleting idempotency keys on errors; use explicit FAILED state and consider persisting idempotency for durability.

PR 4 — Observability and error handling
- Add correlation id propagation, a global exception handler, metrics (Micrometer/Prometheus) and distributed tracing (OpenTelemetry).

PR 5 — Testing & schema governance
- Add Testcontainers-based integration tests (DB, Kafka, Redis), and introduce schema registry for event contracts.

Plan to introduce an API Gateway and a Users service
---------------------------------------------------
Design goals for Gateway and Users service:

- Gateway (edge): centralize authentication (JWT validation via a JWKS), rate-limiting, CORS, TLS termination, request transformation and correlation id injection. Consider Spring Cloud Gateway for a Spring-native approach or a dedicated API gateway (Kong/Envoy) in production.
- Users service: own canonical user data (userId, email, payment instruments, KYC state). Expose REST APIs and emit `user.created` / `user.updated` events to Kafka.

Integration approach (recommended incremental rollout):
- Phase A (quick): add Gateway to validate JWTs and forward `X-User-Id` and `X-Request-Id`. TransactionService will call UsersService synchronously when it needs definitive user data (behind a circuit breaker).
- Phase B (scalable): UsersService emits domain events. TransactionService subscribes to user events and maintains a local read-model for low-latency validation (eventual consistency). This reduces runtime coupling.

Start of work: PR 1 (Outbox reliability)
---------------------------------------
This repository's next change will be PR 1: make outbox publishing reliable (mark processed only after Kafka ACK) and add outbox metadata for attempts and lastError. This improves delivery guarantees and supports safer retries.

The PR will change:
- `TransactionService/src/main/java/.../models/OutboxEntity.java` (add attempts,lastError,processedAt)
- `TransactionService/src/main/java/.../scheduler/OutboxRelay.java` (use Kafka send callbacks; only mark processed on success)

After PR 1 we will run the build and run a smoke test against local infra. Subsequent PRs will follow the prioritized roadmap above.

If you'd like I can open the PRs and apply changes now — confirm and I'll start with PR 1 edits and run a local compile.

---

