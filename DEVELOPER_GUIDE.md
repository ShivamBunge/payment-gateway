# Developer Guide — Payment Gateway (Junior Java/Spring Developer)

Task: technical guide that explains the business use case, high-level and low-level design, important classes and methods, and run/debug instructions for the repository.

Checklist (what I'm delivering here):
- [x] Clear description of the business use case and main flows
- [x] High-level architecture and responsibilities mapped to source files
- [x] Low-level design: DB schemas, key classes, annotated code snippets with one-line comments
- [x] Sequence steps for request -> DB -> Outbox -> Kafka -> Consumer
- [x] How to run the services locally (PowerShell commands)
- [x] How to view logs and common debugging fixes
- [x] Ready-to-paste one-line comments for important Java methods
- [x] Next-priority improvements / PR suggestions

---

Contents
- Business use case (what the app solves)
- High-level architecture (components & responsibilities)
- End-to-end sequence (numbered steps)
- Data model (tables you will see and why)
- Key classes & methods (path, purpose and one-line comments)
- How idempotency works (Redis lifecycle and Lua script rationale)
- How outbox works (why, what fields were added)
- How to run locally (PowerShell commands)
- How to read logs (console vs actuator logfile)
- Common failures & debug checklist
- Suggested one-line comments (copy/paste)
- Next PRs / improvements

---

1) Business use case (plain language)

This project implements a small Payment Gateway. A client (mobile/web) sends a payment request to the TransactionService API. 
TransactionService records the payment intent in Postgres and emits an event to Kafka (via the transactional outbox pattern). 
NotificationService consumes that Kafka event and sends notifications (email/SMS) to users or downstream systems. 
Idempotency is enforced so repeating the same client request (e.g. due to retries) does not create duplicate payments.

Why this matters:
- Payments must be durable (won't be lost if a server crashes) -> write to Postgres in the same transaction as the business record.
- Events must be published reliably -> use the Outbox to guarantee the message is published eventually.
- External retries must not duplicate work -> use an idempotency key stored in Redis.


2) High-level architecture

Components and where to find them:
- TransactionService 
  - Controller: `com.payment.gateway.TransactionPlatform.controllers.PaymentController` — HTTP API
  - Service: `...services.PaymentService` — business logic that persists PaymentEntity and writes outbox row
  - Repository: Spring Data JPA repositories for PaymentEntity and OutboxEntity
  - Outbox relay: `...scheduler.OutboxRelay` — scheduled job that publishes pending outbox rows to Kafka
  - Idempotency: `...services.IdempotencyService` — Redis-based lifecycle for idempotency keys
  - Configs: `application.properties`, `logback-spring.xml`, `RedisConfig` etc.
  - Dev helper: `SchemaUpdater` — alters the outbox table to add dev-only columns if missing

- NotificationService 
  - Consumer: `NotificationService.consumers.PaymentConsumer` — consumes `payment-events` topic and deduplicates with Redis
  - DTO: `dto.PaymentEvent` — mapping of event payload

Supporting systems (expected to be available but not part of repo):
- PostgreSQL database (payment_db)
- Redis (for idempotency and consumer dedupe)
- Kafka cluster (for `payment-events` topic)


3) End-to-End flow (numbered, what happens on POST /api/v1/payments)

1. Client sends POST /api/v1/payments with JSON body and header `X-Idempotency-Key: <client-key>`.
2. `PaymentController` receives request and validates it with `@Valid`.
3. Controller asks `IdempotencyService` to reserve the idempotency key (setIfAbsent) — if another request is processing, it will return existing response or indicate 'in-progress'.
4. `PaymentService` starts a DB transaction and:
   a. Persists `PaymentEntity` (business record).
   b. Creates an `OutboxEntity` row in the same transaction with the serialized event payload.
   c. Transaction commits. At this point, the event intent is durably stored.
5. The `OutboxRelay` scheduler periodically queries for `outbox` rows where `processed=false`.
6. For each candidate outbox row, the relay publishes the payload to Kafka (topic `payment-events`) and, upon successful send acknowledgment, marks the outbox row `processed=true` and sets `processedAt` and `attempts`.
7. `NotificationService` consumes messages from `payment-events`. It uses Redis dedupe (setIfAbsent) to avoid double-processing. It sends notifications and stores any audit if needed.
8. `IdempotencyService` (controller) finalizes the response for the idempotency key in Redis once processing completes — this allows subsequent retries to return the saved response.


4) Data model (what tables look like)

- payments (represented by `PaymentEntity`)
  - id (UUID)
  - amount (BigDecimal) — scale & precision: `precision=19, scale=4`
  - currency (String)
  - status, created_at, updated_at
  - other fields (not storing source/dest account currently as requested)

- outbox (represented by `OutboxEntity`)
  - id (UUID)
  - aggregate_id (UUID or payment id)
  - payload (text/json) — serialized event
  - processed (boolean)
  - attempts (int) — added to track retries
  - last_error (text) — last error message
  - processed_at (timestamp)
  - created_at (timestamp)

