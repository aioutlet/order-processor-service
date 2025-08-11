package com.aioutlet.orderprocessor.controller;

import com.aioutlet.orderprocessor.model.entity.OrderProcessingSaga;
import com.aioutlet.orderprocessor.repository.OrderProcessingSagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API endpoints for the Order Processor Service
 */
@RestController
@RequestMapping("/api/v1/admin/sagas")
@RequiredArgsConstructor
public class AdminController {

    private final OrderProcessingSagaRepository sagaRepository;

    /**
     * Get all sagas with pagination
     */
    @GetMapping
    public ResponseEntity<Page<OrderProcessingSaga>> getAllSagas(Pageable pageable) {
        return ResponseEntity.ok(sagaRepository.findAll(pageable));
    }

    /**
     * Get saga by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderProcessingSaga> getSagaById(@PathVariable UUID id) {
        return sagaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get saga by order ID
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<OrderProcessingSaga> getSagaByOrderId(@PathVariable UUID orderId) {
        return sagaRepository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get saga counts by status
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getSagaStats() {
        Map<String, Long> stats = Map.of(
            "STARTED", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.STARTED),
            "PAYMENT_PROCESSING", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.PAYMENT_PROCESSING),
            "INVENTORY_PROCESSING", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.INVENTORY_PROCESSING),
            "SHIPPING_PROCESSING", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.SHIPPING_PROCESSING),
            "COMPLETED", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.COMPLETED),
            "FAILED", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.FAILED),
            "COMPENSATING", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.COMPENSATING),
            "COMPENSATED", sagaRepository.countByStatus(OrderProcessingSaga.SagaStatus.COMPENSATED)
        );
        
        return ResponseEntity.ok(stats);
    }
}
