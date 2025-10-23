package com.aioutlet.orderprocessor.listener;

import com.aioutlet.orderprocessor.model.MessageWrapper;
import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;
import com.aioutlet.orderprocessor.util.CorrelationIdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for order-related events from the message queue
 * and coordinates the saga flow through the choreography pattern.
 * Uses a unified listener with routing key based dispatch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final SagaOrchestratorService sagaOrchestratorService;
    private final ObjectMapper objectMapper;

    /**
     * Unified message listener that routes to appropriate handler based on routing key
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleMessage(Message message, @Header Map<String, Object> headers) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        
        try {
            log.debug("Received message with routing key: {}", routingKey);
            
            switch (routingKey) {
                case "order.created":
                    handleOrderCreatedEvent(message, headers);
                    break;
                case "payment.processed":
                    handlePaymentProcessedEvent(message, headers);
                    break;
                case "payment.failed":
                    handlePaymentFailedEvent(message, headers);
                    break;
                case "inventory.reserved":
                    handleInventoryReservedEvent(message, headers);
                    break;
                case "inventory.failed":
                    handleInventoryFailedEvent(message, headers);
                    break;
                case "shipping.prepared":
                    handleShippingPreparedEvent(message, headers);
                    break;
                case "shipping.failed":
                    handleShippingFailedEvent(message, headers);
                    break;
                case "order.updated":
                case "order.status.changed":
                    handleOrderUpdatedEvent(message, headers);
                    break;
                case "order.cancelled":
                    handleOrderCancelledEvent(message, headers);
                    break;
                case "order.shipped":
                    handleOrderShippedEvent(message, headers);
                    break;
                case "order.delivered":
                    handleOrderDeliveredEvent(message, headers);
                    break;
                case "order.deleted":
                    handleOrderDeletedEvent(message, headers);
                    break;
                default:
                    log.warn("Unhandled routing key: {}", routingKey);
            }
        } catch (Exception e) {
            log.error("Error processing message with routing key {}: {}", routingKey, e.getMessage(), e);
            throw e; // Re-throw to trigger retry/DLQ
        }
    }

    /**
     * Handle order created event from order service
     */
    private void handleOrderCreatedEvent(Message message, Map<String, Object> headers) {
        try {
            // Unwrap the message-broker-service wrapper
            MessageWrapper wrapper = objectMapper.readValue(message.getBody(), MessageWrapper.class);
            
            // Extract and deserialize the actual event from the data field
            OrderCreatedEvent event = objectMapper.convertValue(wrapper.getData(), OrderCreatedEvent.class);
            
            String correlationId = extractCorrelationId(
                wrapper.getCorrelationId() != null ? wrapper.getCorrelationId() : event.getCorrelationId(), 
                headers
            );
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderCreatedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.startOrderProcessingSaga(event);
        } catch (Exception e) {
            log.error("Error processing OrderCreatedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderCreatedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle payment processed event
     */
    private void handlePaymentProcessedEvent(Message message, Map<String, Object> headers) {
        try {
            PaymentProcessedEvent event = objectMapper.readValue(message.getBody(), PaymentProcessedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received PaymentProcessedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handlePaymentProcessed(event);
        } catch (Exception e) {
            log.error("Error processing PaymentProcessedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process PaymentProcessedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle payment failed event
     */
    private void handlePaymentFailedEvent(Message message, Map<String, Object> headers) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(message.getBody(), PaymentFailedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received PaymentFailedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handlePaymentFailed(event);
        } catch (Exception e) {
            log.error("Error processing PaymentFailedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process PaymentFailedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle inventory reserved event
     */
    private void handleInventoryReservedEvent(Message message, Map<String, Object> headers) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(message.getBody(), InventoryReservedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received InventoryReservedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleInventoryReserved(event);
        } catch (Exception e) {
            log.error("Error processing InventoryReservedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process InventoryReservedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle inventory failed event
     */
    private void handleInventoryFailedEvent(Message message, Map<String, Object> headers) {
        try {
            InventoryFailedEvent event = objectMapper.readValue(message.getBody(), InventoryFailedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received InventoryFailedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleInventoryFailed(event);
        } catch (Exception e) {
            log.error("Error processing InventoryFailedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process InventoryFailedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle shipping prepared event
     */
    private void handleShippingPreparedEvent(Message message, Map<String, Object> headers) {
        try {
            ShippingPreparedEvent event = objectMapper.readValue(message.getBody(), ShippingPreparedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received ShippingPreparedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleShippingPrepared(event);
        } catch (Exception e) {
            log.error("Error processing ShippingPreparedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process ShippingPreparedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle shipping failed event
     */
    private void handleShippingFailedEvent(Message message, Map<String, Object> headers) {
        try {
            ShippingFailedEvent event = objectMapper.readValue(message.getBody(), ShippingFailedEvent.class);
            String correlationId = extractCorrelationId(null, headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received ShippingFailedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleShippingFailed(event);
        } catch (Exception e) {
            log.error("Error processing ShippingFailedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process ShippingFailedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order updated event (general status changes)
     */
    private void handleOrderUpdatedEvent(Message message, Map<String, Object> headers) {
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
            String correlationId = extractCorrelationId(event.getCorrelationId(), headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderStatusChangedEvent for order: {}, status: {} -> {} [CorrelationId: {}]", 
                    event.getOrderId(), event.getPreviousStatus(), event.getNewStatus(), correlationId);
            
            sagaOrchestratorService.handleOrderStatusChanged(event);
        } catch (Exception e) {
            log.error("Error processing OrderStatusChangedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderStatusChangedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order cancelled event
     */
    private void handleOrderCancelledEvent(Message message, Map<String, Object> headers) {
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
            String correlationId = extractCorrelationId(event.getCorrelationId(), headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderCancelledEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderCancelled(event);
        } catch (Exception e) {
            log.error("Error processing OrderCancelledEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderCancelledEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order shipped event
     */
    private void handleOrderShippedEvent(Message message, Map<String, Object> headers) {
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
            String correlationId = extractCorrelationId(event.getCorrelationId(), headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderShippedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderShipped(event);
        } catch (Exception e) {
            log.error("Error processing OrderShippedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderShippedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order delivered event
     */
    private void handleOrderDeliveredEvent(Message message, Map<String, Object> headers) {
        try {
            OrderStatusChangedEvent event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
            String correlationId = extractCorrelationId(event.getCorrelationId(), headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderDeliveredEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderDelivered(event);
        } catch (Exception e) {
            log.error("Error processing OrderDeliveredEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderDeliveredEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order deleted event
     */
    private void handleOrderDeletedEvent(Message message, Map<String, Object> headers) {
        try {
            OrderDeletedEvent event = objectMapper.readValue(message.getBody(), OrderDeletedEvent.class);
            String correlationId = extractCorrelationId(event.getCorrelationId(), headers);
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            log.info("Received OrderDeletedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderDeleted(event);
        } catch (Exception e) {
            log.error("Error processing OrderDeletedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OrderDeletedEvent", e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Helper method to extract correlation ID from event or headers
     * 
     * @param eventCorrelationId Correlation ID from event object
     * @param headers Message headers
     * @return Correlation ID or "unknown" if not found
     */
    private String extractCorrelationId(String eventCorrelationId, Map<String, Object> headers) {
        // Try event correlation ID first
        if (eventCorrelationId != null && !eventCorrelationId.isEmpty()) {
            return eventCorrelationId;
        }
        
        // Try headers
        if (headers != null) {
            Object headerValue = headers.get("X-Correlation-ID");
            if (headerValue != null) {
                return headerValue.toString();
            }
            
            // Try lowercase variant
            headerValue = headers.get("x-correlation-id");
            if (headerValue != null) {
                return headerValue.toString();
            }
        }
        
        // Default to "unknown"
        return "unknown";
    }
}
