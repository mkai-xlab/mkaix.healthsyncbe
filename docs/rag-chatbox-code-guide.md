# Hướng Dẫn Code RAG Chatbot

Tài liệu này giải thích RAG chatbox theo mã nguồn đang có trong repository. Nó
chi tiết hơn [rag-chatbox.md](rag-chatbox.md): file kia mô tả hợp đồng sản phẩm,
còn file này mô tả từng dòng hoặc nhóm dòng có liên hệ chặt chẽ đang làm gì, gọi
tiếp đến đâu, thay đổi dữ liệu nào và vì sao phải làm như vậy.

Mọi source path và line number tham chiếu đúng revision hiện tại. Một "nhóm dòng"
là các dòng liền nhau tạo thành một biểu thức Java, annotation hoặc khai báo data
không thể tách rời. Tách chúng thành các mảnh một dòng sẽ làm khó đọc mà không
tăng thêm ý nghĩa.

## 0. Giải Thích Tiếng Việt: Đọc Phần Này Trước

### 0.1 RAG của hệ thống này thực sự là gì?

RAG là viết tắt của *Retrieval-Augmented Generation*: trước khi Gemini trả lời,
backend tự tìm tài liệu liên quan, lấy các đoạn text đã được phê duyệt, rồi đưa
chỉ các đoạn đó vào prompt. Gemini không tự biết database nội bộ, không tự kết
nối Qdrant, và không được tự viết SQL.

Hệ thống có ba kho dữ liệu khác nhau, mỗi kho giữ một loại thông tin:

| Kho | Chứa gì | File/documents sử dụng |
| --- | --- | --- |
| MySQL | user, quyền, chat session, chat message, metadata của tài liệu | `ChatSessionService`, `BusinessDataQueryService`, `KnowledgeIngestionService` |
| Ổ đĩa trong `knowledge-dir` | file PDF/DOC/DOCX/TXT đã upload và HTML đã tải từ URL | `KnowledgeIngestionService`, `KnowledgeDocumentReader` |
| Qdrant | vector embedding của từng đoạn text và metadata để lọc quyền | `KnowledgeIndexingWorker`, `MedicalRagService` |

Ollama/BGE-M3 chỉ làm một việc: biến text thành vector số học. Qdrant so sánh
vector của câu hỏi với vector của các đoạn tài liệu. Gemini làm hai việc: phân
loại câu hỏi (route) và viết câu trả lời từ context mà Java đưa vào.

### 0.2 Luồng đầy đủ khi người dùng gửi `POST /api/v1/chat/ask`

Hãy xem một ví dụ: bác sĩ gửi câu hỏi `"Giải thích KL grade 3"`.

1. `JwtAuthenticationFilter` đọc header `Authorization: Bearer ...`. Nếu token
   hợp lệ, filter đưa username, role và permissions vào `SecurityContext`.
2. `ChatController.ask()` nhận request. `@Valid` kiểm tra `question` không rỗng
   và không dài quá 2.000 ký tự. `@PreAuthorize` kiểm tra đồng thời role và quyền
   `USE_AI_CHAT`.
3. Controller gọi `ChatOrchestratorService.ask(sessionId, question, username)`.
   Orchestrator không tự thao tác database trực tiếp; nó điều phối các service.
4. `ChatSessionService.prepare()` tìm `User` từ username, tạo session nếu
   `sessionId == null`, hoặc kiểm tra session này thuộc đúng user. Sau đó nó lấy
   tối đa 20 message cũ, cắt theo giới hạn 12.000 ký tự, và đảo thứ tự về cũ
   đến mới để Gemini đọc dễ hơn.
5. **Chỉ sau khi đã tạo history**, `prepare()` lưu câu hỏi hiện tại thành
   `ChatMessage(role=USER)`. Vì vậy history gửi cho model không bị lặp câu hỏi
   hiện tại; gateway sẽ ghép `Current question:` riêng biệt.
6. `SpringAiChatGateway.route()` gọi Gemini với router prompt. Gemini trả về
   `ChatRoutingDecision`, ví dụ `MEDICAL_RAG`, không trả lời y khoa ngay lúc này.
7. Orchestrator `switch` theo `route`. Java chủ động chọn hàm nào được gọi;
   Gemini không thể yêu cầu chạy một SQL tùy ý hay đọc file tùy ý.
8. Nếu là `MEDICAL_RAG`, `MedicalRagService.retrieve()` tạo filter theo role và
   user ID, gửi `SearchRequest` sang `VectorStore`. Spring AI dùng Ollama để
   embedding câu hỏi, sau đó Qdrant trả về các chunk vượt similarity threshold.
9. Orchestrator chỉ gọi `answerMedical()` khi có source. Không có source thì
   backend trả câu "không đủ bằng chứng" cố định; nó không cho Gemini tự đoán.
10. Khi có answer, `ChatSessionService.saveAssistantMessage()` lưu message
    ASSISTANT, route và token usage. Cuối cùng API trả `ChatAnswerResponse`.

Một điểm rất quan trọng: USER message và ASSISTANT message nằm trong hai
transaction service khác nhau. Nếu Gemini/Qdrant lỗi sau bước 5, bạn có thể thấy
USER message đã được lưu nhưng chưa có ASSISTANT message. Đây là hành vi hiện tại,
không phải do UI hay repository bị mất dữ liệu.

### 0.3 Bốn route và ý nghĩa của chúng

| Route | Khi nào router nên chọn | Java gọi gì | Gemini nhận gì |
| --- | --- | --- | --- |
| `CLARIFICATION` | Câu hỏi mơ hồ, thiếu ID report/examination | Không query DB/Qdrant | Không gọi answer model; dùng câu hỏi làm rõ |
| `BUSINESS_DATA` | Đếm ca khám, danh sách ca khám, report summary, thống kê | `BusinessDataQueryService` | Context text do SQL cố định tạo ra |
| `MEDICAL_RAG` | Hướng dẫn, kiến thức y khoa, giải thích chẩn đoán/điều trị | `MedicalRagService` | Chỉ các chunk Qdrant đã lọc quyền |
| `HYBRID` | Vừa hỏi dữ liệu HealthSync vừa yêu cầu diễn giải y khoa | SQL trước, RAG sau | Cả business context và medical context |

`HYBRID` không phải là "Gemini tự biết report". Trình tự đúng là: Java lấy report
từ MySQL, chèn text report vào retrieval query, Qdrant tìm guideline liên quan,
rồi Gemini nhận hai nhóm context có nhãn riêng.

### 0.4 Giải thích theo từng file trong luồng trả lời

| File | Trách nhiệm bằng tiếng Việt |
| --- | --- |
| `ChatController.java` | Đầu vào HTTP cho hỏi chat, tạo/list/update session; không chứa logic AI. |
| `ChatOrchestratorService.java` | Trung tâm điều phối: chuẩn bị hội thoại, route, chọn business/RAG/hybrid, lưu answer. |
| `ChatSessionService.java` | Sở hữu session, ownership, history, title, lưu USER/ASSISTANT messages. |
| `SpringAiChatGateway.java` | Lớp duy nhất gọi Gemini: classifier, router và answer generator. |
| `AiChatGateway.java` | Interface để service không phụ thuộc trực tiếp Gemini; unit test mock interface này. |
| `BusinessDataQueryService.java` | Whitelist SQL chỉ đọc; không nhận SQL từ model. |
| `MedicalRagService.java` | Tạo `SearchRequest`, lọc scope ngay tại Qdrant, ghép chunk thành context có giới hạn. |
| `Chat*.java` DTO/entity/repository | Định nghĩa JSON, bảng MySQL và các query ownership/history. |

### 0.5 Quyền trong business data và RAG khác nhau như thế nào?

Với business data, Java thêm predicate SQL. Nếu role là `DOCTOR`, query thêm
`e.doctor_id = :userId`. Admin bị chặn trước khi query nếu intent có thể lộ chi
tiết lâm sàng, ví dụ list ca khám, examination final result, report summary hay
grade distribution.

Với RAG, Java không lấy tất cả vector rồi tự lọc trong RAM. `MedicalRagService`
gửi filter expression sang Qdrant ngay lúc similarity search:

```text
ADMIN  -> PUBLISHED và scope ALL hoặc ADMIN
DOCTOR -> PUBLISHED và scope ALL/DOCTOR,
          hoặc OWNER khi ownerUserId/assignedDoctorUserId = user hiện tại
HEAD   -> hiện tại dùng cùng logic OWNER như DOCTOR,
          không được tự động thấy mọi report riêng của cả khoa
```

Metadata `ownerUserId` và `assignedDoctorUserId` được tạo lúc index report. Nếu
một vector không có metadata phù hợp, nó sẽ không xuất hiện trong kết quả search,
dù model có viết prompt thế nào cũng không đọc được nội dung đó.

### 0.6 Luồng upload một tài liệu y khoa

Khi gọi `POST /knowledge-documents/upload`, `KnowledgeIngestionService.upload()`
chạy theo đúng thứ tự sau:

1. Kiểm tra file tồn tại, không rỗng, đúng extension PDF/DOC/DOCX/TXT và không
   quá `maxDocumentBytes`.
2. Đọc bytes và SHA-256. Nếu đã có `sourceKey=file:<checksum>`, dừng ngay để
   tránh cùng một file được index nhiều lần.
3. `MedicalDocumentValidator` đọc text: PDF dùng PDFBox, TXT dùng UTF-8,
   DOC/DOCX dùng Tika. Text rỗng hay không đọc được sẽ bị từ chối.
4. Validator lấy toàn bộ file ngắn, hoặc ba mẫu đầu/giữa/cuối với file lớn, gửi
   Gemini classifier. Chỉ chấp nhận `medical == true` và `confidence >= 0.7`.
5. Chỉ sau khi validation thành công, server mới ghi file vào `knowledge-dir`
   bằng tên UUID, lưu metadata MySQL với status `PENDING`, rồi publish event.
6. `KnowledgeIndexingWorker` nghe event **sau commit**, chạy nền bằng một thread,
   đọc lại file, chia chunk, embedding qua Ollama và ghi Qdrant.
7. Worker cập nhật `PROCESSING -> INDEXED`; nếu có lỗi thì `FAILED` và lưu error.

Vì validation nằm trước `storeFile()` và `repository.save()`, file không y khoa
không có file rác trên ổ đĩa, không có metadata và không có vector.

### 0.7 URL, file và report là ba source type khác nhau

| Source type | Nội dung gốc | `sourceKey` | Cách index |
| --- | --- | --- |
| `FILE` | bytes upload | `file:<sha256-bytes>` | async worker đọc file trên disk |
| `URL` | HTML đã download | `url:<sha256-normalized-url>` | async worker đọc `source.html` trên disk |
| `REPORT` | text tạo từ MySQL report/examination/review | `report:<reportId>` | `ReportKnowledgeSyncService.index()` chia/ghi vector trực tiếp |

URL phải là HTTP(S), có host, không có user-info. Service resolve DNS và chặn
loopback, private, link-local, multicast; HTTP client cũng tắt redirect. Mục tiêu
là ngăn server bị ép truy cập địa chỉ nội bộ (SSRF).

Report không embedding PII như patient name, email, phone, DICOM path hay image
path. Text index chỉ gồm report ID, examination ID, study date, final diagnosis,
confirmed KL grades và clinical summary. Report được gán scope `OWNER`.

### 0.8 Trạng thái, retry và race condition

`KnowledgeDocumentStatus` có bốn giá trị:

```text
PENDING    -> metadata đã lưu, job chưa bắt đầu hoặc đã yêu cầu reindex
PROCESSING -> worker đã bắt đầu đọc/chunk/embed
INDEXED    -> Qdrant đã có chunk và MySQL đã lưu chunkCount/indexedAt
FAILED     -> worker/report index gặp lỗi, errorMessage được lưu để xem và reindex
```

Worker dùng `KnowledgeDocumentOperationCoordinator` để khóa theo document ID.
Điều này ngăn delete và index cùng chạy trên cùng document trong một JVM. Nếu
delete xảy ra đúng sau khi `vectorStore.add(chunks)`, `markIndexed()` sẽ không
tìm thấy row; worker xóa lại vector vừa thêm để tránh orphan chunk.

Report có ba cách để được index/retry: event sau khi tạo PDF, endpoint manual
`POST /knowledge-documents/reports/{id}/sync`, và scheduled reconciliation mỗi
5 phút. Scheduled query tìm report chưa có knowledge row, chưa `INDEXED`, hoặc
còn checksum metadata cũ.

### 0.9 Cách dùng phần còn lại của tài liệu

Phần 18-23 bên dưới là source đối chiếu. Khi muốn hiểu một dòng Java, hãy đọc
theo thứ tự:

1. Đọc bằng tiếng Việt ở phần 0 để biết nó nằm ở đâu của luồng.
2. Mở file Java trong IDE tại đúng line number ghi trong heading.
3. Đọc code block có `//` comment ở phần 18 hoặc 20.
4. Đọc bảng line-by-line ở phần 1-17 nếu cần biết annotation, field hoặc SQL.
5. Mở test tương ứng ở phần 23 để thấy giả định nào đang được bảo vệ.

Từ phần này trở đi, source code Java vẫn giữ nguyên cú pháp và tên class tiếng
Anh. Điều này cần thiết để bạn có thể copy/đối chiếu chính xác với code chạy thực
tế; mọi diễn giải quan trọng đã được viết bằng tiếng Việt trong phần này.

## 1. Scope and Mental Model

The production files that directly implement this feature are:

1. `chat/`: model gateway, structured result records, routes, and events.
2. `controller/ChatController.java` and `controller/KnowledgeController.java`:
   HTTP entry points.
3. `service/Chat*`, `MedicalRagService`, and `BusinessDataQueryService`:
   answering a question.
4. `service/Knowledge*`, `MedicalDocumentValidator`, and
   `ReportKnowledgeSyncService`: adding/removing/indexing knowledge.
5. `dto/Chat*`, `dto/Knowledge*`, `entity/Chat*`, `entity/Knowledge*`, and the
   matching repositories: the HTTP and MySQL contracts.
6. `config/ChatAiConfiguration`, `config/ChatProperties`, `AsyncConfig`,
   `application.yaml`, and the RAG migration: bean creation, external systems,
   concurrency, and persistence.
7. `PdfExportService` and JWT security classes: upstream event producer and
   request authentication boundaries.

The application has three authoritative stores. MySQL stores users, sessions,
messages and knowledge metadata. The configured knowledge directory stores
original uploaded bytes. Qdrant stores only vector chunks and their metadata.
Gemini is never allowed to execute SQL or Qdrant operations; Java performs both
operations and gives Gemini a bounded text context.

```text
HTTP + JWT
  -> ChatController
  -> ChatOrchestratorService
       -> ChatSessionService.prepare()            (MySQL)
       -> SpringAiChatGateway.route()             (Gemini)
       -> BusinessDataQueryService OR MedicalRagService
            -> MySQL SQL              OR Ollama embedding + Qdrant search
       -> SpringAiChatGateway.answer*()           (Gemini)
       -> ChatSessionService.saveAssistantMessage (MySQL)
  -> JSON response

Knowledge upload / URL / report
  -> metadata and/or original file in MySQL/disk
  -> Spring event after commit
  -> async indexer -> reader -> splitter -> Ollama embedding -> Qdrant
```

## 2. Application, Security, and Feature Switch

### `src/main/java/com/g93/be/BeApplication.java`

| Source lines | Explanation |
| --- | --- |
| 8 | `@SpringBootApplication` enables component scanning, auto-configuration, and configuration-property processing. All RAG `@Service`, `@Component`, and `@Controller` classes are discovered from this package root. |
| 9 | `@EnableScheduling` makes `@Scheduled` in `ReportKnowledgeSyncService.syncNewReports()` run. Without it, report repair never occurs. |
| 12-14 | Standard Java `main`: starts the Spring application context and therefore creates the conditional RAG beans when enabled. |

### `src/main/java/com/g93/be/security/JwtAuthenticationFilter.java`

| Source lines | Explanation |
| --- | --- |
| 23 | `OncePerRequestFilter` guarantees this JWT logic runs no more than once for one HTTP dispatch. |
| 41-48 | Reads `Authorization`. A missing or non-Bearer header is not rejected here; the chain continues unauthenticated and `SecurityConfig` later rejects protected endpoints. |
| 50 | Removes the literal `Bearer ` prefix. |
| 52-55 | A blacklisted token is deliberately not put into the security context. |
| 56-76 | Validates the access token, extracts its username, role, and permissions, turns those into `ROLE_<role>` and plain permission authorities, then creates `UsernamePasswordAuthenticationToken`. `Principal.getName()` in the controllers is this username. |
| 78-80 | Invalid-token exceptions are swallowed so standard Spring Security handling produces the unauthenticated outcome rather than leaking parser details. |
| 82 | Always resumes the filter chain. |

### `src/main/java/com/g93/be/config/SecurityConfig.java`

| Source lines | Explanation |
| --- | --- |
| 28-31 | Enables web security and `@PreAuthorize`, the latter being essential because the chat controllers use method-level access rules. |
| 42-64 | Builds a stateless filter chain. Every unlisted endpoint requires authentication; the JWT filter is inserted before username/password authentication. `/chat/**` and `/knowledge-documents/**` are therefore authenticated before their controller-level permission checks. |
| 67-79 | CORS permits browser clients to send `Authorization` and read file download headers. |

### `src/main/resources/application.yaml`, RAG section

| Source lines | Explanation |
| --- | --- |
| 48-53 | Spring AI model providers are selected by environment. Defaults are `none`, avoiding accidental outbound AI calls. |
| 54-62 | Qdrant config: gRPC host/port, collection and schema initialization. When `CHAT_VECTOR_STORE=qdrant`, Spring AI supplies the `VectorStore` injected into retrieval/indexing services. |
| 63-69 | Google GenAI chat model settings. `temperature: 0.2` favors repeatable classification/routing; 2,048 caps generated output tokens. |
| 70-77 | Ollama embedding settings. The exact embedding model is `bge-m3`; Spring AI uses it automatically when a `VectorStore.add()` or similarity search requires embeddings. |
| 97-105 | Product-level RAG properties: feature flag, physical knowledge directory, file and URL byte limits, `topK=12`, similarity threshold `0.4`, validator sample size 6,000, validation threshold `0.7`, and report reconciliation delay 300,000 ms. |

### `src/main/java/com/g93/be/config/ChatProperties.java`

| Lines | Explanation |
| --- | --- |
| 5 | Binds the `app.chat` YAML subtree, including environment-substituted values, into a type-safe immutable record. |
| 6-15 | Every record component corresponds one-to-one with a property consumed in RAG code: `enabled` controls conditional beans; paths/byte limits govern ingestion; `retrievalTopK` and `similarityThreshold` create search request; sample/minimum-confidence govern classifier; report delay drives scheduled repair. Constructors in tests explicitly build the same contract. |

### `src/main/java/com/g93/be/config/ChatAiConfiguration.java`

| Lines | Explanation |
| --- | --- |
| 15-18 | This configuration, properties binding and all three beans exist only when `app.chat.enabled=true`. A missing switch therefore avoids creating a `ChatClient`, splitter or URL client dedicated to the feature. |
| 20-23 `healthSyncChatClient()` | Receives Spring AI's provider-configured `ChatClient.Builder`, calls `build()`, and exposes the `ChatClient` injected into `SpringAiChatGateway`. The builder's active Google provider/model comes from YAML/environment, not hard-coded Java. |
| 25-34 `medicalKnowledgeSplitter()` | Creates the named `TokenTextSplitter`: nominal chunk size 700 tokens, minimum retained fragment 250 characters, prevents embeddings for fragments below 20 characters, caps one source at 10,000 chunks, and preserves separators. Both uploaded knowledge and report text use this same bean. |
| 36-45 `knowledgeRestClient()` | Constructs JDK HTTP client with 5-second connect timeout and redirects disabled, wraps it in Spring's request factory with 20-second read timeout, then builds the `RestClient` used only by URL ingestion. Disabling redirects is part of SSRF safety: a public URL cannot redirect to a private address in this client. |

### `src/main/java/com/g93/be/config/AsyncConfig.java`

| Source lines | Explanation |
| --- | --- |
| 10-12 | Enables `@Async`, which is used by file indexing and generated-report sync. |
| 14-25 | Declares the named `taskExecutor`. One core/max thread serializes expensive Ollama work; queue capacity 20 provides backpressure. Shutdown waits up to 30 seconds for queued/running jobs. |

## 3. Small Data Contracts (`chat/` and DTOs)

These types have no hidden behavior: Java records generate constructor, accessors,
`equals`, `hashCode`, and `toString`. Enums restrict model/backend states to a
fixed vocabulary.

### `src/main/java/com/g93/be/chat/ChatRoute.java`

| Lines | Explanation |
| --- | --- |
| 3-8 | Defines the only four branches the router may select: `BUSINESS_DATA`, `MEDICAL_RAG`, `HYBRID`, and `CLARIFICATION`. `ChatOrchestratorService` exhaustively switches on this enum. |

### `src/main/java/com/g93/be/chat/BusinessQueryIntent.java`

| Lines | Explanation |
| --- | --- |
| 3-12 | Declares the whitelist of database operations. `TODAY_EXAMINATION_COUNT` and `TODAY_EXAMINATION_LIST` have special date defaults. `UNKNOWN` deliberately causes a `400`, preventing a router hallucination from becoming arbitrary SQL. |

### `src/main/java/com/g93/be/chat/ChatRoutingDecision.java`

| Lines | Explanation |
| --- | --- |
| 3-10 | Structured Gemini router output. `route` decides control flow; `businessIntent` selects a hard-coded query; `entityId` is report/examination ID; `dateFrom/dateTo` are ISO date strings; `clarificationQuestion` is used only in the clarification branch. |

### Other `chat/` records/interfaces

| File and lines | Explanation |
| --- | --- |
| `AiChatGateway.java:3-17` | Interface separating orchestration from a specific provider. It exposes medical document classification, route selection, and three answer variants. Tests mock this interface, so they do not call Gemini. |
| `GeneratedChatAnswer.java:3-4` | Holds generated text and nullable total token usage. The latter becomes `ChatMessage.tokensUsed` and response `tokensUsed`. |
| `BusinessQueryResult.java:7-8` | Carries backend-controlled database context and its source objects to the orchestrator. |
| `MedicalRetrievalResult.java:7-11` | Carries concatenated chunk text and sources. `isEmpty()` bases the decision on source count rather than text alone. |
| `MedicalDocumentAssessment.java:7-10` | Expected classifier JSON shape: medical boolean, confidence number, and human-readable rejection reason. |
| `KnowledgeIndexRequestedEvent.java:3-4` | Event payload containing the persistent `KnowledgeDocument` ID; it is emitted only after a successful upload/reindex transaction. |
| `ReportKnowledgeSyncRequestedEvent.java:3-4` | Equivalent event for a generated PDF report ID. |

### Chat request/response DTOs

