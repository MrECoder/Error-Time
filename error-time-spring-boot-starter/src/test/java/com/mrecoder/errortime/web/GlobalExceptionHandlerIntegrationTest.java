package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link GlobalExceptionHandler} through real HTTP requests and
 * asserts the traceId and error-metric side effects it's responsible for.
 * {@code GlobalExceptionHandler} is registered explicitly via
 * {@link TestSupportConfig}, not discovered by component scanning - the
 * library has none, the same way a consumer would only ever get it through
 * the auto-configuration.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerIntegrationTest.TestController.class)
@Import({GlobalExceptionHandlerIntegrationTest.TestController.class, GlobalExceptionHandlerIntegrationTest.TestSupportConfig.class})
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void domainExceptionIsMappedToProblemDetailWithTraceIdAndCountsAppError() throws Exception {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").value("test-trace-id"))
            .andExpect(jsonPath("$.detail").value("widget with id '42' was not found"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist());

        assertThat(meterRegistry.get("app.errors").tag("errorCode", "RESOURCE_NOT_FOUND").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    void beanValidationFailureAggregatesFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void unexpectedExceptionBecomesGenericInternalError() throws Exception {
        mockMvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.traceId").value("test-trace-id"));

        assertThat(meterRegistry.get("app.errors").tag("errorCode", "INTERNAL_ERROR").counter().count())
            .isEqualTo(1.0);
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @GetMapping("/not-found")
        String notFound() {
            throw ResourceNotFoundException.of("widget", "42");
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("kaboom");
        }
    }

    record TestRequest(@NotBlank String name) {
    }

    @TestConfiguration
    static class TestSupportConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ErrorMetrics errorMetrics(MeterRegistry registry) {
            return new ErrorMetrics(registry);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
            return new GlobalExceptionHandler(traceIdProvider, errorMetrics);
        }

        @Bean
        Tracer tracer() {
            TraceContext context = mock(TraceContext.class);
            when(context.traceId()).thenReturn("test-trace-id");
            Span span = mock(Span.class);
            when(span.context()).thenReturn(context);
            Tracer tracer = mock(Tracer.class);
            when(tracer.currentSpan()).thenReturn(span);
            return tracer;
        }

        @Bean
        TraceIdProvider traceIdProvider(Tracer tracer) {
            return new TraceIdProvider(tracer);
        }
    }
}
