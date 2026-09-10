# Security Policy

## Supported versions

This project has not yet reached a `1.0.0` release; the `1.0.0-SNAPSHOT` line
on `main` is the only version receiving fixes.

| Version | Supported |
|---|---|
| `main` (unreleased) | Yes |

Once `1.0.0` ships, this table will track the latest MAJOR.MINOR line(s)
under active support.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for a suspected security
vulnerability. Instead, report it privately by emailing
**security@REPLACE-ME.example.com** (replace with a real monitored address
before the first release) with:

- A description of the vulnerability and its potential impact.
- Steps to reproduce, or a proof-of-concept if you have one.
- The affected version(s)/commit.

You should receive an acknowledgment within **5 business days**. We aim to
provide an initial assessment (confirmed / not applicable / needs more
information) within **10 business days**, and to ship a fix or mitigation
according to severity:

| Severity | Target time to fix |
|---|---|
| Critical (remote code execution, credential/secret leak) | 7 days |
| High (auth bypass, significant data exposure) | 30 days |
| Medium/Low | Next scheduled release |

We'll credit reporters in the release notes unless you ask to stay
anonymous.

## Scope notes specific to this library

`error-time-spring-boot-starter` is a dependency embedded into other
services' request-handling paths, so a few classes of finding are
particularly relevant here:

- **Information disclosure via error responses** - anything that could leak
  request data, stack traces, internal hostnames, or downstream response
  bodies into a `ProblemDetail` a caller can see. See
  `errortime.web.include-stack-trace` and `errortime.web.include-rejected-value`
  (both off by default) and `SensitiveDataRedactor` for the controls already
  in place; a bypass of either is a valid report.
- **Log injection (CWE-117)** - any caller-influenced string that reaches a
  log line unsanitized. See `LogSanitizer`.
- **Dependency vulnerabilities** - see `mvn org.owasp:dependency-check-maven:check`
  in the README for how this repo scans its own dependency tree; a
  vulnerability in a *direct* dependency this library forces onto every
  consumer is treated as higher severity than one in a test-scoped or
  optional dependency.
