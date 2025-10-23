package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for publishing messages using the configured message broker adapter
 * This service provides a high-level API for saga orchestration events
 * and delegates to MessageBrokerService which uses the adapter pattern
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final MessageBrokerService messageBrokerService;

    /**
     * Publish payment processing event
     */
    public void publishPaymentProcessing(PaymentProcessingEvent event) {
        try {
            messageBrokerService.publishPaymentProcessing(event);
            log.info("Published payment processing event for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish payment processing event for order: {}", event.getOrderId(), e);
            throw new RuntimeException("Failed to publish payment processing event", e);
        }
    }

    /**
     * Publish inventory reservation event
     */
    public void publishInventoryReservation(InventoryReservationEvent event) {
        try {
            messageBrokerService.publishInventoryReservation(event);
            log.info("Published inventory reservation event for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish inventory reservation event for order: {}", event.getOrderId(), e);
            throw new RuntimeException("Failed to publish inventory reservation event", e);
        }
    }

    /**
     * Publish shipping preparation event
     */
    public void publishShippingPreparation(UUID orderId, String customerId) {
        try {
            ShippingPreparationEvent event = new ShippingPreparationEvent(
                    orderId, 
                    customerId, 
                    LocalDateTime.now()
            );
            messageBrokerService.publishShippingPreparation(event);
            log.info("Published shipping preparation event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish shipping preparation event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish shipping preparation event", e);
        }
    }

    /**
     * Publish order status changed event to notify Order Service
     */
    public void publishOrderStatusChanged(UUID orderId, String orderNumber, String customerId, 
                                          String previousStatus, String newStatus, 
                                          String reason, String correlationId) {
        try {
            OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                orderId.toString(),
                orderNumber,
                customerId,
                previousStatus,
                newStatus,
                LocalDateTime.now(),
                "order-processor-service", // updatedBy
                reason,
                correlationId
            );
            messageBrokerService.publishOrderStatusChanged(event);
            log.info("Published order status changed event: {} -> {} for order: {} [CorrelationId: {}]", 
                    previousStatus, newStatus, orderId, correlationId);
        } catch (Exception e) {
            log.error("Failed to publish order status changed event for order: {} [CorrelationId: {}]", 
                    orderId, correlationId, e);
            throw new RuntimeException("Failed to publish order status changed event", e);
        }
    }



    /**
     * Publish shipping cancellation event
     */
    public void publishShippingCancellation(UUID orderId, String shippingId) {
        try {
            ShippingCancellationEvent event = new ShippingCancellationEvent(
                orderId, 
                shippingId, 
                "Saga compensation", 
                LocalDateTime.now()
            );
            messageBrokerService.publishShippingCancellation(orderId, shippingId);
            log.info("Published shipping cancellation event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish shipping cancellation event for order: {}", orderId, e);
        }
    }

    /**
     * Publish inventory release event
     */
    public void publishInventoryRelease(UUID orderId, String reservationId) {
        try {
            InventoryReleaseEvent event = new InventoryReleaseEvent(
                orderId, 
                reservationId, 
                "Saga compensation", 
                LocalDateTime.now()
            );
            messageBrokerService.publishInventoryRelease(orderId, reservationId);
            log.info("Published inventory release event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish inventory release event for order: {}", orderId, e);
        }
    }

    /**
     * Publish payment refund event
     */
    public void publishPaymentRefund(UUID orderId, String paymentId) {
        try {
            PaymentRefundEvent event = new PaymentRefundEvent(
                orderId, 
                paymentId, 
                UUID.randomUUID().toString(), // refundId
                "Saga compensation", 
                LocalDateTime.now()
            );
            messageBrokerService.publishPaymentRefund(orderId, paymentId);
            log.info("Published payment refund event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish payment refund event for order: {}", orderId, e);
        }
    }

    /**
     * Helper method to publish order status change for saga completion
     */
    public void publishOrderCompletedStatus(UUID orderId, String orderNumber, String customerId, String correlationId) {
        publishOrderStatusChanged(orderId, orderNumber, customerId, 
                "Processing", "Completed", 
                "Order processing saga completed successfully", correlationId);
    }

    /**
     * Helper method to publish order status change for saga failure
     */
    public void publishOrderFailedStatus(UUID orderId, String orderNumber, String customerId, 
                                         String reason, String correlationId) {
        publishOrderStatusChanged(orderId, orderNumber, customerId, 
                "Processing", "Failed", 
                reason, correlationId);
    }

    /**
     * Helper method to publish order status change for payment processed
     */
    public void publishPaymentProcessedStatus(UUID orderId, String orderNumber, String customerId, String correlationId) {
        publishOrderStatusChanged(orderId, orderNumber, customerId, 
                "Pending", "PaymentProcessed", 
                "Payment processed successfully", correlationId);
    }

    /**
     * Helper method to publish order status change for inventory reserved
     */
    public void publishInventoryReservedStatus(UUID orderId, String orderNumber, String customerId, String correlationId) {
        publishOrderStatusChanged(orderId, orderNumber, customerId, 
                "PaymentProcessed", "InventoryReserved", 
                "Inventory reserved successfully", correlationId);
    }

    /**
     * Helper method to publish order status change for shipping prepared
     */
    public void publishShippingPreparedStatus(UUID orderId, String orderNumber, String customerId, String correlationId) {
        publishOrderStatusChanged(orderId, orderNumber, customerId, 
                "InventoryReserved", "Shipped", 
                "Shipping prepared successfully", correlationId);
    }
}
