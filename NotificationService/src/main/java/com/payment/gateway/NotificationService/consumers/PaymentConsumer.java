package com.payment.gateway.NotificationService.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.NotificationService.dto.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. Inject Redis Template
    private final StringRedisTemplate redisTemplate;

    public PaymentConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @RetryableTopic(
            attempts = "3", // Try 3 times before giving up
            backoff = @Backoff(delay = 2000, multiplier = 2.0), // Wait 2s, then 4s
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR // If it still fails, move to DLT
    )
    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void consume(String message) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);

            // 2. Define a unique key for this notification event
            String redisKey = "notif_processed:" + event.getTransactionId();

            // 3. ATOMIC "SET IF NOT EXISTS"
            // This returns true only if the key didn't exist before.
            Boolean isNewMessage = redisTemplate.opsForValue().setIfAbsent(
                    redisKey,
                    "PROCESSED",
                    Duration.ofHours(24) // Keeps the key for 24 hours
            );

            if (Boolean.FALSE.equals(isNewMessage)) {
                log.warn("⚠️ Duplicate message detected for TXN: {}. Skipping notification to avoid spamming user.",
                        event.getTransactionId());
                return;
            }

            // 4. Business Routing Logic
            switch (event.getStatus().toUpperCase()) {
                case "SUCCESS":
                    sendSuccessEmail(event);
                    break;
                case "FAILED":
                    sendFailureAlert(event);
                    break;
                case "PENDING":
                    log.info("⏳ Transaction {} is pending. Waiting for final status...", event.getTransactionId());
                    break;
                default:
                    log.warn("❓ Unknown status received: {}", event.getStatus());
            }

        } catch (Exception e) {
            log.error("❌ Error processing notification: {}", e.getMessage());
        }
    }

    /**
     * This method is triggered ONLY when a message has failed all retry attempts.
     */
    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("🚨 [DLQ ALERT] Message moved to DLT from topic {}. Content: {}", topic, message);
        // In a real app, you might save this to a 'failed_messages' table in DB
    }

    private void sendSuccessEmail(PaymentEvent event) {
        log.info("📧 [EMAIL SENT] To: customer@example.com | Subject: Payment Received!");
        log.info("Content: Your payment of {} {} for TXN {} was successful.",
                event.getAmount(), event.getCurrency(), event.getTransactionId());
    }

    private void sendFailureAlert(PaymentEvent event) {
        log.info("📱 [SMS SENT] To: +91-XXXXXXXXXX | Message: Alert! Your payment of {} failed. Please retry.",
                event.getAmount());
    }
}