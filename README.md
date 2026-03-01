---
title: "ASPay: Resilient Distributed Systems Learning Log"
date: "2026-03-01"
author: "Software Engineer | Pune"
description: "SDE-2 level architectural deep-dive into high-performance payment gateways."
---

# 💳 ASPay: Distributed Systems Masterclass

This document serves as a comprehensive record of the business logic, high-level design (HLD), and technical hurdles overcome during the construction of a resilient payment gateway[cite: 38].

---

## 🚀 1. Business Logic & System Orchestration

1. **State Machine Management**: Transactions transition through strict lifecycle states: `INITIATED` $\rightarrow$ `PENDING` $\rightarrow$ `SUCCESS` or `FAILED`[cite: 8, 43].
2. **Asynchronous Handshake**: Instead of making the user wait for slow external bank APIs (5–10s), the system saves the "intent" and responds immediately with a "Pending" status[cite: 5, 40, 1069, 1072].
3. **Reactive Notifications**: A dedicated service "reacts" to events to dispatch user alerts, ensuring the core payment flow is never blocked by downstream notification delays[cite: 28, 63, 1611].
4. **The Ledger Principle**: A separate Ledger Service acts as the "Truth," creating an immutable, ACID-compliant record of every successful credit/debit[cite: 11, 46, 1101].
5. **UTC Standardization**: In financial systems, all databases (PostgreSQL) and JVMs are forced to **UTC** to avoid regional timezone naming conflicts like "Asia/Calcutta"[cite: 722, 748, 829].

---

## 🏗️ 2. High-Level Design (HLD) & Patterns

6. **Event-Driven Microservices**: The architecture utilizes Kafka for asynchronous communication to decouple services and ensure high availability[cite: 5, 40, 1471].
7. **Transactional Outbox Pattern**: Within a single ACID transaction, we save the `PaymentEntity` and an `OutboxEntity` to prevent "Ghost Payments" where a DB update succeeds but a message fails to send[cite: 22, 57, 1080, 1107].

8. **Idempotency (Double-Charge Prevention)**: We use Redis `setIfAbsent` (SETNX) to create an atomic lock for each `X-Idempotency-Key`[cite: 17, 52, 184, 927].
9. **Saga Pattern (Choreography)**: For distributed transactions spanning multiple services, we use Sagas to ensure "all-or-nothing" behavior through compensating transactions (refunds)[cite: 27, 32, 63, 65].

10. **Exactly-Once Processing**: While Kafka provides "At-Least-Once" delivery, the consumer uses a Redis-based shield to ensure a notification is only sent once per transaction[cite: 26, 61, 1653, 1655].

---

## 🛡️ 3. Technical Resilience & Fault Tolerance

11. **Dead Letter Queue (DLQ)**: "Poison Pill" messages (unparseable data) are sidelined to a `payment-events-dlt` topic after 3 failed retries with exponential backoff[cite: 1720, 1723, 1726].
12. **Circuit Breaker Pattern**: Implemented to prevent "cascading failures" when communicating with 3rd-party bank APIs[cite: 10, 45, 71].
13. **JSON Schema Decoupling**: We use `@JsonIgnoreProperties(ignoreUnknown = true)` to ensure the consumer doesn't crash if the producer adds new, unrecognized fields to the JSON payload[cite: 1580, 1586, 1595].
14. **Precision Currency**: Transitioning from `Double` to `BigDecimal` for currency fields avoids floating-point rounding errors critical in financial audit trails[cite: 1599].
15. **Global Exception Handling**: A centralized `@ControllerAdvice` ensures the user receives professional JSON error responses instead of scary stack traces[cite: 268, 284, 287].

---

## 🐋 4. Infrastructure & "DevOps" Fixes

16. **WSL 2 VHD Migration**: To resolve C: Drive space exhaustion, we migrated the Docker Virtual Hard Drive to the **E: Drive** using `wsl --export` and `wsl --import`[cite: 1237, 1251, 1257].
17. **Docker Engine Reliability**: Resolved "Connection Refused" errors by ensuring Docker Desktop engine status was solid green before executing `docker-compose up -d`[cite: 1205, 1209].
18. **Multi-Module Mono-repo**: Managed a Parent POM to synchronize dependencies (Java 21, Spring Boot 3) across distinct child microservices[cite: 804, 807, 1471].
19. **Log Rotation Policy**: Implemented log caps (`max-size: 10m`) in `docker-compose.yml` to prevent Kafka and Postgres logs from consuming infinite disk space[cite: 1261, 1266, 1321].
20. **Chaos Engineering**: Proved system resilience by injecting malformed data via the `kafka-console-producer` to verify the automated transition to the Dead Letter Topic[cite: 68, 1730, 1750, 1761].

---

## 💻 Essential Command Reference

### Docker Management
```bash
# Clean up unused Docker resources to free C: drive space
docker system prune -a --volumes [cite: 1242]

# Launch the entire event-driven cluster
docker-compose up -d [cite: 90, 257]

```
## Kafka & Redis Auditing

