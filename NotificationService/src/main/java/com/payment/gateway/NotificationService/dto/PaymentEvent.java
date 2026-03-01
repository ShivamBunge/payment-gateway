package com.payment.gateway.NotificationService.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <--- Add this line!
public class PaymentEvent {
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String customerEmail;
}