package com.payment.gateway.UserService.controllers;

import com.payment.gateway.UserService.exception.DuplicateEmailException;
import com.payment.gateway.UserService.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(new ErrorResponse("validation_failed", message));
	}

	@ExceptionHandler(UserNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("user_not_found", ex.getMessage()));
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEmailException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("duplicate_email", ex.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
		log.error("Unhandled exception: {}", ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("internal_error", "An unexpected error occurred"));
	}

	record ErrorResponse(String error, String message) {}
}
