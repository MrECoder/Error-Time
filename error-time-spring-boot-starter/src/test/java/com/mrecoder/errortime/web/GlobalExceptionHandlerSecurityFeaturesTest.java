package com.mrecoder.errortime.web;

import com.mrecoder.errortime.exception.AuthenticationException;
import com.mrecoder.errortime.exception.AuthorizationException;
import com.mrecoder.errortime.exception.ConflictException;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.support.SensitiveDataRedactor;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the richer, security-conscious behavior on top of the baseline
 * exercised by {@link GlobalExceptionHandlerIntegrationTest}: sensitive-field
 * redaction, opt-in stack traces, the {@code Retry-After} header on
 * {@link RateLimitExceededException}, and a distinct {@code type} URI per
 * error code once a real {@code problemTypeBaseUri} is configured.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerSecurityFeaturesTest.TestController.class)
@Import({GlobalExceptionHandlerSecurityFeaturesTest.TestController.class,
    GlobalExceptionHandlerSecurityFeaturesTest.TestSupportConfig.class})
class GlobalExceptionHandlerSecurityFeaturesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sensitiveFieldIsRedactedEvenWithIncludeRejectedValueOn() throws Exception {
        mockMvc.perform(post("/secure-test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("password"))
            .andExpect(jsonPath("$.errors[0].rejectedValue").value(SensitiveDataRedactor.REDACTED));
    }

    @Test
    void stackTraceIsIncludedWhenOptedIn() throws Exception {
        mockMvc.perform(get("/secure-test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.stackTrace").exists())
            .andExpect(jsonPath("$.stackTrace").value(org.hamcrest.Matchers.containsString("RuntimeException")));
    }

    @Test
    void rateLimitExceededSetsRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/secure-test/rate-limited"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }

    @Test
    void authenticationAndAuthorizationExceptionsMapToDistinctStatusesAndTypeUris() throws Exception {
        mockMvc.perform(get("/secure-test/unauthenticated"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.type").value("https://errors.example.com/unauthenticated"));

        mockMvc.perform(get("/secure-test/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.type").value("https://errors.example.com/unauthorized"));
    }

    @Test
    void conflictExceptionMapsToDistinctTypeUriFromNotFound() throws Exception {
        mockMvc.perform(get("/secure-test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("https://errors.example.com/conflict"));

        mockMvc.perform(get("/secure-test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://errors.example.com/resource-not-found"));
    }

    @RestController
    @RequestMapping("/secure-test")
    public static class TestController {

        @PostMapping("/validate")
        String validate(@Valid @RequestBody SecureTestRequest request) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("kaboom with sensitive internal detail");
        }

        @GetMapping("/rate-limited")
        String rateLimited() {
            throw RateLimitExceededException.withRetryAfter("slow down", 42);
        }

        @GetMapping("/unauthenticated")
        String unauthenticated() {
            throw new AuthenticationException("no credentials supplied");
        }

        @GetMapping("/forbidden")
        String forbidden() {
            throw new AuthorizationException("not permitted");
        }

        @GetMapping("/conflict")
        String conflict() {
            throw new ConflictException("already exists");
        }

        @GetMapping("/not-found")
        String notFound() {
            throw ResourceNotFoundException.of("widget", "42");
        }
    }

    record SecureTestRequest(@NotBlank String password) {
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
        TraceIdProvider traceIdProvider() {
            return new TraceIdProvider(null);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
            return new GlobalExceptionHandler(traceIdProvider, errorMetrics,
                URI.create("https://errors.example.com"), true, 0,
                true, 10, new SensitiveDataRedactor(true, List.of()));
        }
    }
}
