package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Choreography-based Saga Orchestrator Service
 * Manages the state and flow of order processing saga
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorService {

    private final OrderProcessingSagaRepository sagaRepository;
    private final MessagePublisher messagePublisher;
    private final SagaMetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Value("${saga.retry.maxAttempts:3}")
    private int maxRetryAttempts;

    /**
     * Start a new saga for order processing
     */
    @Transactional
    public void startOrderProcessingSaga(OrderCreatedEvent orderCreatedEvent) {
        log.info("Starting order processing saga for order: {}", orderCreatedEvent.getOrderId());

        // Check if saga already exists
        if (sagaRepository.existsByOrderId(orderCreatedEvent.getOrderId())) {
            log.warn("Saga already exists for order: {}", orderCreatedEvent.getOrderId());
            return;
        }

        // Create new saga
        OrderProcessingSaga saga = new OrderProcessingSaga();
        saga.setOrderId(orderCreatedEvent.getOrderId());
        saga.setCustomerId(orderCreatedEvent.getCustomerId());
        saga.setOrderNumber(orderCreatedEvent.getOrderNumber());
        saga.setTotalAmount(orderCreatedEvent.getTotalAmount());
        saga.setCurrency(orderCreatedEvent.getCurrency());
        saga.setStatus(OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING);
        saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.PAYMENT_PROCESSING);

        // Store order items and addresses from event (event-driven architecture)
        // This eliminates the need for HTTP calls to Order Service
        try {
            if (orderCreatedEvent.getItems() != null && !orderCreatedEvent.getItems().isEmpty()) {
                saga.setOrderItems(objectMapper.writeValueAsString(orderCreatedEvent.getItems()));
            }
            if (orderCreatedEvent.getShippingAddress() != null) {
                saga.setShippingAddress(objectMapper.writeValueAsString(orderCreatedEvent.getShippingAddress()));
            }
            if (orderCreatedEvent.getBillingAddress() != null) {
                saga.setBillingAddress(objectMapper.writeValueAsString(orderCreatedEvent.getBillingAddress()));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order data for saga", e);
            throw new RuntimeException("Failed to store order data in saga", e);
        }

        saga = sagaRepository.save(saga);
        log.info("Created saga {} for order: {}", saga.getId(), orderCreatedEvent.getOrderId());

        // Record metrics
        metricsService.recordSagaStarted(orderCreatedEvent.getOrderNumber());

        // Start payment processing
        try {
            processPayment(saga, orderCreatedEvent);
        } catch (Exception e) {
            log.error("Failed to start payment processing for saga: {}", saga.getId(), e);
            handleSagaFailure(saga, "Failed to start payment processing: " + e.getMessage());
        }
    }

    /**
     * Handle payment processed event
     */
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedEvent paymentProcessedEvent) {
        log.info("Handling payment processed for order: {}", paymentProcessedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(paymentProcessedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", paymentProcessedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        saga.setPaymentId(paymentProcessedEvent.getPaymentId());
        saga.setStatus(OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING);
        saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.INVENTORY_PROCESSING);
        
        saga = sagaRepository.save(saga);
        log.info("Updated saga {} status to INVENTORY_PROCESSING", saga.getId());

        // Notify Order Service of payment completion
        messagePublisher.publishPaymentProcessed(paymentProcessedEvent);

        // Proceed to inventory reservation
        try {
            reserveInventory(saga);
        } catch (Exception e) {
            log.error("Failed to start inventory reservation for saga: {}", saga.getId(), e);
            handleSagaFailure(saga, "Failed to start inventory reservation: " + e.getMessage());
        }
    }

    /**
     * Handle payment failed event
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent paymentFailedEvent) {
        log.info("Handling payment failure for order: {}", paymentFailedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(paymentFailedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", paymentFailedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        if (saga.canRetry()) {
            log.info("Retrying payment for saga: {} (attempt {})", saga.getId(), saga.getRetryCount() + 1);
            saga.incrementRetry();
            sagaRepository.save(saga);
            
            // Retry payment processing with exponential backoff
            // In a real implementation, you'd use a scheduler or delay queue
            try {
                Thread.sleep(1000 * saga.getRetryCount()); // Simple backoff
                // Re-fetch order details and retry payment
                processPaymentRetry(saga);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleSagaFailure(saga, "Payment retry interrupted");
            } catch (Exception e) {
                log.error("Failed to retry payment for saga: {}", saga.getId(), e);
                handleSagaFailure(saga, "Payment retry failed: " + e.getMessage());
            }
        } else {
            log.error("Payment failed for saga: {} after {} attempts", saga.getId(), saga.getRetryCount());
            handleSagaFailure(saga, "Payment failed: " + paymentFailedEvent.getReason());
        }
    }

    /**
     * Handle inventory reserved event
     */
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent inventoryReservedEvent) {
        log.info("Handling inventory reserved for order: {}", inventoryReservedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(inventoryReservedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", inventoryReservedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        saga.setInventoryReservationId(inventoryReservedEvent.getReservationId());
        saga.setStatus(OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING);
        saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.SHIPPING_PROCESSING);
        
        saga = sagaRepository.save(saga);
        log.info("Updated saga {} status to SHIPPING_PROCESSING", saga.getId());

        // Notify Order Service of inventory reservation
        messagePublisher.publishInventoryReserved(inventoryReservedEvent);

        // Proceed to shipping preparation
        try {
            prepareShipping(saga);
        } catch (Exception e) {
            log.error("Failed to start shipping preparation for saga: {}", saga.getId(), e);
            handleSagaFailure(saga, "Failed to start shipping preparation: " + e.getMessage());
        }
    }

    /**
     * Handle inventory reservation failed event
     */
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent inventoryFailedEvent) {
        log.info("Handling inventory failure for order: {}", inventoryFailedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(inventoryFailedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", inventoryFailedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        if (saga.canRetry()) {
            log.info("Retrying inventory reservation for saga: {} (attempt {})", saga.getId(), saga.getRetryCount() + 1);
            saga.incrementRetry();
            sagaRepository.save(saga);
            
            try {
                Thread.sleep(1000 * saga.getRetryCount()); // Simple backoff
                reserveInventory(saga);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleSagaFailure(saga, "Inventory retry interrupted");
            } catch (Exception e) {
                log.error("Failed to retry inventory reservation for saga: {}", saga.getId(), e);
                handleSagaFailure(saga, "Inventory retry failed: " + e.getMessage());
            }
        } else {
            log.error("Inventory reservation failed for saga: {} after {} attempts", saga.getId(), saga.getRetryCount());
            handleSagaFailure(saga, "Inventory reservation failed: " + inventoryFailedEvent.getReason());
        }
    }

    /**
     * Handle shipping prepared event
     */
    @Transactional
    public void handleShippingPrepared(ShippingPreparedEvent shippingPreparedEvent) {
        log.info("Handling shipping prepared for order: {}", shippingPreparedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(shippingPreparedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", shippingPreparedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        saga.setShippingId(shippingPreparedEvent.getShippingId());
        saga.setStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
        saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.COMPLETED);
        saga.markCompleted();
        
        sagaRepository.save(saga);
        log.info("Successfully completed saga {} for order: {}", saga.getId(), shippingPreparedEvent.getOrderId());
        
        // Notify Order Service of shipping preparation
        messagePublisher.publishShippingPreparedToOrderService(shippingPreparedEvent);
        
        // Publish order completed event
        messagePublisher.publishOrderCompleted(shippingPreparedEvent.getOrderId());
    }

    /**
     * Handle shipping failed event
     */
    @Transactional
    public void handleShippingFailed(ShippingFailedEvent shippingFailedEvent) {
        log.info("Handling shipping failure for order: {}", shippingFailedEvent.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(shippingFailedEvent.getOrderId());
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", shippingFailedEvent.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        if (saga.canRetry()) {
            log.info("Retrying shipping preparation for saga: {} (attempt {})", saga.getId(), saga.getRetryCount() + 1);
            saga.incrementRetry();
            sagaRepository.save(saga);
            
            try {
                Thread.sleep(1000 * saga.getRetryCount()); // Simple backoff
                prepareShipping(saga);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleSagaFailure(saga, "Shipping retry interrupted");
            } catch (Exception e) {
                log.error("Failed to retry shipping preparation for saga: {}", saga.getId(), e);
                handleSagaFailure(saga, "Shipping retry failed: " + e.getMessage());
            }
        } else {
            log.error("Shipping preparation failed for saga: {} after {} attempts", saga.getId(), saga.getRetryCount());
            handleSagaFailure(saga, "Shipping preparation failed: " + shippingFailedEvent.getReason());
        }
    }
    @Transactional
    public void completeSaga(UUID orderId) {
        log.info("Completing saga for order: {}", orderId);

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(orderId);
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for order: {}", orderId);
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        saga.markCompleted();
        sagaRepository.save(saga);
        
        log.info("Successfully completed saga {} for order: {}", saga.getId(), orderId);
        
        // Publish order completed event
        messagePublisher.publishOrderCompleted(orderId);
    }

    /**
     * Handle saga failure and initiate compensation
     */
    @Transactional
    public void handleSagaFailure(OrderProcessingSaga saga, String errorMessage) {
        log.error("Handling saga failure for saga: {} - {}", saga.getId(), errorMessage);

        saga.markFailed(errorMessage);
        saga.setStatus(OrderProcessingSaga.SagaStatus.COMPENSATING);
        sagaRepository.save(saga);

        // Start compensation process
        try {
            compensateSaga(saga);
        } catch (Exception e) {
            log.error("Failed to compensate saga: {}", saga.getId(), e);
            saga.setStatus(OrderProcessingSaga.SagaStatus.FAILED);
            sagaRepository.save(saga);
        }
    }

    /**
     * Process payment for the order
     */
    private void processPayment(OrderProcessingSaga saga, OrderCreatedEvent orderEvent) {
        log.info("Processing payment for saga: {}", saga.getId());

        PaymentProcessingEvent paymentEvent = new PaymentProcessingEvent(
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getTotalAmount(),
                saga.getCurrency(),
                "default", // payment method
                LocalDateTime.now()
        );

        messagePublisher.publishPaymentProcessing(paymentEvent);
    }

    /**
     * Retry payment processing
     */
    private void processPaymentRetry(OrderProcessingSaga saga) {
        log.info("Retrying payment for saga: {}", saga.getId());
        
        PaymentProcessingEvent paymentEvent = new PaymentProcessingEvent(
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getTotalAmount(),
                saga.getCurrency(),
                "default",
                LocalDateTime.now()
        );

        messagePublisher.publishPaymentProcessing(paymentEvent);
    }

    /**
     * Reserve inventory for the order
     * Note: Order items should be included in the OrderCreatedEvent
     * This maintains event-driven architecture without direct service calls
     */
    private void reserveInventory(OrderProcessingSaga saga) {
        log.info("Reserving inventory for saga: {}", saga.getId());

        // Deserialize order items from saga (stored from OrderCreatedEvent)
        // This maintains event-driven architecture without HTTP calls
        List<InventoryItem> items;
        try {
            if (saga.getOrderItems() != null) {
                items = objectMapper.readValue(
                    saga.getOrderItems(), 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, InventoryItem.class)
                );
            } else {
                log.warn("No order items found in saga {}, using empty list", saga.getId());
                items = java.util.Collections.emptyList();
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize order items from saga", e);
            throw new RuntimeException("Failed to read order items from saga", e);
        }

        InventoryReservationEvent inventoryEvent = new InventoryReservationEvent(
            saga.getOrderId(),
            items,
            LocalDateTime.now()
        );

        messagePublisher.publishInventoryReservation(inventoryEvent);
    }

    /**
     * Prepare shipping for the order
     */
    private void prepareShipping(OrderProcessingSaga saga) {
        log.info("Preparing shipping for saga: {}", saga.getId());

        // In a real implementation, you'd fetch shipping details and prepare shipping
        messagePublisher.publishShippingPreparation(saga.getOrderId(), saga.getCustomerId());
    }

    /**
     * Compensate the saga by reversing completed actions
     */
    private void compensateSaga(OrderProcessingSaga saga) {
        log.info("Compensating saga: {}", saga.getId());

        // Reverse actions in reverse order
        if (saga.getShippingId() != null) {
            messagePublisher.publishShippingCancellation(saga.getOrderId(), saga.getShippingId());
        }

        if (saga.getInventoryReservationId() != null) {
            messagePublisher.publishInventoryRelease(saga.getOrderId(), saga.getInventoryReservationId());
        }

        if (saga.getPaymentId() != null) {
            messagePublisher.publishPaymentRefund(saga.getOrderId(), saga.getPaymentId());
        }

        saga.setStatus(OrderProcessingSaga.SagaStatus.COMPENSATED);
        sagaRepository.save(saga);
        
        // Notify Order Service of order failure
        String failureStep = determineFailureStep(saga);
        messagePublisher.publishOrderFailed(saga.getOrderId(), saga.getErrorMessage(), failureStep);
        
        log.info("Completed compensation for saga: {}", saga.getId());
    }

    /**
     * Determine which step failed based on saga state
     */
    private String determineFailureStep(OrderProcessingSaga saga) {
        if (saga.getPaymentId() == null) {
            return "payment";
        } else if (saga.getInventoryReservationId() == null) {
            return "inventory";
        } else if (saga.getShippingId() == null) {
            return "shipping";
        } else {
            return "unknown";
        }
    }

    /**
     * Find and process stuck sagas
     */
    @Transactional
    public void processStuckSagas() {
        log.info("Processing stuck sagas");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<OrderProcessingSaga.SagaStatus> processingStatuses = List.of(
                OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING,
                OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING,
                OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING
        );

        List<OrderProcessingSaga> stuckSagas = sagaRepository.findStuckSagas(processingStatuses, cutoffTime);
        
        for (OrderProcessingSaga saga : stuckSagas) {
            log.warn("Found stuck saga: {} in status: {}", saga.getId(), saga.getStatus());
            
            if (saga.canRetry()) {
                // Retry the current step
                retryCurrentStep(saga);
            } else {
                // Mark as failed and compensate
                handleSagaFailure(saga, "Saga stuck in processing state");
            }
        }
    }

    /**
     * Retry the current step of a saga
     */
    private void retryCurrentStep(OrderProcessingSaga saga) {
        saga.incrementRetry();
        sagaRepository.save(saga);

        switch (saga.getCurrentStep()) {
            case PAYMENT_PROCESSING -> processPaymentRetry(saga);
            case INVENTORY_PROCESSING -> reserveInventory(saga);
            case SHIPPING_PROCESSING -> prepareShipping(saga);
            default -> log.warn("Unknown step for retry: {}", saga.getCurrentStep());
        }
    }

    /**
     * Handle order status changed event from Order Service
     */
    @Transactional
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Handling order status change for order: {} from {} to {}", 
                event.getOrderId(), event.getPreviousStatus(), event.getNewStatus());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        if (sagaOpt.isEmpty()) {
            log.info("No saga found for order: {}, status change may be external", event.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        // Log status change for audit trail
        log.info("Saga {} - Order status changed: {} -> {} by {} (reason: {})", 
                saga.getId(), event.getPreviousStatus(), event.getNewStatus(), 
                event.getUpdatedBy(), event.getReason());
        
        // Update saga based on new status
        switch (event.getNewStatus().toLowerCase()) {
            case "cancelled":
                handleOrderCancelledFromStatus(saga, event);
                break;
            case "shipped":
                handleOrderShippedFromStatus(saga, event);
                break;
            case "delivered":
                handleOrderDeliveredFromStatus(saga, event);
                break;
            default:
                log.debug("Status change to {} does not require saga update", event.getNewStatus());
        }
    }

    /**
     * Handle order cancelled event from Order Service
     * Triggers compensation/rollback of saga
     */
    @Transactional
    public void handleOrderCancelled(OrderStatusChangedEvent event) {
        log.info("Handling order cancellation for order: {}", event.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        if (sagaOpt.isEmpty()) {
            log.warn("No saga found for cancelled order: {}", event.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        // Check if saga is already compensating or compensated
        if (saga.getStatus() == OrderProcessingSaga.SagaStatus.COMPENSATING ||
            saga.getStatus() == OrderProcessingSaga.SagaStatus.COMPENSATED) {
            log.info("Saga {} is already being compensated", saga.getId());
            return;
        }

        log.info("Initiating compensation for cancelled order: {}", event.getOrderId());
        saga.setStatus(OrderProcessingSaga.SagaStatus.COMPENSATING);
        saga.setErrorMessage("Order cancelled: " + (event.getReason() != null ? event.getReason() : "User requested"));
        sagaRepository.save(saga);

        // Start compensation process
        try {
            compensateSaga(saga);
            metricsService.recordSagaCancelled(event.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to compensate saga {} for cancelled order: {}", saga.getId(), event.getOrderId(), e);
            saga.setStatus(OrderProcessingSaga.SagaStatus.FAILED);
            saga.setErrorMessage("Compensation failed: " + e.getMessage());
            sagaRepository.save(saga);
        }
    }

    /**
     * Handle order shipped event from Order Service
     * Updates saga to reflect shipping has been completed
     */
    @Transactional
    public void handleOrderShipped(OrderStatusChangedEvent event) {
        log.info("Handling order shipped for order: {}", event.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        if (sagaOpt.isEmpty()) {
            log.info("No saga found for shipped order: {}, may have been completed already", event.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        // Update saga to reflect shipping is complete
        if (saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPLETED) {
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.COMPLETED);
            sagaRepository.save(saga);
            
            log.info("Updated saga {} to COMPLETED due to order shipment", saga.getId());
            metricsService.recordSagaCompleted(event.getOrderNumber());
        }
    }

    /**
     * Handle order delivered event from Order Service
     * Marks saga as fully completed and ready for archival
     */
    @Transactional
    public void handleOrderDelivered(OrderStatusChangedEvent event) {
        log.info("Handling order delivered for order: {}", event.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        if (sagaOpt.isEmpty()) {
            log.info("No saga found for delivered order: {}, may have been completed already", event.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        // Mark saga as completed if not already
        if (saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPLETED) {
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.COMPLETED);
            saga.markCompleted();
            sagaRepository.save(saga);
            
            log.info("Marked saga {} as COMPLETED due to order delivery", saga.getId());
            metricsService.recordSagaCompleted(event.getOrderNumber());
        }
        
        // Saga can now be archived or cleaned up
        log.info("Saga {} for delivered order {} is complete and can be archived", 
                saga.getId(), event.getOrderId());
    }

    /**
     * Handle order deleted event from Order Service
     * Cleans up saga record if it exists
     */
    @Transactional
    public void handleOrderDeleted(OrderDeletedEvent event) {
        log.info("Handling order deletion for order: {}", event.getOrderId());

        Optional<OrderProcessingSaga> sagaOpt = sagaRepository.findByOrderId(UUID.fromString(event.getOrderId()));
        if (sagaOpt.isEmpty()) {
            log.info("No saga found for deleted order: {}", event.getOrderId());
            return;
        }

        OrderProcessingSaga saga = sagaOpt.get();
        
        // If saga is in progress, trigger compensation first
        if (saga.getStatus() == OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING ||
            saga.getStatus() == OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING ||
            saga.getStatus() == OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING) {
            
            log.warn("Saga {} is in progress, compensating before deletion", saga.getId());
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPENSATING);
            saga.setErrorMessage("Order deleted: " + (event.getReason() != null ? event.getReason() : "User requested"));
            sagaRepository.save(saga);
            
            try {
                compensateSaga(saga);
            } catch (Exception e) {
                log.error("Failed to compensate saga {} before deletion", saga.getId(), e);
            }
        }
        
        // Archive or delete saga record
        log.info("Deleting saga {} for deleted order {}", saga.getId(), event.getOrderId());
        sagaRepository.delete(saga);
        
        metricsService.recordSagaDeleted(event.getOrderNumber());
    }

    /**
     * Helper method to handle status change to cancelled
     */
    private void handleOrderCancelledFromStatus(OrderProcessingSaga saga, OrderStatusChangedEvent event) {
        if (saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPENSATING &&
            saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPENSATED) {
            
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPENSATING);
            saga.setErrorMessage("Order cancelled via status change: " + event.getReason());
            sagaRepository.save(saga);
            
            try {
                compensateSaga(saga);
            } catch (Exception e) {
                log.error("Failed to compensate saga {} after status change to cancelled", saga.getId(), e);
            }
        }
    }

    /**
     * Helper method to handle status change to shipped
     */
    private void handleOrderShippedFromStatus(OrderProcessingSaga saga, OrderStatusChangedEvent event) {
        if (saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPLETED) {
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.COMPLETED);
            sagaRepository.save(saga);
        }
    }

    /**
     * Helper method to handle status change to delivered
     */
    private void handleOrderDeliveredFromStatus(OrderProcessingSaga saga, OrderStatusChangedEvent event) {
        if (saga.getStatus() != OrderProcessingSaga.SagaStatus.COMPLETED) {
            saga.setStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            saga.setCurrentStep(OrderProcessingSaga.ProcessingStep.COMPLETED);
            saga.markCompleted();
            sagaRepository.save(saga);
        }
    }
}
