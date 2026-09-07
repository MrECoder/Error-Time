package com.mrecoder.errortime.example.demo.constant;

import com.mrecoder.errortime.exception.ValidationException;

import java.util.Locale;

/**
 * Selects which outcome a demo service pretends to have, driven by the
 * {@code simulate} query parameter on {@link com.mrecoder.errortime.example.demo.RemoteServicesDemoController}'s
 * endpoints - lets a single endpoint per service demonstrate the whole range
 * of responses {@code GlobalExceptionHandler} maps: success, not-found
 * (404), invalid input (400), and a downstream outage (503).
 */
public enum SimulatedOutcome {

    SUCCESS,
    NOT_FOUND,
    INVALID,
    UNAVAILABLE;

    public static SimulatedOutcome from(String value) {
        try {
            return SimulatedOutcome.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "Unknown simulate value '%s' - expected one of: success, not-found, invalid, unavailable"
                    .formatted(value));
        }
    }
}