```bash
# List all topics to verify DLT (Dead Letter Topic) creation
docker exec -it <kafka_id> kafka-topics --list --bootstrap-server localhost:9092 [cite: 1732, 1765]

# Inspect Idempotency keys in the Redis cache
docker exec -it <redis_id> redis-cli [cite: 1691]
> KEYS *
> GET idempotency:payment:<UUID> [cite: 1694, 1695]

```



---
title: "ASPay: SDE-2 Distributed Systems & Infrastructure Learning Log"
date: "2026-03-01"
author: "Software Engineer | Pune"
description: "A deep dive into building resilient, event-driven payment gateways."
---

# 💳 ASPay: Engineering Resilience

This log documents the transition from simple code to a production-grade distributed system, focusing on the "Holy Trinity" of SDE-2 engineering: partial failures, data consistency, and idempotency. [cite: 37]

---

## 🏗️ 1. Distributed Systems & Business Logic
These points cover how the system handles state, consistency, and asynchronous flow. [cite: 1084]

* **Event-Driven Orchestration**: The architecture utilizes an asynchronous flow to ensure the payment process is non-blocking, preventing system hangs even if external bank APIs are slow. [cite: 5, 40]
* **State Machine Management**: The Payment Service acts as the orchestrator, strictly managing transaction states from `PENDING` to `SUCCESS` or `FAILED`. [cite: 8, 43]
* **Atomic "Dual-Write" Prevention**: We avoid the risk of saving to a database while failing to notify Kafka by implementing the **Transactional Outbox Pattern**. [cite: 22, 57, 1076]
* **ACID-Compliant Ledgering**: A dedicated Ledger Service provides an immutable "Source of Truth" for successful transactions, ensuring strict financial boundaries. [cite: 11, 46, 1101]
* **Compensating Transactions (Sagas)**: In multi-service flows (Payment $\rightarrow$ Ledger $\rightarrow$ Notification), if a downstream step fails, a **Compensating Transaction** must trigger a refund to maintain consistency. [cite: 32, 63, 65]

* **At-Least-Once Delivery**: By using an outbox relay that only marks entries as processed after a successful Kafka ACK, we guarantee that no payment event is lost. [cite: 26, 61, 1167]
* **Exactly-Once Processing**: While Kafka provides the message, the consumer uses **Idempotency logic** to ensure that business actions (like sending an email) only happen once. [cite: 1565, 1654, 1667]
* **Service Decoupling**: If the Notification Service is down, the Transaction Service remains functional; messages queue safely in Kafka until the consumer recovers. [cite: 1476, 1483, 1513]

---

## 🛠️ 2. Advanced Technical Patterns & Idempotency
Implementation details for high-concurrency and high-availability environments. [cite: 154, 925]

* **Distributed Locking with Redis**: We use Redis `setIfAbsent` (SETNX) to create an atomic lock for each `X-Idempotency-Key`, preventing "Double-Click" charges. [cite: 13, 20, 55, 927]
* **JSON Schema Evolution Resilience**: By applying `@JsonIgnoreProperties(ignoreUnknown = true)`, we ensure the Notification Service doesn't crash when the Transaction Service adds new fields to the payload. [cite: 1586, 1595, 1597]
* **Precision in Currency**: We transitioned to **BigDecimal** for all amount fields to avoid IEEE 754 floating-point rounding errors common in financial calculations. [cite: 1534, 1589, 1599]
* **Exponential Backoff Strategy**: For transient failures, the system uses a retry policy (e.g., waiting 2s, then 4s) giving the infrastructure time to heal. [cite: 1728, 1731]
* **Dead Letter Queue (DLQ) Sidelining**: "Poison Pill" messages (unparseable data) are automatically moved to a `payment-events-dlt` topic to prevent blocking the pipeline. [cite: 1720, 1723, 1732]

* **Atomic Check-and-Set**: The `IdempotencyService` uses a single Redis command to both check for existence and set a `PROCESSING` state, eliminating race conditions. [cite: 183, 184, 927]

---

## 🐋 3. Infrastructure, Docker & Major Fixes
Critical "DevOps" hurdles overcome to stabilize the development environment on the **E: Drive**. [cite: 1237, 1321]

