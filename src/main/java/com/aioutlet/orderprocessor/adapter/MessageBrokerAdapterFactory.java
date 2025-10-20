package com.aioutlet.orderprocessor.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Factory for creating the appropriate MessageBrokerAdapter based on configuration
 * Uses Spring's ApplicationContext to resolve adapter beans
 */
@Slf4j
@Component
public class MessageBrokerAdapterFactory {

    private final ApplicationContext applicationContext;
    private final String messagingProvider;
    private MessageBrokerAdapter activeAdapter;

    public MessageBrokerAdapterFactory(
            ApplicationContext applicationContext,
            @Value("${messaging.provider:RabbitMQ}") String messagingProvider) {
        this.applicationContext = applicationContext;
        this.messagingProvider = messagingProvider;
    }

    /**
     * Initialize the adapter after bean construction
     */
    @PostConstruct
    public void initialize() throws Exception {
        log.info("Initializing MessageBrokerAdapterFactory with provider: {}", messagingProvider);
        activeAdapter = createAdapter();
        activeAdapter.initialize();
        log.info("MessageBrokerAdapter initialized successfully: {}", activeAdapter.getProviderName());
    }

    /**
     * Get the active message broker adapter
     * 
     * @return The configured MessageBrokerAdapter instance
     */
    public MessageBrokerAdapter getAdapter() {
        if (activeAdapter == null) {
            throw new IllegalStateException("MessageBrokerAdapter not initialized");
        }
        return activeAdapter;
    }

    /**
     * Create the appropriate adapter based on configuration
     * 
     * @return MessageBrokerAdapter instance
     * @throws IllegalArgumentException if provider is not supported
     */
    private MessageBrokerAdapter createAdapter() {
        log.debug("Creating MessageBrokerAdapter for provider: {}", messagingProvider);

        return switch (messagingProvider.toLowerCase()) {
            case "rabbitmq" -> {
                log.info("Creating RabbitMQ adapter");
                yield applicationContext.getBean("rabbitMQAdapter", MessageBrokerAdapter.class);
            }
            case "kafka" -> {
                log.info("Creating Kafka adapter");
                yield applicationContext.getBean("kafkaAdapter", MessageBrokerAdapter.class);
            }
            case "azureservicebus", "azure-service-bus" -> {
                log.info("Creating Azure Service Bus adapter");
                yield applicationContext.getBean("azureServiceBusAdapter", MessageBrokerAdapter.class);
            }
            default -> {
                log.error("Unsupported messaging provider: {}. Supported providers: RabbitMQ, Kafka, AzureServiceBus", 
                         messagingProvider);
                throw new IllegalArgumentException(
                    "Unsupported messaging provider: " + messagingProvider + 
                    ". Supported providers: RabbitMQ, Kafka, AzureServiceBus");
            }
        };
    }

    /**
     * Get the configured provider name
     * 
     * @return Provider name
     */
    public String getProviderName() {
        return messagingProvider;
    }

    /**
     * Check if the adapter is healthy
     * 
     * @return true if adapter is healthy
     */
    public boolean isHealthy() {
        return activeAdapter != null && activeAdapter.isHealthy();
    }
}
