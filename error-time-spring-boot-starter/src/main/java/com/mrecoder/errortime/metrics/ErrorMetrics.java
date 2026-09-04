package com.mrecoder.errortime.metrics;

import com.mrecoder.errortime.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.lang.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central place for two counters: {@code app.errors} (anything the
 * GlobalExceptionHandler turns into a response) and {@code downstream.errors}
 * (failures talking to other services, via Feign or AMQP). Both are tagged by
 * {@code errorCode}/{@code source} so Prometheus queries can break them down
 * without needing a metric per exception type.
 *
 * <p>{@code meterRegistry} may be {@code null} - a consumer without any
 * {@link MeterRegistry} bean still gets a working, auto-configured bean
 * (recording becomes a no-op) rather than losing {@code GlobalExceptionHandler}
 * and {@code FeignErrorDecoder}, which both depend on this class.
 */
public class ErrorMetrics {

    private static final String APP_ERRORS = "app.errors";
    private static final String DOWNSTREAM_ERRORS = "downstream.errors";

    private static final String TAG_ERROR_CODE = "errorCode";
    private static final String TAG_STATUS = "status";
    private static final String TAG_SOURCE = "source";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> appErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> downstreamErrorCounters = new ConcurrentHashMap<>();

    public ErrorMetrics(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAppError(ErrorCode errorCode, int statusCode) {
        if (meterRegistry == null) {
            return;
        }
        appErrorCounters
            .computeIfAbsent(errorCode.name() + '|' + statusCode, key -> Counter.builder(APP_ERRORS)
                .description("Errors surfaced to callers via the global exception handler")
                .tag(TAG_ERROR_CODE, errorCode.name())
                .tag(TAG_STATUS, String.valueOf(statusCode))
                .register(meterRegistry))
            .increment();
    }

    public void recordDownstreamError(String source, ErrorCode errorCode) {
        if (meterRegistry == null) {
            return;
        }
        downstreamErrorCounters
            .computeIfAbsent(source + '|' + errorCode.name(), key -> Counter.builder(DOWNSTREAM_ERRORS)
                .description("Failures encountered calling downstream systems (Feign, AMQP)")
                .tag(TAG_SOURCE, source)
                .tag(TAG_ERROR_CODE, errorCode.name())
                .register(meterRegistry))
            .increment();
    }
}