* **WSL 2 VHD Migration**: To resolve C: Drive "Disk Full" crashes, we migrated the Docker Virtual Hard Drive (WSL2) to the E: Drive using `wsl --export` and `wsl --import`. [cite: 1237, 1246, 1251, 1257]
* **The "Asia/Calcutta" Timezone Fatal Error**: We resolved JDBC connection failures by forcing **UTC** at both the container (`TZ=UTC`, `PGTZ=UTC`) and database levels. [cite: 706, 748, 761, 773]
* **Docker Log Caps**: To prevent logs from consuming infinite disk space, we implemented `max-size: "10m"` log rotations in the `docker-compose.yml`. [cite: 1261, 1266, 1321]
* **Multi-Module Maven Parentage**: We established a **Mono-repo architecture** where a Parent POM manages shared versions (Java 21, Spring Boot 3) across services. [cite: 804, 805, 808, 1471]
* **Component-Based Security**: We fixed the "Package not found" error in Spring Boot 3 by moving from the deprecated `WebSecurityConfigurerAdapter` to a modern `SecurityFilterChain` bean. [cite: 450, 452, 461, 467]
* **Chaos Engineering Tests**: We utilized the `kafka-console-producer` to bypass the API and inject malformed strings to verify the system's fault-tolerant DLQ logic. [cite: 68, 69, 1746, 1750]

---

### 🚀 Final Verification
A successful project means running `mvn clean test` and seeing the GREEN "BUILD SUCCESS," confirming that the Java, Postgres, and Redis "handshake" is fully operational. [cite: 795, 796, 801, 833]


---
title: "ASPay: Business Logic & Real-World Solutions"
date: "2026-03-01"
description: "A non-technical overview of how ASPay solves the most common financial transaction failures."
---

# 🏦 ASPay: Ensuring Financial Integrity

In the world of digital payments, a "code that works" isn't enough. [cite_start]For an SDE-2 role, the focus shifts to how a system survives partial failures and maintains a perfect record of the user's money[cite: 1, 2]. [cite_start]ASPay is built to be a resilient "Orchestrator" of financial truth[cite: 8].

---

## 🚀 The Business Workflow

[cite_start]ASPay follows a strict "State Machine" to ensure every transaction is tracked from start to finish[cite: 8, 43].

1.  [cite_start]**Request Initiation**: The system accepts the user's intent to pay and generates a unique Transaction ID[cite: 8, 209].
2.  [cite_start]**The "Pending" Promise**: Instead of making a user wait on a spinning wheel while the bank processes the request, ASPay records the intent and immediately confirms: *"We've received your request; it's being processed"*[cite: 5, 1072].
3.  [cite_start]**Smart Notifications**: The system acts as a "Reactor," automatically sending receipts on success or urgent alerts if a payment fails[cite: 1558, 1559, 1611].
4.  [cite_start]**The Master Ledger**: Every successful move of money is recorded in an immutable "Truth" log, ensuring the bank's books always match the user's experience[cite: 11, 46].



---

## 🛡️ Problems Solved (Business Impact)

### 1. The "Double-Click" Problem (Idempotency)
**The Scenario**: A user clicks "Pay" twice due to a slow UI, or a network retry happens automatically.
[cite_start]**The Risk**: The user is charged twice for a single order[cite: 17, 18, 53].
**The Solution**: ASPay uses a unique "Idempotency Key" to recognize duplicate requests instantly. [cite_start]If you try to pay for the same thing twice, the system recognizes the second attempt and simply returns the status of the first one without re-charging[cite: 19, 20, 54, 55].

### 2. The "Slow Bank" Bottleneck
[cite_start]**The Scenario**: A 3rd-party bank API takes 10 seconds to respond[cite: 5, 1069].
[cite_start]**The Risk**: The entire app hangs, and the user thinks the app is broken[cite: 5, 1070].
**The Solution**: We use **Asynchronous Processing**. [cite_start]The payment is recorded as "Pending," and the heavy work of talking to the bank happens in the background, freeing the user to continue using the app[cite: 40, 1072, 1074].

### 3. The "Ghost Payment" Mystery (Data Consistency)
[cite_start]**The Scenario**: A system crash happens exactly after a payment is saved in the database but before a notification is sent[cite: 1077, 1078].
**The Risk**: The user is charged, but they never receive a confirmation or their order status never updates.
**The Solution**: We use the **Transactional Outbox Pattern**. [cite_start]The payment and the "intent to notify" are saved together in one "all-or-nothing" step[cite: 22, 24, 1107]. [cite_start]If the system crashes, it simply picks up where it left off upon restarting[cite: 1166, 1483].



### 4. The "Poison Pill" Safeguard (Fault Tolerance)
[cite_start]**The Scenario**: A corrupted or "bad" piece of data enters the system[cite: 1553, 1721].
[cite_start]**The Risk**: The entire notification system gets "stuck" trying to process a bad message over and over, blocking all other customers[cite: 1553, 1721].
**The Solution**: We implement a **Dead Letter Queue**. [cite_start]If a message cannot be processed after 3 attempts, it is moved to a "Hospital" area for manual checking, allowing the rest of the payments to flow smoothly[cite: 1554, 1555, 1723].

---

## 🧘 Engineering Philosophy
> [cite_start]"Code works when it survives. In digital finance, reliability is the greatest form of customer happiness." [cite: 1716, 1719]

[cite_start]By decoupling the **Payment** from the **Notification**, ASPay ensures that even if one part of the system is "sleeping," the money never stops moving safely[cite: 1476, 1513].