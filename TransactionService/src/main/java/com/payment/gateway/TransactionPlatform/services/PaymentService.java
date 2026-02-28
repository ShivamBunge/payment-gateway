package com.payment.gateway.TransactionPlatform.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.TransactionPlatform.dto.PaymentRequest;
import com.payment.gateway.TransactionPlatform.dto.PaymentResponse;
import com.payment.gateway.TransactionPlatform.models.OutboxEntity;
import com.payment.gateway.TransactionPlatform.models.PaymentEntity;
import com.payment.gateway.TransactionPlatform.models.PaymentStatus;
import com.payment.gateway.TransactionPlatform.repositories.OutboxRepository;
import com.payment.gateway.TransactionPlatform.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository; // New: To save the event
    private final ObjectMapper objectMapper;         // New: To convert data to JSON

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse process(PaymentRequest request, String idempotencyKey) {
        // 1. Save the Payment Entity
        PaymentEntity payment = new PaymentEntity();
        String transactionId = UUID.randomUUID().toString();
        payment.setTransactionId(transactionId);
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setIdempotencyKey(idempotencyKey);

        paymentRepository.save(payment);

        // 2. Save the Outbox Entity (This guarantees Kafka will eventually get the message)
        try {
            OutboxEntity outbox = new OutboxEntity();
            outbox.setAggregateId(transactionId);
            outbox.setEventType("PAYMENT_SUCCESS");

            // Convert the payment object to a JSON string for the message payload
            String payload = objectMapper.writeValueAsString(payment);
            outbox.setPayload(payload);
            outbox.setProcessed(false); // Background worker will pick this up later

            outboxRepository.save(outbox);

        } catch (JsonProcessingException e) {
            // If serialization fails, we roll back the whole transaction!
            throw new RuntimeException("Could not create outbox message", e);
        }

        return new PaymentResponse(transactionId, "SUCCESS", "Payment processed successfully");
    }
}