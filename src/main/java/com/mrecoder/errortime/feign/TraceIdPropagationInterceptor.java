package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards this service's current traceId to downstream calls (subtask 5),
 * so the same identifier ties together this service's logs, the downstream
 * service's logs, and {@code FeignErrorDecoder}'s failure logs for one request.
 */
@Component
public class TraceIdPropagationInterceptor implements RequestInterceptor {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final TraceIdProvider traceIdProvider;

    public TraceIdPropagationInterceptor(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(TRACE_ID_HEADER, traceIdProvider.currentTraceId());
    }
}
