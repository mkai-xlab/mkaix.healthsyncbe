# Database Migration

[Back to Documentation Index](README.md) | Previous: [Code Style](code-style.md) | Next: [Observability](observability.md)

This document defines the recommended database migration approach for future schema changes.

## Current Status

The project currently uses MySQL and Spring Data JPA. Hibernate DDL auto-generation is disabled:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none
```

This is a good production-safe default because the application should not silently change schema at runtime.

## Recommended Tool

Use Flyway or Liquibase when database schema is introduced.

Recommended starting choice: Flyway, because it is simple for sequential SQL migrations.

## Migration Naming

Recommended Flyway naming:

```text
V1__create_users_table.sql
V2__create_appointments_table.sql
V3__add_user_status.sql
```

Recommended location:

```text
src/main/resources/db/migration
```

## Rules

- Every schema change must have a migration.
- Do not edit already-applied migration files.
- Add a new migration for every change.
- Test migrations locally before merging.
- Avoid destructive migrations unless there is a backup and rollback plan.

## Seed Data

Keep seed data separate from schema migration when possible.

Use seed data only for:

- local development
- test environments
- required reference data

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Code Style](code-style.md)
- [Next: Observability](observability.md)
