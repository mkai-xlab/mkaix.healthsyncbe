# HealthSync RAG Chatbox

The chatbox is implemented with Spring AI 2.0. Gemini Flash generates and routes
answers, Ollama serves BGE-M3 embeddings, and Qdrant stores vectors. The
feature is disabled unless `CHAT_AI_ENABLED=true`.

## Request Flow

1. `POST /chat/ask` authenticates the doctor, department head, or administrator and resolves or creates a chat session.
2. Gemini returns a structured routing decision: `BUSINESS_DATA`, `MEDICAL_RAG`,
   `HYBRID`, or `CLARIFICATION`.
3. Business questions execute one of the read-only queries defined in
   `BusinessDataQueryService`. This includes a maximum of 10 examinations for
   "show today's cases" questions. The LLM never creates or executes SQL.
4. Medical questions are embedded with BGE-M3 and searched in Qdrant. Up to 12 results
   are filtered by publication status, role scope, and report owner.
5. Gemini receives only the filtered context and returns an answer with source
   metadata and a clinical warning.
6. The user message and assistant answer are stored under the session. Up to 20
   recent messages (capped at 12,000 characters) provide follow-up context.

Doctors only see aggregates and clinical records assigned to them. Department
heads may access general and doctor-scoped clinical knowledge; owner-scoped
report data remains limited to the report creator and the examination's assigned
doctor until department metadata is available.
Administrators may use system aggregates and general/admin knowledge, but not
report or examination clinical details.

## API

All endpoints require a bearer token and their matching permission. Authorized
clinical users can upload and manage medical-knowledge sources. Report sync
still checks the clinical role and the `USE_AI_CHAT` permission.

| Method and path | Permission | Purpose |
| --- | --- | --- |
| `POST /chat/ask` | `USE_AI_CHAT` | Ask a question in an existing session or automatically create one. |
| `POST /chat/sessions` | `USE_AI_CHAT` | Create a session, optionally linked to an examination. |
| `GET /chat/sessions` | `USE_AI_CHAT` | List the current user's sessions, newest activity first. |
| `GET /chat/sessions/{id}/messages` | `USE_AI_CHAT` | Read one owned session's message history. |
| `PATCH /chat/sessions/{id}` | `USE_AI_CHAT` | Rename, close, or reopen an owned session. |
| `POST /knowledge-documents/upload` | `MANAGE_MEDICAL_KNOWLEDGE` | Validate and upload a medical PDF, DOC, DOCX, or TXT. |
| `POST /knowledge-documents/upload/batch` | `MANAGE_MEDICAL_KNOWLEDGE` | Upload up to 10 documents with per-file results. |
| `POST /knowledge-documents/url` | `MANAGE_MEDICAL_KNOWLEDGE` | Ingest an approved public HTTP(S) URL. |
| `GET /knowledge-documents` | `MANAGE_MEDICAL_KNOWLEDGE` | Check indexing state and errors. |
| `POST /knowledge-documents/{id}/reindex` | `MANAGE_MEDICAL_KNOWLEDGE` | Reindex a stored source. |
| `DELETE /knowledge-documents/{id}` | `MANAGE_MEDICAL_KNOWLEDGE` | Delete metadata, file, and vectors. |
| `POST /knowledge-documents/reports/{reportId}/sync` | `USE_AI_CHAT` and clinical role | Index one approved report. |

Example question request:

```json
{
  "sessionId": 12,
  "question": "Hom nay toi co bao nhieu ca kham?"
}
```

To list cases for selection, ask `Cho toi xem cac ca kham hom nay`. The backend
routes this to `TODAY_EXAMINATION_LIST`, filters by the application server's date, orders
by `visit_time` (falling back to `created_at`), and returns at most 10 rows. Each
row contains `examination_id`, `encounter_code`, `patient_code`, patient name,
visit time, status, and priority. Doctors are scoped to assigned cases; department
heads can see the clinical list; administrators cannot access it. A follow-up can
select an `examination_id` to request that examination's final result. Rendering
buttons or another picker remains a frontend concern. Configure the container/JVM
timezone consistently with the hospital timezone so "today" has the intended boundary.

`sessionId` is optional. Omit it on the first question to let the backend create
a titled session automatically, then reuse the returned `sessionId` for follow-up questions.

Example answer:

