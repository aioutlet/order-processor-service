package com.aioutlet.orderprocessor.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Order Processing Saga Entity
 * Tracks the state of order processing across multiple services
 */
@Entity
@Table(name = "order_processing_saga")
@Data
@NoArgsConstructor
public class OrderProcessingSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status = SagaStatus.STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false)
    private ProcessingStep currentStep = ProcessingStep.PAYMENT_PROCESSING;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "inventory_reservation_id")
    private String inventoryReservationId;

    @Column(name = "shipping_id")
    private String shippingId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum SagaStatus {
        STARTED,
        PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        INVENTORY_PROCESSING,
        INVENTORY_COMPLETED,
        SHIPPING_PROCESSING,
        COMPLETED,
        FAILED,
        COMPENSATING,
        COMPENSATED
    }

    public enum ProcessingStep {
        PAYMENT_PROCESSING,
        INVENTORY_PROCESSING,
        SHIPPING_PROCESSING,
        COMPLETED
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == SagaStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == SagaStatus.FAILED || status == SagaStatus.COMPENSATED;
    }

    public boolean canRetry() {
        return retryCount < 3 && (status == SagaStatus.FAILED || isProcessingStep());
    }

    private boolean isProcessingStep() {
        return status == SagaStatus.PAYMENT_PROCESSING ||
               status == SagaStatus.INVENTORY_PROCESSING ||
               status == SagaStatus.SHIPPING_PROCESSING;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public void markCompleted() {
        this.status = SagaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.currentStep = ProcessingStep.COMPLETED;
    }

    public void markFailed(String errorMessage) {
        this.status = SagaStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
