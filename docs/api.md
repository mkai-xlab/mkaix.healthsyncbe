# API Documentation

[Back to Documentation Index](README.md) | Previous: [Database](database.md) | Next: [Deployment Guide](deployment.md)

No public API endpoints are currently implemented, except for authentication endpoints.

## `POST /auth/change-password`

Endpoint for users to change their password. This is required for first-time login activation and can be used to update passwords securely.

### Request

```json
{
  "username": "admin",
  "oldPassword": "oldPassword123",
  "newPassword": "newPassword456"
}
```

### Response

```text
Password changed successfully
```

### Status Codes

- `200 OK`: Password changed successfully
- `400 Bad Request`: Invalid input or incorrect credentials
- `500 Internal Server Error`: Unexpected server error

## `POST /auth/forgot-password`

Endpoint to initiate the forgot password flow. Generates a 6-digit OTP and sends it via email.

### Request

```json
{
  "email": "admin@example.com"
}
```

### Response

```text
If the email exists, a password reset token has been sent.
```

### Status Codes

- `200 OK`: Request processed successfully
- `400 Bad Request`: Invalid email format

## `POST /auth/reset-password`

Endpoint to reset the password using the 6-digit OTP sent to the user's email.

### Request

```json
{
  "email": "admin@example.com",
  "token": "123456",
  "newPassword": "newPassword123"
}
```

### Response

```text
Password reset successfully
```

### Status Codes

- `200 OK`: Password reset successfully
- `400 Bad Request`: Invalid input, incorrect token, or expired token
- `500 Internal Server Error`: Unexpected server error

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
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)
