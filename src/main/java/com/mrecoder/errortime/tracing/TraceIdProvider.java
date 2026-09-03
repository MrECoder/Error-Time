package com.mrecoder.errortime.tracing;

import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * Single source for "what's the current traceId" (subtask 4), shared by the
 * global exception handler, the AMQP listener, and the Feign error decoder
 * so all three attach the same identifier to their logs/responses instead of
 * each re-deriving it from {@link Tracer} independently.
 */
@Component
public class TraceIdProvider {

    private static final String UNAVAILABLE = "unavailable";

    private final Tracer tracer;

    public TraceIdProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    public String currentTraceId() {
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : UNAVAILABLE;
    }
}