Notes:
- The outbox row is inserted inside the same DB transaction as the business entity. That guarantees the event will not be published without the data being persisted.
- `SchemaUpdater` exists as a small dev helper to add `attempts`, `last_error`, `processed_at` columns at startup if missing. This is a dev/testing expedient, not a production migration tool.


5) Key classes & methods — what they do and single-line comments you can paste into the code

Below are the most important classes and the one-line comment (ready to paste) that explains each main method. Each snippet includes file path and method signature.

A) `PaymentController` (path: `TransactionService/src/main/java/.../controllers/PaymentController.java`)

public ResponseEntity<?> processPayment(String idempotencyKey, PaymentRequest request)
// One-line: "Handle incoming payment request: validate, check idempotency, delegate to PaymentService and finalize idempotency record."

B) `PaymentService` (path: `.../services/PaymentService.java`)

public PaymentResponse process(PaymentRequest request, String idempotencyKey)
// One-line: "Perform business transaction: persist PaymentEntity and create an OutboxEntity in the same DB transaction."

C) `IdempotencyService` (path: `.../services/IdempotencyService.java`)

public boolean reserveIfAbsent(String idempotencyKey)
// One-line: "Try to reserve an idempotency key in Redis (SETNX) to prevent duplicate processing."

public boolean finalizeResponseIfProcessing(String key, String responseJson)
// One-line: "Atomically set the final response only if the key is currently in PROCESSING state using a Redis Lua script."

public boolean markFailedIfProcessing(String key, String reason)
// One-line: "If a processing attempt failed, atomically mark the idempotency record as FAILED so later callers see failure."

D) `OutboxEntity` (path: `.../models/OutboxEntity.java`)

// Fields: payload, processed, attempts, lastError, processedAt
// One-line: "Represents a durable event 'intent' saved within the application DB for reliable publication." 

E) `OutboxRelay` (path: `.../scheduler/OutboxRelay.java`)

public void pollAndPublish()
// One-line: "Scheduled job: find unprocessed outbox rows, attempt to publish them to Kafka, and mark processed only after an ACK."

F) `SchemaUpdater` (path: `.../config/SchemaUpdater.java`)

@PostConstruct public void ensureOutboxColumns()
// One-line: "Dev helper: run ALTER TABLE statements at startup to add missing development-only columns to outbox."

G) `CorrelationIdFilter` (path: `.../config/CorrelationIdFilter.java`)

protected void doFilterInternal(...) 
// One-line: "Add or propagate a correlation id (X-Request-Id) to MDC and response headers for traceable logs."

H) `GlobalExceptionHandler` (path: `.../controllers/GlobalExceptionHandler.java`)

@ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> handleValidation(...)
// One-line: "Return structured 400 responses when controller request body validation fails."

I) `NotificationService` consumer (path: `NotificationService/src/main/java/.../consumers/PaymentConsumer.java`)

public void consume(String payload)
// One-line: "Deserialize payment event, deduplicate using Redis, and send notifications to users or downstream systems."


6) How idempotency works (conceptually & where to look)

- Purpose: When clients retry the same logically identical request (same X-Idempotency-Key), we should not create duplicate payments.
- Implementation summary:
  1. Controller calls `IdempotencyService.reserveIfAbsent(key)` which uses Redis SETNX to reserve the key and sets a short TTL and a marker value `PROCESSING`.
  2. If the key was already present and value is a previous response JSON, controller returns the stored response immediately.
  3. If the key was present and value is `PROCESSING`, controller returns a 409 or waits/returns a message that processing is in progress.
  4. After processing completes successfully, controller calls `finalizeResponseIfProcessing(key, responseJson)` which uses a Lua script to atomically check current value == PROCESSING, and perform SET with the JSON response and a longer TTL.
  5. If processing fails, `markFailedIfProcessing` writes `FAILED:<reason>` atomically instead of the final response.

Why a Lua script? SETNX + GET + SET is not atomic across multiple requests; the Lua script runs server-side and guarantees the compare-and-set operation.


7) How Outbox works (technical details)

- Reason: Kafka publish is an external action that cannot join the DB transaction. The outbox pattern persists the event in the same DB transaction, then a separate relay publishes it. This guarantees that if a DB transaction commits, the intent to publish exists.
- Important points in the current code:
  - `OutboxEntity` stores payload and metadata.
  - `OutboxRelay` increments `attempts` before attempting to send and updates `lastError` if send fails.
  - The relay uses `kafkaTemplate.send(...).get(timeout)` to wait for send ack; only on success it sets `processed=true`.

Notes on multi-instance safety: Current relay is best-effort; multi-instance deployments should add locking (select for update skip locked, or a claim/lease column) to ensure only one instance handles a row.


8) Running locally (PowerShell commands)

Prerequisites (local machine): Java 17+, Maven, Docker (optional but recommended for Kafka/Postgres/Redis). If you don't have Kafka/Postgres/Redis installed locally, run the quick docker-compose included at repo root (if present) or use the commands below.

Start the supporting stack using Docker (optional):

```powershell

# If docker-compose.yml is configured for local Kafka/Postgres/Redis (inspect it first)
docker-compose up -d
```

Build and run TransactionService (from repo root):

