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

## Deployment Environments

The backend has two deployment targets:

| Environment | Host | Trigger | SSH authentication | Remote user |
| --- | --- | --- | --- | --- |
| Development | AWS EC2 | Push to `dev` | `EC2_PUBLIC_IP` + `SSH_PRIVATE_KEY` | `ubuntu` |
| Production | Viettel IDC | Manual Actions workflow | `VIETTEL_PUBLIC_IP` + `VIETTEL_PASSWORD` | `root` |

Both deployments build only the backend service:

```bash
docker compose up -d --build be
```

Do not use `docker compose up -d --build` for deployment; that starts every
service in the Compose file. MySQL is assigned to the `database` profile and
is not started by the deployment command.

## AWS EC2 Development Deployment

The workflow is [aws-ec2-dev-deploy.yml](../.github/workflows/aws-ec2-dev-deploy.yml).
It runs automatically after a qualifying push to `dev` (source changes,
`pom.xml`, `Dockerfile`, or `docker-compose.yaml`). The remote commands are:

```bash
cd ~/healthsync/mkaix.healthsyncbe/
git checkout dev
git pull --ff-only origin dev
docker compose config --quiet
docker compose up -d --build be
```

Required repository secrets:

- `EC2_PUBLIC_IP`
- `SSH_PRIVATE_KEY`

The AWS host must contain the environment file required by the `be` service:

```text
/home/ubuntu/healthsync/env/be.env
```

`database.env` is not required unless MySQL is deliberately enabled. To run
MySQL manually on the host, provide that file and use:

```bash
docker compose --profile database up -d mysql
```

## Viettel IDC Production Deployment

The workflow is [viettel-idc-prod-deploy.yml](../.github/workflows/viettel-idc-prod-deploy.yml).
It never deploys on a push automatically. To deploy, open **Actions**, select
**Deliver Production to Viettel IDC**, click **Run workflow**, enable the
confirmation checkbox, and click **Run workflow** again.

Required repository secrets:

- `VIETTEL_PUBLIC_IP`
- `VIETTEL_PASSWORD`

The workflow connects as `root` and runs:

```bash
cd ~/healthsync/mkaix.healthsyncbe/
git checkout main
git pull --ff-only origin main
docker compose config --quiet
docker compose up -d --build be
```

The Viettel host must contain:

```text
/root/healthsync/env/be.env
```

Do not store `be.env`, database credentials, JWT secrets, or SSH/password
credentials in Git. Keep them on the server or in GitHub Secrets.

## Deployment Verification

After either deployment, connect to the target host and check the backend:

```bash
cd ~/healthsync/mkaix.healthsyncbe/
docker compose ps be
docker compose logs --tail=100 be
curl -i http://127.0.0.1:8000/actuator/health
```

The `be` container should be running and the health endpoint should return a
successful HTTP response. If the container exits, inspect the first startup
error in `docker compose logs be`; common causes are a missing `be.env`, an
incorrect external database address, or an unavailable Redis host.

## Rollback

From the target host, identify a known-good commit and redeploy that commit:

```bash
cd ~/healthsync/mkaix.healthsyncbe/
git fetch origin
git log --oneline -10
git checkout <known-good-commit>
docker compose config --quiet
docker compose up -d --build be
docker compose ps be
```

After the incident is resolved, return the checkout to the appropriate branch
(`dev` on AWS or `main` on Viettel IDC) before the next automated/manual
deployment.

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Observability](observability.md)
- [Next: Release Process](release-process.md)
