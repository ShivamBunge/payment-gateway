package com.payment.gateway.TransactionPlatform.repositories;

import com.payment.gateway.TransactionPlatform.models.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    // Allows us to find a payment by the transactionId (UUID) we generate
    Optional<PaymentEntity> findByTransactionId(String transactionId);
}