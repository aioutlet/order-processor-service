package com.aioutlet.orderprocessor.model.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when payment processing is requested
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessingEvent {
    private UUID orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime requestedAt;
}
