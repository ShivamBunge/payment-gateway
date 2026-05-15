package com.payment.gateway.UserService.services;

import com.payment.gateway.UserService.dto.CreateUserRequest;
import com.payment.gateway.UserService.dto.UpdateUserRequest;
import com.payment.gateway.UserService.dto.UserResponse;
import com.payment.gateway.UserService.exception.DuplicateEmailException;
import com.payment.gateway.UserService.exception.UserNotFoundException;
import com.payment.gateway.UserService.models.KycStatus;
import com.payment.gateway.UserService.models.UserEntity;
import com.payment.gateway.UserService.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new DuplicateEmailException(request.email());
		}

		UserEntity user = new UserEntity();
		user.setUserId(UUID.randomUUID().toString());
		user.setEmail(request.email());
		user.setName(request.name());
		user.setPhone(request.phone());
		user.setKycStatus(KycStatus.PENDING);

		return toResponse(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public UserResponse getByUserId(String userId) {
		return userRepository.findByUserId(userId)
				.map(this::toResponse)
				.orElseThrow(() -> new UserNotFoundException(userId));
	}

	@Transactional
	public UserResponse update(String userId, UpdateUserRequest request) {
		UserEntity user = userRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException(userId));

		if (request.email() != null && !request.email().equals(user.getEmail())) {
			if (userRepository.existsByEmail(request.email())) {
				throw new DuplicateEmailException(request.email());
			}
			user.setEmail(request.email());
		}
		if (request.name() != null) {
			user.setName(request.name());
		}
		if (request.phone() != null) {
			user.setPhone(request.phone());
		}
		if (request.kycStatus() != null) {
			user.setKycStatus(request.kycStatus());
		}

		return toResponse(userRepository.save(user));
	}

	private UserResponse toResponse(UserEntity user) {
		return new UserResponse(
				user.getUserId(),
				user.getEmail(),
				user.getName(),
				user.getPhone(),
				user.getKycStatus()
		);
	}
}
