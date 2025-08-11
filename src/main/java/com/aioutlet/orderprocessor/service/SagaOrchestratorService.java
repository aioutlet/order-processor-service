package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
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
    private final ExternalServiceClient externalServiceClient;
    private final SagaMetricsService metricsService;

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
     */
    private void reserveInventory(OrderProcessingSaga saga) {
        log.info("Reserving inventory for saga: {}", saga.getId());

        // Fetch order items and create inventory reservation event
        List<InventoryReservationEvent.InventoryItem> items = 
                externalServiceClient.getOrderItems(saga.getOrderId());

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
}
