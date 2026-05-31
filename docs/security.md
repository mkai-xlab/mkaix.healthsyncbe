# Security Guide

[Back to Documentation Index](README.md) | Previous: [Error Handling](error-handling.md) | Next: [API Design Guidelines](api-design-guidelines.md)

This document records security expectations for the backend service.

## Current Status

The documentation describes Keycloak-based authentication, but Spring Security configuration has not been implemented in the current backend code yet.

## Authentication

The intended authentication provider is Keycloak using Authorization Code Flow.

See [Authentication Workflow](authentication.md) for the high-level flow.

## Token Validation

When Spring Security is added, the backend should validate:

- token signature
- issuer
- expiry time
- audience, if configured
- roles or scopes required by each endpoint

## Authorization

Recommended approach:

- Use endpoint-level authorization for coarse access control.
- Use service-level checks for business permissions.
- Keep role names consistent with Keycloak realm or client roles.
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
