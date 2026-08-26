# Hướng Dẫn Code: Xác Minh Examination Đến Xuất Report

Tài liệu này giải thích luồng bác sĩ/trưởng khoa xác nhận kết quả AI, chuyển ca
khám sang `VERIFIED`, mở bản nháp report, xác nhận sinh PDF, khóa report lại,
preview/download và đồng bộ report vào knowledge base. Đây là bản viết lại
hoàn toàn (không phải bản vá) vì luồng report đã đổi triệt để: template cũ
(`PdfReportDataDto` + `report-template.html`) đã bị xóa, thay bằng phiếu
"PHIẾU CHỤP XQUANG" (`XrayReportDataDto` + `xray-report-template.html`) với
2 endpoint tách biệt (xem nháp / xác nhận), và report giờ **chỉ sinh được
đúng 1 lần** cho mỗi ca khám.

Nguồn Java trong các block được giữ nguyên cấu trúc; comment tiếng Việt thêm
vào để giải thích, không phải code thật (được đánh dấu rõ bằng `//`).

## 1. Phạm Vi Và Sơ Đồ Luồng

Luồng này bắt đầu **sau khi AI prediction đã hoàn tất**. Nó không bao gồm
upload DICOM hay gọi FastAPI prediction; điều kiện đầu vào là examination có
các `DicomInstance` ở `GET_RESULTED` và các `AiResult` cần bác sĩ review.

```text
AiResult / DicomInstance GET_RESULTED
  -> PUT /ai/results/{aiResultId}/confirm
       hoặc PUT /ai/results/{aiResultId}/kl-grade
  -> DiagnosisReviewServiceImpl
       -> DiagnosisReview upsert
       -> markVerifiedWhenAllLatestResultsAreReviewed(): Examination NEED_VERIFY -> VERIFIED
       -> recalculateMaxPredictedGrade(): Examination.maxPredictedGrade cập nhật
  -> GET /examinations/{id}/report-draft          [BƯỚC 1 - MỞ ĐÚNG 1 LẦN]
       -> PdfExportService.getReportDraft()
            -> requireDraftable(): status phải == VERIFIED, nếu đã REPORT_GENERATED -> 400
            -> buildFormDefaults(): autofill từ Examination.findings/conclusion đã lưu
               (nếu có), hoặc tự sinh từ độ KL đã verify (XrayReportContentComposer)
       -> FE hiển thị form, bác sĩ sửa tay trên UI (không gọi API ở bước này)
  -> POST /examinations/{id}/generate-report        [BƯỚC 2 - CHỈ CHẠY THẬT 1 LẦN]
  -> PdfExportService.generateAndSavePdfReport()
       -> khóa Examination trong MySQL (SELECT ... FOR UPDATE)
       -> đã REPORT_GENERATED + còn file? -> trả report cũ, BỎ QUA request, dừng ở đây
       -> requireVerified(): chỉ VERIFIED (hoặc REPORT_GENERATED-nhưng-mất-file) đi tiếp
       -> applyDoctorEdits(buildFormDefaults(...), request): overlay chữ bác sĩ gửi lên
       -> Thymeleaf HTML (xray-report-template.html) -> OpenHTMLtoPDF -> file tạm -> PDF
       -> lưu Report (metadata file) -> lưu Examination.findings/conclusion + REPORT_GENERATED
       -> event ReportKnowledgeSyncRequestedEvent (sau commit)
  -> GET /reports/{examinationId}/preview | /download   [đường xem lại duy nhất sau đó]
```

Trạng thái hợp lệ theo code:

| Trạng thái | Ý nghĩa trong luồng này |
| --- | --- |
| `AI_PROCESSING` | AI chưa xong; chưa được review/sinh report. |
| `AI_FAILED` | Ca AI thất bại; không đủ điều kiện export report bình thường. |
| `NEED_VERIFY` | Có kết quả AI cần bác sĩ xác nhận/điều chỉnh. |
| `VERIFIED` | Mọi DICOM AI thành công đều có `DiagnosisReview`; **duy nhất** trạng thái cho phép mở draft và generate lần đầu. |
| `REPORT_GENERATED` | Report đã có metadata và file PDF; review bị khóa, draft bị khóa, generate chỉ còn idempotent (trả report cũ). |

**Điểm khác biệt lớn nhất so với thiết kế cũ:** trước đây chỉ có 1 endpoint
`POST generate-report` không tham số, tự soạn toàn bộ nội dung report từ dữ
liệu AI/DICOM/patient, không ai sửa được chữ trong report. Giờ có **2 bước**:
bác sĩ xem nháp trước (`GET report-draft`), có thể sửa tay phần KẾT QUẢ/KẾT
LUẬN, rồi mới xác nhận (`POST generate-report`) — và một khi đã xác nhận,
**cả 2 endpoint đều đóng lại vĩnh viễn** cho ca đó.

## 2. File Coverage

| File | Vai trò |
| --- | --- |
| `DiagnosisReviewController.java` | Hai HTTP endpoint confirm/adjust KL grade. |
| `DiagnosisReviewServiceImpl.java` | Logic review, ownership, upsert review, chuyển `VERIFIED`, cập nhật `maxPredictedGrade`. |
| `DiagnosisReview.java`, `DiagnosisReviewDecision.java` | Bản ghi final clinical decision cho đúng một `AiResult`. |
| `ReportController.java` | HTTP draft/generate/list/preview/download PDF. |
| `PdfExportService.java` | Toàn bộ build draft, sinh/lưu/list/đọc PDF và kiểm tra quyền report. |
| `XrayReportContentComposer.java` | Soạn text KẾT QUẢ/KẾT LUẬN mặc định từ độ KL đã verify (chỉ dùng khi chưa có sẵn text đã lưu). |
| `XrayReportProperties.java`, `ReportConfiguration.java` | Letterhead (bộ/viện/khoa/mã phiếu/nơi ký) cấu hình theo môi trường, không hardcode. |
| `GenerateReportRequest.java` | Body xác nhận của `POST generate-report`, mọi field optional. |
| `ReportDraftResponse.java` | Response của `GET report-draft`. |
| `XrayReportDataDto.java` | View model cuối cùng bind vào Thymeleaf template. |
| `xray-report-template.html` | Template PDF "PHIẾU CHỤP XQUANG". |
| `Report.java`, `ReportRepository.java` | Metadata file PDF trong MySQL (không chứa `findings`/`conclusion`). |
| `Examination.java`, `ExaminationStatus.java` | Trạng thái ca khám + **`findings`/`conclusion`** (cột mới, thuộc examination, không thuộc report). |
| `AiAnalysis.java`, `AiResult.java`, `DicomInstance.java` | Chuỗi dữ liệu AI được review, cung cấp độ KL cho report. |
| `database/migrations/examination_result_conclusion_migration.sql` | Migration tay cho `examinations.findings`/`examinations.conclusion` (bắt buộc chạy trước khi deploy `prod`, vì `prod` dùng `ddl-auto: validate`). |
| `PdfExportServiceTest`, `XrayReportTemplateTest`, `XrayReportContentComposerTest`, `ReportControllerTest`, `ReportListServiceTest`, `DiagnosisReviewServiceTest`, `DiagnosisReviewControllerRbacTest`, `ControllerRbacTest`, `OpenApiDocumentationTest` | Bằng chứng tự động cho các nhánh quan trọng. |

## 3. HTTP Và Phân Quyền

### 3.1 Review kết quả AI

Nguồn: `DiagnosisReviewController.java`.

```java
@PutMapping("/{aiResultId}/confirm")  // PUT vì đây là idempotent-by-intent: gọi lại nhiều
                                       // lần với cùng aiResultId cho cùng kết quả (upsert).
@PreAuthorize(
        // Trưởng khoa: luôn được phép, không cần thêm authority - review CA BẤT KỲ.
        "hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        // Bác sĩ thường: cần CẢ role DOCTOR LẪN authority CONFIRM_CONCLUSION - thiếu 1
        // trong 2 điều kiện đều bị 403 ngay tại tầng controller, chưa chạm service.
        + "or (hasRole('DOCTOR') and hasAuthority('CONFIRM_CONCLUSION'))")
public ResponseEntity<DiagnosisReviewResponse> confirmAiGrade(
        @PathVariable Long aiResultId,  // lấy từ URL path, VD /ai/results/19/confirm -> 19.
        Principal principal) {  // Spring Security tự inject; principal.getName() = username.
    // Không có @RequestBody: endpoint này KHÔNG NHẬN gì từ client ngoài ID trên URL.
    // "Confirm" nghĩa là: final grade = AI predicted grade, không có gì để bác sĩ nhập thêm
    // (khác hẳn adjustKlGrade bên dưới, cần cả grade mới lẫn lý do điều chỉnh).
    return ResponseEntity.ok(diagnosisReviewService.confirmAiGrade(aiResultId, principal.getName()));
}

@PutMapping("/{aiResultId}/kl-grade")
@PreAuthorize(
        "hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        // Quyền KHÁC với confirm: OVERRIDE_AI_GRADE, không phải CONFIRM_CONCLUSION - hệ
        // thống phân quyền tách riêng "đồng ý với AI" và "sửa lại AI" thành 2 quyền độc lập,
        // một bác sĩ có thể có quyền này mà không có quyền kia.
        + "or (hasRole('DOCTOR') and hasAuthority('OVERRIDE_AI_GRADE'))")
public ResponseEntity<DiagnosisReviewResponse> adjustKlGrade(
        @PathVariable Long aiResultId,
        @Valid @RequestBody AdjustKlGradeRequest request,  // @Valid kích hoạt Bean Validation
                                                             // TRƯỚC khi vào body method này.
        Principal principal) {
    // @Valid kiểm tra grade 0..4 và reviewNote không rỗng/tối đa 2.000 ký tự - vi phạm thì
    // Spring trả 400 kèm chi tiết field lỗi, KHÔNG BAO GIỜ chạy tới dòng return dưới đây.
    return ResponseEntity.ok(diagnosisReviewService.adjustKlGrade(aiResultId, request, principal.getName()));
}
```

