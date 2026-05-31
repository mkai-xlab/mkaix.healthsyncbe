# Release Process

[Back to Documentation Index](README.md) | Previous: [Runbook](runbook.md) | Next: [Contributing](contributing.md)

This document defines a lightweight release process for the backend service.

## Branches

The project uses a simplified Git Flow:

- `main`: production-ready branch.
- `develop`: integration branch.
- `feature/*`: feature work.
- `hotfix/*`: urgent fixes from `main`.

See [Standard Git Flow](git-flow.md).

## Versioning

Use semantic versioning when releases become formal:

```text
MAJOR.MINOR.PATCH
```

Examples:

```text
v1.0.0
v1.1.0
v1.1.1
```

## Release Checklist

1. Confirm all intended features are merged into `develop`.
2. Run `mvn test`.
3. Run `mvn clean package`.
4. Update documentation if behavior changed.
5. Merge `develop` into `main`.
6. Create a version tag on `main`.
7. Deploy the tagged version.
8. Smoke test the deployed service.

## Hotfix Checklist

1. Create `hotfix/<short-description>` from `main`.
2. Apply the fix.
3. Add or update tests when possible.
4. Merge the hotfix into `main`.
5. Tag a patch version.
6. Merge the hotfix back into `develop`.

## Changelog

Maintain user-visible and operational changes in:

```text
CHANGELOG.md
```

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Runbook](runbook.md)
- [Next: Contributing](contributing.md)
