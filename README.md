# Error-Time

Centralized error handling, tracing, retry, and observability for a Spring Boot service, built around the 10 subtasks below. Java 25, Spring Boot 4.1.1, Maven.

## Subtasks

1. **Core Exception Model** — `AppException` base class plus `ValidationException`, `ResourceNotFoundException`, `InternalServiceException`. See `exception/`.
2. **Global Exception Handler** — `@RestControllerAdvice` (`web/GlobalExceptionHandler`) handling `AppException` and generic `Exception`, returning RFC 9457 `ProblemDetail`.
3. **Validation Handling** — `MethodArgumentNotValidException` / `ConstraintViolationException` handled in the same class, with field-level error aggregation (`web/FieldErrorDetail`).
4. **Distributed Tracing** — OpenTelemetry via `spring-boot-starter-opentelemetry`; traceId injected into every `ProblemDetail` response and into the log pattern (`tracing/TraceIdProvider`).
5. **Feign Error Mapping** — `feign/FeignErrorDecoder` maps downstream HTTP errors to the `AppException` hierarchy; `feign/TraceIdPropagationInterceptor` forwards the current traceId downstream.
6. **Retry Mechanism** — `feign/DownstreamService` retries transient (`InternalServiceException`) downstream failures with `@Retryable`/backoff, recovering via `@Recover`.
7. **AMQP Error Handling** — `amqp/OrderEventListener` fails consistently as `AppException`; `amqp/RabbitRetryConfig` retries transient failures and routes permanent ones (bad input, missing resource) straight to the dead-letter queue.
8. **Metrics Integration** — `metrics/ErrorMetrics` exposes `app.errors` and `downstream.errors` Micrometer counters, scraped via `spring-boot-starter-actuator` + `micrometer-registry-prometheus` (`/actuator/prometheus`).
9. **Logging Improvements** — log pattern includes `traceId`/`spanId`; all error paths log once, centrally, in `GlobalExceptionHandler`/the listener/the decoder.
10. **Integration Testing** — `src/test/java`: web-layer tests for the exception handler (status codes, traceId, metrics), unit tests for the Feign decoder and AMQP listener, plus metrics tests.

## Running locally

```
mvn spring-boot:run
```

The app starts and serves HTTP even without RabbitMQ or an OTLP collector running — it logs connection warnings and keeps retrying in the background rather than failing to boot. For full end-to-end behavior (AMQP retry/DLQ, real trace export), run:

- A RabbitMQ broker on `localhost:5672` (`RABBITMQ_HOST`/`PORT`/`USERNAME`/`PASSWORD` env vars to override)
- An OTLP collector on `localhost:4318` (`OTEL_EXPORTER_OTLP_ENDPOINT` to override)
- A downstream HTTP service matching `DownstreamClient` (`DOWNSTREAM_SERVICE_URL` to override)

## Testing

```
mvn test
```

All 10 tests run without any external infrastructure. Testing the AMQP retry-vs-DLQ routing and the Feign retry path end-to-end against a real broker/downstream would need Testcontainers (Docker) — not exercised here, but `spring-rabbit-test` is already on the test classpath for that if added later.
