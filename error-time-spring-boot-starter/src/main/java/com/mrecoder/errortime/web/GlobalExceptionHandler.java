package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.CommonErrorCode;
import com.mrecoder.errortime.exception.ErrorCode;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.support.LogSanitizer;
import com.mrecoder.errortime.support.SensitiveDataRedactor;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
 *
 * <p>Every distinct {@link ErrorCode} gets its own {@code type} URI (see
 * {@link ProblemDetailFactory}), sensitive field/detail names are redacted
 * regardless of what a caller supplied (see {@link SensitiveDataRedactor}),
 * and every string that traces back to caller input is stripped of
 * CR/LF before it reaches a log line (see {@link LogSanitizer}) so a crafted
 * request can't forge extra log entries.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler implements Ordered {

    private static final String PROPERTY_ERRORS = "errors";

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;
    private final ProblemDetailFactory problemDetailFactory;
    private final SensitiveDataRedactor redactor;
    private final boolean includeRejectedValue;
    private final int order;

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this(traceIdProvider, errorMetrics, URI.create("about:blank"), false, Ordered.LOWEST_PRECEDENCE);
    }

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            URI problemTypeBaseUri, boolean includeRejectedValue, int order) {
        this(traceIdProvider, errorMetrics, problemTypeBaseUri, includeRejectedValue, order,
            false, 10, new SensitiveDataRedactor(true, List.of()));
    }

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            URI problemTypeBaseUri, boolean includeRejectedValue, int order,
            boolean includeStackTrace, int stackTraceMaxFrames, SensitiveDataRedactor redactor) {
        this(traceIdProvider, errorMetrics,
            new ProblemDetailFactory(traceIdProvider, problemTypeBaseUri, includeStackTrace, stackTraceMaxFrames),
            includeRejectedValue, order, redactor);
    }

    /** Used by the auto-configuration, which shares one {@link ProblemDetailFactory} bean across this and {@code CircuitBreakerExceptionHandler}. */
    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            ProblemDetailFactory problemDetailFactory, boolean includeRejectedValue, int order,
            SensitiveDataRedactor redactor) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
        this.problemDetailFactory = problemDetailFactory;
        this.includeRejectedValue = includeRejectedValue;
        this.order = order;
        this.redactor = redactor;
        log.info("Error-Time web error handling activated (order={}, includeRejectedValue={})", order, includeRejectedValue);
    }

    @Override
    public int getOrder() {
        return order;
    }

    /**
     * More specific than {@link #handleAppException}, so Spring routes every
     * {@link RateLimitExceededException} here instead - the only
     * {@code AppException} subtype whose response needs a header alongside
     * the body (the standard {@code Retry-After}, RFC 9110 §10.2.3).
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex, WebRequest request) {
        logHandling(ex, request);
        ProblemDetail problem = buildAndRecord(ex, request);
        HttpHeaders headers = new HttpHeaders();
        ex.getRetryAfterSeconds().ifPresent(seconds -> headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)));
        return ResponseEntity.status(ex.getStatus()).headers(headers).body(problem);
    }

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex, WebRequest request) {
        logHandling(ex, request);
        return buildAndRecord(ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        logHandling(ex, request);
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> toFieldErrorDetail(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
            .toList();

        ErrorCode errorCode = CommonErrorCode.VALIDATION_ERROR;
        ProblemDetail problem = problemDetailFactory.create(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request, ex);
        problem.setProperty(PROPERTY_ERRORS, fieldErrors);

        errorMetrics.recordAppError(errorCode, HttpStatus.BAD_REQUEST.value());
        logError(HttpStatus.BAD_REQUEST, errorCode, problem.getDetail(), null);

        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        logHandling(ex, request);
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
            .map(this::toFieldErrorDetail)
            .toList();

        ErrorCode errorCode = CommonErrorCode.CONSTRAINT_VIOLATION;
        ProblemDetail problem = problemDetailFactory.create(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request, ex);
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
        logHandling(ex, request);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = problemDetailFactory.create(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", errorCode, request, ex);

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
        problem.setProperty(ProblemDetailFactory.PROPERTY_TIMESTAMP, Instant.now());
        String traceId = traceIdProvider.currentTraceId();
        problem.setProperty(ProblemDetailFactory.PROPERTY_TRACE_ID, traceId);

        HttpStatusCode statusCode = errorResponse.getStatusCode();
        String detail = LogSanitizer.sanitize(problem.getDetail());
        var event = statusCode.is5xxServerError() ? log.atError() : log.atWarn();
        event.addKeyValue("status", statusCode.value())
            .addKeyValue("traceId", traceId);
        if (statusCode.is5xxServerError()) {
            event.setCause(ex);
        }
        event.log("Pass-through error response: status={} traceId={} message={}", statusCode.value(), traceId, detail);
        return problem;
    }

    private ProblemDetail buildAndRecord(AppException ex, WebRequest request) {
        ProblemDetail problem = problemDetailFactory.create(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), request, ex);
        ex.getDetails().forEach((key, value) -> problem.setProperty(key, redactor.redactIfSensitive(key, value)));

        errorMetrics.recordAppError(ex.getErrorCode(), ex.getStatus().value());
        logError(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex);

        return problem;
    }

    private void logHandling(Exception ex, WebRequest request) {
        log.debug("Resolving {} for request {}", ex.getClass().getSimpleName(),
            LogSanitizer.sanitize(request.getDescription(false)));
    }

    private void logError(HttpStatus status, ErrorCode errorCode, String message, Throwable cause) {
        String traceId = traceIdProvider.currentTraceId();
        var event = status.is5xxServerError() ? log.atError() : log.atWarn();
        event.addKeyValue("errorCode", errorCode.name())
            .addKeyValue("httpStatus", status.value())
            .addKeyValue("traceId", traceId);
        if (cause != null && status.is5xxServerError()) {
            event.setCause(cause);
        }
        event.log("errorCode={} status={} traceId={} message={}",
            errorCode.name(), status.value(), traceId, LogSanitizer.sanitize(message));
    }

    private FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
        return toFieldErrorDetail(field, violation.getMessage(), violation.getInvalidValue());
    }

    private FieldErrorDetail toFieldErrorDetail(String field, String message, Object rejectedValue) {
        Object value = includeRejectedValue ? redactor.redactIfSensitive(field, rejectedValue) : null;
        return new FieldErrorDetail(field, message, value);
    }
}
