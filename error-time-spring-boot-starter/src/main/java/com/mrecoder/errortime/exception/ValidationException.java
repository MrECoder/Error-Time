package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown for domain-level validation failures (as opposed to bean-validation
 * annotation failures, which surface as {@link jakarta.validation.ConstraintViolationException}
 * or {@link org.springframework.web.bind.MethodArgumentNotValidException} and are handled
 * separately - see {@code GlobalExceptionHandler}).
 */
public class ValidationException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(CommonErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(String message, Map<String, Object> fieldErrors) {
        super(CommonErrorCode.VALIDATION_ERROR, message, fieldErrors);
    }
}
