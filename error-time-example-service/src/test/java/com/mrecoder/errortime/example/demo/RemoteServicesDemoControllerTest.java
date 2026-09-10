package com.mrecoder.errortime.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.mrecoder.errortime.example.demo.constant.DemoErrorCode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises every pretend remote service through {@link RemoteServicesDemoController},
 * proving {@code error-time-spring-boot-starter}'s auto-configured
 * {@code GlobalExceptionHandler} maps each outcome correctly - including the
 * {@code SERVICE_UNAVAILABLE} code this application defines itself via the
 * library's {@code ErrorCode} interface (see {@link DemoErrorCode}).
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class RemoteServicesDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void databaseRecordSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("42"));
    }

    @Test
    void databaseRecordNotFound() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void databaseRecordUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void databaseRecordConflict() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void databaseRecordPreconditionFailed() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "precondition-failed"))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.errorCode").value("PRECONDITION_FAILED"));
    }

    @Test
    void databaseRecordRejectsOutcomeItDoesNotModel() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "rate-limited"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void messageQueueSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/message-queue/orders/next-message"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queueName").value("orders"));
    }

    @Test
    void messageQueueUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/message-queue/orders/next-message").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void messageQueueRateLimitedSetsRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/demo/services/message-queue/orders/next-message").param("simulate", "rate-limited"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
            .andExpect(header().string("Retry-After", "5"));
    }

    @Test
    void ldapUserSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("jdoe"));
    }

    @Test
    void ldapUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "unauthenticated"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void ldapUserUnauthorized() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "unauthorized"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void ldapUserInvalid() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "invalid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void ldapUserUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/ldap/users/jdoe").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void weatherForecastSucceedsByDefault() throws Exception {
        mockMvc.perform(get("/demo/services/weather/LHR/forecast"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cityCode").value("LHR"));
    }

    @Test
    void weatherForecastUnavailable() throws Exception {
        mockMvc.perform(get("/demo/services/weather/LHR/forecast").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void weatherForecastDownstreamTimeout() throws Exception {
        mockMvc.perform(get("/demo/services/weather/LHR/forecast").param("simulate", "downstream-timeout"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.errorCode").value("DOWNSTREAM_TIMEOUT"));
    }

    @Test
    void unknownSimulateValueIsRejectedAsValidationError() throws Exception {
        mockMvc.perform(get("/demo/services/database/records/42").param("simulate", "bogus"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    /**
     * One test method, not several, deliberately - the circuit breaker is a
     * singleton bean shared with the rest of this test class's (reused)
     * Spring context, so splitting this into separate {@code @Test} methods
     * would make the outcome depend on JUnit's undefined method execution
     * order (an earlier method's calls would count toward this one's sliding
     * window). Self-contained here, the sequence is deterministic:
     * application.yml's circuit-breaker-demo window (sliding-window-size and
     * minimum-number-of-calls both 4, failure-rate-threshold 50%) evaluates
     * once the 4th call in the window completes - so the breaker is still
     * closed for that 4th call itself, and only rejects the 5th.
     */
    @Test
    void circuitBreakerOpensAfterEnoughFailuresAndRejectsFurtherCalls() throws Exception {
        mockMvc.perform(get("/demo/services/circuit-breaker/status"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/demo/services/circuit-breaker/status").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/demo/services/circuit-breaker/status").param("simulate", "unavailable"))
            .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/demo/services/circuit-breaker/status"))
            .andExpect(status().isOk());

        // The breaker is now open - this call never reaches CircuitBreakerDemoService
        // at all; Resilience4j itself throws CallNotPermittedException, which the
        // starter's CircuitBreakerExceptionHandler maps to the same 503 shape.
        mockMvc.perform(get("/demo/services/circuit-breaker/status"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("circuit-breaker-demo")));
    }
}
