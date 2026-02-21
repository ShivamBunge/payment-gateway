package com.payment.gateway.TransactionPlatform.dto;

public record PaymentRequest(
        Double amount,
        String currency,
        String sourceAccount,
        String destinationAccount
) {}