package com.mrecoder.errortime.example.demo;

import java.time.Instant;

/** Fake message returned by {@link MessageQueueService}. */
public record QueueMessage(String queueName, String payload, Instant receivedAt) {
}
