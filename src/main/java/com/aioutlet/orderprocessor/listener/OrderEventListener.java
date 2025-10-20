package com.aioutlet.orderprocessor.listener;

import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;
import com.aioutlet.orderprocessor.util.CorrelationIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens for order-related events from the message queue
 * and coordinates the saga flow through the choreography pattern
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final SagaOrchestratorService sagaOrchestratorService;

    /**
     * Handle order created event from order service
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderCreatedEvent(OrderCreatedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = "unknown";
        }
        
        // Set correlation ID using utility
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderCreatedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.startOrderProcessingSaga(event);
        } catch (Exception e) {
            log.error("Error processing OrderCreatedEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle payment processed event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handlePaymentProcessedEvent(PaymentProcessedEvent event) {
        log.info("Received PaymentProcessedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handlePaymentProcessed(event);
        } catch (Exception e) {
            log.error("Error processing PaymentProcessedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle payment failed event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handlePaymentFailedEvent(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handlePaymentFailed(event);
        } catch (Exception e) {
            log.error("Error processing PaymentFailedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle inventory reserved event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleInventoryReservedEvent(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handleInventoryReserved(event);
        } catch (Exception e) {
            log.error("Error processing InventoryReservedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle inventory reservation failed event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleInventoryFailedEvent(InventoryFailedEvent event) {
        log.info("Received InventoryFailedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handleInventoryFailed(event);
        } catch (Exception e) {
            log.error("Error processing InventoryFailedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle shipping prepared event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleShippingPreparedEvent(ShippingPreparedEvent event) {
        log.info("Received ShippingPreparedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handleShippingPrepared(event);
        } catch (Exception e) {
            log.error("Error processing ShippingPreparedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle shipping failed event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleShippingFailedEvent(ShippingFailedEvent event) {
        log.info("Received ShippingFailedEvent for order: {}", event.getOrderId());
        
        try {
            sagaOrchestratorService.handleShippingFailed(event);
        } catch (Exception e) {
            log.error("Error processing ShippingFailedEvent for order {}: {}", event.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Handle order updated event (status changes)
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderUpdatedEvent(OrderStatusChangedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderStatusChangedEvent for order: {}, status: {} -> {} [CorrelationId: {}]", 
                    event.getOrderId(), event.getPreviousStatus(), event.getNewStatus(), correlationId);
            
            sagaOrchestratorService.handleOrderStatusChanged(event);
        } catch (Exception e) {
            log.error("Error processing OrderStatusChangedEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order cancelled event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderCancelledEvent(OrderStatusChangedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderCancelledEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            // Trigger compensation/rollback for cancelled orders
            sagaOrchestratorService.handleOrderCancelled(event);
        } catch (Exception e) {
            log.error("Error processing OrderCancelledEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order shipped event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderShippedEvent(OrderStatusChangedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderShippedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderShipped(event);
        } catch (Exception e) {
            log.error("Error processing OrderShippedEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order delivered event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderDeliveredEvent(OrderStatusChangedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderDeliveredEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            sagaOrchestratorService.handleOrderDelivered(event);
        } catch (Exception e) {
            log.error("Error processing OrderDeliveredEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }

    /**
     * Handle order deleted event
     */
    @RabbitListener(queues = "${messaging.queue.order-processor}")
    public void handleOrderDeletedEvent(OrderDeletedEvent event, @Header Map<String, Object> headers) {
        String correlationId = event.getCorrelationId();
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = (String) headers.get("X-Correlation-ID");
        }
        
        CorrelationIdUtil.setCorrelationId(correlationId);
        
        try {
            log.info("Received OrderDeletedEvent for order: {} [CorrelationId: {}]", 
                    event.getOrderId(), correlationId);
            
            // Clean up any associated saga records
            sagaOrchestratorService.handleOrderDeleted(event);
        } catch (Exception e) {
            log.error("Error processing OrderDeletedEvent for order {} [CorrelationId: {}]: {}", 
                    event.getOrderId(), correlationId, e.getMessage(), e);
        } finally {
            CorrelationIdUtil.clearCorrelationId();
        }
    }
}
