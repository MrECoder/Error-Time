package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import com.mrecoder.errortime.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit coverage of the plain method body, bypassing the
 * {@code @CircuitBreaker} proxy entirely - {@code RemoteServicesDemoControllerTest}
 * intentionally keeps its circuit-breaker scenario to one sequential test
 * method (a shared breaker instance makes outcome order-dependent otherwise),
 * so it never exercises the outcomes this service doesn't model.
 */
class CircuitBreakerDemoServiceTest {

    private final CircuitBreakerDemoService service = new CircuitBreakerDemoService();

    @Test
    void successReturnsAHealthyMessage() {
        assertThat(service.checkStatus(SimulatedOutcome.SUCCESS)).contains("healthy");
    }

    @Test
    void unavailableThrowsRemoteServiceUnavailableException() {
        assertThatThrownBy(() -> service.checkStatus(SimulatedOutcome.UNAVAILABLE))
            .isInstanceOf(RemoteServiceUnavailableException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SimulatedOutcome.class,
        names = {"NOT_FOUND", "INVALID", "UNAUTHENTICATED", "UNAUTHORIZED",
            "CONFLICT", "PRECONDITION_FAILED", "RATE_LIMITED", "DOWNSTREAM_TIMEOUT"})
    void outcomesThisServiceDoesNotModelAreRejectedAsValidationErrors(SimulatedOutcome outcome) {
        assertThatThrownBy(() -> service.checkStatus(outcome))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("is not modeled for");
    }
}
