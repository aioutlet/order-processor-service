package com.aioutlet.orderprocessor.adapter;

import java.util.Map;

/**
 * Message Broker Adapter Interface
 * Provides abstraction for different message broker implementations (RabbitMQ, Kafka, Azure Service Bus)
 * Enables loose coupling and easy switching between message broker providers
 */
public interface MessageBrokerAdapter {

    /**
     * Publish a message to the broker
     * 
     * @param exchange Exchange/Topic name
     * @param routingKey Routing key or topic partition
     * @param message Message payload
     * @param headers Optional message headers
     * @throws Exception if publishing fails
     */
    void publish(String exchange, String routingKey, Object message, Map<String, Object> headers) throws Exception;

    /**
     * Publish a message to the broker (without headers)
     * 
     * @param exchange Exchange/Topic name
     * @param routingKey Routing key or topic partition
     * @param message Message payload
     * @throws Exception if publishing fails
     */
    default void publish(String exchange, String routingKey, Object message) throws Exception {
        publish(exchange, routingKey, message, null);
    }

    /**
     * Check if the broker connection is healthy
     * 
     * @return true if connection is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Get the broker provider name
     * 
     * @return Provider name (RabbitMQ, Kafka, AzureServiceBus)
     */
    String getProviderName();

    /**
     * Initialize the adapter and establish connections
     * 
     * @throws Exception if initialization fails
     */
    void initialize() throws Exception;

    /**
     * Clean up resources and close connections
     * 
     * @throws Exception if cleanup fails
     */
    void shutdown() throws Exception;
}
