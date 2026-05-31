# Database

[Back to Documentation Index](README.md) | Previous: [Environment Configuration](environment.md) | Next: [API Documentation](api.md)

The backend uses MySQL for persistence. Local development runs MySQL through Docker Compose from the backend folder.

## Docker Compose

The Compose file is located in the backend folder:

```text
docker-compose.yaml
```

The MySQL service uses this image:

```text
dhi.io/mysql:9
```

Environment files are stored in:

```text
../env/database.env
../env/be.env
```

## Start Database

From `/home/viet/Capstone/be`:

```bash
docker compose up -d mysql
```

Check service status:

```bash
docker compose ps
```

Stop services:

```bash
docker compose down
```

Stop services and remove the MySQL volume:

```bash
docker compose down -v
```

## Spring Configuration

The backend database settings are defined in:

```text
src/main/resources/application.yaml
```

The app reads these environment variables:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`

Local defaults use the MySQL root user because the configured `dhi.io/mysql:9` image initializes root credentials but does not create an application user from `MYSQL_USER` / `MYSQL_PASSWORD`.

## Run Backend With Env File

From `/home/viet/Capstone/be`:

```bash
set -a && source ../env/be.env && set +a && mvn spring-boot:run
```

## Connection Check

`spring-boot-starter-data-jpa` and the MySQL JDBC driver are included so the application opens a datasource connection during startup.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Environment Configuration](environment.md)
- [Next: API Documentation](api.md)