| File and lines | Explanation |
| --- | --- |
| `ChatQuestionRequest.java:6-11` | `sessionId` is optional. `question` must be nonblank and at most 2,000 characters; `@Valid` in the controller turns violations into HTTP 400 before service code runs. |
| `CreateChatSessionRequest.java:5-9` | Optional title, maximum 160 characters, and optional examination link. Ownership is checked in the service rather than by DTO validation. |
| `UpdateChatSessionRequest.java:5-9` | Optional title and `active` flag. The service rejects a request where both are omitted. |
| `ChatAnswerResponse.java:6-15` | API result: session/message IDs, route, answer, sources, optional clinical warning, server generation time, and model token usage. |
| `ChatSourceResponse.java:3-8` | Source is normalized to `sourceId`, display title, source type, locator/reference, and optional similarity score. DB sources have `score=null`. |
| `ChatSessionResponse.java:5-12` | Exposes session identity, optional linked examination, title, active flag, and timestamps. |
| `ChatMessageResponse.java:5-13` | Exposes one persisted message. It intentionally has no source/warning fields because those are not stored. |

### Knowledge DTOs

| File and lines | Explanation |
| --- | --- |
| `KnowledgeUrlRequest.java:7-15` | Requires a nonblank title and URL, each bounded before URI parsing. Scope remains optional and defaults to `ALL` in the service. |
| `KnowledgeDocumentResponse.java:5-20` | Listing/upload response. It omits internal `storagePath` and checksum; content/preview/download links are nullable for `REPORT` sources. |
| `KnowledgeBatchUploadItemResponse.java:3-8` | One file outcome: original name, accepted boolean, document for success, error for failure. |
| `KnowledgeBatchUploadResponse.java:5-10` | Aggregate count plus immutable item list. A mixed valid/invalid batch is still a successful endpoint response. |

## 4. Persistence Model and Repository Queries

### Chat entities

| File and lines | Explanation |
| --- | --- |
| `ChatMessageRole.java:3-7` | `USER`, `ASSISTANT`, and `SYSTEM`. Current persistence creates USER and ASSISTANT; SYSTEM is currently only a formatted in-memory examination context. |
| `ChatSession.java:20-61` | Maps `chat_sessions`. Lines 31-33 make every session owned by a user; 35-37 optionally link examination; 39-49 store presentation/status/audit data. `@PrePersist` assigns both timestamps, `@PreUpdate` refreshes `updatedAt`. |
| `ChatMessage.java:21-55` | Maps `chat_messages`. Each message points to a session, has role and non-null TEXT content; only assistant messages normally have route/token values. `@PrePersist` stamps creation time. |

### Knowledge entities and enums

| File and lines | Explanation |
| --- | --- |
| `KnowledgeAccessScope.java:3-8` | `ALL`, `DOCTOR`, `ADMIN`, `OWNER`; these literal enum names are copied into Qdrant metadata and filter expressions, so changing one is a data migration. |
| `KnowledgeDocumentStatus.java:3-8` | State machine values: `PENDING`, `PROCESSING`, `INDEXED`, `FAILED`. |
| `KnowledgeSourceType.java:3-7` | Distinguishes uploaded `FILE`, fetched `URL`, and relationally generated `REPORT`. |
| `KnowledgeDocument.java:22-99` | Maps `knowledge_documents`. `sourceKey` is unique and identifies all Qdrant chunks of the logical source. `storagePath`/checksum are server-only. `uploadedBy` supplies owner metadata for uploaded OWNER sources. `chunkCount`, error, and index timestamp make async status observable. `onCreate()` defaults null status/scope safely; `onUpdate()` refreshes timestamp. |

### Repositories

| File and lines | Explanation |
| --- | --- |
| `ChatSessionRepository.java:10-14` | `findByIdAndUserId` is the principal ownership boundary. Listing is ordered by recently touched session and stable ID. |
| `ChatMessageRepository.java:10-14` | Page history is ascending. The context query is intentionally descending with `Top20`, because the service first wants newest messages under a character budget. |
| `KnowledgeDocumentRepository.java:16-36` | Supports source-key idempotency and filtering. JPQL `search()` performs case-insensitive title/original-name search and applies optional source/status/scope filters. |

### Migration: `database/migrations/chatbox_rag_migration.sql`

| Lines | Explanation |
| --- | --- |
| 1-26 | Creates `knowledge_documents`, unique source key and operational indexes. `uploaded_by_user_id` becomes NULL if the user is deleted so documents can survive. |
| 28-45 | Creates sessions, cascading delete when the owning user is deleted and nullable examination link on examination deletion. Composite index matches session listing. |
| 47-60 | Creates messages; messages cascade with the session. Composite index supports both ordered history queries. |
| 62-77 | Adds feature and the two permissions idempotently. Management permission declares dependency on chat use permission. |
| 79-97 | Grants the seeded permissions to recognised roles, avoiding duplicate `role_permissions` rows. |

## 5. HTTP Controllers

### `src/main/java/com/g93/be/controller/ChatController.java`

| Lines | Explanation |
| --- | --- |
| 30-34 | Declares REST controller at `/chat`, constructor injection, and feature flag. When chat is disabled, these endpoints are absent rather than merely returning a disabled message. |
| 39-40 | Reusable SpEL authorization: valid clinical/admin role **and** `USE_AI_CHAT` authority. |
| 42-49 | `POST /chat/ask`. Validates JSON and delegates exactly `(sessionId, question, principal username)` to orchestrator. It returns `200` for all normal route branches. |
| 51-58 | Explicit session creation returns `201`; all ownership examination checks happen in `ChatSessionService.create`. |
| 60-66 | Lists only the principal's sessions, default page size 20. |
| 68-75 | Lists messages of only one owned session, default 50. |
| 77-84 | Renames/opens/closes only an owned session. |

### `src/main/java/com/g93/be/controller/KnowledgeController.java`

| Lines | Explanation |
| --- | --- |
| 43-47 | RAG-only REST controller rooted at `/knowledge-documents`. |
| 53-63 | Multipart single-file upload. Requires management permission, audit annotation, optional title/scope, and returns `202` because vector indexing follows asynchronously. |
| 65-74 | Batch equivalent. `files` must be explicitly named in multipart; individual failures are returned in body rather than aborting the batch. |
| 76-83 | JSON URL intake, also `202`; `@Valid` verifies DTO bounds before SSRF/content checks. |
| 85-94 | Paginated metadata listing. Filter parameters are optional and passed unchanged to the service. |
| 96-109 | Preview streams original bytes inline; content re-extracts readable source text and forbids caching. |
| 111-116 | Download streams the same resolved file with attachment disposition and audit logging. |
| 118-123 | Marks a document pending and emits reindex event; response is `202`. |
| 125-132 | Manual report index. Controller advertises `202`, but current `syncReport()` itself performs vector work synchronously before returning. |
| 134-140 | Deletes vectors, metadata and possibly file, then returns `204`. |
| 142-163 | Shared stream response helper. Safely parses content type, chooses inline/attachment, UTF-8 encodes filename, uses `no-store`, and sends `nosniff`. |

## 6. Conversation Service in Detail

### `src/main/java/com/g93/be/service/ChatSessionService.java`

| Lines | Explanation |
| --- | --- |
| 33-40 | Conditional service and constants. `DEFAULT_TITLE` identifies not-yet-named sessions; 12,000 is the history character cap. |
| 41-44 | Injected repositories. No model/vector dependency belongs here; this class owns MySQL conversation lifecycle only. |
| 46-52 `create()` | Resolves user, delegates to internal creation, maps entity to DTO. Transaction commits session creation atomically. |
| 54-59 `getSessions()` | Resolves requester, uses repository ownership query, maps each entity. |
| 61-68 `getMessages()` | Resolves requester, requires owned session first, then reads ascending messages. |
| 70-88 `update()` | Rejects empty patch; validates nonblank supplied title; independently applies supplied active flag; touches/save session. |
| 90-108 `prepare()` | Core pre-answer transaction. Resolves user; creates session from question when null or asserts ownership; rejects inactive session; reads prior 20 messages; converts them to model history; persists trimmed USER question; gives a default-titled session a question title; touches session; returns session/user/history. Importantly, history is built **before** saving current question, so callers pass current question separately. |
| 110-123 `saveAssistantMessage()` | Saves ASSISTANT content with route/token usage, touches session, returns generated message ID. This is a separate transaction from `prepare()`. |
| 125-137 `createSession()` | Sets user/title/active. When examination ID exists, loads it and calls authorization before session is saved. |
| 139-152 `saveMessage()` | Single low-level entity construction point, preventing different message shapes in different call paths. |
| 154-157 `touch()` | Assigns current time and saves; JPA lifecycle hook also refreshes it. |
| 159-167 | `requireUser()` translates no user into 404; `requireOwnedSession()` queries by both IDs, intentionally returning 404 rather than revealing another user's session. |
| 169-178 `authorizeExamination()` | A head role may link any examination; a doctor must equal `examination.doctor.id`; all other users get `AccessDeniedException`. |
| 180-203 `formatHistory()` | Input is newest-first. It selects messages until cap, always allowing the newest message even if it alone exceeds cap, reverses selected messages, and formats `ROLE: content`. |
| 205-213 `conversationContext()` | Prepends examination ID only in in-memory prompt history; it does not create a SYSTEM row. |
| 215-226 | Title normalization: blank becomes default; question whitespace collapses for titles only; values longer than 160 become 157 chars plus `...`. |
| 228-247 | Entity-to-response mapping. Notice no lazy user/report data is exposed. |
| 249-250 | `PreparedConversation` moves exactly the session, authenticated user and pre-question history to the orchestrator. |

## 7. Orchestration and LLM Gateway

### `src/main/java/com/g93/be/service/ChatOrchestratorService.java`

| Lines | Explanation |
| --- | --- |
| 20-31 | Declares RAG-only orchestrator and dependencies. The warning is only attached to medical/hybrid results. |
| 33-38 `ask()` setup | Calls `prepare()` first, derives role/user ID from authoritative MySQL user, asks Gemini to route current question using old history, then normalizes null decisions. |
| 40-47 | Exhaustive switch: clarification creates a local answer; business, medical and hybrid delegate to dedicated methods. No branch lets the LLM decide which Java method/SQL to invoke. |
| 48-56 | Persists assistant only after result exists, then maps API response. A provider failure before here leaves the already-persisted USER message without a paired assistant answer. |
| 59-67 `businessAnswer()` | Executes controlled business query first, then calls Gemini with only returned context and history. Sources originate from the database service. |
| 69-79 `medicalAnswer()` | Constructs follow-up-aware retrieval query and asks RAG service. Empty retrieval returns local insufficient-evidence text, sources none and warning; nonempty retrieval calls medical answer gateway. |
| 81-102 `hybridAnswer()` | Executes business data first. That business context is appended to retrieval query, allowing clinical terms/IDs to improve evidence retrieval. Empty medical evidence falls back to business wording but keeps medical warning; successful case concatenates database and vector sources. |
| 104-110 `normalize()` | Null/malformed route becomes safe clarification rather than `NullPointerException`. Other invalid fields are later rejected by the called branch. |
| 112-116 `clarification()` | Uses model's question only when nonblank, otherwise local generic request. |
| 118-124 `contextualRetrievalQuery()` | No history means raw question. With history it keeps only final 2,000 characters and appends explicit current question, bounding vector-query size. |
| 126-142 `response()` | Copies source list immutably and stamps server generation time. The time is not model-provider time. |
| 144-148 | Private record ties answer, sources and warning together through branch handling. |

### `src/main/java/com/g93/be/chat/SpringAiChatGateway.java`

| Lines | Explanation |
| --- | --- |
| 9-12 | Concrete `AiChatGateway` active only with feature flag. It receives the named `ChatClient` bean. |
| 14-23 | Medical-document classifier system prompt. It defines accepted/rejected subject matter, treats samples as untrusted, asks for structured boolean/confidence/reason, and says ambiguous content must be rejected. |
| 25-45 | Router system prompt. It defines route semantics, allowed intents, ID/date extraction rules, warns history is untrusted, and forbids SQL output. `%s` at line 44 is replaced by role at runtime. |
| 47-54 | Answer system prompt. It requires answer language matching, supplied-context-only behavior, resistance to prompt injection, cautious response, and exact numbered examination-list rendering. |
| 59-65 `assessMedicalDocument()` | Sends samples as user data under classifier prompt and deserializes provider structured output into `MedicalDocumentAssessment`. |
| 68-74 `route()` | Formats current role into system prompt, wraps history/current question, calls Gemini, and deserializes to `ChatRoutingDecision`. |
| 77-100 | Three public wrappers label the supplied context as business, medical, or both then reuse `answer()`. Labels help the model distinguish database facts from clinical evidence. |
| 102-119 `answer()` | Calls Gemini; handles null response/result/text defensively; extracts total token usage only when metadata exists. It returns text and nullable token count, not sources. |
| 121-127 `conversationPrompt()` | Uses literal `No previous messages.` for empty history and clearly separates prior context from current question. |

## 8. Controlled Business Data Path

### `src/main/java/com/g93/be/service/BusinessDataQueryService.java`

| Lines | Explanation |
| --- | --- |
| 22-28 | RAG-only service injected with named JDBC and users. It intentionally does not accept free-form SQL/model text. |
| 30-58 `execute()` | Loads user/role, defaults missing intent to `UNKNOWN`, blocks admin clinical intents, computes date range and named parameters, then switches solely over enum to fixed methods. `UNKNOWN` is an explicit bad request. |
| 60-74 `recentExaminations()` | Uses visit time falling back to created time; joins patients; scopes doctor; orders newest first and hard-limits 10. It serializes result maps into context and labels the source `database:examinations/recent`. |
| 76-83 `countExaminations()` | Counts by `e.created_at`, optionally scoped to doctor. This differs intentionally/operationally from list's visit-time definition. |
| 85-93 `countReports()` | Counts report rows joined to examinations, applying doctor scope through examination owner. |
| 95-109 `examinationResult()` | Requires positive ID, reads one allowed examination's status/final diagnosis/study timing and aggregate confirmed grades. Empty result maps to not-found-or-inaccessible. |
| 111-126 `reportSummary()` | Same pattern for report plus examination detail and confirmed grades. |
| 128-137 `gradeDistribution()` | Groups examination max predicted grade and count over range, optionally per doctor. |
| 139-142 `result()` | One source DTO factory; database sources have no vector similarity score. |
| 144-149 `isClinical()` | Determines which intents admins cannot access because they can expose clinical/patient detail. |
| 151-165 `dateRange()` | No explicit date on non-today intents means all data from 1970 through tomorrow. Today intents default both dates to today. `to` is converted to exclusive next-day midnight. Reversed dates are invalid. |
| 167-179 | Parses ISO `LocalDate`, validates positive IDs, turns null database count into zero. |
| 181-182 | Local immutable date range with exclusive upper bound. |

## 9. Medical RAG Retrieval Path

### `src/main/java/com/g93/be/service/MedicalRagService.java`

| Lines | Explanation |
| --- | --- |
| 19-28 | Conditional service. Context is capped at 16,000 characters independently of model output-token configuration. |
| 29-37 `retrieve()` | Builds role-aware Qdrant filter, then `SearchRequest` with query/topK/threshold/configured filter. `VectorStore.similaritySearch()` causes Spring AI to embed the query through the configured Ollama embedding model and issue the filtered Qdrant request. |
| 38-40 | Normalizes null/empty vector-store result to an empty retrieval result. |
| 42-63 | Iterates score-ranked documents, stops at cap, gets title/reference/type/score from chunk metadata, appends text with a source label, and de-duplicates response sources by reference/title. Every chunk may appear in context, but one document source is returned once. |
| 66-85 `scopeFilter()` | Adds mandatory `publicationStatus == PUBLISHED`. Admin sees ALL/ADMIN. Department heads and doctors see ALL/DOCTOR and OWNER only when they are source owner or assigned doctor. Unknown role fails closed with `AccessDeniedException`. |
| 87-89 | Converts arbitrary metadata values to strings while applying fallback. |

## 10. Knowledge Source Reading and Validation

### `src/main/java/com/g93/be/service/KnowledgeDocumentReader.java`

| Lines | Explanation |
| --- | --- |
| 20 | Stateless component reusable by validation, indexing, and content endpoint. |
| 22-25 `read(KnowledgeDocument)` | Wraps persistent `storagePath` as `FileSystemResource` and delegates with stored filename/content type. |
| 27-30 `read(bytes,...)` | Wraps upload/download bytes in a custom resource that retains filename, which Tika needs for type detection. |
| 32-42 private `read()` | PDF goes to PDFBox; TXT is read strictly UTF-8; all other allowed formats (DOC/DOCX and HTML URL storage) go through Tika. Output is Spring AI `Document` objects. |
| 44-50 `readPdf()` | Opens PDF in try-with-resources, extracts all textual pages into one `Document`; unreadable files become a 400-style `IllegalArgumentException`. |
| 52-57 `isType()` | Content type wins when exactly known; filename extension is fallback. |
| 59-71 | `NamedByteArrayResource` overrides filename, preserving it after receiving bytes rather than a multipart file. |

### `src/main/java/com/g93/be/service/MedicalDocumentValidator.java`

| Lines | Explanation |
| --- | --- |
| 13-22 | Conditional service, fixed count of three samples, and dependencies on reader/gateway/config. |
| 24-43 `validate()` | Reads source, filters non-text/null content, joins text, rejects absent text, samples it, calls Gemini classifier, and rejects when assessment is null, not medical, confidence absent, or below threshold. No file/metadata write occurs before this method returns. |
| 45-57 `sample()` | Replaces NUL characters, ensures at least one character sample size, sends complete short documents, or returns beginning/middle/end blocks of exactly configured size for large ones. |

## 11. Ingesting File and URL Sources

### `src/main/java/com/g93/be/service/KnowledgeIngestionService.java`

| Lines | Explanation |
| --- | --- |
| 45-60 | Conditional RAG service. `ALLOWED_EXTENSIONS` is an extension allow-list; dependencies cover metadata, identity, HTTP, validation/read, coordination/deletion and event publication. |
| 62-88 `upload()` | Validates multipart metadata; reads bytes; SHA-256 de-duplicates exact content; validates medical meaning synchronously; resolves uploader; stores random-named physical file; creates FILE metadata/scope/status; saves to MySQL; publishes index event; maps response. Transaction ensures the listener sees event after committed metadata. |
| 90-115 `addUrl()` | Validates a public URL before use; normalizes URL to deterministic source key; blocks duplicate URL; downloads bounded content; classifies it as medical; stores bytes as `source.html`; creates URL metadata and emits index request. |
| 117-134 | `getAll()` overloads: simple created-desc list and filtered paginated search. Keyword blank is normalized to null so JPQL optional-filter behavior works. |
| 136-164 `getFile()` | Loads metadata, requires storage path, resolves both configured root and stored path with `toRealPath`, rejects traversal/outside-root/nonregular files, derives safe media/name and returns a streamable record. Any IO failure becomes 404 to avoid leaking filesystem information. |
| 166-174 `getText()` | First calls `getFile()` as an authorization/path-existence guard, then re-reads document and joins extracted text. |
| 176-177 | Transport record for controller file responses. |
| 179-187 `reindex()` | Sets metadata back to PENDING and clears prior error, saves, emits event. Old vectors are removed later by worker before new add. |
| 189-194 `delete()` | Serializes deletion against worker for same document through operation coordinator, then delegates real deletion. |
| 196-198 | Event publisher boundary. Worker listens after transaction commit. |
| 200-211 `validateFile()` | Rejects null/empty, size over config, or file extension outside allow-list before reading/model call. |
| 213-231 `download()` | Uses configured `RestClient`; requires 2xx, reads at most limit+1, rejects empty and oversize body. |
| 234-251 `validatePublicUrl()` | Requires absolute HTTP(S), host and no user-info. DNS resolution rejects unspecified, loopback, link-local, site-local and multicast addresses, mitigating server-side request forgery. |
| 253-267 `storeFile()` | Creates root, generates UUID filename while preserving extension, normalizes and asserts path stays beneath root, then writes bytes. |
| 269-275 | Multipart byte extraction with domain-specific 400 error. |
| 277-285 | User/document lookup helpers mapping absence to `ResourceNotFoundException`. |
| 287-300 | Response mapper and authenticated action URL builder. Report documents have no storage path, therefore action URLs null. |
| 302-320 | Title uses given title or sanitized basename, defaults safely, clips 255 chars; filename strips path components; extension lowercases using locale-independent rules. |
| 323-329 | SHA-256 helper produces hex checksum; unavailable crypto is unrecoverable server misconfiguration. |

### `src/main/java/com/g93/be/service/KnowledgeBatchIngestionService.java`

| Lines | Explanation |
| --- | --- |
| 16-24 | Conditional batch facade; maximum is exactly 10 files. |
| 26-56 `upload()` | Rejects empty/oversized batch, processes each file by calling regular upload, counts successes, converts expected validation errors into a per-file failure, logs unexpected runtime errors but continues remaining files, and returns immutable results. |

## 12. Async Indexing, State, Locks, and Deletion

### `src/main/java/com/g93/be/service/KnowledgeIndexingWorker.java`

| Lines | Explanation |
| --- | --- |
| 22-32 | Conditional worker depending on state writer, lock coordinator, reader, splitter and vector store. |
| 34-41 `index()` | `@TransactionalEventListener(AFTER_COMMIT)` prevents indexing rolled-back uploads. `@Async(taskExecutor)` moves work off request thread. It acquires document-specific lock before actual work. `fallbackExecution=true` permits direct event delivery outside a transaction. |
| 43-75 `indexExclusively()` | Marks metadata PROCESSING in new transaction; skips deletion race; reads stored source; retains text documents; replaces metadata with source metadata; splits; rejects zero chunks; deletes old chunks by source key; adds new chunks; marks INDEXED. If document disappeared before status save, deletes newly added vectors. Runtime/linkage errors are logged and status becomes FAILED. |
| 77-93 `metadata()` | Produces all fields later used by retrieval: source key/id/title/type/reference/scope/publication. OWNER upload sources additionally receive uploader ID. Metadata is copied to every chunk. |
| 95-100 | Keeps status error bounded at 1,000 characters. |

### `src/main/java/com/g93/be/service/KnowledgeIndexStateService.java`

| Lines | Explanation |
| --- | --- |
| 21-30 `markProcessing()` | Uses `REQUIRES_NEW`, so PROCESSING remains observable even though worker does external vector work outside a long transaction. Missing document returns null. |
| 32-44 `markIndexed()` | Also new transaction; records chunk count/time/status and returns false if concurrent deletion removed row. |
| 46-55 `markFailedIfPresent()` | Best-effort new transaction: deleted documents are not resurrected by a failed worker. |

### `src/main/java/com/g93/be/service/KnowledgeDocumentOperationCoordinator.java`

| Lines | Explanation |
| --- | --- |
| 8-13 | Maintains 256 JVM-local `ReentrantLock`s. It is a striped lock, so unrelated IDs may occasionally share a lock but the array stays bounded. |
| 15-23 | Hashes document ID to a stripe, locks, invokes supplied operation, always unlocks. It protects delete versus index in one application instance. |
| 25-31 | Allocates every lock once during bean creation. |

### `src/main/java/com/g93/be/service/KnowledgeDocumentDeletionService.java`

| Lines | Explanation |
| --- | --- |
| 27-34 `delete()` | In one transaction loads metadata, deletes all Qdrant chunks filtered by source key, deletes metadata, then deletes physical source when applicable. |
| 36-50 `deleteStoredFile()` | REPORT has null path and is skipped. File paths are normalized and must remain under configured root before `Files.deleteIfExists`; failures become server errors rather than silently leaving data. |

## 13. Report-to-Knowledge Pipeline

