package com.mrecoder.errortime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central place for the two counters the subtasks call for: {@code app.errors}
 * (anything the GlobalExceptionHandler turns into a response) and
 * {@code downstream.errors} (failures talking to other services, via Feign
 * or AMQP). Both are tagged by {@code errorCode}/{@code source} so Prometheus
 * queries can break them down without needing a metric per exception type.
 */
@Component
public class ErrorMetrics {

    private static final String APP_ERRORS = "app.errors";
    private static final String DOWNSTREAM_ERRORS = "downstream.errors";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> appErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> downstreamErrorCounters = new ConcurrentHashMap<>();

    public ErrorMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAppError(String errorCode, int statusCode) {
        appErrorCounters
            .computeIfAbsent(errorCode + '|' + statusCode, key -> Counter.builder(APP_ERRORS)
                .description("Errors surfaced to callers via the global exception handler")
                .tag("errorCode", errorCode)
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry))
            .increment();
    }

    public void recordDownstreamError(String source, String errorCode) {
        downstreamErrorCounters
            .computeIfAbsent(source + '|' + errorCode, key -> Counter.builder(DOWNSTREAM_ERRORS)
                .description("Failures encountered calling downstream systems (Feign, AMQP)")
                .tag("source", source)
                .tag("errorCode", errorCode)
                .register(meterRegistry))
            .increment();
    }
}