Trưởng khoa được service cho phép review ca không gán cho mình. Bác sĩ thường
phải vừa có authority ở controller, vừa là bác sĩ được gán vào examination ở
service. Hai lớp kiểm tra này bổ sung cho nhau.

### 3.2 Draft, generate, preview và download report

Nguồn: `ReportController.java` (toàn bộ file).

```java
@GetMapping("/reports")
// Ba role đều được, KHÔNG cần authority riêng - khác hẳn 4 endpoint bên dưới, vốn cần thêm
// GENERATE_PDF_REPORT/EXPORT_DOWNLOAD_PDF cho riêng DOCTOR. List report được coi là quyền
// "mặc định" của mọi bác sĩ/trưởng khoa.
@PreAuthorize("hasAnyRole('DOCTOR', 'DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT')")
public ResponseEntity<PageResponse<ReportListItemResponse>> getGeneratedReports(
        Principal principal,
        // @PageableDefault: nếu client không truyền ?page=&size=&sort=, mặc định trang 0,
        // 10 dòng/trang, sắp theo createdAt giảm dần (report mới nhất lên đầu).
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(pdfExportService.getGeneratedReports(pageable, principal.getName()));
}

@GetMapping("/examinations/{id}/report-draft")  // URL gắn với EXAMINATION, không phải REPORT -
                                                  // đúng bản chất: chưa có Report nào tồn tại.
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
public ResponseEntity<ReportDraftResponse> getReportDraft(
        @PathVariable Long id, Principal principal) {
    // Không tạo PDF. Ném IllegalArgumentException (-> 400) nếu status không phải VERIFIED,
    // kể cả khi status đã REPORT_GENERATED - xem PdfExportService.requireDraftable().
    return ResponseEntity.ok(pdfExportService.getReportDraft(id, principal.getName()));
}

@PostMapping("/examinations/{id}/generate-report")  // POST vì đây là hành động TẠO (file PDF
                                                       // mới, Report row mới) - khác GET ở trên.
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        // Dùng CHUNG quyền GENERATE_PDF_REPORT với endpoint draft phía trên - không có
        // quyền riêng "xác nhận report" tách khỏi "xem nháp report".
        + "or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
public ResponseEntity<ReportResponse> generatePdfReport(
        @PathVariable Long id,
        // required = false: body hoàn toàn optional ở tầng HTTP - client có thể POST không
        // kèm Content-Type/body gì cả, Spring sẽ bind request = null thay vì trả lỗi 400.
        @Valid @RequestBody(required = false) GenerateReportRequest request,
        Principal principal) {
    // @Valid CHỈ chạy khi request != null (Spring bỏ qua validate cho body null) - mọi
    // @Size trong GenerateReportRequest chỉ có tác dụng khi client THỰC SỰ gửi field đó.
    return ResponseEntity.ok(
            pdfExportService.generateAndSavePdfReport(id, principal.getName(), request));
}

@GetMapping("/reports/{examinationId}/preview")  // URL đổi sang gắn REPORT (đã tồn tại).
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        // LƯU Ý: dùng lại GENERATE_PDF_REPORT, KHÔNG PHẢI quyền "xem" riêng - ai tạo được
        // report thì cũng xem trước được report, hợp lý vì đó chính là bước xác nhận cuối.
        + "or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
public ResponseEntity<Resource> previewReport(@PathVariable Long examinationId, Principal principal) {
    // false = không phải download -> fileResponse() dựng Content-Disposition: inline.
    // inline Content-Disposition: browser có thể render PDF nhưng response không được cache.
    return fileResponse(pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), false);
}

@GetMapping("/reports/{examinationId}/download")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        // KHÁC preview: EXPORT_DOWNLOAD_PDF, không phải GENERATE_PDF_REPORT - một bác sĩ có
        // thể xem trước (preview) được nhưng KHÔNG tải file thật về máy nếu thiếu quyền này.
        + "or (hasRole('DOCTOR') and hasAuthority('EXPORT_DOWNLOAD_PDF'))")
@LogAction("DOWNLOAD_PDF_REPORT")  // duy nhất trong 5 method của class này có audit log -
                                     // tải file về máy được coi là hành động nhạy cảm hơn xem.
public ResponseEntity<Resource> downloadReport(@PathVariable Long examinationId, Principal principal) {
    // true = download -> fileResponse() dựng Content-Disposition: attachment (ép trình
    // duyệt tải xuống thay vì mở trực tiếp, dù cùng file với preview ở trên).
    return fileResponse(pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), true);
}
```

Ba điểm quan trọng về phân quyền:

1. `report-draft` và `generate-report` dùng **chung một quyền**
   (`GENERATE_PDF_REPORT`) — không có quyền riêng cho "xem nháp" và "xác
   nhận". Ai xem được nháp thì cũng xác nhận được.
2. `preview` cũng dùng `GENERATE_PDF_REPORT` (không phải quyền xem riêng),
   còn `download` dùng `EXPORT_DOWNLOAD_PDF` — hai quyền khác nhau, một bác
   sĩ có thể xem được nhưng không tải được nếu chỉ có quyền đầu.
3. `previewUrl`/`downloadUrl` dùng **examinationId**, không dùng reportId.
   Service luôn tìm report mới nhất theo examination bằng
   `findFirstByExaminationIdOrderByCreatedAtDesc` — với thiết kế "chỉ generate
   1 lần" hiện tại, mỗi examination chỉ có đúng 1 report nên "mới nhất" và
   "duy nhất" là một.

## 4. Review Và Chuyển VERIFIED

### 4.1 Confirm hoặc adjust

Nguồn: `DiagnosisReviewServiceImpl.java:41-72`.

```java
@Override  // implements DiagnosisReviewService - interface tồn tại để service khác mock được.
@Transactional  // toàn bộ method chạy trong 1 transaction MySQL - xem mục 10.2 để biết
                 // chính xác những câu SQL nào commit cùng nhau.
@LogAction("CONFIRM_AI_GRADE")  // AOP ghi audit log: username, IP, tham số method - tự động,
                                  // không cần code thủ công trong body.
public DiagnosisReviewResponse confirmAiGrade(Long aiResultId, String username) {
    // true cho phép trưởng khoa review case không được gán trực tiếp; tham số thứ 3 của
    // loadReviewContext() - false sẽ chặn cả trưởng khoa nếu case không phải của họ (không
    // dùng ở đây, nhưng method được viết tổng quát để tái dùng cho ngữ cảnh khác).
    ReviewContext context = loadReviewContext(aiResultId, username, true);
    // Validate NGAY độ AI đã dự đoán - dù về lý thuyết AI luôn trả 0-4, đây là lớp phòng thủ
    // nếu dữ liệu AI bị hỏng/predictedGrade null.
    validateGrade(context.aiResult().getPredictedGrade());
    // Confirm không sửa dự đoán AI: confirmed = predicted - đây là điểm khác BIỆT DUY NHẤT
    // với adjustKlGrade() bên dưới (nơi confirmed = giá trị bác sĩ tự nhập).
    return saveReview(
            context,
            context.aiResult().getPredictedGrade(),  // grade cuối = grade AI, không đổi gì.
            DiagnosisReviewDecision.AI_CONFIRMED,  // đánh dấu rõ đây là "đồng ý", không phải "sửa".
            "AI result confirmed");  // reviewNote cố định, cột NOT NULL nên không thể để trống.
}

@Override
@Transactional
@LogAction("OVERRIDE_AI_GRADE")  // action khác CONFIRM_AI_GRADE - audit log phân biệt được
                                   // "đồng ý AI" và "ghi đè AI" dù cùng update 1 bảng.
public DiagnosisReviewResponse adjustKlGrade(
        Long aiResultId,
        AdjustKlGradeRequest request,
        String username) {
    // Service kiểm tra lại dù controller đã có @Valid: bảo vệ khi service được gọi nội bộ/test
    // (VD unit test gọi thẳng service, bỏ qua tầng HTTP/Bean Validation của controller).
    if (request == null || request.reviewNote() == null || request.reviewNote().isBlank()) {
        throw new IllegalArgumentException("Review note is required");
    }
    validateGrade(request.confirmedKlGrade());  // grade DO BÁC SĨ NHẬP, phải validate riêng
                                                  // (khác nhánh confirm dùng thẳng grade AI).

    ReviewContext context = loadReviewContext(aiResultId, username, true);
    return saveReview(
            context,
            request.confirmedKlGrade(),  // grade cuối = grade bác sĩ tự chọn, KHÔNG PHẢI AI.
            DiagnosisReviewDecision.DOCTOR_ADJUSTED,  // đánh dấu rõ đây là quyết định của người.
            request.reviewNote().trim());  // lý do bác sĩ ghi - bắt buộc, đã validate ở trên.
}
```

`DiagnosisReview` có quan hệ `@OneToOne` unique với `AiResult`. Vì vậy một lần
confirm lần hai hoặc adjust sau confirm không tạo review thứ hai; nó cập nhật
row cũ thành quyết định mới nhất.

### 4.2 Upsert review, chuyển VERIFIED, và tính lại maxPredictedGrade

Nguồn: `DiagnosisReviewServiceImpl.java:74-174`. **`recalculateMaxPredictedGrade`
là phần được thêm từ nhánh `dev` sau khi tài liệu bản cũ được viết** — cần
đọc kỹ vì đây là điểm khác duy nhất so với logic review "gốc".

