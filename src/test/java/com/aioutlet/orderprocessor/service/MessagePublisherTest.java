package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePublisherTest {

    @Mock
    private MessageBrokerService messageBrokerService;

    @InjectMocks
    private MessagePublisher messagePublisher;

    private UUID orderId;
    private String customerId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = "customer-123";
    }

    @Test
    void publishPaymentProcessing_ShouldSendMessageSuccessfully() throws Exception {
        // Arrange
        PaymentProcessingEvent event = new PaymentProcessingEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setAmount(new BigDecimal("99.99"));

        // Act
        messagePublisher.publishPaymentProcessing(event);

        // Assert
        verify(messageBrokerService).publishPaymentProcessing(eq(event));
    }

    @Test
    void publishPaymentProcessing_WhenBrokerFails_ShouldThrowException() throws Exception {
        // Arrange
        PaymentProcessingEvent event = new PaymentProcessingEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setAmount(new BigDecimal("99.99"));

        doThrow(new RuntimeException("Message broker connection error"))
                .when(messageBrokerService).publishPaymentProcessing(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishPaymentProcessing(event));
        verify(messageBrokerService).publishPaymentProcessing(eq(event));
    }

    @Test
    void publishInventoryReservation_ShouldSendMessageSuccessfully() throws Exception {
        // Arrange
        InventoryReservationEvent event = new InventoryReservationEvent();
        event.setOrderId(orderId);
        event.setItems(new ArrayList<>());
        event.setRequestedAt(LocalDateTime.now());

        // Act
        messagePublisher.publishInventoryReservation(event);

        // Assert
        verify(messageBrokerService).publishInventoryReservation(eq(event));
    }

    @Test
    void publishInventoryReservation_WhenBrokerFails_ShouldThrowException() throws Exception {
        // Arrange
        InventoryReservationEvent event = new InventoryReservationEvent();
        event.setOrderId(orderId);
        event.setItems(new ArrayList<>());
        event.setRequestedAt(LocalDateTime.now());

        doThrow(new RuntimeException("Message broker connection error"))
                .when(messageBrokerService).publishInventoryReservation(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishInventoryReservation(event));
        verify(messageBrokerService).publishInventoryReservation(eq(event));
    }

    @Test
    void publishShippingPreparation_ShouldSendMessageSuccessfully() throws Exception {
        // Act
        messagePublisher.publishShippingPreparation(orderId, customerId);

        // Assert
        verify(messageBrokerService).publishShippingPreparation(any(ShippingPreparationEvent.class));
    }

    @Test
    void publishShippingPreparation_WhenBrokerFails_ShouldThrowException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Message broker connection error"))
                .when(messageBrokerService).publishShippingPreparation(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishShippingPreparation(orderId, customerId));
        verify(messageBrokerService).publishShippingPreparation(any(ShippingPreparationEvent.class));
    }

    @Test
    void publishOrderStatusChanged_ShouldSendMessageSuccessfully() throws Exception {
        // Arrange
        String orderNumber = "ORD-001";
        String customerId = "CUST-123";
        String previousStatus = "PROCESSING";
        String newStatus = "COMPLETED";
        String reason = "Order completed successfully";
        String correlationId = UUID.randomUUID().toString();
        
        // Act
        messagePublisher.publishOrderStatusChanged(orderId, orderNumber, customerId, previousStatus, newStatus, reason, correlationId);

        // Assert
        verify(messageBrokerService).publishOrderStatusChanged(any(OrderStatusChangedEvent.class));
    }

    @Test
    void publishOrderStatusChanged_WhenBrokerFails_ShouldThrowException() throws Exception {
        // Arrange
        String orderNumber = "ORD-001";
        String customerId = "CUST-123";
        String previousStatus = "PROCESSING";
        String newStatus = "COMPLETED";
        String reason = "Order completed successfully";
        String correlationId = UUID.randomUUID().toString();
        doThrow(new RuntimeException("Message broker connection error"))
                .when(messageBrokerService).publishOrderStatusChanged(any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishOrderStatusChanged(orderId, orderNumber, customerId, previousStatus, newStatus, reason, correlationId));
        verify(messageBrokerService).publishOrderStatusChanged(any(OrderStatusChangedEvent.class));
    }

    @Test
    void publishShippingCancellation_ShouldSendMessageSuccessfully() throws Exception {
        // Arrange
        String shippingId = "SHIP-789";

        // Act
        messagePublisher.publishShippingCancellation(orderId, shippingId);

        // Assert
        verify(messageBrokerService).publishShippingCancellation(eq(orderId), eq(shippingId));
    }
}
