package com.mrecoder.errortime.amqp;

import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

import java.time.Duration;

/**
 * Retry-vs-DLQ policy for subtask 7. A message is redelivered in-process up
 * to {@code MAX_ATTEMPTS} times only for exceptions considered transient;
 * exceptions that are never going to succeed on retry (bad input, missing
 * resource) are excluded from the policy so they skip straight to the
 * dead-letter queue via {@link RepublishMessageRecoverer} instead of wasting
 * retries on a message that can't be fixed by trying again.
 */
@Configuration
public class RabbitRetryConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 500L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_DELAY_MS = 5_000L;

    @Bean
    public StatelessRetryOperationsInterceptor errorTimeRetryInterceptor(AmqpTemplate amqpTemplate) {
        RetryPolicy retryPolicy = RetryPolicy.builder()
            .maxRetries(MAX_ATTEMPTS)
            .delay(Duration.ofMillis(INITIAL_DELAY_MS))
            .multiplier(BACKOFF_MULTIPLIER)
            .maxDelay(Duration.ofMillis(MAX_DELAY_MS))
            .excludes(ValidationException.class, ResourceNotFoundException.class)
            .build();

        return RetryInterceptorBuilder.stateless()
            .retryPolicy(retryPolicy)
            .recoverer(new RepublishMessageRecoverer(amqpTemplate,
                RabbitTopologyConfig.DEAD_LETTER_EXCHANGE, RabbitTopologyConfig.ROUTING_KEY))
            .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            StatelessRetryOperationsInterceptor errorTimeRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(errorTimeRetryInterceptor);
        return factory;
    }
}
