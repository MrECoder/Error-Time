package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link TraceIdProvider} without requiring a {@link Tracer} bean -
 * a consumer that hasn't wired up any tracing bridge still gets a working
 * bean (falling back to {@code errortime.tracing.unavailable-value}) rather
 * than a broken application context, since {@code GlobalExceptionHandler} and
 * {@code FeignErrorDecoder} both depend on this.
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
    "org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration"
})
@EnableConfigurationProperties(ErrorTimeProperties.class)
public class ErrorTimeTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TraceIdProvider traceIdProvider(ObjectProvider<Tracer> tracer, ErrorTimeProperties properties) {
        return new TraceIdProvider(tracer.getIfAvailable(), properties.getTracing().getUnavailableValue());
    }
}
