# Frontend Integration: Examination Review and PDF Report

[Back to Documentation Index](README.md) | [API Documentation](api.md)

This guide is the frontend contract for reviewing every AI result, generating one finalized examination report, previewing it in the browser, and downloading it on demand.

## Base URL and Authentication

Local API base URL:

```text
http://localhost:8080/api/v1
```

All review and report requests require the access token returned by `POST /auth/login`:

```http
Authorization: Bearer <accessToken>
```

Do not open `previewUrl` or `downloadUrl` directly with `window.open()`. A direct browser navigation does not attach the Bearer token and the PDF viewer will receive an error response instead of PDF bytes.

## State Flow

```text
NEED_VERIFY
  -> doctor chooses confirm or adjust for every AI result
  -> VERIFIED
  -> POST generate-report
  -> REPORT_GENERATED
  -> preview inline or download as attachment
```

An examination can contain multiple DICOM instances, and each instance can expose multiple AI results. The frontend must process every `images[].aiResults[]` item returned by `GET /examinations/{id}`.

For each AI result:

- `predictedGrade`: original AI prediction; never overwritten by doctor review.
- `confirmedGrade`: final grade selected by confirm or adjust.
- `effectiveGrade`: `confirmedGrade` after review, otherwise `predictedGrade`.
- `reviewDecision`: `AI_CONFIRMED` or `DOCTOR_ADJUSTED` after review.

The Generate Report action should be disabled until the examination status is `VERIFIED`.

## Confirm AI Grade

```http
PUT /ai/results/{aiResultId}/confirm
Authorization: Bearer <accessToken>
```

No request body is required. The response sets `confirmedKlGrade` to `predictedKlGrade` and returns decision `AI_CONFIRMED`.

## Adjust KL Grade

```http
PUT /ai/results/{aiResultId}/kl-grade
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "confirmedKlGrade": 3,
  "reviewNote": "Clinical findings support KL grade 3"
}
```

`confirmedKlGrade` must be an integer from 0 to 4. `reviewNote` is required and has a maximum length of 2000 characters. The response keeps the AI prediction and returns decision `DOCTOR_ADJUSTED`.

## Generate Report

```http
POST /examinations/{examinationId}/generate-report
Authorization: Bearer <accessToken>
```

Successful response:

```json
{
  "reportId": 1,
  "examinationId": 1,
  "fileName": "report_STUDY001_a1b2c3d4.pdf",
  "fileSize": 38826,
  "contentType": "application/pdf",
  "generatedAt": "2026-07-29T14:32:14",
  "previewUrl": "/api/v1/reports/1/preview",
  "downloadUrl": "/api/v1/reports/1/download"
}
```

Generation stores the PDF on the backend and report metadata in MySQL. It does not download the file to the doctor's device. Repeating the request returns the existing report while its stored file is available.

## Frontend API Helpers

The response URLs already include `/api/v1`, so resolve them against the server origin rather than appending them to an API base that also contains `/api/v1`.

```javascript
const API_ORIGIN = "http://localhost:8080";
const API_BASE = `${API_ORIGIN}/api/v1`;

async function authenticatedFetch(url, accessToken, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? `Request failed with status ${response.status}`);
  }
  return response;
}

export async function generateReport(examinationId, accessToken) {
  const response = await authenticatedFetch(
    `${API_BASE}/examinations/${examinationId}/generate-report`,
    accessToken,
    { method: "POST" },
  );
  return response.json();
}
```

## Preview PDF

```javascript
export async function createReportPreviewUrl(previewPath, accessToken) {
  const response = await authenticatedFetch(
    new URL(previewPath, API_ORIGIN),
    accessToken,
  );

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/pdf")) {
    throw new Error("Server did not return a PDF response");
  }

  const blob = await response.blob();
  return URL.createObjectURL(blob);
}
```

Assign the returned object URL to an `iframe`, `embed`, or a new browser tab. Revoke it when the preview closes:

```javascript
URL.revokeObjectURL(previewObjectUrl);
```

Preview returns `Content-Disposition: inline` and `Cache-Control: no-store`. It does not save a file in the user's Downloads folder.

## Download PDF

```javascript
export async function downloadReport(report, accessToken) {
  const response = await authenticatedFetch(
    new URL(report.downloadUrl, API_ORIGIN),
    accessToken,
  );

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/pdf")) {
    throw new Error("Server did not return a PDF response");
  }

  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = report.fileName ?? `report-${report.reportId}.pdf`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}
```

Download returns `Content-Disposition: attachment` and creates an audit-log entry. Only this frontend action saves the PDF to the user's device.

## Authorization

- Assigned doctors require `CONFIRM_CONCLUSION` to confirm and `OVERRIDE_AI_GRADE` to adjust.
- Report generation and preview require `GENERATE_PDF_REPORT`.
- Download requires `EXPORT_DOWNLOAD_PDF`.
- `DEPARTMENT_HEAD` and `HEAD_OF_DEPARTMENT` can review and export examinations outside their assignment.
- A regular doctor cannot review or export an examination assigned to another doctor.

## Error Handling

| Status | Meaning | Frontend behavior |
| --- | --- | --- |
| `400` | Invalid grade/note, unknown ID, examination not verified, or an AI result is unreviewed | Show the server `message` and refresh examination data |
| `401` | Token missing, invalid, or expired | Refresh authentication or return to login |
| `403` | Missing authority or examination ownership | Hide the action and show an access-denied message |
| `500` | PDF rendering/storage failure or stored file missing | Show retry guidance and log the request context |

Before treating a preview/download response as PDF, always verify both `response.ok` and that `Content-Type` contains `application/pdf`. This prevents a JSON error response from being passed to the browser PDF viewer as `Failed to load PDF file`.

## Swagger and Bruno

- Swagger UI: `http://localhost:8080/api/v1/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/api/v1/v3/api-docs`
- Bruno sequence: login, get examination, confirm/adjust every AI result, generate report, preview report, download report.

