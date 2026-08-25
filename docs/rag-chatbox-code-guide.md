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

### 0.10 Mục Lục Hành Động: Frontend Bấm Gì → Backend Chạy Chuỗi Hàm Nào

Repo này (`mkaix.healthsyncbe`) chỉ chứa source **backend**, không có source
frontend. Vì vậy "hành động frontend" dưới đây được mô tả ở mức hợp đồng API:
đây là danh sách đầy đủ mọi request mà bất kỳ client nào (web, mobile, Postman,
Bruno collection trong `bruno/`...) phải gọi để tạo ra trải nghiệm RAG chatbox.
Với mỗi request, tài liệu liệt kê chính xác chuỗi hàm Java chạy theo thứ tự,
kèm `file.java:dòng` để mở đúng vị trí trong IDE.

**Một lớp áp dụng cho toàn bộ 15 hành động đầu tiên, không lặp lại ở từng mục:**
mọi HTTP request trước tiên đi qua `JwtAuthenticationFilter`
(`security/JwtAuthenticationFilter.java`, xem mục 2). Thiếu header
`Authorization: Bearer <accessToken>`, token hết hạn, token nằm trong blacklist,
hoặc chữ ký sai sẽ khiến request bị `SecurityConfig` chặn với `401` **trước khi**
controller được gọi — không một dòng code RAG nào (Gemini, Qdrant, MySQL) chạy.
Nếu token hợp lệ, Spring Security AOP tiếp tục đánh giá `@PreAuthorize` được khai
báo trên từng method; sai role hoặc thiếu permission trả về `403` cũng **trước
khi** vào thân method controller.

| # | Hành động người dùng bấm trên UI | HTTP | Quyền bắt buộc (`@PreAuthorize`) |
| --- | --- | --- | --- |
| 1 | Mở "Đoạn chat mới" (trống, hoặc gắn với 1 ca khám) | `POST /chat/sessions` | role lâm sàng + `USE_AI_CHAT` |
| 2 | Sidebar hiển thị danh sách các đoạn chat | `GET /chat/sessions` | role lâm sàng + `USE_AI_CHAT` |
| 3 | Bấm vào 1 đoạn chat cũ để tải lịch sử | `GET /chat/sessions/{id}/messages` | role lâm sàng + `USE_AI_CHAT` |
| 4 | Gõ câu hỏi, bấm nút "Gửi" | `POST /chat/ask` | role lâm sàng + `USE_AI_CHAT` |
| 5 | Đổi tên / đóng / mở lại một đoạn chat | `PATCH /chat/sessions/{id}` | role lâm sàng + `USE_AI_CHAT` |
| 6 | Upload 1 tài liệu y khoa vào kho tri thức | `POST /knowledge-documents/upload` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 7 | Upload nhiều tài liệu cùng lúc (kéo-thả) | `POST /knowledge-documents/upload/batch` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 8 | Thêm nguồn tri thức từ URL | `POST /knowledge-documents/url` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 9 | Trang quản lý tài liệu: liệt kê / lọc / tìm kiếm / phân trang | `GET /knowledge-documents` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 10 | Bấm icon "xem trước" (nhúng trong iframe) | `GET /knowledge-documents/{id}/preview` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 11 | Bấm "xem nội dung text" đã trích xuất | `GET /knowledge-documents/{id}/content` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 12 | Bấm icon "tải xuống" file gốc | `GET /knowledge-documents/{id}/download` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 13 | Bấm "Index lại" trên tài liệu `FAILED`/`PENDING` | `POST /knowledge-documents/{id}/reindex` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 14 | Bấm "Xóa" một tài liệu | `DELETE /knowledge-documents/{id}` | role quản lý + `MANAGE_MEDICAL_KNOWLEDGE` |
| 15 | Bác sĩ bấm "Đồng bộ report này vào AI" thủ công | `POST /knowledge-documents/reports/{id}/sync` | `DOCTOR`/`DEPARTMENT_HEAD`/`HEAD_OF_DEPARTMENT` + `USE_AI_CHAT` (**không** cần `MANAGE_MEDICAL_KNOWLEDGE`, **không** cho `ADMIN`) |
| 16 | Bấm "Tạo báo cáo PDF" trên trang khám (thuộc module khám bệnh, không thuộc `/chat` hay `/knowledge-documents`) | `POST /examinations/{id}/report` (module khác) | tự động kích hoạt RAG, xem mục 0.10.16 |
| — | Worker lập chỉ mục chạy nền | không phải HTTP, chạy sau khi transaction commit | không áp dụng |
| — | Job quét định kỳ mỗi 5 phút | không phải HTTP, `@Scheduled` | không áp dụng |

Vai trò "role lâm sàng" ở trên là literal SpEL
`hasAnyRole('ADMIN', 'DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')`
(`ChatController.java:39-40`); "role quản lý" là cùng bốn role đó nhưng đổi
permission thành `MANAGE_MEDICAL_KNOWLEDGE` (khai báo lặp lại trên từng
`@PreAuthorize` của `KnowledgeController.java`, không dùng hằng số chung).

---

#### 0.10.1 Tạo đoạn chat mới (trống, hoặc gắn với 1 ca khám)

**FE làm gì**: người dùng bấm nút "Đoạn chat mới" trên sidebar chatbox. Nếu bấm
từ trang chi tiết một ca khám ("Hỏi AI về ca này"), FE gửi kèm `examinationId`.

```
POST /chat/sessions
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "title": null, "examinationId": 482 }
```

Chuỗi gọi:

1. `ChatController.createSession()` (`ChatController.java:51-58`) nhận
   `CreateChatSessionRequest`. `@Valid` kiểm tra `title` (nếu có) tối đa 160 ký
   tự (`CreateChatSessionRequest.java:5-9`); không kiểm tra ownership ở DTO.
2. Gọi `ChatSessionService.create(request, principal.getName())`
   (`ChatSessionService.java:46-52`), chạy trong 1 `@Transactional`.
3. `requireUser(username)` (`ChatSessionService.java:159-162`) tra
   `UserRepository.findByUsername`; không có user → `404 ResourceNotFoundException`.
4. `createSession(user, title, examinationId)` (`ChatSessionService.java:125-137`):
   - Tạo entity `ChatSession` mới, gán `user`, `active=true`.
   - `normalizeTitle()` (`ChatSessionService.java:215-217`): title rỗng →
     hằng số `"New conversation"` (`DEFAULT_TITLE`, dòng 38).
   - Nếu `examinationId != null`: `examinationRepository.findById()`, không có
     → `404`; sau đó `authorizeExamination(user, examination)`
     (`ChatSessionService.java:169-178`) — `HEAD_OF_DEPARTMENT`/`DEPARTMENT_HEAD`
     được gắn bất kỳ ca khám nào, các role khác **phải** là
     `examination.doctor.id == user.id`, sai thì `403 AccessDeniedException`.
   - `chatSessionRepository.save(session)` ghi 1 row mới vào bảng
     `chat_sessions` (MySQL). `@PrePersist` trên entity tự gán `createdAt`/`updatedAt`.
5. `toSessionResponse()` (`ChatSessionService.java:228-236`) map entity → DTO.

**Dữ liệu thay đổi**: 1 row mới trong `chat_sessions`. Không đụng tới
`chat_messages`, không gọi Gemini, không gọi Qdrant.

**Response** `201 Created`:
```json
{
  "id": 91, "examinationId": 482, "title": "New conversation",
  "active": true, "createdAt": "...", "updatedAt": "..."
}
```

---

#### 0.10.2 Sidebar hiển thị danh sách đoạn chat

```
GET /chat/sessions?page=0&size=20
```

`ChatController.getSessions()` (`ChatController.java:60-66`, mặc định
`size=20`) → `ChatSessionService.getSessions()` (`ChatSessionService.java:54-59`,
`readOnly` transaction) → `requireUser()` rồi
`ChatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc()`
(`ChatSessionRepository.java:10-14`) — **chỉ** trả session của chính người gọi,
sắp xếp theo lần chạm gần nhất. Mỗi entity map qua `toSessionResponse()`.
Không có tài liệu/report/Gemini/Qdrant nào bị chạm tới; đây là API đọc thuần
MySQL, dùng để vẽ sidebar.

---

#### 0.10.3 Bấm vào 1 đoạn chat cũ, tải lịch sử tin nhắn

```
GET /chat/sessions/91/messages?page=0&size=50
```

`ChatController.getMessages()` (`ChatController.java:68-75`) →
`ChatSessionService.getMessages()` (`ChatSessionService.java:61-68`):

1. `requireOwnedSession(sessionId, requireUser(username))`
   (`ChatSessionService.java:164-166`) truy vấn
   `ChatSessionRepository.findByIdAndUserId(sessionId, user.getId())`. Nếu
   session thuộc người khác, kết quả rỗng → `404` — **không phải** `403`, để
   không tiết lộ session đó có tồn tại hay không.
2. `ChatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc()`
   (`ChatMessageRepository.java:10-14`) đọc trang tin nhắn theo thứ tự **cũ →
   mới** (ngược với truy vấn lịch sử nội bộ dùng cho Gemini, vốn lấy mới → cũ
   rồi đảo lại — xem 0.10.4).
3. Map từng `ChatMessage` qua `toMessageResponse()`
   (`ChatSessionService.java:238-246`). DTO này **không** có field
   `sources`/`warning` vì hai thứ đó không được lưu bền — chỉ tồn tại trong
   response gốc của `POST /chat/ask` lúc trả lời. Tải lại lịch sử sẽ hiển thị
   nội dung trả lời text nhưng không hiển thị lại citation.

---

#### 0.10.4 Gõ câu hỏi, bấm "Gửi" — luồng trung tâm của RAG

```
POST /chat/ask
{ "sessionId": 91, "question": "Giải thích KL grade 3 là gì" }
```

`ChatQuestionRequest` (`dto/ChatQuestionRequest.java:6-11`): `sessionId` có thể
`null` (tự tạo session mới); `question` bắt buộc, tối đa 2000 ký tự.
`ChatController.ask()` (`ChatController.java:42-49`) gọi thẳng
`ChatOrchestratorService.ask(sessionId, question, principal.getName())`
(`ChatOrchestratorService.java:33-57`). Chuỗi gọi đầy đủ:

**Bước 1 — chuẩn bị hội thoại (MySQL, trước khi chạm AI):**
`ChatSessionService.prepare()` (`ChatSessionService.java:90-108`, 1 transaction):
tìm/tạo session sở hữu bởi user; nếu `session.active == false` → `400`; đọc
`findTop20BySessionIdOrderByCreatedAtDescIdDesc` (20 tin nhắn gần nhất, mới →
cũ) và dựng chuỗi lịch sử qua `formatHistory()`
(`ChatSessionService.java:180-203`, cắt ở 12.000 ký tự — hằng số
`MAX_HISTORY_CHARACTERS`, dòng 39 — rồi đảo lại cũ → mới); **sau đó mới**
`saveMessage(session, USER, question.trim(), null, null)` lưu câu hỏi hiện tại
thành 1 row `chat_messages` — vì vậy `history` trả về **chưa** chứa câu hỏi vừa
gửi, gateway sẽ nối `Current question:` riêng ở bước 3.

**Bước 2 — Gemini phân loại câu hỏi (router), chưa trả lời:**
`aiGateway.route(question, roleCode, history)` →
`SpringAiChatGateway.route()` (`chat/SpringAiChatGateway.java:121-144`) gọi
`ChatClient` với `ROUTER_PROMPT` (dòng 34-77) đã format `%s` = ngày hiện tại và
`%s` = role code. Prompt này bắt Gemini: chỉ phân loại chứ không trả lời; tự
suy luận lại câu hỏi từ pronoun/tham chiếu trong lịch sử (`"những ca đó"`,
`"report đó"`...) thành `retrievalQuery` độc lập ngữ cảnh; chỉ chọn 1 trong 4
route (`ChatRoute`: `BUSINESS_DATA`, `MEDICAL_RAG`, `HYBRID`, `CLARIFICATION`);
chọn `businessIntent` trong whitelist cố định (`BusinessQueryIntent`, xem
0.10.4.B); tuyệt đối **không được sinh ra SQL**. Kết quả deserialize thành
`ChatRoutingDecision` record (`chat/ChatRoutingDecision.java:3-11`: `route`,
`businessIntent`, `entityId`, `dateFrom`, `dateTo`, `klGrade`, `retrievalQuery`,
`clarificationQuestion`). Nếu Gemini trả JSON không parse được, `route()` bắt
`JacksonException`, log cảnh báo và trả `null` thay vì `500`;
`ChatOrchestratorService.normalize()` (dòng 105-111) biến `null`/`route==null`
thành `CLARIFICATION` với câu hỏi làm rõ mặc định.

