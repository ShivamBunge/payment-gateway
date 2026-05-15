package com.payment.gateway.UserService.exception;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException(String email) {
		super("Email already registered: " + email);
	}
}