### `src/main/java/com/g93/be/service/PdfExportService.java`, relevant lines 101-173

As of the X-ray report rework, a report can be generated **exactly once** per
examination and the draft preview (`GET /examinations/{id}/report-draft`) is
locked shut immediately afterward. Neither of those two endpoints is
reachable again once `REPORT_GENERATED` is reached; only `generateAndSavePdfReport`
still runs (idempotently) to recover a missing file. See
[examination-verification-report-code-guide.md](examination-verification-report-code-guide.md)
for the full line-by-line breakdown of both endpoints; only the RAG-relevant
event-publishing lines are annotated here.

| Lines | Explanation |
| --- | --- |
| 103-106 | `generateAndSavePdfReport(Long examinationId, String username, GenerateReportRequest request)` — the third parameter is the doctor's confirmed form fields, always optional. |
| 107-111 | Locks the examination row (`findByIdForUpdate`), resolves the caller, authorizes access. |
| 113-122 | If already `REPORT_GENERATED` and the PDF file still exists on disk, republishes `ReportKnowledgeSyncRequestedEvent` for the existing report ID and returns it untouched — `request` is not even inspected. This is what repairs prior failed/missing vector indexing without regenerating the PDF. |
| 123 | `requireVerified()` — only `VERIFIED`, or `REPORT_GENERATED` with a missing file (recovery), reach the render path below. |
| 125-132 | Builds `ReportForm` (pre-filled defaults overlaid with any submitted edits) and the Thymeleaf `data` context for `pdf/xray-report-template`. |
| 134-145 | Renders and atomically moves the PDF into the export directory. |
| 147-156 | Persists `Report` metadata, then publishes `ReportKnowledgeSyncRequestedEvent` for the newly saved report ID — same event type as the reuse branch above, so the listener code path in `ReportKnowledgeSyncService` does not need to distinguish first-time generation from a republish. |
| 159-164 | Saves `findings`/`conclusion` onto the `Examination` row (not onto `Report`) and marks the examination `REPORT_GENERATED`. This happens in the same transaction as the `Report` insert, so the sync event is only ever published for a report whose examination-level result text is already durable. |
| 167-172 | Any exception during render/persist deletes the temporary/output files and rethrows; the event already published for a reused report (lines 113-122) is unaffected since that branch returns before reaching this `try` block. |

Annotated excerpt of only the RAG-relevant lines (the two places
`ReportKnowledgeSyncRequestedEvent` is published, and why the listener never
needs to tell first-generation and republish apart):

```java
public ReportResponse generateAndSavePdfReport(
        Long examinationId, String username, GenerateReportRequest request) {
    Examination examination = examinationRepository.findByIdForUpdate(examinationId)
            .orElseThrow(/* ... */);
    User currentUser = getUser(username);
    authorizeReportAccess(examination, currentUser);

    if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
        Report existingReport = reportRepository
                .findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                .filter(this::reportFileExists)
                .orElse(null);
        if (existingReport != null) {
            // PUBLISH #1 - "reuse" path: no new Report row, no new PDF, but the event still
            // fires. This is the ONLY way a stuck/failed knowledge row gets retried without
            // the doctor having to somehow "un-generate" a report first - they just call
            // generate-report again (any client retry, or the FE resending on a stale UI
            // state) and this branch alone repairs indexing.
            eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(existingReport.getId()));
            return toResponse(existingReport);
        }
        // existingReport == null: status says REPORT_GENERATED but no live file - falls
        // through to render again below, which will hit PUBLISH #2 with a FRESH report ID.
    }
    requireVerified(examination, "generating");

    // ... build ReportForm, render PDF, move file (lines 125-145; no RAG-relevant lines here) ...

    Report report = new Report();
    // ... report.set*(...) (lines 148-155; no RAG-relevant lines here) ...
    Report savedReport = reportRepository.save(report);
    // PUBLISH #2 - "first generation or recovery" path: a brand-new (or freshly re-rendered)
    // Report row exists NOW, saved in the SAME transaction as everything above it. Because
    // Spring only actually delivers this event after the surrounding @Transactional commits
    // (see ReportKnowledgeSyncService.syncGeneratedReport(), annotated separately below),
    // the listener is GUARANTEED to find savedReport.getId() persisted in MySQL by the time
    // it runs - there is no race where the event fires before the report row is durable.
    eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(savedReport.getId()));

    examination.setFindings(String.join("\n", form.findings()));
    examination.setConclusion(form.conclusion());
    examination.setStatus(ExaminationStatus.REPORT_GENERATED);
    examinationRepository.save(examination);
    return toResponse(savedReport);
    // Both PUBLISH #1 and PUBLISH #2 emit the SAME event type carrying only a report ID -
    // ReportKnowledgeSyncService.syncGeneratedReport() (below) has no idea, and does not
    // need to know, which branch produced it.
}
```

### `src/main/java/com/g93/be/service/ReportKnowledgeSyncService.java`

| Lines | Explanation |
| --- | --- |
| 36-47 | Conditional service sharing splitter/vector store but not file reader: report content originates from relational fields. |
| 48-62 `syncReport()` | Manual path: requester must exist, admin is prohibited, doctor must be report owner or assigned doctor, then `index()` is called. Heads pass the doctor-specific check. |
| 64-73 `syncGeneratedReport()` | Async after-commit listener calls `index(loadReport(reportId))` in a new transaction. Errors are logged to avoid breaking PDF generation. |
| 75-92 `syncNewReports()` | Every configured delay, finds at most 100 reports whose knowledge row is missing/not INDEXED or has old metadata checksum, indexes each independently, and continues after failures. |
| 94-140 `index()` | Uses `report:<id>` source key, creates/reuses metadata, marks OWNER/PROCESSING, sets metadata version checksum, assigns source owner, builds deliberately limited text (report/examination IDs, study date, diagnosis, grades, clinical summary), attaches owner/assigned-doctor metadata, splits, replaces old vectors, then marks INDEXED or FAILED. |
| 143-165 `loadReport()` | Executes one fixed SQL aggregation. `COALESCE(operating_doctor_id, e.doctor_id)` defines report owner. Empty result or zero owner is treated as unavailable approved report. |
| 167-176 | Maps report knowledge response with no file URLs; `nullable()` writes `not provided` instead of null into embedding text. |
| 178-181 | Private record keeps raw SQL projection typed through indexing. |

## 14. How One Question Executes, Line by Line

For `POST /api/v1/chat/ask` with `{ "question": "Explain KL grade 3" }`:

1. JWT filter lines 41-76 authenticates, then controller lines 42-49 validates and calls orchestrator.
2. Orchestrator lines 33-38 calls session `prepare()` lines 90-108. A new session is created/title derived, empty history is built, and the user question is saved.
3. `SpringAiChatGateway.route()` lines 68-74 sends router prompt plus role/history/question to Gemini. Suppose structured response route is `MEDICAL_RAG`.
4. Orchestrator switch lines 40-47 calls `medicalAnswer()` lines 69-79. It calls `contextualRetrievalQuery()` lines 118-124.
5. `MedicalRagService.retrieve()` lines 29-37 builds doctor filter, asks vector store. The vector store embeds query with BGE-M3 then searches Qdrant. Qdrant rejects vectors without metadata matching role/filter.
6. Lines 42-63 construct bounded context and source DTOs. Empty sources return a local refusal; otherwise gateway lines 85-90/102-119 calls Gemini with that context.
7. Orchestrator lines 48-56 calls `saveAssistantMessage()` lines 110-123, then returns JSON answer/sources/warning.

For `"How many examinations today?"`, step 4 instead calls
`businessAnswer()`, which calls `BusinessDataQueryService.countExaminations()`;
Gemini receives only `examination_count=<n>` context. For a hybrid report
interpretation, it runs fixed report SQL before vector retrieval and supplies both
contexts to answer generation.

## 15. Error, Status, and Security Consequences

| Situation | Code path and externally visible result |
| --- | --- |
| Missing/invalid question | DTO validation -> `GlobalExceptionHandler.handleValidationExceptions()` -> 400. |
| No JWT/invalid JWT | No security context -> Spring Security rejects protected endpoint. |
| Valid user missing chat role/permission | `@PreAuthorize` -> `AccessDeniedException` -> 403. |
| Other user's session | `findByIdAndUserId` empty -> 404, avoiding existence disclosure. |
| Router says unknown business intent | `BusinessDataQueryService.execute()` -> `IllegalArgumentException` -> 400. |
| Admin requests report/examination clinical data | `UnauthorizedAccessException` -> 403 before SQL. |
| No qualifying vector | Local insufficient-evidence answer, no second Gemini answer call, clinical warning. |
| Invalid/non-medical file | Validator throws before disk/metadata write -> 400; batch records just that item failed. |
| Indexer exception | Worker catches it -> `KnowledgeDocumentStatus.FAILED`; original metadata/error is visible in listing. |
| Delete races indexing | Lock serializes the operations; worker also checks row existence when setting INDEXED and deletes chunks if deleted. |
| Gemini quota/rate limitation | Global handler detects relevant provider cause text and returns 429 with generic quota message. |

## 16. Tests that Lock Down the Behavior

| Test file | What the line-level production behavior it verifies |
| --- | --- |
| `ChatOrchestratorServiceTest.java` | Each route calls only correct data path; empty RAG does not ask model to invent; hybrid uses business result in retrieval query. |
| `ChatSessionServiceTest.java` | Assigned examination link, cross-user denial, auto-title, ordered history, inactive session rejection. |
| `MedicalRagServiceTest.java` | Top-K configuration, owner/assigned-doctor filter, unsupported role denial. |
| `BusinessDataQueryServiceTest.java` | Doctor SQL scope, admin clinical denial, date/list query semantics and fixed ten-row cap. |
| `KnowledgeIngestionServiceTest.java` | Validated upload lifecycle, file path protections, and rejection before file/metadata/event side effects. |
| `KnowledgeIndexingWorkerTest.java` | UTF-8 plain-text extraction baseline. |
| `MedicalDocumentValidatorTest.java` | Sampling and model assessment rejection thresholds. |
| `KnowledgeBatchIngestionServiceTest.java` | Partial batch success semantics and max batch limit. |
| `KnowledgeDocumentDeletionServiceTest.java` | Vector/metadata/file deletion ordering and path safety. |
| `ReportKnowledgeSyncServiceTest.java` | Retry SQL and dual owner/assigned-doctor report metadata. |
| `KnowledgeControllerAuthorizationTest.java` | Management-route authorization contract. |

## 17. Reading Order for Debugging

When debugging a wrong answer, start with `ChatOrchestratorService.ask()` and log
or inspect: route decision, business intent, role, retrieval filter, Qdrant match
count, metadata, constructed context, then final Gemini answer. When debugging a
missing source, start with `knowledge_documents.status/error_message`, then worker
logs, physical path, Qdrant collection, embedding model availability and metadata
filter. This order follows the actual call graph and avoids treating the final LLM
answer as the source of truth.

## 18. Annotated Source: Important Methods

The following blocks reproduce the important control-flow methods in an
annotated form. The executable code remains the source file named above; these
are explanatory copies. Comments beginning with `//` explain the adjacent line,
not an additional behavior.

### 18.1 `ChatOrchestratorService.ask`: one request, four possible routes

Source: `ChatOrchestratorService.java:33-57`.

```java
public ChatAnswerResponse ask(Long sessionId, String question, String username) {
    // 1. Resolve/create the owned session, build OLD history, then save the USER row.
    //    The returned history deliberately does not yet contain `question`.
    ChatSessionService.PreparedConversation conversation =
            chatSessionService.prepare(sessionId, question, username);

    // 2. Role and user ID come from MySQL User, not from the model output.
    //    These values later constrain SQL and Qdrant access.
    String roleCode = conversation.user().getRole().getCode();
    String history = conversation.history();

    // 3. Gemini returns structured JSON mapped to ChatRoutingDecision.
    //    normalize prevents a null route from crashing the switch.
    ChatRoutingDecision decision = normalize(aiGateway.route(question, roleCode, history));

    // 4. Java, not Gemini, chooses the exact allowed execution branch.
    AnswerResult result = switch (decision.route()) {
        // No database/vector/model answer call. Return the router's clarification text.
        case CLARIFICATION -> new AnswerResult(
                new GeneratedChatAnswer(clarification(decision), null), List.of(), null);

        // Run fixed SQL selected by enum, then ask Gemini only to phrase that result.
        case BUSINESS_DATA -> businessAnswer(question, username, decision, history);

        // Search authorised vector chunks, then answer only when evidence exists.
        case MEDICAL_RAG -> medicalAnswer(question, roleCode, conversation.user().getId(), history);

        // Run business SQL first, use it to improve retrieval, then answer from both contexts.
        case HYBRID -> hybridAnswer(
                question, username, roleCode, conversation.user().getId(), decision, history);
    };

    // 5. The assistant row is persisted only after every required external call succeeded.
    //    If Gemini/Qdrant fails earlier, the USER row from prepare() remains without this row.
    ChatMessage savedMessage = chatSessionService.saveAssistantMessage(
            conversation.session(), decision.route().name(), result.answer());

    // 6. Build API-only response. Sources/warning are returned now but not stored in ChatMessage.
    return response(
            conversation.session().getId(),
            savedMessage.getId(),
            decision.route(),
            result.answer(),
            result.sources(),
            result.warning());
}
```

The important invariant is that the model can classify but cannot broaden the
backend's operational surface. A `BUSINESS_DATA` decision still goes through a
finite Java `switch`; a `MEDICAL_RAG` decision still receives an authorization
filter before searching Qdrant.

```java
private AnswerResult medicalAnswer(String question, String roleCode, Long userId, String history) {
    // Add bounded follow-up history to the vector query. This is retrieval context,
    // not the entire model prompt history.
    MedicalRetrievalResult result = medicalRagService.retrieve(
            contextualRetrievalQuery(question, history), roleCode, userId);

    if (result.isEmpty()) {
        // Do not ask Gemini to answer a medical question with no approved evidence.
        return new AnswerResult(new GeneratedChatAnswer(
                "I could not find sufficient approved medical evidence in the knowledge base.", null),
                List.of(), MEDICAL_WARNING);
    }

    // `result.context()` contains only chunks which passed Qdrant similarity and scope filters.
    return new AnswerResult(
            aiGateway.answerMedical(question, result.context(), history),
            result.sources(),
            MEDICAL_WARNING);
}

private AnswerResult hybridAnswer(
        String question, String username, String roleCode, Long userId,
        ChatRoutingDecision decision, String history) {
    // The report/examination value comes from controlled MySQL SQL, before any LLM answer.
    BusinessQueryResult business = businessDataQueryService.execute(decision, username);

    // Including that factual data in the retrieval query makes a guideline search specific
    // to the selected report, diagnosis or grade.
    MedicalRetrievalResult medical = medicalRagService.retrieve(
            contextualRetrievalQuery(question + "\nHEALTHSYNC DATA:\n" + business.context(), history),
            roleCode, userId);

    if (medical.isEmpty()) {
        // The user still receives correct operational data; warning remains because request was medical.
        return new AnswerResult(
                aiGateway.answerBusiness(question, business.context(), history),
                business.sources(), MEDICAL_WARNING);
    }

    // Preserve database source first, append clinical-evidence sources after it.
    List<ChatSourceResponse> sources = new ArrayList<>(business.sources());
    sources.addAll(medical.sources());
    return new AnswerResult(
            aiGateway.answerHybrid(question, business.context(), medical.context(), history),
            sources, MEDICAL_WARNING);
}
```

### 18.2 `ChatSessionService.prepare`: persistence order and follow-up context

Source: `ChatSessionService.java:90-108`, `180-213`.

```java
@Transactional
public PreparedConversation prepare(Long sessionId, String question, String username) {
    // Look up the principal's actual User. Missing user is 404; no anonymous fallback exists.
    User user = requireUser(username);

    // First question creates a session and derives its title from the question.
    // Existing conversation must belong to exactly this user.
    ChatSession session = sessionId == null
            ? createSession(user, titleFromQuestion(question), null)
            : requireOwnedSession(sessionId, user);

    // Closed conversations retain history but cannot accept a new message.
    if (!session.isActive()) {
        throw new IllegalArgumentException("Chat session is inactive");
    }

    // Query returns newest -> oldest. conversationContext() reverses selected messages
    // so Gemini receives chronological `USER:` / `ASSISTANT:` history.
    String history = conversationContext(session, chatMessageRepository
            .findTop20BySessionIdOrderByCreatedAtDescIdDesc(session.getId()));

    // Persist after history construction: caller supplies current question separately to Gemini.
    saveMessage(session, ChatMessageRole.USER, question.trim(), null, null);

    // A session created by an older/default flow becomes titled at first real question.
    if (DEFAULT_TITLE.equals(session.getTitle())) {
        session.setTitle(titleFromQuestion(question));
    }

    // Makes this session newest in its owner's session list.
    touch(session);

    // Carries session/user/history to orchestrator, all scoped to this transaction's work.
    return new PreparedConversation(session, user, history);
}

private String formatHistory(List<ChatMessage> newestFirst) {
    if (newestFirst == null || newestFirst.isEmpty()) {
        return "";
    }

    List<ChatMessage> selected = new ArrayList<>();
    int characters = 0;
    for (ChatMessage message : newestFirst) {
        int messageLength = message.getContent() == null ? 0 : message.getContent().length();

        // Stop before adding an older row that would exceed 12,000 chars.
        // `!selected.isEmpty()` means the newest message is retained even if it is very large.
        if (!selected.isEmpty() && characters + messageLength > MAX_HISTORY_CHARACTERS) {
            break;
        }
        selected.add(message);
        characters += messageLength;
    }

    // Repository order is newest-first; the prompt needs chronological order.
    Collections.reverse(selected);
    StringBuilder history = new StringBuilder();
    for (ChatMessage message : selected) {
        history.append(message.getRole().name())
                .append(": ")
                .append(message.getContent())
                .append('\n');
    }
    return history.toString().trim();
}

private String conversationContext(ChatSession session, List<ChatMessage> messages) {
    String history = formatHistory(messages);
    if (session.getExamination() == null) {
        return history;
    }

    // This is not saved as a ChatMessage(SYSTEM); it exists only in this model prompt.
    String examinationContext = "SYSTEM: This conversation is linked to examination ID "
            + session.getExamination().getId() + ".";
    return history.isBlank() ? examinationContext : examinationContext + "\n" + history;
}
```

The separate `saveAssistantMessage()` method runs in its own transaction because
`ChatOrchestratorService` calls it after external calls. This gives a useful audit
trail for user input, but a retry strategy must account for an unpaired USER row.

### 18.3 `SpringAiChatGateway`: turning controlled data into provider calls

Source: `SpringAiChatGateway.java:59-127`.

```java
public ChatRoutingDecision route(String question, String roleCode, String conversationHistory) {
    return chatClient.prompt()
            // Role is substituted into router instructions. It is not supplied by user text.
            .system(ROUTER_PROMPT.formatted(roleCode))
            // History is clearly labelled follow-up context and question is delimited.
            .user(conversationPrompt(question, conversationHistory))
            // Executes the configured Google GenAI provider request.
            .call()
            // Spring AI maps the structured response to the Java record.
            .entity(ChatRoutingDecision.class);
}

private GeneratedChatAnswer answer(String question, String context, String conversationHistory) {
    ChatResponse response = chatClient.prompt()
            // ANSWER_RULES say to use only context and ignore instructions inside it.
            .system(ANSWER_RULES)
            // Context is appended by Java only after history/current-question delimiter.
            .user(conversationPrompt(question, conversationHistory) + "\n\n" + context)
            .call()
            // Unlike route(), retain raw response for usage metadata.
            .chatResponse();

    if (response == null || response.getResult() == null) {
        // A syntactically successful but empty provider result does not become null application content.
        return new GeneratedChatAnswer("The AI provider returned an empty response.", null);
    }

    // Usage data is optional across providers, so each nullable level is checked.
    Integer tokensUsed = response.getMetadata() == null || response.getMetadata().getUsage() == null
            ? null
            : response.getMetadata().getUsage().getTotalTokens();
    String content = response.getResult().getOutput().getText();
    if (content == null || content.isBlank()) {
        content = "The AI provider returned an empty response.";
    }
    return new GeneratedChatAnswer(content, tokensUsed);
}

private String conversationPrompt(String question, String conversationHistory) {
    // Avoid passing Java null as literal model content.
    String history = conversationHistory == null || conversationHistory.isBlank()
            ? "No previous messages."
            : conversationHistory;
    return "Conversation history (for follow-up context only):\n" + history
            + "\n\nCurrent question:\n" + question;
}
```

## 18A. Mã Nguồn Tiếng Việt: Business, Knowledge và Report

### 18A.1 `BusinessDataQueryService`: toàn bộ SQL được phép chạy

Nguồn: `src/main/java/com/g93/be/service/BusinessDataQueryService.java`.

```java
public BusinessQueryResult execute(ChatRoutingDecision decision, String username) {
    // Identity/role thật luôn được nạp lại từ database, không tin dữ liệu LLM.
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    String role = user.getRole().getCode();
    BusinessQueryIntent intent = decision.businessIntent() == null
            ? BusinessQueryIntent.UNKNOWN
            : decision.businessIntent();

    // Admin có thể xem aggregate không lâm sàng nhưng không được xem chi tiết clinical/patient.
    if ("ADMIN".equals(role) && isClinical(intent)) {
        throw new UnauthorizedAccessException("Administrators cannot access clinical examination details");
    }

    DateRange range = dateRange(decision, intent);
    MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("from", range.from())
            .addValue("to", range.to())
            .addValue("userId", user.getId())
            .addValue("entityId", decision.entityId());
    boolean scopedDoctor = "DOCTOR".equals(role);

    // Đây là toàn bộ bề mặt SQL của chatbot. UNKNOWN bị từ chối, không có fallback SQL.
    return switch (intent) {
        case TODAY_EXAMINATION_COUNT, EXAMINATION_COUNT ->
                countExaminations(parameters, range, scopedDoctor);
        case TODAY_EXAMINATION_LIST -> recentExaminations(parameters, range, scopedDoctor);
        case REPORT_COUNT -> countReports(parameters, range, scopedDoctor);
        case EXAMINATION_FINAL_RESULT -> examinationResult(parameters, decision.entityId(), scopedDoctor);
        case REPORT_SUMMARY -> reportSummary(parameters, decision.entityId(), scopedDoctor);
        case GRADE_DISTRIBUTION -> gradeDistribution(parameters, range, scopedDoctor);
        case UNKNOWN -> throw new IllegalArgumentException("Unsupported business data question");
    };
}

private BusinessQueryResult recentExaminations(
        MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
    // "Hôm nay" của list ưu tiên visit_time, fallback created_at cho dữ liệu cũ.
    String examinationTime = "COALESCE(e.visit_time, e.created_at)";
    String sql = "SELECT e.id AS examination_id, e.encounter_code, p.patient_code, "
            + "p.full_name AS patient_name, " + examinationTime + " AS visit_time, "
            + "e.status, e.priority FROM examinations e "
            + "JOIN patients p ON p.patient_code = e.patient_id "
            + "WHERE " + examinationTime + " >= :from AND " + examinationTime + " < :to"
            // Bác sĩ chỉ thấy case được gán cho chính mình.
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
            // Đây là hard limit backend, model không thể yêu cầu hơn 10 dòng.
            + " ORDER BY " + examinationTime + " DESC, e.id DESC LIMIT 10";
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    return result("recent_examinations=" + rows + ", from=" + range.from() + ", to=" + range.to()
                    + ", maximum_results=10",
            "MySQL recent examinations", "database:examinations/recent");
}

private BusinessQueryResult countExaminations(
        MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
    // Count dùng created_at, khác với list dùng visit_time/created_at fallback.
    String sql = "SELECT COUNT(*) FROM examinations e WHERE e.created_at >= :from AND e.created_at < :to"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
    return result("examination_count=" + value(count) + ", from=" + range.from() + ", to=" + range.to(),
            "MySQL examinations aggregate", "database:examinations");
}

private BusinessQueryResult countReports(
        MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
    String sql = "SELECT COUNT(*) FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "WHERE r.created_at >= :from AND r.created_at < :to"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    Long count = jdbcTemplate.queryForObject(sql, parameters, Long.class);
    return result("report_count=" + value(count) + ", from=" + range.from() + ", to=" + range.to(),
            "MySQL report aggregate", "database:report");
}
```

