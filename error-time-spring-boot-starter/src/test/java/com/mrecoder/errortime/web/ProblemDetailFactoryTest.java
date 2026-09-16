package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.CommonErrorCode;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit coverage of {@link ProblemDetailFactory}, independent of the
 * {@code GlobalExceptionHandler}/{@code CircuitBreakerExceptionHandler}
 * integration tests that only ever exercise it through the 5-arg
 * {@code create} overload with a real request.
 */
class ProblemDetailFactoryTest {

    private final TraceIdProvider traceIdProvider = new TraceIdProvider(null);

    @Test
    void fourArgOverloadDelegatesWithNoStackTraceEvenWhenEnabled() {
        ProblemDetailFactory factory = new ProblemDetailFactory(traceIdProvider, URI.create("about:blank"), true, 10);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/widgets/1"));

        ProblemDetail problem = factory.create(HttpStatus.NOT_FOUND, "not found", CommonErrorCode.RESOURCE_NOT_FOUND, request);

        assertThat(problem.getProperties()).doesNotContainKey("stackTrace");
    }

    @Test
    void aboutBlankBaseUriIsNeverExpandedPerErrorCode() {
        ProblemDetailFactory factory = new ProblemDetailFactory(traceIdProvider, URI.create("about:blank"));

        assertThat(factory.typeUriFor(CommonErrorCode.VALIDATION_ERROR)).isEqualTo(URI.create("about:blank"));
    }

    @Test
    void realBaseUriGetsErrorCodeSlugAppended() {
        ProblemDetailFactory factory = new ProblemDetailFactory(traceIdProvider, URI.create("https://errors.example.com"));

        assertThat(factory.typeUriFor(CommonErrorCode.CONSTRAINT_VIOLATION))
            .isEqualTo(URI.create("https://errors.example.com/constraint-violation"));
    }

    @Test
    void stackTraceIsCappedAtConfiguredFrameCountWithASummaryLine() {
        ProblemDetailFactory factory = new ProblemDetailFactory(traceIdProvider, URI.create("about:blank"), true, 2);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/widgets/1"));
        Exception deepFailure = new RuntimeException("boom");
        // Guarantee more frames than the cap regardless of where the test runner put this call.
        deepFailure.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("A", "m1", "A.java", 1),
            new StackTraceElement("B", "m2", "B.java", 2),
            new StackTraceElement("C", "m3", "C.java", 3),
            new StackTraceElement("D", "m4", "D.java", 4),
        });

        ProblemDetail problem = factory.create(HttpStatus.INTERNAL_SERVER_ERROR, "boom",
            CommonErrorCode.INTERNAL_ERROR, request, deepFailure);

        String stackTrace = (String) problem.getProperties().get("stackTrace");
        assertThat(stackTrace).contains("A.m1").contains("B.m2").contains("... 2 more")
            .doesNotContain("C.m3").doesNotContain("D.m4");
    }

    @Test
    void illegalRequestUriOmitsInstanceInsteadOfThrowing() {
        ProblemDetailFactory factory = new ProblemDetailFactory(traceIdProvider, URI.create("about:blank"));
        // A raw space in the request URI makes URI.create throw - the factory must
        // swallow that and simply omit "instance" rather than fail the whole response.
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/widgets/has space");
        ServletWebRequest request = new ServletWebRequest(servletRequest);

        ProblemDetail problem = factory.create(HttpStatus.NOT_FOUND, "not found", CommonErrorCode.RESOURCE_NOT_FOUND, request);

        assertThat(problem.getInstance()).isNull();
    }
}
