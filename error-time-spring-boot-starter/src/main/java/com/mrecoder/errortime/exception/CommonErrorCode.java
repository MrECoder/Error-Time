package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.util.Optional;

/** The {@link ErrorCode}s this library itself throws or records. */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    PRECONDITION_FAILED(HttpStatus.PRECONDITION_FAILED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    DOWNSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    /** Recorded only when {@code @Recover} gives up after retries - never carried by an {@link AppException}. */
    RETRY_EXHAUSTED(null);

    private final HttpStatus defaultStatus;

    CommonErrorCode(HttpStatus defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    @Override
    public Optional<HttpStatus> defaultStatus() {
        return Optional.ofNullable(defaultStatus);
    }
}
