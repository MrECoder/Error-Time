package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.CommonErrorCode;
import com.mrecoder.errortime.exception.ErrorCode;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Single point of translation from any exception this service can throw to
 * the {@link ProblemDetail} (RFC 9457) response callers see, so every error
 * path - deliberate domain exceptions, bean-validation failures, or a bug -
 * ends up logged once, counted once, and shaped the same way. See
 * {@code AppException} for the domain hierarchy and {@code ErrorMetrics} for
 * the counters incremented here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROPERTY_ERROR_CODE = "errorCode";
    private static final String PROPERTY_TIMESTAMP = "timestamp";
    private static final String PROPERTY_TRACE_ID = "traceId";
    private static final String PROPERTY_ERRORS = "errors";

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;
    private final URI validationErrorType;
    private final boolean includeRejectedValue;
    private final int order;

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this(traceIdProvider, errorMetrics, URI.create("about:blank"), false, Ordered.LOWEST_PRECEDENCE);
    }

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            URI problemTypeBaseUri, boolean includeRejectedValue, int order) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
        this.validationErrorType = "about:blank".equals(problemTypeBaseUri.toString())
            ? problemTypeBaseUri
            : URI.create(problemTypeBaseUri.toString() + "/validation-error");
        this.includeRejectedValue = includeRejectedValue;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex, WebRequest request) {
        ProblemDetail problem = newProblemDetail(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), request);
        ex.getDetails().forEach(problem::setProperty);

        errorMetrics.recordAppError(ex.getErrorCode(), ex.getStatus().value());
        logError(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex);

        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> toFieldErrorDetail(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
            .toList();

        ErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetail problem = newProblemDetail(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request);
        problem.setType(validationErrorType);
        problem.setProperty(PROPERTY_ERRORS, fieldErrors);

        errorMetrics.recordAppError(errorCode, HttpStatus.BAD_REQUEST.value());
        logError(HttpStatus.BAD_REQUEST, errorCode, problem.getDetail(), null);

        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
            .map(this::toFieldErrorDetail)
            .toList();

        ErrorCode errorCode = CommonErrorCode.CONSTRAINT_VIOLATION;
        ProblemDetail problem = newProblemDetail(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request);
        problem.setType(validationErrorType);
        problem.setProperty(PROPERTY_ERRORS, fieldErrors);

        errorMetrics.recordAppError(errorCode, HttpStatus.BAD_REQUEST.value());
        logError(HttpStatus.BAD_REQUEST, errorCode, problem.getDetail(), null);

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            return passThroughErrorResponse(ex, errorResponse);
        }

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = newProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", errorCode, request);

        errorMetrics.recordAppError(errorCode, HttpStatus.INTERNAL_SERVER_ERROR.value());
        logError(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, ex.getMessage(), ex);

        return problem;
    }

    /**
     * Exceptions Spring's own resolvers already know how to turn into a correct
     * status/body - unmapped routes ({@code NoResourceFoundException}, 404),
     * {@code HttpRequestMethodNotSupportedException} (405),
     * {@code HttpMediaTypeNotAcceptableException} (406),
     * {@code HttpMessageNotReadableException} (400), and more - implement
     * {@link ErrorResponse}. Because {@code @ExceptionHandler(Exception.class)}
     * runs before those resolvers, handling every exception the same way would
     * otherwise force all of these to 500. Reuse the status/body Spring already
     * computed instead, only adding the traceId/timestamp every response here
     * carries.
     */
    private ProblemDetail passThroughErrorResponse(Exception ex, ErrorResponse errorResponse) {
        ProblemDetail problem = errorResponse.getBody();
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        String traceId = traceIdProvider.currentTraceId();
        problem.setProperty(PROPERTY_TRACE_ID, traceId);

        HttpStatusCode statusCode = errorResponse.getStatusCode();
        if (statusCode.is5xxServerError()) {
            log.error("status={} traceId={} message={}", statusCode.value(), traceId, problem.getDetail(), ex);
        } else {
            log.warn("status={} traceId={} message={}", statusCode.value(), traceId, problem.getDetail());
        }
        return problem;
    }

    private ProblemDetail newProblemDetail(HttpStatus status, String detail, ErrorCode errorCode, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(PROPERTY_ERROR_CODE, errorCode.name());
        problem.setProperty(PROPERTY_TIMESTAMP, Instant.now());
        problem.setProperty(PROPERTY_TRACE_ID, traceIdProvider.currentTraceId());
        setInstanceIfValid(problem, request);
        return problem;
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
            log.warn("Could not build a ProblemDetail 'instance' URI from request description '{}': {}", uri, ex.getMessage());
        }
    }

    private void logError(HttpStatus status, ErrorCode errorCode, String message, Throwable cause) {
        String traceId = traceIdProvider.currentTraceId();
        if (status.is5xxServerError()) {
            log.error("errorCode={} status={} traceId={} message={}", errorCode.name(), status.value(), traceId, message, cause);
        } else {
            log.warn("errorCode={} status={} traceId={} message={}", errorCode.name(), status.value(), traceId, message);
        }
    }

    private FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
        return toFieldErrorDetail(field, violation.getMessage(), violation.getInvalidValue());
    }

    private FieldErrorDetail toFieldErrorDetail(String field, String message, Object rejectedValue) {
        return new FieldErrorDetail(field, message, includeRejectedValue ? rejectedValue : null);
    }
}
