package com.mrecoder.errortime.feign;

import com.mrecoder.errortime.exception.AppException;
import com.mrecoder.errortime.exception.AuthenticationException;
import com.mrecoder.errortime.exception.AuthorizationException;
import com.mrecoder.errortime.exception.ConflictException;
import com.mrecoder.errortime.exception.DownstreamTimeoutException;
import com.mrecoder.errortime.exception.InternalServiceException;
import com.mrecoder.errortime.exception.PreconditionFailedException;
import com.mrecoder.errortime.exception.RateLimitExceededException;
import com.mrecoder.errortime.exception.ResourceNotFoundException;
import com.mrecoder.errortime.exception.ServiceUnavailableException;
import com.mrecoder.errortime.exception.ValidationException;
import com.mrecoder.errortime.metrics.ErrorMetrics;
import com.mrecoder.errortime.support.LogSanitizer;
import com.mrecoder.errortime.tracing.TraceIdProvider;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Maps any Feign client failure to the {@code AppException} hierarchy, so
 * {@code GlobalExceptionHandler} doesn't need to know or care that a given
 * 404/500 originated from a downstream HTTP call rather than this service's
 * own logic. Every mapped status gets its own {@link AppException} subtype
 * (401/403/404/409/412/429/503/504 and generic 4xx/5xx) rather than lumping
 * everything non-4xx into {@link InternalServiceException}, so a consumer
 * gets the same specificity from a downstream failure as from one of its own.
 *
 * <p>The downstream response body is deliberately never embedded in the
 * mapped exception's message - that message becomes the caller-facing
 * {@code ProblemDetail.detail}, and a downstream service is not a trusted
 * input source; only the internal log line (behind
 * {@code errortime.feign.log-response-body}, off by default) ever sees it,
 * and even then it's read from the wire at all only when that flag is on.
 */
@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    private static final String SOURCE_PREFIX = "feign:";
    private static final String DETAIL_STATUS = "status";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

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
        String traceId = traceIdProvider.currentTraceId();
        int status = response.status();
        HttpStatus resolvedStatus = HttpStatus.resolve(status);
        String safeMethodKey = LogSanitizer.sanitize(methodKey);
        log.debug("Decoding Feign error method={} status={} traceId={}", safeMethodKey, status, traceId);

        AppException mapped = switch (resolvedStatus) {
            case BAD_REQUEST -> new ValidationException(
                "Downstream call %s rejected the request".formatted(methodKey));
            case UNAUTHORIZED -> new AuthenticationException(
                "Downstream call %s was not authenticated".formatted(methodKey));
            case FORBIDDEN -> new AuthorizationException(
                "Downstream call %s was not authorized".formatted(methodKey));
            case NOT_FOUND -> ResourceNotFoundException.of(methodKey, extractRequestSummary(response));
            case CONFLICT -> new ConflictException(
                "Downstream call %s conflicted with the current state of the resource".formatted(methodKey));
            case PRECONDITION_FAILED -> new PreconditionFailedException(
                "Downstream call %s failed a precondition".formatted(methodKey));
            case TOO_MANY_REQUESTS -> rateLimitException(methodKey, response);
            case SERVICE_UNAVAILABLE -> ServiceUnavailableException.of(methodKey);
            case GATEWAY_TIMEOUT, REQUEST_TIMEOUT -> new DownstreamTimeoutException(
                "Downstream call %s timed out".formatted(methodKey));
            case HttpStatus s when s.is4xxClientError() -> new ValidationException(
                "Downstream call %s rejected the request with status %d".formatted(methodKey, status));
            case null, default -> new InternalServiceException(
                "Downstream call %s failed with status %d".formatted(methodKey, status),
                Map.of(DETAIL_STATUS, status), null);
        };

        logFailure(safeMethodKey, status, traceId, mapped, response);
        errorMetrics.recordDownstreamError(SOURCE_PREFIX + methodKey, mapped.getErrorCode());

        return mapped;
    }

    private void logFailure(String safeMethodKey, int status, String traceId, AppException mapped, Response response) {
        var event = mapped.getStatus().is5xxServerError() ? log.atError() : log.atWarn();
        event.addKeyValue("errorCode", mapped.getErrorCode().name())
            .addKeyValue("status", status)
            .addKeyValue("traceId", traceId)
            .addKeyValue("method", safeMethodKey);
        if (logResponseBody) {
            event.addKeyValue("body", LogSanitizer.sanitize(truncate(readBody(response), maxLoggedBodyChars)));
        }
        event.log("Feign call failed method={} status={} traceId={}", safeMethodKey, status, traceId);
    }

    private RateLimitExceededException rateLimitException(String methodKey, Response response) {
        String message = "Downstream call %s was rate limited".formatted(methodKey);
        return firstHeaderValue(response, RETRY_AFTER_HEADER)
            .flatMap(FeignErrorDecoder::parseSeconds)
            .map(seconds -> RateLimitExceededException.withRetryAfter(message, seconds))
            .orElseGet(() -> new RateLimitExceededException(message));
    }

    private static Optional<Long> parseSeconds(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> firstHeaderValue(Response response, String headerName) {
        return response.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
            .flatMap(entry -> entry.getValue().stream())
            .findFirst();
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
