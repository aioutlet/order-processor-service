package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
// import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for publishing messages to RabbitMQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TopicExchange orderExchange;

    // Note: Routing key @Value properties removed as they were unused
    // Methods use hardcoded routing keys instead of configurable ones

    /**
     * Publish payment processing event
     */
    public void publishPaymentProcessing(PaymentProcessingEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange.getName(), "payment.processing", event);
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
            rabbitTemplate.convertAndSend(orderExchange.getName(), "inventory.reservation", event);
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
            rabbitTemplate.convertAndSend(orderExchange.getName(), "shipping.preparation", event);
            log.info("Published shipping preparation event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish shipping preparation event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish shipping preparation event", e);
        }
    }

    /**
     * Publish order completed event
     */
    public void publishOrderCompleted(UUID orderId) {
        try {
            OrderCompletedEvent event = new OrderCompletedEvent(orderId, LocalDateTime.now());
            rabbitTemplate.convertAndSend(orderExchange.getName(), "order.completed", event);
            log.info("Published order completed event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish order completed event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish order completed event", e);
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
            rabbitTemplate.convertAndSend(orderExchange.getName(), "shipping.cancelled", event);
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
            rabbitTemplate.convertAndSend(orderExchange.getName(), "inventory.released", event);
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
            rabbitTemplate.convertAndSend(orderExchange.getName(), "payment.refund", event);
            log.info("Published payment refund event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish payment refund event for order: {}", orderId, e);
        }
    }

    /**
     * Forward payment processed event to Order Service
     */
    public void publishPaymentProcessed(PaymentProcessedEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange.getName(), "payment.processed", event);
            log.info("Published payment processed event for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish payment processed event for order: {}", event.getOrderId(), e);
        }
    }

    /**
     * Forward inventory reserved event to Order Service
     */
    public void publishInventoryReserved(InventoryReservedEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange.getName(), "inventory.reserved", event);
            log.info("Published inventory reserved event for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish inventory reserved event for order: {}", event.getOrderId(), e);
        }
    }

    /**
     * Forward shipping prepared event to Order Service
     */
    public void publishShippingPreparedToOrderService(ShippingPreparedEvent event) {
        try {
            rabbitTemplate.convertAndSend(orderExchange.getName(), "shipping.prepared", event);
            log.info("Published shipping prepared event for order: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish shipping prepared event for order: {}", event.getOrderId(), e);
        }
    }

    /**
     * Publish order failed event to Order Service
     */
    public void publishOrderFailed(UUID orderId, String reason, String failureStep) {
        try {
            OrderFailedEvent event = new OrderFailedEvent(
                orderId, 
                reason, 
                failureStep, 
                "SAGA_FAILURE", 
                LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(orderExchange.getName(), "order.failed", event);
            log.info("Published order failed event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish order failed event for order: {}", orderId, e);
        }
    }
}
