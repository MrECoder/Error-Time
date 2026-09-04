package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.feign.FeignErrorDecoder;
import com.mrecoder.errortime.feign.TraceIdPropagationInterceptor;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link FeignErrorDecoder} and {@link TraceIdPropagationInterceptor}
 * only when Feign is on the classpath. {@code FeignErrorDecoder} backs off by
 * {@link ErrorDecoder} - the interface, so a consumer's own decoder wins.
 * {@link TraceIdPropagationInterceptor} backs off only by its own type, not
 * by {@link RequestInterceptor} - consumers routinely register other
 * interceptors (auth, locale, ...) and keying on the interface would
 * silently disable trace propagation the moment they do.
 */
@AutoConfiguration(after = {
    ErrorTimeTracingAutoConfiguration.class,
    ErrorTimeMetricsAutoConfiguration.class
})
@ConditionalOnClass({ErrorDecoder.class, RequestInterceptor.class})
@ConditionalOnProperty(prefix = "errortime.feign", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ErrorTimeProperties.class)
public class ErrorTimeFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    FeignErrorDecoder errorTimeFeignErrorDecoder(
            TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics, ErrorTimeProperties properties) {
        ErrorTimeProperties.Feign feign = properties.getFeign();
        return new FeignErrorDecoder(traceIdProvider, errorMetrics, feign.isLogResponseBody(), feign.getMaxLoggedBodyChars());
    }

    @Bean
    @ConditionalOnMissingBean
    TraceIdPropagationInterceptor errorTimeTraceIdPropagationInterceptor(
            TraceIdProvider traceIdProvider, ErrorTimeProperties properties) {
        return new TraceIdPropagationInterceptor(traceIdProvider, properties.getFeign().getTraceIdHeader());
    }
}
