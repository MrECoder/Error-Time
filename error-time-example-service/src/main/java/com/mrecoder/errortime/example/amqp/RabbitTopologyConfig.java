package com.mrecoder.errortime.example.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    @Bean
    DirectExchange errorTimeExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue errorTimeQueue() {
        return QueueBuilder.durable(QUEUE).build();
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
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY);
    }
}
