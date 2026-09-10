package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Generic "a dependency this call needed is temporarily unreachable" failure
 * - a circuit breaker refusing the call, a downstream 503, a connection pool
 * exhausted. Maps to HTTP 503 and is always {@link #isRetryable() retryable}.
 *
 * <p>A consuming service with a specific downstream in mind is still free to
 * declare its own {@link ErrorCode} for a more precise {@code errorCode} tag
 * (see the {@code error-time-example-service} demo) - this type exists for
 * the common case (and for library-internal use, e.g. the Resilience4j
 * circuit-breaker integration) where a generic "unavailable" is enough.
 */
public class ServiceUnavailableException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, Map.of(), cause);
    }

    public ServiceUnavailableException(String message, Map<String, Object> details) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, details);
    }

    public ServiceUnavailableException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, message, details, cause);
    }

    public static ServiceUnavailableException of(String serviceName) {
        return new ServiceUnavailableException("%s is currently unavailable - try again later".formatted(serviceName));
    }
}
