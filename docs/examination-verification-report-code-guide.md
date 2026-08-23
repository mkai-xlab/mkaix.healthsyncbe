# Hướng Dẫn Code: Xác Minh Examination Đến Xuất Report

Tài liệu này giải thích luồng bác sĩ/trưởng khoa xác nhận kết quả AI, chuyển ca
khám sang `VERIFIED`, sinh PDF, lưu report, preview/download và đồng bộ report
vào knowledge base. Source Java trong các block được giữ nguyên cấu trúc; comment
tiếng Việt giải thích từng quyết định quan trọng.

## 1. Phạm Vi Và Sơ Đồ Luồng

Luồng này bắt đầu **sau khi AI prediction đã hoàn tất**. Nó không bao gồm upload
DICOM hay gọi FastAPI prediction; điều kiện đầu vào là examination có các
`DicomInstance` ở `GET_RESULTED` và các `AiResult` cần bác sĩ review.

```text
AiResult / DicomInstance GET_RESULTED
  -> PUT /ai/results/{aiResultId}/confirm
       hoặc PUT /ai/results/{aiResultId}/kl-grade
  -> DiagnosisReviewServiceImpl
       -> DiagnosisReview upsert
       -> kiểm tra toàn bộ kết quả AI mới nhất đã review?
       -> Examination: NEED_VERIFY -> VERIFIED
  -> POST /examinations/{id}/generate-report
  -> PdfExportService
       -> khóa Examination trong MySQL
       -> kiểm tra quyền + VERIFIED
       -> build PDF data từ Patient/Examination/DICOM/AiResult/DiagnosisReview
       -> Thymeleaf HTML -> OpenHTMLtoPDF -> file tạm -> file PDF
       -> lưu Report -> Examination: REPORT_GENERATED
       -> event ReportKnowledgeSyncRequestedEvent (sau commit)
  -> GET /reports/{examinationId}/preview | /download
```

Trạng thái hợp lệ theo code:

| Trạng thái | Ý nghĩa trong luồng này |
| --- | --- |
| `AI_PROCESSING` | AI chưa xong; chưa được review/sinh report. |
| `AI_FAILED` | Ca AI thất bại; không đủ điều kiện export report bình thường. |
| `NEED_VERIFY` | Có kết quả AI cần bác sĩ xác nhận/điều chỉnh. |
| `VERIFIED` | Mọi DICOM AI thành công đều có `DiagnosisReview`; có thể generate report. |
| `REPORT_GENERATED` | Report đã có metadata và file PDF; không được review lại. |

## 2. File Coverage

| File | Vai trò |
| --- | --- |
| `DiagnosisReviewController.java` | Hai HTTP endpoint confirm/adjust KL grade. |
| `DiagnosisReviewServiceImpl.java` | Logic review, ownership, upsert review và chuyển `VERIFIED`. |
| `DiagnosisReview.java`, `DiagnosisReviewDecision.java` | Bản ghi final clinical decision cho đúng một `AiResult`. |
| `PdfExportService.java` | Toàn bộ sinh/lưu/list/đọc PDF và kiểm tra quyền report. |
| `ReportController.java` | HTTP generate/list/preview/download PDF. |
| `Report.java`, `ReportRepository.java` | Metadata PDF trong MySQL. |
| `Examination.java`, `ExaminationStatus.java` | Trạng thái ca khám và thông tin lâm sàng đưa vào report. |
| `AiAnalysis.java`, `AiResult.java`, `DicomInstance.java` | Chuỗi dữ liệu AI được review và xuất PDF. |
| `PdfReportDataDto.java`, `report-template.html` | Contract Java-to-Thymeleaf của PDF. |
| `DiagnosisReviewServiceTest`, `PdfExportServiceTest`, `PdfReportTemplateTest`, `ReportListServiceTest` | Bằng chứng tự động cho các nhánh quan trọng. |

## 3. HTTP Và Phân Quyền

### 3.1 Review kết quả AI

```java
@PutMapping("/{aiResultId}/confirm")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('CONFIRM_CONCLUSION'))")
public ResponseEntity<DiagnosisReviewResponse> confirmAiGrade(
        @PathVariable Long aiResultId, Principal principal) {
    // Không có request body: final grade = AI predicted grade.
    return ResponseEntity.ok(diagnosisReviewService.confirmAiGrade(aiResultId, principal.getName()));
}

@PutMapping("/{aiResultId}/kl-grade")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('OVERRIDE_AI_GRADE'))")
public ResponseEntity<DiagnosisReviewResponse> adjustKlGrade(
        @PathVariable Long aiResultId,
        @Valid @RequestBody AdjustKlGradeRequest request,
        Principal principal) {
    // @Valid kiểm tra grade 0..4 và reviewNote không rỗng/tối đa 2.000 ký tự.
    return ResponseEntity.ok(diagnosisReviewService.adjustKlGrade(aiResultId, request, principal.getName()));
}
```

