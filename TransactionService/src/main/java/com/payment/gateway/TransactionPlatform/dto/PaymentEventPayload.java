package com.payment.gateway.TransactionPlatform.dto;

import java.math.BigDecimal;

/**
 * Kafka outbox payload shape consumed by NotificationService.
 */
public record PaymentEventPayload(
		String transactionId,
		BigDecimal amount,
		String currency,
		String status,
		String userId,
		String customerEmail
) {}
