package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom health indicator for order processor service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderProcessorHealthIndicator implements HealthIndicator {

    private final OrderProcessingSagaRepository sagaRepository;

    @Override
    public Health health() {
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
                return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "Too many stuck sagas")
                    .build();
            } else if (stuckSagas > 5) {
                return Health.status("DEGRADED")
                    .withDetails(details)
                    .withDetail("reason", "Some sagas are stuck")
                    .build();
            } else {
                return Health.up()
                    .withDetails(details)
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
