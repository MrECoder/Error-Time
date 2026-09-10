package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Forwards this service's current traceId to downstream calls, so the same
 * identifier ties together this service's logs, the downstream service's
 * logs, and {@code FeignErrorDecoder}'s failure logs for one request.
 */
@Slf4j
public class TraceIdPropagationInterceptor implements RequestInterceptor {

    public static final String DEFAULT_TRACE_ID_HEADER = "X-Trace-Id";

    private final TraceIdProvider traceIdProvider;
    private final String traceIdHeader;

    public TraceIdPropagationInterceptor(TraceIdProvider traceIdProvider) {
        this(traceIdProvider, DEFAULT_TRACE_ID_HEADER);
    }

    public TraceIdPropagationInterceptor(TraceIdProvider traceIdProvider, String traceIdHeader) {
        this.traceIdProvider = traceIdProvider;
        this.traceIdHeader = traceIdHeader;
    }

    @Override
    public void apply(RequestTemplate template) {
        String traceId = traceIdProvider.currentTraceId();
        log.debug("Propagating traceId={} via header {} to {}", traceId, traceIdHeader, template.url());
        template.header(traceIdHeader, traceId);
    }
}
