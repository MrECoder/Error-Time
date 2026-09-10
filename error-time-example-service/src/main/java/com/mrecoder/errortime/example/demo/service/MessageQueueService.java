package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.example.demo.record.QueueMessage;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Stands in for a real message-queuing client (SQS, RabbitMQ, ...) - no
 * actual broker, just enough branching on {@link SimulatedOutcome} to
 * exercise every path through {@code GlobalExceptionHandler}, including the
 * outcome a broker consumer is naturally prone to: being throttled
 * ({@code rate-limited}).
 */
@Service
public class MessageQueueService {

    public QueueMessage receiveNextMessage(String queueName, SimulatedOutcome outcome) {

        return switch (outcome) {
            case SUCCESS -> new QueueMessage(queueName, "{\"event\":\"order.created\"}", Instant.now());
            case NOT_FOUND -> throw ResourceNotFoundException.of("queue", queueName);
            case INVALID -> throw new ValidationException("Queue name '%s' does not match the required naming convention".formatted(queueName));
            case UNAVAILABLE -> throw RemoteServiceUnavailableException.of("message queuing service");
            case RATE_LIMITED -> throw RateLimitExceededException.withRetryAfter(
                "Consumer exceeded the broker's poll rate limit for queue '%s'".formatted(queueName), 5);
            case UNAUTHENTICATED, UNAUTHORIZED, CONFLICT, PRECONDITION_FAILED, DOWNSTREAM_TIMEOUT ->
                throw outcome.unsupportedFor("the message queue consume");
        };
    }
}
