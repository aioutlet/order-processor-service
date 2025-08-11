package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for collecting and exposing metrics about saga processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaMetricsService {

    private final OrderProcessingSagaRepository sagaRepository;
    private final MeterRegistry meterRegistry;
    
    private final AtomicLong activeSagasGauge = new AtomicLong(0);
    private final AtomicLong completedSagasGauge = new AtomicLong(0);
    private final AtomicLong failedSagasGauge = new AtomicLong(0);
    private final AtomicLong stuckSagasGauge = new AtomicLong(0);

    public SagaMetricsService(OrderProcessingSagaRepository sagaRepository, MeterRegistry meterRegistry) {
        this.sagaRepository = sagaRepository;
        this.meterRegistry = meterRegistry;
        
        // Register gauges
        Gauge.builder("saga.active.count")
            .description("Number of active sagas")
            .register(meterRegistry, activeSagasGauge, AtomicLong::get);
            
        Gauge.builder("saga.completed.count")
            .description("Number of completed sagas")
            .register(meterRegistry, completedSagasGauge, AtomicLong::get);
            
        Gauge.builder("saga.failed.count")
            .description("Number of failed sagas")
            .register(meterRegistry, failedSagasGauge, AtomicLong::get);
            
        Gauge.builder("saga.stuck.count")
            .description("Number of stuck sagas")
            .register(meterRegistry, stuckSagasGauge, AtomicLong::get);
    }

    /**
     * Record saga started event
     */
    public void recordSagaStarted(String orderNumber) {
        Counter.builder("saga.started.total")
            .description("Total number of sagas started")
            .tag("order_number", orderNumber)
            .register(meterRegistry)
            .increment();
        
        updateGauges();
    }

    /**
     * Record saga completed event
     */
    public void recordSagaCompleted(String orderNumber, Duration processingTime) {
        Counter.builder("saga.completed.total")
            .description("Total number of sagas completed")
            .tag("order_number", orderNumber)
            .register(meterRegistry)
            .increment();
            
        Timer.builder("saga.processing.duration")
            .description("Time taken to process saga")
            .tag("outcome", "completed")
            .register(meterRegistry)
            .record(processingTime);
        
        updateGauges();
    }

    /**
     * Record saga failed event
     */
    public void recordSagaFailed(String orderNumber, String failureReason, Duration processingTime) {
        Counter.builder("saga.failed.total")
            .description("Total number of sagas failed")
            .tag("order_number", orderNumber)
            .tag("failure_reason", failureReason)
            .register(meterRegistry)
            .increment();
            
        Timer.builder("saga.processing.duration")
            .description("Time taken to process saga")
            .tag("outcome", "failed")
            .register(meterRegistry)
            .record(processingTime);
        
        updateGauges();
    }

    /**
     * Record payment processing event
     */
    public void recordPaymentProcessing(String orderNumber) {
        Counter.builder("saga.payment.processing.total")
            .description("Total number of payment processing events")
            .tag("order_number", orderNumber)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record inventory processing event
     */
    public void recordInventoryProcessing(String orderNumber) {
        Counter.builder("saga.inventory.processing.total")
            .description("Total number of inventory processing events")
            .tag("order_number", orderNumber)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record shipping processing event
     */
    public void recordShippingProcessing(String orderNumber) {
        Counter.builder("saga.shipping.processing.total")
            .description("Total number of shipping processing events")
            .tag("order_number", orderNumber)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record retry event
     */
    public void recordRetry(String orderNumber, String step, int retryCount) {
        Counter.builder("saga.retry.total")
            .description("Total number of saga retries")
            .tag("order_number", orderNumber)
            .tag("step", step)
            .tag("retry_count", String.valueOf(retryCount))
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record compensation event
     */
    public void recordCompensation(String orderNumber, String step) {
        Counter.builder("saga.compensation.total")
            .description("Total number of compensation actions")
            .tag("order_number", orderNumber)
            .tag("step", step)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Update gauge metrics
     */
    public void updateGauges() {
        try {
            List<OrderProcessingSaga.SagaStatus> activeStatuses = List.of(
                OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING,
                OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING,
                OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING
            );
            
            long activeSagas = sagaRepository.countByStatusIn(activeStatuses);
            long completedSagas = sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            long failedSagas = sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.FAILED);
            
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
            long stuckSagas = sagaRepository.countStuckSagas(activeStatuses, cutoffTime);
            
            activeSagasGauge.set(activeSagas);
            completedSagasGauge.set(completedSagas);
            failedSagasGauge.set(failedSagas);
            stuckSagasGauge.set(stuckSagas);
            
        } catch (Exception e) {
            log.error("Failed to update saga metrics", e);
        }
    }
}
