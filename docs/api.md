# API Documentation

[Back to Documentation Index](README.md) | Previous: [Database](database.md) | Next: [Deployment Guide](deployment.md)

All paths below are relative to the configured `/api/v1` context path.

## Recent API Updates

### Today's examination selection and report-aware RAG

`POST /chat/ask` now recognizes requests such as `Cho toi xem cac ca kham hom nay`
as `TODAY_EXAMINATION_LIST`. The backend returns an AI-formatted numbered list from
a controlled read-only query of at most 10 rows, newest visit first. Each row supplies
the examination ID, encounter code, patient code and name, visit time, status, and
priority so the user can select an examination in a follow-up message.

```json
{
  "question": "Cho toi xem cac ca kham hom nay"
}
```

Doctors receive only examinations assigned to them. Department heads can receive
the clinical list; administrators are rejected because the rows contain patient data.
No new endpoint or request field is required. A visual selection control is outside
the backend contract; the current `answer` contains the numbered options.

Stored report summaries use the controlled MySQL `BUSINESS_DATA` path. When a user
asks to medically interpret a report, the router uses `HYBRID`, passes the stored
report fields into retrieval, and combines them with approved Qdrant evidence.
Report indexing now retries unfinished records, resynchronizes when an existing PDF
is requested again, and permits both the report creator and assigned doctor to find
the owner-scoped report vector.

### Medical knowledge validation, listing, file access, and deletion

