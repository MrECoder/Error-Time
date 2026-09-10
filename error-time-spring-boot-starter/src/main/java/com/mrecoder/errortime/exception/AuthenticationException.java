package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a request carries no credentials, or credentials that don't
 * establish who the caller is - as distinct from {@link AuthorizationException},
 * which is for a caller who is known but not permitted to do this. Maps to
 * HTTP 401.
 */
public class AuthenticationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(CommonErrorCode.UNAUTHENTICATED, message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(CommonErrorCode.UNAUTHENTICATED, message, Map.of(), cause);
    }

    public AuthenticationException(String message, Map<String, Object> details) {
        super(CommonErrorCode.UNAUTHENTICATED, message, details);
    }

    public AuthenticationException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.UNAUTHENTICATED, message, details, cause);
    }
}
