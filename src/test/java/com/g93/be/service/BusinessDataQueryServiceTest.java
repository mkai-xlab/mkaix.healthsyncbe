package com.g93.be.service;

import com.g93.be.chat.BusinessQueryIntent;
import com.g93.be.chat.BusinessQueryResult;
import com.g93.be.chat.ChatRoute;
import com.g93.be.chat.ChatRoutingDecision;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.exception.UnauthorizedAccessException;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDataQueryServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private UserRepository userRepository;

    @Test
    void doctorAggregateIsScopedByAssignedDoctorId() {
        BusinessDataQueryService service = new BusinessDataQueryService(jdbcTemplate, userRepository);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user(9L, "DOCTOR")));
        when(jdbcTemplate.queryForObject(
                org.mockito.ArgumentMatchers.anyString(),
                any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(4L);
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.BUSINESS_DATA, BusinessQueryIntent.TODAY_EXAMINATION_COUNT,
                null, "2026-08-06", "2026-08-06", null);

        BusinessQueryResult result = service.execute(decision, "doctor");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertTrue(sql.getValue().contains("e.doctor_id = :userId"));
        assertTrue(result.context().contains("examination_count=4"));
    }

    @Test
    void adminCannotReadClinicalReportDetail() {
        BusinessDataQueryService service = new BusinessDataQueryService(jdbcTemplate, userRepository);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "ADMIN")));
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.BUSINESS_DATA, BusinessQueryIntent.REPORT_SUMMARY, 12L, null, null, null);

        assertThrows(UnauthorizedAccessException.class, () -> service.execute(decision, "admin"));

        verify(jdbcTemplate, never()).queryForList(
                org.mockito.ArgumentMatchers.anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void doctorCanListAtMostTenMostRecentExaminationsForToday() {
        BusinessDataQueryService service = new BusinessDataQueryService(jdbcTemplate, userRepository);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user(9L, "DOCTOR")));
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "examination_id", 21L,
                        "encounter_code", "ENC-021",
                        "patient_code", "PAT-004",
                        "patient_name", "Nguyen Van A",
                        "status", "NEED_VERIFY")));
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.BUSINESS_DATA, BusinessQueryIntent.TODAY_EXAMINATION_LIST,
                null, null, null, null);

        BusinessQueryResult result = service.execute(decision, "doctor");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertTrue(sql.getValue().contains("COALESCE(e.visit_time, e.created_at)"));
        assertTrue(sql.getValue().contains("JOIN patients p ON p.patient_code = e.patient_id"));
        assertTrue(sql.getValue().contains("e.doctor_id = :userId"));
        assertTrue(sql.getValue().contains("ORDER BY COALESCE(e.visit_time, e.created_at) DESC"));
        assertTrue(sql.getValue().endsWith("LIMIT 10"));
        assertTrue(result.context().contains("examination_id=21"));
        assertTrue(result.context().contains("maximum_results=10"));
        assertEquals("database:examinations/recent", result.sources().getFirst().locator());
    }

    @Test
    void adminCannotListClinicalExaminations() {
        BusinessDataQueryService service = new BusinessDataQueryService(jdbcTemplate, userRepository);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "ADMIN")));
        ChatRoutingDecision decision = new ChatRoutingDecision(
                ChatRoute.BUSINESS_DATA, BusinessQueryIntent.TODAY_EXAMINATION_LIST,
                null, null, null, null);

        assertThrows(UnauthorizedAccessException.class, () -> service.execute(decision, "admin"));

        verify(jdbcTemplate, never()).queryForList(
                org.mockito.ArgumentMatchers.anyString(), any(MapSqlParameterSource.class));
    }

    private User user(Long id, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
