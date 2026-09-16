package com.mrecoder.errortime.autoconfigure;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ErrorTimeAutoConfigurationTests} only ever binds a handful of these
 * properties from a property-source string; this exercises every
 * getter/setter pair directly so a typo that breaks the binding contract
 * (e.g. a setter that doesn't assign the field it looks like it does) fails
 * a test instead of only ever surfacing as a silently-ignored property.
 */
class ErrorTimePropertiesTest {

    private final ErrorTimeProperties properties = new ErrorTimeProperties();

    @Test
    void webDefaultsMatchDocumentedSafeValues() {
        ErrorTimeProperties.Web web = properties.getWeb();

        assertThat(web.isEnabled()).isTrue();
        assertThat(web.getProblemTypeBaseUri()).isEqualTo(URI.create("about:blank"));
        assertThat(web.isIncludeRejectedValue()).isFalse();
        assertThat(web.isIncludeStackTrace()).isFalse();
        assertThat(web.getStackTraceMaxFrames()).isEqualTo(10);
        assertThat(web.isRedactSensitiveFields()).isTrue();
        assertThat(web.getAdditionalRedactedFieldMarkers()).isEmpty();
    }

    @Test
    void webPropertiesRoundTripThroughTheirSetters() {
        ErrorTimeProperties.Web web = properties.getWeb();

        web.setEnabled(false);
        web.setProblemTypeBaseUri(URI.create("https://errors.example.com"));
        web.setIncludeRejectedValue(true);
        web.setOrder(5);
        web.setIncludeStackTrace(true);
        web.setStackTraceMaxFrames(20);
        web.setRedactSensitiveFields(false);
        web.setAdditionalRedactedFieldMarkers(List.of("nationalId"));

        assertThat(web.isEnabled()).isFalse();
        assertThat(web.getProblemTypeBaseUri()).isEqualTo(URI.create("https://errors.example.com"));
        assertThat(web.isIncludeRejectedValue()).isTrue();
        assertThat(web.getOrder()).isEqualTo(5);
        assertThat(web.isIncludeStackTrace()).isTrue();
        assertThat(web.getStackTraceMaxFrames()).isEqualTo(20);
        assertThat(web.isRedactSensitiveFields()).isFalse();
        assertThat(web.getAdditionalRedactedFieldMarkers()).containsExactly("nationalId");
    }

    @Test
    void feignPropertiesRoundTripThroughTheirSetters() {
        ErrorTimeProperties.Feign feign = properties.getFeign();

        assertThat(feign.isEnabled()).isTrue();
        assertThat(feign.getTraceIdHeader()).isEqualTo("X-Trace-Id");
        assertThat(feign.isLogResponseBody()).isFalse();
        assertThat(feign.getMaxLoggedBodyChars()).isEqualTo(2048);

        feign.setEnabled(false);
        feign.setTraceIdHeader("X-Correlation-Id");
        feign.setLogResponseBody(true);
        feign.setMaxLoggedBodyChars(512);

        assertThat(feign.isEnabled()).isFalse();
        assertThat(feign.getTraceIdHeader()).isEqualTo("X-Correlation-Id");
        assertThat(feign.isLogResponseBody()).isTrue();
        assertThat(feign.getMaxLoggedBodyChars()).isEqualTo(512);
    }

    @Test
    void metricsPropertiesRoundTripThroughTheirSetters() {
        ErrorTimeProperties.Metrics metrics = properties.getMetrics();

        assertThat(metrics.isEnabled()).isTrue();
        metrics.setEnabled(false);
        assertThat(metrics.isEnabled()).isFalse();
    }

    @Test
    void tracingPropertiesRoundTripThroughTheirSetters() {
        ErrorTimeProperties.Tracing tracing = properties.getTracing();

        assertThat(tracing.getUnavailableValue()).isEqualTo("unavailable");
        tracing.setUnavailableValue("no-trace");
        assertThat(tracing.getUnavailableValue()).isEqualTo("no-trace");
    }

    @Test
    void resiliencePropertiesRoundTripThroughTheirSetters() {
        ErrorTimeProperties.Resilience resilience = properties.getResilience();

        assertThat(resilience.isEnabled()).isTrue();
        resilience.setEnabled(false);
        resilience.setOrder(7);

        assertThat(resilience.isEnabled()).isFalse();
        assertThat(resilience.getOrder()).isEqualTo(7);
    }
}
