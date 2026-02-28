package com.payment.gateway.NotificationService.consumers;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    /**
     * Listen to 'payment-events'.
     * Group ID ensures that even if we scale this service to 3 instances,
     * only ONE instance processes each specific message.
     */
    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void consume(String message) {
        log.info("🔔 [NOTIFICATION-SERVICE] Received new event from Kafka: {}", message);

        // Logical Step: Send Email/SMS
        log.info("Sending confirmation to customer for this transaction...");
    }
}