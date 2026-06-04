# Email Integration and Testing with MailDev

[Back to Documentation Index](README.md) | Previous: [Database](database.md) | Next: [API Documentation](api.md)

This document describes how email integration is configured, how to use the `MailUtil` helper class, and how to verify sent emails locally using **MailDev**.

## MailDev Setup

We use MailDev to catch and preview sent emails during local development without connecting to a real SMTP server.

### Start MailDev

From `/home/viet/Capstone/be`, start MailDev with Docker Compose:

```bash
docker compose up -d maildev
```

This starts MailDev with the following exposed ports:
- **SMTP Server**: Port `1025` (no authentication, TLS disabled).
- **Web UI**: [http://localhost:1080](http://localhost:1080) (preview and inspect sent emails).

### Stop MailDev

```bash
docker compose stop maildev
```

---

## Spring Boot Configuration

The email integration is defined in `src/main/resources/application.yaml`:

```yaml
spring:
  mail:
    host: ${SPRING_MAIL_HOST:localhost}
    port: ${SPRING_MAIL_PORT:1025}
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false
```

- **Host**: SMTP server host (`localhost` by default).
- **Port**: SMTP server port (`1025` by default).
- **Authentication / TLS**: Disabled for simple local testing.

---

## Using MailUtil

We provide [MailUtil](file:///home/viet/Capstone/be/src/main/java/com/g93/be/common/util/MailUtil.java) to easily send plain text and Thymeleaf template emails.

### 1. Sending Plain Text Email

```java
@Autowired
private MailUtil mailUtil;

public void testPlainEmail() {
    mailUtil.sendPlainTextMail(
        "recipient@example.com",
        "Test Subject",
        "Hello! This is a plain text email."
    );
}
```

### 2. Sending HTML Thymeleaf Template Email

Create a template under `src/main/resources/templates/example-email.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Welcome</title>
</head>
<body>
    <h1 th:text="'Hello, ' + ${name} + '!'">Hello, User!</h1>
    <p>Welcome to our Capstone project.</p>
</body>
</html>
```

Send the template email by supplying variables to [MailUtil](file:///home/viet/Capstone/be/src/main/java/com/g93/be/common/util/MailUtil.java):

```java
@Autowired
private MailUtil mailUtil;

public void testTemplateEmail() {
    Map<String, Object> variables = Map.of(
        "name", "Viet"
    );
    mailUtil.sendTemplateMail(
        "recipient@example.com",
        "Welcome to Capstone",
        "example-email",
        variables
    );
}
```

---

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Database](database.md)
- [Next: API Documentation](api.md)
