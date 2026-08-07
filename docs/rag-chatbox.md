# HealthSync RAG Chatbox

The chatbox is implemented with Spring AI 2.0. Gemini generates and routes
answers, Ollama serves BGE-M3 embeddings, and Qdrant stores vectors. The
feature is disabled unless `CHAT_AI_ENABLED=true`.

## Request Flow

1. `POST /chat/ask` authenticates the doctor, department head, or administrator.
2. Gemini returns a structured routing decision: `BUSINESS_DATA`, `MEDICAL_RAG`,
   `HYBRID`, or `CLARIFICATION`.
3. Business questions execute one of the read-only queries defined in
   `BusinessDataQueryService`. The LLM never creates or executes SQL.
4. Medical questions are embedded with BGE-M3 and searched in Qdrant. Results
   are filtered by publication status, role scope, and report owner.
5. Gemini receives only the filtered context and returns an answer with source
   metadata and a clinical warning.

Doctors only see aggregates and clinical records assigned to them. Department
heads may access general and doctor-scoped clinical knowledge; owner-scoped
report data remains limited to its owner until department metadata is available.
Administrators may use system aggregates and general/admin knowledge, but not
report or examination clinical details.

## API

All endpoints require a bearer token and their matching permission. Authorized
clinical users can upload and manage medical-knowledge sources. Report sync
still checks the clinical role and the `USE_AI_CHAT` permission.

| Method and path | Permission | Purpose |
| --- | --- | --- |
| `POST /chat/ask` | `USE_AI_CHAT` | Ask a business or medical question. |
| `POST /knowledge-documents/upload` | `MANAGE_MEDICAL_KNOWLEDGE` | Upload PDF, DOC, DOCX, or TXT. |
| `POST /knowledge-documents/upload/batch` | `MANAGE_MEDICAL_KNOWLEDGE` | Upload up to 10 documents with per-file results. |
| `POST /knowledge-documents/url` | `MANAGE_MEDICAL_KNOWLEDGE` | Ingest an approved public HTTP(S) URL. |
| `GET /knowledge-documents` | `MANAGE_MEDICAL_KNOWLEDGE` | Check indexing state and errors. |
| `POST /knowledge-documents/{id}/reindex` | `MANAGE_MEDICAL_KNOWLEDGE` | Reindex a stored source. |
| `DELETE /knowledge-documents/{id}` | `MANAGE_MEDICAL_KNOWLEDGE` | Delete metadata, file, and vectors. |
| `POST /knowledge-documents/reports/{reportId}/sync` | `USE_AI_CHAT` and clinical role | Index one approved report. |

Example question request:

```json
{
  "question": "Hom nay toi co bao nhieu ca kham?"
}
```

Example answer:

```json
{
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
  "generatedAt": "2026-08-06T10:00:00"
}
```

Upload is asynchronous. A successful request returns `202 Accepted` with status
`PENDING`; use `GET /knowledge-documents` until the status becomes `INDEXED` or
`FAILED`. URLs resolving to loopback, link-local, multicast, or private IPs are
rejected.

Batch upload accepts repeated multipart parts named `files` and one shared
`accessScope`. Each file is limited to 50 MiB. Valid files are accepted and
indexed independently, so one duplicate or invalid file does not reject the
other files. The response reports `acceptedCount`, `rejectedCount`, and one
item per submitted file.

## Database and Report Ingestion

The only new MySQL table is `knowledge_documents`; it stores source metadata and
indexing state, not vectors. Qdrant stores vectors. The migration is
`chatbox_rag_migration.sql`.

The scheduled report sync reads generated reports and their verified diagnosis
reviews. It indexes report ID, examination ID, study date, final diagnosis,
confirmed KL grades, and clinical summary. Patient identifiers, names,
emails, phone numbers, DICOM paths, and image paths are not embedded. Report
vectors carry `ownerUserId`, and doctor searches enforce that owner.

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
OLLAMA_BASE_URL=http://ollama:11434
QDRANT_HOST=qdrant
QDRANT_GRPC_PORT=6334
QDRANT_COLLECTION=healthsync_medical_knowledge
```

Never commit the Gemini key. `bge-m3` must be pulled before the first ingestion.
