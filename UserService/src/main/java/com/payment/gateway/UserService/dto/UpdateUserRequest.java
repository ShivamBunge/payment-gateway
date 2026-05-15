package com.payment.gateway.UserService.dto;

import com.payment.gateway.UserService.models.KycStatus;
import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
		@Email String email,
		String name,
		String phone,
		KycStatus kycStatus
) {}
