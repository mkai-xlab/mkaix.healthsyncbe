# Runbook

[Back to Documentation Index](README.md) | Previous: [Observability](observability.md) | Next: [Release Process](release-process.md)

This runbook helps developers diagnose common backend problems.

## Application Does Not Start

Check Java and Maven versions:

```bash
java -version
mvn -version
```

Expected:

- Java 21
- Maven 3.9+

Run the app:

```bash
mvn spring-boot:run
```

## Port 8080 Already In Use

Find the process using the port:

```bash
lsof -i :8080
```

Stop the process or run the backend on another port using Spring configuration.

## Database Connection Fails

Start MySQL:

```bash
docker compose up -d mysql
```

Check containers:

```bash
docker compose ps
```

Run backend with environment file:

```bash
set -a && source ../env/be.env && set +a && mvn spring-boot:run
```

Verify these variables:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

## Tests Fail

Run:

```bash
mvn test
```

If the failure is datasource-related, confirm MySQL and environment variables are configured correctly.

## Clean Build Output

Generated files live under `target/`.

Clean and rebuild:

```bash
mvn clean package
```

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Observability](observability.md)
- [Next: Release Process](release-process.md)
