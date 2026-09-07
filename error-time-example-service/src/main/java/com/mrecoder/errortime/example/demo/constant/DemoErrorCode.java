package com.mrecoder.errortime.example.demo.constant;

import com.mrecoder.errortime.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * This service's own error code, declared exactly the way any consumer of
 * error-time-spring-boot-starter would: implementing the library's
 * {@link ErrorCode} interface rather than needing the library to know about
 * it in advance. 503 isn't part of {@code CommonErrorCode}, so services that
 * want it define it themselves.
 */
public enum DemoErrorCode implements ErrorCode {

    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    DemoErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public Optional<HttpStatus> defaultStatus() {
        return Optional.of(status);
    }
}