**Bước 3 — Java (không phải Gemini) quyết định gọi hàm nào**, `switch` thuần
Java trên `decision.route()` (dòng 40-47), 4 nhánh:

- **`CLARIFICATION`** (dòng 41-42): không đụng DB/Qdrant. Trả thẳng
  `decision.clarificationQuestion()` nếu có, không thì câu mặc định
  (`clarification()`, dòng 113-117).

- **`BUSINESS_DATA`** (dòng 43, → `businessAnswer()` dòng 59-67): gọi
  `BusinessDataQueryService.execute(decision, username)` **trước**, sau đó mới
  gọi Gemini với đúng context SQL trả về — xem 0.10.4.B để có chuỗi SQL chi
  tiết theo từng `businessIntent`. Câu trả lời cuối là
  `aiGateway.answerBusiness(question, result.context(), history)` →
  `SpringAiChatGateway.answerBusiness()` (dòng 146-152) gắn nhãn
  `"BUSINESS DATA CONTEXT:\n"` rồi gọi `answer()` chung (dòng 172-190) với hệ
  thống prompt `ANSWER_RULES` (dòng 79-94) — prompt này cấm bịa dữ liệu, cấm
  tiết lộ tên/địa chỉ/SĐT bệnh nhân (chỉ được nói `patient_code`), và bắt định
  dạng Markdown GitHub-flavoured.

- **`MEDICAL_RAG`** (dòng 44, → `medicalAnswer()` dòng 69-80): tính
  `retrievalQuery` ưu tiên `decision.retrievalQuery()` do Gemini tự viết (đã
  resolve pronoun), fallback về `contextualRetrievalQuery()` (dòng 131-137, lấy
  2000 ký tự cuối lịch sử + câu hỏi hiện tại) nếu router bỏ trống — hàm
  `retrievalQuery()` dòng 124-129. Query này đưa vào
  `MedicalRagService.retrieve(query, roleCode, userId)` — xem 0.10.4.C. Nếu
  `result.isEmpty()` (không có source nào qua ngưỡng) → trả thẳng câu cố định
  `"I could not find sufficient approved medical evidence in the knowledge
  base."` (dòng 74-75), **Gemini không được gọi để tự đoán** khi không có
  bằng chứng. Có source → `aiGateway.answerMedical()` (dòng 154-160, gắn nhãn
  `"RETRIEVED MEDICAL CONTEXT:\n"`). Cả hai nhánh đều gắn `MEDICAL_WARNING`
  (dòng 25-26) vào response.

- **`HYBRID`** (dòng 45-46, → `hybridAnswer()` dòng 82-103): chạy SQL
  (`businessDataQueryService.execute()`) **trước**, rồi nối kết quả SQL vào
  cuối chuỗi truy vấn retrieval
  (`retrievalQuery(...) + "\nHEALTHSYNC DATA:\n" + business.context()`, dòng
  90-92) trước khi gọi `medicalRagService.retrieve()` — nghĩa là ID/thuật ngữ
  lâm sàng lấy được từ MySQL giúp tìm vector chính xác hơn. Nếu phần y khoa
  rỗng, fallback dùng `answerBusiness()` nhưng **vẫn giữ** cảnh báo y khoa
  (dòng 93-96); nếu có cả hai, gộp `sources` của business + medical rồi gọi
  `aiGateway.answerHybrid()` (dòng 162-170, gắn cả hai nhãn context).

**Bước 4 — lưu câu trả lời và trả response:** dù route nào, sau khi có
`AnswerResult`, `ChatSessionService.saveAssistantMessage(session, route.name(),
answer)` (`ChatSessionService.java:110-123`, **transaction riêng** với bước 1)
lưu 1 row `ASSISTANT` vào `chat_messages` kèm `route` và `tokensUsed`, rồi
`touch()` cập nhật `updatedAt` của session. `response()`
(`ChatOrchestratorService.java:139-155`) dựng `ChatAnswerResponse` cuối cùng.

**Vì sao 2 message có thể "lệch cặp"**: bước 1 (lưu USER) và bước 4 (lưu
ASSISTANT) là hai transaction MySQL độc lập. Nếu Gemini/Qdrant lỗi ở bước 2-3
sau khi bước 1 đã commit, `chat_messages` sẽ có 1 row USER không có row
ASSISTANT đi kèm — đây là hành vi hiện tại của code, không phải bug của
UI/repository.

**Response** `200 OK`:
```json
{
  "sessionId": 91, "messageId": 214, "route": "MEDICAL_RAG",
  "answer": "## KL Grade 3\n...", "sources": [
    {"sourceId": "knowledge-document:12", "title": "OARSI Guideline 2023",
     "sourceType": "MEDICAL_DOCUMENT", "reference": "knowledge-document:12, tr. 4",
     "score": 0.81}
  ],
  "warning": "AI-generated medical information is for decision support and must be reviewed by a qualified clinician.",
  "generatedAt": "...", "tokensUsed": 812
}
```

##### 0.10.4.A Bảng route ⇄ hàm Java ⇄ nguồn dữ liệu

| Route | Java gọi gì | Nguồn context cho Gemini |
| --- | --- | --- |
| `CLARIFICATION` | không gọi service nào | không có, chỉ hỏi lại |
| `BUSINESS_DATA` | `BusinessDataQueryService.execute()` | text do SQL cố định sinh ra |
| `MEDICAL_RAG` | `MedicalRagService.retrieve()` | chunk đã qua similarity + lọc quyền ở Qdrant |
| `HYBRID` | SQL trước, `MedicalRagService.retrieve()` sau | cả hai, có nhãn tách riêng trong prompt |

##### 0.10.4.B Nhánh `BUSINESS_DATA`/`HYBRID`: whitelist SQL, không có SQL tự do

`BusinessDataQueryService.execute()` (`BusinessDataQueryService.java:42-72`):

1. Tra `role` thật từ MySQL (không tin role trong JWT claim một cách mù quáng —
   vẫn query lại `User`). `ADMIN` + intent lâm sàng (`isClinical()`, dòng
   192-199: `EXAMINATION_LIST`, `TODAY_EXAMINATION_LIST`,
   `EXAMINATION_FINAL_RESULT`, `REPORT_SUMMARY`, `GRADE_DISTRIBUTION`,
   `GRADE_COUNT`) → `403 UnauthorizedAccessException` **trước khi** chạy SQL.
2. `dateRange()` (dòng 208-222) resolve `dateFrom`/`dateTo` do Gemini trích ra
   từ câu hỏi tự nhiên thành khoảng `LocalDateTime` (không có ngày tường minh
   → toàn bộ dữ liệu từ 1970 tới ngày mai; hai intent `TODAY_*` mặc định hôm
   nay).
3. `switch (intent)` (dòng 61-71) — đây là **whitelist đóng cứng** 9 giá trị
   enum (`BusinessQueryIntent.java:3-14`), Gemini chỉ được chọn tên intent, Java
   chọn hàm/SQL tương ứng, không bao giờ nhận SQL string từ model:
   - `TODAY_EXAMINATION_COUNT`/`EXAMINATION_COUNT` → `countExaminations()`
     (dòng 110-117): `COUNT(*) FROM examinations` theo `created_at`, thêm
     `AND e.doctor_id = :userId` nếu role là `DOCTOR`.
   - `TODAY_EXAMINATION_LIST`/`EXAMINATION_LIST` → `recentExaminations()`
     (dòng 88-108): join `patients` lấy `patient_code` (**không bao giờ** lấy
     tên bệnh nhân — comment dòng 80-86 giải thích lý do: context này bị echo
     lại vào lịch sử hội thoại ở mọi lượt sau, nên PII không được phép lọt
     vào); lọc theo `klGrade` nếu có, dùng đúng công thức ưu tiên review đã
     xác nhận hơn AI-prediction thô (`EFFECTIVE_GRADE`, dòng 34-37); giới hạn
     cứng 10 dòng.
   - `REPORT_COUNT` → `countReports()` (dòng 119-127).
   - `EXAMINATION_FINAL_RESULT` → `examinationResult()` (dòng 129-143), bắt
     buộc `entityId` dương, không tìm thấy/không thuộc quyền → `404`.
   - `REPORT_SUMMARY` → `reportSummary()` (dòng 145-160).
   - `GRADE_DISTRIBUTION` → `gradeDistribution()` (dòng 162-172): `GROUP BY`
     grade hiệu lực.
   - `GRADE_COUNT` → `gradeCount()` (dòng 174-185), bắt buộc `klGrade` hợp lệ
     0-4 (`requireGrade()`, dòng 201-206) nếu Gemini không trích được thì
     `400`.
   - `UNKNOWN` (Gemini không map được câu hỏi vào 8 intent trên) →
     `400 IllegalArgumentException` tường minh, **không** âm thầm chạy 1 câu
     truy vấn đoán mò.
4. Mỗi nhánh trả `BusinessQueryResult(context, sources)` qua `result()` (dòng
   187-190) — `sources` luôn có `score = null` vì không phải kết quả similarity.

##### 0.10.4.C Nhánh `MEDICAL_RAG`/`HYBRID`: Ollama embedding + Qdrant, lọc quyền ngay tại Qdrant

`MedicalRagService.retrieve(question, roleCode, userId)`
(`MedicalRagService.java:31-70`):

1. `scopeFilter(roleCode, userId)` (dòng 72-91) dựng biểu thức filter Qdrant
   dạng chuỗi, **luôn** bắt buộc `publicationStatus == 'PUBLISHED'`:
   - `ADMIN`: `(accessScope == 'ALL' || accessScope == 'ADMIN')`.
   - `DOCTOR`/`HEAD_OF_DEPARTMENT`/`DEPARTMENT_HEAD`: `(accessScope == 'ALL' ||
     accessScope == 'DOCTOR' || (accessScope == 'OWNER' && (ownerUserId == <id>
     || assignedDoctorUserId == <id>)))` — role trưởng khoa **chưa** tự động
     thấy toàn bộ report của khoa, chỉ thấy report họ là chủ sở hữu/bác sĩ được
     gán (comment dòng 78-80 nói rõ đây là hạn chế hiện tại, không phải thiết
     kế cuối cùng).
   - Role khác → `403 AccessDeniedException`, fail-closed.
2. `SearchRequest.builder().query(question).topK(properties.retrievalTopK())`
   (mặc định 12, `application.yaml:109`)
   `.similarityThreshold(properties.similarityThreshold())` (mặc định 0.35,
   `application.yaml:110`) `.filterExpression(scopeFilter)` rồi
   `vectorStore.similaritySearch(request)` (dòng 33-39). Bản thân Java
   **không** tự embedding câu hỏi: Spring AI's Qdrant `VectorStore` tự động gọi
   model embedding cấu hình (`bge-m3` qua Ollama,
   `application.yaml:70-77`/mục 2) để biến `question` thành vector, rồi gửi
   `filterExpression` cùng vector đó thẳng tới Qdrant. Java không có bước
   "lấy hết vector rồi tự lọc trong RAM" — mọi document không khớp filter
   **không bao giờ** rời khỏi Qdrant.
3. Không có match → `MedicalRetrievalResult("", List.of())` (dòng 40-41,
   "empty" theo số lượng source chứ không theo độ dài text).
