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

- **Endpoint**: `ws://localhost:8080/ws`
- **Authentication**: Pass the JWT token in the `CONNECT` frame headers (`Authorization: Bearer <token>`).
- **Subscription**: Subscribe to `/user/queue/notifications` to receive events.

When controllers are added, document each endpoint using this format:

## `METHOD /path`

Purpose of the endpoint.

### Request

```json
{
  "example": "value"
}
```

### Response

```json
{
  "example": "value"
}
```

### Status Codes

- `200 OK`: request succeeded
- `400 Bad Request`: invalid request input
- `401 Unauthorized`: authentication is required
- `403 Forbidden`: authenticated user is not allowed
- `404 Not Found`: resource does not exist
- `500 Internal Server Error`: unexpected server error

Remove unused status codes from each endpoint section as APIs are documented.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: Deployment Guide](deployment.md)
