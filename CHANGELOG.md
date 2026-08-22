# Changelog

All notable backend changes should be documented in this file.

This project can follow semantic versioning when formal releases start.

## Unreleased

### Added

- Added paginated medical-knowledge listing with keyword, source type, indexing status, access scope, pagination, and sorting filters.
- Added authenticated knowledge-source text extraction, inline preview, and original-file download endpoints with safe storage-path validation.
- Added `contentUrl`, `previewUrl`, and `downloadUrl` to file and URL knowledge-document responses and matching Bruno requests.
- Added a large diagonal `HealthSync` watermark, AI-assistance disclaimer, and DICOM study date/time to newly generated PDF reports.
- Added `TODAY_EXAMINATION_LIST` chat routing and a controlled query returning up to 10 newest examinations from today for backend selection flows.
- Added report-aware HYBRID retrieval that uses stored report context to locate relevant approved medical evidence.
- Added synchronous AI medical-content validation for uploaded files and public URL knowledge sources using large samples from the beginning, middle, and end.
- Added focused deletion, validation, sampling, batch rejection, and retrieval-size tests plus a Bruno request for deleting knowledge documents.
- Added Staff Management endpoints (`GET /users/staff/search` and `PATCH /users/{userId}/status/toggle`) with email notification support.
- Added `UserMapper` to extract mapping logic and maintain clean architecture.
- Added explicit KL review decisions: assigned doctors can confirm or adjust AI results; department heads inherit both review actions across assignments; both actions are audit logged.
- Added persistent diagnosis reviews that retain the original AI grade and expose the decision, confirmed grade, and effective grade in examination responses.
- Updated PDF export to use only the latest confirmed AI analysis and select the final KL grade from either the confirmed AI prediction or the doctor/department-head adjustment.
- Added examination status transitions from `NEED_VERIFY` to `VERIFIED` after all latest AI results are reviewed, then to `REPORT_GENERATED` after a successful PDF export.
- Added service, RBAC, mapper, PDF, and Bruno tests for KL confirmation and adjustment.
- Added `fullName` to successful login responses.
- Added `GET /notifications` to retrieve all notifications for the authenticated user, including read items.
- Added admin-only `DELETE /permissions/{id}` and `DELETE /features/{id}` endpoints with dependent RBAC relationship cleanup and audit logging.
- Added focused unit and Bruno tests for the new authentication, notification, permission, and feature behavior.
- Added 4 separate endpoints for Examination filtering and sorting (`/examinations/sort/study-date`, `/examinations/sort/upload-date`, `/examinations/filter/study-date`, `/examinations/filter/upload-date`).
- Added `GET /audit-logs` endpoint for admins to view user activities.
- Implemented AOP-based Audit Logging pattern to track state modifications automatically via `@LogAction`.
- Added `GET /doctors/profile` and `PUT /doctors/profile` endpoints for doctors to view and edit their own profiles.
- Added `PUT /doctors/{id}` endpoint to update existing doctors.
- Added `degree` and `biography` fields to `Doctor` entity and related APIs.
- Added `GET /examinations` and `GET /examinations/{id}` endpoints to retrieve paginated examinations and detailed views (including patient information and DICOM images).
- Support for batch uploading of multiple DICOM files (parsing multiple images for the same Examination).
- Implemented `PatientServiceImpl.getPatientDetailsWithImages` to retrieve details of a patient alongside their related examinations and image instances.
- Added docker volumes (`./data:/app/data`) for safely persisting uploaded DICOM files.
- Direct backend DICOM file upload API (`POST /dicom/upload`).
- Deduplication logic rejecting duplicate DICOM uploads based on `SOPInstanceUID`.
- DICOM image retrieval API (`GET /dicom/instances/{id}/image`).
- DICOM raw file retrieval API (`GET /dicom/instances/{id}/raw`).
- Refactored database schema to separate `DicomRaw` and `Image` mappings for DICOM instances.
- Backend documentation set.
- Architecture, authentication, database, development, API, and Git Flow documentation.

### Changed

- Knowledge-document list responses are now paginated instead of returning an unbounded array.
- PDF report template tests now render the PDF and verify that watermark-colored pixels are visible on every generated page.
- Report vectors now authorize both the report creator and the examination's assigned doctor, while remaining owner-scoped for other users.
- Report reconciliation now retries incomplete indexing, upgrades legacy report metadata, and resynchronizes when an existing PDF is returned.
- Increased the default medical RAG retrieval size from 5 to 12 chunks.
- Hardened knowledge deletion against concurrent asynchronous indexing so deleted vectors are not recreated.
- Refactored `ExaminationDto` to return a list of `images` (`ExaminationImageDto`) instead of a single `imageUrl`.
- Documentation should be updated whenever backend behavior changes.

### Fixed

- Fixed generated reports becoming permanently invisible to RAG after an indexing failure or when a department head generated the assigned doctor's report.
- Resolved duplicate fields issue in `DicomInstance` entity.
- Fixed Tomcat `/tmp` space issue in Docker environment by adding `VOLUME /tmp`.
- Resolved compilation error due to mismatched JDK versions in Dockerfile.