```java
private BusinessQueryResult examinationResult(
        MapSqlParameterSource parameters, Long examinationId, boolean scopedDoctor) {
    requireId(examinationId, "examination");
    String sql = "SELECT e.id, e.status, e.final_diagnosis, e.study_date, e.study_time, "
            + "(SELECT GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') "
            + "FROM diagnosis_reviews dr WHERE dr.examination_id = e.id) AS confirmed_kl_grades "
            + "FROM examinations e WHERE e.id = :entityId"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    if (rows.isEmpty()) {
        // Cùng thông báo cho không tồn tại và không được phép, tránh lộ existence của record.
        throw new ResourceNotFoundException("Examination not found or not accessible");
    }
    return result(rows.getFirst().toString(), "MySQL examination " + examinationId,
            "database:examinations/" + examinationId);
}

private BusinessQueryResult reportSummary(
        MapSqlParameterSource parameters, Long reportId, boolean scopedDoctor) {
    requireId(reportId, "report");
    String sql = "SELECT r.id, r.created_at, r.clinical_summary, e.id AS examination_id, "
            + "e.status, e.final_diagnosis, "
            + "(SELECT GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') "
            + "FROM diagnosis_reviews dr WHERE dr.examination_id = e.id) AS confirmed_kl_grades "
            + "FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "WHERE r.id = :entityId"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    if (rows.isEmpty()) {
        throw new ResourceNotFoundException("Report not found or not accessible");
    }
    return result(rows.getFirst().toString(), "MySQL report " + reportId,
            "database:report/" + reportId);
}

private BusinessQueryResult gradeDistribution(
        MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
    String sql = "SELECT e.max_predicted_grade AS grade, COUNT(*) AS total FROM examinations e "
            + "WHERE e.created_at >= :from AND e.created_at < :to"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
            + " GROUP BY e.max_predicted_grade ORDER BY e.max_predicted_grade";
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    return result("grade_distribution=" + rows + ", from=" + range.from() + ", to=" + range.to(),
            "MySQL examination grade aggregate", "database:examinations/grade-distribution");
}

private BusinessQueryResult result(String context, String title, String reference) {
    // Mọi nhánh business trả đúng một source loại BUSINESS_DATA, score luôn null.
    return new BusinessQueryResult(context,
            List.of(new ChatSourceResponse(reference, title, "BUSINESS_DATA", reference, null)));
}

private boolean isClinical(BusinessQueryIntent intent) {
    return intent == BusinessQueryIntent.TODAY_EXAMINATION_LIST
            || intent == BusinessQueryIntent.EXAMINATION_FINAL_RESULT
            || intent == BusinessQueryIntent.REPORT_SUMMARY
            || intent == BusinessQueryIntent.GRADE_DISTRIBUTION;
}

private DateRange dateRange(ChatRoutingDecision decision, BusinessQueryIntent intent) {
    boolean noExplicitDate = (decision.dateFrom() == null || decision.dateFrom().isBlank())
            && (decision.dateTo() == null || decision.dateTo().isBlank());
    if (noExplicitDate && intent != BusinessQueryIntent.TODAY_EXAMINATION_COUNT
            && intent != BusinessQueryIntent.TODAY_EXAMINATION_LIST) {
        // Intent không-phải-hôm-nay mà không nêu ngày => toàn bộ dữ liệu lịch sử.
        return new DateRange(LocalDate.of(1970, 1, 1).atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay());
    }
    LocalDate from = parseDate(decision.dateFrom(), LocalDate.now());
    LocalDate to = parseDate(decision.dateTo(), from);
    if (to.isBefore(from)) {
        throw new IllegalArgumentException("dateTo must not be before dateFrom");
    }
    // Mốc trên exclusive: [00:00 from, 00:00 ngày sau to).
    return new DateRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
}

private LocalDate parseDate(String raw, LocalDate fallback) {
    return raw == null || raw.isBlank() ? fallback : LocalDate.parse(raw);
}

private void requireId(Long id, String type) {
    if (id == null || id < 1) {
        throw new IllegalArgumentException("A valid " + type + " id is required");
    }
}

private long value(Long count) {
    // COUNT bình thường không null, nhưng fallback này giữ context ổn định khi driver trả null.
    return count == null ? 0 : count;
}
```

### 18A.2 `MedicalRagService`: retrieval, cắt context và filter quyền

Nguồn: `src/main/java/com/g93/be/service/MedicalRagService.java`.

```java
public MedicalRetrievalResult retrieve(String question, String roleCode, Long userId) {
    // Filter quyền được tạo trước search và chạy ở Qdrant, không phải hậu kiểm ở Java.
    String scopeFilter = scopeFilter(roleCode, userId);
    SearchRequest request = SearchRequest.builder()
            .query(question)
            .topK(properties.retrievalTopK())
            .similarityThreshold(properties.similarityThreshold())
            .filterExpression(scopeFilter)
            .build();

    // Spring AI gọi embedding model Ollama cho query rồi gửi similarity search tới Qdrant.
    List<Document> matches = vectorStore.similaritySearch(request);
    if (matches == null || matches.isEmpty()) {
        return new MedicalRetrievalResult("", List.of());
    }

    StringBuilder context = new StringBuilder();
    Map<String, ChatSourceResponse> uniqueSources = new LinkedHashMap<>();
    for (Document document : matches) {
        // Bảo vệ prompt Gemini không vượt quá ngân sách context do backend đặt ra.
        if (context.length() >= MAX_CONTEXT_CHARS) {
            break;
        }
        Map<String, Object> metadata = document.getMetadata();
        String title = stringValue(metadata.get("title"), "Medical knowledge source");
        String reference = stringValue(metadata.get("reference"), stringValue(metadata.get("sourceKey"), null));
        Double score = document.getScore();
        String text = document.getText();
        if (text != null && !text.isBlank()) {
            int remaining = MAX_CONTEXT_CHARS - context.length();
            context.append("[SOURCE: ").append(title).append("]\n")
                    .append(text, 0, Math.min(text.length(), remaining)).append("\n\n");
        }
        // Một tài liệu có nhiều chunk nhưng API chỉ trả source một lần theo reference.
        String key = reference == null ? title : reference;
        uniqueSources.putIfAbsent(key,
                new ChatSourceResponse(key, title,
                        stringValue(metadata.get("sourceType"), "MEDICAL_DOCUMENT"), reference, score));
    }
    return new MedicalRetrievalResult(context.toString(), new ArrayList<>(uniqueSources.values()));
}

private String scopeFilter(String roleCode, Long userId) {
    // Mọi chunk index thành công được gán PUBLISHED; chunk khác không bao giờ match.
    String published = "publicationStatus == 'PUBLISHED' && ";
    if ("ADMIN".equals(roleCode)) {
        return published + "(accessScope == 'ALL' || accessScope == 'ADMIN')";
    }
    if ("HEAD_OF_DEPARTMENT".equals(roleCode) || "DEPARTMENT_HEAD".equals(roleCode)) {
        // Trưởng khoa không mặc định đọc mọi report OWNER của mọi bác sĩ.
        return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                + " || assignedDoctorUserId == " + userId + ")))";
    }
    if ("DOCTOR".equals(roleCode)) {
        return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                + " || assignedDoctorUserId == " + userId + ")))";
    }
    throw new AccessDeniedException("Role is not allowed to search medical knowledge");
}

private String stringValue(Object value, String fallback) {
    return value == null ? fallback : value.toString();
}
```

### 18A.3 `MedicalDocumentValidator` và `KnowledgeDocumentReader`

Nguồn: `MedicalDocumentValidator.java`, `KnowledgeDocumentReader.java`.

```java
public void validate(byte[] bytes, String originalName, String contentType) {
    // Cùng reader sẽ được dùng ở validation, preview content và worker index.
    List<Document> documents = documentReader.read(bytes, originalName, contentType);

    // Bỏ Document không phải text/null; còn lại nối thành một nội dung để classifier đọc.
    String content = documents.stream()
            .filter(document -> document != null && document.isText())
            .map(Document::getText)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElseThrow(() -> new IllegalArgumentException("No readable text was found in the document"));

    String samples = sample(content, properties.medicalValidationSampleChars());
    MedicalDocumentAssessment assessment = aiChatGateway.assessMedicalDocument(samples);

    // Fail closed: provider null/lỗi, medical=false, thiếu confidence hoặc confidence thấp đều reject.
    if (assessment == null || !Boolean.TRUE.equals(assessment.medical())
            || assessment.confidence() == null
            || assessment.confidence() < properties.medicalValidationMinConfidence()) {
        String reason = assessment == null || assessment.reason() == null || assessment.reason().isBlank()
                ? "the content is not clearly medical"
                : assessment.reason().trim();
        throw new IllegalArgumentException("Document rejected: " + reason);
    }
}

String sample(String content, int sampleChars) {
    String normalized = content.replace('\u0000', ' ').trim();
    int size = Math.max(1, sampleChars);
    if (normalized.length() <= size * SAMPLE_COUNT) {
        // File ngắn: classifier nhận toàn bộ, tránh mất ngữ cảnh.
        return "[COMPLETE DOCUMENT]\n" + normalized;
    }
    // File dài: ba vị trí giúp giảm rủi ro đầu file y khoa nhưng phần còn lại không liên quan.
    int middleStart = Math.max(0, normalized.length() / 2 - size / 2);
    int endStart = normalized.length() - size;
    return "[BEGINNING SAMPLE]\n" + normalized.substring(0, size)
            + "\n\n[MIDDLE SAMPLE]\n" + normalized.substring(middleStart, middleStart + size)
            + "\n\n[ENDING SAMPLE]\n" + normalized.substring(endStart);
}

public List<Document> read(KnowledgeDocument knowledge) {
    // Worker đã có metadata/path; bọc file thật thành Spring Resource.
    return read(new FileSystemResource(knowledge.getStoragePath()),
            knowledge.getOriginalName(), knowledge.getContentType());
}

public List<Document> read(byte[] bytes, String originalName, String contentType) {
    // ByteArrayResource thường không có filename; Tika cần filename để nhận dạng đúng.
    Resource resource = new NamedByteArrayResource(bytes, originalName);
    return read(resource, originalName, contentType);
}

private List<Document> read(Resource resource, String originalName, String contentType) {
    if (isType(originalName, contentType, "pdf", "application/pdf")) {
        return readPdf(resource); // PDFBox text extraction
    }
    if (isType(originalName, contentType, "txt", "text/plain")) {
        TextReader reader = new TextReader(resource);
        reader.setCharset(StandardCharsets.UTF_8); // không phụ thuộc default charset của máy chạy
        return reader.get();
    }
    // DOC/DOCX và source HTML từ URL đi qua Apache Tika.
    return new TikaDocumentReader(resource).get();
}

private List<Document> readPdf(Resource resource) {
    try (PDDocument pdf = PDDocument.load(resource.getInputStream())) {
        return List.of(new Document(new PDFTextStripper().getText(pdf)));
    } catch (IOException exception) {
        throw new IllegalArgumentException("Could not read PDF document", exception);
    }
}
```

### 18A.4 `KnowledgeIngestionService`: upload, URL, file an toàn và event

Nguồn: `src/main/java/com/g93/be/service/KnowledgeIngestionService.java`.

```java
@Transactional
public KnowledgeDocumentResponse upload(
        MultipartFile file, String title, KnowledgeAccessScope scope, String username) {
    validateFile(file);                 // extension/empty/size trước khi đọc bytes
    byte[] bytes = readBytes(file);
    String checksum = sha256(bytes);
    if (repository.existsBySourceKey("file:" + checksum)) {
        throw new IllegalArgumentException("This document has already been uploaded");
    }

    // Không có DB/file side effect nào trước dòng này thành công.
    medicalDocumentValidator.validate(bytes, file.getOriginalFilename(), file.getContentType());
    User user = findUser(username);
    Path storagePath = storeFile(bytes, file.getOriginalFilename());

    KnowledgeDocument document = new KnowledgeDocument();
    document.setSourceKey("file:" + checksum); // dùng đồng thời cho duplicate và xóa toàn bộ vector source
    document.setTitle(normalizeTitle(title, file.getOriginalFilename()));
    document.setSourceType(KnowledgeSourceType.FILE);
    document.setOriginalName(safeFileName(file.getOriginalFilename()));
    document.setContentType(file.getContentType());
    document.setStoragePath(storagePath.toString());
    document.setChecksum(checksum);
    document.setAccessScope(scope == null ? KnowledgeAccessScope.ALL : scope);
    document.setUploadedBy(user);
    document = repository.save(document); // entity đặt PENDING nếu status chưa được set

    // Listener chạy AFTER_COMMIT nên worker chỉ thấy metadata/file đã bền vững.
    requestIndex(document.getId());
    return toResponse(document);
}

@Transactional
public KnowledgeDocumentResponse addUrl(KnowledgeUrlRequest request, String username) {
    URI uri = validatePublicUrl(request.url());
    String sourceKey = "url:" + sha256(uri.normalize().toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (repository.existsBySourceKey(sourceKey)) {
        throw new IllegalArgumentException("This URL has already been added");
    }
    byte[] bytes = download(uri); // client timeout + no redirect + byte limit
    medicalDocumentValidator.validate(bytes, "source.html", "text/html");
    Path storagePath = storeFile(bytes, "source.html");

    KnowledgeDocument document = new KnowledgeDocument();
    document.setSourceKey(sourceKey);
    document.setTitle(request.title().trim());
    document.setSourceType(KnowledgeSourceType.URL);
    document.setSourceUrl(uri.toString());
    document.setOriginalName("source.html");
    document.setContentType("text/html");
    document.setStoragePath(storagePath.toString());
    document.setChecksum(sha256(bytes));
    document.setAccessScope(request.accessScope() == null ? KnowledgeAccessScope.ALL : request.accessScope());
    document.setUploadedBy(findUser(username));
    document = repository.save(document);
    requestIndex(document.getId());
    return toResponse(document);
}

private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("Document file is required");
    }
    if (file.getSize() > properties.maxDocumentBytes()) {
        throw new IllegalArgumentException("Document exceeds the configured size limit");
    }
    // Filename được safeFileName() trước khi lấy extension, tránh path giả từ client.
    String extension = extension(file.getOriginalFilename());
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
        throw new IllegalArgumentException("Only PDF, DOC, DOCX, and TXT documents are supported");
    }
}
```

```java
private byte[] download(URI uri) {
    byte[] bytes = knowledgeRestClient.get().uri(uri).exchange((request, response) -> {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalArgumentException("URL returned HTTP " + response.getStatusCode().value());
        }
        // Đọc tối đa limit + 1 để phát hiện chính xác remote body quá lớn.
        int readLimit = (int) Math.min(Integer.MAX_VALUE - 1L, properties.maxUrlBytes() + 1L);
        try (java.io.InputStream input = response.getBody()) {
            return input.readNBytes(readLimit);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read URL content", exception);
        }
    });
    if (bytes == null || bytes.length == 0) {
        throw new IllegalArgumentException("The URL returned no content");
    }
    if (bytes.length > properties.maxUrlBytes()) {
        throw new IllegalArgumentException("URL content exceeds the configured size limit");
    }
    return bytes;
}

private URI validatePublicUrl(String rawUrl) {
    URI uri = URI.create(rawUrl.trim());
    // Không chấp nhận URL relative, protocol ngoài HTTP(S), host trống hay URL có user:password@.
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Only absolute HTTP or HTTPS URLs are supported");
    }
    try {
        // Duyệt tất cả kết quả DNS A/AAAA. Một địa chỉ local/private là reject toàn bộ URL.
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Private or local URLs are not allowed");
            }
        }
    } catch (UnknownHostException exception) {
        throw new IllegalArgumentException("URL host could not be resolved", exception);
    }
    return uri;
}

@Transactional(readOnly = true)
public KnowledgeDocumentFile getFile(Long id) {
    KnowledgeDocument document = findDocument(id);
    if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
        // REPORT source không có file upload để preview/download.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
    try {
        // toRealPath giải link trước khi startsWith, chống symlink escape ra ngoài knowledgeDir.
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize().toRealPath();
        Path path = Path.of(document.getStoragePath()).toAbsolutePath().normalize().toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Knowledge document file not found");
        }
        String contentType = document.getContentType();
        if (contentType == null || contentType.isBlank()) contentType = Files.probeContentType(path);
        if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";
        String fileName = safeFileName(document.getOriginalName());
        if (fileName == null || fileName.isBlank()) fileName = path.getFileName().toString();
        return new KnowledgeDocumentFile(new FileSystemResource(path), fileName, contentType, Files.size(path));
    } catch (IOException exception) {
        // Không tiết lộ path thực hoặc khác biệt file-mất/path-không-hợp-lệ.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
}

@Transactional(readOnly = true)
public String getText(Long id) {
    KnowledgeDocument document = findDocument(id);
    getFile(id); // tái dùng check path/root/file tồn tại trước khi parser đọc
    return documentReader.read(document).stream()
            .map(Document::getText)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n\n"));
}

@Transactional
public KnowledgeDocumentResponse reindex(Long id) {
    KnowledgeDocument document = findDocument(id);
    document.setStatus(KnowledgeDocumentStatus.PENDING);
    document.setErrorMessage(null);
    repository.save(document);
    // Worker sẽ xóa vector sourceKey cũ trước add vector mới.
    requestIndex(id);
    return toResponse(document);
}

public void delete(Long id) {
    // Cùng lock với worker: không cho delete/index xen kẽ cùng document trong một JVM.
    operationCoordinator.executeExclusively(id, () -> {
        deletionService.delete(id);
        return null;
    });
}
```

### 18A.5 `KnowledgeIndexingWorker` và state service: từ PENDING sang Qdrant

Nguồn: `KnowledgeIndexingWorker.java`, `KnowledgeIndexStateService.java`.

```java
@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void index(KnowledgeIndexRequestedEvent event) {
    // taskExecutor hiện có đúng một thread; các job index được tuần tự hóa thêm ở mức executor.
    operationCoordinator.executeExclusively(event.documentId(), () -> {
        indexExclusively(event);
        return null;
    });
}

private void indexExclusively(KnowledgeIndexRequestedEvent event) {
    log.info("Starting knowledge indexing for document {}", event.documentId());
    try {
        // REQUIRES_NEW: UI có thể nhìn thấy PROCESSING dù embedding còn đang chạy.
        KnowledgeDocument knowledge = stateService.markProcessing(event.documentId());
        if (knowledge == null) {
            // Document đã bị xóa khi event đang nằm trong queue.
            log.info("Skipping indexing because knowledge document {} was deleted", event.documentId());
            return;
        }

        List<Document> parsed = documentReader.read(knowledge);
        List<Document> enriched = parsed.stream()
                .filter(document -> document != null && document.isText())
                // Thay parser metadata bằng metadata chuẩn chứa sourceKey/scope/owner.
                .map(document -> new Document(document.getText(), metadata(knowledge)))
                .toList();
        List<Document> chunks = splitter.apply(enriched);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No readable text was found in the document");
        }

        // Reindex mang nghĩa replace: xóa toàn bộ vector cũ cùng sourceKey trước.
        vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", knowledge.getSourceKey()).build());
        // Spring AI embedding từng chunk bằng BGE-M3 và ghi vector + metadata vào Qdrant.
        vectorStore.add(chunks);

        if (!stateService.markIndexed(event.documentId(), chunks.size())) {
            // Delete có thể đã xóa row ngay sau add; phải rollback vector bằng tay.
            vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", knowledge.getSourceKey()).build());
            log.info("Discarded indexed chunks because knowledge document {} was deleted", event.documentId());
            return;
        }
        log.info("Knowledge indexing completed for document {} with {} chunks",
                event.documentId(), chunks.size());
    } catch (RuntimeException | LinkageError exception) {
        // Không ném ra executor; lưu FAILED để UI/admin reindex sau.
        log.error("Knowledge indexing failed for document {}", event.documentId(), exception);
        stateService.markFailedIfPresent(event.documentId(), truncate(exception.getMessage()));
    }
}
```

```java
private Map<String, Object> metadata(KnowledgeDocument document) {
    String reference = document.getSourceUrl() == null
            ? "knowledge-document:" + document.getId()
            : document.getSourceUrl();
    Map<String, Object> metadata = new HashMap<>();
    // Các key này là hợp đồng giữa indexer và MedicalRagService.scopeFilter().
    metadata.put("sourceKey", document.getSourceKey());
    metadata.put("knowledgeDocumentId", document.getId());
    metadata.put("title", document.getTitle());
    metadata.put("sourceType", document.getSourceType().name());
    metadata.put("reference", reference);
    metadata.put("accessScope", document.getAccessScope().name());
    metadata.put("publicationStatus", "PUBLISHED");
    if (document.getAccessScope() == KnowledgeAccessScope.OWNER && document.getUploadedBy() != null) {
        metadata.put("ownerUserId", document.getUploadedBy().getId());
    }
    return metadata;
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public KnowledgeDocument markProcessing(Long documentId) {
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) return null;
    document.setStatus(KnowledgeDocumentStatus.PROCESSING);
    document.setErrorMessage(null);
    return repository.save(document);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean markIndexed(Long documentId, int chunkCount) {
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) return false; // tín hiệu cho worker xóa vector vừa add
    document.setChunkCount(chunkCount);
    document.setStatus(KnowledgeDocumentStatus.INDEXED);
    document.setIndexedAt(LocalDateTime.now());
    document.setErrorMessage(null);
    repository.save(document);
    return true;
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void markFailedIfPresent(Long documentId, String errorMessage) {
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) return; // không hồi sinh row đã delete chỉ để ghi lỗi
    document.setStatus(KnowledgeDocumentStatus.FAILED);
    document.setErrorMessage(errorMessage);
    repository.save(document);
}

public <T> T executeExclusively(Long documentId, Supplier<T> operation) {
    // 256 stripe lock giữ bộ nhớ cố định; hai ID khác nhau đôi khi cùng stripe nhưng vẫn an toàn.
    ReentrantLock lock = locks[Math.floorMod(Long.hashCode(documentId), LOCK_COUNT)];
    lock.lock();
    try {
        return operation.get();
    } finally {
        lock.unlock();
    }
}
```

### 18A.6 Batch và delete: side effect được cô lập theo source

Nguồn: `KnowledgeBatchIngestionService.java`, `KnowledgeDocumentDeletionService.java`.