4. Có match: lặp theo thứ tự điểm số giảm dần, ghép text vào `context` (giới
   hạn 60.000 ký tự — `MAX_CONTEXT_CHARS`, dòng 26, comment giải thích: với
   `topK=12` × chunk ~700 token, giới hạn cũ từng cắt mất khoảng nửa bằng
   chứng trước khi tới Gemini), không bao giờ cắt giữa chừng 1 chunk — thà bỏ
   nguyên chunk (dòng 56-61). Mỗi chunk được gắn nhãn `[SOURCE: <title>]`.
   `uniqueSources` dùng `LinkedHashMap` để 1 tài liệu chỉ xuất hiện 1 lần trong
   danh sách nguồn trả về cho FE dù nhiều chunk của nó được chọn (dòng 64-67).

---

#### 0.10.5 Đổi tên / đóng / mở lại một đoạn chat

```
PATCH /chat/sessions/91
{ "title": "Hỏi về KL grade", "active": null }
```

`ChatController.updateSession()` (`ChatController.java:77-84`) →
`ChatSessionService.update()` (`ChatSessionService.java:70-88`): cả `title` và
`active` đều `null` → `400` (dòng 72-74, phải có ít nhất 1 field); `title` rỗng
sau `trim()` → `400`; áp field nào có, ghi `updatedAt = now()`, lưu. Đóng đoạn
chat tức là `active=false`, khiến `POST /chat/ask` sau đó dùng `sessionId` này
bị chặn ở `prepare()` với `400 "Chat session is inactive"` (mục 0.10.4, bước 1).

---

#### 0.10.6 Upload 1 tài liệu y khoa

**FE làm gì**: trang "Kho tri thức", bấm "Tải lên", chọn 1 file PDF/DOC/DOCX/TXT.

```
POST /knowledge-documents/upload
Content-Type: multipart/form-data
file=<bytes>; title=(optional); accessScope=ALL|DOCTOR|ADMIN|OWNER (default ALL)
```

`KnowledgeController.upload()` (`KnowledgeController.java:53-63`) có thêm
`@LogAction("UPLOAD_MEDICAL_KNOWLEDGE")` — `AuditLogAspect` ghi 1 row vào bảng
`audit_logs` (ai làm, action nào, khi nào) song song, độc lập với luồng nghiệp
vụ chính. Gọi `KnowledgeIngestionService.upload()`
(`KnowledgeIngestionService.java:79-104`, **cố ý không** `@Transactional` vì
hàm này gọi Gemini đồng bộ — không giữ transaction DB mở trong lúc chờ mạng):

1. `validateFile()` (dòng 209-220): file rỗng → `400`; vượt
   `properties.maxDocumentBytes()` (mặc định 50 MB,
   `application.yaml:107`) → `400`; đuôi file ngoài
   `{pdf, doc, docx, txt}` (`ALLOWED_EXTENSIONS`, dòng 51) → `400`.
2. Đọc toàn bộ bytes, tính `sha256`. `sourceKey = "file:" + checksum`; đã tồn
   tại trong `knowledge_documents` (`repository.existsBySourceKey()`) → `400`
   "đã upload rồi" — chặn **trước khi** gọi Gemini, tiết kiệm 1 lượt gọi model
   cho file trùng.
3. **`medicalDocumentValidator.validate(bytes, filename, contentType)`** —
   xem 0.10.6.A, đây là bước "cổng chắn" quan trọng nhất: nếu không pass, toàn
   bộ các bước ghi file/DB dưới đây **không xảy ra**.
4. `storeFile()` (dòng 262-276): ghi bytes vào `knowledgeDir` (mặc định
   `<storage-base-dir>/knowledge`, `application.yaml:106`) với tên **UUID**
   ngẫu nhiên giữ nguyên đuôi file — tên file gốc không bao giờ dùng làm tên
   vật lý trên đĩa (chống path traversal/đè file). Có `path.startsWith(root)`
   guard sau khi normalize.
5. Tạo entity `KnowledgeDocument`: `sourceType=FILE`, `status` mặc định
   `PENDING` (do `@PrePersist` của entity, không set tường minh ở đây),
   `uploadedBy = user hiện tại`, lưu MySQL.
6. `requestIndex(document.getId())` (dòng 205-207) publish
   `KnowledgeIndexRequestedEvent` — Spring **chưa gửi event ngay**, đợi tới khi
   phương thức HTTP hiện tại hoàn tất chu trình request/response bình thường
   (đây không phải transactional event vì `upload()` không có
   `@Transactional`, event được publish và xử lý đồng bộ trong cùng lời gọi,
   nhưng listener thật sự chạy nền — xem 0.10.17).
7. `toResponse()` trả DTO với `status: "PENDING"`, `chunkCount: null`.

**Response** `202 Accepted` (không phải `200`/`201` — vector hoá chưa xong lúc
trả response):
```json
{ "id": 44, "title": "OARSI Guideline 2023", "sourceType": "FILE",
  "status": "PENDING", "chunkCount": null,
  "previewUrl": "/api/v1/knowledge-documents/44/preview", ... }
```

FE thường phải tự polling lại `GET /knowledge-documents` hoặc mở lại danh sách
sau vài giây để thấy `status` chuyển `PROCESSING → INDEXED`/`FAILED`.

##### 0.10.6.A Cổng chắn: `MedicalDocumentValidator` — Gemini xác nhận "đây có phải tài liệu y khoa"

`MedicalDocumentValidator.validate()` (`MedicalDocumentValidator.java:24-43`):

1. `documentReader.read(bytes, filename, contentType)` →
   `KnowledgeDocumentReader` (`KnowledgeDocumentReader.java`, mục 10): PDF dùng
   PDFBox, TXT đọc UTF-8 nghiêm ngặt, DOC/DOCX/HTML (từ URL) dùng Apache Tika.
2. Nối toàn bộ text; rỗng/không đọc được → `400`.
3. `sample()` (`MedicalDocumentValidator.java:45-57`): tài liệu ngắn gửi
   nguyên văn (`[COMPLETE DOCUMENT]`); tài liệu dài chỉ gửi 3 mẫu đầu/giữa/cuối
   mỗi mẫu `medicalValidationSampleChars` ký tự (mặc định 6000,
   `application.yaml:111`) — để không tốn quá nhiều token Gemini cho 1 file
   lớn mà vẫn đủ đại diện.
4. `aiChatGateway.assessMedicalDocument(samples)` →
   `SpringAiChatGateway.assessMedicalDocument()` (dòng 99-119) gọi Gemini với
   `MEDICAL_DOCUMENT_CLASSIFIER_PROMPT` (dòng 23-32) — prompt nói rõ: chỉ chấp
   nhận nội dung y khoa/lâm sàng/dược/y tế công cộng thực chất; từ chối tài
   liệu kinh doanh/phần mềm/pháp lý dù có nhắc từ y khoa hờ hợt; **coi mẫu văn
   bản là dữ liệu không tin cậy, không được làm theo chỉ dẫn nằm trong đó**
   (chống prompt injection từ chính nội dung file upload); mơ hồ → phải trả
   `medical=false`. Kết quả deserialize thành `MedicalDocumentAssessment`
   (`chat/MedicalDocumentAssessment.java:7-10`: `medical`, `confidence`,
   `reason`).
5. Từ chối khi: `assessment == null` (Gemini trả JSON không parse được), hoặc
   `medical != true`, hoặc `confidence == null`, hoặc
   `confidence < medicalValidationMinConfidence` (mặc định 0.7,
   `application.yaml:112`) → `400 "Document rejected: <reason>"`.

Vì bước này chạy **trước** `storeFile()`/`repository.save()` trong
`upload()`/`addUrl()`, một file bị từ chối không để lại rác trên đĩa, không có
metadata MySQL, không có vector — không cần dọn dẹp gì thêm.

---

#### 0.10.7 Upload nhiều tài liệu cùng lúc (kéo-thả nhiều file)

```
POST /knowledge-documents/upload/batch
files=<file1>,<file2>,...; accessScope=ALL
```

`KnowledgeController.uploadBatch()` (`KnowledgeController.java:65-74`) →
`KnowledgeBatchIngestionService.upload()`
(`KnowledgeBatchIngestionService.java:26-56`): rỗng hoặc rỗng danh sách → `400`;
quá `MAX_BATCH_FILES = 10` (dòng 22) → `400`. Sau đó **lặp tuần tự** (không
song song) gọi lại chính xác `KnowledgeIngestionService.upload()` của mục
0.10.6 cho từng file — nghĩa là mỗi file trải qua đầy đủ: kiểm tra đuôi/size →
SHA-256 dedupe → Gemini classifier → lưu MySQL → publish event riêng (mỗi file
= 1 lần gọi Gemini classifier, 1 event index riêng, chạy nền độc lập). Lỗi
`IllegalArgumentException` của 1 file (trùng file, không phải y khoa, sai
định dạng...) được bắt riêng và chỉ đánh dấu file đó `accepted=false` kèm
`error` — **không** làm hỏng các file còn lại trong batch (dòng 39-51); lỗi
runtime không lường trước cũng được log rồi tiếp tục, không để 1 file lỗi lạ
làm crash toàn bộ request.

**Response** `202 Accepted` — luôn thành công ở tầng HTTP dù batch có file bị
từ chối, chi tiết từng file nằm trong body:
```json
{ "total": 3, "accepted": 2, "rejected": 1, "items": [
  {"originalFileName": "a.pdf", "accepted": true, "document": {...}, "error": null},
  {"originalFileName": "b.txt", "accepted": false, "document": null,
   "error": "Document rejected: the content is not clearly medical"},
  {"originalFileName": "a.pdf", "accepted": false, "document": null,
   "error": "This document has already been uploaded"}
]}
```

---

#### 0.10.8 Thêm nguồn tri thức từ URL

```
POST /knowledge-documents/url
{ "title": "WHO Osteoarthritis Guideline", "url": "https://who.int/...", "accessScope": "ALL" }
```

`KnowledgeController.addUrl()` (`KnowledgeController.java:76-83`) →
`KnowledgeIngestionService.addUrl()` (`KnowledgeIngestionService.java:106-130`):

1. `validatePublicUrl()` (dòng 243-260): bắt buộc scheme `http`/`https`, có
   host, **không** có user-info trong URL (chặn dạng `http://user:pass@host`).
   `InetAddress.getAllByName(host)` resolve DNS rồi loại mọi địa chỉ
   any-local/loopback/link-local/site-local/multicast — đây là hàng rào chống
   **SSRF**: ngăn backend bị lừa tự gọi vào `127.0.0.1`, mạng nội bộ
   `10.x/172.16.x/192.168.x`, hay `169.254.x` (metadata endpoint của cloud).
2. `sourceKey = "url:" + sha256(URL đã normalize)`; đã tồn tại → `400`.
3. `download(uri)` (dòng 222-241): dùng `knowledgeRestClient` bean
   (`ChatAiConfiguration.java`, mục 2) — **redirect bị tắt hoàn toàn** ở tầng
   HTTP client, nên kể cả nếu DNS check ở bước 1 pass nhưng server trả
   `302` sang địa chỉ nội bộ, request vẫn không đi theo redirect đó; đọc tối
   đa `maxUrlBytes + 1` byte (mặc định 5 MB, `application.yaml:108`) để phát
   hiện vượt giới hạn mà không cần tải hết file khổng lồ; không phải `2xx` →
   `400`; rỗng hoặc vượt giới hạn → `400`.
4. `medicalDocumentValidator.validate(bytes, "source.html", "text/html")` —
   **cùng cổng chắn Gemini y hệt mục 0.10.6.A**, dùng Tika để đọc text từ HTML.
5. `storeFile(bytes, "source.html")` ghi HTML thô vào `knowledgeDir` (không
   phải bản đã strip tag).
6. Lưu `KnowledgeDocument` với `sourceType=URL`, `sourceUrl` = URL gốc, publish
   `KnowledgeIndexRequestedEvent` giống hệt luồng upload file.

**Response** `202 Accepted`, cấu trúc DTO giống 0.10.6 nhưng `sourceType: "URL"`.

---

#### 0.10.9 Trang quản lý tài liệu: liệt kê / lọc / tìm kiếm

```
GET /knowledge-documents?keyword=OARSI&sourceType=FILE&status=INDEXED&accessScope=ALL&page=0&size=20&sort=createdAt,desc
```