Trưởng khoa được service cho phép review ca không gán cho mình. Bác sĩ thường
phải vừa có authority ở controller, vừa là bác sĩ được gán vào examination ở
service. Hai lớp kiểm tra này bổ sung cho nhau.

### 3.2 Generate, preview và download report

```java
@PostMapping("/examinations/{id}/generate-report")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
public ResponseEntity<ReportResponse> generatePdfReport(@PathVariable Long id, Principal principal) {
    return ResponseEntity.ok(pdfExportService.generateAndSavePdfReport(id, principal.getName()));
}

@GetMapping("/reports/{examinationId}/preview")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('GENERATE_PDF_REPORT'))")
public ResponseEntity<Resource> previewReport(@PathVariable Long examinationId, Principal principal) {
    // inline Content-Disposition: browser có thể render PDF nhưng response không được cache.
    return fileResponse(pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), false);
}

@GetMapping("/reports/{examinationId}/download")
@PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') "
        + "or (hasRole('DOCTOR') and hasAuthority('EXPORT_DOWNLOAD_PDF'))")
@LogAction("DOWNLOAD_PDF_REPORT")
public ResponseEntity<Resource> downloadReport(@PathVariable Long examinationId, Principal principal) {
    // attachment Content-Disposition; @LogAction ghi audit cho hành động tải xuống.
    return fileResponse(pdfExportService.getReportFileByExaminationId(examinationId, principal.getName()), true);
}
```

`previewUrl` và `downloadUrl` dùng **examinationId**, không dùng reportId. Service
luôn tìm report mới nhất theo examination bằng
`findFirstByExaminationIdOrderByCreatedAtDesc`.

## 4. Review Và Chuyển VERIFIED

### 4.1 Confirm hoặc adjust

Nguồn: `DiagnosisReviewServiceImpl.java`.

```java
@Override
@Transactional
@LogAction("CONFIRM_AI_GRADE")
public DiagnosisReviewResponse confirmAiGrade(Long aiResultId, String username) {
    // true cho phép trưởng khoa review case không được gán trực tiếp.
    ReviewContext context = loadReviewContext(aiResultId, username, true);
    validateGrade(context.aiResult().getPredictedGrade());
    // Confirm không sửa dự đoán AI: confirmed = predicted.
    return saveReview(
            context,
            context.aiResult().getPredictedGrade(),
            DiagnosisReviewDecision.AI_CONFIRMED,
            "AI result confirmed");
}

@Override
@Transactional
@LogAction("OVERRIDE_AI_GRADE")
public DiagnosisReviewResponse adjustKlGrade(
        Long aiResultId,
        AdjustKlGradeRequest request,
        String username) {
    // Service kiểm tra lại dù controller đã có @Valid: bảo vệ khi service được gọi nội bộ/test.
    if (request == null || request.reviewNote() == null || request.reviewNote().isBlank()) {
        throw new IllegalArgumentException("Review note is required");
    }
    validateGrade(request.confirmedKlGrade());

    ReviewContext context = loadReviewContext(aiResultId, username, true);
    return saveReview(
            context,
            request.confirmedKlGrade(),
            DiagnosisReviewDecision.DOCTOR_ADJUSTED,
            request.reviewNote().trim());
}
```

`DiagnosisReview` có quan hệ `@OneToOne` unique với `AiResult`. Vì vậy một lần
confirm lần hai hoặc adjust sau confirm không tạo review thứ hai; nó cập nhật row
cũ thành quyết định mới nhất.

