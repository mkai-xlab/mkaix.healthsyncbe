# Error Handling

[Back to Documentation Index](README.md) | Previous: [Testing Strategy](testing.md) | Next: [Security Guide](security.md)

This document defines the recommended API error format and error handling conventions.

## Current Status

No public controllers are implemented yet. Use this guide when adding REST APIs.

## Standard Error Response

Recommended response format:

```json
{
  "timestamp": "2026-05-31T10:15:30Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/example",
  "details": [
    {
      "field": "email",
      "message": "must be a valid email address"
    }
  ]
}
```

## Status Code Rules

- `200 OK`: request succeeded.
- `201 Created`: resource created.
- `204 No Content`: request succeeded with no response body.
- `400 Bad Request`: invalid request syntax or validation failure.
- `401 Unauthorized`: authentication is missing or invalid.
- `403 Forbidden`: authenticated user lacks permission.
- `404 Not Found`: resource does not exist.
- `409 Conflict`: state conflict or duplicate resource.
- `500 Internal Server Error`: unexpected server error.

## Exception Handling Rules

- Use a global exception handler for API errors.
- Do not expose stack traces to clients.
- Log unexpected server errors with enough context for debugging.
- Keep user-facing messages clear and safe.
- Use stable error codes for frontend handling.

## Validation Errors

Validation errors should identify the invalid field and the reason.

Example:

```json
{
  "field": "password",
  "message": "must contain at least 8 characters"
}
```

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Testing Strategy](testing.md)
- [Next: Security Guide](security.md)
