package com.payment.gateway.TransactionPlatform.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox")
@Data
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId; // The Transaction ID (Business Key)

    private String eventType;   // e.g., "PAYMENT_CREATED"

    @Column(columnDefinition = "TEXT")
    private String payload;     // The JSON data to send to Kafka

    @CreationTimestamp
    private LocalDateTime createdAt;

    private boolean processed = false; // Becomes true once sent to Kafka

}