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

- `200 OK`: Profile updated successfully
- `400 Bad Request`: Invalid input fields
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
```

### Status Codes

- `200 OK`: Request successful
- `401 Unauthorized`: Authentication is required
- `403 Forbidden`: Authenticated user is not an ADMIN

## `POST /examinations/{id}/generate-report`

Generates a comprehensive PDF report for an examination, including patient information, clinical notes, and AI analysis results (with GradCAM images). The generated PDF is automatically saved to the local file system (e.g., `D:/HealthSync_Exports`).

### Request

```http
POST /examinations/1/generate-report
Authorization: Bearer <token>
```

### Response

```text
Report generated and saved at: D:\HealthSync_Exports\report_EX-001_1a2b3c4d.pdf
```

### Status Codes

- `200 OK`: Report generated successfully.
- `401 Unauthorized`: Authentication is required.
- `404 Not Found`: Examination with the given ID does not exist.
- `500 Internal Server Error`: Failed to generate PDF (e.g., template processing or font loading error).