### 4.2 Upsert review và điều kiện VERIFIED

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
```

Nói ngắn gọn: `VERIFIED` không có nghĩa "một AI result đã được review". Nó có
nghĩa **mọi kết quả AI của mọi DICOM thành công** trong examination đã có review.

### 4.3 Ownership, freeze sau report và các helper

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

## 5. Generate PDF: Transaction, File Và Report Metadata

### 5.1 Hàm chính `generateAndSavePdfReport`

Nguồn: `PdfExportService.java:80-148`.

```java
@Transactional
@LogAction("GENERATE_PDF_REPORT")
public ReportResponse generateAndSavePdfReport(Long examinationId, String username) {
    // Pessimistic lock tránh hai request đồng thời sinh hai report cho cùng examination.
    Examination examination = examinationRepository.findByIdForUpdate(examinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Examination not found with id: " + examinationId));
    User currentUser = getUser(username);
    authorizeReportAccess(examination, currentUser);

    if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
        Report existingReport = reportRepository
                .findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
                .filter(this::reportFileExists)
                .orElse(null);
        if (existingReport != null) {
            // Idempotent: không render PDF mới; chỉ kích hoạt lại sync RAG của report đã có.
            eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(existingReport.getId()));
            return toResponse(existingReport);
        }
    }

    // Chỉ VERIFIED (hoặc trạng thái report cũ thiếu file) được đi tiếp.
    if (examination.getStatus() != ExaminationStatus.VERIFIED
            && examination.getStatus() != ExaminationStatus.REPORT_GENERATED) {
        throw new IllegalArgumentException("Examination must be verified before generating its report");
    }

    Patient patient = examination.getPatient();
    FinalAiResults finalAiResults = buildFinalAiResults(examinationId);
    PdfReportDataDto dataDto = buildReportData(examination, patient, finalAiResults);

    Context context = new Context();
    context.setVariable("data", dataDto); // đúng tên `${data...}` dùng trong template HTML
    String htmlContent = templateEngine.process("pdf/report-template", context);

    Path exportRoot = getExportRoot();
    String fileName = buildFileName(examination);
    Path outputPath = exportRoot.resolve(fileName).normalize();
    Path temporaryPath = null;

    try {
        Files.createDirectories(exportRoot);
        temporaryPath = Files.createTempFile(exportRoot, ".report-", ".tmp");
        renderPdf(htmlContent, temporaryPath);
        // Chỉ sau render thành công mới đổi tên temp thành tên PDF nhìn thấy được.
        moveAtomically(temporaryPath, outputPath);
        temporaryPath = null;
        deleteFileIfTransactionRollsBack(outputPath);

        Report report = new Report();
        report.setExamination(examination);
        report.setOperatingDoctor(currentUser);
        report.setClinicalSummary(examination.getFinalDiagnosis());
        report.setFilePath(fileName); // relative name, không lưu absolute path trong DB
        report.setFileName(fileName);
        report.setContentType(PDF_CONTENT_TYPE);
        report.setFileSize(Files.size(outputPath));
        report.setCreatedAt(LocalDateTime.now());
        Report savedReport = reportRepository.save(report);
        eventPublisher.publishEvent(new ReportKnowledgeSyncRequestedEvent(savedReport.getId()));

        examination.setStatus(ExaminationStatus.REPORT_GENERATED);
        examinationRepository.save(examination);
        return toResponse(savedReport);
    } catch (Exception exception) {
        // Dọn file tạm hoặc file output khi render/DB flow lỗi.
        deleteQuietly(temporaryPath);
        deleteQuietly(outputPath);
        throw new RuntimeException("Failed to generate PDF: " + exception.getMessage(), exception);
    }
}
```

## 6. Dữ Liệu Đưa Vào Template Và PDF

`buildFinalAiResults()` duyệt mọi DICOM instance của examination. Instance
`AI_FAILED` bị bỏ qua; instance khác phải có `AiAnalysis`, có `AiResult`, và
mỗi `AiResult` phải có `DiagnosisReview`, nếu không generation dừng với `400`.
Nó lấy `confirmedKlGrade` làm final grade, giữ `predictedGrade` để so sánh, cộng
duration mỗi `AiAnalysis` đúng một lần và có thể nhúng Grad-CAM thành Base64.

```java
private PdfReportDataDto buildReportData(
        Examination examination, Patient patient, FinalAiResults finalAiResults) {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    List<PdfReportDataDto.AiResultExportDto> aiResults = finalAiResults.results();
    return PdfReportDataDto.builder()
            .patientCode(patient.getPatientCode())
            .patientName(patient.getFullName())
            .dob(patient.getDob() != null ? patient.getDob().format(dateFormatter) : "")
            .age(formatAge(patient, examination))
            .gender(patient.getGender() != null ? patient.getGender().name() : "")
            .address(valueOrBlank(patient.getAddress()))
            .encounterCode(valueOrBlank(examination.getEncounterCode()))
            .studyDateTime(formatStudyDateTime(examination))
            .visitTime(examination.getVisitTime() != null ? examination.getVisitTime().format(dateTimeFormatter) : "")
            .doctorName(examination.getDoctor() != null ? valueOrBlank(examination.getDoctor().getFullName()) : "")
            .clinicalNotes(valueOrBlank(examination.getClinicalNotes()))
            .finalDiagnosis(valueOrBlank(examination.getFinalDiagnosis()))
            // Mỗi bên lấy KL grade lớn nhất trong các result của cùng bên.
            .leftKlGrade(finalGradeForSide(aiResults, "LEFT"))
            .rightKlGrade(finalGradeForSide(aiResults, "RIGHT"))
            .processingTime(finalAiResults.totalDurationMillis() == null
                    ? "" : formatDuration(finalAiResults.totalDurationMillis()))
            .aiResults(aiResults)
            .build();
}
```

### 6.1 Toàn bộ `buildFinalAiResults`: vì sao report không thể bỏ sót review

```java
private FinalAiResults buildFinalAiResults(Long examinationId) {
    List<PdfReportDataDto.AiResultExportDto> results = new ArrayList<>();
    // Một AiAnalysis có thể bị tham chiếu nhiều lần; chỉ cộng duration một lần theo ID.
    Set<Long> countedAnalysisIds = new HashSet<>();
    long totalDurationMillis = 0L;
    boolean hasDuration = false;

    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(examinationId);
    if (instances.isEmpty()) {
        throw new IllegalArgumentException("Examination has no AI results to export");
    }

    for (DicomInstance instance : instances) {
        if (instance.getStatus() == DicomInstanceStatus.AI_FAILED) {
            // Policy: bỏ qua ảnh AI thất bại; không export một "final result" giả cho nó.
            continue;
        }

        AiAnalysis latestAnalysis = instance.getAiAnalysis();
        if (latestAnalysis == null
                || latestAnalysis.getAiResults() == null
                || latestAnalysis.getAiResults().isEmpty()) {
            throw new IllegalArgumentException(
                    "DICOM instance with ID " + instance.getId() + " has no AI results to export");
        }

        if (latestAnalysis.getDuration() != null
                && (latestAnalysis.getId() == null || countedAnalysisIds.add(latestAnalysis.getId()))) {
            totalDurationMillis += latestAnalysis.getDuration();
            hasDuration = true;
        }

        for (AiResult aiResult : latestAnalysis.getAiResults()) {
            DiagnosisReview review = aiResult.getDiagnosisReview();
            if (review == null) {
                // Đây là lớp phòng thủ cuối, dù VERIFIED lẽ ra đã bảo đảm điều này.
                throw new IllegalArgumentException(
                        "AI result with ID " + aiResult.getId() + " has not been confirmed");
            }

            results.add(PdfReportDataDto.AiResultExportDto.builder()
                    .dicomInstanceId(dicomIdentifier(instance))
                    // ưu tiên side từ AI result, fallback DICOM image laterality
                    .kneeSide(resolveKneeSide(aiResult, instance))
                    // final clinical grade luôn từ review, không phải raw prediction
                    .klGrade(String.valueOf(review.getConfirmedKlGrade()))
                    .aiPredictedGrade(String.valueOf(aiResult.getPredictedGrade()))
                    .decision(review.getDecision().name())
                    .confidence(formatConfidence(aiResult.getConfidence()))
                    .inferenceTime(formatDuration(latestAnalysis.getDuration()))
                    .modality(valueOrBlank(instance.getModality()))
                    .imageFormat("DICOM")
                    // Các field reader chi tiết hiện chưa được backend thu thập nên xuất chuỗi rỗng.
                    .manufacturer("")
                    .acquisitionPosition("")
                    .imageQuality("")
                    .readerOneOsteophyte("")
                    .readerTwoOsteophyte("")
                    .readerOneJointSpace("")
                    .readerTwoJointSpace("")
                    .readerOneSubchondralSclerosis("")
                    .readerTwoSubchondralSclerosis("")
                    .readerOneBoneDeformity("")
                    .readerTwoBoneDeformity("")
                    .readerOneKlGrade("")
                    .readerTwoKlGrade("")
                    .consensusKlGrade(String.valueOf(review.getConfirmedKlGrade()))
                    .readerOneProcessingTime("")
                    .readerTwoProcessingTime("")
                    .osteophyteDetection("")
                    .jointSpaceDetection("")
                    .comparisonResult(formatComparison(
                            aiResult.getPredictedGrade(), review.getConfirmedKlGrade()))
                    .errorAnalysisNote(valueOrBlank(review.getReviewNote()))
                    .interpretation(aiResult.getDescription())
                    .reviewNote(review.getReviewNote())
                    .gradcamBase64(fetchImageAsBase64(
                            aiResult.getStorageHeatmapFilePath() != null
                                    ? aiResult.getStorageHeatmapFilePath()
                                    : aiResult.getGradcamImage() != null
                                            ? aiResult.getGradcamImage().getFilePath()
                                            : null))
                    .build());
        }
    }

    if (results.isEmpty()) {
        // Toàn bộ instance đều AI_FAILED cũng không tạo được report lâm sàng.
        throw new IllegalArgumentException("Examination has no successful AI results to export");
    }
    return new FinalAiResults(results, hasDuration ? totalDurationMillis : null);
}

