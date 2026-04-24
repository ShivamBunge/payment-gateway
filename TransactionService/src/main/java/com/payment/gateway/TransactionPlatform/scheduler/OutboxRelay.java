package com.payment.gateway.TransactionPlatform.scheduler;

import com.payment.gateway.TransactionPlatform.models.OutboxEntity;
import com.payment.gateway.TransactionPlatform.repositories.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Poll pending outbox rows and publish them.
     *
     * Note: we intentionally do not keep a DB transaction open while sending to Kafka.
     * This implementation uses the async send callback and marks rows processed only after a successful send.
     * For multi-instance safety and higher throughput consider using SELECT ... FOR UPDATE SKIP LOCKED
     * or an 'in_flight' claim/update pattern.
     */
    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {
        List<OutboxEntity> pendingEvents = outboxRepository.findByProcessedFalse();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("OutboxRelay: Found {} pending events to publish.", pendingEvents.size());

        for (OutboxEntity event : pendingEvents) {
            try {
                // increment attempt immediately (best-effort)
                event.setAttempts(event.getAttempts() + 1);
                outboxRepository.save(event);

                // send and wait for acknowledgement (synchronous here for compatibility with a range of
                // Spring/Kafka client versions). This is simpler and compatible where addCallback() may not be
                // available in the classpath used by the project. Use a reasonable timeout to avoid long blocking.
                try {
                    var future = kafkaTemplate.send("payment-events", event.getAggregateId(), event.getPayload());
                    // wait up to 10 seconds for send to complete
                    future.get(10, java.util.concurrent.TimeUnit.SECONDS);

                    event.setProcessed(true);
                    event.setProcessedAt(LocalDateTime.now());
                    event.setLastError(null);
                    outboxRepository.save(event);
                    log.info("OutboxRelay: Successfully pushed transaction {} to Kafka.", event.getAggregateId());
                } catch (java.util.concurrent.TimeoutException tex) {
                    event.setLastError("timeout: " + tex.getMessage());
                    outboxRepository.save(event);
                    log.error("OutboxRelay: Timeout while pushing transaction {} to Kafka: {}", event.getAggregateId(), tex.getMessage());
                } catch (Exception ex) {
                    event.setLastError(ex.getMessage());
                    outboxRepository.save(event);
                    log.error("OutboxRelay: Failed to push transaction {}. Error: {}", event.getAggregateId(), ex.getMessage());
                }

            } catch (Exception e) {
                // Guard - any unexpected exception
                log.error("OutboxRelay: Unexpected error when processing outbox id {}: {}", event.getId(), e.getMessage());
                try {
                    event.setLastError(e.getMessage());
                    outboxRepository.save(event);
                } catch (Exception ex) {
                    log.error("OutboxRelay: Failed to persist lastError for outbox id {}: {}", event.getId(), ex.getMessage());
                }
            }
        }
    }
}