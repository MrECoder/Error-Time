package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.util.Optional;

/** The {@link ErrorCode}s this library itself throws or records. */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

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
