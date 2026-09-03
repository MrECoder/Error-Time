package com.mrecoder.errortime.amqp;

import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the listener fails consistently as an {@code AppException} and is
 * counted (subtask 7/8), which is what {@link com.mrecoder.errortime.amqp.RabbitRetryConfig}
 * relies on to decide retry vs DLQ.
 */
class OrderEventListenerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ErrorMetrics errorMetrics = new ErrorMetrics(meterRegistry);
    private final TraceIdProvider traceIdProvider = new TraceIdProvider(noopTracer());
    private final OrderEventListener listener = new OrderEventListener(traceIdProvider, errorMetrics);

    @Test
    void blankOrderIdIsRejectedAsValidationExceptionAndCounted() {
        assertThatThrownBy(() -> listener.handle(new OrderEvent(" ", "payload")))
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
        listener.handle(new OrderEvent("order-1", "payload"));

        assertThat(meterRegistry.find("downstream.errors").counter()).isNull();
    }

    private static Tracer noopTracer() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);
        return tracer;
    }
}
