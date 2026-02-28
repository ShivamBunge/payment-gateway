package com.payment.gateway.TransactionPlatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TransactionPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransactionPlatformApplication.class, args);
	}

}
