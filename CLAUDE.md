# Error-Time — Security Constraints

Source: OWASP Secure Coding Practices Quick Reference Guide v2.1
(Authentication, Session Management, and Error Handling/Logging sections).
Rule IDs cited for traceability.

## Authentication
- [AUTH-08] Store passwords only as cryptographically strong, salted,
  one-way hashes (Argon2id, bcrypt, or scrypt). Never MD5, SHA-1, or plaintext.
- [AUTH-11] Authentication failures must return an identical, generic error
  for both an unknown username and a wrong password. Never indicate which
  part of the credentials was incorrect.
- [AUTH-18] Throttle or lock out after repeated failed login attempts to
  limit brute-force attacks.

## Session Management
- [SESS-01] Use the framework's session management mechanisms — do not
  implement custom session ID generation.
- [SESS-02] Generate session identifiers only on the server; never accept
  one supplied by the client.
- [SESS-03] Use a cryptographically strong random session identifier.
- [SESS-04] Regenerate the session ID on every successful authentication,
  to prevent session fixation.
- [SESS-10] Mark session cookies `Secure`.
- [SESS-11] Mark session cookies `HttpOnly`.
- [SESS-18] Use per-session CSRF tokens on state-changing requests once
  session-based auth is in place.

## Error Handling & Logging
- [ERR-17] Log all authentication attempts, both successes and failures,
  with user identifier, source IP, and outcome.
- [ERR-13] Never log secrets, passwords, session tokens, or other sensitive
  payloads — redact or mask sensitive fields, and avoid data structures
  (e.g. auto-generated toString()) that could leak them into logs by accident.
- [ERR-LOCAL-01] (CWE-117, project-specific) Any string that ultimately
  traces back to a source outside this JVM — an HTTP request, a header, a
  downstream HTTP response, **or an inbound AMQP message payload** — must go
  through `LogSanitizer.sanitize()` before it reaches a log line, at every
  log level (including `debug`/`trace`), not only in HTTP-facing code. This
  applies across module/package boundaries: e.g. a Feign `RequestInterceptor`
  building a URL from caller-controlled path variables is just as much an
  external-input boundary as a `@RestControllerAdvice`. Internally-generated
  values (a `Tracer`-issued traceId, an enum name, a fixed literal) don't
  need it.

### Findings log
- 2026-09-16: `OrderEventListener` (example service) logged the AMQP
  `OrderEvent.orderId()` and a message built from it without sanitizing,
  even though `orderId` comes from a queue message an external publisher
  controls — a CRLF in `orderId` could forge fake log lines (CWE-117).
  Fixed by wrapping every log site with `LogSanitizer.sanitize()`.
- 2026-09-16: `TraceIdPropagationInterceptor` (starter's Feign integration)
  logged the outbound `RequestTemplate.url()` at `debug` unsanitized. That
  URL can embed a caller-controlled path variable (e.g. an id sourced from
  an AMQP message via `DownstreamService.fetchResources`), so the same
  CWE-117 log-forging risk applied one hop further downstream than the
  first finding. Fixed the same way; see [ERR-LOCAL-01] above — this is why
  the rule calls out debug-level and cross-module log sites explicitly.
