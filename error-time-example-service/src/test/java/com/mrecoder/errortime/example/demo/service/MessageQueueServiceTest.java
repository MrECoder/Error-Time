package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.QueueMessage;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageQueueServiceTest {

    private final MessageQueueService service = new MessageQueueService();

    @Test
    void successReturnsAMessageForTheGivenQueue() {
        QueueMessage message = service.receiveNextMessage("orders", SimulatedOutcome.SUCCESS);

        assertThat(message.queueName()).isEqualTo("orders");
    }

    @Test
    void notFoundThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> service.receiveNextMessage("orders", SimulatedOutcome.NOT_FOUND))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidThrowsValidationException() {
        assertThatThrownBy(() -> service.receiveNextMessage("orders", SimulatedOutcome.INVALID))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void unavailableThrowsRemoteServiceUnavailableException() {
        assertThatThrownBy(() -> service.receiveNextMessage("orders", SimulatedOutcome.UNAVAILABLE))
            .isInstanceOf(RemoteServiceUnavailableException.class);
    }

    @Test
    void rateLimitedThrowsRateLimitExceededExceptionWithRetryAfter() {
        assertThatThrownBy(() -> service.receiveNextMessage("orders", SimulatedOutcome.RATE_LIMITED))
            .isInstanceOf(RateLimitExceededException.class)
            .satisfies(ex -> assertThat(((RateLimitExceededException) ex).getRetryAfterSeconds()).contains(5L));
    }

    @ParameterizedTest
    @EnumSource(value = SimulatedOutcome.class,
        names = {"UNAUTHENTICATED", "UNAUTHORIZED", "CONFLICT", "PRECONDITION_FAILED", "DOWNSTREAM_TIMEOUT"})
    void outcomesThisServiceDoesNotModelAreRejectedAsValidationErrors(SimulatedOutcome outcome) {
        assertThatThrownBy(() -> service.receiveNextMessage("orders", outcome))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not modeled for");
    }
}
