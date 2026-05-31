# Contributing Guide

[Back to Documentation Index](README.md) | Previous: [Release Process](release-process.md) | Next: [Architecture Decision Records](adr/README.md)

This document defines team contribution rules for the backend project.

## Branch Naming

- Feature: `feature/<short-description>`
- Hotfix: `hotfix/<short-description>`
- Documentation: `docs/<short-description>`
- Refactor: `refactor/<short-description>`

Examples:

```text
feature/user-login
hotfix/fix-database-config
docs/update-api-guide
refactor/service-layer
```

## Commit Messages

Use concise commit messages that explain the change.

Recommended format:

```text
type: short description
```

Common types:

- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation change
- `test`: test change
- `refactor`: code change without behavior change
- `chore`: maintenance task

Examples:

```text
feat: add appointment creation endpoint
fix: handle missing database password
docs: add deployment guide
```

## Pull Request Checklist

- Code builds successfully.
- Tests pass with `mvn test`.
- Documentation is updated when behavior changes.
- No secrets are committed.
- Generated files are not committed unless intentionally required.
- API changes are documented.

## Code Review Checklist

- Does the change solve the intended problem?
- Is the code placed in the correct layer?
- Are errors handled consistently?
- Are security risks introduced?
- Are tests sufficient for the behavior?
- Are docs updated?

## Documentation Rule

When code or functionality changes, update relevant docs in the same pull request.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Release Process](release-process.md)
- [Next: Architecture Decision Records](adr/README.md)
