package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for publishing messages to RabbitMQ
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePublishingService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${messaging.exchange.name}")
    private String exchangeName;

    @Value("${messaging.routing-key.payment-processed}")
    private String paymentProcessingRoutingKey;

    @Value("${messaging.routing-key.inventory-reserved}")
    private String inventoryReservationRoutingKey;

    @Value("${messaging.routing-key.shipping-prepared}")
    private String shippingPreparationRoutingKey;

    /**
     * Publish payment processing event
     */
    public void publishPaymentProcessing(PaymentProcessingEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, "payment.process", event);
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
            rabbitTemplate.convertAndSend(exchangeName, "inventory.reserve", event);
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
            rabbitTemplate.convertAndSend(exchangeName, "shipping.prepare", event);
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
            OrderCompletedEvent event = new OrderCompletedEvent(
                    orderId,
                    LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(exchangeName, "order.completed", event);
            log.info("Published order completed event for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to publish order completed event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish order completed event", e);
        }
    }

    /**
     * Publish payment refund event for compensation
     */
    public void publishPaymentRefund(UUID orderId, String paymentId) {
        try {
            PaymentRefundEvent event = new PaymentRefundEvent(
                    orderId,
                    paymentId,
                    LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(exchangeName, "payment.refund", event);
            log.info("Published payment refund event for order: {}, payment: {}", orderId, paymentId);
        } catch (Exception e) {
            log.error("Failed to publish payment refund event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish payment refund event", e);
        }
    }

    /**
     * Publish inventory release event for compensation
     */
    public void publishInventoryRelease(UUID orderId, String reservationId) {
        try {
            InventoryReleaseEvent event = new InventoryReleaseEvent(
                    orderId,
                    reservationId,
                    LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(exchangeName, "inventory.release", event);
            log.info("Published inventory release event for order: {}, reservation: {}", orderId, reservationId);
        } catch (Exception e) {
            log.error("Failed to publish inventory release event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish inventory release event", e);
        }
    }

    /**
     * Publish shipping cancellation event for compensation
     */
    public void publishShippingCancellation(UUID orderId, String shippingId) {
        try {
            ShippingCancellationEvent event = new ShippingCancellationEvent(
                    orderId,
                    shippingId,
                    LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(exchangeName, "shipping.cancel", event);
            log.info("Published shipping cancellation event for order: {}, shipping: {}", orderId, shippingId);
        } catch (Exception e) {
            log.error("Failed to publish shipping cancellation event for order: {}", orderId, e);
            throw new RuntimeException("Failed to publish shipping cancellation event", e);
        }
    }
}
