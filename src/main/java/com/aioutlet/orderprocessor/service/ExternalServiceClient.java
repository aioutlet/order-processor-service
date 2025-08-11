package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.InventoryReservationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Client for external services (Order Service, Payment Service, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.payment.url}")
    private String paymentServiceUrl;

    @Value("${services.inventory.url}")
    private String inventoryServiceUrl;

    @Value("${services.shipping.url}")
    private String shippingServiceUrl;

    /**
     * Get order items from Order Service
     */
    public List<InventoryReservationEvent.InventoryItem> getOrderItems(UUID orderId) {
        try {
            // In a real implementation, you'd call the Order Service API
            // For now, return a mock list
            log.info("Fetching order items for order: {}", orderId);
            
            // Mock implementation - replace with actual API call
            return List.of(
                new InventoryReservationEvent.InventoryItem("product-1", 2),
                new InventoryReservationEvent.InventoryItem("product-2", 1)
            );
        } catch (Exception e) {
            log.error("Failed to fetch order items for order: {}", orderId, e);
            throw new RuntimeException("Failed to fetch order items", e);
        }
    }

    /**
     * Get customer payment method
     */
    public String getCustomerPaymentMethod(String customerId) {
        try {
            log.info("Fetching payment method for customer: {}", customerId);
            
            // Mock implementation - replace with actual API call
            return "credit_card";
        } catch (Exception e) {
            log.error("Failed to fetch payment method for customer: {}", customerId, e);
            return "default";
        }
    }

    /**
     * Get shipping address for order
     */
    public String getShippingAddress(UUID orderId) {
        try {
            log.info("Fetching shipping address for order: {}", orderId);
            
            // Mock implementation - replace with actual API call
            return "123 Main St, City, State, 12345";
        } catch (Exception e) {
            log.error("Failed to fetch shipping address for order: {}", orderId, e);
            return "default_address";
        }
    }
}
