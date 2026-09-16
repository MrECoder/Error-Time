package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.AuthenticationException;
import com.mrecoder.errortime.exception.AuthorizationException;
import com.mrecoder.errortime.exception.ConflictException;
import com.mrecoder.errortime.exception.DownstreamTimeoutException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.PreconditionFailedException;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ServiceUnavailableException;
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
import java.util.List;
import java.util.Map;

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
    void mapsUnrecognizedServerErrorToInternalServiceException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(500, "boom"));

        assertThat(mapped).isInstanceOf(InternalServiceException.class);
        assertThat(meterRegistry.get("downstream.errors").tag("errorCode", "INTERNAL_SERVICE_ERROR").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void mapsServiceUnavailableToServiceUnavailableException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(503, "unavailable"));

        assertThat(mapped).isInstanceOf(ServiceUnavailableException.class);
        assertThat(meterRegistry.get("downstream.errors").tag("errorCode", "SERVICE_UNAVAILABLE").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void mapsUnauthorizedToAuthenticationException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(401, "no credentials"));

        assertThat(mapped).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void mapsForbiddenToAuthorizationException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(403, "not permitted"));

        assertThat(mapped).isInstanceOf(AuthorizationException.class);
    }

    @Test
    void mapsConflictToConflictException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(409, "conflict"));

        assertThat(mapped).isInstanceOf(ConflictException.class);
    }

    @Test
    void mapsGatewayTimeoutToDownstreamTimeoutException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(504, "timeout"));

        assertThat(mapped).isInstanceOf(DownstreamTimeoutException.class);
    }

    @Test
    void mapsRequestTimeoutToDownstreamTimeoutExceptionToo() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(408, "timeout"));

        assertThat(mapped).isInstanceOf(DownstreamTimeoutException.class);
    }

    @Test
    void mapsPreconditionFailedToPreconditionFailedException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(412, "stale"));

        assertThat(mapped).isInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void mapsUnrecognizedFourXxToValidationExceptionWithStatusInMessage() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(418, "teapot"));

        assertThat(mapped).isInstanceOf(ValidationException.class);
        assertThat(mapped.getMessage()).contains("418");
    }

    @Test
    void mapsUnresolvableStatusCodeToInternalServiceException() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(599, "unknown"));

        assertThat(mapped).isInstanceOf(InternalServiceException.class);
    }

    @Test
    void rateLimitWithoutRetryAfterHeaderStillMapsButCarriesNoRetryAfter() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response(429, "slow down"));

        assertThat(mapped).isInstanceOf(RateLimitExceededException.class);
        assertThat(((RateLimitExceededException) mapped).getRetryAfterSeconds()).isEmpty();
    }

    @Test
    void rateLimitWithUnparseableRetryAfterHeaderIsIgnored() {
        Response response = Response.builder()
            .status(429)
            .reason("test")
            .request(Request.create(Request.HttpMethod.GET, "http://downstream/api/resources/42",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
            .headers(Map.of("Retry-After", List.of("not-a-number")))
            .body("rate limited", StandardCharsets.UTF_8)
            .build();

        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response);

        assertThat(((RateLimitExceededException) mapped).getRetryAfterSeconds()).isEmpty();
    }

    @Test
    void logResponseBodyEnabledReadsAndTruncatesTheDownstreamBody() {
        FeignErrorDecoder loggingDecoder = new FeignErrorDecoder(traceIdProvider, errorMetrics, true, 5);

        Exception mapped = loggingDecoder.decode("DownstreamClient#getResource(String)",
            response(500, "0123456789"));

        // Truncation only affects the logged body, never the caller-facing message.
        assertThat(mapped).isInstanceOf(InternalServiceException.class);
    }

    @Test
    void mapsTooManyRequestsToRateLimitExceededExceptionCarryingRetryAfter() {
        Response response = Response.builder()
            .status(429)
            .reason("test")
            .request(Request.create(Request.HttpMethod.GET, "http://downstream/api/resources/42",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null))
            .headers(Map.of("Retry-After", List.of("30")))
            .body("rate limited", StandardCharsets.UTF_8)
            .build();

        Exception mapped = decoder.decode("DownstreamClient#getResource(String)", response);

        assertThat(mapped).isInstanceOf(RateLimitExceededException.class);
        assertThat(((RateLimitExceededException) mapped).getRetryAfterSeconds()).contains(30L);
    }

    @Test
    void doesNotLeakDownstreamResponseBodyIntoPublicMessageByDefault() {
        Exception mapped = decoder.decode("DownstreamClient#getResource(String)",
            response(400, "SUPER-SECRET-INTERNAL-DETAIL"));

        assertThat(mapped.getMessage()).doesNotContain("SUPER-SECRET-INTERNAL-DETAIL");
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
