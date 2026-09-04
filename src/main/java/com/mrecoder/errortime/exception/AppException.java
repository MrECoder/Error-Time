package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Collections;
import java.util.Map;

/**
 * Base type for every domain exception this service throws deliberately.
 * Carries enough structure ({@link #getErrorCode()}, {@link #getStatus()},
 * {@link #getDetails()}) for {@code GlobalExceptionHandler} to turn any
 * subclass into a {@link org.springframework.http.ProblemDetail} without
 * needing a case for each concrete type.
 */
public abstract class AppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final Map<String, Object> details;

    protected AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, Collections.emptyMap(), null);
    }

    protected AppException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, null);
    }

    protected AppException(ErrorCode errorCode, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = errorCode.defaultStatus()
            .orElseThrow(() -> new IllegalArgumentException(errorCode + " has no HTTP status to throw as an AppException"));
        this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
