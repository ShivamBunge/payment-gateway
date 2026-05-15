package com.payment.gateway.TransactionPlatform.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.TransactionPlatform.client.dto.UserServiceResponse;
import com.payment.gateway.TransactionPlatform.dto.PaymentEventPayload;
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
    private final OutboxRepository outboxRepository;
    private final UserValidationService userValidationService;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          UserValidationService userValidationService,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.userValidationService = userValidationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse process(PaymentRequest request, String userId, String idempotencyKey) {
        UserServiceResponse user = userValidationService.validateUserForPayment(userId);

        PaymentEntity payment = new PaymentEntity();
        String transactionId = UUID.randomUUID().toString();
        payment.setTransactionId(transactionId);
        payment.setUserId(userId);
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setIdempotencyKey(idempotencyKey);

        paymentRepository.save(payment);

        try {
            OutboxEntity outbox = new OutboxEntity();
            outbox.setAggregateId(transactionId);
            outbox.setEventType("PAYMENT_SUCCESS");

            PaymentEventPayload eventPayload = new PaymentEventPayload(
                    transactionId,
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getStatus().name(),
                    userId,
                    user.email()
            );
            String payload = objectMapper.writeValueAsString(eventPayload);
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