package com.mrecoder.errortime.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.lang.Nullable;

/**
 * Single source for "what's the current traceId", shared by the global
 * exception handler, the AMQP listener, and the Feign error decoder so all
 * three attach the same identifier to their logs/responses instead of each
 * re-deriving it from {@link Tracer} independently.
 *
 * <p>{@code tracer} may be {@code null} - a consumer that hasn't configured
 * any tracing bridge still gets a working, auto-configured bean rather than
 * a broken application context.
 */
public class TraceIdProvider {

    public static final String DEFAULT_UNAVAILABLE = "unavailable";

    private final Tracer tracer;
    private final String unavailableValue;

    public TraceIdProvider(@Nullable Tracer tracer) {
        this(tracer, DEFAULT_UNAVAILABLE);
    }

    public TraceIdProvider(@Nullable Tracer tracer, String unavailableValue) {
        this.tracer = tracer;
        this.unavailableValue = unavailableValue;
    }

    public String currentTraceId() {
        if (tracer == null) {
            return unavailableValue;
        }
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : unavailableValue;
    }
}
