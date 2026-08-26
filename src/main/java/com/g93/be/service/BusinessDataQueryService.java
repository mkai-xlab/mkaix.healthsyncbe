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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.enabled", havingValue = "true")
public class BusinessDataQueryService {

    /**
     * The confirmed grade from a doctor's diagnosis review overrides the raw AI
     * prediction once a review exists; only the latest review counts. Both grade
     * queries below share this precedence so "how many KL4" and the full
     * distribution never silently disagree with a doctor's own correction.
     */
    private static final String EFFECTIVE_GRADE = "COALESCE("
            + "(SELECT dr.confirmed_kl_grade FROM diagnosis_reviews dr "
            + "WHERE dr.examination_id = e.id ORDER BY dr.reviewed_at DESC, dr.id DESC LIMIT 1), "
            + "e.max_predicted_grade)";

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
            case TODAY_EXAMINATION_LIST -> recentExaminations(parameters, range, null, scopedDoctor);
            case EXAMINATION_LIST -> recentExaminations(parameters, range, decision.klGrade(), scopedDoctor);
            case REPORT_COUNT -> countReports(parameters, range, scopedDoctor);
            case EXAMINATION_FINAL_RESULT -> examinationResult(parameters, decision.entityId(), scopedDoctor);
            case REPORT_SUMMARY -> reportSummary(parameters, decision.entityId(), scopedDoctor);
            case GRADE_DISTRIBUTION -> gradeDistribution(parameters, range, scopedDoctor);
            case GRADE_COUNT -> gradeCount(parameters, range, requireGrade(decision.klGrade()), scopedDoctor);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported business data question");
        };
    }

    /**
     * Lists the examinations themselves rather than an aggregate, so a follow-up
     * such as "which cases are those" can be answered from the same rows the
     * previous count was built from. A non-null grade narrows the list to one KL
     * grade using the same confirmed-review-first precedence as the counts.
     *
     * <p>Patients are identified by {@code patient_code} only. The full name is
     * deliberately not selected: this context string is sent to the external
     * chat model and is then replayed as conversation history on every later
     * turn, so a name would leave the system repeatedly. This matches the report
     * indexing path, which also keeps direct patient identifiers out of the
     * model. {@code priority} is left out for the same reason of only supplying
     * what the answer actually needs.
     */
    private BusinessQueryResult recentExaminations(
            MapSqlParameterSource parameters, DateRange range, Integer grade, boolean scopedDoctor) {
        String examinationTime = "COALESCE(e.visit_time, e.created_at)";
        String sql = "SELECT e.id AS examination_id, e.encounter_code, p.patient_code, "
                + examinationTime + " AS visit_time, "
                + "e.status FROM examinations e "
                + "JOIN patients p ON p.patient_code = e.patient_id "
                + "WHERE " + examinationTime + " >= :from AND " + examinationTime + " < :to"
                + (grade == null ? "" : " AND " + EFFECTIVE_GRADE + " = :grade")
                + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
                + " ORDER BY " + examinationTime + " DESC, e.id DESC LIMIT 10";
        if (grade != null) {
            parameters.addValue("grade", grade);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        return result("recent_examinations=" + rows + ", from=" + range.from() + ", to=" + range.to()
                        + (grade == null ? "" : ", kl_grade=" + grade
                                + ", grade_source=confirmed_review_else_ai_prediction")
                        + ", maximum_results=10",
                "MySQL recent examinations", "database:examinations/recent");
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
        String sql = "SELECT " + EFFECTIVE_GRADE + " AS grade, COUNT(*) AS total FROM examinations e "
                + "WHERE e.created_at >= :from AND e.created_at < :to"
                + (scopedDoctor ? " AND e.doctor_id = :userId" : "")
                + " GROUP BY grade ORDER BY grade";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        return result("grade_distribution=" + rows + ", from=" + range.from() + ", to=" + range.to()
                        + ", grade_source=confirmed_review_else_ai_prediction",
                "MySQL examination grade aggregate", "database:examinations/grade-distribution");
    }

    private BusinessQueryResult gradeCount(
            MapSqlParameterSource parameters, DateRange range, int grade, boolean scopedDoctor) {
        String sql = "SELECT COUNT(*) FROM examinations e "
                + "WHERE e.created_at >= :from AND e.created_at < :to "
                + "AND " + EFFECTIVE_GRADE + " = :grade"
                + (scopedDoctor ? " AND e.doctor_id = :userId" : "");
        Long count = jdbcTemplate.queryForObject(sql, parameters.addValue("grade", grade), Long.class);
        return result("kl_grade=" + grade + ", examination_count=" + value(count)
                        + ", from=" + range.from() + ", to=" + range.to()
                        + ", grade_source=confirmed_review_else_ai_prediction",
                "MySQL KL grade aggregate", "database:examinations/kl-grade/" + grade);
    }

    private BusinessQueryResult result(String context, String title, String reference) {
        return new BusinessQueryResult(context,
                List.of(new ChatSourceResponse(reference, title, "BUSINESS_DATA", reference, null)));
    }

    private boolean isClinical(BusinessQueryIntent intent) {
        return intent == BusinessQueryIntent.TODAY_EXAMINATION_LIST
                || intent == BusinessQueryIntent.EXAMINATION_LIST
                || intent == BusinessQueryIntent.EXAMINATION_FINAL_RESULT
                || intent == BusinessQueryIntent.REPORT_SUMMARY
                || intent == BusinessQueryIntent.GRADE_DISTRIBUTION
                || intent == BusinessQueryIntent.GRADE_COUNT;
    }

    private int requireGrade(Integer grade) {
        if (grade == null || grade < 0 || grade > 4) {
            throw new IllegalArgumentException("A valid KL grade (0-4) is required");
        }
        return grade;
    }

    private DateRange dateRange(ChatRoutingDecision decision, BusinessQueryIntent intent) {
        boolean noExplicitDate = (decision.dateFrom() == null || decision.dateFrom().isBlank())
                && (decision.dateTo() == null || decision.dateTo().isBlank());
        if (noExplicitDate && intent != BusinessQueryIntent.TODAY_EXAMINATION_COUNT
                && intent != BusinessQueryIntent.TODAY_EXAMINATION_LIST) {
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
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date: " + raw);
        }
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
