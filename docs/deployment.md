# Deployment Guide

[Back to Documentation Index](README.md) | Previous: [API Documentation](api.md) | Next: [Testing Strategy](testing.md)

This document describes the expected deployment process for the backend service.

## Current Deployment Status

The project currently provides a Spring Boot backend and a local MySQL Docker Compose service. A production deployment pipeline has not been added yet.

## Build Artifact

Build the application:

```bash
mvn clean package
```

The packaged application is generated under:

```text
target/
```

## Runtime Requirements

- Java 21 runtime.
- MySQL-compatible database.
- Required environment variables from [Environment Configuration](environment.md).
- Network access from the backend service to the database.

## Manual Deployment Checklist

1. Pull the intended Git commit or release tag.
2. Confirm required environment variables are configured.
3. Run tests with `mvn test`.
4. Build with `mvn clean package`.
5. Start or restart the backend service.
6. Confirm the service starts without datasource errors.
7. Smoke test public and protected endpoints when they exist.

## Rollback Strategy

When production deployment is introduced, keep rollback simple:

- Deploy immutable build artifacts.
- Tag every production release.
- Keep the previous deployable artifact available.
- Roll back application code before rolling back database schema.
- Avoid destructive database migrations without a backup plan.

## Docker Notes

The current `docker-compose.yaml` starts MySQL only. If the backend is containerized later, add:

- `Dockerfile`
- backend service in `docker-compose.yaml`
- image build and run instructions
- container health check
- production environment variable list

## Production Readiness Checklist

- Centralized secrets management.
- Health check endpoint.
- Structured logging.
- Database migration tool such as Flyway or Liquibase.
- CI build and test pipeline.
- Deployment rollback procedure.
- Monitoring and alerting.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: API Documentation](api.md)
- [Next: Testing Strategy](testing.md)
