package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.resilience.CircuitBreakerExceptionHandler;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import com.mrecoder.errortime.web.ProblemDetailFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link CircuitBreakerExceptionHandler} only when Resilience4j's
 * circuit-breaker module is on the classpath - most consumers won't have it,
 * and referencing {@link CallNotPermittedException} without this guard would
 * break their build.
 */
@AutoConfiguration(after = {
    ErrorTimeWebAutoConfiguration.class,
    ErrorTimeMetricsAutoConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(CallNotPermittedException.class)
@ConditionalOnProperty(prefix = "errortime.resilience", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ErrorTimeProperties.class)
public class ErrorTimeResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailFactory errorTimeProblemDetailFactory(
            TraceIdProvider traceIdProvider, ErrorTimeProperties properties) {
        ErrorTimeProperties.Web web = properties.getWeb();
        return new ProblemDetailFactory(traceIdProvider, web.getProblemTypeBaseUri(),
            web.isIncludeStackTrace(), web.getStackTraceMaxFrames());
    }

    @Bean
    @ConditionalOnMissingBean
    CircuitBreakerExceptionHandler errorTimeCircuitBreakerExceptionHandler(
            ErrorMetrics errorMetrics, ProblemDetailFactory problemDetailFactory, ErrorTimeProperties properties) {
        return new CircuitBreakerExceptionHandler(errorMetrics, problemDetailFactory, properties.getResilience().getOrder());
    }
}