```powershell
# build both modules
.\mvnw -DskipTests clean package
# run just TransactionService from the module folder
cd TransactionService
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Build and run NotificationService:

```powershell
cd E:\payment-gateway\NotificationService
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Notes:
- The `application.properties` in TransactionService sets `spring.profiles.active=local` by default so you don't need to pass profiles when running from IDE, but it's good practice to explicitly set it.
- If you want to run tests: from repo root run `.\mvnw test` (or run per-module). Tests may need Redis/Postgres running depending on how they are written.

9) Common failures & fixes (practical quick checklist)

- SQL Error: column "attempts" does not exist
  - Cause: `OutboxEntity` was updated but DB table wasn't migrated.
  - Quick fix (dev): schema updater `SchemaUpdater` tries to add missing columns at startup. Alternatively, set `spring.jpa.hibernate.ddl-auto=update` for test/dev or run an `ALTER TABLE` manually.
  - Production: add a proper migration (Flyway or Liquibase).

- Logback error: ClassNotFoundException for `net.logstash.logback.encoder.LogstashEncoder`
  - Cause: `logback-spring.xml` references the encoder but the dependency isn't present or profile not active.
  - Fix: Either add dependency to `pom.xml` or guard the JSON appender with a profile so local runs don't parse it.

- `/actuator/logfile` returns 404
  - Cause: Actuator cannot find the path configured in `management.endpoint.logfile.external-file` or it wasn't enabled in `management.endpoints.web.exposure.include`.
  - Fix: Ensure `management.endpoint.logfile.external-file` is set to the absolute path of the existing log file and the endpoint is enabled.

- Kafka publish appears not to happen but outbox not marked processed
  - Cause: `OutboxRelay` waits for ACK and only marks processed on success; check broker connectivity, topic existence, and inspect `attempts`/`lastError` in DB.
  - Fix: Check `spring.kafka.bootstrap-servers`, ensure topic exists and broker reachable, inspect logs for send error stack traces.


10) Suggested one-line comments (ready to paste)

Below are short comments you can paste inside the important methods (use `//` in Java). They are intentionally short so they don't clutter code but help junior devs.

PaymentController.processPayment(...)
// Handle incoming payment request: validate input, enforce idempotency, call service, and finalize idempotency record.

PaymentService.process(...)
// Persist payment and outbox row atomically in one DB transaction.

OutboxRelay.pollAndPublish(...)
// Poll unprocessed outbox rows, publish to Kafka and mark processed on ACK.

IdempotencyService.reserveIfAbsent(...)
// Reserve idempotency key in Redis with a short TTL (prevents duplicate processing).

IdempotencyService.finalizeResponseIfProcessing(...)
// Atomically write final response into Redis only if key is in PROCESSING state (Lua CAS).

NotificationConsumer.consume(...)
// Deserialize event, deduplicate using Redis, perform notification send logic.

SchemaUpdater.ensureOutboxColumns(...)
// Dev helper: add missing outbox columns (attempts,last_error,processed_at) if they do not exist.

CorrelationIdFilter.doFilterInternal(...)
// Ensure each request has a correlation id and place it into MDC for traceable logs.

GlobalExceptionHandler.handleValidation(...)
// Convert validation errors into structured 400 responses instead of stack traces.


12) Next PRs / improvements (prioritized)

1. Add schema migrations (Flyway or Liquibase) and remove `SchemaUpdater` dev hack.
2. Harden OutboxRelay for multi-instance deployments (claim/lease column, SELECT ... FOR UPDATE SKIP LOCKED or a separate claim table).
3. Add distributed tracing (OpenTelemetry) and propagate trace/context headers through Kafka messages.
4. Add Testcontainers-based integration tests for end-to-end flow (Postgres + Kafka + Redis) and CI pipeline.
5. Add schema registry for Kafka events (Avro or JSON Schema) and evolve event versions safely.
6. Add metrics (Micrometer counters/histograms) for outbox publish attempts, idempotency hits/misses, and consumer success/failure.


13) Appendix: quick file references (where to look first)

- TransactionService/src/main/java/com/payment/gateway/TransactionPlatform/controllers/PaymentController.java
- TransactionService/src/main/java/com/payment/gateway/TransactionPlatform/services/PaymentService.java
- TransactionService/src/main/java/com/payment/gateway/TransactionPlatform/services/IdempotencyService.java
- TransactionService/src/main/java/com/payment/gateway/TransactionPlatform/models/OutboxEntity.java
- TransactionService/src/main/java/com/payment/gateway/TransactionPlatform/scheduler/OutboxRelay.java
- TransactionService/src/main/resources/application.properties
- TransactionService/src/main/resources/logback-spring.xml
- NotificationService/src/main/java/com/payment/gateway/NotificationService/consumers/PaymentConsumer.java

---

If you'd like, I can now:
- A) Insert the one-line comments directly into the actual Java files (I can apply safe small edits to the key methods), OR
- B) Produce a PlantUML diagram file for the sequence flows, OR
- C) Create a short checklist PR with the top 3 improvements (migrations, outbox hardening, tracing) and small starter patches.

Which of A/B/C do you want next? If A, I will apply minimal changes (one-line comments) to the top files listed to help future readers.


