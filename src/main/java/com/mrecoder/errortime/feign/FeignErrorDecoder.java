package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Maps any Feign client failure to the same {@code AppException} hierarchy
 * the rest of the app uses (subtask 5), so {@code GlobalExceptionHandler}
 * doesn't need to know or care that a given 404/500 originated from a
 * downstream HTTP call rather than this service's own logic.
 */
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;
    private final ErrorDecoder.Default fallback = new ErrorDecoder.Default();

    public FeignErrorDecoder(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        String traceId = traceIdProvider.currentTraceId();
        int status = response.status();

        AppException mapped = switch (status) {
            case 400 -> new ValidationException(
                "Downstream call %s rejected the request: %s".formatted(methodKey, body));
            case 404 -> ResourceNotFoundException.of(methodKey, extractRequestSummary(response));
            default -> new InternalServiceException(
                "Downstream call %s failed with status %d: %s".formatted(methodKey, status, body),
                Map.of("status", status), null);
        };

        log.error("Feign call failed method={} status={} traceId={} body={}", methodKey, status, traceId, body);
        errorMetrics.recordDownstreamError("feign:" + methodKey, mapped.getErrorCode());

        return mapped;
    }

    private String extractRequestSummary(Response response) {
        return response.request() != null ? response.request().url() : "unknown";
    }

    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (InputStream body = response.body().asInputStream()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fallback.decode("body-unreadable", response).getMessage();
        }
    }
}
