# Environment Configuration

[Back to Documentation Index](README.md) | Previous: [Development Guide](development.md) | Next: [Database](database.md)

This document describes the environment variables and configuration files required to run the backend service.

## Configuration Sources

- `src/main/resources/application.yaml`: default Spring Boot configuration.
- `../env/be.env`: local backend environment variables.
- `../env/database.env`: local MySQL Docker environment variables.
- Docker Compose environment: used by `docker-compose.yaml`.

Do not commit real secrets. Keep local environment files outside Git or use ignored `.env` files.

## Backend Variables

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `DB_HOST` | No | `localhost` | MySQL host used by Spring datasource. |
| `DB_PORT` | No | `3306` | MySQL port used by Spring datasource. |
| `DB_NAME` | No | `capstone` | Database name. |
| `DB_USERNAME` | No | `root` | Database username. |
| `DB_PASSWORD` | No | `capstone_root_password` | Database password. |
| `SPRING_MAIL_HOST` | No | `localhost` | SMTP mail server host. |
| `SPRING_MAIL_PORT` | No | `1025` | SMTP mail server port (MailDev default is 1025). |


## Database Variables

The MySQL Docker service reads variables from:

```text
../env/database.env
```

At minimum, the MySQL image must receive a valid root password variable supported by the selected image.

## Spring Profiles

Use Spring profiles when environment-specific configuration becomes necessary.

Recommended files:

```text
application-dev.yaml
application-test.yaml
application-prod.yaml
```

Run with a profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Local Startup With Env File

From `/home/viet/Capstone/be`:

```bash
set -a && source ../env/be.env && set +a && mvn spring-boot:run
```

## Secret Rules

- Never commit database passwords, JWT signing secrets, tokens, or private keys.
- Prefer environment variables for local development.
- Prefer deployment secrets or secret managers in production.
- Keep example files safe by using fake values only.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Development Guide](development.md)
- [Next: Database](database.md)
