package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;
import java.util.Optional;

/**
 * Thrown when a caller has exceeded a rate or quota limit. Maps to HTTP 429.
 * Optionally carries how long the caller should wait before retrying -
 * {@code GlobalExceptionHandler} reflects that value onto the standard
 * {@code Retry-After} response header (RFC 9110 §10.2.3) when present.
 */
public class RateLimitExceededException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String DETAIL_RETRY_AFTER_SECONDS = "retryAfterSeconds";

    public RateLimitExceededException(String message) {
        super(CommonErrorCode.RATE_LIMITED, message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(CommonErrorCode.RATE_LIMITED, message, Map.of(), cause);
    }

    public RateLimitExceededException(String message, Map<String, Object> details) {
        super(CommonErrorCode.RATE_LIMITED, message, details);
    }

    public RateLimitExceededException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.RATE_LIMITED, message, details, cause);
    }

    /** Convenience factory for the common case: rate-limited, retry in {@code retryAfterSeconds}. */
    public static RateLimitExceededException withRetryAfter(String message, long retryAfterSeconds) {
        return new RateLimitExceededException(message, Map.of(DETAIL_RETRY_AFTER_SECONDS, retryAfterSeconds));
    }

    public Optional<Long> getRetryAfterSeconds() {
        return switch (getDetails().get(DETAIL_RETRY_AFTER_SECONDS)) {
            case Number n -> Optional.of(n.longValue());
            case null, default -> Optional.empty();
        };
    }
}
