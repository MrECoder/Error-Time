package com.mrecoder.errortime.example.demo;

import com.mrecoder.errortime.example.demo.constant.DemoErrorCode;
import com.mrecoder.errortime.exception.AppException;

/** Thrown when a pretend remote dependency is simulated as down. */
public class RemoteServiceUnavailableException extends AppException {

    public RemoteServiceUnavailableException(String message) {
        super(DemoErrorCode.SERVICE_UNAVAILABLE, message);
    }

    public static RemoteServiceUnavailableException of(String serviceName) {
        return new RemoteServiceUnavailableException("%s is currently unavailable - try again later".formatted(serviceName));
    }
}
