package com.mrecoder.errortime.retry;

import com.mrecoder.errortime.exception.AppException;

/**
 * Transport-agnostic "is this worth retrying" policy, so AMQP listeners,
 * Feign clients, and anything else that retries can share one answer instead
 * of each hand-maintaining its own exclusion list that can drift out of sync
 * with {@link AppException#isRetryable()}. A throwable that isn't an
 * {@link AppException} (a bug, not a classified failure) is treated as
 * retryable by default - there's no basis to assume otherwise.
 */
public final class RetryClassifier {

    private RetryClassifier() {
    }

    public static boolean isRetryable(Throwable t) {
        return !(t instanceof AppException app) || app.isRetryable();
    }
}
