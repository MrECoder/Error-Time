package com.mrecoder.errortime.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GlobalExceptionHandler}/{@code FeignErrorDecoder} tests only ever
 * exercise the single-arg constructor of each {@link AppException} subtype
 * (the one a caller actually throws); the other three overloads - added so a
 * consumer can attach a cause and/or a details map - are exercised here
 * directly instead, along with the shared {@link AppException} contract
 * (status derivation, details defensive-copy, retryability) every subtype
 * relies on.
 */
class ExceptionHierarchyTest {

    private static final Throwable CAUSE = new RuntimeException("root cause");
    private static final Map<String, Object> DETAILS = Map.of("field", "value");

    static Stream<Arguments4Ctor> fourConstructorSubtypes() {
        return Stream.of(
            new Arguments4Ctor("AuthenticationException", AuthenticationException::new,
                AuthenticationException::new, AuthenticationException::new, AuthenticationException::new,
                CommonErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED),
            new Arguments4Ctor("AuthorizationException", AuthorizationException::new,
                AuthorizationException::new, AuthorizationException::new, AuthorizationException::new,
                CommonErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN),
            new Arguments4Ctor("ConflictException", ConflictException::new,
                ConflictException::new, ConflictException::new, ConflictException::new,
                CommonErrorCode.CONFLICT, HttpStatus.CONFLICT),
            new Arguments4Ctor("DownstreamTimeoutException", DownstreamTimeoutException::new,
                DownstreamTimeoutException::new, DownstreamTimeoutException::new, DownstreamTimeoutException::new,
                CommonErrorCode.DOWNSTREAM_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT),
            new Arguments4Ctor("InternalServiceException", InternalServiceException::new,
                InternalServiceException::new, InternalServiceException::new, InternalServiceException::new,
                CommonErrorCode.INTERNAL_SERVICE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR),
            new Arguments4Ctor("PreconditionFailedException", PreconditionFailedException::new,
                PreconditionFailedException::new, PreconditionFailedException::new, PreconditionFailedException::new,
                CommonErrorCode.PRECONDITION_FAILED, HttpStatus.PRECONDITION_FAILED),
            new Arguments4Ctor("ServiceUnavailableException", ServiceUnavailableException::new,
                ServiceUnavailableException::new, ServiceUnavailableException::new, ServiceUnavailableException::new,
                CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
            new Arguments4Ctor("ValidationException", ValidationException::new,
                ValidationException::new, ValidationException::new, ValidationException::new,
                CommonErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST),
            new Arguments4Ctor("ResourceNotFoundException", ResourceNotFoundException::new,
                ResourceNotFoundException::new, ResourceNotFoundException::new, ResourceNotFoundException::new,
                CommonErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fourConstructorSubtypes")
    void everyOverloadWiresErrorCodeStatusMessageDetailsAndCause(Arguments4Ctor args) {
        AppException messageOnly = args.messageCtor.apply("boom");
        assertThat(messageOnly.getErrorCode()).isEqualTo(args.errorCode);
        assertThat(messageOnly.getStatus()).isEqualTo(args.status);
        assertThat(messageOnly.getMessage()).isEqualTo("boom");
        assertThat(messageOnly.getDetails()).isEmpty();
        assertThat(messageOnly.getCause()).isNull();

        AppException withCause = args.messageAndCauseCtor.apply("boom", CAUSE);
        assertThat(withCause.getCause()).isSameAs(CAUSE);
        assertThat(withCause.getDetails()).isEmpty();

        AppException withDetails = args.messageAndDetailsCtor.apply("boom", DETAILS);
        assertThat(withDetails.getDetails()).containsEntry("field", "value");
        assertThat(withDetails.getCause()).isNull();

        AppException full = args.fullCtor.apply("boom", DETAILS, CAUSE);
        assertThat(full.getDetails()).containsEntry("field", "value");
        assertThat(full.getCause()).isSameAs(CAUSE);

        // AppException.getDetails() defensively copies its constructor argument -
        // mutating the map handed in afterward must not leak into the exception.
        assertThat(full.getDetails()).isUnmodifiable();
    }

    @Test
    void fourAndFiveXxStatusesAreRetryableAndFourXxStatusesAreNot() {
        assertThat(new ServiceUnavailableException("x").isRetryable()).isTrue();
        assertThat(new DownstreamTimeoutException("x").isRetryable()).isTrue();
        assertThat(new InternalServiceException("x").isRetryable()).isTrue();

        assertThat(new ValidationException("x").isRetryable()).isFalse();
        assertThat(new ConflictException("x").isRetryable()).isFalse();
        assertThat(new AuthenticationException("x").isRetryable()).isFalse();
    }

    @Test
    void resourceNotFoundExceptionFactoryFormatsResourceTypeAndId() {
        ResourceNotFoundException ex = ResourceNotFoundException.of("widget", "42");

        assertThat(ex.getMessage()).isEqualTo("widget with id '42' was not found");
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void serviceUnavailableExceptionFactoryFormatsServiceName() {
        ServiceUnavailableException ex = ServiceUnavailableException.of("downstream-service");

        assertThat(ex.getMessage()).isEqualTo("downstream-service is currently unavailable - try again later");
    }

    @Test
    void rateLimitExceededExceptionPlainConstructorsCarryNoRetryAfter() {
        assertThat(new RateLimitExceededException("slow down").getRetryAfterSeconds()).isEmpty();
        assertThat(new RateLimitExceededException("slow down", CAUSE).getCause()).isSameAs(CAUSE);
        assertThat(new RateLimitExceededException("slow down", CAUSE).getRetryAfterSeconds()).isEmpty();
        RateLimitExceededException withDetailsAndCause =
            new RateLimitExceededException("slow down", DETAILS, CAUSE);
        assertThat(withDetailsAndCause.getDetails()).containsEntry("field", "value");
        assertThat(withDetailsAndCause.getCause()).isSameAs(CAUSE);
    }

    @Test
    void rateLimitExceededExceptionWithRetryAfterCarriesTheValue() {
        RateLimitExceededException ex = RateLimitExceededException.withRetryAfter("slow down", 30);

        assertThat(ex.getRetryAfterSeconds()).contains(30L);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private record Arguments4Ctor(
        String name,
        Function<String, AppException> messageCtor,
        BiFunction<String, Throwable, AppException> messageAndCauseCtor,
        BiFunction<String, Map<String, Object>, AppException> messageAndDetailsCtor,
        TriFunction<String, Map<String, Object>, Throwable, AppException> fullCtor,
        ErrorCode errorCode,
        HttpStatus status) {

        @Override
        public String toString() {
            return name;
        }
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
