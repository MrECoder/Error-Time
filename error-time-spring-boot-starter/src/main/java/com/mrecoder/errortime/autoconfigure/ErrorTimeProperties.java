package com.mrecoder.errortime.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

import java.net.URI;

/** Configuration surface for {@code error-time-spring-boot-starter}, prefix {@code errortime}. */
@ConfigurationProperties(prefix = "errortime")
public class ErrorTimeProperties {

    private final Web web = new Web();
    private final Feign feign = new Feign();
    private final Metrics metrics = new Metrics();
    private final Tracing tracing = new Tracing();

    public Web getWeb() {
        return web;
    }

    public Feign getFeign() {
        return feign;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public static class Web {

        private boolean enabled = true;

        /**
         * Base URI for the "validation-error" problem type. Defaults to the RFC
         * 9457-recommended {@code about:blank} for callers who haven't published
         * their own problem-type documentation; a real base URI gets
         * {@code /validation-error} appended.
         */
        private URI problemTypeBaseUri = URI.create("about:blank");

        /**
         * Whether {@code FieldErrorDetail.rejectedValue} echoes the caller's
         * submitted value back in the response body. Off by default - a shared
         * library shouldn't default to reflecting a password/PII field's value
         * into an error response across every consuming service.
         */
        private boolean includeRejectedValue = false;

        /** {@link org.springframework.core.annotation.Order} of the advice, so a consumer's own advice can take precedence. */
        private int order = Ordered.LOWEST_PRECEDENCE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public URI getProblemTypeBaseUri() {
            return problemTypeBaseUri;
        }

        public void setProblemTypeBaseUri(URI problemTypeBaseUri) {
            this.problemTypeBaseUri = problemTypeBaseUri;
        }

        public boolean isIncludeRejectedValue() {
            return includeRejectedValue;
        }

        public void setIncludeRejectedValue(boolean includeRejectedValue) {
            this.includeRejectedValue = includeRejectedValue;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }
    }

    public static class Feign {

        private boolean enabled = true;

        /** Outbound header carrying the current traceId to downstream calls. */
        private String traceIdHeader = "X-Trace-Id";

        /**
         * Whether a failed downstream response body is included in the ERROR log
         * line. Off by default - across a fleet of services this is a PII/secret
         * leak vector and a log-volume cost, not just noise.
         */
        private boolean logResponseBody = false;

        /** Caps how much of the body (message and, if enabled, log line) is kept when logResponseBody is on. */
        private int maxLoggedBodyChars = 2048;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTraceIdHeader() {
            return traceIdHeader;
        }

        public void setTraceIdHeader(String traceIdHeader) {
            this.traceIdHeader = traceIdHeader;
        }

        public boolean isLogResponseBody() {
            return logResponseBody;
        }

        public void setLogResponseBody(boolean logResponseBody) {
            this.logResponseBody = logResponseBody;
        }

        public int getMaxLoggedBodyChars() {
            return maxLoggedBodyChars;
        }

        public void setMaxLoggedBodyChars(int maxLoggedBodyChars) {
            this.maxLoggedBodyChars = maxLoggedBodyChars;
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Tracing {

        private String unavailableValue = "unavailable";

        public String getUnavailableValue() {
            return unavailableValue;
        }

        public void setUnavailableValue(String unavailableValue) {
            this.unavailableValue = unavailableValue;
        }
    }
}
