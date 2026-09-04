package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown for failures that are this service's fault (or a downstream
 * service's fault surfaced through it) rather than the caller's - e.g. a
 * downstream call failing after retries ({@code FeignErrorDecoder},
 * {@code @Recover} methods) or an unexpected internal failure.
 */
public class InternalServiceException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InternalServiceException(String message) {
        super(CommonErrorCode.INTERNAL_SERVICE_ERROR, message);
    }

    public InternalServiceException(String message, Throwable cause) {
        super(CommonErrorCode.INTERNAL_SERVICE_ERROR, message, Map.of(), cause);
    }

    public InternalServiceException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.INTERNAL_SERVICE_ERROR, message, details, cause);
    }
}
