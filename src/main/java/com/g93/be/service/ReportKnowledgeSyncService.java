package com.g93.be.service;

import com.g93.be.chat.ReportKnowledgeSyncRequestedEvent;
import com.g93.be.dto.KnowledgeDocumentResponse;
import com.g93.be.entity.KnowledgeAccessScope;
import com.g93.be.entity.KnowledgeDocument;
import com.g93.be.entity.KnowledgeDocumentStatus;
import com.g93.be.entity.KnowledgeSourceType;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.exception.UnauthorizedAccessException;
import com.g93.be.repository.KnowledgeDocumentRepository;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class ReportKnowledgeSyncService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final KnowledgeDocumentRepository repository;
    private final UserRepository userRepository;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;

    @Transactional
    public KnowledgeDocumentResponse syncReport(Long reportId, String username) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if ("ADMIN".equals(requester.getRole().getCode())) {
            throw new UnauthorizedAccessException("Administrators cannot index clinical reports");
        }
        ReportKnowledge row = loadReport(reportId);
        if ("DOCTOR".equals(requester.getRole().getCode()) && !requester.getId().equals(row.ownerUserId())) {
            throw new UnauthorizedAccessException("You can only index reports from your own examinations");
        }
        return index(row);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void syncGeneratedReport(ReportKnowledgeSyncRequestedEvent event) {
        try {
            index(loadReport(event.reportId()));
        } catch (RuntimeException exception) {
            log.error("Could not synchronize generated report {} to Qdrant", event.reportId(), exception);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.chat.report-sync-delay-ms:300000}",
            initialDelayString = "${app.chat.report-sync-delay-ms:300000}")
    public void syncNewReports() {
        String sql = "SELECT r.id FROM report r JOIN examinations e ON e.id = r.examination_id "
                + "LEFT JOIN knowledge_documents k ON k.source_key = CONCAT('report:', r.id) "
                + "WHERE k.id IS NULL AND (e.doctor_id IS NOT NULL OR r.operating_doctor_id IS NOT NULL) "
                + "ORDER BY r.id LIMIT 100";
        for (Long reportId : jdbcTemplate.queryForList(sql, Map.of(), Long.class)) {
            try {
                index(loadReport(reportId));
            } catch (RuntimeException exception) {
                log.error("Could not synchronize report {} to Qdrant", reportId, exception);
            }
        }
    }

    private KnowledgeDocumentResponse index(ReportKnowledge row) {
        String sourceKey = "report:" + row.reportId();
        KnowledgeDocument knowledge = repository.findBySourceKey(sourceKey).orElseGet(KnowledgeDocument::new);
        knowledge.setSourceKey(sourceKey);
        knowledge.setTitle("Approved clinical report " + row.reportId());
        knowledge.setSourceType(KnowledgeSourceType.REPORT);
        knowledge.setAccessScope(KnowledgeAccessScope.OWNER);
        knowledge.setStatus(KnowledgeDocumentStatus.PROCESSING);
        knowledge.setUploadedBy(userRepository.findById(row.ownerUserId()).orElse(null));
        knowledge = repository.save(knowledge);

        String text = "Approved HealthSync clinical report. Report ID: " + row.reportId()
                + ". Examination ID: " + row.examinationId()
                + ". Study date: " + nullable(row.studyDate())
                + ". Final diagnosis: " + nullable(row.finalDiagnosis())
                + ". Confirmed KL grades: " + nullable(row.confirmedGrades())
                + ". Clinical summary: " + nullable(row.clinicalSummary()) + ".";
        Map<String, Object> metadata = Map.of(
                "sourceKey", sourceKey,
                "knowledgeDocumentId", knowledge.getId(),
                "title", knowledge.getTitle(),
                "sourceType", KnowledgeSourceType.REPORT.name(),
                "reference", "report:" + row.reportId(),
                "accessScope", KnowledgeAccessScope.OWNER.name(),
                "ownerUserId", row.ownerUserId(),
                "publicationStatus", "PUBLISHED");
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
        String sql = "SELECT r.id AS report_id, e.id AS examination_id, "
                + "COALESCE(r.operating_doctor_id, e.doctor_id) AS owner_user_id, e.study_date, "
                + "e.final_diagnosis, r.clinical_summary, "
                + "GROUP_CONCAT(dr.confirmed_kl_grade ORDER BY dr.id SEPARATOR ',') AS confirmed_grades "
                + "FROM report r JOIN examinations e ON e.id = r.examination_id "
                + "LEFT JOIN diagnosis_reviews dr ON dr.examination_id = e.id WHERE r.id = :reportId "
                + "GROUP BY r.id, e.id, r.operating_doctor_id, e.doctor_id, e.study_date, "
                + "e.final_diagnosis, r.clinical_summary";
        List<ReportKnowledge> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("reportId", reportId),
                (resultSet, rowNum) -> new ReportKnowledge(
                        resultSet.getLong("report_id"), resultSet.getLong("examination_id"),
                        resultSet.getLong("owner_user_id"), resultSet.getObject("study_date"),
                        resultSet.getString("final_diagnosis"), resultSet.getString("confirmed_grades"),
                        resultSet.getString("clinical_summary")));
        if (rows.isEmpty() || rows.getFirst().ownerUserId() == 0) {
            throw new ResourceNotFoundException("Approved report not found");
        }
        return rows.getFirst();
    }

    private KnowledgeDocumentResponse response(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(document.getId(), document.getTitle(), document.getSourceType().name(),
                document.getSourceUrl(), document.getOriginalName(), document.getAccessScope().name(),
                document.getStatus().name(), document.getChunkCount(), document.getErrorMessage(),
                document.getCreatedAt(), document.getIndexedAt());
    }

    private String nullable(Object value) {
        return value == null ? "not provided" : value.toString();
    }

    private record ReportKnowledge(
            Long reportId, Long examinationId, Long ownerUserId, Object studyDate,
            String finalDiagnosis, String confirmedGrades, String clinicalSummary) {
    }
}
