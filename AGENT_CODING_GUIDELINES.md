# HealthSync Backend - Java Spring Boot Coding Guidelines

This document outlines the standard coding rules and project-specific conventions for this application. Any AI agent or developer working on this project MUST strictly follow these guidelines.

## 1. Architecture & Project Structure
- **Layered Architecture**: Strictly follow the standard `Controller -> Service -> Repository` pattern.
- **Service Interfaces**: EVERY service MUST have an interface defined in the `service` package (e.g., `AuthService`). The actual implementation MUST be placed in the `service.impl` subpackage (e.g., `AuthServiceImpl`).
- **Mapper Pattern**: All Entity-to-DTO and DTO-to-Entity mapping logic MUST be extracted into dedicated Mapper components located in the `mapper` package (e.g., `DoctorMapper`). Do not place mapping logic directly inside Service or Controller classes.
- **Clean Workspace**: Remove any unused imports and delete `.gitkeep` files when a folder is no longer empty.

## 2. API Design & Controllers
- **RESTful Endpoints**: Use appropriate HTTP methods (`@GetMapping`, `@PostMapping`, `@PutMapping`, etc.) and return proper HTTP status codes (e.g., `201 Created` for POST, `200 OK` for GET/PUT).
- **ResponseEntity**: Always wrap controller return types in `ResponseEntity<T>` to have full control over the response body and status code.
- **API Documentation**: All Controllers must be properly documented. Maintain compatibility with `springdoc-openapi`.

## 3. Data Transfer Objects (DTOs) & Validation
- **Records vs Classes**: Use Java `record` for simple, immutable DTOs (e.g., `LoginRequest`, `LoginResponse`). Use standard classes with Lombok (`@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`) for complex or mutable DTOs.
- **Validation Constraints**: Always apply Jakarta Validation annotations (`@NotBlank`, `@Email`, `@NotNull`, etc.) to DTO fields to prevent invalid data from reaching the service layer.
- **Controller Validation**: You MUST include the `@Valid` annotation next to `@RequestBody` in the Controller method signature to actively trigger the validation.
- **Documentation**: All DTOs MUST have clear JavaDoc comments written in **English**, explaining the purpose of the class and any important fields.

## 4. Exception Handling
- **Global Error Handling**: Do not handle generic HTTP errors locally in controllers using try-catch blocks. Use the `GlobalExceptionHandler` (annotated with `@RestControllerAdvice`) to catch and format exceptions globally.
- **Validation Errors**: `MethodArgumentNotValidException` must be caught in the `GlobalExceptionHandler` to return a formatted `400 Bad Request` with a standardized `ErrorResponse`, concatenating and detailing which fields failed validation.
- **Standardized Response**: All errors should return a structured JSON response matching the `ErrorResponse` format (`status`, `error`, `message`, `timestamp`).

## 5. Dependency Injection & Best Practices
- **Constructor Injection**: Never use field injection (`@Autowired`). Use constructor injection, preferably via Lombok's `@RequiredArgsConstructor`.
- **Logging**: Use SLF4J (`@Slf4j`) for logging info, warnings, and errors. Never use `System.out.println`.
- **Transactions**: Annotate service methods that modify data (save, update, delete) with Spring's `@Transactional`.

## 6. Language & Comments
- **English Only**: All code comments, JavaDocs, commit messages, and API documentation MUST be written in **English**.
- **Clarity**: Keep comments clear and descriptive, focusing on the "why" rather than the "what" for complex business logic.

## 7. API Testing (Bruno)
- **Always Update Tests**: Whenever you create or modify an API endpoint, its functionality, its payload, or its validation logic, you MUST simultaneously update or add the corresponding Bruno (`.bru`) test files in the `bruno/` directory so the user can immediately test the new functionality.
- **Test Structure**: Group tests logically into folders by feature and action (e.g., `bruno/patient/create_patient/`).
- **Comprehensive Scenarios**: Ensure both success and failure test cases (e.g., missing required fields, duplicate data) are written or updated so the user can immediately test the changes.

## 8. WebSockets & Real-Time Notifications
- **Technology Stack**: Use STOMP over WebSockets (`spring-boot-starter-websocket`). Avoid SockJS fallback unless strictly required for backward compatibility.
- **Security Integration**: WebSocket connections MUST be authenticated. Implement `ChannelInterceptor` in the `security` package (e.g., `WebSocketChannelInterceptor`) to intercept the STOMP `CONNECT` frame and validate the JWT from the `Authorization: Bearer <token>` header.
- **Broker Config**: Register standard `/topic` (broadcast) and `/queue` (user-specific) brokers in a `WebSocketConfig` class located in the `config` package.
- **Payload & DTOs**: Always use DTOs (e.g., `NotificationDto`) when sending messages via `SimpMessagingTemplate`. Do not send raw Entities to avoid exposing sensitive internal state.
- **Client Testing**: Maintain an HTML test client (e.g., `src/main/resources/static/test-stomp.html`) to allow quick local verification of real-time events. Ensure the client connects using the correct context path (`/api/v1/ws`).

