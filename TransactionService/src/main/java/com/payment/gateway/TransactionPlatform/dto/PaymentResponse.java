package com.payment.gateway.TransactionPlatform.dto;

public record PaymentResponse(
        String transactionId,
        String status,
        String message
) {}
