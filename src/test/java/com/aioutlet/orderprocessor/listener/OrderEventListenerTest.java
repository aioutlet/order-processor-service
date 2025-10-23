package com.aioutlet.orderprocessor.listener;

import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private SagaOrchestratorService sagaOrchestratorService;

    @InjectMocks
    private OrderEventListener orderEventListener;

    private UUID orderId;
    private UUID customerId;
    private String correlationId;
    
    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        correlationId = "test-correlation-123";
    }

    @Test
    void handleOrderCreatedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        event.setCorrelationId(correlationId);

        Map<String, Object> headers = new HashMap<>();

        // Act
        orderEventListener.handleOrderCreatedEvent(event, headers);

        // Assert
        verify(sagaOrchestratorService).startOrderProcessingSaga(event);
    }

    @Test
    void handleOrderCreatedEvent_WithHeaderCorrelationId_ShouldCallSagaOrchestrator() {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        // No correlation ID in event

        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Correlation-ID", correlationId);

        // Act
        orderEventListener.handleOrderCreatedEvent(event, headers);

        // Assert
        verify(sagaOrchestratorService).startOrderProcessingSaga(event);
    }

    @Test
    void handleOrderCreatedEvent_WithException_ShouldLogError() {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        event.setCorrelationId(correlationId);

        Map<String, Object> headers = new HashMap<>();
        doThrow(new RuntimeException("Processing error")).when(sagaOrchestratorService).startOrderProcessingSaga(event);

        // Act
        orderEventListener.handleOrderCreatedEvent(event, headers);

        // Assert
        verify(sagaOrchestratorService).startOrderProcessingSaga(event);
    }

    @Test
    void handlePaymentProcessedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setOrderId(orderId);
        event.setPaymentId("PAY-123");
        event.setAmount(new BigDecimal("99.99"));

        // Act
        orderEventListener.handlePaymentProcessedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentProcessed(event);
    }

    @Test
    void handlePaymentProcessedEvent_WithException_ShouldLogError() {
        // Arrange
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setOrderId(orderId);
        event.setPaymentId("PAY-123");
        event.setAmount(new BigDecimal("99.99"));

        doThrow(new RuntimeException("Payment processing error")).when(sagaOrchestratorService).handlePaymentProcessed(event);

        // Act
        orderEventListener.handlePaymentProcessedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentProcessed(event);
    }

    @Test
    void handlePaymentFailedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Insufficient funds");
        event.setErrorCode("INSUFFICIENT_FUNDS");

        // Act
        orderEventListener.handlePaymentFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentFailed(event);
    }

    @Test
    void handlePaymentFailedEvent_WithException_ShouldLogError() {
        // Arrange
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Insufficient funds");
        event.setErrorCode("INSUFFICIENT_FUNDS");

        doThrow(new RuntimeException("Payment failure processing error")).when(sagaOrchestratorService).handlePaymentFailed(event);

        // Act
        orderEventListener.handlePaymentFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentFailed(event);
    }

    @Test
    void handleInventoryReservedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setOrderId(orderId);
        event.setReservationId("RES-456");

        // Act
        orderEventListener.handleInventoryReservedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryReserved(event);
    }

    @Test
    void handleInventoryReservedEvent_WithException_ShouldLogError() {
        // Arrange
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setOrderId(orderId);
        event.setReservationId("RES-456");

        doThrow(new RuntimeException("Inventory processing error")).when(sagaOrchestratorService).handleInventoryReserved(event);

        // Act
        orderEventListener.handleInventoryReservedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryReserved(event);
    }

    @Test
    void handleInventoryFailedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        InventoryFailedEvent event = new InventoryFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Out of stock");
        event.setErrorCode("OUT_OF_STOCK");

        // Act
        orderEventListener.handleInventoryFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryFailed(event);
    }

    @Test
    void handleInventoryFailedEvent_WithException_ShouldLogError() {
        // Arrange
        InventoryFailedEvent event = new InventoryFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Out of stock");
        event.setErrorCode("OUT_OF_STOCK");

        doThrow(new RuntimeException("Inventory processing error")).when(sagaOrchestratorService).handleInventoryFailed(event);

        // Act
        orderEventListener.handleInventoryFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryFailed(event);
    }

    @Test
    void handleShippingPreparedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        ShippingPreparedEvent event = new ShippingPreparedEvent();
        event.setOrderId(orderId);
        event.setShippingId("SHIP-789");
        event.setTrackingNumber("TRK-123456");

        // Act
        orderEventListener.handleShippingPreparedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingPrepared(event);
    }

    @Test
    void handleShippingPreparedEvent_WithException_ShouldLogError() {
        // Arrange
        ShippingPreparedEvent event = new ShippingPreparedEvent();
        event.setOrderId(orderId);
        event.setShippingId("SHIP-789");
        event.setTrackingNumber("TRK-123456");

        doThrow(new RuntimeException("Shipping processing error")).when(sagaOrchestratorService).handleShippingPrepared(event);

        // Act
        orderEventListener.handleShippingPreparedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingPrepared(event);
    }

    @Test
    void handleShippingFailedEvent_ShouldCallSagaOrchestrator() {
        // Arrange
        ShippingFailedEvent event = new ShippingFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Address not found");
        event.setErrorCode("ADDRESS_NOT_FOUND");

        // Act
        orderEventListener.handleShippingFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingFailed(event);
    }

    @Test
    void handleShippingFailedEvent_WithException_ShouldLogError() {
        // Arrange
        ShippingFailedEvent event = new ShippingFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Address not found");
        event.setErrorCode("ADDRESS_NOT_FOUND");

        doThrow(new RuntimeException("Shipping processing error")).when(sagaOrchestratorService).handleShippingFailed(event);

        // Act
        orderEventListener.handleShippingFailedEvent(event, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingFailed(event);
    }
}
