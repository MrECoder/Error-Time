package com.mrecoder.errortime.example.amqp;

import com.mrecoder.errortime.example.feign.DownstreamService;
import com.mrecoder.errortime.example.feign.ResourceResponse;
import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Example listener. The retry-vs-DLQ decision itself lives in
 * {@link RabbitRetryConfig} (it classifies via the library's
 * {@code RetryClassifier}); this class's job is to fail *consistently* -
 * always as an {@link AppException} subtype, always logged with the current
 * traceId, always counted - so that policy has something reliable to classify.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private static final String SOURCE = "amqp:" + RabbitTopologyConfig.QUEUE;

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;
    private final DownstreamService downstreamService;

    public OrderEventListener(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            DownstreamService downstreamService) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
        this.downstreamService = downstreamService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void handle(OrderEvent event) {
        try {
            process(event);
        } catch (AppException ex) {
            recordFailure(event, ex);
            throw ex;
        } catch (Exception ex) {
            InternalServiceException wrapped =
                new InternalServiceException("Unexpected failure processing order event " + event.orderId(), ex);
            recordFailure(event, wrapped);
            throw wrapped;
        }
    }

    private void process(OrderEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new ValidationException("orderId is required", Map.of("payload", String.valueOf(event.payload())));
        }
        List<String> resourceIds = event.resourceIds() == null ? List.of() : event.resourceIds();
        if (!resourceIds.isEmpty()) {
            List<ResourceResponse> resources = downstreamService.fetchResources(resourceIds);
            log.info("orderId={} resolved {} downstream resource(s) concurrently traceId={}",
                event.orderId(), resources.size(), traceIdProvider.currentTraceId());
        }
        // Further order processing (persistence, follow-up events) would happen here;
        // this stays a scaffold for the error-handling paths.
    }

    private void recordFailure(OrderEvent event, AppException ex) {
        log.error("AMQP listener failure errorCode={} orderId={} traceId={} message={}",
            ex.getErrorCode(), event.orderId(), traceIdProvider.currentTraceId(), ex.getMessage(), ex);
        errorMetrics.recordDownstreamError(SOURCE, ex.getErrorCode());
    }
}
