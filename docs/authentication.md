# Authentication Workflow

[Back to Documentation Index](README.md) | Previous: [Backend Architecture](architecture.md) | Next: [Package Diagram](package-diagram.md)

This document describes the direct stateless authentication flow between a client application (Flutter) and the Spring Boot backend using username/password and JSON Web Tokens (JWT).


```mermaid
sequenceDiagram
    autonumber
    participant Client as Client App
    participant Spring as Spring App (Backend)
    participant Database as MySQL Database

    Note over Client,Spring: Initial Login
    Client->>Spring: POST /api/v1/auth/login (username, password)
    Spring->>Database: Query user by username or email
    Database->>Spring: Return user record (hashed password)
    Spring->>Spring: Verify BCrypt password match
    alt Credentials are valid
        Spring->>Spring: Generate Access Token & Refresh Token
        Spring->>Client: Return 200 OK (access_token, refresh_token)
    else Invalid credentials
        Spring->>Client: Return 401 Unauthorized
    end

    Note over Client,Spring: Accessing Protected Resource
    Client->>Spring: GET /api/v1/protected (Authorization: Bearer <access-token>)
    Spring->>Spring: JWT Filter validates signature, expiry, and roles
    alt Token is valid and authorized
        Spring->>Client: Return 200 OK (Protected Resource)
    else Token invalid or expired
        Spring->>Client: Return 401 Unauthorized or 403 Forbidden
    end
```

## Steps

### Login Phase
1. The client sends a login request with `username` (or `email`) and `password` to `/api/v1/auth/login`.
2. The Spring app loads user details, verifies the BCrypt hashed password, and generates JWT access and refresh tokens.
3. The client receives and stores the tokens securely.

### Access Phase
4. The client includes the access token in the `Authorization: Bearer <token>` header of subsequent API requests.
5. Spring Security filters intercept the request, extract the token, and validate its signature and expiry.
6. If the token is valid, Spring populates the Security Context with the user's details and authorities.
7. The API endpoint executes and returns the resource.

## Spring Responsibilities

- **Password Hashing**: Encode user passwords using BCrypt when users register or update their credentials.
- **JWT Generation & Parsing**: Generate secure, signed tokens containing username and roles, and validate them on every API request.
- **Stateless Session Management**: Configure Spring Security to not create HTTP sessions (`SessionCreationPolicy.STATELESS`).
- **Error Handling**: Properly handle authentication entry points to return standard JSON responses for `401 Unauthorized` and `403 Forbidden`.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Backend Architecture](architecture.md)
- [Next: Package Diagram](package-diagram.md)
