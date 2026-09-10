package com.mrecoder.errortime.retry;

import com.mrecoder.errortime.exception.AppException;
import lombok.extern.slf4j.Slf4j;

/**
 * Transport-agnostic "is this worth retrying" policy, so AMQP listeners,
 * Feign clients, and anything else that retries can share one answer instead
 * of each hand-maintaining its own exclusion list that can drift out of sync
 * with {@link AppException#isRetryable()}. A throwable that isn't an
 * {@link AppException} (a bug, not a classified failure) is treated as
 * retryable by default - there's no basis to assume otherwise.
 */
@Slf4j
public final class RetryClassifier {

    private RetryClassifier() {
    }

    public static boolean isRetryable(Throwable t) {
        boolean retryable = !(t instanceof AppException app) || app.isRetryable();
        log.debug("Classified {} as {}", t.getClass().getSimpleName(), retryable ? "retryable" : "not retryable");
        return retryable;
    }
}
