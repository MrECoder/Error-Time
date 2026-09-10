package com.mrecoder.errortime.support;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a field/detail name looks sensitive enough that its value
 * should never round-trip into a {@link org.springframework.http.ProblemDetail}
 * response, regardless of {@code errortime.web.include-rejected-value} or
 * what a consumer put in an {@code AppException}'s details map. Matching is a
 * case-insensitive substring test against a built-in marker list (password,
 * token, secret, ...) plus any markers a consumer adds - it can be widened,
 * never narrowed, since a shared library defaulting to "leak it" for an
 * unrecognized field name would be the wrong failure mode.
 */
public final class SensitiveDataRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> DEFAULT_MARKERS = Set.of(
        "password", "passwd", "pwd", "secret", "token", "apikey", "api-key", "api_key",
        "authorization", "credential", "ssn", "socialsecurity", "creditcard", "credit-card",
        "cvv", "cvc", "pin", "privatekey", "private-key");

    private final boolean enabled;
    private final Set<String> markers;

    public SensitiveDataRedactor(boolean enabled, Collection<String> additionalMarkers) {
        this.enabled = enabled;
        Set<String> merged = new HashSet<>(DEFAULT_MARKERS);
        additionalMarkers.forEach(marker -> merged.add(marker.toLowerCase(Locale.ROOT)));
        this.markers = Set.copyOf(merged);
    }

    public boolean isSensitive(String fieldName) {
        if (!enabled || fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return markers.stream().anyMatch(normalized::contains);
    }

    public Object redactIfSensitive(String fieldName, Object value) {
        return isSensitive(fieldName) ? REDACTED : value;
    }
}
