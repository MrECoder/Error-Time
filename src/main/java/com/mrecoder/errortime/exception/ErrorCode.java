package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * Every error code this service reports - either as the {@code errorCode}
 * property on a {@link org.springframework.http.ProblemDetail} response
 * ({@link AppException#getErrorCode()}) or as an {@code errorCode} tag on the
 * {@code downstream.errors} metric ({@code ErrorMetrics}). Pairing each code
 * with its {@link #defaultStatus()} here means a code/status mismatch is a
 * compile error instead of two literals that happen to agree today and can
 * silently drift apart.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /** Recorded only when {@code @Recover} gives up after retries - never carried by an {@link AppException}. */
    RETRY_EXHAUSTED(null);

    private final HttpStatus defaultStatus;

    ErrorCode(HttpStatus defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public Optional<HttpStatus> defaultStatus() {
        return Optional.ofNullable(defaultStatus);
    }
}
