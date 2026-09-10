package com.mrecoder.errortime.resilience;

import com.mrecoder.errortime.exception.CommonErrorCode;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.support.LogSanitizer;
import com.mrecoder.errortime.web.ProblemDetailFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Maps Resilience4j's {@link CallNotPermittedException} - thrown when a
 * {@code @CircuitBreaker}-decorated call is rejected because the breaker is
 * open - to the same {@link ProblemDetail} shape ({@link CommonErrorCode#SERVICE_UNAVAILABLE},
 * 503) every other error this library reports uses, instead of it falling
 * through to {@code GlobalExceptionHandler}'s generic 500 handler. A separate
 * advice (not a method on {@code GlobalExceptionHandler}) because that class
 * is always instantiated, with or without Resilience4j on the classpath -
 * referencing {@link CallNotPermittedException} there would break every
 * consumer that doesn't have it.
 */
@Slf4j
@RestControllerAdvice
public class CircuitBreakerExceptionHandler implements Ordered {

    private final ErrorMetrics errorMetrics;
    private final ProblemDetailFactory problemDetailFactory;
    private final int order;

    public CircuitBreakerExceptionHandler(ErrorMetrics errorMetrics, ProblemDetailFactory problemDetailFactory, int order) {
        this.errorMetrics = errorMetrics;
        this.problemDetailFactory = problemDetailFactory;
        this.order = order;
        log.info("Error-Time Resilience4j circuit-breaker integration activated (order={})", order);
    }

    @Override
    public int getOrder() {
        return order;
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCallNotPermitted(CallNotPermittedException ex, WebRequest request) {
        String circuitBreakerName = ex.getCausingCircuitBreakerName() != null
            ? ex.getCausingCircuitBreakerName()
            : "unknown";
        log.debug("Resolving CallNotPermittedException (circuitBreaker={}) for request {}",
            circuitBreakerName, LogSanitizer.sanitize(request.getDescription(false)));

        String detail = "Circuit breaker '%s' is open - call rejected".formatted(circuitBreakerName);
        ProblemDetail problem = problemDetailFactory.create(
            HttpStatus.SERVICE_UNAVAILABLE, detail, CommonErrorCode.SERVICE_UNAVAILABLE, request, ex);

        String source = "circuitBreaker:" + circuitBreakerName;
        errorMetrics.recordDownstreamError(source, CommonErrorCode.SERVICE_UNAVAILABLE);
        log.atWarn()
            .addKeyValue("errorCode", CommonErrorCode.SERVICE_UNAVAILABLE.name())
            .addKeyValue("circuitBreaker", circuitBreakerName)
            .log("Circuit breaker open, rejecting call: circuitBreaker={}", circuitBreakerName);

        return problem;
    }
}