`POST /knowledge-documents/upload`, `POST /knowledge-documents/upload/batch`, and
`POST /knowledge-documents/url` validate source content before storing or indexing it.
The backend reads the document and classifies large samples from its beginning, middle,
and end. Only clearly medical or healthcare content at the configured confidence
threshold is accepted. A rejected single upload returns `400 Bad Request`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Document rejected: Software documentation",
  "timestamp": "2026-08-13T10:30:00"
}
```

Batch requests still return `202 Accepted`; each non-medical file has
`accepted: false` and its rejection reason in `error`. Accepted sources return status
`PENDING` and are indexed asynchronously. Medical retrieval now requests up to 12
matching chunks by default.

#### `GET /knowledge-documents`

Returns a paginated list of uploaded file, URL, and approved-report knowledge
sources. Requires a supported management role and the
`MANAGE_MEDICAL_KNOWLEDGE` authority.

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `keyword` | No | Case-insensitive title or original-file-name search. |
| `sourceType` | No | `FILE`, `URL`, or `REPORT`. |
| `status` | No | `PENDING`, `PROCESSING`, `INDEXED`, or `FAILED`. |
| `accessScope` | No | `ALL`, `DOCTOR`, `ADMIN`, or `OWNER`. |
| `page` | No | Zero-based page number; default `0`. |
| `size` | No | Page size; default `20`. |
| `sort` | No | Spring sort expression; default `createdAt,desc`. |

Example response:

```json
{
  "content": [
    {
      "id": 8,
      "title": "Knee osteoarthritis guideline",
      "sourceType": "FILE",
      "sourceUrl": null,
      "originalName": "knee-guideline.pdf",
      "contentUrl": "/api/v1/knowledge-documents/8/content",
      "previewUrl": "/api/v1/knowledge-documents/8/preview",
      "downloadUrl": "/api/v1/knowledge-documents/8/download",
      "accessScope": "ALL",
      "status": "INDEXED",
      "chunkCount": 12,
      "errorMessage": null,
      "createdAt": "2026-08-20T09:00:00",
      "indexedAt": "2026-08-20T09:02:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

Internal `storagePath` and `checksum` values are never returned. Knowledge sources
of type `REPORT` have no locally stored ingestion file, so their `contentUrl`,
`previewUrl`, and `downloadUrl` are `null`.

Status codes: `200 OK`, `400 Bad Request` for invalid enum/sort input,
`401 Unauthorized`, and `403 Forbidden`.

#### `GET /knowledge-documents/{id}/content`

Extracts and returns readable source text as `text/plain;charset=UTF-8`. PDF and TXT
use dedicated readers; DOC/DOCX and stored URL HTML use Tika. The response includes
`Cache-Control: no-store`.

Status codes: `200 OK`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found` when
the metadata/file is missing or the resolved path is outside the configured
knowledge directory, and `500 Internal Server Error` when an otherwise valid source
cannot be parsed.

#### `GET /knowledge-documents/{id}/preview`

Streams the original source using its stored media type and file name. The response
sets `Content-Disposition: inline`, `Cache-Control: no-store`, and
`X-Content-Type-Options: nosniff`. Browser support determines whether DOC/DOCX is
displayed inline; clients can use `/content` for a browser-independent text view.

Status codes: `200 OK`, `401 Unauthorized`, `403 Forbidden`, and `404 Not Found`.

#### `GET /knowledge-documents/{id}/download`

Streams the original source with `Content-Disposition: attachment`; the original
safe file name is encoded as UTF-8. The operation is audit logged as
`DOWNLOAD_MEDICAL_KNOWLEDGE`.

Status codes: `200 OK`, `401 Unauthorized`, `403 Forbidden`, and `404 Not Found`.

#### `DELETE /knowledge-documents/{id}`

Deletes the knowledge metadata, locally stored source, and all matching Qdrant chunks.
The endpoint requires a supported management role and the
`MANAGE_MEDICAL_KNOWLEDGE` authority. It is safe when indexing is still in flight.

Response: no body.

Status codes: `204 No Content`, `401 Unauthorized`, `403 Forbidden`, and
`404 Not Found` when the document does not exist.

### KL result confirmation and adjustment

The reviewing doctor must choose exactly one final-result action. A doctor assigned to the examination can confirm the AI prediction, or adjust it to a clinically determined Kellgren-Lawrence grade. A department head inherits both actions and can review examinations outside their own assignment.

#### `PUT /ai/results/{aiResultId}/confirm`

Confirms the AI-predicted KL grade as the final result. An assigned doctor requires the `DOCTOR` role and `CONFIRM_CONCLUSION` authority. Department heads can confirm using either department-head role.

No request body is required.

#### `PUT /ai/results/{aiResultId}/kl-grade`

Adjusts the final KL grade while retaining the original AI prediction. Doctors require the `DOCTOR` role and `OVERRIDE_AI_GRADE` authority. Department heads can adjust using either department-head role.

Request:

```json
{
  "confirmedKlGrade": 3,
  "reviewNote": "Clinical findings support KL grade 3"
}
```

`confirmedKlGrade` must be an integer from `0` to `4`. `reviewNote` is required and limited to 2000 characters.

Response:

```json
{
  "reviewId": 23,
  "aiResultId": 19,
  "examinationId": 11,
  "predictedKlGrade": 2,
  "confirmedKlGrade": 3,
  "decision": "DOCTOR_ADJUSTED",
  "reviewNote": "Clinical findings support KL grade 3",
  "reviewedByDoctorId": 7,
  "reviewedAt": "2026-07-25T09:30:00"
}
```

The response decision is `AI_CONFIRMED` when the reviewer accepts the prediction and `DOCTOR_ADJUSTED` when the reviewer changes it. Subsequent examination responses expose `predictedGrade`, `confirmedGrade`, `effectiveGrade`, and `reviewDecision` for each AI result.

Successful confirm and adjust operations are recorded by the audit-log aspect with action codes `CONFIRM_AI_GRADE` and `OVERRIDE_AI_GRADE`, respectively.

After all AI results in every latest analysis have a review decision, the examination moves to `VERIFIED`. Review changes are rejected after the examination reaches `REPORT_GENERATED`.

PDF export reads only the latest AI analysis for each DICOM image and requires every exported AI result to have a review decision. For `AI_CONFIRMED`, the final exported KL grade is the original AI prediction. For `DOCTOR_ADJUSTED`, it is the reviewer-entered grade. Missing or unconfirmed AI results cause PDF export to return `400 Bad Request`. A successful export moves the examination to `REPORT_GENERATED`.

Status codes for both review endpoints: `200 OK`, `400 Bad Request` for invalid input or an unknown AI result, `401 Unauthorized`, `403 Forbidden` when the required role, authority, or examination ownership is missing.

### PDF report generation, preview, and download

#### `POST /examinations/{examinationId}/generate-report`

Generates and stores the finalized PDF for a `VERIFIED` examination. The response contains report metadata plus authenticated preview and download URLs. Calling the endpoint again returns the existing report while its stored file is available.

Every newly generated PDF page includes a large pale-blue `HealthSync` watermark
rotated diagonally behind the content and the disclaimer `đây là sản phẩm AI, chỉ
là công cụ hỗ trợ, AI có thể sai sót`. The general-information section includes
the DICOM acquisition date/time from `studyDate` and `studyTime`, formatted as
`dd/MM/yyyy HH:mm:ss` or as `dd/MM/yyyy` when no time was supplied. Existing stored
PDFs are reused and are not retroactively regenerated with the new template.

```json
{
  "reportId": 1,
  "examinationId": 1,
  "fileName": "report_STUDY001_a1b2c3d4.pdf",
  "fileSize": 38826,
  "contentType": "application/pdf",
  "generatedAt": "2026-07-29T14:32:14",
  "previewUrl": "/api/v1/reports/1/preview",
  "downloadUrl": "/api/v1/reports/1/download"
}
```

The numeric segment in both URLs is the `examinationId`, not the `reportId`.

#### `GET /reports/{examinationId}/preview`

Returns the latest PDF generated for the examination with `Content-Type: application/pdf` and `Content-Disposition: inline`. The frontend must fetch this URL with the Bearer token and display the resulting Blob URL.

#### `GET /reports/{examinationId}/download`

Returns the latest PDF generated for the examination with `Content-Disposition: attachment`. The frontend must fetch with the Bearer token, create a Blob URL, and trigger an anchor download. The operation is recorded in the audit log.

Do not navigate directly to either URL because normal browser navigation does not attach the Bearer token. See [Frontend Examination Report Integration](frontend-examination-report.md) for the full state flow, JavaScript examples, authorization matrix, and error handling.

Swagger UI is available at `/api/v1/swagger-ui/index.html`; the OpenAPI JSON is available at `/api/v1/v3/api-docs`.

### `POST /auth/login` response update

Successful login responses include the user's full name:

```json
{
  "accessToken": "eyJhbGciOiJIUz...",
  "refreshToken": "eyJhbGciOiJIUz...",
  "role": "DOCTOR",
  "username": "doctor.b",
  "fullName": "Nguyen Van B",
  "permissions": []
}
```

### `GET /notifications`

Returns all notifications owned by the authenticated user, including both read and unread items, ordered from newest to oldest.

Status codes: `200 OK`, `401 Unauthorized`.

### Admin role reassignment

#### `PUT /users/{userId}/role`

Changes the role of a non-admin user. The endpoint requires an authenticated `ADMIN` account. It supports transitions such as `DOCTOR` to `HEAD_OF_DEPARTMENT` and the reverse transition without changing the user's profile, doctor-specific data, examinations, or audit history.

Request:

```json
{
  "roleId": 3
}
```

The target role must exist and cannot be `ADMIN`. An administrator cannot change their own role or change the role of another administrator. On success, the service updates only `users.role_id`. `users.user_type` identifies the medical-staff entity and remains `DOCTOR` for doctors, heads of department, nurses, and future medical roles. Access tokens issued before the change retain their old authority claims until they expire (15 minutes by default), so the client should refresh its token or sign in again to receive the new role.

Response: the updated `UserResponse` object, including the new `role`; `userType` remains unchanged.

Status codes: `200 OK`, `400 Bad Request` for an invalid role transition or payload, `401 Unauthorized`, `403 Forbidden` for non-admin callers, `404 Not Found` when the target user or role does not exist.

### `DELETE /permissions/{id}`

Deletes a permission and removes its role assignments and dependency references. Requires the `ADMIN` role and returns no response body.

Status codes: `204 No Content`, `400 Bad Request` when the permission does not exist, `401 Unauthorized`, `403 Forbidden`.

### `DELETE /features/{id}`

Deletes a feature and its permissions after removing related role assignments and dependency references. Requires the `ADMIN` role and returns no response body.

Status codes: `204 No Content`, `400 Bad Request` when the feature does not exist, `401 Unauthorized`, `403 Forbidden`.

## `POST /auth/change-password`

Endpoint for users to change their password. This is required for first-time login activation and can be used to update passwords securely.

### Request

```json
{
  "username": "admin",
  "oldPassword": "oldPassword123",
  "newPassword": "newPassword456"
}
```

### Response

```text
Password changed successfully
```

### Status Codes

- `200 OK`: Password changed successfully
- `400 Bad Request`: Invalid input or incorrect credentials
- `500 Internal Server Error`: Unexpected server error

## `POST /auth/forgot-password`

Endpoint to initiate the forgot password flow. Generates a 6-digit OTP and sends it via email.

### Request

```json
{
  "email": "admin@example.com"
}
```

### Response

```text
If the email exists, a password reset token has been sent.
```

### Status Codes

- `200 OK`: Request processed successfully
- `400 Bad Request`: Invalid email format

## `POST /auth/reset-password`

Endpoint to reset the password using the 6-digit OTP sent to the user's email.

### Request

```json
{
  "email": "admin@example.com",
  "token": "123456",
  "newPassword": "newPassword123"
}
```

### Response

```text
Password reset successfully
```

## `GET /users/staff/search`

Retrieves a paginated list of medical staff (Doctors and Head of Departments). Supports search and status filtering.

### Query Parameters

- `keyword` (Optional): Search term for username, email, or full name.
- `status` (Optional): Filter by status (`ACTIVE` or `INACTIVE`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

```http
GET /users/staff/search?keyword=doctor&status=ACTIVE&page=0&size=10
```

### Response

```json
{
  "content": [
    {
      "id": 2,
      "username": "doctor.smith",
      "fullName": "John Smith",
      "email": "doctor@example.com",
      "phone": "0987654321",
      "role": { "id": 2, "code": "DOCTOR", "name": "Doctor" },
      "status": "ACTIVE",
      "userType": "DOCTOR",
      "avatarUrl": "/images/avatar/123.jpg",
      "createdAt": "2026-06-01T10:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not allowed (requires ADMIN, VIEW_USER_LIST, or HEAD_OF_DEPARTMENT)

## `PATCH /users/{userId}/status/toggle`

Toggles the active/inactive status of a user. If deactivating, an `inactiveReason` is required and an email is sent to the user. Reactivating clears the reason and sends a welcome back email. Target cannot be an ADMIN.

### Request

```json
{
  "inactiveReason": "Violation of policies"
}
```

### Response

```json
{
  "id": 2,
  "username": "doctor.smith",
  "status": "INACTIVE",
  "avatarUrl": "/images/avatar/123.jpg"
}
```

### Status Codes

- `200 OK`: Status toggled successfully
- `400 Bad Request`: Missing inactive reason when deactivating, or attempting to toggle an ADMIN user
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not allowed (requires ADMIN or UPDATE_USER)
- `404 Not Found`: User not found

### `GET /doctors`

Retrieves a paginated list of all doctors. Supports search, filter, and sorting.

### Query Parameters

- `keyword` (Optional): Search term for code, name, email, phone, or specialization.
- `specialization` (Optional): Filter by specialization.
- `status` (Optional): Filter by status (e.g., `ACTIVE`, `INACTIVE`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).
- `sort` (Optional): Sort criteria in the format `property,direction` (default: `createdAt,desc`).

### Request

```http
GET /doctors?page=0&size=5&keyword=Nguyen&status=ACTIVE&sort=fullName,asc
```

### Response

```json
{
  "content": [
    {
      "id": 1,
      "username": "doctor.b",
      "email": "doctor.b@example.com",
      "fullName": "Nguyen Van B",
      "phone": "0987654321",
      "role": "DOCTOR",
      "status": "ACTIVE",
      "doctorCode": "DR12345",
      "licenseNumber": "LIC98765",
      "specialization": "Orthopedics",
      "yearsOfExperience": 10,
      "academicTitle": "PhD",
      "degree": "MD",
      "bio": "Expert in knee joints",
      "position": "Head of Orthopedics",
      "avatarUrl": "http://example.com/avatar.jpg"
    }
  ],
  "pageNumber": 0,
  "pageSize": 5,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed
- `404 Not Found`: resource does not exist
- `500 Internal Server Error`: unexpected server error

## `GET /doctors/active`

Retrieves a list of all active doctors.

### Request

No request body.

### Response

```json
[
  {
    "id": 1,
    "username": "doctor.b",
    "email": "doctor.b@example.com",
    "fullName": "Nguyen Van B",
    "phone": "0987654321",
    "role": "DOCTOR",
    "status": "ACTIVE",
    "doctorCode": "DR12345",
    "licenseNumber": "LIC98765",
    "specialization": "Orthopedics",
    "yearsOfExperience": 10,
    "academicTitle": "PhD",
    "degree": "MD",
    "bio": "Expert in knee joints",
    "position": "Head of Orthopedics",
    "avatarUrl": "http://example.com/avatar.jpg"
  }
]
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `POST /doctors/{id}/activate`

Activates a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor activated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `POST /doctors/{id}/deactivate`

Deactivates a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor deactivated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `DELETE /doctors/{id}`

Deactivates (soft deletes) a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor deactivated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `PUT /doctors/{id}`

Updates an existing doctor's profile.

### Request

```json
{
  "fullName": "Nguyen Van B Updated",
  "phone": "0987654323",
  "yearsOfExperience": 12,
  "degree": "Ph.D. in AI Health",
  "biography": "An experienced doctor specializing in AI analysis."
}
```

### Response

```json
{
  "id": 1,
  "username": "doctor.b",
  "email": "doctor.b@example.com",
  "fullName": "Nguyen Van B Updated",
  "phone": "0987654323",
  "role": "DOCTOR",
  "status": "ACTIVE",
  "yearsOfExperience": 12,
  "degree": "Ph.D. in AI Health",
  "biography": "An experienced doctor specializing in AI analysis."
}
```

### Status Codes

- `200 OK`: Doctor updated successfully
- `400 Bad Request`: Invalid input fields
- `401 Unauthorized`: Authentication is required
- `404 Not Found`: Doctor not found

## `GET /doctors/profile`

Retrieves the profile of the currently authenticated doctor.

### Request

No request body.

### Response

```json
{
  "id": 1,
  "username": "doctor.b",
  "email": "doctor.b@example.com",
  "fullName": "Nguyen Van B",
  "phone": "0987654321",
  "role": "DOCTOR",
  "status": "ACTIVE",
  "yearsOfExperience": 10,
  "degree": "MD",
  "biography": "Experienced doctor."
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not a DOCTOR

## `PUT /doctors/profile`

Updates the profile of the currently authenticated doctor.
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed
- `404 Not Found`: resource does not exist
- `500 Internal Server Error`: unexpected server error

## `GET /doctors/active`

Retrieves a list of all active doctors.

### Request

No request body.

### Response

```json
[
  {
    "id": 1,
    "username": "doctor.b",
    "email": "doctor.b@example.com",
    "fullName": "Nguyen Van B",
    "phone": "0987654321",
    "role": "DOCTOR",
    "status": "ACTIVE",
    "doctorCode": "DR12345",
    "licenseNumber": "LIC98765",
    "specialization": "Orthopedics",
    "yearsOfExperience": 10,
    "academicTitle": "PhD",
    "degree": "MD",
    "bio": "Expert in knee joints",
    "position": "Head of Orthopedics",
    "avatarUrl": "http://example.com/avatar.jpg"
  }
]
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `POST /doctors/{id}/activate`

Activates a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor activated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `POST /doctors/{id}/deactivate`

Deactivates a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor deactivated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `DELETE /doctors/{id}`

Deactivates (soft deletes) a doctor by ID.

### Request

No request body. Replace `{id}` with the doctor ID.

### Response

No response body.

### Status Codes

- `200 OK`: Doctor deactivated successfully
- `400 Bad Request`: Doctor with the given ID not found
- `401 Unauthorized`: Authentication is required

## `GET /notifications/unread`

Retrieves all unread notifications for the currently authenticated user.

### Request

No request body.

### Response

```json
[
  {
    "id": 1,
    "title": "Welcome to HealthSync",
    "message": "This is a real-time STOMP notification test.",
    "type": "SYSTEM",
    "isRead": false,
    "createdAt": "2026-06-07T10:00:00"
  }
]
```

## `PUT /notifications/{id}/read`

Marks a specific notification as read.

### Request

No request body. Replace `{id}` with the notification ID.

### Response

```text
Notification marked as read
```

## `POST /notifications/send`

Trigger a test notification to a specific user.

### Request

```json
{
  "fullName": "Doctor B Self Updated",
  "phone": "0987654325",
  "yearsOfExperience": 15,
  "degree": "Ph.D. in AI Health updated",
  "biography": "Self updated profile."
  "userId": 1,
  "title": "New Appointment",
  "message": "You have a new appointment at 10 AM.",
  "type": "APPOINTMENT"
}
```

### Response

```text
Notification sent successfully
```

## WebSocket Connection (STOMP)

Clients can connect to the real-time notification server using STOMP over WebSocket.

- **Endpoint**: `ws://localhost:8080/api/v1/ws`
- **Authentication**: Pass the JWT token in the `CONNECT` frame headers (`Authorization: Bearer <token>`).
- **Subscription**: Subscribe to `/user/queue/notifications` to receive events.

## `POST /s3/test-upload`

Uploads a test file to the configured AWS S3 bucket.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `folderName` (Text): The destination folder name in S3.
  - `fileName` (Text): The desired file name in S3.
  - `file` (File): The actual file/image to upload.

### Response

```text
Successfully uploaded to S3: s3://test-bucket-819109476069-ap-southeast-1-an/test-folder/sample.txt
```

## `POST /auth/login`

Endpoint for user login.

### Request

```json
{
  "username": "admin",
  "password": "password123"
}
```

### Response

```json
{
  "token": "eyJhbGciOiJIUz...",
  "username": "admin",
  "role": "ADMIN"
}
```

### Status Codes

- `200 OK`: login successful
- `401 Unauthorized`: incorrect credentials

## `POST /users`

Creates a generic user. Restricts assignment of the ADMIN role.

### Request

```json
{
  "fullName": "John Doe",
  "email": "johndoe@example.com",
  "phone": "0123456789",
  "roleId": 2
}
```

### Response

```json
{
  "id": 1,
  "username": "doctor.b",
  "email": "doctor.b@example.com",
  "fullName": "Doctor B Self Updated",
  "phone": "0987654325",
  "role": "DOCTOR",
  "status": "ACTIVE",
  "yearsOfExperience": 15,
  "degree": "Ph.D. in AI Health updated",
  "biography": "Self updated profile."
  "fullName": "John Doe",
  "email": "johndoe@example.com",
  "phone": "0123456789",
  "role": "DOCTOR",
  "status": "ACTIVE",
  "createdAt": "2026-06-19T10:00:00"
}
```

### Status Codes

- `201 Created`: User created successfully
- `400 Bad Request`: Invalid input validation
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed

## `POST /patients`

Registers a new patient.

### Request

```json
{
  "patientCode": "PT001",
  "fullName": "Alice Smith",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "123 Main St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789"
}
```

### Response

```json
{
  "id": 1,
  "patientCode": "PT001",
  "fullName": "Alice Smith",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "123 Main St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789",
  "createdAt": "2026-06-19T10:00:00",
  "updatedAt": "2026-06-19T10:00:00"
}
```

### Status Codes

- `201 Created`: Patient created successfully
- `400 Bad Request`: Invalid input validation
- `401 Unauthorized`: authentication is required

## `GET /patients`

Retrieves a paginated list of patients with optional filtering.

### Query Parameters

- `patientCode` (Optional)
- `fullName` (Optional)
- `phone` (Optional)
- `email` (Optional)
- `page` (Optional): default 0
- `size` (Optional): default 10

### Response

```json
{
  "content": [
    {
      "id": 1,
      "patientCode": "PT001",
      "fullName": "Alice Smith",
      "dateOfBirth": "1990-01-01",
      "gender": "FEMALE",
      "phone": "0987654321",
      "email": "alice@example.com",
      "address": "123 Main St",
      "emergencyContactName": "Bob Smith",
      "emergencyContactPhone": "0123456789",
      "createdAt": "2026-06-19T10:00:00",
      "updatedAt": "2026-06-19T10:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: request succeeded
- `401 Unauthorized`: authentication is required

## `PUT /patients/{id}`

Updates a patient's information.

### Request

```json
{
  "fullName": "Alice Johnson",
  "address": "456 Elm St",
  "phone": "0987654321",
  "email": "alice@example.com"
}
```

### Response

```json
{
  "id": 1,
  "patientCode": "PT001",
  "fullName": "Alice Johnson",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "456 Elm St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789",
  "createdAt": "2026-06-19T10:00:00",
  "updatedAt": "2026-06-19T10:05:00"
}
```

### Status Codes

- `200 OK`: Patient updated successfully
- `400 Bad Request`: Invalid input or patient not found
- `401 Unauthorized`: authentication is required

## `DELETE /patients/{id}`

Deletes a patient by ID.

### Request

No request body. Replace `{id}` with the patient ID.

### Response

No response body.

### Status Codes

- `200 OK`: Patient deleted successfully
- `400 Bad Request`: Patient not found
- `401 Unauthorized`: authentication is required

## `POST /dicom/upload/batch`

Uploads multiple DICOM files synchronously, parses metadata, extracts images, and returns a session for verification before persisting records.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files` (List of Files): Multiple `.dcm` files.

### Response

```json
{
  "message": null,
  "uploadSessionId": "140d6df5-f47d-41d1-add1-2201a85bce7c",
  "errors": [],
  "successfulPatients": []
}
```

### Status Codes

- `200 OK`: Files processed successfully (pending verify)
- `400 Bad Request`: Invalid file format or request
- `401 Unauthorized`: Authentication is required

## `POST /examinations/{id}/generate-report`

Generates and saves a PDF report for a given examination.

### Request

No request body. Replace `{id}` with the examination ID.

### Response

```text
Report generated and saved at: D:/HealthSync_Exports/report_EX-001_1a2b3c4d.pdf
```

### Status Codes

- `200 OK`: Report generated successfully
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user does not have `GENERATE_PDF_REPORT` permission
- `500 Internal Server Error`: Failed to generate PDF

## `POST /dicom/upload/zip`

Uploads a single ZIP file containing multiple DICOM files synchronously, extracts and processes them.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `file` (File): A `.zip` file containing `.dcm` files.

### Response

Returns the same `BatchDicomUploadResponse` structure as `/dicom/upload/batch`.



Verifies and commits a pending DICOM upload session into the database.

### Request

```json
{
  "uploadSessionId": "140d6df5-f47d-41d1-add1-2201a85bce7c",
  "acceptedPatientCodes": [
    "PT001"
  ]
}
```

### Response

`json
[
  {
    "grade": 1,
    "patientCount": 5
  },
  {
    "grade": 2,
    "patientCount": 3
  }
]



### Status Codes

- `200 OK`: Data saved successfully
- `400 Bad Request`: Session expired or invalid patient codes
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not a DOCTOR

## `GET /notifications/unread`

Retrieves all unread notifications for the currently authenticated user.

### Request

No request body.

### Response

```json
[
  {
    "id": 1,
    "title": "Welcome to HealthSync",
    "message": "This is a real-time STOMP notification test.",
    "type": "SYSTEM",
    "isRead": false,
    "createdAt": "2026-06-07T10:00:00"
  }
]
```

## `PUT /notifications/{id}/read`

Marks a specific notification as read.

### Request

No request body. Replace `{id}` with the notification ID.

### Response

```text
Notification marked as read
```

## `POST /notifications/send`

Trigger a test notification to a specific user.

### Request

```json
{
  "userId": 1,
  "title": "New Appointment",
  "message": "You have a new appointment at 10 AM.",
  "type": "APPOINTMENT"
}
```

### Response

```text
Notification sent successfully
```

## `GET /examinations/total`

Retrieves the total number of examinations based on the user's role.

### Query Parameters

- `userId` (Required): The ID of the user requesting the total.

### Request

```http
GET /examinations/total?userId=1
```

### Response

```json
150
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Missing user ID or invalid user
- `401 Unauthorized`: Authentication is required

## `GET /examinations/total-severe`

Retrieves the total number of severe examinations (KL3, KL4) based on the user's role.

### Query Parameters

- `userId` (Required): The ID of the user.

### Request

```http
GET /examinations/total-severe?userId=1
```

### Response

```json
25
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Missing user ID or invalid user
- `401 Unauthorized`: Authentication is required

## `GET /examinations/total-verified`

Retrieves the total number of verified (REVIEWED) examinations based on the user's role.

### Query Parameters

- `userId` (Required): The ID of the user.

### Request

```http
GET /examinations/total-verified?userId=1
```

### Response

```json
100
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Missing user ID or invalid user
- `401 Unauthorized`: Authentication is required

## `GET /examinations/total-unverified`

Retrieves the total number of unverified examinations based on the user's role.

### Query Parameters

- `userId` (Required): The ID of the user.

### Request

```http
GET /examinations/total-unverified?userId=1
```

### Response

```json
50
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Missing user ID or invalid user
- `401 Unauthorized`: Authentication is required

## WebSocket Connection (STOMP)

Clients can connect to the real-time notification server using STOMP over WebSocket.

- **Endpoint**: `ws://localhost:8080/api/v1/ws`
- **Authentication**: Pass the JWT token in the `CONNECT` frame headers (`Authorization: Bearer <token>`).
- **Subscription**: Subscribe to `/user/queue/notifications` to receive events.

## `POST /s3/test-upload`

Uploads a test file to the configured AWS S3 bucket.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `folderName` (Text): The destination folder name in S3.
  - `fileName` (Text): The desired file name in S3.
  - `file` (File): The actual file/image to upload.

### Response

```text
Successfully uploaded to S3: s3://test-bucket-819109476069-ap-southeast-1-an/test-folder/sample.txt
```

## `POST /auth/login`

Endpoint for user login.

### Request

```json
{
  "username": "admin",
  "password": "password123"
}
```

### Response

```json
{
  "token": "eyJhbGciOiJIUz...",
  "username": "admin",
  "role": "ADMIN"
}
```

### Status Codes

- `200 OK`: login successful
- `401 Unauthorized`: incorrect credentials

## `POST /users`

Creates a generic user. Restricts assignment of the ADMIN role.

### Request

```json
{
  "fullName": "John Doe",
  "email": "johndoe@example.com",
  "phone": "0123456789",
  "roleId": 2
}
```

### Response

```json
{
  "id": 1,
  "fullName": "John Doe",
  "email": "johndoe@example.com",
  "phone": "0123456789",
  "role": "DOCTOR",
  "status": "ACTIVE",
  "createdAt": "2026-06-19T10:00:00"
}
```

### Status Codes

- `201 Created`: User created successfully
- `400 Bad Request`: Invalid input validation
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed

## `POST /patients`

Registers a new patient.

### Request

```json
{
  "patientCode": "PT001",
  "fullName": "Alice Smith",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "123 Main St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789"
}
```

### Response

```json
{
  "id": 1,
  "patientCode": "PT001",
  "fullName": "Alice Smith",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "123 Main St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789",
  "createdAt": "2026-06-19T10:00:00",
  "updatedAt": "2026-06-19T10:00:00"
}
```

### Status Codes

- `201 Created`: Patient created successfully
- `400 Bad Request`: Invalid input validation
- `401 Unauthorized`: authentication is required

## `GET /patients`

Retrieves a paginated list of patients with optional filtering.

### Query Parameters

- `patientCode` (Optional)
- `fullName` (Optional)
- `phone` (Optional)
- `email` (Optional)
- `page` (Optional): default 0
- `size` (Optional): default 10

### Response

```json
{
  "content": [
    {
      "id": 1,
      "patientCode": "PT001",
      "fullName": "Alice Smith",
      "dateOfBirth": "1990-01-01",
      "gender": "FEMALE",
      "phone": "0987654321",
      "email": "alice@example.com",
      "address": "123 Main St",
      "emergencyContactName": "Bob Smith",
      "emergencyContactPhone": "0123456789",
      "createdAt": "2026-06-19T10:00:00",
      "updatedAt": "2026-06-19T10:00:00"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: request succeeded
- `401 Unauthorized`: authentication is required

## `PUT /patients/{id}`

Updates a patient's information.

### Request

```json
{
  "fullName": "Alice Johnson",
  "address": "456 Elm St",
  "phone": "0987654321",
  "email": "alice@example.com"
}
```

### Response

```json
{
  "id": 1,
  "patientCode": "PT001",
  "fullName": "Alice Johnson",
  "dateOfBirth": "1990-01-01",
  "gender": "FEMALE",
  "phone": "0987654321",
  "email": "alice@example.com",
  "address": "456 Elm St",
  "emergencyContactName": "Bob Smith",
  "emergencyContactPhone": "0123456789",
  "createdAt": "2026-06-19T10:00:00",
  "updatedAt": "2026-06-19T10:05:00"
}
```

### Status Codes

- `200 OK`: Patient updated successfully
- `400 Bad Request`: Invalid input or patient not found
- `401 Unauthorized`: authentication is required

## `DELETE /patients/{id}`

Deletes a patient by ID.

### Request

No request body. Replace `{id}` with the patient ID.

### Response

No response body.

### Status Codes

- `200 OK`: Patient deleted successfully
- `400 Bad Request`: Patient not found
- `401 Unauthorized`: authentication is required

## `POST /dicom/upload`

Uploads a DICOM file, parses metadata, extracts the image, creates patient and examination records.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `file` (File): The multipart DICOM file.

### Response

```json
{
  "patient": {
    "id": 1,
    "patientCode": null,
    "fullName": "Nguyen Van A",
    "email": "hn-2026-0099@healthsync.com"
  },
  "examinations": []
}
```

### Status Codes

- `201 Created`: DICOM uploaded and processed successfully
- `400 Bad Request`: Uploaded file is empty or invalid
- `401 Unauthorized`: authentication is required

## `POST /dicom/upload/batch`

Uploads multiple DICOM files asynchronously. The server will immediately return a success message while processing metadata and extracting images in the background.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files` (List of Files): The multipart DICOM files.

### Response

```json
{
  "message": "Successfully received 2 DICOM files. Processing in background.",
  "errors": [],
  "successfulPatients": []
}
```

### Status Codes

- `200 OK`: DICOM batch uploaded and accepted for processing
- `400 Bad Request`: Uploaded files are empty or invalid
- `401 Unauthorized`: Authentication is required

## `GET /patients/{patientId}/details`

Retrieves a patient's details and their examination images.

### Request

No request body. Replace `{patientId}` with the patient's username (which acts as PatientID from DICOM).

### Response

```json
{
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "examinations": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "images": [
        {
          "examinationId": 1,
          "encounterCode": "...",
          "status": "CREATED",
          "visitTime": "2023-10-15T10:30:00",
          "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
        }
      ]
    }
  ]
}
```

### Status Codes

- `200 OK`: Details retrieved successfully
- `400 Bad Request`: Patient not found
- `401 Unauthorized`: Authentication is required



## `GET /permissions/tree`

Retrieves the hierarchical tree of all features and their corresponding permissions. Used for rendering the role management UI.

### Response

```json
[
  {
    "id": 1,
    "name": "User & Account Management",
    "description": "Quản lý Tài khoản",
    "permissions": [
      {
        "id": 1,
        "code": "READ_OWN_PROFILE",
        "name": "Xem hồ sơ cá nhân",
        "priority": 1,
        "presentation": "profile_screen",
        "requiresPermissionId": null
      }
    ]
  }
]
```

### Status Codes

- `200 OK`: Tree retrieved successfully
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `GET /permissions/role/{roleName}`

Retrieves the list of permission IDs currently assigned to a specific role.

### Request

No request body. Replace `{roleName}` with the role name (e.g., `DOCTOR`, `ADMIN`).

### Response

```json
[1, 2, 4, 5, 8]
```

### Status Codes

- `200 OK`: List retrieved successfully
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /permissions/role/{roleName}`

Updates the permissions assigned to a specific role. Replaces all existing permissions for that role.

### Request

```json
{
  "permissionIds": [1, 2, 3, 4, 5]
}
```

### Status Codes

- `200 OK`: Role permissions updated successfully
- `400 Bad Request`: Invalid role name or permission ID
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `POST /features`

Creates a new feature (module) to group permissions.

### Request

```json
{
  "name": "Reporting Module",
  "description": "Module for generating system reports"
}
```

### Status Codes

- `201 Created`: Feature created successfully
- `400 Bad Request`: Feature name already exists
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /features/{id}`

Updates an existing feature's details.

### Request

```json
{
  "name": "Advanced Reporting",
  "description": "Updated description"
}
```

### Status Codes

- `200 OK`: Feature updated successfully
- `400 Bad Request`: Feature not found or name conflict
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `POST /permissions`

Creates a new permission under a specific feature. Optionally links to a parent permission via `requiresPermissionId`.

### Request

```json
{
  "featureId": 1,
  "requiresPermissionId": null,
  "code": "EXPORT_REPORTS",
  "name": "Export data to Excel",
  "priority": 50,
  "presentation": "report_screen"
}
```

### Status Codes

- `201 Created`: Permission created successfully
- `400 Bad Request`: Invalid feature ID or parent permission ID
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /permissions/{id}`

Updates an existing permission's details and dependencies.

### Request

```json
{
  "requiresPermissionId": 2,
  "code": "EXPORT_PDF",
  "name": "Export data to PDF",
  "priority": 40,
  "presentation": "report_screen"
}
```

### Status Codes

- `200 OK`: Permission updated successfully
- `400 Bad Request`: Permission not found or circular dependency
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

# DICOM Operations

Endpoints for handling DICOM files and imaging.

## `POST /dicom/upload/batch`

Uploads multiple DICOM files asynchronously. The server processes the files in the background, extracting metadata and generating PNG thumbnails. Processing results, including duplicate/invalid file errors and successful patient/examination mappings, are broadcasted via STOMP WebSockets.

### Request

`multipart/form-data`
- `files`: An array of DICOM `.dcm` files.

### HTTP Response

```json
{
  "message": "DICOM files accepted for background processing.",
  "status": "PROCESSING",
  "filesCount": 2,
  "batchId": "6c84fb90-12c4-11e1-840d-7b25c5ee775a"
}
```

### Status Codes

- `202 Accepted`: Files accepted for asynchronous processing
- `400 Bad Request`: No files provided or missing authentication
- `401 Unauthorized`: Authentication is required

---

## `POST /dicom/upload/zip-batch`

Uploads a single ZIP file containing multiple DICOM files (can contain nested folders or nested ZIP files). The server processes the files asynchronously in the background.

### Request

`multipart/form-data`
- `file`: The `.zip` file containing `.dcm` files.

### HTTP Response

```json
{
  "message": "ZIP batch accepted for background processing.",
  "status": "PROCESSING",
  "zipFileName": "dataset.zip",
  "batchId": "6c84fb90-12c4-11e1-840d-7b25c5ee775a"
}
```

### Status Codes

- `202 Accepted`: File accepted for asynchronous processing
- `400 Bad Request`: No file provided or missing authentication
- `401 Unauthorized`: Authentication is required

---

## Asynchronous Notifications (WebSocket / STOMP)

Clients subscribed to `/user/queue/notifications` will receive real-time updates regarding their uploads.

### Progress Notifications (`type="SYSTEM"`)

Sent immediately upon receiving the upload to indicate progress.
- **Title**: `Tiếp nhận File ZIP` or `Đang xử lý DICOM`
- **Message**: "Hệ thống đang tiến hành giải nén..." or "Hệ thống đang trích xuất dữ liệu từ X file DICOM..."

### Final Result Notification (`type="DICOM_BATCH_RESULT"`)

Sent when the entire batch is finished processing. The `message` field contains a JSON string of the `BatchDicomUploadResponse`.

```json
{
  "id": 5,
  "title": "DICOM Upload Complete",
  "type": "DICOM_BATCH_RESULT",
  "isRead": false,
  "createdAt": "2026-07-08T23:15:00.123",
  "message": "{\"errors\":[{\"fileName\":\"duplicate.dcm\",\"errorMessage\":\"File DICOM đã tồn tại trên hệ thống.\"}],\"successfulPatients\":[{\"patient\":{\"id\":1,\"patientCode\":\"12345\"},\"recentExaminations\":[{\"examinationId\":1,\"status\":\"PENDING_REVIEW\",\"images\":[{\"imageUrl\":\"/api/v1/dicom/instances/1/image\"}]}]}]}"
}
```

## `GET /dicom/instances/{id}/image`

Retrieves the PNG image associated with a specific DICOM instance.

### Path Parameters

- `id`: The ID of the DICOM instance.

### Response

Returns the image binary data with `Content-Type: image/png`.

### Status Codes

- `200 OK`: Image returned successfully
- `404 Not Found`: Instance or image not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations`

Retrieves a paginated list of examinations.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/{id}`

Retrieves detailed information of an examination by ID, including patient details and associated DICOM images.

### Path Parameters

- `id`: The ID of the examination.

### Response

```json
{
  "examinationId": 1,
  "encounterCode": "...",
  "status": "CREATED",
  "studyDate": "2023-10-15",
  "visitTime": "2023-10-15T10:30:00",
  "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
  "bodyPart": "KNEE",
  "referringPhysician": "Dr. Smith",
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "images": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "visitTime": "2023-10-15T10:30:00",
      "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
    }
  ]
}
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Examination not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations/doctor/{doctorId}`

Retrieves a paginated list of examinations for a specific doctor.

### Path Parameters

- `doctorId`: The ID of the doctor.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/patient/{patientId}`

Retrieves a paginated list of examinations for a specific patient.

### Path Parameters

- `patientId`: The ID of the patient.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)
# #   D i c o m   E n d p o i n t s 
 
 
- `201 Created`: DICOM uploaded and processed successfully
- `400 Bad Request`: Uploaded file is empty or invalid
- `401 Unauthorized`: authentication is required

## `POST /dicom/upload/batch`

Uploads multiple DICOM files asynchronously. The server will immediately return a success message while processing metadata and extracting images in the background.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `files` (List of Files): The multipart DICOM files.

### Response

```json
{
  "message": "Successfully received 2 DICOM files. Processing in background.",
  "errors": [],
  "successfulPatients": []
}
```

### Status Codes

- `200 OK`: DICOM batch uploaded and accepted for processing
- `400 Bad Request`: Uploaded files are empty or invalid
- `401 Unauthorized`: Authentication is required

## `GET /patients/{patientId}/details`

Retrieves a patient's details and their examination images.

### Request

No request body. Replace `{patientId}` with the patient's username (which acts as PatientID from DICOM).

### Response

```json
{
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "examinations": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "images": [
        {
          "examinationId": 1,
          "encounterCode": "...",
          "status": "CREATED",
          "visitTime": "2023-10-15T10:30:00",
          "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
        }
      ]
    }
  ]
}
```

### Status Codes

- `200 OK`: Details retrieved successfully
- `400 Bad Request`: Patient not found
- `401 Unauthorized`: Authentication is required



## `GET /permissions/tree`

Retrieves the hierarchical tree of all features and their corresponding permissions. Used for rendering the role management UI.

### Response

```json
[
  {
    "id": 1,
    "name": "User & Account Management",
    "description": "Quản lý Tài khoản",
    "permissions": [
      {
        "id": 1,
        "code": "READ_OWN_PROFILE",
        "name": "Xem hồ sơ cá nhân",
        "priority": 1,
        "presentation": "profile_screen",
        "requiresPermissionId": null
      }
    ]
  }
]
```

### Status Codes

- `200 OK`: Tree retrieved successfully
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `GET /permissions/role/{roleName}`

Retrieves the list of permission IDs currently assigned to a specific role.

### Request

No request body. Replace `{roleName}` with the role name (e.g., `DOCTOR`, `ADMIN`).

### Response

```json
[1, 2, 4, 5, 8]
```

### Status Codes

- `200 OK`: List retrieved successfully
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /permissions/role/{roleName}`

Updates the permissions assigned to a specific role. Replaces all existing permissions for that role.

### Request

```json
{
  "permissionIds": [1, 2, 3, 4, 5]
}
```

### Status Codes

- `200 OK`: Role permissions updated successfully
- `400 Bad Request`: Invalid role name or permission ID
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `POST /features`

Creates a new feature (module) to group permissions.

### Request

```json
{
  "name": "Reporting Module",
  "description": "Module for generating system reports"
}
```

### Status Codes

- `201 Created`: Feature created successfully
- `400 Bad Request`: Feature name already exists
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /features/{id}`

Updates an existing feature's details.

### Request

```json
{
  "name": "Advanced Reporting",
  "description": "Updated description"
}
```

### Status Codes

- `200 OK`: Feature updated successfully
- `400 Bad Request`: Feature not found or name conflict
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `POST /permissions`

Creates a new permission under a specific feature. Optionally links to a parent permission via `requiresPermissionId`.

### Request

```json
{
  "featureId": 1,
  "requiresPermissionId": null,
  "code": "EXPORT_REPORTS",
  "name": "Export data to Excel",
  "priority": 50,
  "presentation": "report_screen"
}
```

### Status Codes

- `201 Created`: Permission created successfully
- `400 Bad Request`: Invalid feature ID or parent permission ID
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## `PUT /permissions/{id}`

Updates an existing permission's details and dependencies.

### Request

```json
{
  "requiresPermissionId": 2,
  "code": "EXPORT_PDF",
  "name": "Export data to PDF",
  "priority": 40,
  "presentation": "report_screen"
}
```

### Status Codes

- `200 OK`: Permission updated successfully
- `400 Bad Request`: Permission not found or circular dependency
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

# DICOM Operations

Endpoints for handling DICOM files and imaging.

## `POST /dicom/upload/batch`

Uploads multiple DICOM files asynchronously. The server processes the files in the background, extracting metadata and generating PNG thumbnails. Processing results, including duplicate/invalid file errors and successful patient/examination mappings, are broadcasted via STOMP WebSockets.

### Request

`multipart/form-data`
- `files`: An array of DICOM `.dcm` files.

### HTTP Response

```json
{
  "message": "DICOM files accepted for background processing.",
  "status": "PROCESSING",
  "filesCount": 2,
  "batchId": "6c84fb90-12c4-11e1-840d-7b25c5ee775a"
}
```

### Status Codes

- `202 Accepted`: Files accepted for asynchronous processing
- `400 Bad Request`: No files provided or missing authentication
- `401 Unauthorized`: Authentication is required

---

## `POST /dicom/upload/zip-batch`

Uploads a single ZIP file containing multiple DICOM files (can contain nested folders or nested ZIP files). The server processes the files asynchronously in the background.

### Request

`multipart/form-data`
- `file`: The `.zip` file containing `.dcm` files.

### HTTP Response

```json
{
  "message": "ZIP batch accepted for background processing.",
  "status": "PROCESSING",
  "zipFileName": "dataset.zip",
  "batchId": "6c84fb90-12c4-11e1-840d-7b25c5ee775a"
}
```

### Status Codes

- `202 Accepted`: File accepted for asynchronous processing
- `400 Bad Request`: No file provided or missing authentication
- `401 Unauthorized`: Authentication is required

---

## Asynchronous Notifications (WebSocket / STOMP)

Clients subscribed to `/user/queue/notifications` will receive real-time updates regarding their uploads.

### Progress Notifications (`type="SYSTEM"`)

Sent immediately upon receiving the upload to indicate progress.
- **Title**: `Tiếp nhận File ZIP` or `Đang xử lý DICOM`
- **Message**: "Hệ thống đang tiến hành giải nén..." or "Hệ thống đang trích xuất dữ liệu từ X file DICOM..."

### Final Result Notification (`type="DICOM_BATCH_RESULT"`)

Sent when the entire batch is finished processing. The `message` field contains a JSON string of the `BatchDicomUploadResponse`.

```json
{
  "id": 5,
  "title": "DICOM Upload Complete",
  "type": "DICOM_BATCH_RESULT",
  "isRead": false,
  "createdAt": "2026-07-08T23:15:00.123",
  "message": "{\"errors\":[{\"fileName\":\"duplicate.dcm\",\"errorMessage\":\"File DICOM đã tồn tại trên hệ thống.\"}],\"successfulPatients\":[{\"patient\":{\"id\":1,\"patientCode\":\"12345\"},\"recentExaminations\":[{\"examinationId\":1,\"status\":\"PENDING_REVIEW\",\"images\":[{\"imageUrl\":\"/api/v1/dicom/instances/1/image\"}]}]}]}"
}
```

## `GET /dicom/instances/{id}/image`

Retrieves the PNG image associated with a specific DICOM instance.

### Path Parameters

- `id`: The ID of the DICOM instance.

### Response

Returns the image binary data with `Content-Type: image/png`.

### Status Codes

- `200 OK`: Image returned successfully
- `404 Not Found`: Instance or image not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations`

Retrieves a paginated list of examinations.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/{id}`

Retrieves detailed information of an examination by ID, including patient details and associated DICOM images.

### Path Parameters

- `id`: The ID of the examination.

### Response

```json
{
  "examinationId": 1,
  "encounterCode": "...",
  "status": "CREATED",
  "studyDate": "2023-10-15",
  "visitTime": "2023-10-15T10:30:00",
  "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
  "bodyPart": "KNEE",
  "referringPhysician": "Dr. Smith",
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "images": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "visitTime": "2023-10-15T10:30:00",
      "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
    }
  ]
}
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Examination not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations/doctor/{doctorId}`

Retrieves a paginated list of examinations for a specific doctor.

### Path Parameters

- `doctorId`: The ID of the doctor.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/patient/{patientId}`

Retrieves a paginated list of examinations for a specific patient.

### Path Parameters

- `patientId`: The ID of the patient.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

Retrieves the PNG image associated with a specific DICOM instance.

### Path Parameters

- `id`: The ID of the DICOM instance.

### Response

Returns the image binary data with `Content-Type: image/png`.

### Status Codes

- `200 OK`: Image returned successfully
- `404 Not Found`: Instance or image not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations`

Retrieves a paginated list of examinations.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/{id}`

Retrieves detailed information of an examination by ID, including patient details and associated DICOM images.

### Path Parameters

- `id`: The ID of the examination.

### Response

```json
{
  "examinationId": 1,
  "encounterCode": "...",
  "status": "CREATED",
  "studyDate": "2023-10-15",
  "visitTime": "2023-10-15T10:30:00",
  "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
  "bodyPart": "KNEE",
  "referringPhysician": "Dr. Smith",
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "images": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "visitTime": "2023-10-15T10:30:00",
      "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
    }
  ]
}
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Examination not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations/doctor/{doctorId}`

Retrieves a paginated list of examinations for a specific doctor.

### Path Parameters

- `doctorId`: The ID of the doctor.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/patient/{patientId}`

Retrieves a paginated list of examinations for a specific patient.

### Path Parameters

- `patientId`: The ID of the patient.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)

