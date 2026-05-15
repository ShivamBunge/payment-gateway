package com.payment.gateway.TransactionPlatform.client;

import com.payment.gateway.TransactionPlatform.client.dto.UserServiceResponse;
import com.payment.gateway.TransactionPlatform.exception.UserServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserServiceClient {

	private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

	private final RestClient restClient;

	public UserServiceClient(
			RestClient.Builder restClientBuilder,
			@Value("${user.service.base-url}") String baseUrl) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).build();
	}

	public UserServiceResponse getUser(String userId) {
		try {
			return restClient.get()
					.uri("/api/v1/users/{userId}", userId)
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
						if (response.getStatusCode().value() == 404) {
							throw new UserServiceException.UserNotFoundException(userId);
						}
						throw new UserServiceException("User service returned " + response.getStatusCode());
					})
					.onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
						throw new UserServiceException("User service unavailable: " + response.getStatusCode());
					})
					.body(UserServiceResponse.class);
		} catch (UserServiceException e) {
			throw e;
		} catch (Exception e) {
			log.error("Failed to call User Service for userId {}: {}", userId, e.getMessage());
			throw new UserServiceException("Could not reach User Service", e);
		}
	}
}
