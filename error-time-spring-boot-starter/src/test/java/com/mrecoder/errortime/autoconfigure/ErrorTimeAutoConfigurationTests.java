package com.mrecoder.errortime.autoconfigure;

import com.mrecoder.errortime.exception.CommonErrorCode;
import com.mrecoder.errortime.feign.FeignErrorDecoder;
import com.mrecoder.errortime.feign.TraceIdPropagationInterceptor;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import com.mrecoder.errortime.web.GlobalExceptionHandler;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the auto-configuration itself - the standard way a Spring Boot
 * starter is tested - rather than the classes it wires up (those have their
 * own unit tests). Each case here mirrors a real consumer scenario: no
 * tracing configured, no metrics configured, Feign absent, web disabled, or
 * a consumer overriding a bean.
 */
class ErrorTimeAutoConfigurationTests {

    private static final Class<?>[] AUTOCONFIGS = {
        ErrorTimeTracingAutoConfiguration.class,
        ErrorTimeMetricsAutoConfiguration.class,
        ErrorTimeWebAutoConfiguration.class,
        ErrorTimeFeignAutoConfiguration.class
    };

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AUTOCONFIGS));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AUTOCONFIGS));

    @Test
    void webContextWithDefaultsRegistersAllFourBeans() {
        webContextRunner.withUserConfiguration(MeterRegistryConfig.class, TracerConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TraceIdProvider.class);
                assertThat(context).hasSingleBean(ErrorMetrics.class);
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                assertThat(context).hasSingleBean(FeignErrorDecoder.class);
                assertThat(context).hasSingleBean(TraceIdPropagationInterceptor.class);
            });
    }

    @Test
    void nonWebContextDoesNotRegisterGlobalExceptionHandler() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
        });
    }

    @Test
    void withoutFeignOnClasspathNoFeignBeansButContextStillStarts() {
        webContextRunner.withClassLoader(new FilteredClassLoader(ErrorDecoder.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(FeignErrorDecoder.class);
                assertThat(context).doesNotHaveBean(TraceIdPropagationInterceptor.class);
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            });
    }

    @Test
    void withoutTracerBeanContextStartsAndTraceIdIsUnavailable() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            TraceIdProvider provider = context.getBean(TraceIdProvider.class);
            assertThat(provider.currentTraceId()).isEqualTo("unavailable");
        });
    }

    @Test
    void withoutMeterRegistryBeanContextStartsAndErrorMetricsIsInert() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ErrorMetrics.class);
            context.getBean(ErrorMetrics.class).recordAppError(CommonErrorCode.INTERNAL_ERROR, 500);
        });
    }

    @Test
    void webEnabledFalseDisablesGlobalExceptionHandler() {
        webContextRunner.withPropertyValues("errortime.web.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
            });
    }

    @Test
    void consumerSuppliedGlobalExceptionHandlerAndErrorDecoderWin() {
        webContextRunner.withUserConfiguration(CustomBeansConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(GlobalExceptionHandler.class).isSameAs(CustomBeansConfig.HANDLER);
                assertThat(context).getBean(ErrorDecoder.class).isSameAs(CustomBeansConfig.DECODER);
            });
    }

    @Test
    void consumerSuppliedOtherRequestInterceptorDoesNotDisableTracePropagation() {
        webContextRunner.withUserConfiguration(OtherInterceptorConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TraceIdPropagationInterceptor.class);
                assertThat(context.getBeansOfType(RequestInterceptor.class)).hasSize(2);
            });
    }

    @Test
    void feignTraceIdHeaderIsConfigurable() {
        webContextRunner.withPropertyValues("errortime.feign.trace-id-header=X-Correlation-Id")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var interceptor = context.getBean(TraceIdPropagationInterceptor.class);
                var template = new feign.RequestTemplate();
                interceptor.apply(template);
                assertThat(template.headers()).containsKey("X-Correlation-Id");
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TracerConfig {

        @Bean
        Tracer tracer() {
            return mock(Tracer.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeansConfig {

        static final GlobalExceptionHandler HANDLER =
            new GlobalExceptionHandler(new TraceIdProvider(null), new ErrorMetrics(null));
        static final ErrorDecoder DECODER = (methodKey, response) -> new RuntimeException("custom decoder");

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return HANDLER;
        }

        @Bean
        ErrorDecoder customErrorDecoder() {
            return DECODER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OtherInterceptorConfig {

        @Bean
        RequestInterceptor authInterceptor() {
            return template -> template.header("Authorization", "Bearer test");
        }
    }
}