private String finalGradeForSide(
        List<PdfReportDataDto.AiResultExportDto> aiResults, String expectedSide) {
    // Một bên có nhiều result: report hiển thị mức độ final cao nhất của bên đó.
    return aiResults.stream()
            .filter(result -> result != null && expectedSide.equals(normalizeKneeSide(result.getKneeSide())))
            .map(PdfReportDataDto.AiResultExportDto::getKlGrade)
            .filter(grade -> grade != null && !grade.isBlank())
            .map(Integer::valueOf)
            .max(Integer::compareTo)
            .map(String::valueOf)
            .orElse("");
}

private String resolveKneeSide(AiResult aiResult, DicomInstance instance) {
    String side = valueOrBlank(aiResult.getKneeSide());
    if (side.isBlank()) side = valueOrBlank(instance.getImageLaterality());
    return normalizeKneeSide(side);
}

private String normalizeKneeSide(String side) {
    if (side == null) return "";
    // Bỏ dấu để nhận được "trái"/"phải" lẫn LEFT/RIGHT/L/R.
    String normalized = Normalizer.normalize(side, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .trim()
            .toUpperCase(Locale.ROOT);
    if (normalized.equals("L") || normalized.equals("LEFT")
            || normalized.equals("TRAI") || normalized.equals("GOI TRAI")) return "LEFT";
    if (normalized.equals("R") || normalized.equals("RIGHT")
            || normalized.equals("PHAI") || normalized.equals("GOI PHAI")) return "RIGHT";
    return normalized;
}
```

### 6.2 Render PDF, file tạm, rollback và ảnh Grad-CAM

```java
private void renderPdf(String htmlContent, Path outputPath) throws Exception {
    try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(htmlContent, "/");

        ClassPathResource fontResource = new ClassPathResource("fonts/tahoma.ttf");
        if (fontResource.exists()) {
            // Tahoma giúp OpenHTMLtoPDF render tiếng Việt ổn định.
            builder.useFont(() -> {
                try {
                    return fontResource.getInputStream();
                } catch (java.io.IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }, "Tahoma");
        } else {
            // Không dừng ngay; renderer dùng font fallback, có thể làm lỗi dấu tiếng Việt.
            log.warn("Tahoma font not found in resources");
        }
        builder.toStream(outputStream);
        builder.run();
    }
}

private void moveAtomically(Path source, Path target) throws Exception {
    try {
        // Không có thời điểm client thấy PDF đang ghi dở khi filesystem hỗ trợ atomic move.
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
        // Fallback cho filesystem không hỗ trợ, vẫn thay thế target hoàn chỉnh.
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}

private void deleteFileIfTransactionRollsBack(Path path) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCompletion(int status) {
            // File đã move nhưng DB transaction rollback thì phải xóa để không có orphan PDF.
            if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(path);
        }
    });
}

