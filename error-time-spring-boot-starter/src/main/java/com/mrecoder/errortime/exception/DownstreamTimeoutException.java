package com.mrecoder.errortime.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a downstream call didn't respond in time - a connect/read
 * timeout, or a downstream service itself returning HTTP 504/408. Maps to
 * HTTP 504 and is always {@link #isRetryable() retryable}: a timeout says
 * nothing about whether the underlying operation succeeded or failed, only
 * that this attempt didn't get an answer in time.
 */
public class DownstreamTimeoutException extends AppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DownstreamTimeoutException(String message) {
        super(CommonErrorCode.DOWNSTREAM_TIMEOUT, message);
    }

    public DownstreamTimeoutException(String message, Throwable cause) {
        super(CommonErrorCode.DOWNSTREAM_TIMEOUT, message, Map.of(), cause);
    }

    public DownstreamTimeoutException(String message, Map<String, Object> details) {
        super(CommonErrorCode.DOWNSTREAM_TIMEOUT, message, details);
    }

    public DownstreamTimeoutException(String message, Map<String, Object> details, Throwable cause) {
        super(CommonErrorCode.DOWNSTREAM_TIMEOUT, message, details, cause);
    }
}
