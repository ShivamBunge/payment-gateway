package com.payment.gateway.UserService.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, updatable = false)
	private String userId;

	@Column(unique = true, nullable = false)
	private String email;

	@Column(nullable = false)
	private String name;

	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private KycStatus kycStatus = KycStatus.PENDING;

	@CreationTimestamp
	@Column(updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	private LocalDateTime updatedAt;
}