## `GET /dicom/instances/{id}/raw`

Retrieves the raw DICOM file for a specific DICOM instance.

### Path Parameters

- `id`: The ID of the DICOM instance.

### Response

Returns the physical DICOM file with content type `application/dicom`.

### Status Codes

- `200 OK`: Request successful, file attached.
- `401 Unauthorized`: Authentication is required
- `404 Not Found`: Instance or file not found.

## `GET /audit-logs`

Retrieves a paginated list of system audit logs. This API is used by administrators to track user activities (such as creating, updating, or deleting records). View (GET) actions are not recorded.

### Request

- `page` (optional): The page index (starts at 0).
- `size` (optional): Number of records per page (default: 10).
- `sort` (optional): Field to sort by (default: timeStamp,desc).

### Response

```json
{
  "content": [
    {
      "id": 1,
      "username": "admin",
      "title": "CREATE_DOCTOR",
      "description": "[\"CreateDoctorRequest(fullName=John Doe, email=john@hospital.com...)\"]",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
      "timeStamp": "2026-07-14T15:58:25"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/{id}`

Retrieves detailed information of an examination by ID, including patient details and associated DICOM images.

### Path Parameters

- `id`: The ID of the examination.

### Response

```json
{
  "examinationId": 1,
  "encounterCode": "...",
  "status": "CREATED",
  "studyDate": "2023-10-15",
  "visitTime": "2023-10-15T10:30:00",
  "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
  "bodyPart": "KNEE",
  "referringPhysician": "Dr. Smith",
  "patient": {
    "id": 1,
    "fullName": "Nguyen Van A"
  },
  "images": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "visitTime": "2023-10-15T10:30:00",
      "imageUrl": "http://localhost:8080/api/v1/dicom/instances/1/image"
    }
  ]
}
```

