package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI VALIDATION_ERROR_TYPE = URI.create("https://errors.error-time.dev/validation-error");

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;

    public GlobalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
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
            .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
            .toList();

        String errorCode = "VALIDATION_ERROR";
        ProblemDetail problem = newProblemDetail(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request);
        problem.setType(VALIDATION_ERROR_TYPE);
        problem.setProperty("errors", fieldErrors);

        errorMetrics.recordAppError(errorCode, HttpStatus.BAD_REQUEST.value());
        logError(HttpStatus.BAD_REQUEST, errorCode, problem.getDetail(), null);

        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
            .map(GlobalExceptionHandler::toFieldErrorDetail)
            .toList();

        String errorCode = "CONSTRAINT_VIOLATION";
        ProblemDetail problem = newProblemDetail(HttpStatus.BAD_REQUEST,
            "Validation failed for %d field(s)".formatted(fieldErrors.size()), errorCode, request);
        problem.setType(VALIDATION_ERROR_TYPE);
        problem.setProperty("errors", fieldErrors);

        errorMetrics.recordAppError(errorCode, HttpStatus.BAD_REQUEST.value());
        logError(HttpStatus.BAD_REQUEST, errorCode, problem.getDetail(), null);

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        String errorCode = "INTERNAL_ERROR";
        ProblemDetail problem = newProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", errorCode, request);

        errorMetrics.recordAppError(errorCode, HttpStatus.INTERNAL_SERVER_ERROR.value());
        logError(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, ex.getMessage(), ex);

        return problem;
    }

    private ProblemDetail newProblemDetail(HttpStatus status, String detail, String errorCode, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("traceId", traceIdProvider.currentTraceId());
        problem.setInstance(URI.create(request.getDescription(false).replaceFirst("^uri=", "")));
        return problem;
    }

    private void logError(HttpStatus status, String errorCode, String message, Throwable cause) {
        String traceId = traceIdProvider.currentTraceId();
        if (status.is5xxServerError()) {
            log.error("errorCode={} status={} traceId={} message={}", errorCode, status.value(), traceId, message, cause);
        } else {
            log.warn("errorCode={} status={} traceId={} message={}", errorCode, status.value(), traceId, message);
        }
    }

    private static FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
        return new FieldErrorDetail(field, violation.getMessage(), violation.getInvalidValue());
    }
}
