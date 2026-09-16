package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.RequestTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdPropagationInterceptorTest {

    @Test
    void appliesTraceIdToTheDefaultHeaderWhenNoneIsConfigured() {
        TraceIdPropagationInterceptor interceptor = new TraceIdPropagationInterceptor(traceIdProvider("trace-1"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(TraceIdPropagationInterceptor.DEFAULT_TRACE_ID_HEADER))
            .containsExactly("trace-1");
    }

    @Test
    void appliesTraceIdToACustomHeaderWhenConfigured() {
        TraceIdPropagationInterceptor interceptor =
            new TraceIdPropagationInterceptor(traceIdProvider("trace-2"), "X-Correlation-Id");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Correlation-Id")).containsExactly("trace-2");
    }

    private static TraceIdProvider traceIdProvider(String traceId) {
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(traceId);
        Span span = mock(Span.class);
        when(span.context()).thenReturn(context);
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(span);
        return new TraceIdProvider(tracer);
    }
}