```java
private DiagnosisReviewResponse saveReview(
        ReviewContext context,
        Integer confirmedGrade,
        DiagnosisReviewDecision decision,
        String reviewNote) {
    // Có review cũ thì update; chưa có thì tạo mới.
    DiagnosisReview review = diagnosisReviewRepository.findByAiResultId(context.aiResult().getId())
            .orElseGet(DiagnosisReview::new);
    review.setAiResult(context.aiResult());
    review.setExamination(context.examination());
    review.setDoctor(context.reviewer());
    review.setConfirmedKlGrade(confirmedGrade);
    review.setDecision(decision);
    review.setReviewNote(reviewNote);
    review.setReviewedAt(LocalDateTime.now());

    DiagnosisReview savedReview = diagnosisReviewRepository.save(review);
    // Giữ object graph trong persistence context đồng bộ cho check ngay sau đó.
    context.aiResult().setDiagnosisReview(savedReview);
    markVerifiedWhenAllLatestResultsAreReviewed(context.examination());
    // MỚI: chạy sau markVerified, độc lập với việc examination có chuyển VERIFIED hay không.
    recalculateMaxPredictedGrade(context.examination());
    return toResponse(savedReview);
}

private void markVerifiedWhenAllLatestResultsAreReviewed(Examination examination) {
    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
    if (instances.isEmpty()) return;

    boolean hasSuccessfulAiResult = false;
    for (DicomInstance instance : instances) {
        if (instance.getStatus() == DicomInstanceStatus.AI_FAILED) {
            // Một ảnh AI failed không chặn xác minh các ảnh AI thành công khác.
            continue;
        }
        if (instance.getStatus() != DicomInstanceStatus.GET_RESULTED) {
            // AI còn sending/không ở trạng thái kết quả: chưa được VERIFIED.
            return;
        }
        AiAnalysis latestAnalysis = instance.getAiAnalysis();
        if (latestAnalysis == null
                || latestAnalysis.getAiResults() == null
                || latestAnalysis.getAiResults().isEmpty()) {
            return;
        }
        // Tất cả AiResult trong latest analysis của instance này phải có review.
        if (latestAnalysis.getAiResults().stream()
                .anyMatch(result -> result.getDiagnosisReview() == null)) {
            return;
        }
        hasSuccessfulAiResult = true;
    }
    // Không đánh VERIFIED cho ca chỉ có AI_FAILED, dù vòng lặp không còn instance chưa review.
    if (!hasSuccessfulAiResult) return;

    examination.setStatus(ExaminationStatus.VERIFIED);
    examinationRepository.save(examination);
}

private void recalculateMaxPredictedGrade(Examination examination) {
    // Đọc lại toàn bộ DICOM/AI/review của ca — KHÔNG chỉ AiResult vừa review — vì
    // maxPredictedGrade phản ánh độ nặng nhất của TOÀN BỘ ca, không riêng 1 ảnh.
    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examination.getId());
    int maxGrade = -1;
    for (DicomInstance instance : instances) {
        AiAnalysis aiAnalysis = instance.getAiAnalysis();
        if (aiAnalysis != null && aiAnalysis.getAiResults() != null) {
            for (AiResult aiResult : aiAnalysis.getAiResults()) {
                // Mặc định lấy predicted grade của AI...
                int gradeToConsider = aiResult.getPredictedGrade() != null ? aiResult.getPredictedGrade() : -1;
                // ...nhưng nếu đã có review, độ đã CONFIRMED/ADJUSTED được ưu tiên hơn AI raw.
                if (aiResult.getDiagnosisReview() != null && aiResult.getDiagnosisReview().getConfirmedKlGrade() != null) {
                    gradeToConsider = aiResult.getDiagnosisReview().getConfirmedKlGrade();
                }
                if (gradeToConsider > maxGrade) {
                    maxGrade = gradeToConsider;
                }
            }
        }
    }
    // Chỉ ghi DB khi có ít nhất 1 grade hợp lệ VÀ giá trị thực sự đổi - tránh UPDATE thừa.
    if (maxGrade >= 0 && !java.util.Objects.equals(examination.getMaxPredictedGrade(), maxGrade)) {
        examination.setMaxPredictedGrade(maxGrade);
        examinationRepository.save(examination);
    }
}
```

Nói ngắn gọn: `VERIFIED` không có nghĩa "một AI result đã được review". Nó có
nghĩa **mọi kết quả AI của mọi DICOM thành công** trong examination đã có
review. `maxPredictedGrade` thì khác — nó được tính lại **sau mỗi lần review**
(kể cả khi ca chưa đủ điều kiện VERIFIED), dùng để hiển thị mức độ nặng nhất
hiện biết của ca trên dashboard/thống kê (`BusinessDataQueryService
.gradeDistribution()` đọc cột này), **không** liên quan trực tiếp tới nội
dung report — report tự tính lại độ KL final riêng qua
`PdfExportService.finalGradeForSide()` (mục 6.1), không đọc `maxPredictedGrade`.

### 4.3 Ownership, freeze sau report và các helper

Nguồn: `DiagnosisReviewServiceImpl.java:96-198`.

```java
private ReviewContext loadReviewContext(
        Long aiResultId,
        String username,
        boolean departmentHeadCanReviewUnassignedExamination) {
    AiResult aiResult = aiResultRepository.findById(aiResultId)
            .orElseThrow(() -> new IllegalArgumentException("AI result not found with ID: " + aiResultId));
    Doctor reviewer = doctorRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Doctor not found: " + username));
    Examination examination = getExamination(aiResult);

    // Report là bản chốt; hệ thống không cho đổi final grade sau khi PDF đã sinh.
    // Đây là lớp khóa ĐỘC LẬP với requireDraftable()/generate-once ở PdfExportService:
    // review bị khóa ngay khi REPORT_GENERATED, không có ngoại lệ "mất file thì mở lại".
    if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
        throw new IllegalArgumentException("Cannot review an examination after its report has been generated");
    }

    boolean headMayReview = departmentHeadCanReviewUnassignedExamination && isDepartmentHead(reviewer);
    boolean assignedDoctor = examination.getDoctor() != null
            && Objects.equals(examination.getDoctor().getId(), reviewer.getId());
    if (!headMayReview && !assignedDoctor) {
        throw new AccessDeniedException("Doctor is not assigned to this examination");
    }
    return new ReviewContext(aiResult, examination, reviewer);
}

private void validateGrade(Integer grade) {
    if (grade == null || grade < 0 || grade > 4) {
        throw new IllegalArgumentException("Confirmed KL grade must be between 0 and 4");
    }
}

private Examination getExamination(AiResult aiResult) {
    // Chuỗi bắt buộc: AiResult -> AiAnalysis -> DicomInstance -> Examination.
    if (aiResult.getAiAnalysis() == null
            || aiResult.getAiAnalysis().getDicomInstance() == null
            || aiResult.getAiAnalysis().getDicomInstance().getExamination() == null) {
        throw new IllegalStateException("AI result is not linked to an examination");
    }
    return aiResult.getAiAnalysis().getDicomInstance().getExamination();
}
```

## 5. Draft Preview: Xem Trước, Không Tạo Gì Cả

### 5.1 `getReportDraft`: đọc thuần túy

Nguồn: `PdfExportService.java:215-256`.

```java
/**
 * Returns the report form pre-filled for review. The doctor edits it on screen and posts the
 * confirmed values back to the generate endpoint, which is the only step that renders a PDF.
 *
 * Once a report has been generated for the examination, the report is final: this endpoint
 * stops offering a draft and the doctor is pointed to the preview/download endpoints instead,
 * matching generateAndSavePdfReport which likewise refuses to render a second time.
 */
@Transactional(readOnly = true)  // không FOR UPDATE, không ghi gì - draft an toàn để gọi song song.
public ReportDraftResponse getReportDraft(Long examinationId, String username) {
    // findById() THƯỜNG, KHÔNG PHẢI findByIdForUpdate() như bên generate (mục 6.1) - draft
    // không cần khóa pessimistic vì không ghi gì vào DB.
    Examination examination = examinationRepository.findById(examinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Examination not found with id: " + examinationId));
    User currentUser = getUser(username);  // người đang xem draft - sẽ in tên lên doctorName.
    authorizeReportAccess(examination, currentUser);  // 403 nếu không phải bác sĩ được gán/trưởng khoa.
    // KHÁC với generate: dùng requireDraftable(), KHÔNG dùng requireVerified() - xem mục 5.2
    // để thấy 2 method này khác nhau chính xác ở đâu.
    requireDraftable(examination);

    // Tính độ KL final TRƯỚC, vì cả finalGradeForSide() (dùng 2 lần bên dưới) lẫn
    // buildFormDefaults() đều cần cùng 1 kết quả này - tránh gọi buildFinalAiResults() 2 lần.
    FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
    ReportForm form = buildFormDefaults(examination, currentUser, finalAiResults);  // xem mục 5.3.
    Patient patient = examination.getPatient();  // có thể null nếu quan hệ patient bị đứt.
    return new ReportDraftResponse(
            examinationId,
            // patient có thể null: dùng toán tử 3 ngôi thay vì gọi thẳng patient.getPatientCode()
            // để tránh NullPointerException - field này chỉ mang tính hiển thị, không bắt buộc.
            patient == null ? null : patient.getPatientCode(),
            // 5 dòng dưới đây (ministryName...clinicalDepartment): letterhead CỐ ĐỊNH từ
            // application.yaml (XrayReportProperties), KHÔNG liên quan gì tới examination -
            // giống hệt nhau cho mọi ca khám trong cùng 1 lần deploy.
            reportProperties.ministryName(),
            reportProperties.hospitalName(),
            reportProperties.departmentName(),
            reportProperties.formCode(),
            reportProperties.clinicalDepartment(),
            // doctorName: LUÔN là currentUser (người đang gọi API này), không phải bác sĩ
            // được gán cho ca - FE cần hiển thị đúng "ai sẽ ký tên" trước khi họ xác nhận.
            form.doctorName(),
            // 2 field CHỈ ĐỌC, không nằm trong ReportForm - tính riêng, chỉ để FE hiển thị
            // tham khảo "hệ thống đang thấy độ KL là bao nhiêu", KHÔNG gửi lại được khi confirm
            // (GenerateReportRequest không có leftKlGrade/rightKlGrade).
            finalGradeForSide(finalAiResults.results(), "LEFT"),
            finalGradeForSide(finalAiResults.results(), "RIGHT"),
            // 9 field còn lại: toàn bộ lấy từ `form` (ReportForm) - đây là phần EDITABLE,
            // đúng những field mà GenerateReportRequest cũng có, để FE gửi lại nguyên vẹn
            // (hoặc sửa rồi gửi) khi bác sĩ bấm xác nhận.
            form.documentNumber(),
            form.attemptNumber(),
            form.patientName(),
            form.age(),
            form.gender(),
            form.address(),
            form.findings(),
            form.conclusion(),
            form.signaturePlace(),
            form.signatureDate());
}
```