private String fetchImageAsBase64(String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) return null;
    try {
        byte[] bytes;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            try (InputStream inputStream = new URL(imageUrl).openStream()) {
                bytes = inputStream.readAllBytes();
            }
        } else {
            bytes = Files.readAllBytes(Paths.get(imageUrl));
        }
        return Base64.getEncoder().encodeToString(bytes);
    } catch (Exception exception) {
        // Grad-CAM mất không làm hỏng toàn bộ report; field tương ứng là null.
        log.error("Could not fetch image for PDF: {}", imageUrl, exception);
        return null;
    }
}
```

`buildFileName()` thay mọi ký tự nguy hiểm trong `encounterCode` bằng `_`, fallback
sang examination ID nếu mã trống, rồi thêm 8 ký tự UUID. `formatConfidence()` đổi
xác suất 0..1 thành phần trăm hai chữ số thập phân; `formatComparison()` trả
`MATCH`, `AI_HIGHER` hoặc `AI_LOWER`; `formatStudyDateTime()` không tự bịa giờ
00:00 khi chỉ có ngày chụp.

Template [report-template.html](../src/main/resources/templates/pdf/report-template.html)
dùng `th:text="${data...}"` để bind các field này. Nó hiển thị watermark
HealthSync/cảnh báo AI, thông tin bệnh nhân, DICOM metadata, final KL grade trái
phải, processing time và chữ ký bác sĩ. Các ô assessment thủ công hiện để trống
trong template vì DTO hiện không có dữ liệu reader cho chúng.

## 7. Đọc, List Và Bảo Vệ File PDF

```java
@Transactional(readOnly = true)
public PageResponse<ReportListItemResponse> getGeneratedReports(Pageable pageable, String username) {
    User currentUser = getUser(username);
    String roleCode = currentUser.getRole() == null ? null : currentUser.getRole().getCode();
    Page<Report> reportPage;
    if ("DOCTOR".equalsIgnoreCase(roleCode)) {
        // Doctor chỉ list report thuộc examinations được gán cho mình.
        reportPage = reportRepository.findByExamination_Doctor_Id(currentUser.getId(), pageable);
    } else if (isDepartmentHead(currentUser)) {
        reportPage = reportRepository.findAll(pageable);
    } else {
        throw new AccessDeniedException("Only doctors and department heads can view generated reports");
    }
    return PageResponse.of(reportPage.map(this::toListItemResponse));
}

