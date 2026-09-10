package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.ErrorCode;
import com.mrecoder.errortime.support.LogSanitizer;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * Builds every {@link ProblemDetail} this library returns, so the RFC 9457
 * fields beyond the standard ones ({@code type}, {@code errorCode},
 * {@code timestamp}, {@code traceId}, and an optional {@code stackTrace}) are
 * assembled in exactly one place - shared by {@code GlobalExceptionHandler}
 * and the Resilience4j circuit-breaker advice, so a Resilience4j failure
 * looks like every other error this library reports instead of a bespoke
 * shape.
 *
 * <p>Every {@link ErrorCode} gets its own {@code type} URI (RFC 9457 §3.1) -
 * {@code {problemTypeBaseUri}/{kebab-case error code}} - rather than only
 * validation errors getting one and everything else defaulting to
 * {@code about:blank}; when {@code problemTypeBaseUri} itself is
 * {@code about:blank} (the default), every error's {@code type} stays
 * {@code about:blank} too, since there's no base to build a real URI from.
 */
@Slf4j
public class ProblemDetailFactory {

    static final String PROPERTY_ERROR_CODE = "errorCode";
    static final String PROPERTY_TIMESTAMP = "timestamp";
    static final String PROPERTY_TRACE_ID = "traceId";
    static final String PROPERTY_STACK_TRACE = "stackTrace";

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final TraceIdProvider traceIdProvider;
    private final URI problemTypeBaseUri;
    private final boolean includeStackTrace;
    private final int stackTraceMaxFrames;

    public ProblemDetailFactory(TraceIdProvider traceIdProvider, URI problemTypeBaseUri) {
        this(traceIdProvider, problemTypeBaseUri, false, 10);
    }

    public ProblemDetailFactory(TraceIdProvider traceIdProvider, URI problemTypeBaseUri,
            boolean includeStackTrace, int stackTraceMaxFrames) {
        this.traceIdProvider = traceIdProvider;
        this.problemTypeBaseUri = problemTypeBaseUri;
        this.includeStackTrace = includeStackTrace;
        this.stackTraceMaxFrames = stackTraceMaxFrames;
    }

    public ProblemDetail create(HttpStatus status, String detail, ErrorCode errorCode, WebRequest request) {
        return create(status, detail, errorCode, request, null);
    }

    /**
     * @param cause only used (and only when {@code errortime.web.include-stack-trace}
     *              is on) to populate {@code stackTrace}; may be {@code null}.
     */
    public ProblemDetail create(HttpStatus status, String detail, ErrorCode errorCode, WebRequest request,
            @Nullable Throwable cause) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(typeUriFor(errorCode));
        problem.setProperty(PROPERTY_ERROR_CODE, errorCode.name());
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        problem.setProperty(PROPERTY_TRACE_ID, traceIdProvider.currentTraceId());
        setInstanceIfValid(problem, request);
        if (includeStackTrace && cause != null) {
            problem.setProperty(PROPERTY_STACK_TRACE, formatStackTrace(cause));
        }
        return problem;
    }

    public URI typeUriFor(ErrorCode errorCode) {
        if (ABOUT_BLANK.toString().equals(problemTypeBaseUri.toString())) {
            return ABOUT_BLANK;
        }
        String slug = errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return URI.create(problemTypeBaseUri.toString() + "/" + slug);
    }

    /**
     * {@code request.getDescription(false)}'s "uri=" prefix strip can leave an
     * unencoded illegal URI character (a raw space, {@code {}, |, ^}, ...) from
     * the original request path, which makes {@link URI#create} throw
     * {@code IllegalArgumentException} - inside the exception handler itself.
     * Omit {@code instance} rather than let that crash the response.
     */
    private void setInstanceIfValid(ProblemDetail problem, WebRequest request) {
        String uri = request.getDescription(false).replaceFirst("^uri=", "");
        try {
            problem.setInstance(URI.create(uri));
        } catch (IllegalArgumentException ex) {
            log.warn("Could not build a ProblemDetail 'instance' URI from request description '{}': {}",
                LogSanitizer.sanitize(uri), ex.getMessage());
        }
    }

    /**
     * Deliberately never used unless a consumer opts in - a stack trace in an
     * HTTP response is an information-disclosure risk (file paths, package
     * structure, library versions) and must never be the default for a
     * library other services depend on. Capped at {@code stackTraceMaxFrames}
     * so a deep recursive failure doesn't inflate every error response.
     */
    private String formatStackTrace(Throwable cause) {
        StackTraceElement[] elements = cause.getStackTrace();
        int limit = Math.min(elements.length, stackTraceMaxFrames);
        StringBuilder trace = new StringBuilder(cause.toString());
        for (int i = 0; i < limit; i++) {
            trace.append(System.lineSeparator()).append("\tat ").append(elements[i]);
        }
        if (elements.length > limit) {
            trace.append(System.lineSeparator()).append("\t... ").append(elements.length - limit).append(" more");
        }
        return trace.toString();
    }
}
