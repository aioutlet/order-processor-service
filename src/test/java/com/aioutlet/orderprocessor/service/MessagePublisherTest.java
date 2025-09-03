package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.model.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private TopicExchange orderExchange;

    @InjectMocks
    private MessagePublisher messagePublisher;

    private UUID orderId;
    private String customerId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = "customer-123";
        
        // Set up the exchange name
        when(orderExchange.getName()).thenReturn("order.exchange");
        
        // Note: Routing keys are now hardcoded in the MessagePublisher methods
        // No need to set up routing keys via reflection
    }

    @Test
    void publishPaymentProcessing_ShouldSendMessageSuccessfully() {
        // Arrange
        PaymentProcessingEvent event = new PaymentProcessingEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setAmount(new BigDecimal("99.99"));

        // Act
        messagePublisher.publishPaymentProcessing(event);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("payment.processing"), eq(event));
    }

    @Test
    void publishPaymentProcessing_WhenRabbitTemplateFails_ShouldThrowException() {
        // Arrange
        PaymentProcessingEvent event = new PaymentProcessingEvent();
        event.setOrderId(orderId);
        event.setCustomerId(customerId);
        event.setAmount(new BigDecimal("99.99"));

        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishPaymentProcessing(event));
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("payment.processing"), eq(event));
    }

    @Test
    void publishInventoryReservation_ShouldSendMessageSuccessfully() {
        // Arrange
        InventoryReservationEvent event = new InventoryReservationEvent();
        event.setOrderId(orderId);
        event.setItems(new ArrayList<>());
        event.setRequestedAt(LocalDateTime.now());

        // Act
        messagePublisher.publishInventoryReservation(event);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("inventory.reservation"), eq(event));
    }

    @Test
    void publishInventoryReservation_WhenRabbitTemplateFails_ShouldThrowException() {
        // Arrange
        InventoryReservationEvent event = new InventoryReservationEvent();
        event.setOrderId(orderId);
        event.setItems(new ArrayList<>());
        event.setRequestedAt(LocalDateTime.now());

        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishInventoryReservation(event));
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("inventory.reservation"), eq(event));
    }

    @Test
    void publishShippingPreparation_ShouldSendMessageSuccessfully() {
        // Act
        messagePublisher.publishShippingPreparation(orderId, customerId);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("shipping.preparation"), any(ShippingPreparationEvent.class));
    }

    @Test
    void publishShippingPreparation_WhenRabbitTemplateFails_ShouldThrowException() {
        // Arrange
        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishShippingPreparation(orderId, customerId));
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("shipping.preparation"), any(ShippingPreparationEvent.class));
    }

    @Test
    void publishOrderCompleted_ShouldSendMessageSuccessfully() {
        // Act
        messagePublisher.publishOrderCompleted(orderId);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("order.completed"), any(OrderCompletedEvent.class));
    }

    @Test
    void publishOrderCompleted_WhenRabbitTemplateFails_ShouldThrowException() {
        // Arrange
        doThrow(new RuntimeException("RabbitMQ connection error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> messagePublisher.publishOrderCompleted(orderId));
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("order.completed"), any(OrderCompletedEvent.class));
    }

    @Test
    void publishShippingCancellation_ShouldSendMessageSuccessfully() {
        // Arrange
        String shippingId = "SHIP-789";

        // Act
        messagePublisher.publishShippingCancellation(orderId, shippingId);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq("order.exchange"), eq("shipping.cancelled"), any(Object.class));
    }
}
