package com.mrecoder.errortime.amqp;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example listener for subtask 7. The retry-vs-DLQ decision itself lives in
 * {@link RabbitRetryConfig} (it inspects the exception type); this class's
 * job is to fail *consistently* - always as an {@link AppException} subtype,
 * always logged with the current traceId, always counted - so that policy
 * has something reliable to classify.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private static final String SOURCE = "amqp:" + RabbitTopologyConfig.QUEUE;

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;

    public OrderEventListener(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
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
        // Real order processing would happen here; this is a scaffold for the error-handling paths.
    }

    private void recordFailure(OrderEvent event, AppException ex) {
        log.error("AMQP listener failure errorCode={} orderId={} traceId={} message={}",
            ex.getErrorCode(), event.orderId(), traceIdProvider.currentTraceId(), ex.getMessage(), ex);
        errorMetrics.recordDownstreamError(SOURCE, ex.getErrorCode());
    }
}