```java
public KnowledgeBatchUploadResponse upload(
        List<MultipartFile> files, KnowledgeAccessScope scope, String username) {
    if (files == null || files.isEmpty()) {
        throw new IllegalArgumentException("At least one document file is required");
    }
    if (files.size() > MAX_BATCH_FILES) {
        throw new IllegalArgumentException("A batch can contain at most " + MAX_BATCH_FILES + " documents");
    }

    List<KnowledgeBatchUploadItemResponse> items = new ArrayList<>(files.size());
    int accepted = 0;
    for (MultipartFile file : files) {
        String originalName = file == null ? null : file.getOriginalFilename();
        try {
            // Dùng lại toàn bộ flow single upload nên không tạo hai quy tắc validation khác nhau.
            KnowledgeDocumentResponse document = ingestionService.upload(file, null, scope, username);
            items.add(new KnowledgeBatchUploadItemResponse(originalName, true, document, null));
            accepted++;
        } catch (IllegalArgumentException exception) {
            // File invalid/duplicate/non-medical chỉ fail chính nó, batch vẫn chạy file sau.
            items.add(new KnowledgeBatchUploadItemResponse(
                    originalName, false, null, exception.getMessage()));
        } catch (RuntimeException exception) {
            log.error("Could not accept knowledge document {} from batch", originalName, exception);
            // Không leak chi tiết internal error vào response.
            items.add(new KnowledgeBatchUploadItemResponse(
                    originalName, false, null, "Could not accept document"));
        }
    }
    return new KnowledgeBatchUploadResponse(
            files.size(), accepted, files.size() - accepted, List.copyOf(items));
}

@Transactional
public void delete(Long id) {
    KnowledgeDocument document = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
    // Xóa mọi chunk cùng sourceKey, không cần biết một source đã bị split bao nhiêu chunk.
    vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", document.getSourceKey()).build());
    repository.delete(document);
    deleteStoredFile(document);
}

private void deleteStoredFile(KnowledgeDocument document) {
    if (document.getStoragePath() == null) return; // REPORT không có file source
    try {
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
        Path storedFile = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
        // Metadata bị lỗi không được biến delete API thành lệnh xóa file bất kỳ trên máy chủ.
        if (!storedFile.startsWith(root)) {
            throw new IllegalStateException("Invalid knowledge storage path");
        }
        Files.deleteIfExists(storedFile);
    } catch (IOException exception) {
        throw new IllegalStateException("Could not delete stored knowledge document", exception);
    }
}
```

### 18A.7 `ReportKnowledgeSyncService`: report thành vector owner-scoped

Nguồn: `src/main/java/com/g93/be/service/ReportKnowledgeSyncService.java`.

```java
@Transactional
public KnowledgeDocumentResponse syncReport(Long reportId, String username) {
    User requester = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if ("ADMIN".equals(requester.getRole().getCode())) {
        throw new UnauthorizedAccessException("Administrators cannot index clinical reports");
    }
    ReportKnowledge row = loadReport(reportId);
    if ("DOCTOR".equals(requester.getRole().getCode())
            && !requester.getId().equals(row.ownerUserId())
            && !requester.getId().equals(row.assignedDoctorUserId())) {
        throw new UnauthorizedAccessException("You can only index reports from your own examinations");
    }
    // Endpoint trả 202 nhưng flow manual hiện tại thực hiện index đồng bộ trước khi return.
    return index(row);
}

@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void syncGeneratedReport(ReportKnowledgeSyncRequestedEvent event) {
    try {
        // Event chỉ mang ID; query lại sau commit để đọc report hoàn chỉnh.
        index(loadReport(event.reportId()));
    } catch (RuntimeException exception) {
        // Không làm thất bại việc sinh PDF chỉ vì Qdrant tạm thời lỗi.
        log.error("Could not synchronize generated report {} to Qdrant", event.reportId(), exception);
    }
}

@Scheduled(
        fixedDelayString = "${app.chat.report-sync-delay-ms:300000}",
        initialDelayString = "${app.chat.report-sync-delay-ms:300000}")
public void syncNewReports() {
    String sql = "SELECT r.id FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "LEFT JOIN knowledge_documents k ON k.source_key = CONCAT('report:', r.id) "
            + "WHERE (e.doctor_id IS NOT NULL OR r.operating_doctor_id IS NOT NULL) "
            + "AND (k.id IS NULL OR k.status <> 'INDEXED' "
            + "OR COALESCE(k.checksum, '') <> 'report-metadata-v2') "
            + "ORDER BY r.id LIMIT 100";
    for (Long reportId : jdbcTemplate.queryForList(sql, Map.of(), Long.class)) {
        try {
            index(loadReport(reportId));
        } catch (RuntimeException exception) {
            // Một report lỗi không cản các report khác được repair.
            log.error("Could not synchronize report {} to Qdrant", reportId, exception);
        }
    }
}
```

```java
private KnowledgeDocumentResponse index(ReportKnowledge row) {
    String sourceKey = "report:" + row.reportId();
    // Một report luôn tái sử dụng một knowledge_documents row, scheduler gọi lại cũng không tạo duplicate.
    KnowledgeDocument knowledge = repository.findBySourceKey(sourceKey)
            .orElseGet(KnowledgeDocument::new);
    knowledge.setSourceKey(sourceKey);
    knowledge.setTitle("Approved clinical report " + row.reportId());
    knowledge.setSourceType(KnowledgeSourceType.REPORT);
    knowledge.setAccessScope(KnowledgeAccessScope.OWNER);
    knowledge.setStatus(KnowledgeDocumentStatus.PROCESSING);
    // Dấu hiệu version metadata để scheduled job biết vector cũ cần nâng cấp.
    knowledge.setChecksum("report-metadata-v2");
    knowledge.setUploadedBy(userRepository.findById(row.ownerUserId()).orElse(null));
    knowledge = repository.save(knowledge);

    // Đây là toàn bộ text report được embedding. Có chủ đích không có PII/path ảnh/DICOM.
    String text = "Approved HealthSync clinical report. Report ID: " + row.reportId()
            + ". Examination ID: " + row.examinationId()
            + ". Study date: " + nullable(row.studyDate())
            + ". Final diagnosis: " + nullable(row.finalDiagnosis())
            + ". Confirmed KL grades: " + nullable(row.confirmedGrades())
            + ". Clinical summary: " + nullable(row.clinicalSummary()) + ".";

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("sourceKey", sourceKey);
    metadata.put("knowledgeDocumentId", knowledge.getId());
    metadata.put("title", knowledge.getTitle());
    metadata.put("sourceType", KnowledgeSourceType.REPORT.name());
    metadata.put("reference", "report:" + row.reportId());
    metadata.put("accessScope", KnowledgeAccessScope.OWNER.name());
    metadata.put("ownerUserId", row.ownerUserId());
    if (row.assignedDoctorUserId() != null) {
        // Chính key này giúp bác sĩ được gán case đọc report do người khác tạo.
        metadata.put("assignedDoctorUserId", row.assignedDoctorUserId());
    }
    metadata.put("publicationStatus", "PUBLISHED");
    List<Document> chunks = splitter.apply(List.of(new Document(text, metadata)));

    try {
        vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", sourceKey).build());
        vectorStore.add(chunks);
        knowledge.setChunkCount(chunks.size());
        knowledge.setStatus(KnowledgeDocumentStatus.INDEXED);
        knowledge.setIndexedAt(LocalDateTime.now());
        knowledge.setErrorMessage(null);
    } catch (RuntimeException exception) {
        knowledge.setStatus(KnowledgeDocumentStatus.FAILED);
        String message = exception.getMessage() == null ? "Report indexing failed" : exception.getMessage();
        knowledge.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        repository.save(knowledge);
        throw exception;
    }
    knowledge = repository.save(knowledge);
    return response(knowledge);
}

private ReportKnowledge loadReport(Long reportId) {
    // SQL hằng, chỉ lấy các field được cho phép xuất hiện trong vector text.
    String sql = "SELECT r.id AS report_id, e.id AS examination_id, "
            + "COALESCE(r.operating_doctor_id, e.doctor_id) AS owner_user_id, "
            + "e.doctor_id AS assigned_doctor_user_id, e.study_date, "
            + "e.final_diagnosis, r.clinical_summary, "
            + "GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') AS confirmed_grades "
            + "FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "LEFT JOIN diagnosis_reviews dr ON dr.examination_id = e.id WHERE r.id = :reportId "
            + "GROUP BY r.id, e.id, r.operating_doctor_id, e.doctor_id, e.study_date, "
            + "e.final_diagnosis, r.clinical_summary";
    List<ReportKnowledge> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("reportId", reportId),
            (resultSet, rowNum) -> new ReportKnowledge(
                    resultSet.getLong("report_id"),
                    resultSet.getLong("examination_id"),
                    resultSet.getLong("owner_user_id"),
                    resultSet.getObject("assigned_doctor_user_id", Long.class),
                    resultSet.getObject("study_date"),
                    resultSet.getString("final_diagnosis"),
                    resultSet.getString("confirmed_grades"),
                    resultSet.getString("clinical_summary")));
    if (rows.isEmpty() || rows.getFirst().ownerUserId() == 0) {
        throw new ResourceNotFoundException("Approved report not found");
    }
    return rows.getFirst();
}

private String nullable(Object value) {
    // Embedding text không chứa literal null khó hiểu; dùng mô tả nhất quán.
    return value == null ? "not provided" : value.toString();
}
```

### 18A.8 Controller và DTO/Entity/Repository: vì sao không lặp lại từng method dài?

`ChatController` và `KnowledgeController` chỉ nhận request, chạy `@Valid`/
`@PreAuthorize`, rồi gọi service tương ứng. Logic quan trọng không nằm trong
controller; nó đã được chú thích tiếng Việt tại các block 18A và 24. Các DTO,
record, enum, entity và repository không có business flow ẩn: chúng chỉ giữ data
contract, JPA mapping hoặc derived query. Bản khai báo đầy đủ của chúng ở section
21, còn line-by-line English reference ở section 1-17 được giữ để so sánh trực
tiếp với file thật.

Điểm cần nhớ khi đọc nhóm file này:

| Nhóm | Điều cần kiểm tra khi debug |
| --- | --- |
| DTO | Validation annotation có chạy không, tức controller có `@Valid` hay không. |
| `ChatSession`/`ChatMessage` entity | FK nào xác định ownership/session và timestamp nào được JPA callback tự đặt. |
| `KnowledgeDocument` entity | `sourceKey`, `accessScope`, `status`, `uploadedBy`, `storagePath` đang có giá trị gì. |
| Repository | Query có scope theo `userId` chưa; message history đang lấy ASC hay top-20 DESC. |
| Controller | Role/authority nào được `@PreAuthorize` yêu cầu trước khi service chạy. |

Vì vậy, phần code có khả năng làm thay đổi hành vi RAG thực tế đã được chép và chú
thích trực tiếp ở các block 18A và 24; các lớp còn lại được mô tả đầy đủ theo vai
trò và field ở section 21 để tránh lặp lại hàng trăm dòng Lombok/JPA boilerplate.

`answerBusiness`, `answerMedical`, and `answerHybrid` are thin wrappers around
`answer()`. Their only additional behavior is adding explicit labels such as
`BUSINESS DATA CONTEXT` and `RETRIEVED MEDICAL CONTEXT`; this preserves one
answer implementation and lets the provider distinguish the provenance of data.

## 18. Annotated Source: Important Methods (English Reference Continued)

### 18.4 `BusinessDataQueryService.execute`: model intent to a fixed query

Source: `BusinessDataQueryService.java:30-74`.

```java
public BusinessQueryResult execute(ChatRoutingDecision decision, String username) {
    // Do not trust the router for identity; reload current user and role from MySQL.
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    String role = user.getRole().getCode();

    // Missing intent cannot select a default query.
    BusinessQueryIntent intent = decision.businessIntent() == null
            ? BusinessQueryIntent.UNKNOWN : decision.businessIntent();

    // Clinical detail is blocked for ADMIN before any SQL is sent.
    if ("ADMIN".equals(role) && isClinical(intent)) {
        throw new UnauthorizedAccessException("Administrators cannot access clinical examination details");
    }

    // Date conversion and named parameter binding are performed once for all branches.
    DateRange range = dateRange(decision, intent);
    MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("from", range.from())
            .addValue("to", range.to())
            .addValue("userId", user.getId())
            .addValue("entityId", decision.entityId());
    boolean scopedDoctor = "DOCTOR".equals(role);

    // This is the SQL allow-list. No router-produced SQL string is ever executed.
    return switch (intent) {
        case TODAY_EXAMINATION_COUNT, EXAMINATION_COUNT ->
                countExaminations(parameters, range, scopedDoctor);
        case TODAY_EXAMINATION_LIST -> recentExaminations(parameters, range, scopedDoctor);
        case REPORT_COUNT -> countReports(parameters, range, scopedDoctor);
        case EXAMINATION_FINAL_RESULT -> examinationResult(parameters, decision.entityId(), scopedDoctor);
        case REPORT_SUMMARY -> reportSummary(parameters, decision.entityId(), scopedDoctor);
        case GRADE_DISTRIBUTION -> gradeDistribution(parameters, range, scopedDoctor);
        case UNKNOWN -> throw new IllegalArgumentException("Unsupported business data question");
    };
}

private BusinessQueryResult recentExaminations(
        MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
    // A visit time takes precedence; older data may have only created_at.
    String examinationTime = "COALESCE(e.visit_time, e.created_at)";
    String sql = "SELECT e.id AS examination_id, e.encounter_code, p.patient_code, "
            + "p.full_name AS patient_name, " + examinationTime + " AS visit_time, "
            + "e.status, e.priority FROM examinations e "
            + "JOIN patients p ON p.patient_code = e.patient_id "
            + "WHERE " + examinationTime + " >= :from AND " + examinationTime + " < :to"
            // Doctors only see assigned rows. Heads are deliberately unscoped here.
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
            // LIMIT is a hard data/control boundary, not an LLM request preference.
            + " ORDER BY " + examinationTime + " DESC, e.id DESC LIMIT 10";

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    // The provider receives serialised result data and an explicit maximum, not JDBC access.
    return result("recent_examinations=" + rows + ", from=" + range.from() + ", to=" + range.to()
                    + ", maximum_results=10",
            "MySQL recent examinations", "database:examinations/recent");
}
```

Every remaining helper follows this same pattern: validate required ID where
needed, execute a constant SQL expression with named parameters, handle empty
results, then create one `BusinessQueryResult` with a database source marker.

### 18.5 `MedicalRagService.retrieve`: authorization occurs inside search

Source: `MedicalRagService.java:29-85`.

```java
public MedicalRetrievalResult retrieve(String question, String roleCode, Long userId) {
    // Create filter before issuing vector request. The query cannot return unauthorized chunks
    // and rely on a later Java filter.
    String scopeFilter = scopeFilter(roleCode, userId);
    SearchRequest request = SearchRequest.builder()
            .query(question)
            .topK(properties.retrievalTopK())
            .similarityThreshold(properties.similarityThreshold())
            .filterExpression(scopeFilter)
            .build();

    // Spring AI embeds question through configured Ollama model and calls Qdrant.
    List<Document> matches = vectorStore.similaritySearch(request);
    if (matches == null || matches.isEmpty()) {
        return new MedicalRetrievalResult("", List.of());
    }

    StringBuilder context = new StringBuilder();
    Map<String, ChatSourceResponse> uniqueSources = new LinkedHashMap<>();
    for (Document document : matches) {
        // Hard character cap protects the final Gemini prompt size.
        if (context.length() >= MAX_CONTEXT_CHARS) {
            break;
        }
        Map<String, Object> metadata = document.getMetadata();
        String title = stringValue(metadata.get("title"), "Medical knowledge source");
        String reference = stringValue(metadata.get("reference"), stringValue(metadata.get("sourceKey"), null));
        Double score = document.getScore();
        String text = document.getText();
        if (text != null && !text.isBlank()) {
            int remaining = MAX_CONTEXT_CHARS - context.length();
            context.append("[SOURCE: ").append(title).append("]\n")
                    .append(text, 0, Math.min(text.length(), remaining)).append("\n\n");
        }

        // Multiple chunks from the same file may be in prompt; API returns it as one source.
        String key = reference == null ? title : reference;
        uniqueSources.putIfAbsent(key,
                new ChatSourceResponse(key, title,
                        stringValue(metadata.get("sourceType"), "MEDICAL_DOCUMENT"), reference, score));
    }
    return new MedicalRetrievalResult(context.toString(), new ArrayList<>(uniqueSources.values()));
}

private String scopeFilter(String roleCode, Long userId) {
    // Indexer sets this on every chunk. Unpublished chunks cannot match any role.
    String published = "publicationStatus == 'PUBLISHED' && ";
    if ("ADMIN".equals(roleCode)) {
        return published + "(accessScope == 'ALL' || accessScope == 'ADMIN')";
    }
    if ("HEAD_OF_DEPARTMENT".equals(roleCode) || "DEPARTMENT_HEAD".equals(roleCode)) {
        // Department role does not automatically grant every private report.
        return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                + " || assignedDoctorUserId == " + userId + ")))";
    }
    if ("DOCTOR".equals(roleCode)) {
        return published + "(accessScope == 'ALL' || accessScope == 'DOCTOR' "
                + "|| (accessScope == 'OWNER' && (ownerUserId == " + userId
                + " || assignedDoctorUserId == " + userId + ")))";
    }
    throw new AccessDeniedException("Role is not allowed to search medical knowledge");
}
```

### 18.6 Validation before storage: `MedicalDocumentValidator.validate`

Source: `MedicalDocumentValidator.java:24-57`.

```java
public void validate(byte[] bytes, String originalName, String contentType) {
    // Reader chooses PDFBox, UTF-8 TextReader, or Tika based on type/name.
    List<Document> documents = documentReader.read(bytes, originalName, contentType);

    // Ignore null/non-text artifacts and merge readable pieces into classifier input.
    String content = documents.stream()
            .filter(document -> document != null && document.isText())
            .map(Document::getText)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElseThrow(() -> new IllegalArgumentException("No readable text was found in the document"));

    // Full short files are sent; large files send exactly three representative positions.
    String samples = sample(content, properties.medicalValidationSampleChars());
    MedicalDocumentAssessment assessment = aiChatGateway.assessMedicalDocument(samples);

    // Fail closed: provider failure/null, ambiguity, no confidence, or below threshold rejects source.
    if (assessment == null || !Boolean.TRUE.equals(assessment.medical())
            || assessment.confidence() == null
            || assessment.confidence() < properties.medicalValidationMinConfidence()) {
        String reason = assessment == null || assessment.reason() == null || assessment.reason().isBlank()
                ? "the content is not clearly medical"
                : assessment.reason().trim();
        throw new IllegalArgumentException("Document rejected: " + reason);
    }
}

String sample(String content, int sampleChars) {
    String normalized = content.replace('\u0000', ' ').trim();
    int size = Math.max(1, sampleChars);
    if (normalized.length() <= size * SAMPLE_COUNT) {
        return "[COMPLETE DOCUMENT]\n" + normalized;
    }

    int middleStart = Math.max(0, normalized.length() / 2 - size / 2);
    int endStart = normalized.length() - size;
    return "[BEGINNING SAMPLE]\n" + normalized.substring(0, size)
            + "\n\n[MIDDLE SAMPLE]\n" + normalized.substring(middleStart, middleStart + size)
            + "\n\n[ENDING SAMPLE]\n" + normalized.substring(endStart);
}
```

This method has no repository or filesystem dependency. That design is important:
a rejected document has no knowledge row, no stored source, no Qdrant vector and
no later cleanup obligation.

### 18.7 `KnowledgeIngestionService.upload`: validation, durable source, then event

Source: `KnowledgeIngestionService.java:62-88`.

```java
@Transactional
public KnowledgeDocumentResponse upload(
        MultipartFile file, String title, KnowledgeAccessScope scope, String username) {
    // Cheap request checks happen before loading potentially 50 MiB into memory.
    validateFile(file);
    byte[] bytes = readBytes(file);

    // Exact same bytes must not be represented by two file knowledge sources.
    String checksum = sha256(bytes);
    if (repository.existsBySourceKey("file:" + checksum)) {
        throw new IllegalArgumentException("This document has already been uploaded");
    }

    // Synchronous semantic gate. It throws before any disk or DB side effect on rejection.
    medicalDocumentValidator.validate(bytes, file.getOriginalFilename(), file.getContentType());
    User user = findUser(username);

    // Store under random server file name; never trust client pathname as server path.
    Path storagePath = storeFile(bytes, file.getOriginalFilename());

    KnowledgeDocument document = new KnowledgeDocument();
    document.setSourceKey("file:" + checksum);       // also Qdrant deletion/group key
    document.setTitle(normalizeTitle(title, file.getOriginalFilename()));
    document.setSourceType(KnowledgeSourceType.FILE);
    document.setOriginalName(safeFileName(file.getOriginalFilename()));
    document.setContentType(file.getContentType());
    document.setStoragePath(storagePath.toString());
    document.setChecksum(checksum);
    document.setAccessScope(scope == null ? KnowledgeAccessScope.ALL : scope);
    document.setUploadedBy(user);                     // needed for OWNER retrieval filter
    document = repository.save(document);             // default status is PENDING from entity

    // Worker listens after this transaction commits, so it can find metadata/file.
    requestIndex(document.getId());
    return toResponse(document);
}
```

`addUrl()` is deliberately analogous: it validates HTTP(S)/DNS safety, creates a
URL-derived `sourceKey`, downloads bounded bytes without redirects, validates
medical content, stores `source.html`, writes URL metadata, and emits the same
index event. Therefore file and URL ingestion converge at the worker.

### 18.8 `KnowledgeIndexingWorker.indexExclusively`: database state and Qdrant consistency

Source: `KnowledgeIndexingWorker.java:34-100`.

```java
@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void index(KnowledgeIndexRequestedEvent event) {
    // Serialise index/delete for this document ID within this application process.
    operationCoordinator.executeExclusively(event.documentId(), () -> {
        indexExclusively(event);
        return null;
    });
}

private void indexExclusively(KnowledgeIndexRequestedEvent event) {
    log.info("Starting knowledge indexing for document {}", event.documentId());
    try {
        // New transaction makes PROCESSING visible immediately.
        KnowledgeDocument knowledge = stateService.markProcessing(event.documentId());
        if (knowledge == null) {
            // A delete may finish before queued async job begins.
            log.info("Skipping indexing because knowledge document {} was deleted", event.documentId());
            return;
        }

        // Parse original physical file and replace any parser metadata with access-safe metadata.
        List<Document> parsed = documentReader.read(knowledge);
        List<Document> enriched = parsed.stream()
                .filter(document -> document != null && document.isText())
                .map(document -> new Document(document.getText(), metadata(knowledge)))
                .toList();
        List<Document> chunks = splitter.apply(enriched);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No readable text was found in the document");
        }

        // Reindex is replace-not-append: remove all old chunks for one logical source first.
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("sourceKey", knowledge.getSourceKey()).build());
        // Spring AI embeds every chunk using BGE-M3, then writes point/vector + metadata to Qdrant.
        vectorStore.add(chunks);

        // Deletion can occur between add() and status update. Do not leave orphan Qdrant chunks.
        if (!stateService.markIndexed(event.documentId(), chunks.size())) {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq("sourceKey", knowledge.getSourceKey()).build());
            log.info("Discarded indexed chunks because knowledge document {} was deleted", event.documentId());
            return;
        }
        log.info("Knowledge indexing completed for document {} with {} chunks",
                event.documentId(), chunks.size());
    } catch (RuntimeException | LinkageError exception) {
        // A failed job is observable/retryable through metadata, rather than silently disappearing.
        log.error("Knowledge indexing failed for document {}", event.documentId(), exception);
        stateService.markFailedIfPresent(event.documentId(), truncate(exception.getMessage()));
    }
}

private Map<String, Object> metadata(KnowledgeDocument document) {
    String reference = document.getSourceUrl() == null
            ? "knowledge-document:" + document.getId() : document.getSourceUrl();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("sourceKey", document.getSourceKey());
    metadata.put("knowledgeDocumentId", document.getId());
    metadata.put("title", document.getTitle());
    metadata.put("sourceType", document.getSourceType().name());
    metadata.put("reference", reference);
    metadata.put("accessScope", document.getAccessScope().name());
    metadata.put("publicationStatus", "PUBLISHED");
    if (document.getAccessScope() == KnowledgeAccessScope.OWNER
            && document.getUploadedBy() != null) {
        metadata.put("ownerUserId", document.getUploadedBy().getId());
    }
    return metadata;
}
```

