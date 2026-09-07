package com.mrecoder.errortime.example.demo.record;

import java.time.Instant;

/** Fake message returned by {@link com.mrecoder.errortime.example.demo.service.MessageQueueService}. */
public record QueueMessage(String queueName, String payload, Instant receivedAt) {
}
