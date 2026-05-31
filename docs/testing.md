# Testing Strategy

[Back to Documentation Index](README.md) | Previous: [Deployment Guide](deployment.md) | Next: [Error Handling](error-handling.md)

This document defines how tests should be organized as the backend grows.

## Current Test Status

The project currently has a Spring Boot context test:

```text
src/test/java/com/g93/be/BeApplicationTests.java
```

Run tests:

```bash
mvn test
```

## Test Types

## Unit Tests

Use unit tests for isolated business logic.

Recommended targets:

- service methods
- validators
- mappers
- utility classes
- permission logic

## Web Layer Tests

Use web tests for controllers after REST endpoints are added.

Recommended checks:

- request validation
- response body shape
- HTTP status codes
- authentication and authorization behavior

## Integration Tests

Use integration tests for behavior that crosses application boundaries.

Recommended checks:

- repository/database behavior
- transaction behavior
- full request-to-database flows
- Keycloak/JWT integration when security is implemented

## Naming Convention

- Unit test class: `<ClassName>Test`
- Integration test class: `<FeatureName>IntegrationTest`
- Test method: describe expected behavior, for example `shouldReturnBadRequestWhenEmailIsInvalid`

## Test Data Rules

- Keep test data small and explicit.
- Avoid relying on test execution order.
- Clean database state between integration tests.
- Do not use production credentials or production data.

## Minimum Pull Request Requirement

Every pull request that changes behavior should include at least one of:

- new tests
- updated tests
- a clear explanation why tests are not needed

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Deployment Guide](deployment.md)
- [Next: Error Handling](error-handling.md)
