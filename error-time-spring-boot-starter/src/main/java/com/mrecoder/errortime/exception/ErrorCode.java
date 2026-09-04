package com.mrecoder.errortime.exception;

import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * Every error code this service reports - either as the {@code errorCode}
 * property on a {@link org.springframework.http.ProblemDetail} response
 * ({@link AppException#getErrorCode()}) or as an {@code errorCode} tag on the
 * {@code downstream.errors} metric ({@code ErrorMetrics}). Pairing each code
 * with its {@link #defaultStatus()} means a code/status mismatch is a compile
 * error instead of two literals that happen to agree today and can silently
 * drift apart.
 *
 * <p>An interface, not an enum, so a consuming service can add its own error
 * codes (e.g. {@code PAYMENT_DECLINED}) by declaring an enum that implements
 * this - an enum's implicit {@link #name()} already satisfies the contract.
 * {@link CommonErrorCode} supplies the codes this library itself uses.
 */
public interface ErrorCode {

    String name();

    Optional<HttpStatus> defaultStatus();
}
