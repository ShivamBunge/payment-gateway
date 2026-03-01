package com.payment.gateway.TransactionPlatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransactionPlatformApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void testIdempotencyFlow() {
		String idempotencyKey = "test-key-123";
		String body = "{\"amount\": 100, \"currency\": \"INR\"}";

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Idempotency-Key", idempotencyKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> entity = new HttpEntity<>(body, headers);

		// First Request - Should be SUCCESS
		ResponseEntity<String> firstResponse = restTemplate.postForEntity("/api/v1/payments", entity, String.class);
		assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Second Request (Same Key) - Should be duplicate (return the cached SUCCESS)
		ResponseEntity<String> secondResponse = restTemplate.postForEntity("/api/v1/payments", entity, String.class);
		assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(secondResponse.getBody()).contains("SUCCESS");
	}
}