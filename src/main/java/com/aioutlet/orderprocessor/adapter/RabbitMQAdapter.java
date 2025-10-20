package com.aioutlet.orderprocessor.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ implementation of MessageBrokerAdapter
 * Handles message publishing to RabbitMQ exchanges
 */
@Slf4j
@Component("rabbitMQAdapter")
public class RabbitMQAdapter implements MessageBrokerAdapter {

    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public RabbitMQAdapter(RabbitTemplate rabbitTemplate, 
                          ConnectionFactory connectionFactory,
                          ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String exchange, String routingKey, Object message, Map<String, Object> headers) throws Exception {
        try {
            log.debug("Publishing message to RabbitMQ - Exchange: {}, RoutingKey: {}", exchange, routingKey);

            // Create message properties
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType("application/json");
            
            // Add custom headers if provided
            if (headers != null && !headers.isEmpty()) {
                headers.forEach((key, value) -> {
                    if (value != null) {
                        messageProperties.setHeader(key, value);
                    }
                });
            }

            // Serialize message to JSON
            byte[] messageBody = objectMapper.writeValueAsBytes(message);
            Message rabbitMessage = new Message(messageBody, messageProperties);

            // Publish to RabbitMQ
            rabbitTemplate.send(exchange, routingKey, rabbitMessage);
            
            log.info("Successfully published message to RabbitMQ - Exchange: {}, RoutingKey: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to publish message to RabbitMQ - Exchange: {}, RoutingKey: {}", exchange, routingKey, e);
            throw new Exception("Failed to publish message to RabbitMQ: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            return connectionFactory.createConnection().isOpen();
        } catch (Exception e) {
            log.error("RabbitMQ health check failed", e);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "RabbitMQ";
    }

    @Override
    public void initialize() throws Exception {
        log.info("Initializing RabbitMQ adapter");
        // RabbitTemplate is already initialized by Spring
        if (!isHealthy()) {
            throw new Exception("RabbitMQ connection is not healthy");
        }
        log.info("RabbitMQ adapter initialized successfully");
    }

    @Override
    public void shutdown() throws Exception {
        log.info("Shutting down RabbitMQ adapter");
        // Connection management is handled by Spring
        log.info("RabbitMQ adapter shutdown complete");
    }
}