Điểm mấu chốt: `getReportDraft` **không mở transaction ghi**
(`@Transactional(readOnly = true)`), **không gọi `renderPdf`**, **không tạo
`Report` entity**. Nó chỉ build `ReportForm` (mục 5.2) rồi map thẳng sang
`ReportDraftResponse`. Nếu 2 request `GET report-draft` chạy đồng thời cho
cùng 1 ca, cả hai đều trả về y hệt nhau — không có side effect gì để mà đụng
độ.

`findFinalAiResults()` được gọi ở đây và **cũng được gọi lại** trong
`generateAndSavePdfReport()` (mục 6). Đây là tính toán lại từ đầu mỗi lần —
không cache — nên nếu bác sĩ mở draft trước, rồi độ KL của ca bị đổi (về mặt
lý thuyết, dù review đã bị khóa nếu `REPORT_GENERATED`), lần gọi draft/generate
sau vẫn phản ánh dữ liệu DB mới nhất, không phải dữ liệu tại thời điểm mở
draft lần đầu.

### 5.2 `requireDraftable`: khóa chỉ áp dụng cho draft

Nguồn: `PdfExportService.java:258-281` (`requireVerified` 258-264, `requireDraftable` 266-281).

```java
private void requireVerified(Examination examination, String action) {
    // Dùng bởi generateAndSavePdfReport(). Chấp nhận CẢ VERIFIED lẫn REPORT_GENERATED,
    // vì generate cần đi tiếp qua nhánh REPORT_GENERATED-nhưng-mất-file để tự phục hồi.
    if (examination.getStatus() != ExaminationStatus.VERIFIED
            && examination.getStatus() != ExaminationStatus.REPORT_GENERATED) {
        throw new IllegalArgumentException(
                "Examination must be verified before " + action + " its report");
    }
}

/**
 * Guards the draft preview specifically: unlike requireVerified, a report that has already
 * been generated is rejected rather than let through, since the report is final once
 * generated and there is nothing left to draft.
 */
private void requireDraftable(Examination examination) {
    // Khác requireVerified(): REPORT_GENERATED bị từ chối THẲNG, không có ngoại lệ nào.
    if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
        throw new IllegalArgumentException(
                "Report has already been generated for this examination; "
                        + "view the confirmed result via the report preview or download endpoint");
    }
    if (examination.getStatus() != ExaminationStatus.VERIFIED) {
        throw new IllegalArgumentException(
                "Examination must be verified before drafting its report");
    }
}
```

Đây là 2 method riêng biệt có chủ đích — không dùng chung 1 method với tham
số boolean. Lý do: `requireVerified` phải cho `REPORT_GENERATED` đi qua để
`generateAndSavePdfReport` tự xử lý nhánh phục hồi (mục 6.1, dòng 113-122),
còn `requireDraftable` thì tuyệt đối không cho `REPORT_GENERATED` đi tiếp
trong bất kỳ trường hợp nào — kể cả khi file PDF đã mất, draft vẫn không mở
lại (chỉ `generate` mới có quyền tự phục hồi file).

### 5.3 `buildFormDefaults`: nơi autofill thực sự xảy ra

Nguồn: `PdfExportService.java:283-316` (Javadoc dòng 283-291, method dòng 292-316).

```java
/**
 * Fills every form field from the examination record, the configured letterhead, and the
 * Kellgren-Lawrence grades confirmed during verification. This is what the doctor sees in the
 * preview, and what any field they leave alone falls back to on confirm.
 *
 * The result block prefers whatever the doctor confirmed the last time a report was
 * generated for this examination, since that already reflects their own wording; only a first
 * confirmation falls back to the grade-only text auto-composed from the verified KL grades.
 */
private ReportForm buildFormDefaults(
        Examination examination,
        User currentUser,
        FinalAiResults finalAiResults) {
    Patient patient = examination.getPatient();
    String leftKlGrade = finalGradeForSide(finalAiResults.results(), "LEFT");
    String rightKlGrade = finalGradeForSide(finalAiResults.results(), "RIGHT");
    // BƯỚC 1: đọc thẳng cột đã lưu trên Examination (không phải trên Report).
    List<String> savedFindings = splitLines(examination.getFindings());
    String savedConclusion = valueOrBlank(examination.getConclusion());
    return new ReportForm(
            valueOrBlank(examination.getEncounterCode()),
            "", // attemptNumber: không có nguồn dữ liệu, bác sĩ tự gõ trên form.
            patient == null ? "" : valueOrBlank(patient.getFullName()),
            formatAge(patient, examination),
            formatGender(patient),
            patient == null ? "" : valueOrBlank(patient.getAddress()),
            // BƯỚC 2: nếu Examination.findings rỗng (ca CHƯA TỪNG generate) thì mới tự
            // sinh từ độ KL. Nếu đã có (đã generate ít nhất 1 lần trước đó rồi phải mở
            // lại vì file mất), dùng nguyên chữ bác sĩ đã lưu, KHÔNG tính lại từ độ KL.
            savedFindings.isEmpty()
                    ? contentComposer.composeFindings(leftKlGrade, rightKlGrade) : savedFindings,
            savedConclusion.isBlank()
                    ? contentComposer.composeConclusion(leftKlGrade, rightKlGrade) : savedConclusion,
            reportProperties.signaturePlace(),
            LocalDate.now(),
            currentUser == null ? "" : valueOrBlank(currentUser.getFullName()));
}
```

`splitLines()`/`String.join("\n", ...)` là cặp hàm nghịch đảo nhau: khi lưu
(`generateAndSavePdfReport` dòng 161), `findings` (một `List<String>`) được
nối bằng `\n` thành 1 chuỗi để lưu vào cột TEXT `examinations.findings`. Khi
đọc lại ở đây, `splitLines()` tách đúng ngược lại bằng regex `\R` (khớp mọi
kiểu xuống dòng: `\n`, `\r\n`, `\r`), lọc dòng trắng, trim từng dòng. Round-trip
này chỉ đúng nếu không có dòng nào của bác sĩ tự chứa ký tự xuống dòng thật
sự — điều này đã được đảm bảo phía nhận request (`cleanFindings`, mục 6.2)
loại bỏ dòng rỗng trước khi lưu.

## 6. Generate: Bước Duy Nhất Thật Sự Tạo PDF

### 6.1 Hàm chính `generateAndSavePdfReport`

Nguồn: `PdfExportService.java:92-173`.

