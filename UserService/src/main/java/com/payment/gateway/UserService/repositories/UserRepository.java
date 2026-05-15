package com.payment.gateway.UserService.repositories;

import com.payment.gateway.UserService.models.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByUserId(String userId);

	boolean existsByEmail(String email);
}
