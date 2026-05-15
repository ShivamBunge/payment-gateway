package com.payment.gateway.TransactionPlatform.client.dto;

public record UserServiceResponse(
		String userId,
		String email,
		String name,
		String phone,
		String kycStatus
) {}
