package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import com.mrecoder.errortime.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Registers {@link GlobalExceptionHandler} for servlet web applications only
 * - its handler methods take a {@code WebRequest}, which isn't resolvable in
 * a WebFlux {@code @ControllerAdvice}, so registering it reactively would
 * produce a broken advice rather than no advice.
 */
@AutoConfiguration(after = {
    ErrorTimeTracingAutoConfiguration.class,
    ErrorTimeMetricsAutoConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RestControllerAdvice.class, ProblemDetail.class})
@ConditionalOnProperty(prefix = "errortime.web", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ErrorTimeProperties.class)
public class ErrorTimeWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler errorTimeGlobalExceptionHandler(
            TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics, ErrorTimeProperties properties) {
        ErrorTimeProperties.Web web = properties.getWeb();
        return new GlobalExceptionHandler(traceIdProvider, errorMetrics,
            web.getProblemTypeBaseUri(), web.isIncludeRejectedValue(), web.getOrder());
    }
}
