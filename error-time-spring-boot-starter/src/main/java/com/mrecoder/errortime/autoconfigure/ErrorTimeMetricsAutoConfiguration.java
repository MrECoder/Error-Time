package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.metrics.ErrorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link ErrorMetrics} without requiring a {@link MeterRegistry}
 * bean. Deliberately not {@code @ConditionalOnBean(MeterRegistry.class)} -
 * {@code GlobalExceptionHandler} and {@code FeignErrorDecoder} both hard-depend
 * on {@code ErrorMetrics}, so if it conditionally vanished for lack of a
 * registry, both of those would silently vanish too. Instead the registry is
 * resolved lazily and recording degrades to a no-op when absent.
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
    "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration"
})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "errortime.metrics", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ErrorTimeProperties.class)
public class ErrorTimeMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ErrorMetrics errorMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        return new ErrorMetrics(meterRegistry.getIfAvailable());
    }
}
