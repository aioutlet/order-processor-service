package com.aioutlet.orderprocessor.model.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Event published when an order is created
 * Matches the schema from the .NET Order Service
 */
@Data
public class OrderCreatedEvent {
    private UUID orderId;
    private String correlationId;
    private String customerId;
    private String orderNumber;
    private BigDecimal totalAmount;
    private String currency;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime createdAt;
    
    private List<OrderItemEvent> items;
    private AddressEvent shippingAddress;
    private AddressEvent billingAddress;
}
