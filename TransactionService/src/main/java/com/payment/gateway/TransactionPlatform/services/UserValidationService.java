package com.payment.gateway.TransactionPlatform.services;

import com.payment.gateway.TransactionPlatform.client.UserServiceClient;
import com.payment.gateway.TransactionPlatform.client.dto.UserServiceResponse;
import com.payment.gateway.TransactionPlatform.exception.UserServiceException;
import org.springframework.stereotype.Service;

@Service
public class UserValidationService {

	private final UserServiceClient userServiceClient;

	public UserValidationService(UserServiceClient userServiceClient) {
		this.userServiceClient = userServiceClient;
	}

	public UserServiceResponse validateUserForPayment(String userId) {
		UserServiceResponse user = userServiceClient.getUser(userId);

		if (!"VERIFIED".equalsIgnoreCase(user.kycStatus())) {
			throw new UserServiceException.UserNotVerifiedException(userId);
		}

		return user;
	}
}
