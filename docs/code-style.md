# Code Style

[Back to Documentation Index](README.md) | Previous: [API Design Guidelines](api-design-guidelines.md) | Next: [Database Migration](database-migration.md)

This document defines backend coding conventions.

## Package Structure

Keep application code under:

```text
com.g93.be
```

Recommended packages as the application grows:

```text
com.g93.be
├── config
├── controller
├── dto
├── entity
├── exception
├── mapper
├── repository
├── security
└── service
```

## Layering Rules

- Controllers handle HTTP input and output only.
- Services contain business logic.
- Repositories handle persistence access.
- DTOs define external API contracts.
- Entities represent persisted data.
- Mappers convert between DTOs and entities.

## Naming

- Controller: `<Feature>Controller`
- Service interface: `<Feature>Service`
- Service implementation: `<Feature>ServiceImpl`
- Repository: `<Entity>Repository`
- Request DTO: `<Action><Feature>Request`
- Response DTO: `<Feature>Response`

## Lombok

Lombok is available in the project. Use it carefully:

- Prefer explicit constructors for complex classes.
- Avoid `@Data` on JPA entities if it creates unsafe `equals`, `hashCode`, or `toString` methods.
- Do not hide important behavior behind too many annotations.

## Controller Rules

- Validate request bodies.
- Return appropriate HTTP status codes.
- Do not put business rules in controllers.
- Keep endpoint names aligned with [API Design Guidelines](api-design-guidelines.md).

## Service Rules

- Keep transaction boundaries clear.
- Keep methods focused on one business use case.
- Throw meaningful domain exceptions for expected failures.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: API Design Guidelines](api-design-guidelines.md)
- [Next: Database Migration](database-migration.md)