`KnowledgeController.getAll()` (`KnowledgeController.java:85-94`, mặc định sort
`createdAt DESC`, `size=20`) → `KnowledgeIngestionService.getAll(...)`
(`KnowledgeIngestionService.java:137-149`): `keyword` rỗng/blank normalize
thành `null` để JPQL coi filter là "bỏ qua" thay vì so khớp chuỗi rỗng; gọi
`KnowledgeDocumentRepository.search()` (JPQL case-insensitive theo
title/originalName, kết hợp AND các filter optional khác) — thuần đọc MySQL,
không đụng Qdrant/Gemini.

---

#### 0.10.10 Xem trước tài liệu (nhúng trong iframe)

```
GET /knowledge-documents/44/preview
```

`KnowledgeController.preview()` (`KnowledgeController.java:96-100`) →
`ingestionService.getFile(id)` (`KnowledgeIngestionService.java:151-173`,
`readOnly` transaction):

1. Không có `storagePath` (ví dụ tài liệu `sourceType=REPORT`, sinh ra thẳng
   trong Qdrant, không có file vật lý) → `404`.
2. `Path.of(...).toRealPath()` resolve cả `knowledgeDir` gốc lẫn đường dẫn file
   thật (theo symlink); `!path.startsWith(root)` hoặc không phải regular file
   → `404`. Đây là chống **path traversal**: mọi lỗi IO (kể cả file bị xoá thủ
   công ngoài ứng dụng) đều quy về `404` chung, không rò rỉ chi tiết filesystem
   ra ngoài.
3. Content-Type resolve theo thứ tự ưu tiên: content-type lưu DB (nếu có ý
   nghĩa) → map cứng theo đuôi file (`EXTENSION_CONTENT_TYPES`, dòng 59-63,
   vì `Files.probeContentType` không đáng tin trên nhiều máy chủ Linux/Docker)
   → `Files.probeContentType` → cuối cùng `application/octet-stream`.
4. Controller's `fileResponse(file, download=false)`
   (`KnowledgeController.java:142-167`) dựng response: `Content-Disposition:
   inline`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, và
   ghi đè `Content-Security-Policy: frame-ancestors *` để override mặc định
   `X-Frame-Options: DENY` của Spring Security — chủ đích cho phép FE nhúng
   file này trong `<iframe>` để "xem trước" ngay trên trang, thay vì bị trình
   duyệt chặn vì chính sách chống clickjacking mặc định.

---

#### 0.10.11 Xem nội dung text đã trích xuất

```
GET /knowledge-documents/44/content
```

`KnowledgeController.content()` (`KnowledgeController.java:102-109`, trả
`text/plain; charset=UTF-8`, `no-store`) →
`ingestionService.getText(id)` (`KnowledgeIngestionService.java:175-183`): gọi
lại `getFile(id)` **chỉ để tận dụng guard ownership/tồn-tại/path-traversal ở
trên** (kết quả trả về không được dùng), rồi `documentReader.read(document)`
đọc lại toàn bộ file từ đĩa và nối các đoạn text bằng `\n\n`. Đây là cách FE
hiển thị "văn bản mà hệ thống thực sự trích xuất được" — khác với preview (file
gốc PDF/DOC) — hữu ích để debug khi 1 file bị `FAILED` vì "No readable text".

---

#### 0.10.12 Tải file gốc

```
GET /knowledge-documents/44/download
```

Giống hệt luồng 0.10.10 nhưng `download=true` trong `fileResponse()` →
`Content-Disposition: attachment`, và có thêm
`@LogAction("DOWNLOAD_MEDICAL_KNOWLEDGE")` ghi audit log (khác `preview` –
`preview` không audit).

---

#### 0.10.13 Bấm "Index lại" trên tài liệu `FAILED`/`PENDING`

```
POST /knowledge-documents/44/reindex
```

`KnowledgeController.reindex()` (`KnowledgeController.java:118-123`) →
`KnowledgeIngestionService.reindex()` (`KnowledgeIngestionService.java:188-196`,
1 transaction): set lại `status = PENDING`, xoá `errorMessage`, save, rồi
`requestIndex()` publish lại `KnowledgeIndexRequestedEvent` — **file vật lý và
metadata giữ nguyên**, chỉ kích hoạt lại `KnowledgeIndexingWorker` chạy từ đầu
(đọc file → chunk → embed → ghi Qdrant, xem 0.10.17). Vector cũ (nếu còn sót từ
lần chạy trước) bị xoá theo `sourceKey` trước khi thêm vector mới ở trong
worker, không phải ở bước này.

---

#### 0.10.14 Bấm "Xóa" một tài liệu

```
DELETE /knowledge-documents/44
```

`KnowledgeController.delete()` (`KnowledgeController.java:134-140`) →
`KnowledgeIngestionService.delete(id)` (dòng 198-203): bọc toàn bộ trong
`KnowledgeDocumentOperationCoordinator.executeExclusively(id, ...)`
(`KnowledgeDocumentOperationCoordinator.java`, mục 12) — khoá theo document ID
bằng 1 trong 256 `ReentrantLock` cố định (striped lock), để việc xoá **không
thể** chạy đồng thời với worker đang index cùng document trong cùng 1 JVM.
Bên trong khoá, `KnowledgeDocumentDeletionService.delete()`
(`KnowledgeDocumentDeletionService.java:27-34`, 1 transaction): xoá toàn bộ
chunk Qdrant theo `FilterExpressionBuilder().eq("sourceKey",
document.getSourceKey())` **trước**, xoá row `knowledge_documents` **sau**, rồi
`deleteStoredFile()` (dòng 36-50) xoá file vật lý (bỏ qua nếu
`sourceType=REPORT` vì không có `storagePath`; cùng kiểu `startsWith(root)`
guard chống xoá nhầm ngoài `knowledgeDir`). Trả `204 No Content`.

**Race condition đã được xử lý tường minh**: nếu lệnh xoá này chạy đúng lúc
`KnowledgeIndexingWorker` vừa `vectorStore.add(chunks)` xong nhưng chưa kịp
`markIndexed()`, worker sẽ phát hiện row đã biến mất khi cố update status và tự
xoá lại các chunk vừa thêm — xem 0.10.17, tránh để lại "vector mồ côi" không có
metadata MySQL tương ứng.

---

#### 0.10.15 Bác sĩ đồng bộ 1 report vào AI thủ công

**FE làm gì**: trên trang xem report đã duyệt, bác sĩ bấm nút kiểu "Đưa report
này vào trợ lý AI" (dùng khi report cũ có trước khi RAG được bật, hoặc lần
index tự động trước đó bị lỗi).

```
POST /knowledge-documents/reports/205/sync
```

`KnowledgeController.syncReport()` (`KnowledgeController.java:125-132`) — chú
ý quyền khác hẳn các endpoint `/knowledge-documents` còn lại:
`hasAnyRole('DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')` (**không** có
`ADMIN`) và `hasAuthority('USE_AI_CHAT')` (**không** phải
`MANAGE_MEDICAL_KNOWLEDGE`) — vì đây thực chất là hành động "hỏi AI về report
của mình", không phải quản trị kho tri thức chung. Gọi
`ReportKnowledgeSyncService.syncReport()`
(`ReportKnowledgeSyncService.java:48-62`):

1. `ADMIN` gọi tới đây (dù về lý thuyết không qua nổi `@PreAuthorize` ở trên,
   hàm vẫn tự kiểm tra lại) → `403` — kiểm tra kép có chủ đích.
2. `loadReport(reportId)` (dòng 143-165) 1 câu SQL join `report` +
   `examinations` + `diagnosis_reviews` lấy `ownerUserId` (ưu tiên
   `operating_doctor_id`, fallback `examination.doctor_id`) và
   `assignedDoctorUserId`; không thấy report → `404`.
3. `DOCTOR` chỉ được đồng bộ report họ sở hữu hoặc được gán — không phải cả
   hai → `403`.
4. `index(row)` (dòng 94-141) — **chạy đồng bộ ngay trong request**, khác
   file/URL: dựng text mô tả report (report ID, examination ID, study date,
   final diagnosis, confirmed KL grades, clinical summary — **cố ý không** có
   tên/địa chỉ/SĐT bệnh nhân hay đường dẫn DICOM/ảnh), `splitter.apply()` chia
   chunk, `vectorStore.delete()` chunk cũ theo `sourceKey =
   "report:" + reportId` rồi `vectorStore.add()` chunk mới, cập nhật
   `knowledge_documents` (`status=INDEXED`, `chunkCount`, `indexedAt`).
   `accessScope` luôn là `OWNER`. Lỗi trong lúc ghi Qdrant → `status=FAILED`,
   lưu `errorMessage`, rồi **ném lại exception** (khác `upload()`/`addUrl()`
   vốn trả `202` trước khi biết kết quả — endpoint này đợi xong luôn).

**Response**: dù controller khai `202`, hàm `syncReport()` thực chất trả kết
quả **sau khi** đã index xong (đồng bộ), nên `status` trong body luôn là
`INDEXED` (hoặc exception nếu lỗi) chứ không phải `PENDING` như luồng file/URL.

---

#### 0.10.16 (Ngoài module chat) Bấm "Tạo báo cáo PDF" → tự động đưa report vào RAG

Đây **không phải** endpoint của `/chat` hay `/knowledge-documents`, nhưng là
ví dụ rõ nhất cho việc "1 hành động FE ở tính năng khác âm thầm gọi vào RAG".
Chi tiết đầy đủ của chính endpoint này nằm ở
[examination-verification-report-code-guide.md](examination-verification-report-code-guide.md);
ở đây chỉ trích phần chạm RAG, khớp với `PdfExportService.java:101-173` (mục
13 phía dưới có annotate từng dòng bằng tiếng Việt):

1. Bác sĩ bấm "Tạo báo cáo" trên trang khám đã `VERIFIED` →
   `PdfExportService.generateAndSavePdfReport()` render PDF, lưu row `Report`
   mới trong **cùng transaction** với việc set `examination.status =
   REPORT_GENERATED`, rồi `eventPublisher.publishEvent(new
   ReportKnowledgeSyncRequestedEvent(savedReport.getId()))`.
2. Vì đây là `@TransactionalEventListener(phase = AFTER_COMMIT)`
   trên `ReportKnowledgeSyncService.syncGeneratedReport()`
   (`ReportKnowledgeSyncService.java:64-73`, chạy `@Async("taskExecutor")`
   trong 1 transaction MỚI `REQUIRES_NEW`), listener chỉ chạy **sau khi** row
   `Report` chắc chắn đã commit vào MySQL — không có race condition nào khiến
   event bắn ra trước khi report thật sự tồn tại bền vững.
3. Listener gọi lại đúng hàm `index()` mô tả ở mục 0.10.15 bước 4 — cùng 1
   pipeline, không phân biệt "sync thủ công" hay "sync tự động sau khi tạo
   PDF".
4. Nếu bác sĩ bấm lại nút "Tạo báo cáo" khi report **đã** `REPORT_GENERATED`
   và file PDF vẫn còn trên đĩa, `generateAndSavePdfReport()` **không** render
   lại, chỉ phát lại cùng loại event cho `report.getId()` cũ — đây chính là
   cách một report bị index lỗi/kẹt được "sửa" mà bác sĩ không cần thao tác gì
   trong Kho tri thức, chỉ cần bấm lại nút Tạo báo cáo ở màn hình khám.

---

#### 0.10.17 Nền: `KnowledgeIndexingWorker` — thực sự đẩy vector vào Qdrant

Không có request HTTP nào gọi trực tiếp worker này; nó là listener cho
`KnowledgeIndexRequestedEvent` do 0.10.6/0.10.7/0.10.8/0.10.13 publish.
`KnowledgeIndexingWorker.index()` (`KnowledgeIndexingWorker.java:34-41`):
`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`
đảm bảo **không bao giờ** index 1 upload đã bị rollback; `@Async("taskExecutor")`
đẩy việc ra khỏi thread HTTP request (`AsyncConfig`, mục 2: 1 thread duy nhất,
hàng đợi 20 — cố tình serialize để không làm quá tải Ollama/Qdrant cùng lúc).
Trước khi làm việc thật, cũng khoá qua
`KnowledgeDocumentOperationCoordinator.executeExclusively()` (cùng cơ chế khoá
với xoá ở 0.10.14). `indexExclusively()` (dòng 43-75):