## 9. Feature Completion Checklist
- **Always Wrap Up**: Whenever you finish implementing a new feature or completing a functional requirement, you MUST systematically update and synchronize the following components:
  1. **Documentation**: Update `README.md`, `CHANGELOG.md`, or any related system architecture documentation to reflect the new feature.
  2. **Code**: Ensure all code is clean, properly formatted, commented in English, and adheres to the architecture rules.
  3. **Bruno / Tests**: Create or update Bruno collections and automated tests for the newly added or modified functionality.
  4. **AGENT GUIDELINES**: If the new feature introduces a new architectural pattern, rule, or systemic change, update `AGENT_CODING_GUIDELINES.md` to establish the new standard for future tasks.

## 10. API Documentation (Required)
- You MUST maintain the `docs/api.md` file up to date.
- Every new endpoint added to the system MUST be documented in `docs/api.md` including Request, Response, and Status Codes.

## 11. Audit Logging (Required)
- The system uses AOP (Aspect-Oriented Programming) for business audit logging.
- Any new Service method that modifies state (CREATE, UPDATE, DELETE) MUST be annotated with `@LogAction(action="ACTION_NAME")` (e.g. `@LogAction("CREATE_PATIENT")`).
- This applies to all business logic methods that need administrative tracking. Read-only methods (GET) should NOT be annotated.
- The `AuditLogAspect` will automatically extract the `username`, `ipAddress`, and serialize the method arguments.

## 12. External Integrations (AI/ML)
- **HTTP Clients**: When integrating with external AI/ML services (e.g., Python FastAPI), use Spring's `RestTemplate` or `WebClient`. 
- **Response Mapping**: Always map external JSON responses to strongly-typed dedicated DTOs (e.g., `FastApiPredictionResponse`) rather than using raw Maps or generic Objects. Handle nested arrays and structures cleanly.
- **Error Handling**: Anticipate connection timeouts or unexpected schema changes from external AI services. Wrap external calls in try-catch blocks and log appropriately without crashing the main application flow.

## 13. Media & DICOM Processing
- **DICOM Metadata**: Strictly use the `dcm4che3` library to extract DICOM metadata using standard tags (e.g., `Tag.PatientID`, `Tag.BodyPartExamined`). Never attempt to parse patient data from filenames or custom headers.
- **Base64 Handling**: When receiving images as Base64 strings from external APIs (like GradCAM or ROI images), NEVER save the raw Base64 string directly to the database. Decode it and save it as a physical file on the disk (using `ImageIO` or similar utilities), then save only the file path/URL to the database.

## 14. Security & Authentication
- **Endpoint Security**: Use standard Spring Security annotations (e.g., `@PreAuthorize("hasRole('DOCTOR')")`) at the Controller level to enforce RBAC (Role-Based Access Control) instead of manual checks inside business logic.
- **Temporary State**: Use Redis (`StringRedisTemplate`) to manage temporary states and TTLs, such as `uploadSessionId` for pending DICOM sessions, OTPs, or password reset tokens. Avoid polluting relational database tables with short-lived states.

## 15. Asynchronous Processing & Transactions
- **Thread Context Loss**: Be extremely cautious when using `CompletableFuture.runAsync(...)` or Spring's `@Async`. The Spring `@Transactional` context and JPA Session do NOT automatically propagate to new threads. 
- **Database Access in Threads**: If a background thread must access the database, it must do so by calling a `@Transactional` annotated method on a Spring-managed proxy bean (e.g. injecting the Service into itself or extracting logic to another component).
- **Graceful Degradation**: For critical file uploads where user feedback on validation errors is essential, prefer synchronous blocking responses over asynchronous STOMP notifications if the async flow hides failures.

## 16. Configuration & Environment Variables
- **Secrets Management**: Never hardcode sensitive credentials (database passwords, Redis URLs, external API keys, JWT secrets) directly in Java classes. Always inject them via `@Value("${...}")` or `@ConfigurationProperties` to allow environment-specific overrides in `application.yml`.
- **Mail Provider Selection**: Configure SMTP through environment variables consumed by the shared `application.yaml`. Never hardcode SMTP credentials or replace existing business email flows to change mail servers.
- **Asynchronous Mail**: Route application email through `MailUtil` and its dedicated `mailTaskExecutor`. Callers must treat the operation as queued and rely on logs or provider delivery status for asynchronous failures.

## 17. Unit Tests and Test Report Synchronization (Required)
- **Mandatory Coverage**: Every behavior change, endpoint addition, response payload change, or bug fix MUST include or update focused automated unit tests in the same task. Cover the successful path and material failure, authorization, and boundary paths.
- **Mandatory Execution**: Run the affected unit-test classes before completing the task. Never record a test as passed unless the command completed successfully.
- **Mandatory Report Update**: After tests pass, update `docs/unit-test-report.md` in the same task with the exact command, test cases, classifications, execution date, and totals from Surefire reports.
- **API Change Set**: An API change is incomplete until implementation, unit tests, Bruno requests, `docs/api.md`, `CHANGELOG.md`, and `docs/unit-test-report.md` are synchronized.
