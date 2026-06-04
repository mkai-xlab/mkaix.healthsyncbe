# Security Guide

[Back to Documentation Index](README.md) | Previous: [Error Handling](error-handling.md) | Next: [API Design Guidelines](api-design-guidelines.md)

This document records security expectations for the backend service.

## Current Status

The backend uses Spring Security configured for JWT-based stateless authentication and authorization.

## Authentication

Authentication is handled locally using:
- **Credentials**: Standard `username` and `password` credentials.
- **Hashing**: BCryptPasswordEncoder is used to secure passwords before storing them in the database.
- **JWT**: A stateless JWT authentication filter parses the `Authorization: Bearer <token>` header from incoming requests.

See [Authentication Workflow](authentication.md) for the high-level flow.

## Token Validation

The stateless JWT filter validates:
- token signature using a HMAC-256 secret key
- issuer
- expiry time
- roles / authority claims stored in the JWT payload

## Authorization

Recommended approach:
- Use endpoint-level authorization (`@PreAuthorize` or `SecurityFilterChain` rules) for coarse access control.
- Use service-level checks for business permissions.
- Keep role names consistent with the `UserRole` enum (`ROLE_DOCTOR`, `ROLE_ADMIN`).
- Deny access by default for protected resources.

## CORS

When frontend integration starts, configure CORS explicitly:

- allow only trusted frontend origins
- allow only required methods
- allow only required headers
- do not use wildcard origins in production

## Secret Management

- Do not commit secrets.
- Keep local secrets in ignored environment files.
- Use production secret storage for deployed environments.
- Rotate secrets if they are exposed.

## Logging Safety

Never log:

- access tokens
- refresh tokens
- passwords
- database credentials
- private keys

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Error Handling](error-handling.md)
- [Next: API Design Guidelines](api-design-guidelines.md)