@Transactional(readOnly = true)
public ReportFile getReportFileByExaminationId(Long examinationId, String username) {
    Report report = reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(examinationId)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Report not found for examination with id: " + examinationId));
    authorizeReportAccess(report.getExamination(), getUser(username));

    Path reportPath = resolveReportPath(report);
    if (!Files.isRegularFile(reportPath)) {
        throw new IllegalStateException("PDF file is missing for examination with id: " + examinationId);
    }
    Resource resource = new FileSystemResource(reportPath);
    String fileName = report.getFileName() == null || report.getFileName().isBlank()
            ? "report-" + examinationId + ".pdf" : report.getFileName();
    return new ReportFile(resource, fileName,
            report.getContentType() == null ? PDF_CONTENT_TYPE : report.getContentType(),
            report.getFileSize() == null ? fileSize(reportPath) : report.getFileSize());
}

private Path resolveReportPath(Report report) {
    if (report.getFilePath() == null || report.getFilePath().isBlank()) {
        throw new IllegalStateException("Report file path is missing");
    }
    Path exportRoot = getExportRoot();
    Path reportPath = exportRoot.resolve(report.getFilePath()).normalize();
    // Metadata không được phép biến endpoint thành path traversal đọc file khác.
    if (!reportPath.startsWith(exportRoot)) {
        throw new AccessDeniedException("Invalid report file path");
    }
    return reportPath;
}
```

## 8. Entity, DTO Và Repository Cần Nhớ

| Loại | Chi tiết có ảnh hưởng trực tiếp |
| --- | --- |
| `DiagnosisReview` | `ai_result_id` là unique `@OneToOne`; giữ predicted result, confirmed grade, decision, note, reviewer, time. |
| `DiagnosisReviewDecision` | `AI_CONFIRMED` hoặc `DOCTOR_ADJUSTED`. |
| `Examination` | `status`, `finalDiagnosis`, `studyDate/studyTime`, `visitTime`, `doctor`, `patient` được report đọc. |
| `Report` | Lưu examination, operating doctor, clinical summary, relative file path/name, content type, size, time. |
| `AiResult` | `predictedGrade`, knee side, confidence, description, Grad-CAM và link review. |
| `AdjustKlGradeRequest` | Grade 0..4 và note bắt buộc/tối đa 2.000 ký tự. |
| `DiagnosisReviewResponse` | Trả cả predicted và confirmed grade để frontend không mất dấu quyết định AI ban đầu. |
| `ReportResponse` | Trả report metadata và URL preview/download theo examination ID. |
| `DiagnosisReviewRepository.findByAiResultId` | Là chìa khóa upsert review, không tạo duplicate. |
| `ExaminationRepository.findByIdForUpdate` | Khóa row examination trong transaction generate report. |
| `ReportRepository.findFirstByExaminationIdOrderByCreatedAtDesc` | Chọn report mới nhất để idempotent generation/preview/download. |

## 9. Tương Tác Với Database (MySQL/JPA)

Luồng review và export không tự nối chuỗi SQL trong service. Nó dùng Spring Data
JPA/Hibernate qua repository; SQL thực tế do Hibernate tạo có thể khác chi tiết
theo dialect, nhưng bảng, điều kiện và thứ tự đọc/ghi dưới đây đúng theo code.

### 9.1 Quan hệ dữ liệu mà review/report đi qua

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
```

