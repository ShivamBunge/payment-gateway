---
title: "ASPay: Payment Gateway — README"
date: "2026-03-01"
author: "Software Engineer | Pune"
description: "Compact, clear README for the example payment gateway (TransactionService + NotificationService)."
---

# Payment Gateway (ASPay)

This repository is a compact, multi-module example of a resilient, event-driven payment platform. It demonstrates production patterns commonly used in payment systems: idempotency, transactional outbox, an outbox relay, Kafka-based events, and a consumer-side deduplication strategy.

Purpose of this README:
- Provide a single, non-repetitive explanation of architecture and end-to-end flow.
- Point to key code locations you should read.
- Provide a quick local run/test guide for developers on Windows (PowerShell).

Table of Contents
- Project overview
- Architecture & key patterns
- End-to-end flow (step-by-step)
- Business rules implemented
- Key files (where to look)
- Quick start (PowerShell)
- Testing and next steps

---

Project overview
----------------
Two Spring Boot services in this repo:

- TransactionService — exposes an HTTP payment API, enforces client-side idempotency, stores PaymentEntity and OutboxEntity in the DB (transactional), and exposes an OutboxRelay that publishes events to Kafka.
- NotificationService — Kafka consumer that reacts to payment events and sends notifications (email/SMS). It uses Redis to avoid duplicate notifications.

Infrastructure used in examples
- Relational DB (JPA/Hibernate) for payments & outbox (example: Postgres)
- Kafka for asynchronous event delivery
- Redis for short-lived keys used for idempotency and consumer deduplication

Architecture & key patterns
---------------------------
High-level flow: Client -> TransactionService -> (DB + Outbox) -> OutboxRelay -> Kafka (payment-events) -> NotificationService

Patterns used:
- Transactional Outbox: persist event intent in DB inside the same transaction as the business write to avoid dual-write inconsistencies.
- Outbox Relay: scheduled poller that reads unprocessed outbox rows, publishes to Kafka, and marks them processed after successful send.
- Idempotency (request side): atomic Redis SETNX to mark an idempotency key as PROCESSING, then replace with final JSON response.
- Consumer deduplication: consumer uses Redis setIfAbsent keyed by transactionId to ensure notifications are only sent once.
- Retry + DLT: consumer configured with retry/backoff and DLT handling for poison messages.

End-to-end flow (concise)
------------------------
1) Client POST /api/v1/payments with header `X-Idempotency-Key` and payment body.
   - Controller checks idempotency via `IdempotencyService` (Redis setIfAbsent).
   - If duplicate and status is PROCESSING -> return 409. If duplicate and final JSON exists -> return it.
   - If first attempt -> call `PaymentService.process(...)`.

2) `PaymentService.process(...)` (annotated @Transactional)
   - Create and save `PaymentEntity` (transactionId generated).
   - Create and save `OutboxEntity` (payload = JSON of payment) in same transaction.
   - Return `PaymentResponse` to controller; controller stores serialized response in Redis under the idempotency key.

3) `OutboxRelay` (scheduled) polls unprocessed outbox rows and sends them to Kafka (topic `payment-events`) using `KafkaTemplate`.
   - On success mark outbox.processed = true.
   - On failure, leave record unprocessed to retry later.

4) `NotificationService.PaymentConsumer` consumes `payment-events`:
   - Deserializes into `PaymentEvent`.
   - Ensures first-run-only using Redis setIfAbsent("notif_processed:<transactionId>").
   - Executes business routing logic (email on SUCCESS, SMS on FAILED, log on PENDING).
   - If processing fails after retries it is moved to DLT; `@DltHandler` logs the event.

Business rules implemented (summary)
----------------------------------
- Idempotency: Protects against duplicate client requests using `X-Idempotency-Key` and Redis. Lifecycle: SETNX -> PROCESSING -> replace with final response -> TTL-managed.
- Atomic persistence + event intent: PaymentEntity + OutboxEntity saved in single DB transaction (avoids ghost payments).
- Reliable delivery: OutboxRelay ensures events reach Kafka eventually; marking processed only on successful send.
- Notification deduplication: Consumer-side Redis prevents duplicate notifications on redelivery.

Key files (where to look)
------------------------

- TransactionService
  - `src/main/java/.../controllers/PaymentController.java`
  - `src/main/java/.../services/PaymentService.java`
  - `src/main/java/.../services/IdempotencyService.java`
  - `src/main/java/.../models/PaymentEntity.java`, `OutboxEntity.java`
  - `src/main/java/.../repositories/PaymentRepository.java`, `OutboxRepository.java`
  - `src/main/java/.../scheduler/OutboxRelay.java`
  - `src/main/java/.../config/RedisConfig.java`, `SecurityConfig.java`

- NotificationService
  - `src/main/java/.../consumers/PaymentConsumer.java`
  - `src/main/java/.../dto/PaymentEvent.java`

Quick start (Windows PowerShell)
--------------------------------
Notes: services expect Kafka, Postgres and Redis. Use the repo `docker-compose.yml` to start infra when available.

1) Build both modules (from repo root):

```powershell
./mvnw -pl TransactionService -am -DskipTests=true package ; ./mvnw -pl NotificationService -am -DskipTests=true package
```

2) Start infra (if using docker-compose):

```powershell
docker-compose up -d
```

3) Run services locally (example):

```powershell
cd TransactionService ; ../mvnw spring-boot:run
# New shell
cd NotificationService ; ../mvnw spring-boot:run
```

4) Example request:

```bash
curl -X POST \
  http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: my-unique-key-123" \
  -d '{"amount":100.0,"currency":"USD","sourceAccount":"A","destinationAccount":"B"}'
```

Testing
-------
- Unit tests: `mvn -DskipTests=false test` inside each module.
- Integration tests: run with real infra (docker-compose) or use Testcontainers.

Next steps / suggestions
------------------------
- Add metrics (Prometheus) and tracing (OpenTelemetry) to observe cross-service latency and flows.
- Consider using Kafka transactions or Debezium CDC for higher-scale outbox guarantees.
- Harden idempotency storage if keys need to survive long-term (persist in DB instead of only Redis).

Contributing
------------
Follow the Controller -> Service -> Repository separation. Use feature branches and include tests for behavior changes.

---