### 18.9 `ReportKnowledgeSyncService.index`: private report vector construction

Source: `ReportKnowledgeSyncService.java:94-140`.

```java
private KnowledgeDocumentResponse index(ReportKnowledge row) {
    String sourceKey = "report:" + row.reportId();

    // Reuse one metadata row per report. Scheduled repair executes this same method.
    KnowledgeDocument knowledge = repository.findBySourceKey(sourceKey)
            .orElseGet(KnowledgeDocument::new);
    knowledge.setSourceKey(sourceKey);
    knowledge.setTitle("Approved clinical report " + row.reportId());
    knowledge.setSourceType(KnowledgeSourceType.REPORT);
    knowledge.setAccessScope(KnowledgeAccessScope.OWNER);
    knowledge.setStatus(KnowledgeDocumentStatus.PROCESSING);
    // Marker lets scheduled sync detect older report metadata format.
    knowledge.setChecksum("report-metadata-v2");
    knowledge.setUploadedBy(userRepository.findById(row.ownerUserId()).orElse(null));
    knowledge = repository.save(knowledge);

    // This is the only report text embedded. It intentionally excludes patient PII and image paths.
    String text = "Approved HealthSync clinical report. Report ID: " + row.reportId()
            + ". Examination ID: " + row.examinationId()
            + ". Study date: " + nullable(row.studyDate())
            + ". Final diagnosis: " + nullable(row.finalDiagnosis())
            + ". Confirmed KL grades: " + nullable(row.confirmedGrades())
            + ". Clinical summary: " + nullable(row.clinicalSummary()) + ".";

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("sourceKey", sourceKey);
    metadata.put("knowledgeDocumentId", knowledge.getId());
    metadata.put("title", knowledge.getTitle());
    metadata.put("sourceType", KnowledgeSourceType.REPORT.name());
    metadata.put("reference", "report:" + row.reportId());
    metadata.put("accessScope", KnowledgeAccessScope.OWNER.name());
    metadata.put("ownerUserId", row.ownerUserId());
    if (row.assignedDoctorUserId() != null) {
        // MedicalRagService permits this doctor to retrieve owner's private report.
        metadata.put("assignedDoctorUserId", row.assignedDoctorUserId());
    }
    metadata.put("publicationStatus", "PUBLISHED");
    List<Document> chunks = splitter.apply(List.of(new Document(text, metadata)));

    try {
        // Same replacement semantics as file/URL indexing.
        vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", sourceKey).build());
        vectorStore.add(chunks);
        knowledge.setChunkCount(chunks.size());
        knowledge.setStatus(KnowledgeDocumentStatus.INDEXED);
        knowledge.setIndexedAt(LocalDateTime.now());
        knowledge.setErrorMessage(null);
    } catch (RuntimeException exception) {
        // Unlike generated-PDF event caller, this method records FAILED before rethrowing.
        knowledge.setStatus(KnowledgeDocumentStatus.FAILED);
        String message = exception.getMessage() == null ? "Report indexing failed" : exception.getMessage();
        knowledge.setErrorMessage(message.length() > 1000 ? message.substring(0, 1000) : message);
        repository.save(knowledge);
        throw exception;
    }
    knowledge = repository.save(knowledge);
    return response(knowledge);
}
```

`syncReport()` performs authorization before this method: admin is always denied;
a doctor must be the report owner or assigned examination doctor. The asynchronous
event listener and five-minute scheduler call the same index method without a
human requester because report generation/reconciliation already defines the
report source of truth.

### 18.10 `KnowledgeIngestionService.addUrl` and `validatePublicUrl`: safe URL intake

Source: `KnowledgeIngestionService.java:90-115`, `213-251`.

```java
@Transactional
public KnowledgeDocumentResponse addUrl(KnowledgeUrlRequest request, String username) {
    // Reject unsupported/private URL before opening an outbound connection.
    URI uri = validatePublicUrl(request.url());

    // URL identity is normalized URL text, unlike file identity which is byte checksum.
    String sourceKey = "url:" + sha256(uri.normalize().toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (repository.existsBySourceKey(sourceKey)) {
        throw new IllegalArgumentException("This URL has already been added");
    }

    // RestClient has redirect disabled and download() enforces byte ceiling/HTTP success.
    byte[] bytes = download(uri);
    // URL content is treated as HTML input and must pass the same medical classifier.
    medicalDocumentValidator.validate(bytes, "source.html", "text/html");
    Path storagePath = storeFile(bytes, "source.html");

    KnowledgeDocument document = new KnowledgeDocument();
    document.setSourceKey(sourceKey);
    document.setTitle(request.title().trim());
    document.setSourceType(KnowledgeSourceType.URL);
    document.setSourceUrl(uri.toString());
    document.setOriginalName("source.html");
    document.setContentType("text/html");
    document.setStoragePath(storagePath.toString());
    document.setChecksum(sha256(bytes));             // content checksum retained for diagnostics
    document.setAccessScope(request.accessScope() == null ? KnowledgeAccessScope.ALL : request.accessScope());
    document.setUploadedBy(findUser(username));
    document = repository.save(document);
    requestIndex(document.getId());
    return toResponse(document);
}

private URI validatePublicUrl(String rawUrl) {
    URI uri = URI.create(rawUrl.trim());

    // A relative URI, non-HTTP protocol, missing host, or user-info form is never acceptable.
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Only absolute HTTP or HTTPS URLs are supported");
    }
    try {
        // Resolve all A/AAAA answers. A single private/local answer rejects request.
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException("Private or local URLs are not allowed");
            }
        }
    } catch (UnknownHostException exception) {
        throw new IllegalArgumentException("URL host could not be resolved", exception);
    }
    return uri;
}
```

The validation happens before `download()`, while redirect rejection happens in
the configured Java HTTP client. Both controls are required: DNS validation
prevents direct private destinations; no redirects prevents a public URL from
redirecting the server to a private destination.

### 18.11 `KnowledgeDocumentReader.read`: one parsing contract for validation and index

Source: `KnowledgeDocumentReader.java:22-57`.

```java
public List<Document> read(KnowledgeDocument knowledge) {
    // Persistent metadata tells reader where source bytes live and what the original type was.
    return read(new FileSystemResource(knowledge.getStoragePath()),
            knowledge.getOriginalName(), knowledge.getContentType());
}

public List<Document> read(byte[] bytes, String originalName, String contentType) {
    // ByteArrayResource normally has no filename; custom resource restores it for Tika detection.
    Resource resource = new NamedByteArrayResource(bytes, originalName);
    return read(resource, originalName, contentType);
}

private List<Document> read(Resource resource, String originalName, String contentType) {
    if (isType(originalName, contentType, "pdf", "application/pdf")) {
        // PDFBox performs text extraction. It does not OCR image-only scanned PDF pages.
        return readPdf(resource);
    }
    if (isType(originalName, contentType, "txt", "text/plain")) {
        // Explicit UTF-8 prevents platform-default encoding differences.
        TextReader reader = new TextReader(resource);
        reader.setCharset(StandardCharsets.UTF_8);
        return reader.get();
    }
    // DOC/DOCX and stored HTML reach Apache Tika through Spring AI reader.
    return new TikaDocumentReader(resource).get();
}

private boolean isType(String originalName, String contentType, String extension, String mediaType) {
    // Declared known media type wins; extension is compatibility fallback.
    if (mediaType.equalsIgnoreCase(contentType)) {
        return true;
    }
    return originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith("." + extension);
}
```

Because validator, `GET content`, and async worker all call this component, a
document cannot pass medical validation using one parser then be indexed with a
different parser implementation.

### 18.12 `KnowledgeDocumentDeletionService.delete`: remove every representation

Source: `KnowledgeDocumentDeletionService.java:27-50`.

```java
@Transactional
public void delete(Long id) {
    // The outer coordinator lock has already serialized delete versus worker for this ID.
    KnowledgeDocument document = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));

    // sourceKey is attached to each chunk, so this deletes the whole logical source in Qdrant.
    vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", document.getSourceKey()).build());

    // Delete metadata after vector deletion; FK/user lifecycle rules are handled by schema.
    repository.delete(document);

    // FILE/URL sources remove original bytes. REPORT has no storage path and becomes a no-op.
    deleteStoredFile(document);
}

private void deleteStoredFile(KnowledgeDocument document) {
    if (document.getStoragePath() == null) {
        return;
    }
    try {
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
        Path storedFile = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
        // Database corruption must not turn API delete into arbitrary server-file deletion.
        if (!storedFile.startsWith(root)) {
            throw new IllegalStateException("Invalid knowledge storage path");
        }
        Files.deleteIfExists(storedFile);
    } catch (IOException exception) {
        throw new IllegalStateException("Could not delete stored knowledge document", exception);
    }
}
```

The three storage removals are intentionally coordinated by one user operation:
Qdrant chunk data, MySQL metadata, and disk bytes. The in-process striped lock
and the worker's post-add deleted-row check protect the normal index/delete race.

## 19. Complete File Coverage Map

This is the explicit checklist for the requested "all related files" scope.
Every production file below is either reproduced in annotated source in section
18/20, or is a declarative record/entity/enum/repository whose exact fields and
generated behavior are annotated in section 21. Supporting runtime files are in
section 22; test files are in section 23.

| Package/file | Where to read the detailed annotation |
| --- | --- |
| `chat/AiChatGateway.java` | 21.1 |
| `chat/BusinessQueryIntent.java` | 21.1 |
| `chat/BusinessQueryResult.java` | 21.1 |
| `chat/ChatRoute.java` | 21.1 |
| `chat/ChatRoutingDecision.java` | 21.1 |
| `chat/GeneratedChatAnswer.java` | 21.1 |
| `chat/KnowledgeIndexRequestedEvent.java` | 21.1 |
| `chat/MedicalDocumentAssessment.java` | 21.1 |
| `chat/MedicalRetrievalResult.java` | 21.1 |
| `chat/ReportKnowledgeSyncRequestedEvent.java` | 21.1 |
| `chat/SpringAiChatGateway.java` | 18.3 and 20.2 |
| `config/ChatAiConfiguration.java` | 20.1 |
| `config/ChatProperties.java` | 21.2 |
| `controller/ChatController.java` | 20.3 |
| `controller/KnowledgeController.java` | 20.4 |
| every `dto/Chat*.java` and `dto/Knowledge*.java` in this list | 21.3 |
| every `entity/Chat*.java` and `entity/Knowledge*.java` in this list | 21.4 |
| `repository/ChatMessageRepository.java` | 21.5 |
| `repository/ChatSessionRepository.java` | 21.5 |
| `repository/KnowledgeDocumentRepository.java` | 21.5 |
| `service/ChatOrchestratorService.java` | 18.1 and 20.5 |
| `service/ChatSessionService.java` | 18.2 and 20.6 |
| `service/BusinessDataQueryService.java` | 18.4 and 20.7 |
| `service/MedicalRagService.java` | 18.5 |
| `service/MedicalDocumentValidator.java` | 18.6 |
| `service/KnowledgeIngestionService.java` | 18.7, 18.10 and 20.8 |
| `service/KnowledgeIndexingWorker.java` | 18.8 |
| `service/KnowledgeIndexStateService.java` | 20.9 |
| `service/KnowledgeDocumentOperationCoordinator.java` | 20.10 |
| `service/KnowledgeBatchIngestionService.java` | 20.11 |
| `service/KnowledgeDocumentDeletionService.java` | 18.12 |
| `service/KnowledgeDocumentReader.java` | 18.11 |
| `service/ReportKnowledgeSyncService.java` | 18.9 and 20.12 |

## 20. Annotated Source: Remaining Runtime Methods

### 20.1 `ChatAiConfiguration`: create the three RAG infrastructure beans

Source: `ChatAiConfiguration.java:15-45`.

```java
@Configuration
@EnableConfigurationProperties(ChatProperties.class)
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ChatAiConfiguration {

    @Bean
    ChatClient healthSyncChatClient(ChatClient.Builder builder) {
        // Builder has already selected provider/model from spring.ai YAML configuration.
        // This bean is injected into SpringAiChatGateway by type.
        return builder.build();
    }

    @Bean
    TokenTextSplitter medicalKnowledgeSplitter() {
        return TokenTextSplitter.builder()
                // Target semantic chunk size passed to the embedding model.
                .withChunkSize(700)
                // Avoid tiny trailing fragments when possible.
                .withMinChunkSizeChars(250)
                // Fragments below this are retained/excluded according to splitter logic, not embedded alone.
                .withMinChunkLengthToEmbed(20)
                // Prevent a malformed source from creating unbounded vector writes.
                .withMaxNumChunks(10_000)
                // Keep paragraph/sentence delimiters to preserve semantic boundaries.
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    RestClient knowledgeRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // A redirect could move a validated public URL to a private address.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return builder.requestFactory(requestFactory).build();
    }
}
```

### 20.2 Remaining `SpringAiChatGateway` public methods

Source: `SpringAiChatGateway.java:59-100`.

```java
@Override
public MedicalDocumentAssessment assessMedicalDocument(String sampledContent) {
    return chatClient.prompt()
            // Strict classifier prompt is different from answer prompt.
            .system(MEDICAL_DOCUMENT_CLASSIFIER_PROMPT)
            // Samples remain user data; prompt explicitly says not to obey source instructions.
            .user("Document samples:\n" + sampledContent)
            .call()
            // Provider JSON is decoded to Boolean/Double/reason record.
            .entity(MedicalDocumentAssessment.class);
}

@Override
public GeneratedChatAnswer answerBusiness(
        String question, String businessContext, String conversationHistory) {
    // Only label is added here; `answer()` handles provider call/empty response/usage.
    return answer(question, "BUSINESS DATA CONTEXT:\n" + businessContext, conversationHistory);
}

@Override
public GeneratedChatAnswer answerMedical(
        String question, String medicalContext, String conversationHistory) {
    return answer(question, "RETRIEVED MEDICAL CONTEXT:\n" + medicalContext, conversationHistory);
}

@Override
public GeneratedChatAnswer answerHybrid(
        String question, String businessContext, String medicalContext, String conversationHistory) {
    // Deliberately preserves provenance instead of blending two source types in Java.
    return answer(question, "BUSINESS DATA CONTEXT:\n" + businessContext
            + "\n\nRETRIEVED MEDICAL CONTEXT:\n" + medicalContext, conversationHistory);
}
```

### 20.3 `ChatController`: HTTP signatures have no hidden business logic

Source: `ChatController.java:42-84`.

```java
@PostMapping("/ask")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<ChatAnswerResponse> ask(
        @Valid @RequestBody ChatQuestionRequest request, Principal principal) {
    // @Valid checks question before service call; Principal username comes from JWT security context.
    return ResponseEntity.ok(chatOrchestratorService.ask(
            request.sessionId(), request.question(), principal.getName()));
}

@PostMapping("/sessions")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<ChatSessionResponse> createSession(
        @Valid @RequestBody CreateChatSessionRequest request, Principal principal) {
    // New resource is the only chat endpoint that returns 201.
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(chatSessionService.create(request, principal.getName()));
}

@GetMapping("/sessions")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<PageResponse<ChatSessionResponse>> getSessions(
        @PageableDefault(size = 20) Pageable pageable, Principal principal) {
    return ResponseEntity.ok(chatSessionService.getSessions(principal.getName(), pageable));
}

@GetMapping("/sessions/{sessionId}/messages")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<PageResponse<ChatMessageResponse>> getMessages(
        @PathVariable Long sessionId, @PageableDefault(size = 50) Pageable pageable, Principal principal) {
    // Service checks ownership before it queries messages.
    return ResponseEntity.ok(chatSessionService.getMessages(sessionId, principal.getName(), pageable));
}

@PatchMapping("/sessions/{sessionId}")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<ChatSessionResponse> updateSession(
        @PathVariable Long sessionId, @Valid @RequestBody UpdateChatSessionRequest request, Principal principal) {
    return ResponseEntity.ok(chatSessionService.update(sessionId, request, principal.getName()));
}
```

### 20.4 `KnowledgeController`: endpoint-to-service mapping

Source: `KnowledgeController.java:53-163`.

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
@LogAction("UPLOAD_MEDICAL_KNOWLEDGE")
public ResponseEntity<KnowledgeDocumentResponse> upload(
        @RequestPart MultipartFile file, @RequestParam(required = false) String title,
        @RequestParam(defaultValue = "ALL") KnowledgeAccessScope accessScope, Principal principal) {
    // Accepted means source metadata is durable; indexing may still be PENDING/FAILED later.
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ingestionService.upload(file, title, accessScope, principal.getName()));
}

@PostMapping("/url")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
@LogAction("ADD_MEDICAL_KNOWLEDGE_URL")
public ResponseEntity<KnowledgeDocumentResponse> addUrl(
        @Valid @RequestBody KnowledgeUrlRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ingestionService.addUrl(request, principal.getName()));
}

@PostMapping("/{id}/reindex")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
@LogAction("REINDEX_MEDICAL_KNOWLEDGE")
public ResponseEntity<KnowledgeDocumentResponse> reindex(@PathVariable Long id) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.reindex(id));
}

@DeleteMapping("/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') and hasAuthority('MANAGE_MEDICAL_KNOWLEDGE')")
@LogAction("DELETE_MEDICAL_KNOWLEDGE")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    ingestionService.delete(id);
    return ResponseEntity.noContent().build();
}
```

The omitted controller methods (`uploadBatch`, list, preview, content, download,
manual report sync, `fileResponse`) contain no routing decision. Their detail is
already recorded at source lines 65-163 in section 5; section 20.8/18.12 covers
the meaningful service and file semantics they invoke.

### 20.5 Remaining `ChatOrchestratorService` helpers

Source: `ChatOrchestratorService.java:59-148`.

```java
private AnswerResult businessAnswer(
        String question, String username, ChatRoutingDecision decision, String history) {
    // The SQL service receives decision only as a typed enum/ID/date carrier.
    BusinessQueryResult result = businessDataQueryService.execute(decision, username);
    // The model receives serialised controlled context, not JdbcTemplate or SQL text generation authority.
    return new AnswerResult(
            aiGateway.answerBusiness(question, result.context(), history), result.sources(), null);
}

private ChatRoutingDecision normalize(ChatRoutingDecision decision) {
    if (decision == null || decision.route() == null) {
        // Router failure-to-structure degrades to a question rather than dereferencing null.
        return new ChatRoutingDecision(ChatRoute.CLARIFICATION, null, null, null, null,
                "Could you clarify whether you need HealthSync data or medical information?");
    }
    return decision;
}

private String clarification(ChatRoutingDecision decision) {
    // Model may omit clarification question. A local text keeps API response always nonblank.
    return decision.clarificationQuestion() == null || decision.clarificationQuestion().isBlank()
            ? "Could you provide more detail about the information you need?"
            : decision.clarificationQuestion();
}

private String contextualRetrievalQuery(String question, String history) {
    if (history == null || history.isBlank()) {
        return question;
    }
    // Retain tail because most recent turns are most relevant and cap Qdrant embedding input.
    int start = Math.max(0, history.length() - 2_000);
    return history.substring(start) + "\nCURRENT QUESTION: " + question;
}

private ChatAnswerResponse response(
        Long sessionId, Long messageId, ChatRoute route, GeneratedChatAnswer answer,
        List<ChatSourceResponse> sources, String warning) {
    return new ChatAnswerResponse(
            sessionId, messageId, route.name(), answer.content(),
            // Prevent later mutable list changes from changing returned response.
            List.copyOf(sources), warning,
            // This is application response time, not model provider's time.
            LocalDateTime.now(), answer.tokensUsed());
}
```

### 20.6 Remaining `ChatSessionService` helpers

Source: `ChatSessionService.java:46-88`, `110-178`, `215-250`.

```java
@Transactional
public ChatSessionResponse update(Long sessionId, UpdateChatSessionRequest request, String username) {
    // JSON `{}` is invalid even though each field is individually optional.
    if (request == null || (request.title() == null && request.active() == null)) {
        throw new IllegalArgumentException("At least one session field must be provided");
    }
    ChatSession session = requireOwnedSession(sessionId, requireUser(username));
    if (request.title() != null) {
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Session title must not be blank");
        }
        session.setTitle(title);
    }
    if (request.active() != null) {
        session.setActive(request.active());
    }
    session.setUpdatedAt(LocalDateTime.now());
    return toSessionResponse(chatSessionRepository.save(session));
}

@Transactional
public ChatMessage saveAssistantMessage(
        ChatSession session, String route, GeneratedChatAnswer answer) {
    // Unlike USER messages, assistant rows retain the route and provider token accounting.
    ChatMessage message = saveMessage(session, ChatMessageRole.ASSISTANT,
            answer.content(), route, answer.tokensUsed());
    touch(session);
    return message;
}

private ChatSession createSession(User user, String title, Long examinationId) {
    ChatSession session = new ChatSession();
    session.setUser(user);
    session.setTitle(normalizeTitle(title));
    session.setActive(true);
    if (examinationId != null) {
        Examination examination = examinationRepository.findById(examinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Examination not found"));
        // Only an assigned doctor or head can attach private clinical examination context.
        authorizeExamination(user, examination);
        session.setExamination(examination);
    }
    return chatSessionRepository.save(session);
}

private void authorizeExamination(User user, Examination examination) {
    String roleCode = user.getRole() == null ? null : user.getRole().getCode();
    boolean supervisor = "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)
            || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode);
    boolean assignedDoctor = examination.getDoctor() != null
            && Objects.equals(examination.getDoctor().getId(), user.getId());
    if (!supervisor && !assignedDoctor) {
        throw new AccessDeniedException("User cannot create a chat session for this examination");
    }
}

private String normalizeTitle(String title) {
    return title == null || title.isBlank() ? DEFAULT_TITLE : truncateTitle(title.trim());
}

private String titleFromQuestion(String question) {
    // Collapse whitespace in title only; saved message preserves user's original internal spacing.
    String normalized = question == null ? DEFAULT_TITLE : question.trim().replaceAll("\\s+", " ");
    return normalized.isEmpty() ? DEFAULT_TITLE : truncateTitle(normalized);
}

