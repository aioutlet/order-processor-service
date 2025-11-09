package com.aioutlet.orderprocessor.events.consumer;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aioutlet.orderprocessor.model.events.ShippingPreparedEvent;
import com.aioutlet.orderprocessor.model.events.ShippingFailedEvent;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;

/**
 * Shipping Event Consumer
 * Handles shipping-related events from Dapr pub/sub
 */
@RestController
@RequestMapping("/dapr/events")
@RequiredArgsConstructor
@Slf4j
public class ShippingEventConsumer {

    private final SagaOrchestratorService sagaOrchestratorService;

    /**
     * Handle shipping.prepared event
     */
    @Topic(name = "shipping.prepared", pubsubName = "order-processor-pubsub")
    @PostMapping("/shipping-prepared")
    public ResponseEntity<Void> handleShippingPrepared(@RequestBody CloudEvent<ShippingPreparedEvent> cloudEvent) {
        try {
            log.info("Received shipping.prepared event: {}", cloudEvent.getId());
            ShippingPreparedEvent event = cloudEvent.getData();
            sagaOrchestratorService.handleShippingPrepared(event);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error handling shipping.prepared event", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Handle shipping.failed event
     */
    @Topic(name = "shipping.failed", pubsubName = "order-processor-pubsub")
    @PostMapping("/shipping-failed")
    public ResponseEntity<Void> handleShippingFailed(@RequestBody CloudEvent<ShippingFailedEvent> cloudEvent) {
        try {
            log.info("Received shipping.failed event: {}", cloudEvent.getId());
            ShippingFailedEvent event = cloudEvent.getData();
            sagaOrchestratorService.handleShippingFailed(event);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error handling shipping.failed event", e);
            return ResponseEntity.status(500).build();
        }
    }
}
