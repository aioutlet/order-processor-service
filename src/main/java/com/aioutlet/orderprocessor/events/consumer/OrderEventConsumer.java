package com.aioutlet.orderprocessor.events.consumer;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aioutlet.orderprocessor.model.events.OrderCreatedEvent;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;

/**
 * Order Event Consumer
 * Handles order-related events from Dapr pub/sub
 */
@RestController
@RequestMapping("/dapr/events")
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final SagaOrchestratorService sagaOrchestratorService;

    /**
     * Handle order.created event
     */
    @Topic(name = "${topics.order-created}", pubsubName = "${dapr.pubsub-name}")
    @PostMapping("/order-created")
    public ResponseEntity<Void> handleOrderCreated(@RequestBody CloudEvent<OrderCreatedEvent> cloudEvent) {
        try {
            log.info("Received order.created event: {}", cloudEvent.getId());
            OrderCreatedEvent event = cloudEvent.getData();
            sagaOrchestratorService.startOrderProcessingSaga(event);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error handling order.created event", e);
            return ResponseEntity.status(500).build();
        }
    }
}
