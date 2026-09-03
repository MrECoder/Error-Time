package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Subtask 6: retries the downstream call only for failures
 * {@link FeignErrorDecoder} classified as {@link InternalServiceException}
 * (i.e. likely transient - 5xx, timeouts). A 400/404 from the decoder comes
 * back as {@code ValidationException}/{@code ResourceNotFoundException},
 * which are not listed here and so propagate immediately - retrying a
 * downstream 404 three times with backoff would only slow the caller down
 * for no benefit.
 */
@Service
public class DownstreamService {

    private static final int MAX_ATTEMPTS = 3;

    private final DownstreamClient downstreamClient;
    private final ErrorMetrics errorMetrics;

    public DownstreamService(DownstreamClient downstreamClient, ErrorMetrics errorMetrics) {
        this.downstreamClient = downstreamClient;
        this.errorMetrics = errorMetrics;
    }

    @Retryable(
        value = InternalServiceException.class,
        maxAttempts = MAX_ATTEMPTS,
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5_000))
    public ResourceResponse fetchResource(String id) {
        return downstreamClient.getResource(id);
    }

    @Recover
    public ResourceResponse recover(InternalServiceException ex, String id) {
        errorMetrics.recordDownstreamError("feign:downstream-service", "RETRY_EXHAUSTED");
        throw new InternalServiceException(
            "downstream-service unavailable for resource '%s' after %d attempts".formatted(id, MAX_ATTEMPTS), ex);
    }
}
