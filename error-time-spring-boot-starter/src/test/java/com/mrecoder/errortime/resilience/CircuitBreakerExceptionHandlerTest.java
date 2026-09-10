package com.mrecoder.errortime.resilience;

import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import com.mrecoder.errortime.web.ProblemDetailFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerExceptionHandlerTest {

    private final ErrorMetrics errorMetrics = new ErrorMetrics(new SimpleMeterRegistry());
    private final ProblemDetailFactory problemDetailFactory =
        new ProblemDetailFactory(new TraceIdProvider(null), java.net.URI.create("about:blank"));
    private final CircuitBreakerExceptionHandler handler =
        new CircuitBreakerExceptionHandler(errorMetrics, problemDetailFactory, 0);

    @Test
    void mapsCallNotPermittedToServiceUnavailableProblemDetail() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("downstream-service");
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(circuitBreaker);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/demo"));

        ProblemDetail problem = handler.handleCallNotPermitted(ex, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getProperties()).containsEntry("errorCode", "SERVICE_UNAVAILABLE");
        assertThat(problem.getDetail()).contains("downstream-service");
    }
}