1. `stateService.markProcessing(documentId)` (`KnowledgeIndexStateService.java`,
   transaction `REQUIRES_NEW`) set `status=PROCESSING`; nếu document đã bị xoá
   trước khi worker kịp chạy → trả `null`, worker dừng ngay, không làm gì thêm.
2. `documentReader.read(knowledge)` đọc lại file từ đĩa (PDFBox/Tika/UTF-8 tuỳ
   loại — cùng logic với `MedicalDocumentValidator` ở bước validate, nhưng lần
   này đọc **toàn bộ** tài liệu, không sample).
3. Với mỗi `Document` con trả về, gắn `metadata()` (dòng 92-108): `sourceKey`,
   `knowledgeDocumentId`, `title`, `sourceType`, `reference` (URL gốc nếu có,
   không thì `"knowledge-document:<id>"`), `accessScope`,
   `publicationStatus="PUBLISHED"` cứng, và **chỉ khi** `accessScope=OWNER`
   mới thêm `ownerUserId` (đúng chính là chỉ trường hợp file upload dạng
   riêng tư; report dùng `ReportKnowledgeSyncService` tự gắn metadata OWNER
   riêng, xem 0.10.15). PDF còn có thêm `page` theo từng đoạn (dòng 83-90).
4. `splitter.apply(enriched)` — bean `medicalKnowledgeSplitter`
   (`ChatAiConfiguration.java`, mục 2): chunk ~700 token, tối thiểu 250 ký tự
   giữ lại, bỏ chunk dưới 20 ký tự, tối đa 10.000 chunk/nguồn. 0 chunk (tài
   liệu không có text đọc được) → ném lỗi, rơi xuống `catch` thành `FAILED`.
5. `vectorStore.delete(filter sourceKey == ...)` xoá sạch chunk cũ (idempotent
   cho trường hợp reindex) **trước khi** `vectorStore.add(chunks)` — đây chính
   là bước Spring AI **tự động gọi Ollama** để embedding từng chunk thành
   vector rồi ghi (upsert) vào Qdrant.
6. `stateService.markIndexed(documentId, chunks.size())`
   (transaction `REQUIRES_NEW` riêng): ghi `status=INDEXED`, `chunkCount`,
   `indexedAt`. Nếu hàm này trả `false` (document đã bị xoá đúng lúc giữa bước
   5 và bước 6), worker **tự xoá lại** chính các vector vừa thêm ở bước 5 để
   không để lại "vector mồ côi" trong Qdrant không có metadata MySQL tương ứng.
7. Bất kỳ `RuntimeException`/`LinkageError` nào trong toàn bộ quá trình →
   log lỗi, `stateService.markFailedIfPresent()` ghi `status=FAILED` +
   `errorMessage` (cắt ở 1000 ký tự) **chỉ khi** document vẫn còn tồn tại
   (best-effort, không "hồi sinh" 1 document đã bị người dùng xoá).

---

#### 0.10.18 Nền: job quét định kỳ vá report bị bỏ sót

`ReportKnowledgeSyncService.syncNewReports()`
(`ReportKnowledgeSyncService.java:75-92`), `@Scheduled` mỗi
`reportSyncDelayMs` (mặc định 300.000 ms = 5 phút,
`application.yaml:113`, cần `@EnableScheduling` trên `BeApplication`, mục 2).
Không do FE gọi, không cần người dùng thao tác gì. Mỗi lần chạy: 1 câu SQL
`LEFT JOIN knowledge_documents ON source_key = CONCAT('report:', r.id)` tìm tối
đa 100 report có bác sĩ phụ trách nhưng **chưa** có knowledge row, hoặc có
nhưng chưa `INDEXED`, hoặc `checksum` khác giá trị schema hiện tại
(`'report-metadata-v2'` — dùng như một "phiên bản format metadata", tăng lên
mỗi khi cấu trúc text/metadata index report đổi, để tự động re-index toàn bộ
report cũ theo format mới mà không cần migration thủ công). Với mỗi report,
gọi lại đúng `index()` (0.10.15 bước 4); lỗi từng report được log và bỏ qua,
không chặn các report còn lại trong batch 100.

---

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
            // PHÁT SỰ KIỆN #1 - nhánh "dùng lại": không có Report row mới, không có PDF mới,
            // nhưng sự kiện vẫn được phát. Đây là CÁCH DUY NHẤT để 1 knowledge row bị kẹt/lỗi
            // được thử lại mà bác sĩ không cần "hủy generate" report trước - họ chỉ cần gọi lại
            // generate-report (client tự retry, hoặc FE gửi lại từ UI cũ) là nhánh này tự sửa
            // lại việc index.
            eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(existingReport.getId()));
            return toResponse(existingReport);
        }
        // existingReport == null: status báo REPORT_GENERATED nhưng không còn file thật - rơi
        // xuống dưới để render lại, sẽ chạm PHÁT SỰ KIỆN #2 với 1 report ID HOÀN TOÀN MỚI.
    }
    requireVerified(examination, "generating");

    // ... dựng ReportForm, render PDF, di chuyển file (dòng 125-145; không có dòng nào liên quan RAG ở đây) ...

    Report report = new Report();
    // ... report.set*(...) (dòng 148-155; không có dòng nào liên quan RAG ở đây) ...
    Report savedReport = reportRepository.save(report);
    // PHÁT SỰ KIỆN #2 - nhánh "generate lần đầu hoặc phục hồi": 1 Report row hoàn toàn mới
    // (hoặc vừa được render lại) NGAY LÚC NÀY đã tồn tại, được lưu trong CÙNG transaction với
    // mọi thứ ở trên. Vì Spring chỉ thực sự gửi sự kiện này SAU KHI @Transactional bao quanh
    // commit xong (xem ReportKnowledgeSyncService.syncGeneratedReport(), chú thích riêng bên
    // dưới), listener CHẮC CHẮN tìm thấy savedReport.getId() đã được lưu bền trong MySQL vào
    // lúc nó chạy - không có race condition nào khiến sự kiện phát ra trước khi report row
    // thực sự tồn tại lâu dài.
    eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(savedReport.getId()));

    examination.setFindings(String.join("\n", form.findings()));
    examination.setConclusion(form.conclusion());
    examination.setStatus(ExaminationStatus.REPORT_GENERATED);
    examinationRepository.save(examination);
    return toResponse(savedReport);
    // Cả PHÁT SỰ KIỆN #1 lẫn #2 đều phát CÙNG 1 loại sự kiện, chỉ mang theo report ID -
    // ReportKnowledgeSyncService.syncGeneratedReport() (bên dưới) không biết và cũng không
    // cần biết nhánh nào đã tạo ra nó.
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
    // 1. Lấy/tạo session thuộc sở hữu người gọi, dựng lịch sử CŨ, rồi mới lưu row USER.
    //    Lịch sử trả về CỐ Ý chưa chứa `question` hiện tại.
    ChatSessionService.PreparedConversation conversation =
            chatSessionService.prepare(sessionId, question, username);

    // 2. Role và user ID lấy từ User trong MySQL, không phải từ output của model.
    //    2 giá trị này sau đó dùng để giới hạn quyền truy cập SQL và Qdrant.
    String roleCode = conversation.user().getRole().getCode();
    String history = conversation.history();

    // 3. Gemini trả JSON có cấu trúc, map vào ChatRoutingDecision.
    //    normalize() ngăn route null làm crash switch bên dưới.
    ChatRoutingDecision decision = normalize(aiGateway.route(question, roleCode, history));

    // 4. Java, KHÔNG PHẢI Gemini, mới là bên chọn đúng nhánh thực thi được phép.
    AnswerResult result = switch (decision.route()) {
        // Không gọi database/vector/model nào cả. Trả thẳng câu hỏi làm rõ của router.
        case CLARIFICATION -> new AnswerResult(
                new GeneratedChatAnswer(clarification(decision), null), List.of(), null);

        // Chạy SQL cố định theo enum, rồi chỉ nhờ Gemini diễn giải kết quả đó thành câu trả lời.
        case BUSINESS_DATA -> businessAnswer(question, username, decision, history);

        // Tìm chunk vector đã qua lọc quyền, chỉ trả lời khi thực sự có evidence.
        case MEDICAL_RAG -> medicalAnswer(question, roleCode, conversation.user().getId(), history);

        // Chạy SQL nghiệp vụ trước, dùng kết quả đó cải thiện retrieval, rồi trả lời từ cả 2 nguồn.
        case HYBRID -> hybridAnswer(
                question, username, roleCode, conversation.user().getId(), decision, history);
    };

    // 5. Row ASSISTANT chỉ được lưu SAU KHI mọi lệnh gọi ngoài cần thiết đã thành công.
    //    Nếu Gemini/Qdrant lỗi trước đó, row USER từ prepare() vẫn còn mà không có row này.
    ChatMessage savedMessage = chatSessionService.saveAssistantMessage(
            conversation.session(), decision.route().name(), result.answer());

    // 6. Dựng response CHỈ để trả API. Sources/warning trả về ngay đây nhưng không lưu vào ChatMessage.
    return response(
            conversation.session().getId(),
            savedMessage.getId(),
            decision.route(),
            result.answer(),
            result.sources(),
            result.warning());
}
```

Bất biến quan trọng: model chỉ được phân loại chứ không thể mở rộng phạm vi vận hành của
backend. Quyết định `BUSINESS_DATA` vẫn phải đi qua đúng 1 `switch` hữu hạn của Java; quyết định
`MEDICAL_RAG` vẫn phải qua lớp lọc quyền trước khi search Qdrant.

```java
private AnswerResult medicalAnswer(String question, String roleCode, Long userId, String history) {
    // Thêm 1 đoạn lịch sử follow-up có giới hạn vào câu truy vấn vector. Đây là ngữ cảnh cho
    // retrieval, không phải toàn bộ lịch sử prompt gửi model.
    MedicalRetrievalResult result = medicalRagService.retrieve(
            contextualRetrievalQuery(question, history), roleCode, userId);

    if (result.isEmpty()) {
        // Không yêu cầu Gemini trả lời câu hỏi y khoa khi không có evidence đã duyệt nào.
        return new AnswerResult(new GeneratedChatAnswer(
                "I could not find sufficient approved medical evidence in the knowledge base.", null),
                List.of(), MEDICAL_WARNING);
    }

    // `result.context()` chỉ chứa chunk đã qua cả bộ lọc similarity lẫn bộ lọc phạm vi của Qdrant.
    return new AnswerResult(
            aiGateway.answerMedical(question, result.context(), history),
            result.sources(),
            MEDICAL_WARNING);
}

