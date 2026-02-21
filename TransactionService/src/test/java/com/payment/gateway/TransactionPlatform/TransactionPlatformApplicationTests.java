package com.payment.gateway.TransactionPlatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransactionPlatformApplicationTests {

	@Test
	void contextLoads() {
		// This will now pass as long as your manual Docker is running
	}
}