```json
{
  "sessionId": 12,
  "messageId": 84,
  "route": "BUSINESS_DATA",
  "answer": "Hom nay ban co 4 ca kham.",
  "sources": [
    {
      "sourceId": "database:examinations",
      "title": "MySQL examinations aggregate",
      "sourceType": "BUSINESS_DATA",
      "locator": "database:examinations",
      "score": null
    }
  ],
  "warning": null,
  "generatedAt": "2026-08-06T10:00:00",
  "tokensUsed": 176
}
```

Medical-content validation is synchronous. The backend extracts readable text and
sends up to three large samples from the beginning, middle, and end to the configured
chat model. The source is accepted only when the structured assessment identifies
substantive medical or healthcare content with confidence at or above `0.7`. Empty,
ambiguous, non-medical, and unreadable sources return `400 Bad Request` and are not
stored. Model/provider failures also fail closed, so unvalidated content never enters
the knowledge base.

Indexing starts asynchronously only after validation succeeds. A successful request
returns `202 Accepted` with status `PENDING`; use `GET /knowledge-documents` until the
status becomes `INDEXED` or `FAILED`. URLs resolving to loopback, link-local,
multicast, or private IPs are rejected.

Batch upload accepts repeated multipart parts named `files` and one shared
`accessScope`. Each file is limited to 50 MiB. Valid files are accepted and
indexed independently, so one duplicate or invalid file does not reject the
other files. The response reports `acceptedCount`, `rejectedCount`, and one
item per submitted file. Non-medical files are rejected independently with a
`Document rejected: ...` error.

Deleting `DELETE /knowledge-documents/{id}` removes the relational metadata, stored
source file, and every Qdrant chunk matching its `sourceKey`. The indexing worker also
removes chunks produced by an in-flight job if the document is deleted concurrently.

## Database and Report Ingestion

The migration creates `knowledge_documents`, `chat_sessions`, and `chat_messages`.
The chat tables store conversation ownership and content; Qdrant still stores only
knowledge vectors. The migration is
`database/migrations/chatbox_rag_migration.sql`.

The scheduled report sync reads generated reports and their verified diagnosis
reviews. It indexes report ID, examination ID, study date, final diagnosis,
confirmed KL grades, and clinical summary. Patient identifiers, names,
emails, phone numbers, DICOM paths, and image paths are not embedded. Report
vectors carry both `ownerUserId` (the report creator) and `assignedDoctorUserId`;
owner-scoped searches permit either user but do not expose the report to every
department member.

A request that only shows or summarizes a stored report uses `BUSINESS_DATA` and
reads MySQL directly; it is intentionally not a vector-RAG query. A request that
also asks for medical interpretation uses `HYBRID`: the stored report context is
included in the Qdrant retrieval query, then Gemini answers from both the MySQL
record and approved medical evidence.

Generated reports publish an after-commit sync event. Returning an existing PDF
also republishes that event, and the scheduled reconciliation retries missing,
`PENDING`, `PROCESSING`, or `FAILED` report documents. It additionally upgrades
older report vectors to metadata version 2 so the assigned-doctor access field is
present. The manual recovery endpoint remains
`POST /knowledge-documents/reports/{reportId}/sync`.

## Local Startup

```powershell
docker compose --profile rag up -d qdrant ollama
docker exec knee-oa-ollama ollama pull bge-m3
docker compose up -d be
```

Set these values in the backend environment before starting it:

```dotenv
CHAT_AI_ENABLED=true
CHAT_MODEL_PROVIDER=google-genai
CHAT_EMBEDDING_PROVIDER=ollama
CHAT_VECTOR_STORE=qdrant
GEMINI_API_KEY=replace_with_your_key
GEMINI_CHAT_MODEL=gemini-3.5-flash
OLLAMA_BASE_URL=http://ollama:11434
QDRANT_HOST=qdrant
QDRANT_GRPC_PORT=6334
QDRANT_COLLECTION=healthsync_medical_knowledge
CHAT_RETRIEVAL_TOP_K=12
CHAT_MEDICAL_VALIDATION_SAMPLE_CHARS=6000
CHAT_MEDICAL_VALIDATION_MIN_CONFIDENCE=0.7
```

Never commit the Gemini key. `bge-m3` must be pulled before the first ingestion.
