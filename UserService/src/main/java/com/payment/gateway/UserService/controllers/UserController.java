package com.payment.gateway.UserService.controllers;

import com.payment.gateway.UserService.dto.CreateUserRequest;
import com.payment.gateway.UserService.dto.UpdateUserRequest;
import com.payment.gateway.UserService.dto.UserResponse;
import com.payment.gateway.UserService.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		UserResponse response = userService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{userId}")
	public ResponseEntity<UserResponse> getById(@PathVariable String userId) {
		return ResponseEntity.ok(userService.getByUserId(userId));
	}

	@PatchMapping("/{userId}")
	public ResponseEntity<UserResponse> update(
			@PathVariable String userId,
			@Valid @RequestBody UpdateUserRequest request) {
		return ResponseEntity.ok(userService.update(userId, request));
	}
}
