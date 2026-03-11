# 💳 ASPay: Distributed Systems Masterclass

This document records the business workflow, architecture decisions, and technical challenges involved in building a resilient event-driven payment gateway.

---

# 🏦 1. Business Workflow & System Logic

In digital payments, the core challenge is ensuring **financial integrity even during partial failures**. ASPay acts as an orchestrator ensuring every transaction is tracked and recoverable.

## 🚀 Transaction Lifecycle

ASPay follows a strict **state machine** to track every payment:

`INITIATED → PENDING → SUCCESS | FAILED`

### Workflow

1. **Request Initiation**
   - User submits a payment request.
   - System generates a unique **Transaction ID**.

2. **Pending Confirmation**
   - Instead of waiting for slow bank APIs (5–10s), the system records the intent and immediately responds with **PENDING**.

3. **Background Processing**
   - Bank processing happens asynchronously.

4. **Reactive Notifications**
   - A separate notification service reacts to events and sends alerts to users.

5. **Master Ledger Recording**
   - Every successful payment is written to an immutable **Ledger Service** ensuring financial auditability.

---

# 🛡️ 2. Business Problems Solved

## Double-Charge Prevention (Idempotency)

**Problem**

Users may click **Pay** multiple times or network retries may occur.

**Risk**

User could be charged multiple times.

**Solution**

Each request includes an **Idempotency Key**.

If the same request arrives again:

- The system detects the duplicate
- Returns the original transaction result
- Prevents a second charge

---

## Slow Bank APIs

**Problem**

Third-party bank APIs can take several seconds to respond.

**Risk**

Users perceive the application as frozen.

**Solution**

ASPay uses **asynchronous processing**:

- Record request
- Return **Pending**
- Process bank interaction in background

---

## Ghost Payments (Data Consistency)

**Problem**

System crash occurs after database write but before notification is sent.

**Risk**

User is charged but never receives confirmation.

**Solution**

**Transactional Outbox Pattern**

Both are saved in a single transaction:

- Payment record
- Outbox event for Kafka

If the system crashes, the event relay continues processing later.

---

## Poison Messages

**Problem**

Corrupted messages enter the event pipeline.

**Risk**

Consumers continuously fail and block the entire queue.

**Solution**

**Dead Letter Queue**

After multiple failed retries:

- Message moves to `payment-events-dlt`
- Other messages continue processing

---

# 🏗️ 3. System Architecture (High Level Design)

## Event-Driven Microservices

ASPay uses **Kafka-based event-driven architecture** to decouple services.

Core services include:

- **Payment Service** — transaction orchestration
- **Ledger Service** — immutable financial record
- **Notification Service** — user alerts
- **Outbox Relay** — event publishing

Benefits:

- Loose coupling
- Resilient processing
- Independent scaling

---

## Transactional Outbox Pattern

Prevents the **dual-write problem**.

Single transaction writes:

- `PaymentEntity`
- `OutboxEntity`

Outbox relay later publishes events to Kafka.

This guarantees:

- No lost messages
- No ghost payments

---

## Saga Pattern (Choreography)

For multi-service workflows:

`Payment → Ledger → Notification`

If a step fails:

- A **compensating transaction** executes
- Example: trigger **refund**

Ensures eventual consistency across services.

---

## Delivery Guarantees

Kafka provides **at-least-once delivery**.

Consumers implement **idempotency logic** to achieve **exactly-once business processing**.

---

## Service Decoupling

If downstream services fail:

- Events remain in Kafka
- System continues accepting payments
- Processing resumes when services recover

---

# ⚙️ 4. Deep Technical Implementation

## Redis Idempotency Lock

To prevent duplicate charges:

Redis uses atomic operation: SETNX idempotency:<key>


Behavior:

- First request → lock created
- Duplicate request → existing result returned

This avoids race conditions under high concurrency.

---

## Atomic Check-and-Set

`IdempotencyService` performs:

- Check existence
- Set processing state

In a **single Redis command**.

Ensures safe behavior in distributed systems.

---

## JSON Schema Evolution

Consumers tolerate schema changes using: @JsonIgnoreProperties(ignoreUnknown = true)


If producers add new fields:

- Consumers remain functional
- No runtime failures occur.

---

## Precision Currency Handling

Floating point types cause rounding errors.

ASPay uses: BigDecimal

Benefits:

- Accurate financial calculations
- Audit-safe arithmetic

---

## Retry Strategy

Transient failures are handled with **exponential backoff**:

Example retry pattern:
2s → 4s → 8s


This prevents overwhelming failing systems.

---

## Dead Letter Queue Processing

If message retries exceed threshold:

- Message moved to `payment-events-dlt`
- Manual inspection occurs
- Pipeline remains unblocked.

---

## Global Exception Handling

Centralized handler using: @ControllerAdvice


Ensures:

- Clean JSON error responses
- No exposed stack traces

---

# 🐋 5. Infrastructure & DevOps Fixes

## WSL2 Docker Disk Migration

Docker virtual disk moved from **C: drive → E: drive** using:
wsl --export
wsl --import


Solved repeated **disk exhaustion issues**.

---

## Docker Engine Reliability

Ensured Docker engine was fully started before running:
docker-compose up -d


Prevented connection-refused failures.

---

## Docker Log Rotation

Prevented logs from filling disk by configuring:
max-size: "10m"


Inside `docker-compose.yml`.

---

## UTC Time Standardization

All systems forced to **UTC**:

- PostgreSQL
- JVM
- Docker containers

Avoids timezone mismatch issues such as `"Asia/Calcutta"`.

---

## Multi-Module Maven Monorepo

Parent POM manages shared dependencies:

- Java 21
- Spring Boot 3

Child services inherit consistent versions.

---

## Chaos Engineering

Failure scenarios were tested by injecting malformed messages using:
kafka-console-producer


Verified:

- DLQ routing
- Retry mechanisms
- Fault isolation

---

# 💻 Essential Command Reference

## Docker Management

```bash
# Clean unused Docker resources
docker system prune -a --volumes

# Launch distributed cluster
docker-compose up -d

Kafka & Redis Auditing

# List Kafka topics
docker exec -it <kafka_id> kafka-topics --list --bootstrap-server localhost:9092

# Inspect Redis idempotency keys
docker exec -it <redis_id> redis-cli
KEYS *
GET idempotency:payment:<UUID>


