# API Documentation

[Back to Documentation Index](README.md) | Previous: [Database](database.md) | Next: [Deployment Guide](deployment.md)

No public API endpoints are currently implemented, except for authentication endpoints.

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

Uploads a DICOM file and returns its extracted metadata.

### Request

- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `file` (File): The multipart DICOM file.

### Response

```json
[
  {
    "tagId": "0010,0010",
    "tagName": "Patient's Name",
    "value": "Doe^John"
  }
]
```

### Status Codes

- `200 OK`: File parsed successfully
- `400 Bad Request`: Uploaded file is empty or invalid
- `401 Unauthorized`: authentication is required

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
        "name": "READ_OWN_PROFILE",
        "description": "Xem hồ sơ cá nhân",
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
  "name": "EXPORT_REPORTS",
  "description": "Export data to Excel"
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
  "name": "EXPORT_PDF",
  "description": "Export data to PDF"
}
```

### Status Codes

- `200 OK`: Permission updated successfully
- `400 Bad Request`: Permission not found or circular dependency
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Admin role is required

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)
