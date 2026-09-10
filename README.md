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
- A distinct RFC 9457 `type` URI per error code once you configure a real `problemTypeBaseUri` (`about:blank` by default, per the spec, for anyone who hasn't).
- `app.errors`/`downstream.errors` Micrometer counters, if a `MeterRegistry` bean is present (otherwise you get a one-line startup log saying so, and recording becomes a no-op — see Logging below).
- `traceId` on every response and log line, if a `Tracer` bean is present (falls back to `"unavailable"` otherwise, again logged once at startup) — bring your own tracing bridge (OTEL, Zipkin, ...), the library only reads `Tracer`, it doesn't configure one.
- Sensitive-looking field/detail names (`password`, `token`, `secret`, `ssn`, ...) always redacted to `"[REDACTED]"` in responses, regardless of `includeRejectedValue` or what you put in an `AppException`'s details map.
- Every string that traces back to caller input stripped of CR/LF before it reaches a log line, so a crafted request can't forge extra log entries (CWE-117).
- If Feign is on your classpath: an `ErrorDecoder` mapping downstream HTTP failures (401/403/404/409/412/429/503/504 and generic 4xx/5xx) to the same `AppException` hierarchy, and a `RequestInterceptor` forwarding your service's current traceId downstream. The downstream response body is never embedded in the mapped exception's caller-facing message — only ever the internal log line, and only when you opt in.
- If Resilience4j's circuit-breaker module is on your classpath: a `CallNotPermittedException` (breaker open) mapped to the same `ProblemDetail` shape as everything else, instead of falling through to a generic 500.

Any of these beans can be overridden — define your own `GlobalExceptionHandler`, `ErrorDecoder`, or `RequestInterceptor` bean and the library's default backs off (`@ConditionalOnMissingBean`).

### Throwing your own errors

```java
throw ResourceNotFoundException.of("widget", id);
throw new ValidationException("orderId is required", Map.of("field", "orderId"));
```

Beyond `ValidationException`/`ResourceNotFoundException`/`InternalServiceException`, the library ships the rest of the everyday HTTP-error vocabulary so you're not stuck defining `ConflictException` in every service that needs one:

| Exception | Status | `errorCode` |
|---|---|---|
| `ValidationException` | 400 | `VALIDATION_ERROR` |
| `AuthenticationException` | 401 | `UNAUTHENTICATED` |
| `AuthorizationException` | 403 | `UNAUTHORIZED` |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `PreconditionFailedException` | 412 | `PRECONDITION_FAILED` |
| `RateLimitExceededException` | 429 | `RATE_LIMITED` |
| `InternalServiceException` | 500 | `INTERNAL_SERVICE_ERROR` |
| `ServiceUnavailableException` | 503 | `SERVICE_UNAVAILABLE` |
| `DownstreamTimeoutException` | 504 | `DOWNSTREAM_TIMEOUT` |

Every one of these has the same four constructor shapes (`message`; `message, cause`; `message, details`; `message, details, cause`), so wrapping a lower-level failure never means losing its stack trace.

`RateLimitExceededException` can additionally carry how long the caller should wait:

```java
throw RateLimitExceededException.withRetryAfter("slow down", 30); // sets the Retry-After response header
```

Add your own error codes for anything more domain-specific by implementing `ErrorCode` (an enum implementing it costs nothing extra — `name()` comes for free):

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
| `errors` | array | field-level validation failures only (`MethodArgumentNotValidException`/`ConstraintViolationException`); any field name that looks sensitive has `rejectedValue` forced to `"[REDACTED]"` |
| `stackTrace` | string | only present when `errortime.web.include-stack-trace=true` (off by default - see Security below) |

`type` is `about:blank` unless you set `errortime.web.problem-type-base-uri`, in which case every error code gets its own URI: `{base}/resource-not-found`, `{base}/rate-limited`, etc.

Treat these property names, and the `app.errors`/`downstream.errors` metric names and their `errorCode`/`status`/`source` tags, as a stable contract: renaming any of them is a breaking (MAJOR) change, since every consuming service's dashboards and alerts key off them. New error codes or new `errortime.*` properties are MINOR; a genuine bug fix is PATCH.

### Configuration (`errortime.*`)

| Property | Default | |
|---|---|---|
| `errortime.web.enabled` | `true` | |
| `errortime.web.problem-type-base-uri` | `about:blank` | base URI for every error code's problem `type` (see above); `about:blank` per RFC 9457, or your own docs base |
| `errortime.web.include-rejected-value` | `false` | whether field errors echo the submitted value back — sensitive-looking fields are redacted regardless (see `redact-sensitive-fields`) |
| `errortime.web.order` | `Ordered.LOWEST_PRECEDENCE` | so your own `@ControllerAdvice` can take precedence |
| `errortime.web.include-stack-trace` | `false` | adds a `stackTrace` property to 5xx responses — **local/dev troubleshooting only, never production**: a stack trace discloses package structure and library versions to the caller |
| `errortime.web.stack-trace-max-frames` | `10` | caps how many frames `include-stack-trace` adds |
| `errortime.web.redact-sensitive-fields` | `true` | force `"[REDACTED]"` for field/detail names matching a sensitive-data marker (password, token, secret, ssn, ...), regardless of `include-rejected-value` |
| `errortime.web.additional-redacted-field-markers` | `[]` | extra field-name substrings (case-insensitive) merged with the built-in list |
| `errortime.metrics.enabled` | `true` | |
| `errortime.tracing.unavailable-value` | `"unavailable"` | |
| `errortime.feign.enabled` | `true` | |
| `errortime.feign.trace-id-header` | `X-Trace-Id` | |
| `errortime.feign.log-response-body` | `false` | logs the downstream response body internally when a Feign call fails — off by default (PII/log-volume risk across a fleet). The body is *never* included in the caller-facing response regardless of this flag. |
| `errortime.feign.max-logged-body-chars` | `2048` | |
| `errortime.resilience.enabled` | `true` | maps Resilience4j's `CallNotPermittedException` (circuit open) to a 503 `ProblemDetail`, when resilience4j-circuitbreaker is on the classpath |
| `errortime.resilience.order` | `Ordered.HIGHEST_PRECEDENCE` | must stay ahead of `errortime.web.order` - Spring's exception resolver picks the first `@ControllerAdvice` bean (by order) with *any* matching handler, not the most specific one across beans, so this needs to be checked before `GlobalExceptionHandler`'s catch-all |

Metric names, tag keys, and `ProblemDetail` property names are **not** configurable — see the response contract above.

### Retry classification

`AppException.isRetryable()` (true only for 5xx-mapped codes) and `com.mrecoder.errortime.retry.RetryClassifier` give any retry mechanism you use (AMQP listener advice, `@Retryable`, Resilience4j, ...) a shared "is this worth retrying" answer, so you don't hand-maintain your own exclusion list that can drift from the error-code taxonomy. See `error-time-example-service`'s `RabbitRetryConfig` for a worked example — retry *policy execution* (queue/exchange topology, backoff, DLQ routing) stays entirely in your own service, since it's inherently per-service configuration the library can't safely default.

### Resilience4j circuit breakers

If `resilience4j-circuitbreaker` is on your classpath, a `CallNotPermittedException` thrown when one of your own `@CircuitBreaker`-decorated calls is rejected (breaker open) is mapped to a 503 `ServiceUnavailableException`-shaped `ProblemDetail`, counted on `downstream.errors` (source `circuitBreaker:{name}`), and logged - the same treatment as every other error this library handles, instead of falling through to a generic 500. You still own the circuit breaker itself (`resilience4j-spring-boot3`, the `@CircuitBreaker` annotation, its thresholds) - this only handles the "call was rejected" failure mode consistently. Disable with `errortime.resilience.enabled=false`.

### Logging

Every class that can silently do nothing instead of what you'd expect - no `Tracer`, no `MeterRegistry` - says so once at startup (`WARN` for "this capability is off", `INFO` for "this capability is on and here's how"), rather than leaving you to notice traceId is always `"unavailable"` by accident. Each `@AutoConfiguration` class logs an `INFO` line naming the beans it activated and the effective configuration, and per-request classes (`GlobalExceptionHandler`, `FeignErrorDecoder`, `TraceIdPropagationInterceptor`, `RetryClassifier`) log at `DEBUG` on the hot path (which exception is being resolved, which traceId is being propagated where, how a throwable was classified) so a consumer can turn on `logging.level.com.mrecoder.errortime=DEBUG` and see the whole decision trail without attaching a debugger. `logError`/`FeignErrorDecoder`'s failure log use SLF4J 2.x's fluent, structured API (`log.atWarn().addKeyValue(...)`) rather than string-interpolated fields, so a consumer running a JSON log encoder (Logstash, ECS) gets `errorCode`/`status`/`traceId` as real structured fields, not text to regex out of a message.

Everything that ends up in a log line and traces back to caller input (a request URI, a header, a downstream response) is passed through `LogSanitizer` first, stripping CR/LF so a crafted request can't forge extra log lines (CWE-117, log injection).

### Security notes

Beyond the redaction, log-sanitization, and opt-in-stack-trace behavior already covered above:

- **Downstream response bodies never reach a caller.** `FeignErrorDecoder` never embeds a downstream service's response body in the exception message that becomes `ProblemDetail.detail` - only the internal log line does, and only when `errortime.feign.log-response-body=true`. A downstream service is not a trusted input source for what a *different* caller of *your* service gets to see.
- **Nothing here needs a stack trace to be useful.** `errortime.web.include-stack-trace` exists for local troubleshooting; leaving it off (the default) costs you nothing in the normal error-handling path.
- See [`SECURITY.md`](SECURITY.md) for the vulnerability disclosure process and this project's specific threat-model notes.
- **Dependency scanning**: not run as part of every build (it needs network access to the NVD/OSS Index, which a `mvn verify` shouldn't silently depend on), but wired up and ready:
  ```
  mvn org.owasp:dependency-check-maven:check
  ```
- **Software Bill of Materials**: a CycloneDX SBOM (`target/bom.json`/`bom.xml`) is generated for the starter on every `package`/`verify` (skipped for the never-deployed example module).

## Repo layout / building

```
mvn clean verify          # builds + tests both modules
mvn -pl error-time-spring-boot-starter dependency:tree   # sanity-check the library's transitive deps
```

CI (`.github/workflows/ci.yml`) runs the same `mvn -B clean verify` on every push/PR against `main`, on a Docker-equipped runner, then generates and uploads the SBOM as a build artifact. An [`ArchitectureTest`](error-time-spring-boot-starter/src/test/java/com/mrecoder/errortime/ArchitectureTest.java) (ArchUnit) enforces the package-dependency rules the design above relies on - e.g. that `exception` stays a dependency-free domain model, and that `web` never depends on the optional `feign`/`resilience` integrations - so a well-intentioned shortcut in a future change fails the build instead of quietly rotting the design.

To run the demo service locally, install the starter into your local repo first, then run the example module standalone:

```
mvn install                     # from the repo root — builds + installs both modules
cd error-time-example-service
mvn spring-boot:run
```

(A bare plugin goal like `spring-boot:run` runs against every project Maven pulls into the reactor — `mvn -pl error-time-example-service -am spring-boot:run` from the root fails on the parent aggregator POM, which has no main class. `mvn install` then running from inside the module avoids that entirely.)

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

### Demo endpoints

`RemoteServicesDemoController` exposes one endpoint per pretend remote dependency, none of which do any exception handling themselves — every failure propagates to the library's auto-configured `GlobalExceptionHandler`, the same way it would for a real consumer. `success` (default), `not-found`, `invalid`, and `unavailable` work on every endpoint below; the rest are business-appropriate to only some of them, matching what a real version of that dependency would actually fail with.

| Endpoint | Pretend dependency | Extra `?simulate=` values |
|---|---|---|
| `GET /demo/services/database/records/{id}` | a database lookup | `conflict`, `precondition-failed` |
| `GET /demo/services/message-queue/{queueName}/next-message` | a message-queue consume | `rate-limited` |
| `GET /demo/services/ldap/users/{username}` | an LDAP directory bind | `unauthenticated`, `unauthorized` |
| `GET /demo/services/weather/{cityCode}/forecast` | a third-party weather API call | `downstream-timeout` |

```
curl http://localhost:8080/demo/services/weather/LHR/forecast
curl http://localhost:8080/demo/services/weather/LHR/forecast?simulate=unavailable
curl http://localhost:8080/demo/services/ldap/users/jdoe?simulate=unauthenticated
curl -i http://localhost:8080/demo/services/message-queue/orders/next-message?simulate=rate-limited   # -i to see Retry-After
```

The `unavailable` case (`DemoErrorCode`/`RemoteServiceUnavailableException`, 503) is this application's own error code — declared by implementing the library's `ErrorCode` interface, not something the library needed to know about in advance. Every other outcome above uses one of the library's own built-in exception types.

#### Circuit breaker demo

`GET /demo/services/circuit-breaker/status?simulate=success|unavailable` is different from the rest: it's backed by `CircuitBreakerDemoService#checkStatus`, decorated with Resilience4j's `@CircuitBreaker`. `application.yml` configures a deliberately tiny window (`resilience4j.circuitbreaker.instances.circuit-breaker-demo`: 4-call sliding window, 50% failure threshold) so you can trip it by hand:

```
curl http://localhost:8080/demo/services/circuit-breaker/status                        # 200 - closed
curl http://localhost:8080/demo/services/circuit-breaker/status?simulate=unavailable   # 503 - real failure, recorded
curl http://localhost:8080/demo/services/circuit-breaker/status?simulate=unavailable   # 503 - real failure, recorded
curl http://localhost:8080/demo/services/circuit-breaker/status                        # 200 - 4th call in the window; breaker evaluates *after* this one
curl http://localhost:8080/demo/services/circuit-breaker/status                        # 503 - breaker is now OPEN; rejected before the method even runs
```

That last response comes from `CircuitBreakerExceptionHandler`, not `CircuitBreakerDemoService` - Resilience4j throws `CallNotPermittedException` straight from its proxy once the breaker is open, and the starter maps it to the same `ProblemDetail` shape as everything else.

## Testing

`mvn clean verify` runs the full suite for both modules with no external infrastructure required: unit tests for each library class (including `LogSanitizer`/`SensitiveDataRedactor`/`ProblemDetailFactory`), an `ApplicationContextRunner`-based suite verifying the auto-configuration itself (`ErrorTimeAutoConfigurationTests` — the standard way a Spring Boot starter is tested: no `Tracer`/`MeterRegistry` present, Feign/Resilience4j absent from the classpath, consumer overrides, property toggles), an `ArchitectureTest` (ArchUnit) enforcing the package-dependency rules the design relies on, and an end-to-end `@SpringBootTest` in the example module proving the library activates with zero component scanning from a consumer-shaped application.

`RabbitRetryConfigTest` covers the AMQP retry-vs-DLQ routing end-to-end against a real broker (Testcontainers) — it's tagged to skip itself gracefully (not fail the build) when Docker isn't available locally, and runs for real in CI, where the `ubuntu-latest` runner provides Docker. The Feign retry path end-to-end against a real downstream isn't exercised the same way yet; `spring-rabbit-test` and the Testcontainers RabbitMQ module are already on the example module's test classpath as a starting point for either.

## Releasing

```
mvn versions:set -DnewVersion=1.0.0 -DprocessAllModules
git commit -am "Release 1.0.0" && git tag v1.0.0
mvn -B clean deploy                       # deploys the parent POM + the starter jar; the example module is never deployed
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT -DprocessAllModules
git commit -am "Next development version" && git push --follow-tags
```

Requires `~/.m2/settings.xml` `<server>` entries for `nexus-releases`/`nexus-snapshots` (credentials only — never in `pom.xml`), and the real Nexus URLs in place of the `REPLACE-ME` placeholders in the root `pom.xml`.
