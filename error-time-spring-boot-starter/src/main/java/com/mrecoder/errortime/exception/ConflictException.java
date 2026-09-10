package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a request conflicts with the current state of the resource it
 * targets - a duplicate create, a concurrent modification, a state machine
 * transition that isn't valid from here. Maps to HTTP 409.
 */
public class ConflictException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(CommonErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(CommonErrorCode.CONFLICT, message, Map.of(), cause);
    }

    public ConflictException(String message, Map<String, Object> details) {
        super(CommonErrorCode.CONFLICT, message, details);
    }

    public ConflictException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.CONFLICT, message, details, cause);
    }
}
