package com.mrecoder.errortime.example.demo.constant;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.ValidationException;

import java.util.Locale;

/**
 * Selects which outcome a demo service pretends to have, driven by the
 * {@code simulate} query parameter on {@link com.mrecoder.errortime.example.demo.RemoteServicesDemoController}'s
 * endpoints. {@code success}/{@code not-found}/{@code invalid}/{@code unavailable}
 * apply to every pretend service; the rest are business-appropriate to only
 * some of them (an LDAP bind can be unauthenticated, a database write can
 * conflict, a third-party API can time out, ...) - see each service's
 * {@code switch} for which it models, and {@link #unsupportedFor(String)}
 * for what happens if you ask a service for an outcome it doesn't.
 */
public enum SimulatedOutcome {

    SUCCESS,
    NOT_FOUND,
    INVALID,
    UNAVAILABLE,
    UNAUTHENTICATED,
    UNAUTHORIZED,
    CONFLICT,
    PRECONDITION_FAILED,
    RATE_LIMITED,
    DOWNSTREAM_TIMEOUT;

    public static SimulatedOutcome from(String value) {
        try {
            return SimulatedOutcome.valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                ("Unknown simulate value '%s' - expected one of: success, not-found, invalid, unavailable, "
                    + "unauthenticated, unauthorized, conflict, precondition-failed, rate-limited, downstream-timeout")
                        .formatted(value));
        }
    }

    /** For a service whose switch doesn't model this outcome - a clear 400 instead of a confusing crash. */
    public AppException unsupportedFor(String serviceDescription) {
        return new ValidationException(
            "simulate=%s is not modeled for %s".formatted(name().toLowerCase(Locale.ROOT).replace('_', '-'),
                serviceDescription));
    }
}
