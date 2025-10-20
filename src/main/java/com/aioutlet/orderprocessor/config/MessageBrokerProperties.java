package com.aioutlet.orderprocessor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Message Broker Configuration Properties
 * Supports multiple broker providers (RabbitMQ, Kafka, Azure Service Bus)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "messaging")
public class MessageBrokerProperties {

    /**
     * Message broker provider (RabbitMQ, Kafka, AzureServiceBus)
     */
    private String provider = "RabbitMQ";

    /**
     * Exchange name for events
     */
    private ExchangeConfig exchange = new ExchangeConfig();

    /**
     * Queue configuration
     */
    private QueueConfig queue = new QueueConfig();

    /**
     * Routing key configuration
     */
    private RoutingKeyConfig routingKey = new RoutingKeyConfig();

    /**
     * RabbitMQ specific configuration
     */
    private RabbitMQConfig rabbitmq = new RabbitMQConfig();

    /**
     * Kafka specific configuration
     */
    private KafkaConfig kafka = new KafkaConfig();

    /**
     * Azure Service Bus specific configuration
     */
    private AzureServiceBusConfig azureServiceBus = new AzureServiceBusConfig();

    @Data
    public static class ExchangeConfig {
        private String name = "aioutlet.events";
        private String type = "topic";
    }

    @Data
    public static class QueueConfig {
        private String orderProcessor = "order-processor-queue";
    }

    @Data
    public static class RoutingKeyConfig {
        private String orderCreated = "order.created";
        private String orderUpdated = "order.updated";
        private String orderCancelled = "order.cancelled";
        private String orderShipped = "order.shipped";
        private String orderDelivered = "order.delivered";
        private String orderDeleted = "order.deleted";
        private String paymentProcessed = "payment.processed";
        private String paymentFailed = "payment.failed";
        private String inventoryReserved = "inventory.reserved";
        private String inventoryFailed = "inventory.failed";
        private String shippingPrepared = "shipping.prepared";
        private String shippingFailed = "shipping.failed";
    }

    @Data
    public static class RabbitMQConfig {
        private String host = "localhost";
        private int port = 5672;
        private String username = "admin";
        private String password = "admin123";
        private String virtualHost = "/";
    }

    @Data
    public static class KafkaConfig {
        private String brokers = "localhost:9092";
        private String groupId = "order-processor-service";
        private String topic = "aioutlet.events";
    }

    @Data
    public static class AzureServiceBusConfig {
        private String namespace;
        private String connectionString;
        private boolean useManagedIdentity = true;
        private String topicName = "aioutlet-events";
        private String subscriptionName = "order-processor-subscription";
    }
}
