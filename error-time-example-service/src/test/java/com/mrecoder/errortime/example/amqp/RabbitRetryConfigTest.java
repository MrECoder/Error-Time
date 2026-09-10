package com.mrecoder.errortime.example.amqp;

import com.mrecoder.errortime.example.ErrorTimeExampleApplication;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end coverage of {@link RabbitRetryConfig} against a real broker -
 * the one thing the unit-level {@link OrderEventListenerTest} can't verify,
 * since it never touches actual queue/exchange/DLQ bindings. Needs Docker;
 * skips itself (rather than failing the build) when Docker isn't available,
 * so it's harmless in an environment without it and still runs for real in
 * CI (see {@code .github/workflows/ci.yml}, which runs on a Docker-equipped
 * runner).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ErrorTimeExampleApplication.class)
class RabbitRetryConfigTest {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void nonRetryableFailureIsRoutedToTheDeadLetterQueueRatherThanRedelivered() {
        // A blank orderId fails as ValidationException (4xx, non-retryable per
        // RetryClassifier) - RabbitRetryConfig's interceptor should recover
        // straight to the DLQ instead of spending three attempts on a message
        // that can never succeed.
        OrderEvent event = new OrderEvent(" ", List.of(), "test-payload");

        rabbitTemplate.convertAndSend(RabbitTopologyConfig.EXCHANGE, RabbitTopologyConfig.ROUTING_KEY, event);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Object deadLettered = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.DEAD_LETTER_QUEUE, 1000);
            assertThat(deadLettered).isInstanceOf(OrderEvent.class);
            assertThat(((OrderEvent) deadLettered).payload()).isEqualTo("test-payload");
        });

        assertThat(rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.QUEUE, 500)).isNull();
    }
}