`DiagnosisReview` giữ cả `aiResult`, `examination` và `doctor`. Examination có
thể suy ra từ chuỗi AiResult/AiAnalysis/DICOM, nhưng cột `examination_id` trong
review giúp truy vấn/audit trực tiếp và được đặt `nullable=false`.

### 9.2 Một transaction confirm/adjust thực hiện những query gì?

`confirmAiGrade()` và `adjustKlGrade()` đều có `@Transactional`. Trình tự logic:

| Bước | Lời gọi repository/JPA | Bảng đọc hoặc ghi |
| --- | --- | --- |
| 1 | `aiResultRepository.findById(aiResultId)` | Đọc `ai_result`; khi đi qua relation LAZY có thể đọc tiếp `ai_analyses`, `dicom_instances`, `examinations`. |
| 2 | `doctorRepository.findByUsername(username)` | Đọc reviewer trong doctor/user mapping. |
| 3 | `diagnosisReviewRepository.findByAiResultId(id)` | Đọc `diagnosis_reviews` theo cột unique `ai_result_id`. |
| 4 | `diagnosisReviewRepository.save(review)` | Chưa có review: `INSERT diagnosis_reviews`; có review: `UPDATE diagnosis_reviews`. |
| 5 | `dicomInstanceRepository.findByExaminationId(id)` | Đọc mọi `dicom_instances` của examination để quyết định VERIFIED. |
| 6 | `instance.getAiAnalysis().getAiResults()` | Có thể phát sinh thêm SELECT LAZY cho analysis/result/review khi chưa được load. |
| 7 | `examinationRepository.save(examination)` | Chỉ khi mọi result thành công đã review: cập nhật `examinations.status = VERIFIED`. |
| 8 | Commit | Insert/update review và update status commit cùng nhau; runtime exception rollback toàn bộ phần DB của request. |

Pseudo-SQL để hình dung, không phải SQL Java hard-code:

```sql
SELECT * FROM ai_result WHERE id = :aiResultId;
SELECT * FROM diagnosis_reviews WHERE ai_result_id = :aiResultId;

INSERT INTO diagnosis_reviews (
    examination_id, doctor_id, ai_result_id,
    confirmed_kl_grade, decision, review_note, reviewed_at
) VALUES (...);

-- Lần review sau cho cùng aiResult là update row đã có.
UPDATE diagnosis_reviews
SET confirmed_kl_grade = :grade,
    decision = :decision,
    review_note = :note,
    doctor_id = :reviewerId,
    reviewed_at = :now
WHERE ai_result_id = :aiResultId;

UPDATE examinations SET status = 'VERIFIED' WHERE id = :examinationId;
```

`saveReview()` là upsert ở tầng application: SELECT trước bằng
`findByAiResultId`, sau đó save. Unique `ai_result_id` là hàng rào database cuối
cùng chống hai review row cho một AI result.

### 9.3 Một transaction generate PDF thực hiện những query gì?

| Bước | Repository/JPA call | Ý nghĩa database |
| --- | --- | --- |
| 1 | `examinationRepository.findByIdForUpdate(id)` | Lock pessimistic write, tương đương ý nghĩa `SELECT ... FOR UPDATE`; serialize generate cùng examination. |
| 2 | `userRepository.findByUsername(username)` | Đọc người generate để kiểm tra quyền và gán `operating_doctor_id`. |
| 3 | `reportRepository.findFirstByExaminationIdOrderByCreatedAtDesc(id)` | Khi status đã REPORT_GENERATED, đọc report cũ cho idempotency. |
| 4 | `dicomInstanceRepository.findByExaminationId(id)` | Đọc DICOM/AI/review để build PDF; lazy relation sau đó có thể tạo query phụ. |
| 5 | `reportRepository.save(report)` | INSERT một row `report` với metadata file. |
| 6 | `examinationRepository.save(examination)` | UPDATE status thành `REPORT_GENERATED`. |
| 7 | Commit | Sau commit event report knowledge mới tới listener async; rollback sẽ không sync RAG. |

Pseudo-SQL phần chính:

