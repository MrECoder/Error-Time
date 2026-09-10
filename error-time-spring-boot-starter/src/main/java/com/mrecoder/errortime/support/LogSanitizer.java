package com.mrecoder.errortime.support;

/**
 * Strips characters a log-forging attack (CWE-117) relies on - CR/LF (to fake
 * extra log lines) and other ASCII control characters - from any string that
 * ultimately traces back to caller-supplied input (a request URI, a header, a
 * downstream response) before it reaches a log line. Values that are always
 * internally generated (a {@code Tracer}-issued traceId, an enum name) don't
 * need this; anything derived from the request or from another service does.
 */
public final class LogSanitizer {

    private static final String REDACTION_MARKER = "\\n";

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sanitized = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isControlChar = c == '\r' || c == '\n' || (c < 0x20 && c != '\t');
            if (isControlChar && sanitized == null) {
                sanitized = new StringBuilder(value.length() + 16).append(value, 0, i);
            }
            if (sanitized != null) {
                sanitized.append(isControlChar ? REDACTION_MARKER : String.valueOf(c));
            }
        }
        return sanitized == null ? value : sanitized.toString();
    }
}
