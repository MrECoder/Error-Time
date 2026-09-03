package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.Request;
import feign.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit-level coverage of the Feign -> AppException mapping (subtask 5). */
class FeignErrorDecoderTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ErrorMetrics errorMetrics = new ErrorMetrics(meterRegistry);
    private final TraceIdProvider traceIdProvider = new TraceIdProvider(noopTracer());
    private final FeignErrorDecoder decoder = new FeignErrorDecoder(traceIdProvider, errorMetrics);

    @Test
    void mapsNotFoundToResourceNotFoundException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(404, "missing"));

        assertThat(mapped).isInstanceOf(ResourceNotFoundException.class);
        assertThat(meterRegistry.get("downstream.errors").tag("errorCode", "RESOURCE_NOT_FOUND").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void mapsBadRequestToValidationException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(400, "bad input"));

        assertThat(mapped).isInstanceOf(ValidationException.class);
    }

    @Test
    void mapsServerErrorToInternalServiceException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(503, "unavailable"));

        assertThat(mapped).isInstanceOf(InternalServiceException.class);
        assertThat(meterRegistry.get("downstream.errors").tag("errorCode", "INTERNAL_SERVICE_ERROR").counter().count())
            .isEqualTo(1.0);
    }

    private static Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://downstream/api/resources/42",
            Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
            .status(status)
            .reason("test")
            .request(request)
            .headers(Collections.emptyMap())
            .body(body, StandardCharsets.UTF_8)
            .build();
    }

    private static Tracer noopTracer() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);
        return tracer;
    }
}
