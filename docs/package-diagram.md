# Package Diagram

[Back to Documentation Index](README.md) | Previous: [Authentication Workflow](authentication.md) | Next: [Development Guide](development.md)

This page documents the intended backend Java packages at depth `1` only, using a UML package diagram pattern.

![Backend package diagram](diagrams/package-diagram.png)

The editable draw.io diagram is available at [diagrams/package-diagram.drawio](diagrams/package-diagram.drawio).

## Package Scope

```text
com.g93.be
├── controller
├── dto
├── service
├── repository
├── entity
├── mapper
├── specification
├── config
├── exception
├── security
└── common
```

## Scope Rule

This diagram intentionally stops at one package level below `com.g93.be`.

Do not list deeper nested packages here, such as:

- `contract`
- `impl`
- any package nested below a first-level feature or layer package

Only direct child packages under `com.g93.be` should appear here.

## Dependency Relationships

Dashed arrows show package dependencies only.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Authentication Workflow](authentication.md)
- [Next: Development Guide](development.md)
