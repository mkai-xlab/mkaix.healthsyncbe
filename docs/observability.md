# Observability

[Back to Documentation Index](README.md) | Previous: [Database Migration](database-migration.md) | Next: [Runbook](runbook.md)

This document defines expectations for logging, health checks, and troubleshooting visibility.

## Current Status

The project does not currently include Spring Boot Actuator or custom observability configuration.

## Logging

Recommended logging rules:

- Log application startup and shutdown.
- Log unexpected exceptions.
- Log important business events at `INFO` level.
- Log detailed troubleshooting information at `DEBUG` level.
- Never log secrets or tokens.

## Log Format

Recommended fields for production logs:

- timestamp
- level
- logger name
- request id or correlation id
- user id when safe and available
- message
- exception stack trace for unexpected errors

## Health Checks

When production deployment is added, expose health checks with Spring Boot Actuator.

Recommended endpoints:

```text
/actuator/health
/actuator/info
```

## Metrics

Recommended metrics:

- request count
- request duration
- error rate
- database connection pool status
- JVM memory and CPU usage

## Tracing

If the system grows into multiple services, add distributed tracing and propagate a correlation id between requests.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database Migration](database-migration.md)
- [Next: Runbook](runbook.md)
