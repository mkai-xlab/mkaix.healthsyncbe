# API Documentation

[Back to Documentation Index](README.md) | Previous: [Development Guide](development.md)

No public API endpoints are currently implemented.

When controllers are added, document each endpoint using this format:

## `METHOD /path`

Purpose of the endpoint.

### Request

```json
{
  "example": "value"
}
```

### Response

```json
{
  "example": "value"
}
```

### Status Codes

- `200 OK`: request succeeded
- `400 Bad Request`: invalid request input
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed
- `404 Not Found`: resource does not exist
- `500 Internal Server Error`: unexpected server error

Remove unused status codes from each endpoint section as APIs are documented.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Development Guide](development.md)
