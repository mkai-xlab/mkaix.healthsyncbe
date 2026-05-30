# Backend Architecture

[Back to Documentation Index](README.md) | Next: [Authentication Workflow](authentication.md)

## Overview

The backend is a Spring Boot 4 application using Java 21 and Maven. The current codebase contains the application entry point and base Spring configuration only.

![Backend architecture diagram](diagrams/be-architecture.png)

## Current Runtime Flow

```text
Client request
  -> Spring Boot embedded server
  -> Spring Web MVC dispatcher
  -> Controller layer, when implemented
  -> Service layer, when implemented
  -> Repository or integration layer, when implemented
```

At the moment, no controllers are registered, so the service does not expose application-specific routes yet.

## Application Entry Point

The application starts from:

```text
src/main/java/com/g93/be/BeApplication.java
```

`@SpringBootApplication` enables component scanning and auto-configuration for classes under the `com.g93.be` package.

## Configuration

The main configuration file is:

```text
src/main/resources/application.yaml
```

Current settings only define the Spring application name:

```yaml
spring:
  application:
    name: be
```

## Suggested Package Layout

Use a feature-oriented or layered package structure as the application grows. A simple starting point is:

```text
com.g93.be
├── controller
├── service
├── repository
├── domain
├── dto
├── config
└── exception
```

## Architecture Diagram

The current architecture diagram is stored in:

```text
docs/diagrams/be-architecture.drawio
docs/diagrams/be-architecture.png
```

Update both the editable `.drawio` file and exported image when the architecture changes.

## Navigation

- [Back to Documentation Index](README.md)
- [Next: Authentication Workflow](authentication.md)
