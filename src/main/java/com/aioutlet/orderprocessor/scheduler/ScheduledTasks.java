package com.aioutlet.orderprocessor.scheduler;

import com.aioutlet.orderprocessor.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for the Order Processor Service
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final SagaOrchestratorService sagaOrchestratorService;

    /**
     * Check for and process stuck sagas every 15 minutes
     */
    @Scheduled(fixedRateString = "${saga.scheduler.stuck-sagas-rate:900000}")
    public void processStuckSagas() {
        log.info("Starting scheduled task: processStuckSagas");
        
        try {
            sagaOrchestratorService.processStuckSagas();
        } catch (Exception e) {
            log.error("Error processing stuck sagas: {}", e.getMessage(), e);
        }
    }

    /**
     * Process failed sagas that can be retried every 5 minutes
     */
    @Scheduled(fixedRateString = "${saga.scheduler.retry-sagas-rate:300000}")
    public void retryFailedSagas() {
        log.info("Starting scheduled task: retryFailedSagas");
        
        // Implementation omitted for brevity
        // Would call a service method to find and retry eligible failed sagas
    }
}
