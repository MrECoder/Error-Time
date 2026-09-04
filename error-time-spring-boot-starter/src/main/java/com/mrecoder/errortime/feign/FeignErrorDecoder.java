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
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Maps any Feign client failure to the {@code AppException} hierarchy, so
 * {@code GlobalExceptionHandler} doesn't need to know or care that a given
 * 404/500 originated from a downstream HTTP call rather than this service's
 * own logic. Status is classified, not matched status-by-status: only 5xx
 * (and any status this JVM doesn't recognize) becomes an
 * {@link InternalServiceException} - worth retrying, since it may be
 * transient. Every other 4xx becomes non-retryable, same as 400/404, so a
 * 401/403/409/429 from downstream is never retried by a caller like
 * {@code DownstreamService}'s {@code @Retryable}.
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(FeignErrorDecoder.class);
    private static final String SOURCE_PREFIX = "feign:";
    private static final String DETAIL_STATUS = "status";

    private final TraceIdProvider traceIdProvider;
    private final ErrorMetrics errorMetrics;
    private final boolean logResponseBody;
    private final int maxLoggedBodyChars;
    private final ErrorDecoder.Default fallback = new ErrorDecoder.Default();

    public FeignErrorDecoder(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics) {
        this(traceIdProvider, errorMetrics, false, 2048);
    }

    public FeignErrorDecoder(TraceIdProvider traceIdProvider, ErrorMetrics errorMetrics,
            boolean logResponseBody, int maxLoggedBodyChars) {
        this.traceIdProvider = traceIdProvider;
        this.errorMetrics = errorMetrics;
        this.logResponseBody = logResponseBody;
        this.maxLoggedBodyChars = maxLoggedBodyChars;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = truncate(readBody(response), maxLoggedBodyChars);
        String traceId = traceIdProvider.currentTraceId();
        int status = response.status();
        HttpStatus resolvedStatus = HttpStatus.resolve(status);

        AppException mapped = switch (resolvedStatus) {
            case BAD_REQUEST -> new ValidationException(
                "Downstream call %s rejected the request: %s".formatted(methodKey, body));
            case NOT_FOUND -> ResourceNotFoundException.of(methodKey, extractRequestSummary(response));
            case HttpStatus s when s.is4xxClientError() -> new ValidationException(
                "Downstream call %s rejected the request with status %d: %s".formatted(methodKey, status, body));
            case null, default -> new InternalServiceException(
                "Downstream call %s failed with status %d: %s".formatted(methodKey, status, body),
                Map.of(DETAIL_STATUS, status), null);
        };

        if (logResponseBody) {
            log.error("Feign call failed method={} status={} traceId={} body={}", methodKey, status, traceId, body);
        } else {
            log.error("Feign call failed method={} status={} traceId={}", methodKey, status, traceId);
        }
        errorMetrics.recordDownstreamError(SOURCE_PREFIX + methodKey, mapped.getErrorCode());

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

    private static String truncate(String s, int maxChars) {
        if (s == null || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "...(truncated)";
    }
}
