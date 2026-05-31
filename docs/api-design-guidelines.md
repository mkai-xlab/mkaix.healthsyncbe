# API Design Guidelines

[Back to Documentation Index](README.md) | Previous: [Security Guide](security.md) | Next: [Code Style](code-style.md)

This document defines REST API conventions for future backend endpoints.

## URL Naming

- Use plural nouns for resources.
- Use kebab-case for multi-word path segments.
- Keep URLs stable and predictable.

Examples:

```text
/api/users
/api/appointments
/api/medical-records
```

## HTTP Methods

- `GET`: read resources.
- `POST`: create resources or trigger commands.
- `PUT`: replace a resource.
- `PATCH`: partially update a resource.
- `DELETE`: delete a resource.

## Request And Response Bodies

- Use DTOs instead of exposing entities directly.
- Validate request DTOs.
- Return only fields required by clients.
- Keep date/time values in ISO 8601 format.

## Pagination

Recommended query parameters:

```text
page=0
size=20
sort=createdAt,desc
```

Recommended response shape:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## Filtering

Use query parameters for simple filters:

```text
/api/users?status=ACTIVE&keyword=alex
```

## Versioning

If API versioning becomes necessary, prefer URL versioning:

```text
/api/v1/users
```

## Documentation Requirement

Every new endpoint should be documented in [API Documentation](api.md) or generated through OpenAPI.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Security Guide](security.md)
- [Next: Code Style](code-style.md)
