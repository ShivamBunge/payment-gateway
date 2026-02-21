package com.payment.gateway.TransactionPlatform.controllers;

import com.fasterxml.jackson.databind.ObjectMapper; // Import this
import com.payment.gateway.TransactionPlatform.dto.PaymentRequest;
import com.payment.gateway.TransactionPlatform.dto.PaymentResponse;
import com.payment.gateway.TransactionPlatform.services.IdempotencyService;
import com.payment.gateway.TransactionPlatform.services.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper; // 1. Define it

    // 2. Add it to the constructor
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
            @RequestBody PaymentRequest request) {

        if (idempotencyService.isDuplicate(idempotencyKey)) {
            String existingResponse = idempotencyService.getPreviousResponse(idempotencyKey);
            if ("PROCESSING".equals(existingResponse)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("{\"error\": \"Request is already being processed.\"}");
            }
            return ResponseEntity.ok(existingResponse);
        }

        try {
            // 3. Process the payment
            PaymentResponse response = paymentService.process(request, idempotencyKey);

            // 4. CONVERT OBJECT TO JSON STRING
            String jsonResponse = objectMapper.writeValueAsString(response);

            // 5. Update Redis with the JSON string
            idempotencyService.updateRecord(idempotencyKey, jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Payment Failed");
        }
    }
}