private String truncateTitle(String title) {
    // 157 plus `...` honors database/DTO maximum 160 characters.
    return title.length() <= 160 ? title : title.substring(0, 157) + "...";
}
```

```java
private ChatSession createSession(User user, String title, Long examinationId) {
    ChatSession session = new ChatSession();
    session.setUser(user);
    session.setTitle(normalizeTitle(title));
    session.setActive(true);
    if (examinationId != null) {
        Examination examination = examinationRepository.findById(examinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Examination not found"));
        // Chỉ trưởng khoa hoặc bác sĩ được phân ca mới được gắn examination vào chat.
        authorizeExamination(user, examination);
        session.setExamination(examination);
    }
    return chatSessionRepository.save(session);
}

private ChatMessage saveMessage(
        ChatSession session, ChatMessageRole role, String content, String route, Integer tokensUsed) {
    // Một điểm tạo entity duy nhất giúp USER và ASSISTANT luôn có shape persistence nhất quán.
    ChatMessage message = new ChatMessage();
    message.setSession(session);
    message.setRole(role);
    message.setContent(content);
    message.setRoute(route);
    message.setTokensUsed(tokensUsed);
    return chatMessageRepository.save(message);
}

private void touch(ChatSession session) {
    // Cập nhật thứ tự session trong màn hình list. JPA @PreUpdate cũng đặt lại timestamp.
    session.setUpdatedAt(LocalDateTime.now());
    chatSessionRepository.save(session);
}

private User requireUser(String username) {
    return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
}

private ChatSession requireOwnedSession(Long sessionId, User user) {
    // Trả 404 cho cả session không tồn tại và session của người khác để không lộ ID hợp lệ.
    return chatSessionRepository.findByIdAndUserId(sessionId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
}

private void authorizeExamination(User user, Examination examination) {
    String roleCode = user.getRole() == null ? null : user.getRole().getCode();
    boolean supervisor = "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode)
            || "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode);
    boolean assignedDoctor = examination.getDoctor() != null
            && Objects.equals(examination.getDoctor().getId(), user.getId());
    if (!supervisor && !assignedDoctor) {
        throw new AccessDeniedException("User cannot create a chat session for this examination");
    }
}

private String formatHistory(List<ChatMessage> newestFirst) {
    if (newestFirst == null || newestFirst.isEmpty()) {
        return "";
    }
    List<ChatMessage> selected = new ArrayList<>();
    int characters = 0;
    for (ChatMessage message : newestFirst) {
        int messageLength = message.getContent() == null ? 0 : message.getContent().length();
        // Không thêm message cũ hơn nếu nó vượt cap 12.000 ký tự.
        // Message mới nhất luôn được giữ, kể cả khi chỉ riêng nó đã dài hơn cap.
        if (!selected.isEmpty() && characters + messageLength > MAX_HISTORY_CHARACTERS) {
            break;
        }
        selected.add(message);
        characters += messageLength;
    }
    // Database query newest-first nhưng hội thoại gửi model cần oldest-first.
    Collections.reverse(selected);
    StringBuilder history = new StringBuilder();
    for (ChatMessage message : selected) {
        history.append(message.getRole().name())
                .append(": ")
                .append(message.getContent())
                .append('\n');
    }
    return history.toString().trim();
}

private String conversationContext(ChatSession session, List<ChatMessage> messages) {
    String history = formatHistory(messages);
    if (session.getExamination() == null) {
        return history;
    }
    // Đây chỉ là context trong prompt, không tạo record SYSTEM trong chat_messages.
    String examinationContext = "SYSTEM: This conversation is linked to examination ID "
            + session.getExamination().getId() + ".";
    return history.isBlank() ? examinationContext : examinationContext + "\n" + history;
}

private String normalizeTitle(String title) {
    return title == null || title.isBlank() ? DEFAULT_TITLE : truncateTitle(title.trim());
}

private String titleFromQuestion(String question) {
    // Chỉ title bị collapse whitespace; content của USER message vẫn giữ spacing nội bộ ban đầu.
    String normalized = question == null ? DEFAULT_TITLE : question.trim().replaceAll("\\s+", " ");
    return normalized.isEmpty() ? DEFAULT_TITLE : truncateTitle(normalized);
}

private String truncateTitle(String title) {
    // 157 + ba dấu chấm = đúng giới hạn cột/DTO 160 ký tự.
    return title.length() <= 160 ? title : title.substring(0, 157) + "...";
}
```

### 20.VI `SpringAiChatGateway`: mọi lần gọi Gemini (chú thích tiếng Việt)

Nguồn: `src/main/java/com/g93/be/chat/SpringAiChatGateway.java`.

```java
@Override
public MedicalDocumentAssessment assessMedicalDocument(String sampledContent) {
    return chatClient.prompt()
            // Prompt classifier nghiêm ngặt, khác với prompt trả lời user.
            .system(MEDICAL_DOCUMENT_CLASSIFIER_PROMPT)
            // Sample được xem là dữ liệu không tin cậy, không phải system instruction.
            .user("Document samples:\n" + sampledContent)
            .call()
            // Spring AI parse JSON provider thành record có medical/confidence/reason.
            .entity(MedicalDocumentAssessment.class);
}

@Override
public ChatRoutingDecision route(String question, String roleCode, String conversationHistory) {
    return chatClient.prompt()
            // Role được Java format vào system prompt, user không tự đổi được role này.
            .system(ROUTER_PROMPT.formatted(roleCode))
            .user(conversationPrompt(question, conversationHistory))
            .call()
            // Kết quả bắt buộc là object route/intent/id/date có cấu trúc.
            .entity(ChatRoutingDecision.class);
}

@Override
public GeneratedChatAnswer answerBusiness(
        String question, String businessContext, String conversationHistory) {
    // Wrapper chỉ gắn nhãn nguồn; toàn bộ logic gọi provider nằm trong answer().
    return answer(question, "BUSINESS DATA CONTEXT:\n" + businessContext, conversationHistory);
}

@Override
public GeneratedChatAnswer answerMedical(
        String question, String medicalContext, String conversationHistory) {
    return answer(question, "RETRIEVED MEDICAL CONTEXT:\n" + medicalContext, conversationHistory);
}

@Override
public GeneratedChatAnswer answerHybrid(
        String question, String businessContext, String medicalContext, String conversationHistory) {
    // Không trộn mất provenance; Gemini thấy rõ đoạn nào là DB, đoạn nào là evidence RAG.
    return answer(question, "BUSINESS DATA CONTEXT:\n" + businessContext
            + "\n\nRETRIEVED MEDICAL CONTEXT:\n" + medicalContext, conversationHistory);
}

private GeneratedChatAnswer answer(String question, String context, String conversationHistory) {
    ChatResponse response = chatClient.prompt()
            // ANSWER_RULES buộc dùng context được cấp và chống prompt injection từ context/history.
            .system(ANSWER_RULES)
            .user(conversationPrompt(question, conversationHistory) + "\n\n" + context)
            .call()
            // Cần raw ChatResponse để lấy metadata usage.
            .chatResponse();
    if (response == null || response.getResult() == null) {
        return new GeneratedChatAnswer("The AI provider returned an empty response.", null);
    }
    // Không phải provider nào cũng trả usage, nên từng cấp nullable được kiểm tra.
    Integer tokensUsed = response.getMetadata() == null || response.getMetadata().getUsage() == null
            ? null
            : response.getMetadata().getUsage().getTotalTokens();
    String content = response.getResult().getOutput().getText();
    if (content == null || content.isBlank()) {
        content = "The AI provider returned an empty response.";
    }
    return new GeneratedChatAnswer(content, tokensUsed);
}

private String conversationPrompt(String question, String conversationHistory) {
    String history = conversationHistory == null || conversationHistory.isBlank()
            ? "No previous messages."
            : conversationHistory;
    // Delimiter cố định làm rõ history chỉ được dùng để hiểu follow-up, không phải lệnh hệ thống.
    return "Conversation history (for follow-up context only):\n" + history
            + "\n\nCurrent question:\n" + question;
}
```

`create()`, `getSessions()`, and `getMessages()` are thin transactional wrappers:
they call `requireUser`, then an ownership-scoped repository method, then map
the entity to immutable DTO records. `saveMessage()` is the common constructor
for USER/ASSISTANT rows; `touch()` updates the session ordering timestamp.

### 20.7 Remaining business-query methods: clinical detail and date rules

Source: `BusinessDataQueryService.java:76-182`.

```java
private BusinessQueryResult examinationResult(
        MapSqlParameterSource parameters, Long examinationId, boolean scopedDoctor) {
    requireId(examinationId, "examination");
    String sql = "SELECT e.id, e.status, e.final_diagnosis, e.study_date, e.study_time, "
            + "(SELECT GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') "
            + "FROM diagnosis_reviews dr WHERE dr.examination_id = e.id) AS confirmed_kl_grades "
            + "FROM examinations e WHERE e.id = :entityId"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    if (rows.isEmpty()) {
        // Same response intentionally covers absent row and inaccessible doctor-owned row.
        throw new ResourceNotFoundException("Examination not found or not accessible");
    }
    return result(rows.getFirst().toString(), "MySQL examination " + examinationId,
            "database:examinations/" + examinationId);
}

private BusinessQueryResult reportSummary(
        MapSqlParameterSource parameters, Long reportId, boolean scopedDoctor) {
    requireId(reportId, "report");
    String sql = "SELECT r.id, r.created_at, r.clinical_summary, e.id AS examination_id, "
            + "e.status, e.final_diagnosis, "
            + "(SELECT GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') "
            + "FROM diagnosis_reviews dr WHERE dr.examination_id = e.id) AS confirmed_kl_grades "
            + "FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "WHERE r.id = :entityId"
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    if (rows.isEmpty()) {
        throw new ResourceNotFoundException("Report not found or not accessible");
    }
    return result(rows.getFirst().toString(), "MySQL report " + reportId,
            "database:report/" + reportId);
}

private DateRange dateRange(ChatRoutingDecision decision, BusinessQueryIntent intent) {
    boolean noExplicitDate = (decision.dateFrom() == null || decision.dateFrom().isBlank())
            && (decision.dateTo() == null || decision.dateTo().isBlank());
    if (noExplicitDate && intent != BusinessQueryIntent.TODAY_EXAMINATION_COUNT
            && intent != BusinessQueryIntent.TODAY_EXAMINATION_LIST) {
        // Non-today aggregate with no date means full known historical window.
        return new DateRange(LocalDate.of(1970, 1, 1).atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay());
    }
    LocalDate from = parseDate(decision.dateFrom(), LocalDate.now());
    LocalDate to = parseDate(decision.dateTo(), from);
    if (to.isBefore(from)) {
        throw new IllegalArgumentException("dateTo must not be before dateFrom");
    }
    // SQL uses [from, next-day-after-to), avoiding end-of-day nanosecond ambiguity.
    return new DateRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
}

private void requireId(Long id, String type) {
    if (id == null || id < 1) {
        throw new IllegalArgumentException("A valid " + type + " id is required");
    }
}
```

`countExaminations`, `countReports`, and `gradeDistribution` are aggregate
variants of the same safe pattern: constant SQL, named parameters, doctor scope
when applicable, then `result(context, title, reference)`. They never expose a
SQL execution API to Gemini.

### 20.8 Remaining `KnowledgeIngestionService` methods: reindex, read and filesystem safety

Source: `KnowledgeIngestionService.java:136-198`, `200-329`.

```java
@Transactional(readOnly = true)
public KnowledgeDocumentFile getFile(Long id) {
    KnowledgeDocument document = findDocument(id);
    if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
        // REPORT document metadata has no upload file.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
    try {
        // toRealPath resolves links before prefix check, preventing symlink escape from knowledge root.
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize().toRealPath();
        Path path = Path.of(document.getStoragePath()).toAbsolutePath().normalize().toRealPath();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Knowledge document file not found");
        }
        String contentType = document.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = Files.probeContentType(path);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        String fileName = safeFileName(document.getOriginalName());
        if (fileName == null || fileName.isBlank()) {
            fileName = path.getFileName().toString();
        }
        return new KnowledgeDocumentFile(new FileSystemResource(path), fileName, contentType, Files.size(path));
    } catch (IOException exception) {
        // Avoid disclosing path/existence detail to endpoint caller.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
}

@Transactional
public KnowledgeDocumentResponse reindex(Long id) {
    KnowledgeDocument document = findDocument(id);
    document.setStatus(KnowledgeDocumentStatus.PENDING);
    document.setErrorMessage(null);
    repository.save(document);
    // Worker removes old vectors by sourceKey before adding new vectors.
    requestIndex(id);
    return toResponse(document);
}

public void delete(Long id) {
    operationCoordinator.executeExclusively(id, () -> {
        deletionService.delete(id);
        return null;
    });
}

private Path storeFile(byte[] bytes, String originalName) {
    try {
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String extension = extension(originalName);
        Path path = root.resolve(UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension)).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Invalid knowledge storage path");
        }
        Files.write(path, bytes);
        return path;
    } catch (IOException exception) {
        throw new IllegalStateException("Could not store knowledge document", exception);
    }
}
```

### 20.9 `KnowledgeIndexStateService`: short independent state transactions

Source: `KnowledgeIndexStateService.java:21-55`.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public KnowledgeDocument markProcessing(Long documentId) {
    // New transaction means status does not wait for slow parser/Ollama/Qdrant operation.
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) {
        return null;
    }
    document.setStatus(KnowledgeDocumentStatus.PROCESSING);
    document.setErrorMessage(null);
    return repository.save(document);
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean markIndexed(Long documentId, int chunkCount) {
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) {
        // Worker uses false to remove chunks it just added for a deleted source.
        return false;
    }
    document.setChunkCount(chunkCount);
    document.setStatus(KnowledgeDocumentStatus.INDEXED);
    document.setIndexedAt(LocalDateTime.now());
    document.setErrorMessage(null);
    repository.save(document);
    return true;
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void markFailedIfPresent(Long documentId, String errorMessage) {
    KnowledgeDocument document = repository.findById(documentId).orElse(null);
    if (document == null) {
        // Do not recreate a row deleted while the async task was failing.
        return;
    }
    document.setStatus(KnowledgeDocumentStatus.FAILED);
    document.setErrorMessage(errorMessage);
    repository.save(document);
}
```

### 20.10 `KnowledgeDocumentOperationCoordinator`: striped in-process lock

Source: `KnowledgeDocumentOperationCoordinator.java:8-31`.

```java
private static final int LOCK_COUNT = 256;
private final ReentrantLock[] locks = createLocks();

public <T> T executeExclusively(Long documentId, Supplier<T> operation) {
    // floorMod makes negative hash values valid array indexes too.
    ReentrantLock lock = locks[Math.floorMod(Long.hashCode(documentId), LOCK_COUNT)];
    lock.lock();
    try {
        return operation.get();
    } finally {
        // Always release, including vector-store/database exceptions.
        lock.unlock();
    }
}

private ReentrantLock[] createLocks() {
    ReentrantLock[] result = new ReentrantLock[LOCK_COUNT];
    for (int index = 0; index < result.length; index++) {
        result[index] = new ReentrantLock();
    }
    return result;
}
```

This is not a distributed lock. It solves races between async worker and delete
inside one JVM. A horizontally scaled deployment needs a shared lock or another
cross-node concurrency design if simultaneous mutations of one document become
possible across nodes.

### 20.11 `KnowledgeBatchIngestionService.upload`: partial success is intentional

Source: `KnowledgeBatchIngestionService.java:26-56`.

```java
public KnowledgeBatchUploadResponse upload(
        List<MultipartFile> files, KnowledgeAccessScope scope, String username) {
    if (files == null || files.isEmpty()) {
        throw new IllegalArgumentException("At least one document file is required");
    }
    if (files.size() > MAX_BATCH_FILES) {
        throw new IllegalArgumentException("A batch can contain at most " + MAX_BATCH_FILES + " documents");
    }

    List<KnowledgeBatchUploadItemResponse> items = new ArrayList<>(files.size());
    int accepted = 0;
    for (MultipartFile file : files) {
        String originalName = file == null ? null : file.getOriginalFilename();
        try {
            // Reuse the complete single-file path: duplicate, validator, storage and event rules stay identical.
            KnowledgeDocumentResponse document = ingestionService.upload(file, null, scope, username);
            items.add(new KnowledgeBatchUploadItemResponse(originalName, true, document, null));
            accepted++;
        } catch (IllegalArgumentException exception) {
            // Expected invalid/non-medical/duplicate outcome does not cancel next files.
            items.add(new KnowledgeBatchUploadItemResponse(
                    originalName, false, null, exception.getMessage()));
        } catch (RuntimeException exception) {
            // Hide unexpected backend internals from batch response but retain server log.
            log.error("Could not accept knowledge document {} from batch", originalName, exception);
            items.add(new KnowledgeBatchUploadItemResponse(
                    originalName, false, null, "Could not accept document"));
        }
    }
    return new KnowledgeBatchUploadResponse(
            files.size(), accepted, files.size() - accepted, List.copyOf(items));
}
```

### 20.12 Remaining report synchronization entry points

Source: `ReportKnowledgeSyncService.java:48-92`, `143-181`.

```java
@Transactional
public KnowledgeDocumentResponse syncReport(Long reportId, String username) {
    User requester = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    if ("ADMIN".equals(requester.getRole().getCode())) {
        throw new UnauthorizedAccessException("Administrators cannot index clinical reports");
    }
    ReportKnowledge row = loadReport(reportId);
    if ("DOCTOR".equals(requester.getRole().getCode())
            && !requester.getId().equals(row.ownerUserId())
            && !requester.getId().equals(row.assignedDoctorUserId())) {
        throw new UnauthorizedAccessException("You can only index reports from your own examinations");
    }
    // Despite controller response 202, this direct manual call indexes before returning.
    return index(row);
}

@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void syncGeneratedReport(ReportKnowledgeSyncRequestedEvent event) {
    try {
        // PDF event provides only ID; fresh SQL read sees committed final report fields.
        index(loadReport(event.reportId()));
    } catch (RuntimeException exception) {
        // PDF generation remains successful even if vector infrastructure is temporarily unavailable.
        log.error("Could not synchronize generated report {} to Qdrant", event.reportId(), exception);
    }
}

@Scheduled(
        fixedDelayString = "${app.chat.report-sync-delay-ms:300000}",
        initialDelayString = "${app.chat.report-sync-delay-ms:300000}")
public void syncNewReports() {
    String sql = "SELECT r.id FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "LEFT JOIN knowledge_documents k ON k.source_key = CONCAT('report:', r.id) "
            + "WHERE (e.doctor_id IS NOT NULL OR r.operating_doctor_id IS NOT NULL) "
            + "AND (k.id IS NULL OR k.status <> 'INDEXED' "
            + "OR COALESCE(k.checksum, '') <> 'report-metadata-v2') "
            + "ORDER BY r.id LIMIT 100";
    for (Long reportId : jdbcTemplate.queryForList(sql, Map.of(), Long.class)) {
        try {
            index(loadReport(reportId));
        } catch (RuntimeException exception) {
            // One bad report cannot prevent reconciliation of later candidates.
            log.error("Could not synchronize report {} to Qdrant", reportId, exception);
        }
    }
}

private ReportKnowledge loadReport(Long reportId) {
    // Constant aggregate SQL selects only fields approved for clinical report knowledge text.
    String sql = "SELECT r.id AS report_id, e.id AS examination_id, "
            + "COALESCE(r.operating_doctor_id, e.doctor_id) AS owner_user_id, "
            + "e.doctor_id AS assigned_doctor_user_id, e.study_date, "
            + "e.final_diagnosis, r.clinical_summary, "
            + "GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') AS confirmed_grades "
            + "FROM report r JOIN examinations e ON e.id = r.examination_id "
            + "LEFT JOIN diagnosis_reviews dr ON dr.examination_id = e.id WHERE r.id = :reportId "
            + "GROUP BY r.id, e.id, r.operating_doctor_id, e.doctor_id, e.study_date, "
            + "e.final_diagnosis, r.clinical_summary";
    List<ReportKnowledge> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("reportId", reportId),
            (resultSet, rowNum) -> new ReportKnowledge(
                    resultSet.getLong("report_id"), resultSet.getLong("examination_id"),
                    resultSet.getLong("owner_user_id"),
                    resultSet.getObject("assigned_doctor_user_id", Long.class),
                    resultSet.getObject("study_date"), resultSet.getString("final_diagnosis"),
                    resultSet.getString("confirmed_grades"), resultSet.getString("clinical_summary")));
    if (rows.isEmpty() || rows.getFirst().ownerUserId() == 0) {
        throw new ResourceNotFoundException("Approved report not found");
    }
    return rows.getFirst();
}
```

## 21. Annotated Source: Contracts, Entities, and Repositories

These files are intentionally declarative. There is no hidden method body to
explain beyond Java record generation, JPA annotations, Spring Data query-name
parsing, and their fields. The annotated declarations below therefore cover their
complete runtime behavior.

### 21.1 Every small file in `chat/`

```java
public interface AiChatGateway {
    // Synchronous provider classification used before a source can be stored.
    MedicalDocumentAssessment assessMedicalDocument(String sampledContent);

    // Returns structured route/intent/IDs, not a natural-language answer.
    ChatRoutingDecision route(String question, String roleCode, String conversationHistory);

    // Three context-provenance variants, all implemented by SpringAiChatGateway.
    GeneratedChatAnswer answerBusiness(String question, String businessContext, String conversationHistory);
    GeneratedChatAnswer answerMedical(String question, String medicalContext, String conversationHistory);
    GeneratedChatAnswer answerHybrid(
            String question, String businessContext, String medicalContext, String conversationHistory);
}

public enum ChatRoute {
    BUSINESS_DATA,  // fixed MySQL query then natural-language rendering
    MEDICAL_RAG,   // vector evidence then clinical answer
    HYBRID,         // controlled MySQL facts plus vector evidence
    CLARIFICATION   // local follow-up question; no data fetch required
}

public record ChatRoutingDecision(
        ChatRoute route,                 // selects switch branch in ChatOrchestratorService
        BusinessQueryIntent businessIntent, // selects fixed SQL method when business is needed
        Long entityId,                   // report/examination ID, never an arbitrary SQL fragment
        String dateFrom,                 // expected ISO yyyy-MM-dd
        String dateTo,                   // expected ISO yyyy-MM-dd
        String clarificationQuestion) {  // used only for CLARIFICATION
}

public record GeneratedChatAnswer(
        String content,      // assistant text stored in chat_messages.content
        Integer tokensUsed) {// nullable provider total token count
}

public record MedicalDocumentAssessment(
        Boolean medical,    // must be Boolean.TRUE to accept a source
        Double confidence,  // must meet configured threshold
        String reason) {    // returned in rejection message when available
}

public record BusinessQueryResult(
        String context,                 // backend-created text provided to answer model
        List<ChatSourceResponse> sources) { }

public record MedicalRetrievalResult(
        String context,                 // concatenated, role-filtered Qdrant chunk text
        List<ChatSourceResponse> sources) {
    public boolean isEmpty() {
        // Orchestrator uses sources, not just blank text, to decide evidence availability.
        return sources == null || sources.isEmpty();
    }
}

public record KnowledgeIndexRequestedEvent(Long documentId) { }
// Carries database ID only; worker reloads current state rather than receiving stale entity.

public record ReportKnowledgeSyncRequestedEvent(Long reportId) { }
// Same event pattern for generated/returned report PDFs.
```

