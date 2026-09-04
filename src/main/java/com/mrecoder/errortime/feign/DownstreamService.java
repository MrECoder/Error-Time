package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.ErrorCode;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

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
    private static final String SOURCE = "feign:" + DownstreamClient.CLIENT_NAME;

    private final DownstreamClient downstreamClient;
    private final ErrorMetrics errorMetrics;
    private final DownstreamService self;

    public DownstreamService(DownstreamClient downstreamClient, ErrorMetrics errorMetrics,
            @Lazy DownstreamService self) {
        this.downstreamClient = downstreamClient;
        this.errorMetrics = errorMetrics;
        this.self = self;
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
        errorMetrics.recordDownstreamError(SOURCE, ErrorCode.RETRY_EXHAUSTED);
        throw new InternalServiceException(
            "%s unavailable for resource '%s' after %d attempts".formatted(DownstreamClient.CLIENT_NAME, id, MAX_ATTEMPTS), ex);
    }

    /**
     * Fetches several resources concurrently instead of looping over
     * {@link #fetchResource}: each id gets its own virtual thread inside a
     * structured scope, so N downstream round-trips cost roughly the latency
     * of the slowest one instead of their sum. Calls go through {@code self}
     * (not {@code this}) so each one still passes through the Spring proxy
     * and keeps its {@code @Retryable} behavior - a direct {@code this} call
     * here would silently skip retries. All-or-nothing: if any id ultimately
     * fails (retries exhausted), the whole batch is cancelled and that
     * failure is rethrown rather than returning partial results.
     */
    public List<ResourceResponse> fetchResources(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        try (var scope = StructuredTaskScope.open(Joiner.<ResourceResponse>allSuccessfulOrThrow())) {
            ids.forEach(id -> scope.fork(() -> self.fetchResource(id)));
            return scope.join().map(Subtask::get).toList();
        } catch (StructuredTaskScope.FailedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AppException appEx) {
                throw appEx;
            }
            throw new InternalServiceException("Concurrent downstream fetch failed for ids " + ids, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServiceException("Concurrent downstream fetch interrupted for ids " + ids, e);
        }
    }
}