```java
/**
 * Renders the X-ray report PDF for a verified examination.
 *
 * This is the confirm step of the preview flow: request carries the form fields the
 * doctor reviewed and edited, and a fresh PDF is rendered the first time. A report can only be
 * generated once per examination - once the status is REPORT_GENERATED, every later call
 * (with or without a body) just returns that same report untouched instead of creating another
 * one, unless the underlying PDF file has gone missing, in which case it is re-rendered to recover.
 */
@Transactional
@LogAction("GENERATE_PDF_REPORT")
public ReportResponse generateAndSavePdfReport(
        Long examinationId,
        String username,
        GenerateReportRequest request) {
    // Pessimistic lock: 2 request generate đồng thời cho cùng examination bị serialize,
    // request thứ 2 phải đợi request đầu commit/rollback xong mới đọc được row.
    Examination examination = examinationRepository.findByIdForUpdate(examinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Examination not found with id: " + examinationId));
    User currentUser = getUser(username);
    authorizeReportAccess(examination, currentUser);

    // NHÁNH "ĐÃ GENERATE RỒI": kiểm tra TRƯỚC requireVerified(), và HOÀN TOÀN không
    // đọc `request` ở đây - dù bác sĩ gửi chữ mới, nó vẫn bị bỏ qua nếu rơi vào nhánh này.
    if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
        Report existingReport = reportRepository
                .findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                .filter(this::reportFileExists) // .filter(): Optional rỗng nếu file đã mất trên đĩa.
                .orElse(null);
        if (existingReport != null) {
            // Idempotent: không render PDF mới; chỉ kích hoạt lại sync RAG của report đã có -
            // hữu ích khi lần index trước bị lỗi và cần thử lại mà không sinh PDF trùng.
            eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(existingReport.getId()));
            return toResponse(existingReport);
        }
        // existingReport == null nghĩa là: đã REPORT_GENERATED nhưng không tìm thấy report
        // còn file hợp lệ (VD: đã xóa file thủ công trên đĩa) -> rơi xuống dưới để render lại.
    }
    requireVerified(examination, "generating"); // chấp nhận VERIFIED hoặc REPORT_GENERATED-mất-file.

    FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
    // buildFormDefaults(): y hệt logic autofill của draft (mục 5.3).
    // applyDoctorEdits(): overlay những gì `request` gửi lên (mục 6.2).
    ReportForm form = applyDoctorEdits(
            buildFormDefaults(examination, currentUser, finalAiResults), request);
    XrayReportDataDto dataDto = buildXrayReportData(form);

    Context context = new Context();
    context.setVariable("data", dataDto); // đúng tên `${data...}` dùng trong xray-report-template.html
    String htmlContent = templateEngine.process("pdf/xray-report-template", context);

    Path exportRoot = getExportRoot();
    String fileName = buildFileName(examination);
    Path outputPath = exportRoot.resolve(fileName).normalize();
    Path temporaryPath = null;

    try {
        Files.createDirectories(exportRoot);
        temporaryPath = Files.createTempFile(exportRoot, ".report-", ".tmp");
        renderPdf(htmlContent, temporaryPath);
        // Chỉ sau render thành công mới đổi tên temp thành tên PDF nhìn thấy được (atomic).
        moveAtomically(temporaryPath, outputPath);
        temporaryPath = null;
        deleteFileIfTransactionRollsBack(outputPath);

        Report report = new Report();
        report.setExamination(examination);
        report.setOperatingDoctor(currentUser);
        // clinical_summary QUAY VỀ ý nghĩa GỐC: finalDiagnosis của examination.
        // KHÔNG PHẢI conclusion của report nữa (một thiết kế cũ trong phiên làm việc này
        // từng ghi đè clinical_summary = conclusion, đã bị revert vì BusinessDataQueryService
        // coi clinical_summary và final_diagnosis là 2 cột tách biệt - xem mục 9).
        report.setClinicalSummary(examination.getFinalDiagnosis());
        report.setFilePath(fileName); // relative name, không lưu absolute path trong DB
        report.setFileName(fileName);
        report.setContentType(PDF_CONTENT_TYPE);
        report.setFileSize(Files.size(outputPath));
        report.setCreatedAt(LocalDateTime.now());
        Report savedReport = reportRepository.save(report);
        eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(savedReport.getId()));

        // MỚI: findings/conclusion lưu vào EXAMINATION, không phải Report. Đây là điều
        // khiến draft lần sau (nếu từng mở lại được, VD nhánh phục hồi mất-file) đọc lại
        // đúng chữ bác sĩ đã xác nhận, thay vì tự sinh lại từ độ KL.
        examination.setFindings(String.join("\n", form.findings()));
        examination.setConclusion(form.conclusion());
        examination.setStatus(ExaminationStatus.REPORT_GENERATED);
        examinationRepository.save(examination);
        log.info("PDF report {} generated for examination {}", savedReport.getId(), examinationId);
        return toResponse(savedReport);
    } catch (Exception exception) {
        // Dọn file tạm hoặc file output khi render/DB flow lỗi - không để rác trên đĩa.
        deleteQuietly(temporaryPath);
        deleteQuietly(outputPath);
        log.error("Failed to generate PDF for examination {}", examinationId, exception);
        throw new RuntimeException("Failed to generate PDF: " + exception.getMessage(), exception);
    }
}
```

**Vì sao `examination.setFindings/setConclusion` nằm ở dòng 161-162, SAU khi
`Report` đã save (dòng 156) chứ không phải trước?** Vì cả hai đều nằm trong
cùng 1 `@Transactional` — thứ tự các câu lệnh Java không quyết định thứ tự
COMMIT (JPA gom mọi thay đổi vào cuối transaction, flush theo dependency),
nên đặt trước hay sau không ảnh hưởng tính đúng đắn dữ liệu, chỉ ảnh hưởng
tính dễ đọc. Đặt sau khối tạo `Report` để code đọc theo đúng trình tự nghiệp
vụ: "tạo file xong -> lưu metadata file xong -> mới khóa ca lại".

### 6.2 `applyDoctorEdits`: overlay từng field độc lập

Nguồn: `PdfExportService.java:318-355` (`applyDoctorEdits` 318-340, `override` 342-344, `cleanFindings` 346-355).

```java
/**
 * Overlays the values the doctor confirmed onto the pre-filled form. Each field falls back
 * independently, so correcting one line never blanks the rest of the sheet.
 */
private ReportForm applyDoctorEdits(ReportForm defaults, GenerateReportRequest request) {
    if (request == null) {
        // Không gửi body: giữ nguyên 100% giá trị autofill.
        return defaults;
    }
    List<String> findings = cleanFindings(request.findings());
    return new ReportForm(
            override(defaults.documentNumber(), request.documentNumber()),
            override(defaults.attemptNumber(), request.attemptNumber()),
            override(defaults.patientName(), request.patientName()),
            override(defaults.age(), request.age()),
            override(defaults.gender(), request.gender()),
            override(defaults.address(), request.address()),
            // findings là List, không dùng override() (dành cho String) - rỗng thì giữ default.
            findings.isEmpty() ? defaults.findings() : findings,
            override(defaults.conclusion(), request.conclusion()),
            override(defaults.signaturePlace(), request.signaturePlace()),
            request.signatureDate() == null ? defaults.signatureDate() : request.signatureDate(),
            // doctorName KHÔNG BAO GIỜ lấy từ request - luôn là defaults.doctorName(), tức
            // currentUser đang gọi API. GenerateReportRequest thậm chí không có field này.
            defaults.doctorName());
}

private String override(String defaultValue, String submitted) {
    // Chuỗi null hoặc toàn khoảng trắng coi như "không gửi" - giữ giá trị mặc định.
    return submitted == null || submitted.isBlank() ? defaultValue : submitted.trim();
}

private List<String> cleanFindings(List<String> findings) {
    if (findings == null) return List.of();
    return findings.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(line -> !line.isEmpty()) // dòng chỉ có khoảng trắng bị loại, không in bullet rỗng.
            .toList();
}
```

`GenerateReportRequest` (record) không có field `doctorName` — không phải vì
bị lọc ở đây, mà vì **field đó không tồn tại trong DTO ngay từ đầu**
(`@Size` validation cho 10 field khác, không có field ký tên). Đây là lớp
bảo vệ kép: kể cả nếu FE cố tình gửi thêm field lạ trong JSON, Jackson sẽ bỏ
qua nó vì record không có property tương ứng để bind vào.

### 6.3 `buildXrayReportData`: map sang view model cuối cùng

Nguồn: `PdfExportService.java:367-390`.

```java
private XrayReportDataDto buildXrayReportData(ReportForm form) {
    return XrayReportDataDto.builder()
            .ministryName(reportProperties.ministryName())
            .hospitalName(reportProperties.hospitalName())
            .departmentName(reportProperties.departmentName())
            .formCode(reportProperties.formCode())
            .hospitalLogo(logoDataUri("report/logo-hospital.png")) // xem mục 7.2
            .documentNumber(form.documentNumber())
            .attemptNumber(form.attemptNumber())
            .patientName(form.patientName())
            .age(form.age())
            .gender(form.gender())
            .address(form.address())
            // clinicalDepartment KHÔNG lấy từ form - luôn cố định theo config, bác sĩ
            // không sửa được field này dù request có gửi gì (GenerateReportRequest cũng
            // không có field clinicalDepartment).
            .clinicalDepartment(reportProperties.clinicalDepartment())
            .findings(form.findings())
            .conclusionLines(splitLines(form.conclusion())) // 1 chuỗi -> nhiều dòng cho template
            .signaturePlace(form.signaturePlace())
            .signatureDay(formatDayOrMonth(form.signatureDate().getDayOfMonth()))
            .signatureMonth(formatDayOrMonth(form.signatureDate().getMonthValue()))
            .signatureYear(String.valueOf(form.signatureDate().getYear()))
            .doctorName(form.doctorName())
            .build();
}
```

**Lưu ý quan trọng khi đọc `XrayReportDataDto.java`:** class này còn khai báo
6 field không xuất hiện trong block trên: `room`, `bed`, `diagnosis`,
`examRequest`, `requestDay/Month/Year`, `referringPhysician`. Đây là **field
chết** — còn lại từ các vòng thiết kế trước trong cùng phiên làm việc (từng
có khối "Bác sĩ điều trị" trên phiếu, sau đó bị yêu cầu bỏ hẳn). Không method
nào set các field này (`buildXrayReportData` ở trên là nơi DUY NHẤT tạo
`XrayReportDataDto`), và template `xray-report-template.html` cũng không còn
`th:text="${data.room}"`/`${data.diagnosis}`/... nào cả — đã xác minh bằng
`grep` trên cả 2 file, không có kết quả. Nếu đọc code mà thấy các field này,
đừng nhầm là chúng đang được dùng: chúng chỉ đang chờ dọn (xóa khỏi
`XrayReportDataDto`) ở một lần sửa sau, không phải bug ảnh hưởng runtime vì
Lombok mặc định trả `null`/không set gì và template không đọc tới.

## 7. Nội Dung Autofill Và Render PDF

### 7.1 `XrayReportContentComposer`: soạn KẾT QUẢ/KẾT LUẬN mặc định

Nguồn: `XrayReportContentComposer.java` (toàn bộ, 79 dòng). Class này **chỉ**
được gọi khi `Examination.findings`/`conclusion` còn rỗng (lần generate đầu
tiên của ca) — xem lại điều kiện ở mục 5.3.

