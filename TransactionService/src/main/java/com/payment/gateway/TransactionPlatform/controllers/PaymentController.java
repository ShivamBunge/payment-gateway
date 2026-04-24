package com.payment.gateway.TransactionPlatform.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.TransactionPlatform.dto.PaymentRequest;
import com.payment.gateway.TransactionPlatform.dto.PaymentResponse;
import com.payment.gateway.TransactionPlatform.services.IdempotencyService;
import com.payment.gateway.TransactionPlatform.services.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentController(IdempotencyService idempotencyService,
                             PaymentService paymentService,
                             ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> processPayment(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        if (idempotencyService.isDuplicate(idempotencyKey)) {
            String existingResponse = idempotencyService.getPreviousResponse(idempotencyKey);

            if (existingResponse == null) {
                // Key exists but no readable response yet
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("{\"message\": \"Transaction in progress. Please wait.\"}");
            }

            // If stored value starts with FAILED:, return 500 with message
            if (existingResponse.startsWith("FAILED:")) {
                String reason = existingResponse.substring("FAILED:".length());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\": \"processing_failed\", \"reason\": \"" + reason + "\"}");
            }

            // Otherwise return the stored final response (could be JSON string)
            return ResponseEntity.ok(existingResponse);
        }

        try {
            PaymentResponse response = paymentService.process(request, idempotencyKey);
            String jsonResponse = objectMapper.writeValueAsString(response);

            boolean updated = idempotencyService.finalizeResponseIfProcessing(idempotencyKey, jsonResponse);
            if (!updated) {
                log.warn("Idempotency key {} was not in PROCESSING state when finalizing response", idempotencyKey);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing payment for idempotencyKey {}: {}", idempotencyKey, e.getMessage(), e);
            idempotencyService.markFailedIfProcessing(idempotencyKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"Payment Failed\"}");
        }
    }
}