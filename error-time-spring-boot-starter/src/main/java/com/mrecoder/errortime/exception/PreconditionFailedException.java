package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a conditional request (an {@code If-Match}/{@code If-Unmodified-Since}
 * precondition, an optimistic-locking version check) fails because the
 * resource has moved on since the caller last read it. Maps to HTTP 412.
 */
public class PreconditionFailedException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PreconditionFailedException(String message) {
        super(CommonErrorCode.PRECONDITION_FAILED, message);
    }

    public PreconditionFailedException(String message, Throwable cause) {
        super(CommonErrorCode.PRECONDITION_FAILED, message, Map.of(), cause);
    }

    public PreconditionFailedException(String message, Map<String, Object> details) {
        super(CommonErrorCode.PRECONDITION_FAILED, message, details);
    }

    public PreconditionFailedException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.PRECONDITION_FAILED, message, details, cause);
    }
}
