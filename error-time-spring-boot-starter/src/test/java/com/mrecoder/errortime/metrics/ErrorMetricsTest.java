package com.mrecoder.errortime.metrics;

import com.mrecoder.errortime.exception.CommonErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ErrorMetrics errorMetrics = new ErrorMetrics(registry);

    @Test
    void appErrorsAreCountedSeparatelyPerErrorCodeAndStatus() {
        errorMetrics.recordAppError(CommonErrorCode.VALIDATION_ERROR, 400);
        errorMetrics.recordAppError(CommonErrorCode.VALIDATION_ERROR, 400);
        errorMetrics.recordAppError(CommonErrorCode.RESOURCE_NOT_FOUND, 404);

        assertThat(registry.get("app.errors").tag("errorCode", "VALIDATION_ERROR").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("app.errors").tag("errorCode", "RESOURCE_NOT_FOUND").counter().count()).isEqualTo(1.0);
    }

    @Test
    void downstreamErrorsAreTaggedBySource() {
        errorMetrics.recordDownstreamError("feign:downstream-service", CommonErrorCode.INTERNAL_SERVICE_ERROR);
        errorMetrics.recordDownstreamError("amqp:error-time.events", CommonErrorCode.VALIDATION_ERROR);

        assertThat(registry.get("downstream.errors").tag("source", "feign:downstream-service").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("downstream.errors").tag("source", "amqp:error-time.events").counter().count())
            .isEqualTo(1.0);
    }
}