private AnswerResult hybridAnswer(
        String question, String username, String roleCode, Long userId,
        ChatRoutingDecision decision, String history) {
    // Giá trị report/examination lấy từ SQL MySQL đã kiểm soát, TRƯỚC khi có bất kỳ câu trả lời LLM nào.
    BusinessQueryResult business = businessDataQueryService.execute(decision, username);

    // Đưa dữ liệu thực tế đó vào câu truy vấn retrieval khiến việc tìm guideline trở nên đặc thù
    // theo đúng report, chẩn đoán hoặc độ KL đã chọn.
    MedicalRetrievalResult medical = medicalRagService.retrieve(
            contextualRetrievalQuery(question + "\nHEALTHSYNC DATA:\n" + business.context(), history),
            roleCode, userId);

    if (medical.isEmpty()) {
        // Người dùng vẫn nhận đúng dữ liệu vận hành; cảnh báo vẫn giữ vì câu hỏi vốn có tính y khoa.
        return new AnswerResult(
                aiGateway.answerBusiness(question, business.context(), history),
                business.sources(), MEDICAL_WARNING);
    }

    // Giữ nguồn database trước, nối thêm nguồn evidence lâm sàng vào sau.
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
    // Tra đúng User thật của người gọi. Không tìm thấy user thì 404, không có nhánh anonymous nào.
    User user = requireUser(username);

    // Câu hỏi đầu tiên tạo session mới và tự đặt tiêu đề theo câu hỏi đó.
    // Session đã có từ trước bắt buộc phải thuộc ĐÚNG người dùng này.
    ChatSession session = sessionId == null
            ? createSession(user, titleFromQuestion(question), null)
            : requireOwnedSession(sessionId, user);

    // Hội thoại đã đóng vẫn giữ lịch sử nhưng không nhận tin nhắn mới.
    if (!session.isActive()) {
        throw new IllegalArgumentException("Chat session is inactive");
    }

    // Query trả về mới -> cũ. conversationContext() đảo ngược lại các tin nhắn đã chọn
    // để Gemini nhận đúng thứ tự thời gian `USER:` / `ASSISTANT:`.
    String history = conversationContext(session, chatMessageRepository
            .findTop20BySessionIdOrderByCreatedAtDescIdDesc(session.getId()));

    // Lưu SAU KHI đã dựng xong lịch sử: câu hỏi hiện tại được gửi riêng cho Gemini bởi caller.
    saveMessage(session, ChatMessageRole.USER, question.trim(), null, null);

    // Session được tạo từ luồng cũ/mặc định sẽ được đặt tiêu đề ngay ở câu hỏi thật đầu tiên.
    if (DEFAULT_TITLE.equals(session.getTitle())) {
        session.setTitle(titleFromQuestion(question));
    }

    // Đưa session này lên đầu danh sách session của chủ sở hữu (mới nhất).
    touch(session);

    // Mang session/user/history sang cho orchestrator, tất cả nằm trong phạm vi transaction này.
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

        // Dừng TRƯỚC KHI thêm 1 tin nhắn cũ hơn sẽ khiến tổng vượt quá 12.000 ký tự.
        // `!selected.isEmpty()` nghĩa là tin nhắn mới nhất luôn được giữ dù nó rất dài.
        if (!selected.isEmpty() && characters + messageLength > MAX_HISTORY_CHARACTERS) {
            break;
        }
        selected.add(message);
        characters += messageLength;
    }

    // Repository trả về theo thứ tự mới nhất trước; prompt cần thứ tự thời gian thuận.
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

    // Dòng này KHÔNG được lưu thành ChatMessage(SYSTEM) - nó chỉ tồn tại trong prompt gửi model.
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
            // Role được chèn thẳng vào chỉ dẫn cho router. Không lấy từ text của user.
            .system(ROUTER_PROMPT.formatted(roleCode))
            // Lịch sử được ghi nhãn rõ là ngữ cảnh follow-up, câu hỏi được phân tách riêng.
            .user(conversationPrompt(question, conversationHistory))
            // Thực thi request tới provider Google GenAI đã cấu hình.
            .call()
            // Spring AI tự map response có cấu trúc vào Java record.
            .entity(ChatRoutingDecision.class);
}

private GeneratedChatAnswer answer(String question, String context, String conversationHistory) {
    ChatResponse response = chatClient.prompt()
            // ANSWER_RULES dặn chỉ dùng context, bỏ qua mọi chỉ dẫn nằm bên trong nó.
            .system(ANSWER_RULES)
            // Context chỉ được Java nối vào SAU dấu phân cách lịch sử/câu hỏi hiện tại.
            .user(conversationPrompt(question, conversationHistory) + "\n\n" + context)
            .call()
            // Khác route(): giữ nguyên response thô để lấy usage metadata.
            .chatResponse();

    if (response == null || response.getResult() == null) {
        // Kết quả provider thành công về mặt cú pháp nhưng rỗng - không để nó biến thành null.
        return new GeneratedChatAnswer("The AI provider returned an empty response.", null);
    }

    // Dữ liệu usage là tuỳ chọn, khác nhau giữa các provider, nên kiểm tra null ở từng cấp.
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
    // Tránh truyền thẳng Java null làm nội dung literal gửi cho model.
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
    // Không tin danh tính do router trả về; đọc lại current user và role thẳng từ MySQL.
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    String role = user.getRole().getCode();

    // Thiếu intent thì không thể chọn query mặc định nào cả.
    BusinessQueryIntent intent = decision.businessIntent() == null
            ? BusinessQueryIntent.UNKNOWN : decision.businessIntent();

    // Chi tiết lâm sàng bị chặn cho ADMIN TRƯỚC KHI bất kỳ SQL nào được gửi đi.
    if ("ADMIN".equals(role) && isClinical(intent)) {
        throw new UnauthorizedAccessException("Administrators cannot access clinical examination details");
    }

    // Chuyển đổi ngày và bind tham số có tên chỉ thực hiện 1 lần cho mọi nhánh.
    DateRange range = dateRange(decision, intent);
    MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("from", range.from())
            .addValue("to", range.to())
            .addValue("userId", user.getId())
            .addValue("entityId", decision.entityId());
    boolean scopedDoctor = "DOCTOR".equals(role);

    // Đây chính là whitelist SQL. Không có chuỗi SQL nào do router tạo ra từng được thực thi.
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
    // Ưu tiên visit_time; dữ liệu cũ có thể chỉ có created_at.
    String examinationTime = "COALESCE(e.visit_time, e.created_at)";
    String sql = "SELECT e.id AS examination_id, e.encounter_code, p.patient_code, "
            + "p.full_name AS patient_name, " + examinationTime + " AS visit_time, "
            + "e.status, e.priority FROM examinations e "
            + "JOIN patients p ON p.patient_code = e.patient_id "
            + "WHERE " + examinationTime + " >= :from AND " + examinationTime + " < :to"
            // Bác sĩ chỉ thấy row được gán cho mình. Trưởng khoa CỐ Ý không bị giới hạn ở đây.
            + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
            // LIMIT là ranh giới dữ liệu/kiểm soát cứng, không phải tuỳ chọn theo yêu cầu của LLM.
            + " ORDER BY " + examinationTime + " DESC, e.id DESC LIMIT 10";

    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
    // Provider chỉ nhận dữ liệu kết quả đã serialize kèm giới hạn rõ ràng, không có quyền truy cập JDBC.
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
    // Tạo filter TRƯỚC KHI gửi request vector. Không để câu truy vấn trả về chunk chưa được
    // phép rồi trông cậy vào 1 bước lọc Java sau đó.
    String scopeFilter = scopeFilter(roleCode, userId);
    SearchRequest request = SearchRequest.builder()
            .query(question)
            .topK(properties.retrievalTopK())
            .similarityThreshold(properties.similarityThreshold())
            .filterExpression(scopeFilter)
            .build();

    // Spring AI tự embedding câu hỏi qua model Ollama đã cấu hình rồi gọi Qdrant.
    List<Document> matches = vectorStore.similaritySearch(request);
    if (matches == null || matches.isEmpty()) {
        return new MedicalRetrievalResult("", List.of());
    }

    StringBuilder context = new StringBuilder();
    Map<String, ChatSourceResponse> uniqueSources = new LinkedHashMap<>();
    for (Document document : matches) {
        // Giới hạn cứng số ký tự để bảo vệ kích thước prompt cuối cùng gửi Gemini.
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

        // Nhiều chunk từ cùng 1 file có thể cùng nằm trong prompt; API chỉ trả về đúng 1 source cho chúng.
        String key = reference == null ? title : reference;
        uniqueSources.putIfAbsent(key,
                new ChatSourceResponse(key, title,
                        stringValue(metadata.get("sourceType"), "MEDICAL_DOCUMENT"), reference, score));
    }
    return new MedicalRetrievalResult(context.toString(), new ArrayList<>(uniqueSources.values()));
}

private String scopeFilter(String roleCode, Long userId) {
    // Cờ này do indexer gắn vào MỌI chunk. Chunk chưa publish thì không khớp được với role nào cả.
    String published = "publicationStatus == 'PUBLISHED' && ";
    if ("ADMIN".equals(roleCode)) {
        return published + "(accessScope == 'ALL' || accessScope == 'ADMIN')";
    }
    if ("HEAD_OF_DEPARTMENT".equals(roleCode) || "DEPARTMENT_HEAD".equals(roleCode)) {
        // Role cấp khoa không tự động được cấp quyền xem MỌI report riêng tư.
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
    // Reader tự chọn PDFBox, UTF-8 TextReader, hoặc Tika dựa theo type/tên file.
    List<Document> documents = documentReader.read(bytes, originalName, contentType);

    // Bỏ qua các artifact null/không phải text, gộp các đoạn đọc được thành input cho classifier.
    String content = documents.stream()
            .filter(document -> document != null && document.isText())
            .map(Document::getText)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + "\n\n" + right)
            .orElseThrow(() -> new IllegalArgumentException("No readable text was found in the document"));

    // File ngắn gửi nguyên văn; file lớn chỉ gửi đúng 3 vị trí đại diện.
    String samples = sample(content, properties.medicalValidationSampleChars());
    MedicalDocumentAssessment assessment = aiChatGateway.assessMedicalDocument(samples);

    // Fail closed: provider lỗi/null, không rõ ràng, thiếu confidence, hoặc dưới ngưỡng đều bị từ chối.
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
    // Kiểm tra rẻ tiền trước, trước khi phải nạp có thể tới 50 MiB vào bộ nhớ.
    validateFile(file);
    byte[] bytes = readBytes(file);

    // Cùng 1 chuỗi byte không được phép tồn tại thành 2 knowledge source file khác nhau.
    String checksum = sha256(bytes);
    if (repository.existsBySourceKey("file:" + checksum)) {
        throw new IllegalArgumentException("This document has already been uploaded");
    }

    // Cổng kiểm tra ngữ nghĩa đồng bộ. Ném lỗi TRƯỚC khi có bất kỳ side-effect nào lên đĩa/DB nếu bị từ chối.
    medicalDocumentValidator.validate(bytes, file.getOriginalFilename(), file.getContentType());
    User user = findUser(username);

    // Lưu dưới tên file ngẫu nhiên phía server; không bao giờ tin đường dẫn client gửi lên làm path server.
    Path storagePath = storeFile(bytes, file.getOriginalFilename());

    KnowledgeDocument document = new KnowledgeDocument();
    document.setSourceKey("file:" + checksum);       // đồng thời là key để xoá/group trong Qdrant
    document.setTitle(normalizeTitle(title, file.getOriginalFilename()));
    document.setSourceType(KnowledgeSourceType.FILE);
    document.setOriginalName(safeFileName(file.getOriginalFilename()));
    document.setContentType(file.getContentType());
    document.setStoragePath(storagePath.toString());
    document.setChecksum(checksum);
    document.setAccessScope(scope == null ? KnowledgeAccessScope.ALL : scope);
    document.setUploadedBy(user);                     // cần cho bộ lọc retrieval theo OWNER
    document = repository.save(document);             // status mặc định là PENDING (định nghĩa trong entity)

    // Worker chỉ lắng nghe SAU KHI transaction này commit, nên chắc chắn tìm thấy metadata/file.
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
    // Tuần tự hoá index/delete cho cùng 1 document ID, trong phạm vi 1 tiến trình ứng dụng.
    operationCoordinator.executeExclusively(event.documentId(), () -> {
        indexExclusively(event);
        return null;
    });
}

