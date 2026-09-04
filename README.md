# Error-Time

Centralized error handling for Spring Boot microservices: a shared error-code taxonomy, a global `@RestControllerAdvice` producing RFC 9457 `ProblemDetail` responses, Micrometer error metrics, and trace-ID propagation (HTTP + outbound Feign calls). Consumed as a Maven dependency by independently-deployed services, each in its own repo/container — not run on its own.

Two modules:

- **`error-time-spring-boot-starter`** — the library. Published to Nexus. Auto-configures itself the moment it's on a consumer's classpath; no `@Import`, no component scanning, no manual wiring.
- **`error-time-example-service`** — a runnable demo Spring Boot application showing how a consumer wires the library in. Deliberately lives under a different base package (`com.mrecoder.errortime.example` vs the library's `com.mrecoder.errortime`) so it behaves exactly like an unrelated microservice, not a sibling module that happens to share code. Never published.

Java 25, Spring Boot 4.1.1, Maven multi-module. The library itself targets Java 21 (`--release 21`) so it's usable from any consumer on a 21+ runtime; only the example module needs Java 25 preview features (`StructuredTaskScope`/`Joiner`, used in its demo `DownstreamService.fetchResources`).

## Using it in your service

```xml
<dependency>
  <groupId>com.mrecoder</groupId>
  <artifactId>error-time-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Resolved from Nexus — add to your `pom.xml` (or a parent/settings.xml already shared across your services):

```xml
<repositories>
  <repository>
    <id>nexus-releases</id>
    <url>https://REPLACE-ME/repository/maven-releases/</url>
  </repository>
  <repository>
    <id>nexus-snapshots</id>
    <url>https://REPLACE-ME/repository/maven-snapshots/</url>
  </repository>
</repositories>
```

That's it — adding the dependency is enough. What you get automatically:

- A `@RestControllerAdvice` mapping `AppException` subtypes, bean-validation failures, and any other exception to a `ProblemDetail` response, without swallowing responses Spring's own resolvers already handle correctly (unmapped routes, 405s, 406s, malformed bodies, ...).
- `app.errors`/`downstream.errors` Micrometer counters, if a `MeterRegistry` bean is present (silently inert otherwise).
- `traceId` on every response and log line, if a `Tracer` bean is present (falls back to `"unavailable"` otherwise) — bring your own tracing bridge (OTEL, Zipkin, ...), the library only reads `Tracer`, it doesn't configure one.
- If Feign is on your classpath: an `ErrorDecoder` mapping downstream HTTP failures to the same `AppException` hierarchy, and a `RequestInterceptor` forwarding your service's current traceId downstream.

Any of these beans can be overridden — define your own `GlobalExceptionHandler`, `ErrorDecoder`, or `RequestInterceptor` bean and the library's default backs off (`@ConditionalOnMissingBean`).

### Throwing your own errors

```java
throw ResourceNotFoundException.of("widget", id);
throw new ValidationException("orderId is required", Map.of("field", "orderId"));
```

Add your own error codes by implementing `ErrorCode` (an enum implementing it costs nothing extra — `name()` comes for free):

```java
public enum OrderErrorCode implements ErrorCode {
    PAYMENT_DECLINED(HttpStatus.PAYMENT_REQUIRED);
    // ...
}
```

### The response contract

Every `ProblemDetail` this library produces carries, beyond the RFC 9457 standard fields (`type`, `title`, `status`, `detail`, `instance`):

| Property | Type | Notes |
|---|---|---|
| `errorCode` | string | e.g. `"RESOURCE_NOT_FOUND"` — the `ErrorCode.name()` |
| `timestamp` | ISO-8601 string | |
| `traceId` | string | `"unavailable"` if no tracer configured |
| `errors` | array | field-level validation failures only (`MethodArgumentNotValidException`/`ConstraintViolationException`) |

Treat these property names, and the `app.errors`/`downstream.errors` metric names and their `errorCode`/`status`/`source` tags, as a stable contract: renaming any of them is a breaking (MAJOR) change, since every consuming service's dashboards and alerts key off them. New error codes or new `errortime.*` properties are MINOR; a genuine bug fix is PATCH.

### Configuration (`errortime.*`)

| Property | Default | |
|---|---|---|
| `errortime.web.enabled` | `true` | |
| `errortime.web.problem-type-base-uri` | `about:blank` | base URI for the validation-error problem `type`; `about:blank` per RFC 9457, or your own docs base with `/validation-error` appended |
| `errortime.web.include-rejected-value` | `false` | whether field errors echo the submitted value back — leave off for PII/credential fields |
| `errortime.web.order` | `Ordered.LOWEST_PRECEDENCE` | so your own `@ControllerAdvice` can take precedence |
| `errortime.metrics.enabled` | `true` | |
| `errortime.tracing.unavailable-value` | `"unavailable"` | |
| `errortime.feign.enabled` | `true` | |
| `errortime.feign.trace-id-header` | `X-Trace-Id` | |
| `errortime.feign.log-response-body` | `false` | logs the full downstream response body at ERROR when a Feign call fails — off by default (PII/log-volume risk across a fleet) |
| `errortime.feign.max-logged-body-chars` | `2048` | |

Metric names, tag keys, and `ProblemDetail` property names are **not** configurable — see the response contract above.

### Retry classification

`AppException.isRetryable()` (true only for 5xx-mapped codes) and `com.mrecoder.errortime.retry.RetryClassifier` give any retry mechanism you use (AMQP listener advice, `@Retryable`, Resilience4j, ...) a shared "is this worth retrying" answer, so you don't hand-maintain your own exclusion list that can drift from the error-code taxonomy. See `error-time-example-service`'s `RabbitRetryConfig` for a worked example — retry *policy execution* (queue/exchange topology, backoff, DLQ routing) stays entirely in your own service, since it's inherently per-service configuration the library can't safely default.

## Repo layout / building

```
mvn clean verify          # builds + tests both modules
mvn -pl error-time-spring-boot-starter dependency:tree   # sanity-check the library's transitive deps
mvn -pl error-time-example-service spring-boot:run        # run the demo service locally
```

The example service starts and serves HTTP even without RabbitMQ or an OTLP collector running. For full end-to-end behavior:

- A RabbitMQ broker on `localhost:5672` (`RABBITMQ_HOST`/`PORT`/`USERNAME`/`PASSWORD` env vars to override)
- An OTLP collector on `localhost:4318` (`OTEL_EXPORTER_OTLP_ENDPOINT` to override)
- A downstream HTTP service matching `DownstreamClient` (`DOWNSTREAM_SERVICE_URL` to override)

A suggested logging pattern that surfaces `traceId`/`spanId` on every line (lifted from the example module's `application.yml`):

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]"
```

## Testing

`mvn clean verify` runs the full suite for both modules with no external infrastructure: unit tests for each library class, an `ApplicationContextRunner`-based suite verifying the auto-configuration itself (`ErrorTimeAutoConfigurationTests` — the standard way a Spring Boot starter is tested: no `Tracer`/`MeterRegistry` present, Feign absent from the classpath, consumer overrides, property toggles), and an end-to-end `@SpringBootTest` in the example module proving the library activates with zero component scanning from a consumer-shaped application.

Testing the AMQP retry-vs-DLQ routing and the Feign retry path end-to-end against a real broker/downstream would need Testcontainers (Docker) — not exercised here, but `spring-rabbit-test` is already on the example module's test classpath for that if added later.

## Releasing

```
mvn versions:set -DnewVersion=1.0.0 -DprocessAllModules
git commit -am "Release 1.0.0" && git tag v1.0.0
mvn -B clean deploy                       # deploys the parent POM + the starter jar; the example module is never deployed
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT -DprocessAllModules
git commit -am "Next development version" && git push --follow-tags
```

Requires `~/.m2/settings.xml` `<server>` entries for `nexus-releases`/`nexus-snapshots` (credentials only — never in `pom.xml`), and the real Nexus URLs in place of the `REPLACE-ME` placeholders in the root `pom.xml`.
