# Standard Git Flow

This project can use a simplified Git Flow model to keep feature work, development integration, production releases, and urgent fixes separated.

## Branches

- `main`: stable production-ready code.
- `develop`: integration branch for completed features.
- `feature/*`: short-lived branches for new work.
- `hotfix/*`: urgent production fixes created from `main`.

## Workflow

1. Create `feature/*` branches from `develop`.
2. Merge completed features back into `develop` through a pull request.
3. Merge `develop` into `main` when the version is ready.
4. Tag the release on `main`, for example `v1.0.0`.
5. Create `hotfix/*` from `main` for urgent production fixes.
6. Merge hotfixes into both `main` and `develop`.

## Naming

- Feature branch: `feature/<short-description>`
- Hotfix branch: `hotfix/<short-description>`
- Version tag: `v<major>.<minor>.<patch>`

Examples:

```bash
feature/user-login
hotfix/fix-login-error
v1.0.0
```

## Diagram

Editable diagram: [Git Flow draw.io](diagrams/git-flow.drawio)

Image diagram:

![Standard Git Flow](diagrams/git-flow.png)
