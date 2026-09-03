package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Map;

/** Thrown when a requested domain resource does not exist. */
public class ResourceNotFoundException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resourceType, Object id) {
        return new ResourceNotFoundException("%s with id '%s' was not found".formatted(resourceType, id));
    }

    public ResourceNotFoundException(String message, Map<String, Object> details) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, message, details);
    }
}
