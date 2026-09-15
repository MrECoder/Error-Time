package com.mrecoder.errortime.example.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue/exchange topology for subtask 7 (AMQP error handling). Failed
 * messages that exhaust retries (see {@link RabbitRetryConfig}) land on the
 * dead-letter queue via the dead-letter exchange rather than being dropped
 * or endlessly redelivered.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE = "error-time.exchange";
    public static final String QUEUE = "error-time.events";
    public static final String ROUTING_KEY = "events";

    public static final String DEAD_LETTER_EXCHANGE = "error-time.exchange.dlx";
    public static final String DEAD_LETTER_QUEUE = "error-time.events.dlq";

    /** Bounds unbounded broker-memory growth if a flood of messages arrives faster than the consumer drains them. */
    private static final long MAX_QUEUE_LENGTH = 10_000L;

    /**
     * Spring Boot's auto-configured {@code RabbitTemplate} and the
     * {@code SimpleRabbitListenerContainerFactory} built in
     * {@link RabbitRetryConfig} both auto-detect and apply this bean. Without
     * it, Spring AMQP's default {@code SimpleMessageConverter} deserializes
     * any {@code Serializable} payload via raw Java serialization
     * ({@code ObjectInputStream.readObject()}) - a deserialization-gadget
     * RCE risk (CWE-502) for anything arriving from a queue an untrusted
     * party can publish to. JSON has no such risk.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    DirectExchange errorTimeExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue errorTimeQueue() {
        return QueueBuilder.durable(QUEUE)
            .maxLength(MAX_QUEUE_LENGTH)
            .overflow(QueueBuilder.Overflow.rejectPublish)
            .build();
    }

    @Bean
    Binding errorTimeBinding(Queue errorTimeQueue, DirectExchange errorTimeExchange) {
        return BindingBuilder.bind(errorTimeQueue).to(errorTimeExchange).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE)
            .maxLength(MAX_QUEUE_LENGTH)
            .overflow(QueueBuilder.Overflow.rejectPublish)
            .build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY);
    }
}
