package com.mrecoder.errortime.example.amqp;

import com.mrecoder.errortime.example.feign.DownstreamService;
import com.mrecoder.errortime.example.feign.ResourceResponse;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the listener fails consistently as an {@code AppException} and is
 * counted, which is what {@link RabbitRetryConfig} relies on to decide retry
 * vs DLQ.
 */
class OrderEventListenerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ErrorMetrics errorMetrics = new ErrorMetrics(meterRegistry);
    private final TraceIdProvider traceIdProvider = new TraceIdProvider(noopTracer());
    private final DownstreamService downstreamService = mock(DownstreamService.class);
    private final OrderEventListener listener =
        new OrderEventListener(traceIdProvider, errorMetrics, downstreamService);

    @Test
    void blankOrderIdIsRejectedAsValidationExceptionAndCounted() {
        assertThatThrownBy(() -> listener.handle(new OrderEvent(" ", List.of(), "payload")))
            .isInstanceOf(ValidationException.class);

        assertThat(meterRegistry.get("downstream.errors")
                .tag("source", "amqp:error-time.events")
                .tag("errorCode", "VALIDATION_ERROR")
                .counter()
                .count())
            .isEqualTo(1.0);
    }

    @Test
    void validEventIsProcessedWithoutError() {
        listener.handle(new OrderEvent("order-1", List.of(), "payload"));

        assertThat(meterRegistry.find("downstream.errors").counter()).isNull();
    }

    @Test
    void resourceIdsAreFetchedConcurrentlyViaDownstreamService() {
        when(downstreamService.fetchResources(List.of("res-1", "res-2")))
            .thenReturn(List.of(new ResourceResponse("res-1", "one"), new ResourceResponse("res-2", "two")));

        listener.handle(new OrderEvent("order-1", List.of("res-1", "res-2"), "payload"));

        assertThat(meterRegistry.find("downstream.errors").counter()).isNull();
    }

    private static Tracer noopTracer() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);
        return tracer;
    }
}
