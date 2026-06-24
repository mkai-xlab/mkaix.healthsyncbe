# Changelog

All notable backend changes should be documented in this file.

This project can follow semantic versioning when formal releases start.

## Unreleased

### Added

- Direct backend DICOM file upload API (`POST /dicom/upload`).
- Deduplication logic rejecting duplicate DICOM uploads based on `SOPInstanceUID`.
- DICOM image retrieval API (`GET /dicom/instances/{id}/image`).
- Backend documentation set.
- Architecture, authentication, database, development, API, and Git Flow documentation.

### Changed

- Documentation should be updated whenever backend behavior changes.

### Fixed

- Nothing yet.