### Status Codes

- `200 OK`: Request successful
- `400 Bad Request`: Examination not found
- `401 Unauthorized`: Authentication is required

## `GET /examinations/doctor/{doctorId}`

Retrieves a paginated list of examinations for a specific doctor.

### Path Parameters

- `doctorId`: The ID of the doctor.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## `GET /examinations/patient/{patientId}`

Retrieves a paginated list of examinations for a specific patient.

### Path Parameters

- `patientId`: The ID of the patient.

### Query Parameters

- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Response

```json
{
  "content": [
    {
      "examinationId": 1,
      "encounterCode": "...",
      "status": "CREATED",
      "studyDate": "2023-10-15",
      "visitTime": "2023-10-15T10:30:00",
      "thumbnailUrl": "http://localhost:8080/api/v1/dicom/instances/1/thumbnail",
      "bodyPart": "KNEE",
      "referringPhysician": "Dr. Smith",
      "patient": {
        "id": 1,
        "fullName": "Nguyen Van A"
      },
      "images": []
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)

## `GET /dicom/instances/{id}/raw`

Retrieves the raw DICOM file for a specific DICOM instance.

### Path Parameters

- `id`: The ID of the DICOM instance.

### Response

Returns the physical DICOM file with content type `application/dicom`.

### Status Codes

- `200 OK`: Request successful, file attached.
- `401 Unauthorized`: Authentication is required
- `404 Not Found`: Instance or file not found.

## `GET /audit-logs`

Retrieves a paginated list of system audit logs. This API is used by administrators to track user activities (such as creating, updating, or deleting records). View (GET) actions are not recorded.

### Request

- `page` (optional): The page index (starts at 0).
- `size` (optional): Number of records per page (default: 10).
- `sort` (optional): Field to sort by (default: timeStamp,desc).

### Response

```json
{
  "content": [
    {
      "id": 1,
      "username": "admin",
      "title": "CREATE_DOCTOR",
      "description": "[\"CreateDoctorRequest(fullName=John Doe, email=john@hospital.com...)\"]",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...",
      "timeStamp": "2026-07-14T15:58:25"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not an ADMIN

 
 

## `GET /examinations/status`

Retrieves a paginated list of examinations filtered by status. The results are automatically filtered based on the authenticated user's role (RBAC):
- **DOCTOR**: Only returns their own assigned examinations.
- **ADMIN / DEPARTMENT_HEAD**: Returns all examinations in the system.

### Query Parameters

- `status` (Required): Filter by ExaminationStatus (e.g., `AI_PROCESSING`, `NEED_VERIFY`, `VERIFIED`, `REPORT_GENERATED`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

```http
GET /examinations/status?status=NEED_VERIFY&page=0&size=10
Authorization: Bearer <token>
```

### Response

```json
{
  "content": [
    {
      "id": 1,
      "status": "NEED_VERIFY"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: User is not authenticated

## GET /examinations/grade

Retrieves examinations filtered by their max predicted grade. The results are automatically filtered based on the authenticated user's role (doctors only see their own, admins/department heads see all).

### Request

- grade (query parameter): The max predicted grade to filter by (e.g., 3, 4).
- page (query parameter, optional): Page number (default: 0).
- size (query parameter, optional): Page size (default: 10).

Requires Bearer Token in Authorization header.

### Response

`json
{
  "content": [
    {
      "id": 1,
      "maxPredictedGrade": 3
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
`

### Status Codes

- 200 OK: Request successful
- 401 Unauthorized: User is not authenticated
## GET /examinations/statistics/patients-by-grade

Retrieves the number of patients grouped by the max predicted grade of their latest examination.
For doctors, it counts patients based on their latest examination with that specific doctor.
For admins/department heads, it counts all patients based on their latest examination in the system.

### Request

- No query parameters required.

Requires Bearer Token in Authorization header.

### Response

`json
[
  {
    "grade": 1,
    "patientCount": 15
  },
  {
    "grade": 2,
    "patientCount": 8
  },
  {
    "grade": 3,
    "patientCount": 2
  }
]
`

### Status Codes

- 200 OK: Request successful
- 401 Unauthorized: User is not authenticated


## `GET /examinations/sort/study-date`

Retrieves a paginated list of examinations sorted by study date. The results are automatically filtered based on the authenticated user's role (RBAC):
- **DOCTOR**: Only returns their own assigned examinations.
- **ADMIN / DEPARTMENT_HEAD**: Returns all examinations in the system.

### Query Parameters

- `direction` (Optional): Sort direction (`asc` or `desc`, default: `desc`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

`http
GET /examinations/sort/study-date?direction=desc&page=0&size=10
Authorization: Bearer <token>
`

### Status Codes
- 200 OK: Request successful
- 401 Unauthorized: User is not authenticated

## `GET /examinations/sort/upload-date`

Retrieves a paginated list of examinations sorted by the date they were uploaded (created at). Role-based filtering applies.

### Query Parameters

- `direction` (Optional): Sort direction (`asc` or `desc`, default: `desc`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

`http
GET /examinations/sort/upload-date?direction=desc&page=0&size=10
Authorization: Bearer <token>
`

### Status Codes
- 200 OK: Request successful
- 401 Unauthorized: User is not authenticated

## `GET /examinations/filter/study-date`

Retrieves a paginated list of examinations that occurred on a specific study date. Role-based filtering applies.

### Query Parameters

- `date` (Required): The study date to filter by (format: `YYYY-MM-DD`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

`http
GET /examinations/filter/study-date?date=2026-07-22&page=0&size=10
Authorization: Bearer <token>
`

### Status Codes
- 200 OK: Request successful
- 400 Bad Request: Missing or invalid date format
- 401 Unauthorized: User is not authenticated

## `GET /examinations/filter/upload-date`

Retrieves a paginated list of examinations that were uploaded (created) on a specific date. Role-based filtering applies.

### Query Parameters

- `date` (Required): The upload date to filter by (format: `YYYY-MM-DD`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

`http
GET /examinations/filter/upload-date?date=2026-07-22&page=0&size=10
Authorization: Bearer <token>
`

### Status Codes
- 200 OK: Request successful
- 400 Bad Request: Missing or invalid date format
- 401 Unauthorized: User is not authenticated


## `GET /examinations/patient/{patientId}/filter/study-month`

Retrieves a paginated list of examinations for a specific patient, filtered by the month and year of the study date.

### Path Parameters

- `patientId` (Required): The ID of the patient.

### Query Parameters

- `year` (Required): The year to filter by (e.g., `2026`).
- `month` (Required): The month to filter by (e.g., `7`).
- `page` (Optional): Page index (0-based, default: `0`).
- `size` (Optional): Items per page (default: `10`).

### Request

`http
GET /examinations/patient/1/filter/study-month?year=2026&month=7
Authorization: Bearer <token>
`

### Response

Returns a paginated list of `ExaminationDto`.

### Status Codes
- 200 OK: Request successful
- 400 Bad Request: Missing or invalid date format
- 401 Unauthorized: User is not authenticated

## AI Chatbox and Medical Knowledge

See [RAG Chatbox](rag-chatbox.md) for request/response examples, RBAC rules,
knowledge ingestion states, report synchronization, and local configuration.
