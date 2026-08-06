package com.g93.be.service;

import com.g93.be.chat.BusinessQueryIntent;
import com.g93.be.chat.BusinessQueryResult;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.dto.ChatSourceResponse;
import com.g93.be.entity.User;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.exception.UnauthorizedAccessException;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class BusinessDataQueryService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public BusinessQueryResult execute(ChatRoutingDecision decision, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String role = user.getRole().getCode();
        BusinessQueryIntent intent = decision.businessIntent() == null
                ? BusinessQueryIntent.UNKNOWN : decision.businessIntent();

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

        return switch (intent) {
            case TODAY_EXAMINATION_COUNT, EXAMINATION_COUNT -> countExaminations(parameters, range, scopedDoctor);
            case REPORT_COUNT -> countReports(parameters, range, scopedDoctor);
            case EXAMINATION_FINAL_RESULT -> examinationResult(parameters, decision.entityId(), scopedDoctor);
            case REPORT_SUMMARY -> reportSummary(parameters, decision.entityId(), scopedDoctor);
            case GRADE_DISTRIBUTION -> gradeDistribution(parameters, range, scopedDoctor);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported business data question");
        };
    }

    private BusinessQueryResult countExaminations(
            MapSqlParameterSource parameters, DateRange range, boolean scopedDoctor) {
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
        return result(rows.getFirst().toString(), "MySQL report " + reportId, "database:report/" + reportId);
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
        return new BusinessQueryResult(context,
                List.of(new ChatSourceResponse(reference, title, "BUSINESS_DATA", reference, null)));
    }

    private boolean isClinical(BusinessQueryIntent intent) {
        return intent == BusinessQueryIntent.EXAMINATION_FINAL_RESULT
                || intent == BusinessQueryIntent.REPORT_SUMMARY
                || intent == BusinessQueryIntent.GRADE_DISTRIBUTION;
    }

    private DateRange dateRange(ChatRoutingDecision decision, BusinessQueryIntent intent) {
        boolean noExplicitDate = (decision.dateFrom() == null || decision.dateFrom().isBlank())
                && (decision.dateTo() == null || decision.dateTo().isBlank());
        if (noExplicitDate && intent != BusinessQueryIntent.TODAY_EXAMINATION_COUNT) {
            return new DateRange(LocalDate.of(1970, 1, 1).atStartOfDay(),
                    LocalDate.now().plusDays(1).atStartOfDay());
        }
        LocalDate from = parseDate(decision.dateFrom(), LocalDate.now());
        LocalDate to = parseDate(decision.dateTo(), from);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo must not be before dateFrom");
        }
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
        return count == null ? 0 : count;
    }

    private record DateRange(LocalDateTime from, LocalDateTime to) {
    }
}
