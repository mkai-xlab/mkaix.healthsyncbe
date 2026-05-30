# Authentication Workflow

[Back to Documentation Index](README.md) | Previous: [Backend Architecture](architecture.md) | Next: [Package Diagram](package-diagram.md)

This document describes the expected authentication flow between a client application, Keycloak, and the Spring backend.

![Authentication workflow sequence](diagrams/authentication-workflow.png)

The editable draw.io diagram is available at [diagrams/authentication-workflow.drawio](diagrams/authentication-workflow.drawio).

```mermaid
sequenceDiagram
    autonumber
    participant Client as Client App
    participant Keycloak as Keycloak
    participant Spring as Spring App

    Client->>Client: User opens protected route
    Client->>Keycloak: Redirect to login
    Keycloak->>Client: Return authorization code
    Client->>Keycloak: Exchange code + PKCE verifier for tokens
    Keycloak->>Client: Return access token and ID token
    Client->>Spring: API request with Authorization: Bearer token
    Spring->>Keycloak: Fetch JWKS / issuer metadata when needed
    Spring->>Spring: Validate issuer, signature, expiry, and roles
    alt Token is valid and authorized
        Spring->>Client: Return protected resource
    else Token invalid or forbidden
        Spring->>Client: Return 401 Unauthorized or 403 Forbidden
    end
```

## Steps

1. The client opens a protected route or sends a request that requires authentication.
2. The client redirects the user to Keycloak for login.
3. Keycloak authenticates the user and issues tokens.
4. The client sends API requests to the Spring app with `Authorization: Bearer <access-token>`.
5. The Spring app validates the JWT using Keycloak issuer metadata and signing keys.
6. The Spring app returns the protected resource, `401 Unauthorized`, or `403 Forbidden`.

## Spring Responsibilities

- Validate JWT issuer, signature, expiry, and audience when configured.
- Map token roles or claims to Spring Security authorities.
- Reject missing, expired, invalid, or insufficiently privileged tokens.
- Keep business endpoints independent from Keycloak login UI concerns.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Backend Architecture](architecture.md)
- [Next: Package Diagram](package-diagram.md)
