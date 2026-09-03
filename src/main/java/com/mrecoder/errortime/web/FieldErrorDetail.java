package com.mrecoder.errortime.web;

/** One aggregated field-level validation failure, used in the ProblemDetail "errors" property. */
public record FieldErrorDetail(String field, String message, Object rejectedValue) {
}
