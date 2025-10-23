package com.aioutlet.orderprocessor.service;

import com.aioutlet.orderprocessor.adapter.MessageBrokerAdapter;
import com.aioutlet.orderprocessor.adapter.MessageBrokerAdapterFactory;
import com.aioutlet.orderprocessor.config.MessageBrokerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Message Broker Service
 * Provides a high-level interface for publishing messages to the configured message broker
 * Uses the adapter pattern to support multiple broker implementations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBrokerService {

    private final MessageBrokerAdapterFactory adapterFactory;
    private final MessageBrokerProperties messageBrokerProperties;

    /**
     * Publish a message to the default exchange
     * 
     * @param routingKey Routing key for the message
     * @param message Message payload
     * @throws Exception if publishing fails
     */
    public void publish(String routingKey, Object message) throws Exception {
        publish(messageBrokerProperties.getExchange().getName(), routingKey, message, null);
    }

    /**
     * Publish a message with custom headers
     * 
     * @param routingKey Routing key for the message
     * @param message Message payload
     * @param headers Custom message headers
     * @throws Exception if publishing fails
     */
    public void publish(String routingKey, Object message, Map<String, Object> headers) throws Exception {
        publish(messageBrokerProperties.getExchange().getName(), routingKey, message, headers);
    }

    /**
     * Publish a message to a specific exchange
     * 
     * @param exchange Exchange/Topic name
     * @param routingKey Routing key for the message
     * @param message Message payload
     * @param headers Custom message headers
     * @throws Exception if publishing fails
     */
    public void publish(String exchange, String routingKey, Object message, Map<String, Object> headers) throws Exception {
        log.debug("Publishing message - Exchange: {}, RoutingKey: {}, Provider: {}", 
                 exchange, routingKey, adapterFactory.getProviderName());

        try {
            MessageBrokerAdapter adapter = adapterFactory.getAdapter();
            adapter.publish(exchange, routingKey, message, headers);
            
            log.info("Message published successfully - Exchange: {}, RoutingKey: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to publish message - Exchange: {}, RoutingKey: {}", exchange, routingKey, e);
            throw e;
        }
    }

    /**
     * Publish a message with correlation ID
     * 
     * @param routingKey Routing key for the message
     * @param message Message payload
     * @param correlationId Correlation ID for tracing
     * @throws Exception if publishing fails
     */
    public void publishWithCorrelationId(String routingKey, Object message, String correlationId) throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Correlation-Id", correlationId);
        publish(routingKey, message, headers);
    }

    /**
     * Publish order created event
     */
    public void publishOrderCreated(Object event) throws Exception {
        publish(messageBrokerProperties.getRoutingKey().getOrderCreated(), event);
    }

    /**
     * Publish payment processing event
     */
    public void publishPaymentProcessing(Object event) throws Exception {
        publish("payment.processing", event);
    }

    /**
     * Publish payment processed event
     */
    public void publishPaymentProcessed(Object event) throws Exception {
        publish(messageBrokerProperties.getRoutingKey().getPaymentProcessed(), event);
    }

    /**
     * Publish inventory reservation event
     */
    public void publishInventoryReservation(Object event) throws Exception {
        publish("inventory.reservation", event);
    }

    /**
     * Publish inventory reserved event
     */
    public void publishInventoryReserved(Object event) throws Exception {
        publish(messageBrokerProperties.getRoutingKey().getInventoryReserved(), event);
    }

    /**
     * Publish shipping preparation event
     */
    public void publishShippingPreparation(Object event) throws Exception {
        publish("shipping.preparation", event);
    }

    /**
     * Publish shipping prepared event
     */
    public void publishShippingPreparedToOrderService(Object event) throws Exception {
        publish(messageBrokerProperties.getRoutingKey().getShippingPrepared(), event);
    }

    /**
     * Publish order status changed event
     */
    public void publishOrderStatusChanged(Object event) throws Exception {
        publish(messageBrokerProperties.getRoutingKey().getOrderStatusChanged(), event);
    }

    /**
     * Publish payment refund event
     */
    public void publishPaymentRefund(Object orderId, String paymentId) throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId);
        event.put("paymentId", paymentId);
        publish("payment.refund", event);
    }

    /**
     * Publish inventory release event
     */
    public void publishInventoryRelease(Object orderId, String reservationId) throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId);
        event.put("reservationId", reservationId);
        publish("inventory.release", event);
    }

    /**
     * Publish shipping cancellation event
     */
    public void publishShippingCancellation(Object orderId, String shippingId) throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId);
        event.put("shippingId", shippingId);
        publish("shipping.cancellation", event);
    }

    /**
     * Check if the message broker is healthy
     * 
     * @return true if broker is healthy
     */
    public boolean isHealthy() {
        return adapterFactory.isHealthy();
    }

    /**
     * Get the current provider name
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return adapterFactory.getProviderName();
    }
}
