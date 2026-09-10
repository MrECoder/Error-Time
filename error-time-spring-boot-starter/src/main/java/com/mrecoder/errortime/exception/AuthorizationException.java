package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a caller is known (see {@link AuthenticationException}) but not
 * permitted to perform this action on this resource. Maps to HTTP 403.
 */
public class AuthorizationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(CommonErrorCode.UNAUTHORIZED, message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(CommonErrorCode.UNAUTHORIZED, message, Map.of(), cause);
    }

    public AuthorizationException(String message, Map<String, Object> details) {
        super(CommonErrorCode.UNAUTHORIZED, message, details);
    }

    public AuthorizationException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.UNAUTHORIZED, message, details, cause);
    }
}
