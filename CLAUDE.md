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
