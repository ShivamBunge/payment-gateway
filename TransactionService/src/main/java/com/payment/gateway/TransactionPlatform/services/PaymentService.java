package com.payment.gateway.TransactionPlatform.services;

import com.payment.gateway.TransactionPlatform.dto.PaymentRequest;
import com.payment.gateway.TransactionPlatform.dto.PaymentResponse;
import com.payment.gateway.TransactionPlatform.models.PaymentEntity;
import com.payment.gateway.TransactionPlatform.models.PaymentStatus;
import com.payment.gateway.TransactionPlatform.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse process(PaymentRequest request, String idempotencyKey) {
        // Create the record in DB
        PaymentEntity payment = new PaymentEntity();
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setStatus(PaymentStatus.SUCCESS); // Mocking success for now
        payment.setIdempotencyKey(idempotencyKey);

        paymentRepository.save(payment);

        return new PaymentResponse(payment.getTransactionId(), "SUCCESS", "Payment processed successfully");
    }
}
