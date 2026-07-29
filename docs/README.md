# Documentation

This folder contains backend documentation for the Capstone project.

## Reading Order

1. [Backend Architecture](architecture.md)
2. [Authentication Workflow](authentication.md)
3. [Package Diagram](package-diagram.md)
4. [Standard Git Flow](git-flow.md)
5. [Development Guide](development.md)
6. [Environment Configuration](environment.md)
7. [Database](database.md)
8. [Email Integration](email.md)
9. [API Documentation](api.md)
10. [Deployment Guide](deployment.md)
11. [Testing Strategy](testing.md)
12. [Error Handling](error-handling.md)
13. [Security Guide](security.md)
14. [API Design Guidelines](api-design-guidelines.md)
15. [Code Style](code-style.md)
16. [Frontend Examination Report Integration](frontend-examination-report.md)
17. [Database Migration](database-migration.md)
18. [Observability](observability.md)
19. [Runbook](runbook.md)
20. [Release Process](release-process.md)
21. [Contributing Guide](contributing.md)
22. [DICOM to PNG Processing Pipeline](dicom-processor.md)
23. [Architecture Decision Records](adr/README.md)

## Assets

![Backend architecture](diagrams/be-architecture.png)

![Backend package diagram](diagrams/package-diagram.png)

![Standard Git Flow](diagrams/git-flow.png)

![Database ERD](diagrams/database.png)

- [Editable Architecture Diagram](diagrams/be-architecture.drawio)
- [Architecture Diagram Image](diagrams/be-architecture.png)
- [Editable Package Diagram](diagrams/package-diagram.drawio)
- [Package Diagram Image](diagrams/package-diagram.png)
- [Editable Git Flow Diagram](diagrams/git-flow.drawio)
- [Git Flow Diagram Image](diagrams/git-flow.png)
- [Database Schema DBML](database-schema.dbml)
- [Database ERD Image](diagrams/database.png)

## Current State

The backend is a Spring Boot application with JWT-authenticated REST APIs for users, patients, DICOM processing, AI-assisted diagnosis review, notifications, and finalized PDF reports.

Keep these docs updated whenever new modules are added, especially when introducing:

- REST controllers
- services and domain logic
- database access
- authentication or authorization
- external integrations
- deployment configuration

## Maintenance Rule

When code, configuration, database schema, authentication, deployment, or API behavior changes, update the relevant documentation in the same change.

## Navigation

- [Back to repository README](../README.md)
- Next: [Backend Architecture](architecture.md)
