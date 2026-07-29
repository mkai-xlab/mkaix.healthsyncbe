# Backend Architecture

[Back to Documentation Index](README.md) | Next: [Authentication Workflow](authentication.md)

## Overview

The backend is a Spring Boot application using Java 21 and Maven. It follows a modular, layered architecture to support the AI-assisted knee X-ray diagnosis system.

![Backend architecture diagram](diagrams/be-architecture.png)

## Runtime Flow

```text
Client request (REST / HTTP)
  -> Spring Security (JWT Authentication Filters)
  -> Spring Web MVC DispatcherServlet
  -> Controller layer (e.g., AuthController, PatientController, NotificationController)
  -> Service layer (Business Logic & Transactions)
  -> Repository layer (Spring Data JPA / Specifications)
  -> Database (MySQL)

Client request (STOMP WebSocket)
  -> Spring WebSocket Message Broker (/api/v1/ws)
  -> WebSocketChannelInterceptor (JWT Token Validation on CONNECT)
  -> STOMP Destinations (/topic or /user/queue)
```

The application now actively exposes various RESTful API endpoints securely under the `/api/v1` context path and supports real-time, authenticated STOMP WebSocket connections.

## PDF Reporting Engine

The system uses a combination of **Thymeleaf** and **OpenHTMLToPDF** to generate finalized clinical PDF reports containing patient details and reviewed AI analysis results.
- **Thymeleaf** binds dynamic Java Data Transfer Objects (DTOs) into HTML templates (stored in `src/main/resources/templates/pdf`).
- **OpenHTMLToPDF** renders the finalized HTML strings into binary PDF files with a classpath-loaded Tahoma font for Vietnamese and embedded Base64-encoded AI Grad-CAM images.
- Report generation requires examination status `VERIFIED`, stores the PDF under `app.pdf.export-dir`, persists metadata in the `report` table, and changes the examination to `REPORT_GENERATED`.
- Preview and download stream the stored file after authorization. Preview uses `inline`; download uses `attachment` and is audit logged.
- PDF export uses `DiagnosisReview.confirmedKlGrade`, so an AI confirmation exports the AI grade and a doctor adjustment exports the doctor's final grade.

The complete frontend contract is documented in [Frontend Examination Report Integration](frontend-examination-report.md).

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
