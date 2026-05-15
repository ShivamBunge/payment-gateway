package com.payment.gateway.TransactionPlatform.exception;

public class UserServiceException extends RuntimeException {

	public UserServiceException(String message) {
		super(message);
	}

	public UserServiceException(String message, Throwable cause) {
		super(message, cause);
	}

	public static class UserNotFoundException extends UserServiceException {
		public UserNotFoundException(String userId) {
			super("User not found: " + userId);
		}
	}

	public static class UserNotVerifiedException extends UserServiceException {
		public UserNotVerifiedException(String userId) {
			super("User KYC is not verified: " + userId);
		}
	}
}
