package com.payment.gateway.TransactionPlatform.scheduler;

import com.payment.gateway.TransactionPlatform.models.OutboxEntity;
import com.payment.gateway.TransactionPlatform.repositories.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelay {

    // 1. Manual Logger Definition
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 2. Updated Constructor
    public OutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000) // Runs every 5 seconds
    @Transactional
    public void publishEvents() {
        List<OutboxEntity> pendingEvents = outboxRepository.findByProcessedFalse();

        if (pendingEvents.isEmpty()) {
            return; // Exit quietly if nothing to do
        }

        log.info("OutboxRelay: Found {} pending events to publish.", pendingEvents.size());

        for (OutboxEntity event : pendingEvents) {
            try {
                // 3. Send to Kafka (Topic: payment-events)
                // We use AggregateId as the 'Key' to ensure order in Kafka partitions
                kafkaTemplate.send("payment-events", event.getAggregateId(), event.getPayload());

                // 4. Mark as processed ONLY if Kafka send succeeds
                event.setProcessed(true);
                outboxRepository.save(event);

                log.info("OutboxRelay: Successfully pushed transaction {} to Kafka.", event.getAggregateId());

            } catch (Exception e) {
                log.error("OutboxRelay: Failed to push transaction {}. Error: {}",
                        event.getAggregateId(), e.getMessage());
                // Note: We DON'T set processed=true here, so it retries in 5 seconds.
            }
        }
    }
}