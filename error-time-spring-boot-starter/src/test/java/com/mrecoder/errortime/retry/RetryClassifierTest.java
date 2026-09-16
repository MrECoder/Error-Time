package com.mrecoder.errortime.retry;

import com.mrecoder.errortime.exception.ConflictException;
import com.mrecoder.errortime.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryClassifierTest {

    @Test
    void appExceptionWithFiveXxStatusIsRetryable() {
        assertThat(RetryClassifier.isRetryable(new ServiceUnavailableException("down"))).isTrue();
    }

    @Test
    void appExceptionWithFourXxStatusIsNotRetryable() {
        assertThat(RetryClassifier.isRetryable(new ConflictException("already exists"))).isFalse();
    }

    @Test
    void unclassifiedThrowableIsRetryableByDefault() {
        assertThat(RetryClassifier.isRetryable(new IllegalStateException("bug, not a classified failure"))).isTrue();
    }
}
