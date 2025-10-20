package com.aioutlet.orderprocessor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Order Processor Service
 * Sets up exchanges, queues, and bindings for choreography-based saga
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    @Value("${messaging.exchange.name}")
    private String exchangeName;

    @Value("${messaging.queue.order-processor}")
    private String orderProcessorQueue;

    @Value("${messaging.routing-key.order-created}")
    private String orderCreatedRoutingKey;

    @Value("${messaging.routing-key.order-updated}")
    private String orderUpdatedRoutingKey;

    @Value("${messaging.routing-key.order-cancelled}")
    private String orderCancelledRoutingKey;

    @Value("${messaging.routing-key.order-shipped:order.shipped}")
    private String orderShippedRoutingKey;

    @Value("${messaging.routing-key.order-delivered:order.delivered}")
    private String orderDeliveredRoutingKey;

    @Value("${messaging.routing-key.order-deleted:order.deleted}")
    private String orderDeletedRoutingKey;

    @Value("${messaging.routing-key.payment-processed}")
    private String paymentProcessedRoutingKey;

    @Value("${messaging.routing-key.payment-failed}")
    private String paymentFailedRoutingKey;

    @Value("${messaging.routing-key.inventory-reserved}")
    private String inventoryReservedRoutingKey;

    @Value("${messaging.routing-key.inventory-failed}")
    private String inventoryFailedRoutingKey;

    @Value("${messaging.routing-key.shipping-prepared}")
    private String shippingPreparedRoutingKey;

    @Value("${messaging.routing-key.shipping-failed}")
    private String shippingFailedRoutingKey;

    /**
     * Main exchange for order processing events
     */
    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder
                .topicExchange(exchangeName)
                .durable(true)
                .build();
    }

    /**
     * Queue for order processor service
     */
    @Bean
    public Queue orderProcessorQueue() {
        return QueueBuilder
                .durable(orderProcessorQueue)
                .withArgument("x-dead-letter-exchange", exchangeName + ".dlx")
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    /**
     * Dead letter exchange for failed messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(exchangeName + ".dlx")
                .durable(true)
                .build();
    }

    /**
     * Dead letter queue
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(orderProcessorQueue + ".dlq")
                .build();
    }

    /**
     * Bindings for order events
     */
    @Bean
    public Binding orderCreatedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderCreatedRoutingKey);
    }

    @Bean
    public Binding orderUpdatedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderUpdatedRoutingKey);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderCancelledRoutingKey);
    }

    @Bean
    public Binding orderShippedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderShippedRoutingKey);
    }

    @Bean
    public Binding orderDeliveredBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderDeliveredRoutingKey);
    }

    @Bean
    public Binding orderDeletedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(orderDeletedRoutingKey);
    }

    @Bean
    public Binding paymentProcessedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(paymentProcessedRoutingKey);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(paymentFailedRoutingKey);
    }

    @Bean
    public Binding inventoryReservedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(inventoryReservedRoutingKey);
    }

    @Bean
    public Binding inventoryFailedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(inventoryFailedRoutingKey);
    }

    @Bean
    public Binding shippingPreparedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(shippingPreparedRoutingKey);
    }

    @Bean
    public Binding shippingFailedBinding() {
        return BindingBuilder
                .bind(orderProcessorQueue())
                .to(orderExchange())
                .with(shippingFailedRoutingKey);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dlq");
    }

    /**
     * JSON message converter for RabbitMQ
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message not delivered. Correlation data: {}, Cause: {}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("Message returned. Reply code: {}, Reply text: {}, Exchange: {}, Routing key: {}",
                    returned.getReplyCode(), returned.getReplyText(),
                    returned.getExchange(), returned.getRoutingKey());
        });
        return template;
    }

    /**
     * Rabbit listener container factory with JSON converter
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
