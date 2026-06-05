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