`BusinessQueryIntent` has eight enum constants: two today operations, general
examination/report counts, final examination result, report summary, grade
distribution and `UNKNOWN`. The final value is deliberately an error route in
`BusinessDataQueryService`, not a wildcard.

### 21.2 `ChatProperties`: the complete property contract

```java
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        boolean enabled,                    // feeds every @ConditionalOnProperty check
        String knowledgeDir,                // filesystem root for FILE/URL original bytes
        long maxDocumentBytes,              // multipart source ceiling
        long maxUrlBytes,                   // remote response ceiling
        int retrievalTopK,                  // maximum vector matches requested
        double similarityThreshold,         // Qdrant relevance cutoff
        int medicalValidationSampleChars,   // chars per begin/middle/end sample
        double medicalValidationMinConfidence, // classifier acceptance threshold
        long reportSyncDelayMs) { }         // scheduler delay in milliseconds
```

The type is immutable. Changing configuration later does not mutate injected
instances; the application must be restarted/rebound according to Spring Boot
configuration lifecycle.

### 21.3 Every RAG request/response DTO

```java
public record ChatQuestionRequest(
        Long sessionId, // null means auto-create; non-null means owned session required
        @NotBlank(message = "Question is required")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question) { }

public record CreateChatSessionRequest(
        @Size(max = 160, message = "Title must not exceed 160 characters") String title,
        Long examinationId) { } // service validates this clinical link's ownership

public record UpdateChatSessionRequest(
        @Size(max = 160, message = "Title must not exceed 160 characters") String title,
        Boolean active) { } // null means leave that field unchanged

public record ChatAnswerResponse(
        Long sessionId, Long messageId, String route, String answer,
        List<ChatSourceResponse> sources, String warning,
        LocalDateTime generatedAt, Integer tokensUsed) { }

public record ChatSourceResponse(
        String sourceId, // source key/reference; one logical source can own multiple chunks
        String title,    // display label from metadata/database result
        String sourceType, // FILE, URL, REPORT, or BUSINESS_DATA
        String locator,  // Qdrant reference or database URI-like locator
        Double score) { } // similarity score; null for MySQL sources

public record ChatSessionResponse(
        Long id, Long examinationId, String title, boolean active,
        LocalDateTime createdAt, LocalDateTime updatedAt) { }

public record ChatMessageResponse(
        Long id, Long sessionId, String role, String content, String route,
        Integer tokensUsed, LocalDateTime createdAt) { }

public record KnowledgeUrlRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters") String title,
        @NotBlank(message = "URL is required")
        @Size(max = 2048, message = "URL must not exceed 2048 characters") String url,
        KnowledgeAccessScope accessScope) { }

public record KnowledgeDocumentResponse(
        Long id, String title, String sourceType, String sourceUrl, String originalName,
        String contentUrl, String previewUrl, String downloadUrl,
        String accessScope, String status, Integer chunkCount, String errorMessage,
        LocalDateTime createdAt, LocalDateTime indexedAt) { }

public record KnowledgeBatchUploadItemResponse(
        String originalName, boolean accepted, KnowledgeDocumentResponse document, String error) { }

public record KnowledgeBatchUploadResponse(
        int submittedCount, int acceptedCount, int rejectedCount,
        List<KnowledgeBatchUploadItemResponse> items) { }
```

All these types are immutable transport data. Validation annotations only execute
when the controller parameter has `@Valid`; this is present for JSON request DTOs,
while multipart file constraints are enforced manually in `validateFile()`.

### 21.4 Chat/knowledge enums and JPA entities

```java
public enum ChatMessageRole { USER, ASSISTANT, SYSTEM }
// USER and ASSISTANT are persisted today. SYSTEM currently appears only in prompt history string.

public enum KnowledgeAccessScope { ALL, DOCTOR, ADMIN, OWNER }
// Values are copied literally into Qdrant metadata/filter expressions.

public enum KnowledgeDocumentStatus { PENDING, PROCESSING, INDEXED, FAILED }
// Async indexing state machine visible through metadata listing.

public enum KnowledgeSourceType { FILE, URL, REPORT }
// REPORT means relationally generated text; therefore no original uploaded file is guaranteed.
```

```java
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // database-generated primary key

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // required owner; repository queries always scope by this ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id")
    private Examination examination; // optional clinical conversation link

    @Column(name = "title", nullable = false, length = 160)
    private String title;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session; // cascade deletion is defined in SQL migration FK
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ChatMessageRole role;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "route", length = 30)
    private String route; // null for USER, route enum name for ASSISTANT
    @Column(name = "tokens_used")
    private Integer tokensUsed;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
```

`KnowledgeDocument` has the same JPA pattern but represents the bridge between
disk and Qdrant: `sourceKey` is unique and groups vector chunks; `storagePath`
and checksum remain internal; `uploadedBy` enables OWNER rules; `chunkCount`,
status/error/index time expose async progress. Its `@PrePersist` defaults null
status/scope to `PENDING`/`ALL`; `@PreUpdate` updates `updatedAt`.

For completeness, the full field layout of `KnowledgeDocument` is:

```java
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_key", nullable = false, unique = true, length = 160)
    private String sourceKey; // shared Qdrant filter/deletion identity
    @Column(name = "title", nullable = false, length = 255)
    private String title; // safe display/prompt source label
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private KnowledgeSourceType sourceType;
    @Column(name = "source_url", length = 2048)
    private String sourceUrl; // original public URL; null for FILE/REPORT
    @Column(name = "original_name", length = 255)
    private String originalName; // basename only, never trusted storage path
    @Column(name = "content_type", length = 150)
    private String contentType;
    @Column(name = "storage_path", length = 512)
    private String storagePath; // server-only physical path; null for REPORT
    @Column(name = "checksum", length = 64)
    private String checksum; // SHA-256 for file bytes or report metadata-version marker
    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 20)
    private KnowledgeAccessScope accessScope = KnowledgeAccessScope.ALL;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KnowledgeDocumentStatus status = KnowledgeDocumentStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy; // OWNER metadata source when file/URL is private
    @Column(name = "chunk_count")
    private Integer chunkCount;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    @PrePersist void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = KnowledgeDocumentStatus.PENDING;
        if (accessScope == null) accessScope = KnowledgeAccessScope.ALL;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

### 21.5 Every RAG Spring Data repository method

```java
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    // Spring Data generates WHERE id = ? AND user_id = ?, the session ownership boundary.
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);
    // Page results ordered newest activity first, ID breaks equal timestamp ties.
    Page<ChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);
}

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Paginated API history is chronological.
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId, Pageable pageable);
    // Prompt history gets newest 20 first so Java can trim most relevant messages by character limit.
    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceKey(String sourceKey);
    boolean existsBySourceKey(String sourceKey); // exact duplicate file/URL identity guard
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT document FROM KnowledgeDocument document
            WHERE (:keyword IS NULL
                    OR LOWER(document.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(COALESCE(document.originalName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:sourceType IS NULL OR document.sourceType = :sourceType)
              AND (:status IS NULL OR document.status = :status)
              AND (:accessScope IS NULL OR document.accessScope = :accessScope)
            """)
    Page<KnowledgeDocument> search(
            String keyword, KnowledgeSourceType sourceType, KnowledgeDocumentStatus status,
            KnowledgeAccessScope accessScope, Pageable pageable);
}
```

## 22. Supporting Files That Control RAG Runtime

### 22.1 JWT and method security

`JwtAuthenticationFilter` and `SecurityConfig` are not in the RAG package but
are essential. Their complete RAG-relevant behavior is this sequence:

```java
// JwtAuthenticationFilter.doFilterInternal, simplified only to remove unrelated imports.
String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response); // controller will be blocked later if protected
    return;
}
String jwt = authHeader.substring(7);
if (!tokenBlacklistService.isAccessTokenBlacklisted(jwt)
        && jwtTokenProvider.isAccessTokenValid(jwt)) {
    String username = jwtTokenProvider.extractUsernameFromAccessToken(jwt);
    List<String> permissions = jwtTokenProvider.extractPermissionsFromAccessToken(jwt);
    String role = jwtTokenProvider.extractRoleFromAccessToken(jwt);
    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
    if (role != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    if (permissions != null) permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
    SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, authorities));
}
filterChain.doFilter(request, response);
```

Every chat/knowledge controller method then applies `@PreAuthorize`. This means
the JWT carries the initial authorization decision, while services reload User
from MySQL for ownership and scope-dependent decisions.

### 22.2 Application start, async and YAML

```java
@SpringBootApplication
@EnableScheduling // activates ReportKnowledgeSyncService.syncNewReports()
public class BeApplication { ... }

@Configuration
@EnableAsync // activates @Async worker/report event listener methods
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); // indexing is serial in this deployment
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("healthsync-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

The matching YAML controls provider selection and data boundaries:

```yaml
spring.ai.model.chat: ${CHAT_MODEL_PROVIDER:none}       # set google-genai to enable chat model
spring.ai.model.embedding: ${CHAT_EMBEDDING_PROVIDER:none} # set ollama for BGE-M3
spring.ai.vectorstore.type: ${CHAT_VECTOR_STORE:none}   # set qdrant for VectorStore
spring.ai.google.genai.chat.model: ${GEMINI_CHAT_MODEL:gemini-3.5-flash}
spring.ai.ollama.embedding.model: bge-m3
app.chat.enabled: ${CHAT_AI_ENABLED:false}              # gates RAG bean/controller creation
app.chat.retrieval-top-k: ${CHAT_RETRIEVAL_TOP_K:12}
app.chat.similarity-threshold: ${CHAT_SIMILARITY_THRESHOLD:0.4}
```

### 22.3 `chatbox_rag_migration.sql`: database contract

The migration creates exactly three RAG tables: `knowledge_documents`,
`chat_sessions`, and `chat_messages`. The key operational clauses are:

```sql
UNIQUE KEY uk_knowledge_documents_source_key (source_key);
-- prevents two metadata rows from representing one logical vector source.

CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
-- deleting a user removes their sessions; chat_messages then cascade through session FK.

CONSTRAINT fk_chat_sessions_examination FOREIGN KEY (examination_id)
    REFERENCES examinations(id) ON DELETE SET NULL;
-- session can survive removal of a linked examination.

KEY idx_chat_messages_session_created (session_id, created_at, id);
-- matches chronological history and Top20 newest-first queries.
```

It also idempotently seeds feature/permissions `USE_AI_CHAT` and
`MANAGE_MEDICAL_KNOWLEDGE`, then grants them to configured clinical/admin roles.

## 23. All Related Test Files

The following test files are part of the feature scope. They contain no separate
runtime path; each asserts the production behavior annotated above.

| Test file | Test-level purpose and production target |
| --- | --- |
| `controller/ChatControllerRbacTest.java` | Verifies `/chat` methods require the role plus `USE_AI_CHAT` authority before service invocation. |
| `controller/KnowledgeControllerAuthorizationTest.java` | Verifies knowledge endpoint authority and role combinations. |
| `service/ChatOrchestratorServiceTest.java` | Mocks gateway/services to prove route dispatch, missing-evidence behavior, hybrid retrieval query and assistant response mapping. |
| `service/ChatSessionServiceTest.java` | Proves session ownership, examination ownership, title creation, inactive rejection and historical ordering. |
| `service/BusinessDataQueryServiceTest.java` | Captures SQL to prove doctor predicate, ten-row list limit and admin clinical denial. |
| `service/MedicalRagServiceTest.java` | Captures `SearchRequest` to prove configured top-K and Qdrant owner/assigned-doctor filter. |
| `service/MedicalDocumentValidatorTest.java` | Verifies complete-vs-three-sample behavior and fail-closed classifier threshold. |
| `service/KnowledgeIngestionServiceTest.java` | Verifies duplicate/validation side effects, storage/read safety, response mapping and event publication. |
| `service/KnowledgeBatchIngestionServiceTest.java` | Verifies per-file isolation and maximum ten-file rule. |
| `service/KnowledgeIndexingWorkerTest.java` | Verifies UTF-8 reader behavior used before splitting/indexing. |
| `service/KnowledgeDocumentDeletionServiceTest.java` | Verifies source-key vector deletion plus physical-path safety. |
| `service/ReportKnowledgeSyncServiceTest.java` | Verifies scheduled-repair SQL and owner/assigned-doctor metadata added to report chunks. |

When adding a behavior, find the production method in sections 18-21 first,
then add/update its matching test file in this table. That keeps the document,
source behavior and automated proof aligned.

## 24. Mã Nguồn Chú Thích Tiếng Việt: Orchestrator và Session

Phần này là bản để đọc chính khi muốn hiểu code. Mỗi block giữ nguyên cấu trúc
và lời gọi của source Java, nhưng các comment được viết bằng tiếng Việt. Các tên
class, field, method, prompt và SQL không dịch để đối chiếu được từng ký tự với
file `.java` trong IDE.

### 24.1 `ChatOrchestratorService`: toàn bộ đường đi của một câu hỏi

Nguồn: `src/main/java/com/g93/be/service/ChatOrchestratorService.java`.

```java
public ChatAnswerResponse ask(Long sessionId, String question, String username) {
    // B1: Kiểm tra/tạo session, lấy lịch sử CŨ và lưu message USER hiện tại.
    // `history` trả về chưa có `question`; câu mới sẽ được gắn riêng vào prompt.
    ChatSessionService.PreparedConversation conversation =
            chatSessionService.prepare(sessionId, question, username);

    // B2: Không lấy role/userId từ model. Lấy từ User đã xác thực trong MySQL.
    String roleCode = conversation.user().getRole().getCode();
    String history = conversation.history();

    // B3: Gemini chỉ được phân loại request thành object có cấu trúc.
    // normalize() biến kết quả null/thiếu route thành CLARIFICATION an toàn.
    ChatRoutingDecision decision = normalize(aiGateway.route(question, roleCode, history));

    // B4: Java quyết định code path thật sự. Model không gọi trực tiếp service nào.
    AnswerResult result = switch (decision.route()) {
        case CLARIFICATION ->
                // Không có DB/Qdrant/answer model. Trả câu yêu cầu làm rõ.
                new AnswerResult(new GeneratedChatAnswer(clarification(decision), null), List.of(), null);

        case BUSINESS_DATA ->
                // Chạy SQL whitelist trước, sau đó chỉ dùng Gemini để diễn đạt dữ liệu.
                businessAnswer(question, username, decision, history);

        case MEDICAL_RAG ->
                // Truy hồi vector theo role/userId rồi mới cho phép sinh câu trả lời.
                medicalAnswer(question, roleCode, conversation.user().getId(), history);

        case HYBRID ->
                // Lấy business data, dùng nó làm rõ retrieval, rồi tổng hợp hai context.
                hybridAnswer(question, username, roleCode, conversation.user().getId(), decision, history);
    };

    // B5: Chỉ khi mọi xử lý trên thành công mới lưu ASSISTANT message.
    // Nếu Gemini/Qdrant lỗi trước đây, USER message vẫn tồn tại nhưng chưa có answer.
    ChatMessage savedMessage = chatSessionService.saveAssistantMessage(
            conversation.session(), decision.route().name(), result.answer());

    // B6: sources/warning chỉ nằm trong response, không được persist trong ChatMessage.
    return response(
            conversation.session().getId(),
            savedMessage.getId(),
            decision.route(),
            result.answer(),
            result.sources(),
            result.warning());
}

private AnswerResult businessAnswer(
        String question, String username, ChatRoutingDecision decision, String history) {
    // execute() chỉ nhận enum intent/ID/date đã parse, không nhận SQL do Gemini viết.
    BusinessQueryResult result = businessDataQueryService.execute(decision, username);

    // `result.context()` là chuỗi dữ liệu MySQL do backend tạo và kiểm soát.
    return new AnswerResult(
            aiGateway.answerBusiness(question, result.context(), history),
            result.sources(),
            null); // Business thuần túy không gắn cảnh báo y khoa.
}

private AnswerResult medicalAnswer(String question, String roleCode, Long userId, String history) {
    // Câu query vector có thêm tối đa 2.000 ký tự cuối history để hỗ trợ follow-up.
    MedicalRetrievalResult result = medicalRagService.retrieve(
            contextualRetrievalQuery(question, history), roleCode, userId);

    if (result.isEmpty()) {
        // Không có evidence được phép truy cập => backend tự trả lời, không để Gemini đoán.
        return new AnswerResult(new GeneratedChatAnswer(
                "I could not find sufficient approved medical evidence in the knowledge base.", null),
                List.of(), MEDICAL_WARNING);
    }

    // Chỉ các chunk đã vượt threshold và filter quyền mới được gửi sang Gemini.
    return new AnswerResult(
            aiGateway.answerMedical(question, result.context(), history),
            result.sources(),
            MEDICAL_WARNING);
}

private AnswerResult hybridAnswer(
        String question,
        String username,
        String roleCode,
        Long userId,
        ChatRoutingDecision decision,
        String history) {
    // Lấy sự thật nghiệp vụ từ MySQL trước, ví dụ final diagnosis của report.
    BusinessQueryResult business = businessDataQueryService.execute(decision, username);

    // Chèn dữ liệu đó vào retrieval query để guideline tìm được sát report hơn.
    MedicalRetrievalResult medical = medicalRagService.retrieve(
            contextualRetrievalQuery(question + "\nHEALTHSYNC DATA:\n" + business.context(), history),
            roleCode, userId);

    if (medical.isEmpty()) {
        // Vẫn trả được dữ liệu business chính xác, nhưng có warning vì user yêu cầu diễn giải y khoa.
        return new AnswerResult(
                aiGateway.answerBusiness(question, business.context(), history),
                business.sources(),
                MEDICAL_WARNING);
    }

    // Duy trì thứ tự nguồn: database trước, bằng chứng y khoa sau.
    List<ChatSourceResponse> sources = new ArrayList<>(business.sources());
    sources.addAll(medical.sources());
    return new AnswerResult(
            aiGateway.answerHybrid(question, business.context(), medical.context(), history),
            sources,
            MEDICAL_WARNING);
}

private ChatRoutingDecision normalize(ChatRoutingDecision decision) {
    if (decision == null || decision.route() == null) {
        // Provider trả null/JSON không đủ cấu trúc không được làm request lỗi NullPointerException.
        return new ChatRoutingDecision(
                ChatRoute.CLARIFICATION, null, null, null, null,
                "Could you clarify whether you need HealthSync data or medical information?");
    }
    return decision;
}

private String clarification(ChatRoutingDecision decision) {
    // Nếu router không cấp câu hỏi làm rõ, dùng fallback cục bộ luôn có nội dung.
    return decision.clarificationQuestion() == null || decision.clarificationQuestion().isBlank()
            ? "Could you provide more detail about the information you need?"
            : decision.clarificationQuestion();
}

private String contextualRetrievalQuery(String question, String history) {
    if (history == null || history.isBlank()) {
        return question;
    }
    // Giữ đuôi history vì các lượt gần nhất thường quyết định nghĩa của follow-up.
    int start = Math.max(0, history.length() - 2_000);
    return history.substring(start) + "\nCURRENT QUESTION: " + question;
}

private ChatAnswerResponse response(
        Long sessionId,
        Long messageId,
        ChatRoute route,
        GeneratedChatAnswer answer,
        List<ChatSourceResponse> sources,
        String warning) {
    return new ChatAnswerResponse(
            sessionId,
            messageId,
            route.name(),
            answer.content(),
            // Không để caller có thể sửa collection nguồn sau khi response được tạo.
            List.copyOf(sources),
            warning,
            // Đây là thời điểm backend tạo response, không phải thời điểm Gemini xử lý xong.
            LocalDateTime.now(),
            answer.tokensUsed());
}
```

### 24.2 `ChatSessionService`: session, ownership, history và message

Nguồn: `src/main/java/com/g93/be/service/ChatSessionService.java`.

```java
@Transactional
public ChatSessionResponse create(CreateChatSessionRequest request, String username) {
    // Bắt buộc username trong JWT phải map được tới User còn tồn tại.
    User user = requireUser(username);
    ChatSession session = createSession(
            user,
            request == null ? null : request.title(),
            request == null ? null : request.examinationId());
    return toSessionResponse(session);
}

@Transactional(readOnly = true)
public PageResponse<ChatSessionResponse> getSessions(String username, Pageable pageable) {
    User user = requireUser(username);
    // Không có repository method nào list toàn bộ session cho một user client.
    return PageResponse.of(chatSessionRepository
            .findByUserIdOrderByUpdatedAtDescIdDesc(user.getId(), pageable)
            .map(this::toSessionResponse));
}

@Transactional(readOnly = true)
public PageResponse<ChatMessageResponse> getMessages(Long sessionId, String username, Pageable pageable) {
    // Check ownership trước rồi mới query message, tránh lộ lịch sử của user khác.
    ChatSession session = requireOwnedSession(sessionId, requireUser(username));
    Page<ChatMessageResponse> messages = chatMessageRepository
            .findBySessionIdOrderByCreatedAtAscIdAsc(session.getId(), pageable)
            .map(this::toMessageResponse);
    return PageResponse.of(messages);
}

@Transactional
public ChatSessionResponse update(Long sessionId, UpdateChatSessionRequest request, String username) {
    // PATCH không được phép không thay đổi field nào cả.
    if (request == null || (request.title() == null && request.active() == null)) {
        throw new IllegalArgumentException("At least one session field must be provided");
    }
    ChatSession session = requireOwnedSession(sessionId, requireUser(username));
    if (request.title() != null) {
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Session title must not be blank");
        }
        session.setTitle(title);
    }
    if (request.active() != null) {
        // false là giá trị hợp lệ: đóng session để không hỏi tiếp được.
        session.setActive(request.active());
    }
    session.setUpdatedAt(LocalDateTime.now());
    return toSessionResponse(chatSessionRepository.save(session));
}

@Transactional
public PreparedConversation prepare(Long sessionId, String question, String username) {
    User user = requireUser(username);
    ChatSession session = sessionId == null
            // Lần hỏi đầu tự tạo session, title lấy từ question đã chuẩn hóa whitespace.
            ? createSession(user, titleFromQuestion(question), null)
            // Session đã có phải thuộc user hiện tại.
            : requireOwnedSession(sessionId, user);
    if (!session.isActive()) {
        throw new IllegalArgumentException("Chat session is inactive");
    }

    // Repository trả newest-first. Hàm dưới biến thành history cũ-to-mới.
    String history = conversationContext(session, chatMessageRepository
            .findTop20BySessionIdOrderByCreatedAtDescIdDesc(session.getId()));

    // Lưu sau khi build history để current question không xuất hiện hai lần trong prompt.
    saveMessage(session, ChatMessageRole.USER, question.trim(), null, null);
    if (DEFAULT_TITLE.equals(session.getTitle())) {
        session.setTitle(titleFromQuestion(question));
    }
    touch(session);
    return new PreparedConversation(session, user, history);
}

@Transactional
public ChatMessage saveAssistantMessage(
        ChatSession session, String route, GeneratedChatAnswer answer) {
    // Chỉ ASSISTANT message có route và số token từ provider.
    ChatMessage message = saveMessage(
            session, ChatMessageRole.ASSISTANT, answer.content(), route, answer.tokensUsed());
    touch(session);
    return message;
}
```