private void indexExclusively(KnowledgeIndexRequestedEvent event) {
    log.info("Starting knowledge indexing for document {}", event.documentId());
    try {
        // Transaction mới giúp trạng thái PROCESSING hiển thị ngay lập tức.
        KnowledgeDocument knowledge = stateService.markProcessing(event.documentId());
        if (knowledge == null) {
            // Có thể việc xoá đã hoàn tất trước khi job async xếp hàng kịp bắt đầu.
            log.info("Skipping indexing because knowledge document {} was deleted", event.documentId());
            return;
        }

        // Đọc lại file vật lý gốc, thay mọi metadata do parser sinh ra bằng metadata an toàn về quyền truy cập.
        List<Document> parsed = documentReader.read(knowledge);
        List<Document> enriched = parsed.stream()
                .filter(document -> document != null && document.isText())
                .map(document -> new Document(document.getText(), metadata(knowledge)))
                .toList();
        List<Document> chunks = splitter.apply(enriched);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No readable text was found in the document");
        }

        // Reindex là THAY THẾ chứ không phải NỐI THÊM: xoá hết chunk cũ của 1 nguồn logic trước.
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("sourceKey", knowledge.getSourceKey()).build());
        // Spring AI tự embedding từng chunk bằng BGE-M3, rồi ghi point/vector + metadata vào Qdrant.
        vectorStore.add(chunks);

        // Việc xoá có thể xảy ra giữa lúc add() và lúc cập nhật status. Không được để sót chunk mồ côi trong Qdrant.
        if (!stateService.markIndexed(event.documentId(), chunks.size())) {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq("sourceKey", knowledge.getSourceKey()).build());
            log.info("Discarded indexed chunks because knowledge document {} was deleted", event.documentId());
            return;
        }
        log.info("Knowledge indexing completed for document {} with {} chunks",
                event.documentId(), chunks.size());
    } catch (RuntimeException | LinkageError exception) {
        // 1 job lỗi vẫn quan sát/thử lại được qua metadata, thay vì biến mất âm thầm.
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

    // Dùng lại đúng 1 row metadata cho mỗi report. Cơ chế sửa chữa theo lịch cũng gọi đúng method này.
    KnowledgeDocument knowledge = repository.findBySourceKey(sourceKey)
            .orElseGet(KnowledgeDocument::new);
    knowledge.setSourceKey(sourceKey);
    knowledge.setTitle("Approved clinical report " + row.reportId());
    knowledge.setSourceType(KnowledgeSourceType.REPORT);
    knowledge.setAccessScope(KnowledgeAccessScope.OWNER);
    knowledge.setStatus(KnowledgeDocumentStatus.PROCESSING);
    // Marker giúp job sync theo lịch phát hiện metadata report còn ở định dạng cũ.
    knowledge.setChecksum("report-metadata-v2");
    knowledge.setUploadedBy(userRepository.findById(row.ownerUserId()).orElse(null));
    knowledge = repository.save(knowledge);

    // Đây là toàn bộ text của report được embedding. CỐ Ý loại trừ PII bệnh nhân và mọi đường dẫn ảnh.
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
        // Nhờ metadata này, MedicalRagService cho phép đúng bác sĩ này lấy được report riêng của chủ sở hữu.
        metadata.put("assignedDoctorUserId", row.assignedDoctorUserId());
    }
    metadata.put("publicationStatus", "PUBLISHED");
    List<Document> chunks = splitter.apply(List.of(new Document(text, metadata)));

    try {
        // Cùng ngữ nghĩa "thay thế" như khi index file/URL.
        vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", sourceKey).build());
        vectorStore.add(chunks);
        knowledge.setChunkCount(chunks.size());
        knowledge.setStatus(KnowledgeDocumentStatus.INDEXED);
        knowledge.setIndexedAt(LocalDateTime.now());
        knowledge.setErrorMessage(null);
    } catch (RuntimeException exception) {
        // Khác với nơi gọi từ sự kiện PDF vừa tạo: method này ghi nhận FAILED TRƯỚC khi ném lại lỗi.
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
    // Từ chối URL không hỗ trợ/riêng tư TRƯỚC KHI mở bất kỳ kết nối đi ra ngoài nào.
    URI uri = validatePublicUrl(request.url());

    // Danh tính của URL là text URL đã chuẩn hoá, khác với file (danh tính là checksum của byte).
    String sourceKey = "url:" + sha256(uri.normalize().toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (repository.existsBySourceKey(sourceKey)) {
        throw new IllegalArgumentException("This URL has already been added");
    }

    // RestClient đã tắt redirect và download() tự ép trần dung lượng/yêu cầu HTTP thành công.
    byte[] bytes = download(uri);
    // Nội dung URL được coi là input HTML, vẫn phải qua đúng bộ phân loại y khoa như file thường.
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
    document.setChecksum(sha256(bytes));             // giữ checksum nội dung để phục vụ chẩn đoán
    document.setAccessScope(request.accessScope() == null ? KnowledgeAccessScope.ALL : request.accessScope());
    document.setUploadedBy(findUser(username));
    document = repository.save(document);
    requestIndex(document.getId());
    return toResponse(document);
}

private URI validatePublicUrl(String rawUrl) {
    URI uri = URI.create(rawUrl.trim());

    // URI tương đối, giao thức không phải HTTP, thiếu host, hoặc có dạng user-info đều không bao giờ được chấp nhận.
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Only absolute HTTP or HTTPS URLs are supported");
    }
    try {
        // Resolve TOÀN BỘ bản ghi A/AAAA. Chỉ cần 1 kết quả riêng tư/nội bộ là từ chối cả request.
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
    // Metadata đã lưu bền cho reader biết byte gốc nằm ở đâu và kiểu file gốc là gì.
    return read(new FileSystemResource(knowledge.getStoragePath()),
            knowledge.getOriginalName(), knowledge.getContentType());
}

public List<Document> read(byte[] bytes, String originalName, String contentType) {
    // ByteArrayResource bình thường không có tên file; resource tự viết khôi phục lại tên để Tika nhận diện được.
    Resource resource = new NamedByteArrayResource(bytes, originalName);
    return read(resource, originalName, contentType);
}

private List<Document> read(Resource resource, String originalName, String contentType) {
    if (isType(originalName, contentType, "pdf", "application/pdf")) {
        // PDFBox chỉ trích xuất text. Không OCR các trang PDF scan thuần ảnh.
        return readPdf(resource);
    }
    if (isType(originalName, contentType, "txt", "text/plain")) {
        // Ép UTF-8 rõ ràng để tránh khác biệt encoding mặc định giữa các platform.
        TextReader reader = new TextReader(resource);
        reader.setCharset(StandardCharsets.UTF_8);
        return reader.get();
    }
    // DOC/DOCX và HTML đã lưu đều đi qua Apache Tika thông qua reader của Spring AI.
    return new TikaDocumentReader(resource).get();
}

private boolean isType(String originalName, String contentType, String extension, String mediaType) {
    // Media type khai báo rõ ràng luôn được ưu tiên; extension chỉ là phương án dự phòng tương thích.
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
    // Lock của coordinator ở tầng ngoài đã tuần tự hoá delete với worker cho cùng ID này rồi.
    KnowledgeDocument document = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));

    // sourceKey được gắn vào MỌI chunk, nên lệnh này xoá trọn vẹn 1 nguồn logic trong Qdrant.
    vectorStore.delete(new FilterExpressionBuilder().eq("sourceKey", document.getSourceKey()).build());

    // Xoá metadata SAU KHI đã xoá vector; các ràng buộc FK/vòng đời user do schema tự lo.
    repository.delete(document);

    // Nguồn FILE/URL thì xoá luôn byte gốc. REPORT không có storage path nên bước này thành no-op.
    deleteStoredFile(document);
}

private void deleteStoredFile(KnowledgeDocument document) {
    if (document.getStoragePath() == null) {
        return;
    }
    try {
        Path root = Path.of(properties.knowledgeDir()).toAbsolutePath().normalize();
        Path storedFile = Path.of(document.getStoragePath()).toAbsolutePath().normalize();
        // Dữ liệu DB hỏng không được phép biến 1 lệnh xoá qua API thành xoá tuỳ ý file bất kỳ trên server.
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
        // Builder đã tự chọn sẵn provider/model từ cấu hình YAML spring.ai.
        // Bean này được inject vào SpringAiChatGateway theo type.
        return builder.build();
    }

    @Bean
    TokenTextSplitter medicalKnowledgeSplitter() {
        return TokenTextSplitter.builder()
                // Kích thước chunk ngữ nghĩa mục tiêu gửi cho model embedding.
                .withChunkSize(700)
                // Tránh các mảnh nhỏ lẻ còn sót lại ở cuối nếu có thể.
                .withMinChunkSizeChars(250)
                // Mảnh nhỏ hơn giá trị này thì được giữ/loại tuỳ theo logic splitter, không tự embedding riêng lẻ.
                .withMinChunkLengthToEmbed(20)
                // Ngăn 1 nguồn dữ liệu hỏng tạo ra số lượng ghi vector không giới hạn.
                .withMaxNumChunks(10_000)
                // Giữ lại dấu phân cách đoạn văn/câu để bảo toàn ranh giới ngữ nghĩa.
                .withKeepSeparator(true)
                .build();
    }

    @Bean
    RestClient knowledgeRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                // 1 redirect có thể đưa 1 URL công khai đã validate chuyển hướng sang địa chỉ riêng tư.
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
            // Prompt classifier nghiêm ngặt, khác hẳn prompt trả lời thường.
            .system(MEDICAL_DOCUMENT_CLASSIFIER_PROMPT)
            // Mẫu vẫn là dữ liệu người dùng; prompt nói rõ không được tuân theo chỉ dẫn nằm trong đó.
            .user("Document samples:\n" + sampledContent)
            .call()
            // JSON từ provider được decode thẳng vào record Boolean/Double/reason.
            .entity(MedicalDocumentAssessment.class);
}

@Override
public GeneratedChatAnswer answerBusiness(
        String question, String businessContext, String conversationHistory) {
    // Chỉ thêm nhãn ở đây; `answer()` lo phần gọi provider/response rỗng/usage.
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
    // Cố ý giữ nguyên nguồn gốc từng loại context thay vì trộn 2 loại nguồn lại ngay trong Java.
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
    // @Valid kiểm tra câu hỏi trước khi gọi service; username của Principal lấy từ SecurityContext của JWT.
    return ResponseEntity.ok(chatOrchestratorService.ask(
            request.sessionId(), request.question(), principal.getName()));
}

@PostMapping("/sessions")
@PreAuthorize(CHAT_ACCESS)
public ResponseEntity<ChatSessionResponse> createSession(
        @Valid @RequestBody CreateChatSessionRequest request, Principal principal) {
    // Đây là endpoint chat DUY NHẤT tạo resource mới nên trả 201.
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
    // Service tự kiểm tra quyền sở hữu TRƯỚC KHI truy vấn tin nhắn.
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
    // ACCEPTED nghĩa là metadata nguồn đã được lưu bền; việc index vẫn có thể là PENDING/FAILED sau đó.
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
    // Service SQL chỉ nhận decision dưới dạng enum/ID/ngày đã đánh kiểu, không nhận gì khác.
    BusinessQueryResult result = businessDataQueryService.execute(decision, username);
    // Model chỉ nhận context đã kiểm soát và serialize sẵn, không có quyền dùng JdbcTemplate hay tự sinh SQL.
    return new AnswerResult(
            aiGateway.answerBusiness(question, result.context(), history), result.sources(), null);
}

private ChatRoutingDecision normalize(ChatRoutingDecision decision) {
    if (decision == null || decision.route() == null) {
        // Router thất bại không tạo được cấu trúc thì hạ xuống thành câu hỏi làm rõ, thay vì dereference null.
        return new ChatRoutingDecision(ChatRoute.CLARIFICATION, null, null, null, null,
                "Could you clarify whether you need HealthSync data or medical information?");
    }
    return decision;
}

private String clarification(ChatRoutingDecision decision) {
    // Model có thể bỏ trống câu hỏi làm rõ. Text mặc định giữ cho response API không bao giờ rỗng.
    return decision.clarificationQuestion() == null || decision.clarificationQuestion().isBlank()
            ? "Could you provide more detail about the information you need?"
            : decision.clarificationQuestion();
}

private String contextualRetrievalQuery(String question, String history) {
    if (history == null || history.isBlank()) {
        return question;
    }
    // Giữ lại phần đuôi vì các lượt gần nhất liên quan nhất, đồng thời giới hạn input embedding gửi Qdrant.
    int start = Math.max(0, history.length() - 2_000);
    return history.substring(start) + "\nCURRENT QUESTION: " + question;
}

