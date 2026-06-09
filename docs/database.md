# Database

[Back to Documentation Index](README.md) | Previous: [Environment Configuration](environment.md) | Next: [API Documentation](api.md)

The backend uses MySQL for persistence. Local development runs MySQL through Docker Compose from the backend folder.

## Database ERD

<img src="diagrams/database.png" alt="Database ERD" width="100%" />

### Live Mermaid ERD Diagram
```mermaid
erDiagram
    users ||--|| doctors : "id"
    users ||--|| admins : "id"
    users ||--o{ notifications : "user_id"
    users ||--o{ audit_logs : "user_id"
    users ||--o{ password_reset_tokens : "user_id"
    users ||--|| avatar_images : "user_id"

    patients ||--o{ examinations : "patient_id"
    doctors ||--o{ examinations : "doctor_id"

    examinations ||--o{ xray_images : "examination_id"
    examinations ||--o{ ai_analysis_jobs : "examination_id"
    examinations ||--o{ diagnosis_reviews : "examination_id"
    examinations ||--o{ clinical_reports : "examination_id"

    xray_images ||--|| dicom_informations : "xray_image_id"
    xray_images ||--o{ ai_analysis_results : "xray_image_id"
    ai_analysis_jobs ||--o{ ai_analysis_results : "job_id"

    doctors ||--o{ diagnosis_reviews : "doctor_id"
    ai_analysis_results ||--o{ diagnosis_reviews : "ai_result_id"

    doctors ||--o{ clinical_reports : "doctor_id"

    users {
        bigint id PK
        varchar username
        varchar password
        varchar full_name
        varchar email
        varchar phone
        user_role role
        user_status status
        boolean is_first_activated
        datetime created_at
        datetime updated_at
    }

    doctors {
        bigint id PK, FK
        varchar doctor_code
        varchar license_number
        varchar specialization
        int years_of_experience
        varchar academic_title
        varchar degree
        text bio
        doctor_position position
    }

    admins {
        bigint id PK, FK
        varchar admin_code
        varchar position
    }

    patients {
        bigint id PK
        varchar patient_code
        varchar full_name
        date date_of_birth
        gender gender
        varchar phone
        varchar email
        varchar address
        varchar emergency_contact_name
        varchar emergency_contact_phone
        datetime created_at
        datetime updated_at
    }

    examinations {
        bigint id PK
        bigint patient_id FK
        bigint doctor_id FK
        examination_status status
        text clinical_note
        text final_diagnosis
        datetime examined_at
        datetime created_at
        datetime updated_at
    }

    xray_images {
        bigint id PK
        bigint examination_id FK
        varchar file_name
        varchar file_url
        varchar dicom_url
        varchar body_side
        varchar view_position
        datetime uploaded_at
    }

    ai_analysis_jobs {
        bigint id PK
        bigint examination_id FK
        ai_job_status status
        datetime started_at
        datetime completed_at
        text error_message
        datetime created_at
    }

    ai_analysis_results {
        bigint id PK
        bigint job_id FK
        bigint xray_image_id FK
        int predicted_kl_grade
        decimal confidence
        varchar gradcam_url
        text interpretation
        datetime created_at
    }

    diagnosis_reviews {
        bigint id PK
        bigint examination_id FK
        bigint doctor_id FK
        bigint ai_result_id FK
        int confirmed_kl_grade
        text doctor_diagnosis
        text review_note
        datetime reviewed_at
    }

    clinical_reports {
        bigint id PK
        bigint examination_id FK
        bigint doctor_id FK
        varchar report_code
        text report_content
        varchar file_url
        boolean is_confirmed
        datetime confirmed_at
        datetime generated_at
    }

    notifications {
        bigint id PK
        bigint user_id FK
        varchar title
        text message
        varchar type
        boolean is_read
        datetime created_at
        datetime read_at
    }

    audit_logs {
        bigint id PK
        bigint user_id FK
        varchar action
        varchar entity_type
        bigint entity_id
        text description
        text payload
        varchar ip_address
        varchar user_agent
        boolean is_success
        datetime created_at
    }

    password_reset_tokens {
        bigint id PK
        bigint user_id FK
        varchar token
        datetime expiry_date
    }

    avatar_images {
        bigint id PK
        bigint user_id FK, UK
        varchar file_name
        varchar file_url
        datetime uploaded_at
    }

    dicom_informations {
        bigint id PK
        bigint xray_image_id FK, UK
        varchar sop_instance_uid
        varchar series_instance_uid
        varchar study_instance_uid
        varchar patient_name
        varchar patient_id
        date patient_birth_date
        varchar patient_sex
        varchar modality
        varchar manufacturer
        varchar institution_name
        int instance_number
    }
```


## Docker Compose

The Compose file is located in the backend folder:

```text
docker-compose.yaml
```

The MySQL service uses this image:

```text
dhi.io/mysql:9
```

The DBML source used to generate the database diagram is available at:

```text
docs/database-schema.dbml
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

## Proposed Schema

The proposed database schema is documented in DBML format for dbdiagram/dbdraw:

```text
docs/database-schema.dbml
```

Use this file to generate the ERD visually in a DBML-compatible diagram tool.

## JPA Entities and Inheritance Mapping

The project maps tables to JPA Entities under `com.g93.be.entity` and repositories under `com.g93.be.repository`.

### User & Authentication Inheritance (JOINED Strategy)
To avoid duplicating fields (like timestamps and user properties) while preserving a clean relational structure, the database uses the **JPA JOINED inheritance strategy**:

* **`User`** (`com.g93.be.entity.User`): The parent entity mapping to the `users` table. Contains shared account credentials, profile details (`avatar_url`), audit status, and audit timestamps.
* **`Doctor`** (`com.g93.be.entity.Doctor`): Extends `User` mapping to the `doctors` table via `@PrimaryKeyJoinColumn(name = "id")`. Contains clinical details.
* **`Admin`** (`com.g93.be.entity.Admin`): Extends `User` mapping to the `admins` table via `@PrimaryKeyJoinColumn(name = "id")`. Contains administrative configuration.

### Notification and Audit Logging
* **`Notification`** (`com.g93.be.entity.Notification`): Maps to `notifications` with a lazy reference (`@ManyToOne`) back to the `User`.
* **`AuditLog`** (`com.g93.be.entity.AuditLog`): Maps to `audit_logs`. It tracks security context fields including `user_agent`, client `ip_address`, transaction `payload` (JSON), and execution `is_success`.

### Clinical Reporting, Reviews & Metadata
* **`DiagnosisReview`** (`com.g93.be.entity.DiagnosisReview`): Maps to `diagnosis_reviews`. Stores the doctor's clinical review and validation of AI-predicted KL grades and details.
* **`ClinicalReport`** (`com.g93.be.entity.ClinicalReport`): Maps to `clinical_reports`. Stores generated/exported clinical PDF reports, utilizing `is_confirmed` and `confirmed_at` to represent the doctor's confirmation.
* **`DicomInformation`** (`com.g93.be.entity.DicomInformation`): Maps to `dicom_informations` (linked 1-to-1 with `XrayImage`). Stores parsed DICOM metadata tags (Patient ID, Name, Birth Date, Sex, SOP Instance UID, etc.) for uploaded files.

---

## Navigation

- [Back to Documentation Index](README.md)
- [Previous: Environment Configuration](environment.md)
- [Next: API Documentation](api.md)

