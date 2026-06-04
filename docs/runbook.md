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

## Reset Database Tables

If you need to drop all tables and recreate the database schema, choose one of the following methods:

### Method 1: Spring Boot Automatic Recreation (Recommended for Dev)
Run the application with the `ddl-auto=create` property. This drops existing tables and recreates them based on JPA entities on startup:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=create"
```
*Note: Revert to normal startup or set ddl-auto back to `update` afterwards to avoid dropping tables on subsequent launches.*

### Method 2: Recreate MySQL Docker Volume
To completely clear the database (including schema and any stored tables/records) and start with a clean state:
```bash
# Stop containers and delete associated MySQL volume
docker compose down -v

# Start the MySQL service fresh
docker compose up -d mysql
```

### Method 3: Command Line SQL Reset
Drop and recreate the database schema directly using a local MySQL client:
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p -e "DROP DATABASE IF EXISTS capstone; CREATE DATABASE capstone;"
```

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
