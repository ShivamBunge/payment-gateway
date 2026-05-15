package com.payment.gateway.UserService.dto;

import com.payment.gateway.UserService.models.KycStatus;

public record UserResponse(
		String userId,
		String email,
		String name,
		String phone,
		KycStatus kycStatus
) {}