```java
public List<String> composeFindings(String leftKlGrade, String rightKlGrade) {
    List<String> findings = new ArrayList<>();
    // Gối PHẢI thêm trước gối TRÁI - thứ tự cố định, khớp cách đọc phim X-quang thông thường.
    addSideFinding(findings, "Gối phải", rightKlGrade);
    addSideFinding(findings, "Gối trái", leftKlGrade);
    if (findings.isEmpty()) {
        // Cả 2 bên đều null/không hợp lệ (VD chưa verify bên nào, hoặc verify sai định dạng).
        findings.add("Không có độ Kellgren-Lawrence đã xác nhận cho cả hai khớp gối.");
    }
    return findings;
}

private void addSideFinding(List<String> findings, String sideLabel, String klGrade) {
    Integer grade = parseGrade(klGrade); // null nếu klGrade rỗng hoặc ngoài [0,4]
    if (grade == null) return; // bên này CHƯA verify -> bỏ qua, không in dòng giả.
    if (grade == 0) {
        // Độ 0 viết riêng: "Không thoái hóa" thay vì "thoái hóa độ 0" (đọc mâu thuẫn).
        findings.add(sideLabel + ": Không thoái hóa khớp gối (Kellgren-Lawrence độ 0).");
        return;
    }
    findings.add(sideLabel + ": Thoái hóa khớp gối độ " + grade + " (Kellgren-Lawrence).");
}
```

Chỉ nêu **độ KL và không gì khác** — không tự viết "gai xương", "hẹp khe
khớp", hay bất kỳ mô tả X-quang nào khác. Đây là quyết định có chủ đích: dấu
hiệu X-quang là việc của bác sĩ tự mô tả bằng tay, hệ thống không được tự
suy diễn nội dung y khoa vượt quá con số độ KL đã verify.

`composeConclusion()` gộp 2 độ KL thành 1 câu — code đầy đủ:

```java
public String composeConclusion(String leftKlGrade, String rightKlGrade) {
    Integer left = parseGrade(leftKlGrade);   // null nếu chuỗi rỗng hoặc ngoài [0,4].
    Integer right = parseGrade(rightKlGrade);
    if (left == null && right == null) {
        // Cả 2 bên đều chưa verify - câu duy nhất không nhắc tới "độ" nào cả.
        return "Không thấy hình ảnh thoái hóa khớp gối trên phim X-quang.";
    }
    if (left != null && right != null && left.equals(right)) {
        // 2 bên CÙNG một độ - gộp thành 1 câu "hai bên", không lặp lại số 2 lần.
        return left == 0
                ? "Không thấy hình ảnh thoái hóa khớp gối hai bên."  // độ 0: viết riêng, tránh
                                                                       // câu "thoái hóa độ 0" mâu thuẫn.
                : "Hình ảnh thoái hóa khớp gối hai bên độ " + left
                        + " theo phân loại Kellgren-Lawrence.";
    }
    // 2 bên KHÁC độ nhau (hoặc chỉ 1 bên có giá trị) - liệt kê riêng từng bên có giá trị.
    List<String> parts = new ArrayList<>();
    if (right != null) parts.add("gối phải độ " + right);  // phải luôn nhắc trước, khớp thứ
    if (left != null) parts.add("gối trái độ " + left);    // tự composeFindings() ở trên.
    return "Hình ảnh thoái hóa khớp gối: " + String.join(", ", parts)
            + " theo phân loại Kellgren-Lawrence.";
}
```

