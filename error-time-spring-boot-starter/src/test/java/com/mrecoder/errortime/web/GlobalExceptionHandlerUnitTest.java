package com.mrecoder.errortime.web;

import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct, MockMvc-free unit coverage of {@link GlobalExceptionHandler}
 * methods that {@code GlobalExceptionHandlerIntegrationTest} and
 * {@code GlobalExceptionHandlerSecurityFeaturesTest} never reach through a
 * real HTTP request: {@code handleConstraintViolation} (service-layer/AOP
 * method validation, as opposed to {@code @Valid @RequestBody}), the
 * pass-through path for a Spring-internal exception that already implements
 * {@link org.springframework.web.ErrorResponse}, and the 5-arg constructor
 * overload.
 */
class GlobalExceptionHandlerUnitTest {

    private final ErrorMetrics errorMetrics = new ErrorMetrics(new SimpleMeterRegistry());
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
        new TraceIdProvider(null), errorMetrics, URI.create("about:blank"), false, 0);
    private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/test"));

    @Test
    void constraintViolationIsAggregatedIntoFieldErrors() {
        record Sample(@NotBlank String name) {
        }
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<?>> violations = new HashSet<>(validator.validate(new Sample("")));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ProblemDetail problem = handler.handleConstraintViolation(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "CONSTRAINT_VIOLATION");
        assertThat(problem.getProperties().get("errors")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
            .hasSize(1);
    }

    @Test
    void springErrorResponseExceptionsPassThroughWithTraceIdAddedFourXx() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "unsupported media type");

        ProblemDetail problem = handler.handleGenericException(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE.value());
        assertThat(problem.getProperties()).containsKey("traceId");
    }

    @Test
    void springErrorResponseExceptionsPassThroughWithTraceIdAddedFiveXx() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream broke");

        ProblemDetail problem = handler.handleGenericException(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(problem.getProperties()).containsKey("traceId");
    }
}
