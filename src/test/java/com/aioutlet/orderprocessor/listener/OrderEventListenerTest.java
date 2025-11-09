package com.aioutlet.orderprocessor.listener;

import com.aioutlet.orderprocessor.model.MessageWrapper;
import com.aioutlet.orderprocessor.model.events.*;
import com.aioutlet.orderprocessor.service.SagaOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private SagaOrchestratorService sagaOrchestratorService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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
        // Register Java 8 time module for LocalDateTime serialization
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private Message createMessage(Object event, String routingKey) throws Exception {
        byte[] body;
        if (routingKey.equals("order.created")) {
            // OrderCreatedEvent needs to be wrapped in MessageWrapper
            MessageWrapper wrapper = new MessageWrapper();
            wrapper.setId(UUID.randomUUID().toString());
            wrapper.setTopic(routingKey);
            wrapper.setData(objectMapper.convertValue(event, Map.class));
            wrapper.setTimestamp(java.time.Instant.now());
            wrapper.setCorrelationId(((OrderCreatedEvent)event).getCorrelationId());
            body = objectMapper.writeValueAsBytes(wrapper);
        } else {
            body = objectMapper.writeValueAsBytes(event);
        }
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return new Message(body, props);
    }

    @Test
    void handleOrderCreatedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        event.setCorrelationId(correlationId);

        Message message = createMessage(event, "order.created");
        Map<String, Object> headers = new HashMap<>();

        // Act
        orderEventListener.handleMessage(message, headers);

        // Assert
        verify(sagaOrchestratorService).startOrderProcessingSaga(any(OrderCreatedEvent.class));
    }

    @Test
    void handleOrderCreatedEvent_WithHeaderCorrelationId_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        // No correlation ID in event

        Message message = createMessage(event, "order.created");
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Correlation-ID", correlationId);

        // Act
        orderEventListener.handleMessage(message, headers);

        // Assert
        verify(sagaOrchestratorService).startOrderProcessingSaga(any(OrderCreatedEvent.class));
    }

    @Test
    void handleOrderCreatedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId.toString());
        event.setOrderNumber("ORD-001");
        event.setTotalAmount(new BigDecimal("99.99"));
        event.setCorrelationId(correlationId);

        Message message = createMessage(event, "order.created");
        Map<String, Object> headers = new HashMap<>();
        doThrow(new RuntimeException("Processing error")).when(sagaOrchestratorService).startOrderProcessingSaga(any(OrderCreatedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, headers);
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).startOrderProcessingSaga(any(OrderCreatedEvent.class));
    }

    @Test
    void handlePaymentProcessedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setOrderId(orderId);
        event.setPaymentId("PAY-123");
        event.setAmount(new BigDecimal("99.99"));

        Message message = createMessage(event, "payment.processed");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentProcessed(any(PaymentProcessedEvent.class));
    }

    @Test
    void handlePaymentProcessedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setOrderId(orderId);
        event.setPaymentId("PAY-123");
        event.setAmount(new BigDecimal("99.99"));

        Message message = createMessage(event, "payment.processed");
        doThrow(new RuntimeException("Payment processing error")).when(sagaOrchestratorService).handlePaymentProcessed(any(PaymentProcessedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handlePaymentProcessed(any(PaymentProcessedEvent.class));
    }

    @Test
    void handlePaymentFailedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Insufficient funds");
        event.setErrorCode("INSUFFICIENT_FUNDS");

        Message message = createMessage(event, "payment.failed");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handlePaymentFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void handlePaymentFailedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        PaymentFailedEvent event = new PaymentFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Insufficient funds");
        event.setErrorCode("INSUFFICIENT_FUNDS");

        Message message = createMessage(event, "payment.failed");
        doThrow(new RuntimeException("Payment failure processing error")).when(sagaOrchestratorService).handlePaymentFailed(any(PaymentFailedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handlePaymentFailed(any(PaymentFailedEvent.class));
    }

    @Test
    void handleInventoryReservedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setOrderId(orderId);
        event.setReservationId("RES-456");

        Message message = createMessage(event, "inventory.reserved");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    void handleInventoryReservedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setOrderId(orderId);
        event.setReservationId("RES-456");

        Message message = createMessage(event, "inventory.reserved");
        doThrow(new RuntimeException("Inventory processing error")).when(sagaOrchestratorService).handleInventoryReserved(any(InventoryReservedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handleInventoryReserved(any(InventoryReservedEvent.class));
    }

    @Test
    void handleInventoryFailedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        InventoryFailedEvent event = new InventoryFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Out of stock");
        event.setErrorCode("OUT_OF_STOCK");

        Message message = createMessage(event, "inventory.failed");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleInventoryFailed(any(InventoryFailedEvent.class));
    }

    @Test
    void handleInventoryFailedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        InventoryFailedEvent event = new InventoryFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Out of stock");
        event.setErrorCode("OUT_OF_STOCK");

        Message message = createMessage(event, "inventory.failed");
        doThrow(new RuntimeException("Inventory processing error")).when(sagaOrchestratorService).handleInventoryFailed(any(InventoryFailedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handleInventoryFailed(any(InventoryFailedEvent.class));
    }

    @Test
    void handleShippingPreparedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        ShippingPreparedEvent event = new ShippingPreparedEvent();
        event.setOrderId(orderId);
        event.setShippingId("SHIP-789");
        event.setTrackingNumber("TRK-123456");

        Message message = createMessage(event, "shipping.prepared");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingPrepared(any(ShippingPreparedEvent.class));
    }

    @Test
    void handleShippingPreparedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        ShippingPreparedEvent event = new ShippingPreparedEvent();
        event.setOrderId(orderId);
        event.setShippingId("SHIP-789");
        event.setTrackingNumber("TRK-123456");

        Message message = createMessage(event, "shipping.prepared");
        doThrow(new RuntimeException("Shipping processing error")).when(sagaOrchestratorService).handleShippingPrepared(any(ShippingPreparedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handleShippingPrepared(any(ShippingPreparedEvent.class));
    }

    @Test
    void handleShippingFailedEvent_ShouldCallSagaOrchestrator() throws Exception {
        // Arrange
        ShippingFailedEvent event = new ShippingFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Address not found");
        event.setErrorCode("ADDRESS_NOT_FOUND");

        Message message = createMessage(event, "shipping.failed");

        // Act
        orderEventListener.handleMessage(message, new java.util.HashMap<>());

        // Assert
        verify(sagaOrchestratorService).handleShippingFailed(any(ShippingFailedEvent.class));
    }

    @Test
    void handleShippingFailedEvent_WithException_ShouldLogError() throws Exception {
        // Arrange
        ShippingFailedEvent event = new ShippingFailedEvent();
        event.setOrderId(orderId);
        event.setReason("Address not found");
        event.setErrorCode("ADDRESS_NOT_FOUND");

        Message message = createMessage(event, "shipping.failed");
        doThrow(new RuntimeException("Shipping processing error")).when(sagaOrchestratorService).handleShippingFailed(any(ShippingFailedEvent.class));

        // Act & Assert
        try {
            orderEventListener.handleMessage(message, new java.util.HashMap<>());
        } catch (RuntimeException e) {
            // Expected
        }
        verify(sagaOrchestratorService).handleShippingFailed(any(ShippingFailedEvent.class));
    }
}