private ChatAnswerResponse response(
        Long sessionId, Long messageId, ChatRoute route, GeneratedChatAnswer answer,
        List<ChatSourceResponse> sources, String warning) {
    return new ChatAnswerResponse(
            sessionId, messageId, route.name(), answer.content(),
            // Ngăn thay đổi list mutable về sau làm đổi luôn response đã trả ra.
            List.copyOf(sources), warning,
            // Đây là thời điểm response của ứng dụng, không phải thời điểm của model provider.
            LocalDateTime.now(), answer.tokensUsed());
}
```

### 20.6 Remaining `ChatSessionService` helpers

Source: `ChatSessionService.java:46-88`, `110-178`, `215-250`.

```java
@Transactional
public ChatSessionResponse update(Long sessionId, UpdateChatSessionRequest request, String username) {
    // JSON `{}` không hợp lệ dù từng field riêng lẻ đều là tuỳ chọn.
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
    // Khác với tin nhắn USER, row ASSISTANT còn giữ thêm route và số liệu token của provider.
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
        // Chỉ bác sĩ được gán hoặc trưởng khoa mới được gắn ngữ cảnh examination lâm sàng riêng tư vào chat.
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
    // Chỉ gộp khoảng trắng ở tiêu đề; tin nhắn đã lưu vẫn giữ nguyên khoảng trắng gốc bên trong của người dùng.
    String normalized = question == null ? DEFAULT_TITLE : question.trim().replaceAll("\\s+", " ");
    return normalized.isEmpty() ? DEFAULT_TITLE : truncateTitle(normalized);
}

private String truncateTitle(String title) {
    // 157 cộng `...` để đảm bảo đúng giới hạn tối đa 160 ký tự của database/DTO.
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
        // Response GIỐNG HỆT NHAU cho cả 2 trường hợp: không có row, hoặc có row nhưng bác sĩ khác sở hữu.
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
        // Truy vấn tổng hợp không phải "hôm nay" mà không có ngày cụ thể thì lấy trọn khung lịch sử đã biết.
        return new DateRange(LocalDate.of(1970, 1, 1).atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay());
    }
    LocalDate from = parseDate(decision.dateFrom(), LocalDate.now());
    LocalDate to = parseDate(decision.dateTo(), from);
    if (to.isBefore(from)) {
        throw new IllegalArgumentException("dateTo must not be before dateFrom");
    }
    // SQL dùng khoảng [from, ngày-sau-to), tránh mập mờ nano-giây ở cuối ngày.
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
        // Metadata của document loại REPORT không có file upload nào cả.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
    try {
        // toRealPath tự resolve symlink TRƯỚC khi kiểm tra prefix, ngăn symlink thoát khỏi thư mục knowledge gốc.
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
        // Tránh tiết lộ chi tiết path/tồn-tại-hay-không cho phía gọi endpoint.
        throw new ResourceNotFoundException("Knowledge document file not found");
    }
}

@Transactional
public KnowledgeDocumentResponse reindex(Long id) {
    KnowledgeDocument document = findDocument(id);
    document.setStatus(KnowledgeDocumentStatus.PENDING);
    document.setErrorMessage(null);
    repository.save(document);
    // Worker sẽ tự xoá vector cũ theo sourceKey trước khi thêm vector mới.
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
    // Transaction mới nghĩa là việc cập nhật status không phải chờ tác vụ parser/Ollama/Qdrant vốn chậm.
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
        // Worker sẽ dùng giá trị false này để xoá luôn các chunk vừa thêm cho 1 nguồn đã bị xoá.
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
        // Không tự tạo lại 1 row đã bị xoá trong lúc tác vụ async đang thất bại.
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
    // floorMod giúp cả giá trị hash âm cũng thành chỉ số mảng hợp lệ.
    ReentrantLock lock = locks[Math.floorMod(Long.hashCode(documentId), LOCK_COUNT)];
    lock.lock();
    try {
        return operation.get();
    } finally {
        // Luôn giải phóng lock, kể cả khi vector-store/database ném exception.
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
            // Dùng lại nguyên vẹn luồng single-file: quy tắc duplicate, validator, lưu trữ và sự kiện giữ nguyên y hệt.
            KnowledgeDocumentResponse document = ingestionService.upload(file, null, scope, username);
            items.add(new KnowledgeBatchUploadItemResponse(originalName, true, document, null));
            accepted++;
        } catch (IllegalArgumentException exception) {
            // Kết quả không hợp lệ/không phải y khoa/trùng lặp là tình huống đã lường trước, không huỷ các file còn lại.
            items.add(new KnowledgeBatchUploadItemResponse(
                    originalName, false, null, exception.getMessage()));
        } catch (RuntimeException exception) {
            // Giấu chi tiết nội bộ backend không mong muốn khỏi response batch, nhưng vẫn ghi log server.
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
    // Dù controller trả response 202, lệnh gọi tay trực tiếp này vẫn index xong rồi mới return.
    return index(row);
}

@Async("taskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void syncGeneratedReport(ReportKnowledgeSyncRequestedEvent event) {
    try {
        // Sự kiện PDF chỉ mang theo ID; đọc SQL mới sẽ thấy đúng các trường report cuối cùng đã commit.
        index(loadReport(event.reportId()));
    } catch (RuntimeException exception) {
        // Việc tạo PDF vẫn coi là thành công dù hạ tầng vector tạm thời không sẵn sàng.
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
            // 1 report lỗi không được phép chặn việc đối soát các report còn lại phía sau.
            log.error("Could not synchronize report {} to Qdrant", reportId, exception);
        }
    }
}

private ReportKnowledge loadReport(Long reportId) {
    // SQL tổng hợp cố định, chỉ SELECT đúng những field đã được duyệt để đưa vào text knowledge của report.
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
    // Phân loại đồng bộ do provider thực hiện, chạy trước khi 1 nguồn được phép lưu lại.
    MedicalDocumentAssessment assessMedicalDocument(String sampledContent);

    // Trả về route/intent/ID có cấu trúc, không phải câu trả lời dạng ngôn ngữ tự nhiên.
    ChatRoutingDecision route(String question, String roleCode, String conversationHistory);

    // 3 biến thể theo nguồn gốc context khác nhau, đều do SpringAiChatGateway triển khai.
    GeneratedChatAnswer answerBusiness(String question, String businessContext, String conversationHistory);
    GeneratedChatAnswer answerMedical(String question, String medicalContext, String conversationHistory);
    GeneratedChatAnswer answerHybrid(
            String question, String businessContext, String medicalContext, String conversationHistory);
}

public enum ChatRoute {
    BUSINESS_DATA,  // query MySQL cố định rồi diễn giải thành ngôn ngữ tự nhiên
    MEDICAL_RAG,   // evidence từ vector rồi trả lời lâm sàng
    HYBRID,         // dữ liệu MySQL đã kiểm soát cộng thêm evidence từ vector
    CLARIFICATION   // câu hỏi follow-up cục bộ; không cần lấy dữ liệu gì cả
}

public record ChatRoutingDecision(
        ChatRoute route,                 // chọn nhánh switch trong ChatOrchestratorService
        BusinessQueryIntent businessIntent, // chọn method SQL cố định khi cần dữ liệu nghiệp vụ
        Long entityId,                   // ID report/examination, không bao giờ là 1 mảnh SQL tuỳ ý
        String dateFrom,                 // kỳ vọng định dạng ISO yyyy-MM-dd
        String dateTo,                   // kỳ vọng định dạng ISO yyyy-MM-dd
        String clarificationQuestion) {  // chỉ dùng cho CLARIFICATION
}

public record GeneratedChatAnswer(
        String content,      // text của assistant, lưu vào chat_messages.content
        Integer tokensUsed) {// tổng số token của provider, có thể null
}

public record MedicalDocumentAssessment(
        Boolean medical,    // phải đúng Boolean.TRUE mới chấp nhận 1 nguồn
        Double confidence,  // phải đạt ngưỡng đã cấu hình
        String reason) {    // trả kèm trong thông báo từ chối nếu có
}

public record BusinessQueryResult(
        String context,                 // text do backend tự tạo, đưa cho model trả lời
        List<ChatSourceResponse> sources) { }

public record MedicalRetrievalResult(
        String context,                 // text các chunk Qdrant đã lọc theo role, nối lại với nhau
        List<ChatSourceResponse> sources) {
    public boolean isEmpty() {
        // Orchestrator dựa vào sources (không chỉ text rỗng) để quyết định có đủ evidence hay không.
        return sources == null || sources.isEmpty();
    }
}

public record KnowledgeIndexRequestedEvent(Long documentId) { }
// Chỉ mang theo ID trong database; worker tự đọc lại trạng thái mới nhất thay vì nhận entity đã cũ.

public record ReportKnowledgeSyncRequestedEvent(Long reportId) { }
// Cùng 1 kiểu sự kiện dùng cho cả report PDF vừa tạo lẫn report được trả lại (reuse).
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
// Hiện tại chỉ USER và ASSISTANT được lưu vào DB. SYSTEM hiện chỉ xuất hiện trong chuỗi lịch sử prompt.

public enum KnowledgeAccessScope { ALL, DOCTOR, ADMIN, OWNER }
// Các giá trị này được copy nguyên văn vào metadata/filter expression của Qdrant.

public enum KnowledgeDocumentStatus { PENDING, PROCESSING, INDEXED, FAILED }
// State machine của việc index bất đồng bộ, quan sát được qua danh sách metadata.

public enum KnowledgeSourceType { FILE, URL, REPORT }
// REPORT nghĩa là text được tạo ra từ dữ liệu quan hệ; do đó không đảm bảo có file gốc đã upload.
```

```java
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // khoá chính do database tự sinh

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // chủ sở hữu bắt buộc; mọi query repository luôn giới hạn theo ID này

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id")
    private Examination examination; // liên kết ca khám lâm sàng, không bắt buộc

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
    private ChatSession session; // cascade delete được định nghĩa ở FK trong migration SQL
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ChatMessageRole role;
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(name = "route", length = 30)
    private String route; // null với USER, là tên enum route với ASSISTANT
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
    private String sourceKey; // danh tính dùng chung để filter/xoá trong Qdrant
    @Column(name = "title", nullable = false, length = 255)
    private String title; // nhãn nguồn an toàn để hiển thị/đưa vào prompt
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private KnowledgeSourceType sourceType;
    @Column(name = "source_url", length = 2048)
    private String sourceUrl; // URL công khai gốc; null với FILE/REPORT
    @Column(name = "original_name", length = 255)
    private String originalName; // chỉ tên file, không bao giờ dùng làm storage path đáng tin
    @Column(name = "content_type", length = 150)
    private String contentType;
    @Column(name = "storage_path", length = 512)
    private String storagePath; // path vật lý chỉ server biết; null với REPORT
    @Column(name = "checksum", length = 64)
    private String checksum; // SHA-256 của byte file, hoặc marker phiên bản metadata với report
    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 20)
    private KnowledgeAccessScope accessScope = KnowledgeAccessScope.ALL;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KnowledgeDocumentStatus status = KnowledgeDocumentStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy; // nguồn metadata OWNER khi file/URL ở chế độ riêng tư
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
    // Spring Data tự sinh WHERE id = ? AND user_id = ?, đây chính là ranh giới sở hữu của session.
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);
    // Kết quả phân trang sắp xếp hoạt động mới nhất lên trước, ID phá thế bằng nhau khi trùng timestamp.
    Page<ChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);
}

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Lịch sử phân trang trả về cho API theo đúng thứ tự thời gian thuận.
    Page<ChatMessage> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId, Pageable pageable);
    // Lịch sử cho prompt lấy 20 tin mới nhất trước, để Java tự cắt bớt theo giới hạn ký tự cho phù hợp.
    List<ChatMessage> findTop20BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);
}

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    Optional<KnowledgeDocument> findBySourceKey(String sourceKey);
    boolean existsBySourceKey(String sourceKey); // chặn trùng lặp chính xác cùng 1 danh tính file/URL
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
// JwtAuthenticationFilter.doFilterInternal, rút gọn chỉ để bỏ bớt import không liên quan.
String authHeader = request.getHeader("Authorization");
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response); // controller sẽ tự chặn sau nếu endpoint có bảo vệ
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