```sql
SELECT * FROM examinations WHERE id = :id FOR UPDATE;
SELECT * FROM report WHERE examination_id = :id ORDER BY created_at DESC LIMIT 1;
SELECT * FROM dicom_instances WHERE examination_id = :id;

INSERT INTO report (
    examination_id, operating_doctor_id, clinical_summary,
    file_path, file_name, content_type, file_size, created_at
) VALUES (...);

UPDATE examinations SET status = 'REPORT_GENERATED' WHERE id = :id;
```

File PDF không thuộc MySQL transaction. Vì vậy service dùng `try/catch` để xóa
file khi render lỗi, và `deleteFileIfTransactionRollsBack()` để xóa file đã
atomic-move khi transaction database rollback ở giai đoạn sau.

### 9.4 List/preview/download và lazy loading

| Operation | Query chính | Scope |
| --- | --- | --- |
| List report cho doctor | `findByExamination_Doctor_Id(userId, pageable)` | Chỉ report của examination gán cho doctor. |
| List report cho trưởng khoa | `findAll(pageable)` | Mọi report. |
| Preview/download | Latest report theo examination ID, sau đó load user/examination để `authorizeReportAccess`. | Doctor phải được gán, trưởng khoa được phép. |

`Report.examination`, `Examination.patient`, `Examination.doctor`, `AiResult`
và `DiagnosisReview` đều có association LAZY. Khi mapping list/report data hoặc
build PDF, Hibernate có thể sinh thêm SELECT. Đây là điểm cần profile nếu danh
sách lớn hoặc examination có nhiều DICOM/result.

### 9.5 Query chẩn đoán dữ liệu trực tiếp trong MySQL

```sql
-- Workflow state của ca khám.
SELECT e.id, e.encounter_code, e.status, e.final_diagnosis, e.doctor_id,
       e.study_date, e.study_time
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

-- Metadata report và vị trí file tương ứng.
SELECT r.id, r.examination_id, r.operating_doctor_id, r.file_path,
       r.file_name, r.file_size, r.content_type, r.created_at
FROM report r
WHERE r.examination_id = :examinationId
ORDER BY r.created_at DESC;
```

Các query này chỉ dùng để debug. Application vẫn phải đi qua service/repository
để giữ authorization, transaction và file cleanup nhất quán.

## 10. Nhánh Lỗi Và Test

| Tình huống | Kết quả hiện tại |
| --- | --- |
| Grade null, < 0, > 4 | `400` với message grade không hợp lệ. |
| Adjust thiếu note | `400`; confirm không cần note từ request. |
| Bác sĩ không được gán case | `403` tại review/export service. |
| Report đã sinh | Review bị `400`; generate trả lại report cũ nếu file còn. |
| Một DICOM thành công chưa review | Examination giữ `NEED_VERIFY`; generate bị `400`. |
| DICOM AI failed song song DICOM thành công | Failed instance bị bỏ qua khi xét VERIFIED/export. |
| File PDF metadata có path đi ra ngoài export dir | `403 Invalid report file path`. |
| Render/lưu PDF lỗi | File temp/output được dọn; transaction ném runtime error. |

Các test quan trọng:

- `DiagnosisReviewServiceTest`: confirm/adjust, ownership, trưởng khoa, mọi
  instance thành công phải review, failed instance được bỏ qua, cấm review sau
  report, validation và audit annotation.
- `PdfExportServiceTest`: quyền export, lock/idempotency, trạng thái VERIFIED,
  dữ liệu final grade, file lifecycle, event RAG sau report.
- `PdfReportTemplateTest`: template có watermark, các field quan trọng và render
  PDF không rỗng.
- `ReportListServiceTest`: doctor chỉ thấy report case của mình, trưởng khoa thấy
  tất cả, role không hợp lệ bị chặn.

## 11. Thứ Tự Debug Khuyến Nghị

1. Mở examination, kiểm tra `status`, doctor được gán, danh sách DICOM instance.
2. Với từng `AiResult`, kiểm tra có `DiagnosisReview` hay chưa và grade/note là gì.
3. Nếu không thành `VERIFIED`, theo `markVerifiedWhenAllLatestResultsAreReviewed()`
   xem instance nào `AI_FAILED`, chưa `GET_RESULTED`, thiếu analysis/result hoặc
   result chưa review.
4. Nếu không generate được, kiểm tra `VERIFIED`, quyền user, file report cũ và
   `buildFinalAiResults()`.
5. Nếu PDF sinh lỗi, kiểm tra export dir, font `tahoma.ttf`, template, Grad-CAM
   path/URL và log OpenHTMLtoPDF.
6. Nếu preview/download lỗi, kiểm tra `Report.filePath`, root traversal guard,
   file tồn tại thật và Bearer token của request frontend.
