# Email Providers and SMTP Testing

[Back to Documentation Index](README.md) | Previous: [Database](database.md) | Next: [API Documentation](api.md)

HealthSync uses one mail configuration in `application.yaml`. Environment variables select the SMTP server, authentication, and STARTTLS behavior without changing the existing welcome and password-reset flows.

## Local Environment File

Docker Compose loads the ignored `.env` file in the backend directory. The checked-out local file contains non-secret placeholders for Google SMTP. Replace these values before testing:

```dotenv
MAIL_FROM=your-account@gmail.com
MAIL_SMTP_USERNAME=your-account@gmail.com
MAIL_SMTP_PASSWORD=your-google-app-password
```

Use a Google App Password, not the normal account password. The `.env` file is covered by `.gitignore` and must never be force-added to Git.

After changing these values while containers are already running, recreate the backend so Docker reloads the environment:

```bash
docker compose up -d --force-recreate be
```

## Start with Docker Compose

Build and start the database, Redis, and backend from the backend directory:

```bash
docker compose up -d --build mysql redis be
```

Follow backend logs while testing:

```bash
docker compose logs -f be
```

For Google SMTP, the provided `.env` selects `smtp.gmail.com:587`, SMTP authentication, and required STARTTLS.

To use MailDev instead, change the mail section in `.env` to:

```dotenv
MAIL_PROVIDER=maildev
MAIL_FROM=no-reply@healthsync.local
MAIL_SMTP_HOST=maildev
MAIL_SMTP_PORT=1025
MAIL_SMTP_USERNAME=
MAIL_SMTP_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS_ENABLE=false
MAIL_SMTP_STARTTLS_REQUIRED=false
```

Then include MailDev when starting the stack:

```bash
docker compose up -d --build mysql redis maildev be
```

Its inbox is available at [http://localhost:1080](http://localhost:1080).

## Asynchronous Delivery

Plain-text and Thymeleaf-template messages run through the dedicated `mailTaskExecutor`. Calling services queue the work without waiting for SMTP. Delivery failures are written to the backend logs.

## Temporary Test Endpoint

The following endpoint is intentionally unauthenticated for local SMTP setup:

```http
POST http://localhost:8000/api/v1/mail/test
Content-Type: application/json

{
  "recipient": "recipient@example.com"
}
```

PowerShell example:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8000/api/v1/mail/test `
  -ContentType application/json `
  -Body '{"recipient":"recipient@example.com"}'
```

The endpoint returns `202 Accepted` when the background task is queued. Confirm success by receiving the message and checking the backend logs. Remove `MailTestController`, its DTO/service, tests, Bruno requests, and API documentation before deployment.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: API Documentation](api.md)
