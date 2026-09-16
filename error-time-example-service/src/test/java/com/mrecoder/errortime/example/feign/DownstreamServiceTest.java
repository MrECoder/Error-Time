package com.mrecoder.errortime.example.feign;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit coverage of {@link DownstreamService}'s plain method bodies -
 * {@code @Retryable}/{@code @Recover} themselves are Spring Retry's own
 * responsibility to get right, not this project's; what belongs here is the
 * concurrent {@link DownstreamService#fetchResources} orchestration (empty
 * input, all-success, and one-failure-cancels-the-batch) and
 * {@link DownstreamService#recover}'s bookkeeping.
 *
 * <p>{@code self} is a second, independent {@link DownstreamService} wrapping
 * the same mock client rather than a real Spring proxy of the instance under
 * test - {@code fetchResources} only ever calls {@code self.fetchResource},
 * so a plain second instance behaves identically here without needing to
 * stand up an application context for {@code @Lazy} self-injection.
 */
class DownstreamServiceTest {

    private final DownstreamClient client = mock(DownstreamClient.class);
    private final ErrorMetrics errorMetrics = new ErrorMetrics(new SimpleMeterRegistry());
    private final DownstreamService self = new DownstreamService(client, errorMetrics, null);
    private final DownstreamService service = new DownstreamService(client, errorMetrics, self);

    @Test
    void fetchResourceDelegatesDirectlyToTheClient() {
        when(client.getResource("42")).thenReturn(new ResourceResponse("42", "widget"));

        assertThat(service.fetchResource("42")).isEqualTo(new ResourceResponse("42", "widget"));
    }

    @Test
    void recoverRecordsRetryExhaustedAndThrowsInternalServiceException() {
        InternalServiceException exhausted = new InternalServiceException("downstream unavailable");

        assertThatThrownBy(() -> service.recover(exhausted, "42"))
            .isInstanceOf(InternalServiceException.class)
            .hasMessageContaining("42")
            .hasMessageContaining("3 attempts");
    }

    @Test
    void fetchResourcesReturnsEmptyListWithoutCallingTheClient() {
        assertThat(service.fetchResources(List.of())).isEmpty();
    }

    @Test
    void fetchResourcesFetchesEveryIdConcurrentlyAndReturnsAllResponses() {
        when(client.getResource("1")).thenReturn(new ResourceResponse("1", "one"));
        when(client.getResource("2")).thenReturn(new ResourceResponse("2", "two"));
        when(client.getResource("3")).thenReturn(new ResourceResponse("3", "three"));

        List<ResourceResponse> resources = service.fetchResources(List.of("1", "2", "3"));

        assertThat(resources).containsExactlyInAnyOrder(
            new ResourceResponse("1", "one"), new ResourceResponse("2", "two"), new ResourceResponse("3", "three"));
    }

    @Test
    void fetchResourcesCancelsTheWholeBatchAndRethrowsWhenOneIdFails() {
        when(client.getResource(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if (id.equals("bad")) {
                throw new ValidationException("id 'bad' rejected");
            }
            return new ResourceResponse(id, "ok");
        });

        assertThatThrownBy(() -> service.fetchResources(List.of("good", "bad")))
            .isInstanceOf(AppException.class);
    }
}
