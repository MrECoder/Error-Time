package com.mrecoder.errortime.example.demo.service;

import com.mrecoder.errortime.example.demo.RemoteServiceUnavailableException;
import com.mrecoder.errortime.example.demo.constant.SimulatedOutcome;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

/**
 * Demonstrates the starter's Resilience4j integration rather than
 * {@code GlobalExceptionHandler} directly. {@link #checkStatus} is decorated
 * with {@code @CircuitBreaker}, so a run of failures trips the breaker (see
 * {@code application.yml}'s deliberately small
 * {@code resilience4j.circuitbreaker.instances.circuit-breaker-demo} window -
 * a handful of {@code ?simulate=unavailable} calls in a row is enough); once
 * open, Resilience4j throws {@code CallNotPermittedException} straight from
 * its proxy without running the method body at all, and the starter's
 * {@code ErrorTimeResilienceAutoConfiguration} maps that to the same 503
 * {@code ProblemDetail} shape every other error here uses, instead of a
 * generic 500.
 */
@Service
public class CircuitBreakerDemoService {

    public static final String CIRCUIT_BREAKER_NAME = "circuit-breaker-demo";

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME)
    public String checkStatus(SimulatedOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "circuit-breaker-demo backend is healthy";
            case UNAVAILABLE -> throw RemoteServiceUnavailableException.of("circuit-breaker-demo backend");
            case NOT_FOUND, INVALID, UNAUTHENTICATED, UNAUTHORIZED, CONFLICT, PRECONDITION_FAILED,
                RATE_LIMITED, DOWNSTREAM_TIMEOUT -> throw outcome.unsupportedFor("the circuit breaker demo");
        };
    }
}
