package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom health check service for order processor
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderProcessorHealthIndicator {

    private final OrderProcessingSagaRepository sagaRepository;

    /**
     * Get health status of the order processor
     */
    public Map<String, Object> getHealthStatus() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Check saga statistics
            long totalSagas = sagaRepository.count();
            long activeSagas = sagaRepository.countByStatusIn(List.of(
                OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING,
                OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING,
                OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING
            ));
            long failedSagas = sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.FAILED);
            long completedSagas = sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.COMPLETED);
            
            details.put("totalSagas", totalSagas);
            details.put("activeSagas", activeSagas);
            details.put("failedSagas", failedSagas);
            details.put("completedSagas", completedSagas);
            
            // Check for stuck sagas
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
            List<OrderProcessingSaga.SagaStatus> processingStatuses = List.of(
                OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING,
                OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING,
                OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING
            );
            
            long stuckSagas = sagaRepository.countStuckSagas(processingStatuses, cutoffTime);
            details.put("stuckSagas", stuckSagas);
            
            // Determine health status
            if (stuckSagas > 10) {
                details.put("status", "DOWN");
                details.put("reason", "Too many stuck sagas");
            } else if (stuckSagas > 5) {
                details.put("status", "DEGRADED");
                details.put("reason", "Some sagas are stuck");
            } else {
                details.put("status", "UP");
                details.put("reason", "All systems operational");
            }
            
            return details;
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("status", "DOWN");
            errorDetails.put("reason", "Health check failed: " + e.getMessage());
            return errorDetails;
        }
    }
}