Tóm lại: nếu 2 bên bằng nhau thì viết gộp ("thoái hóa khớp gối hai bên độ
X"), khác nhau thì liệt kê riêng từng bên, cả 2 đều không xác định thì trả
câu "không thấy hình ảnh...".

### 7.2 Render PDF, logo, và các hàm định dạng

Nguồn: `PdfExportService.java:392-455` (`logoDataUri` 392-417, `renderPdf` 434-455).

```java
private String logoDataUri(String classpathLocation) {
    // Cache theo classpath location - logo đóng gói cùng jar, không đổi lúc runtime,
    // nên chỉ đọc + encode Base64 một lần cho suốt vòng đời ứng dụng.
    String cached = logoDataUriCache.get(classpathLocation);
    if (cached != null) return cached.isEmpty() ? null : cached;

    String dataUri = "";
    ClassPathResource resource = new ClassPathResource(classpathLocation);
    if (resource.exists()) {
        try (InputStream inputStream = resource.getInputStream()) {
            dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        } catch (Exception exception) {
            log.warn("Could not read report logo {}", classpathLocation, exception);
        }
    } else {
        // KHÔNG throw - thiếu logo chỉ làm letterhead trống ảnh, không chặn cả report.
        log.warn("Report logo {} is not packaged; rendering the letterhead without it", classpathLocation);
    }
    logoDataUriCache.put(classpathLocation, dataUri);
    return dataUri.isEmpty() ? null : dataUri;
}

private void renderPdf(String htmlContent, Path outputPath) throws Exception {
    try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(htmlContent, "/");
        ClassPathResource fontResource = new ClassPathResource("fonts/tahoma.ttf");
        if (fontResource.exists()) {
            // Tahoma giúp OpenHTMLtoPDF render tiếng Việt có dấu ổn định.
            builder.useFont(() -> {
                try { return fontResource.getInputStream(); }
                catch (java.io.IOException e) { throw new UncheckedIOException(e); }
            }, "Tahoma");
        } else {
            log.warn("Tahoma font not found in resources"); // không dừng, dùng font fallback.
        }
        builder.toStream(outputStream);
        builder.run();
    }
}
```

Ảnh logo được nhúng thẳng vào HTML dưới dạng `data:image/png;base64,...` (data
URI) chứ không phải URL — vì OpenHTMLtoPDF chạy offline, không tải được ảnh
qua mạng lúc render.

## 8. Đọc, List Và Bảo Vệ File PDF

Nguồn: `PdfExportService.java:175-213, 510-520`. Phần này **không đổi** so
với thiết kế trước — chỉ nhắc lại vì vẫn nằm trong luồng chính.

```java
@Transactional(readOnly = true)  // chỉ đọc: không có INSERT/UPDATE nào trong toàn bộ method.
public PageResponse<ReportListItemResponse> getGeneratedReports(Pageable pageable, String username) {
    User currentUser = getUser(username);  // ném IllegalArgumentException nếu username không tồn tại.
    // Đọc role TỪ DB (currentUser.getRole()), không tin role trong JWT - phòng trường hợp
    // quyền đổi sau khi token đã phát hành mà chưa hết hạn.
    String roleCode = currentUser.getRole() == null ? null : currentUser.getRole().getCode();
    Page<Report> reportPage;  // khai báo trước, gán trong từng nhánh if/else bên dưới.
    if ("DOCTOR".equalsIgnoreCase(roleCode)) {
        // Doctor chỉ list report thuộc examinations được gán cho mình - lọc ngay ở tầng
        // SQL (JOIN qua examination.doctor_id), không lọc bằng Java sau khi đã load hết.
        reportPage = reportRepository.findByExamination_Doctor_Id(currentUser.getId(), pageable);
    } else if (isDepartmentHead(currentUser)) {
        // Trưởng khoa: không lọc gì cả, thấy report của MỌI bác sĩ trong hệ thống.
        reportPage = reportRepository.findAll(pageable);
    } else {
        // Role khác DOCTOR/DEPARTMENT_HEAD/HEAD_OF_DEPARTMENT (VD ADMIN) không có quyền xem
        // danh sách report dù @PreAuthorize ở controller có lọt qua do cấu hình sai đi nữa -
        // đây là lớp kiểm tra thứ 2, độc lập với @PreAuthorize.
        throw new AccessDeniedException("Only doctors and department heads can view generated reports");
    }
    // .map(this::toListItemResponse): chuyển từng Report entity -> DTO ngay trên Page, giữ
    // nguyên thông tin phân trang (totalElements, totalPages...) của Page gốc.
    return PageResponse.of(reportPage.map(this::toListItemResponse));
}

@Transactional(readOnly = true)
public ReportFile getReportFileByExaminationId(Long examinationId, String username) {
    // "First...OrderByCreatedAtDesc": nếu ca có nhiều report (không nên xảy ra với thiết kế
    // "generate 1 lần" hiện tại, nhưng method này viết chung cho cả dữ liệu cũ), lấy mới nhất.
    Report report = reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Report not found for examination with id: " + examinationId));
    // Kiểm tra quyền SAU KHI đã có report - lấy đúng Examination của report này để check,
    // không phải Examination truyền từ ngoài vào (an toàn hơn nếu examinationId bị đánh tráo).
    authorizeReportAccess(report.getExamination(), getUser(username));
    Path reportPath = resolveReportPath(report);  // validate path traversal, xem bên dưới.
    if (!Files.isRegularFile(reportPath)) {
        // DB nói có report nhưng đĩa không còn file thật - phân biệt rõ với "not found" ở
        // trên bằng IllegalStateException (lỗi hệ thống) thay vì IllegalArgumentException
        // (lỗi input người dùng).
        throw new IllegalStateException("PDF file is missing for examination with id: " + examinationId);
    }
    Resource resource = new FileSystemResource(reportPath);
    // Tên file cũ có thể chưa được set (dữ liệu legacy) - fallback sang tên suy ra từ ID.
    String fileName = report.getFileName() == null || report.getFileName().isBlank()
            ? "report-" + examinationId + ".pdf" : report.getFileName();
    return new ReportFile(resource, fileName,
            report.getContentType() == null ? PDF_CONTENT_TYPE : report.getContentType(),
            // fileSize(reportPath): đọc kích thước thật từ đĩa nếu cột DB chưa có (legacy).
            report.getFileSize() == null ? fileSize(reportPath) : report.getFileSize());
}

private Path resolveReportPath(Report report) {
    if (report.getFilePath() == null || report.getFilePath().isBlank()) {
        // Row report tồn tại nhưng cột file_path rỗng - dữ liệu hỏng, không phải input sai.
        throw new IllegalStateException("Report file path is missing");
    }
    Path exportRoot = getExportRoot();  // thư mục gốc lưu PDF, từ app.pdf.export-dir.
    // .resolve() rồi .normalize(): nếu file_path chứa "../", normalize sẽ "rút gọn" nó,
    // để bước kiểm tra startsWith() ngay dưới phát hiện được việc đi ra ngoài exportRoot.
    Path reportPath = exportRoot.resolve(report.getFilePath()).normalize();
    // Metadata không được phép biến endpoint thành path traversal đọc file khác - nếu ai đó
    // (hoặc dữ liệu hỏng) làm file_path chứa "../../etc/passwd", normalize() ở trên sẽ đưa
    // reportPath ra ngoài exportRoot, và check này chặn lại trước khi Files.isRegularFile
    // chạm vào path đó.
    if (!reportPath.startsWith(exportRoot)) {
        throw new AccessDeniedException("Invalid report file path");
    }
    return reportPath;
}
```

## 9. Entity, DTO Và Repository Cần Nhớ

| Loại | Chi tiết có ảnh hưởng trực tiếp |
| --- | --- |
| `DiagnosisReview` | `ai_result_id` là unique `@OneToOne`; giữ predicted result, confirmed grade, decision, note, reviewer, time. |
| `DiagnosisReviewDecision` | `AI_CONFIRMED` hoặc `DOCTOR_ADJUSTED`. |
| `Examination` | `status`; **`findings`/`conclusion`** (mới, TEXT, thuộc examination - đây là nguồn autofill cho draft, KHÔNG phải `Report`); `finalDiagnosis` (vẫn đúng nghĩa gốc, được `report.clinical_summary` sao chép lại lúc generate); `maxPredictedGrade` (tính lại mỗi lần review, không liên quan report). |
| `Report` | Lưu examination, operating doctor, `clinical_summary` (= `examination.finalDiagnosis` tại thời điểm generate), relative file path/name, content type, size, time. **Không có cột `findings`/`conclusion`** - đã cân nhắc đặt ở đây lúc đầu nhưng chuyển sang `Examination` vì kết quả thuộc về ca khám, không thuộc về từng lần render PDF. |
| `AiResult` | `predictedGrade`, knee side, confidence, description, link review. |
| `GenerateReportRequest` | 10 field, tất cả `@Size`-bound, tất cả optional, không có `doctorName`/`clinicalDepartment`/letterhead. |
| `ReportDraftResponse` | Có cả field chỉ-đọc (`ministryName`...`rightKlGrade`) lẫn field editable (`documentNumber`...`signatureDate`) trong cùng 1 record - FE phải tự biết field nào gửi lại được khi confirm. |
| `XrayReportContentComposer` | Stateless `@Component`, không phụ thuộc DB - nhận thẳng 2 chuỗi độ KL, trả `List<String>`/`String`. |
| `XrayReportProperties` | `@ConfigurationProperties(prefix = "app.report")`, đăng ký qua `ReportConfiguration` - đổi letterhead chỉ cần sửa `application.yaml`, không sửa code. |
| `DiagnosisReviewRepository.findByAiResultId` | Là chìa khóa upsert review, không tạo duplicate. |
| `ExaminationRepository.findByIdForUpdate` | `@Lock(PESSIMISTIC_WRITE)` - khóa row examination trong transaction generate report. |
| `ReportRepository.findFirstByExaminationIdOrderByCreatedAtDesc` | Chọn report mới nhất - với model "generate 1 lần", luôn chỉ có đúng 1 report/examination nên kết quả ổn định. |

## 10. Tương Tác Với Database (MySQL/JPA)

Luồng review và export không tự nối chuỗi SQL trong service (trừ
`BusinessDataQueryService`, nằm ngoài phạm vi tài liệu này). Nó dùng Spring
Data JPA/Hibernate qua repository; SQL thực tế do Hibernate tạo có thể khác
chi tiết theo dialect, nhưng bảng, điều kiện và thứ tự đọc/ghi dưới đây đúng
theo code.

### 10.1 Quan hệ dữ liệu mà review/report đi qua

```text
examinations (1)
  <- dicom_instances.examination_id (N)
       <- ai_analyses.dicom_instance_id (1:1)
            <- ai_result.ai_analysis_id (N)
                 <- diagnosis_reviews.ai_result_id (1:1, unique)
                      -> diagnosis_reviews.examination_id (N:1)
                      -> diagnosis_reviews.doctor_id (N:1)

report.examination_id -> examinations.id
report.operating_doctor_id -> users.id

-- MỚI: findings/conclusion nằm NGAY TRÊN examinations, không phải bảng riêng,
-- không có foreign key nào trỏ tới report.
examinations.findings    TEXT NULL
examinations.conclusion  TEXT NULL
```

`examinations.findings`/`conclusion` được thêm bằng migration tay
`database/migrations/examination_result_conclusion_migration.sql`:

```sql
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS findings TEXT;
ALTER TABLE examinations ADD COLUMN IF NOT EXISTS conclusion TEXT;
```

Migration này **bắt buộc phải chạy tay trước khi deploy `prod`**, vì
`application-prod.yaml` dùng `spring.jpa.hibernate.ddl-auto: validate` (không
tự tạo cột) — thiếu migration thì app sẽ crash ngay lúc khởi động vì
Hibernate validate schema thấy cột bị thiếu so với entity. Ngược lại, `dev` và
`local` dùng `ddl-auto: update`, nên Hibernate tự `ALTER TABLE` thêm cột ngay
lần khởi động kế tiếp — không cần chạy migration tay ở 2 môi trường này.

### 10.2 Một transaction confirm/adjust thực hiện những query gì?

`confirmAiGrade()` và `adjustKlGrade()` đều có `@Transactional`. Trình tự logic:

| Bước | Lời gọi repository/JPA | Bảng đọc hoặc ghi |
| --- | --- | --- |
| 1 | `aiResultRepository.findById(aiResultId)` | Đọc `ai_result`; lazy relation có thể đọc tiếp `ai_analyses`, `dicom_instances`, `examinations`. |
| 2 | `doctorRepository.findByUsername(username)` | Đọc reviewer trong doctor/user mapping. |
| 3 | `diagnosisReviewRepository.findByAiResultId(id)` | Đọc `diagnosis_reviews` theo cột unique `ai_result_id`. |
| 4 | `diagnosisReviewRepository.save(review)` | Chưa có review: `INSERT diagnosis_reviews`; có review: `UPDATE diagnosis_reviews`. |
| 5 | `dicomInstanceRepository.findByExaminationId(id)` | Đọc mọi `dicom_instances` của examination để quyết định VERIFIED (lần 1). |
| 6 | `examinationRepository.save(examination)` | Nếu đủ điều kiện: `UPDATE examinations.status = VERIFIED`. |
| 7 | `dicomInstanceRepository.findByExaminationId(id)` | **MỚI**: đọc LẠI toàn bộ `dicom_instances` (lần 2, độc lập bước 5) để `recalculateMaxPredictedGrade()`. |
| 8 | `examinationRepository.save(examination)` | Nếu `maxGrade` đổi: `UPDATE examinations.max_predicted_grade`. |
| 9 | Commit | Mọi INSERT/UPDATE ở trên commit cùng nhau; runtime exception rollback toàn bộ. |

Pseudo-SQL để hình dung, không phải SQL Java hard-code:

```sql
SELECT * FROM ai_result WHERE id = :aiResultId;
SELECT * FROM diagnosis_reviews WHERE ai_result_id = :aiResultId;

INSERT INTO diagnosis_reviews (
    examination_id, doctor_id, ai_result_id,
    confirmed_kl_grade, decision, review_note, reviewed_at
) VALUES (...);
-- hoặc, lần review sau cho cùng aiResult:
UPDATE diagnosis_reviews
SET confirmed_kl_grade = :grade, decision = :decision, review_note = :note,
    doctor_id = :reviewerId, reviewed_at = :now
WHERE ai_result_id = :aiResultId;

UPDATE examinations SET status = 'VERIFIED' WHERE id = :examinationId;
UPDATE examinations SET max_predicted_grade = :maxGrade WHERE id = :examinationId;
```

`saveReview()` là upsert ở tầng application: SELECT trước bằng
`findByAiResultId`, sau đó save. Unique `ai_result_id` là hàng rào database
cuối cùng chống hai review row cho một AI result.

### 10.3 Một transaction `GET report-draft` thực hiện những query gì?

| Bước | Repository/JPA call | Ý nghĩa database |
| --- | --- | --- |
| 1 | `examinationRepository.findById(id)` | Đọc thường (KHÔNG `FOR UPDATE`) - draft chỉ đọc, không cần khóa row. |
| 2 | `userRepository.findByUsername(username)` | Đọc người xem draft, để in tên lên `doctorName` và kiểm tra quyền. |
| 3 | `dicomInstanceRepository.findByExaminationId(id)` | Đọc DICOM/AI/review để tính độ KL final (giống hệt bước dùng trong generate). |
| 4 | Không có INSERT/UPDATE nào | `@Transactional(readOnly = true)` - Hibernate có thể tối ưu bỏ qua dirty-checking. |

```sql
SELECT * FROM examinations WHERE id = :id;                    -- không FOR UPDATE
SELECT * FROM dicom_instances WHERE examination_id = :id;
-- các SELECT lazy tiếp theo cho ai_analyses/ai_result/diagnosis_reviews tùy Hibernate.
```

### 10.4 Một transaction `POST generate-report` (lần đầu, thành công) thực hiện những query gì?

| Bước | Repository/JPA call | Ý nghĩa database |
| --- | --- | --- |
| 1 | `examinationRepository.findByIdForUpdate(id)` | `SELECT ... FOR UPDATE` - serialize generate cùng examination. |
| 2 | `userRepository.findByUsername(username)` | Đọc người generate để kiểm tra quyền và gán `operating_doctor_id`. |
| 3 | `reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(id)` | Chỉ chạy khi status ĐÃ `REPORT_GENERATED` từ trước (nhánh phục hồi); lần đầu generate thì bỏ qua bước này vì status còn `VERIFIED`. |
| 4 | `dicomInstanceRepository.findByExaminationId(id)` | Đọc DICOM/AI/review để tính độ KL final, dùng cho autofill lẫn `leftKlGrade`/`rightKlGrade`. |
| 5 | `reportRepository.save(report)` | `INSERT` một row `report` với metadata file. |
| 6 | `examinationRepository.save(examination)` | `UPDATE` cả `findings`, `conclusion`, và `status = REPORT_GENERATED` trong CÙNG một câu UPDATE (Hibernate gom mọi field đổi trên 1 entity thành 1 UPDATE). |
| 7 | Commit | Sau commit, event report knowledge mới tới listener async; rollback sẽ không sync RAG (event chỉ thật sự "publish" khi transaction commit thành công). |

Pseudo-SQL phần chính:

```sql
SELECT * FROM examinations WHERE id = :id FOR UPDATE;
SELECT * FROM dicom_instances WHERE examination_id = :id;

INSERT INTO report (
    examination_id, operating_doctor_id, clinical_summary,
    file_path, file_name, content_type, file_size, created_at
) VALUES (...);

UPDATE examinations
SET findings = :findings, conclusion = :conclusion, status = 'REPORT_GENERATED'
WHERE id = :id;
```

File PDF không thuộc MySQL transaction. Vì vậy service dùng `try/catch` để
xóa file khi render lỗi, và `deleteFileIfTransactionRollsBack()` để xóa file
đã atomic-move khi transaction database rollback ở giai đoạn sau.

### 10.5 List/preview/download và lazy loading

| Operation | Query chính | Scope |
| --- | --- | --- |
| List report cho doctor | `findByExamination_Doctor_Id(userId, pageable)` | Chỉ report của examination gán cho doctor. |
| List report cho trưởng khoa | `findAll(pageable)` | Mọi report. |
| Preview/download | Latest report theo examination ID, sau đó load user/examination để `authorizeReportAccess`. | Doctor phải được gán, trưởng khoa được phép. |

`Report.examination`, `Examination.patient`, `Examination.doctor`, `AiResult`
và `DiagnosisReview` đều có association LAZY. Khi mapping list/report data
hoặc build PDF, Hibernate có thể sinh thêm SELECT. Đây là điểm cần profile
nếu danh sách lớn hoặc examination có nhiều DICOM/result.

### 10.6 Query chẩn đoán dữ liệu trực tiếp trong MySQL

```sql
-- Workflow state của ca khám, gồm cả findings/conclusion đã lưu (nếu có).
SELECT e.id, e.encounter_code, e.status, e.final_diagnosis, e.doctor_id,
       e.study_date, e.study_time, e.findings, e.conclusion, e.max_predicted_grade
FROM examinations e
WHERE e.id = :examinationId;

-- NULL review nghĩa AI result đó chưa được xác nhận.
SELECT di.id AS dicom_instance_id, di.status AS dicom_status,
       ar.id AS ai_result_id, ar.predicted_grade, ar.knee_side,
       dr.id AS review_id, dr.confirmed_kl_grade, dr.decision,
       dr.review_note, dr.doctor_id AS reviewed_by, dr.reviewed_at
FROM dicom_instances di
LEFT JOIN ai_analyses aa ON aa.dicom_instance_id = di.id
LEFT JOIN ai_result ar ON ar.ai_analysis_id = aa.id
LEFT JOIN diagnosis_reviews dr ON dr.ai_result_id = ar.id
WHERE di.examination_id = :examinationId
ORDER BY di.id, ar.id;

-- Metadata report và vị trí file tương ứng. clinical_summary ở đây LÀ final_diagnosis
-- tại thời điểm generate, KHÔNG PHẢI conclusion - đọc mục 6.1 nếu cần lý do.
SELECT r.id, r.examination_id, r.operating_doctor_id, r.clinical_summary, r.file_path,
       r.file_name, r.file_size, r.content_type, r.created_at
FROM report r
WHERE r.examination_id = :examinationId
ORDER BY r.created_at DESC;
```

Các query này chỉ dùng để debug. Application vẫn phải đi qua service/repository
để giữ authorization, transaction và file cleanup nhất quán.

## 11. Nhánh Lỗi Và Test

| Tình huống | Kết quả hiện tại |
| --- | --- |
| Grade null, < 0, > 4 | `400` với message grade không hợp lệ. |
| Adjust thiếu note | `400`; confirm không cần note từ request. |
| Bác sĩ không được gán case | `403` tại review/draft/generate service. |
| Report đã sinh, review lại | `400` "Cannot review an examination after its report has been generated". |
| Report đã sinh, mở lại `GET report-draft` | `400` "Report has already been generated for this examination; view the confirmed result via the report preview or download endpoint". |
| Report đã sinh, gọi lại `POST generate-report` (còn file) | `200`, trả report cũ y nguyên, body bị bỏ qua hoàn toàn, không tạo file mới. |
| Report đã sinh nhưng file bị xóa khỏi đĩa, gọi lại `POST generate-report` | Render lại thật sự (áp dụng `request` mới nếu có gửi) - đây là NGOẠI LỆ DUY NHẤT của "chỉ generate 1 lần". |
| Một DICOM thành công chưa review | Examination giữ `NEED_VERIFY`; draft/generate bị `400`. |
| DICOM AI failed song song DICOM thành công | Failed instance bị bỏ qua khi xét VERIFIED/export/maxPredictedGrade. |
| File PDF metadata có path đi ra ngoài export dir | `403 Invalid report file path`. |
| Render/lưu PDF lỗi | File temp/output được dọn; transaction ném runtime error. |
| `documentNumber`/`conclusion`/... vượt quá `@Size` | `400` validation lỗi trước khi service chạy (Bean Validation ở controller). |

Các test quan trọng:

- `DiagnosisReviewServiceTest` (16 test): confirm/adjust, ownership, trưởng
  khoa, mọi instance thành công phải review, failed instance được bỏ qua,
  cấm review sau report, validation, `recalculateMaxPredictedGrade`, audit
  annotation.
- `DiagnosisReviewControllerRbacTest` (7 test): role/permission ở tầng
  controller cho 2 endpoint confirm/adjust.
- `PdfExportServiceTest` (29 test): quyền draft/generate riêng biệt, khóa
  draft sau khi generate (`requireDraftable`), autofill từ độ KL lần đầu,
  autofill từ `Examination.findings/conclusion` đã lưu ở lần sau, overlay
  `GenerateReportRequest` từng field độc lập, chữ ký luôn là người gọi API,
  idempotency khi đã `REPORT_GENERATED`, phục hồi khi mất file, `clinical_
  summary` giữ đúng `finalDiagnosis`, event RAG sau report.
- `XrayReportTemplateTest` (6 test): render PDF thật, mọi block hiển thị,
  chữ bác sĩ tự sửa thay thế đúng chữ autofill, field rỗng không làm crash,
  logo nhúng được/thiếu logo vẫn render.
- `XrayReportContentComposerTest` (10 test): text theo từng độ KL 0-4, một
  bên/hai bên, độ ngoài khoảng hợp lệ.
- `ReportControllerTest` (4 test), `ControllerRbacTest` (31 test),
  `OpenApiDocumentationTest` (4 test): hợp đồng HTTP, phân quyền, tài liệu
  OpenAPI đăng ký đủ cho mọi method controller.
- `ReportListServiceTest` (3 test): doctor chỉ thấy report case của mình,
  trưởng khoa thấy tất cả.

## 12. Thứ Tự Debug Khuyến Nghị

1. Mở examination, kiểm tra `status`, doctor được gán, danh sách DICOM
   instance, và **`findings`/`conclusion` đã có sẵn dữ liệu chưa** (quyết
   định draft lần tới autofill từ đâu).
2. Với từng `AiResult`, kiểm tra có `DiagnosisReview` hay chưa và
   grade/note là gì.
3. Nếu không thành `VERIFIED`, theo
   `markVerifiedWhenAllLatestResultsAreReviewed()` xem instance nào
   `AI_FAILED`, chưa `GET_RESULTED`, thiếu analysis/result hoặc result chưa
   review.
4. Nếu `GET report-draft` trả `400`, kiểm tra đúng `status` hiện tại: nếu
   là `REPORT_GENERATED` thì đây là hành vi ĐÚNG THIẾT KẾ (draft bị khóa
   vĩnh viễn), không phải bug.
5. Nếu `POST generate-report` trả về report cũ dù đã gửi chữ mới, kiểm tra
   `status` — nếu đã `REPORT_GENERATED` và file PDF vẫn còn trên đĩa thì
   đây cũng là hành vi ĐÚNG THIẾT KẾ (idempotent), không phải bug; muốn
   test lại từ đầu phải dùng ca khám `VERIFIED` khác chưa từng generate.
6. Nếu PDF sinh lỗi, kiểm tra export dir, font `tahoma.ttf`, logo
   `report/logo-hospital.png`, template, và log OpenHTMLtoPDF.
7. Nếu preview/download lỗi, kiểm tra `Report.filePath`, root traversal
   guard, file tồn tại thật và Bearer token của request frontend.
8. Nếu deploy `prod` crash lúc khởi động sau khi thêm tính năng này, kiểm
   tra migration `examination_result_conclusion_migration.sql` đã chạy tay
   trên DB prod chưa — `prod` không tự `ALTER TABLE` như `dev`/`local`.
