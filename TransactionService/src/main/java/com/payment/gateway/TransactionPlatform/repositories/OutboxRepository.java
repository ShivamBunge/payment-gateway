package com.payment.gateway.TransactionPlatform.repositories;

import com.payment.gateway.TransactionPlatform.models.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {
    // We will use this later to find messages that haven't been sent yet
    List<OutboxEntity> findByProcessedFalse();
}