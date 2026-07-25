package com.g93.be.controller;

import com.g93.be.dto.AdjustKlGradeRequest;
import com.g93.be.dto.DiagnosisReviewResponse;
import com.g93.be.service.DiagnosisReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DiagnosisReviewControllerRbacTest.SecurityTestConfig.class)
class DiagnosisReviewControllerRbacTest {

    @Autowired
    private DiagnosisReviewController controller;
    @Autowired
    private DiagnosisReviewService diagnosisReviewService;

    private final Principal principal = () -> "doctor.b";
    private final AdjustKlGradeRequest request = new AdjustKlGradeRequest(3, "Clinical review");

    @BeforeEach
    void resetMock() {
        reset(diagnosisReviewService);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "OVERRIDE_AI_GRADE"})
    void doctorWithOverrideAuthorityCanAdjustKlGrade() {
        DiagnosisReviewResponse response = new DiagnosisReviewResponse(
                1L, 19L, 11L, 2, 3, "DOCTOR_ADJUSTED", "Clinical review", 7L, LocalDateTime.now());
        when(diagnosisReviewService.adjustKlGrade(19L, request, "doctor.b")).thenReturn(response);

        assertEquals(response, controller.adjustKlGrade(19L, request, principal).getBody());
        verify(diagnosisReviewService).adjustKlGrade(19L, request, "doctor.b");
    }

    @Test
    @WithMockUser(authorities = "ROLE_DOCTOR")
    void doctorWithoutOverrideAuthorityCannotAdjustKlGrade() {
        assertThrows(AccessDeniedException.class,
                () -> controller.adjustKlGrade(19L, request, principal));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "OVERRIDE_AI_GRADE"})
    void nonDoctorCannotAdjustKlGrade() {
        assertThrows(AccessDeniedException.class,
                () -> controller.adjustKlGrade(19L, request, principal));
    }

    @Test
    @WithMockUser(authorities = "ROLE_HEAD_OF_DEPARTMENT")
    void departmentHeadCanAdjustKlGradeWithoutExplicitAuthority() {
        DiagnosisReviewResponse response = new DiagnosisReviewResponse(
                1L, 19L, 11L, 2, 3, "DOCTOR_ADJUSTED", "Clinical review", 8L, LocalDateTime.now());
        when(diagnosisReviewService.adjustKlGrade(19L, request, "doctor.b")).thenReturn(response);

        assertEquals(response, controller.adjustKlGrade(19L, request, principal).getBody());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "CONFIRM_CONCLUSION"})
    void doctorWithConfirmAuthorityCanConfirmAiGrade() {
        DiagnosisReviewResponse response = new DiagnosisReviewResponse(
                1L, 19L, 11L, 2, 2, "AI_CONFIRMED", "AI result confirmed", 7L, LocalDateTime.now());
        when(diagnosisReviewService.confirmAiGrade(19L, "doctor.b")).thenReturn(response);

        assertEquals(response, controller.confirmAiGrade(19L, principal).getBody());
        verify(diagnosisReviewService).confirmAiGrade(19L, "doctor.b");
    }

    @Test
    @WithMockUser(authorities = "ROLE_HEAD_OF_DEPARTMENT")
    void departmentHeadCanConfirmAiGrade() {
        DiagnosisReviewResponse response = new DiagnosisReviewResponse(
                1L, 19L, 11L, 2, 2, "AI_CONFIRMED", "AI result confirmed", 8L, LocalDateTime.now());
        when(diagnosisReviewService.confirmAiGrade(19L, "doctor.b")).thenReturn(response);

        assertEquals(response, controller.confirmAiGrade(19L, principal).getBody());
        verify(diagnosisReviewService).confirmAiGrade(19L, "doctor.b");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfig {

        @Bean
        DiagnosisReviewService diagnosisReviewService() {
            return mock(DiagnosisReviewService.class);
        }

        @Bean
        DiagnosisReviewController diagnosisReviewController(DiagnosisReviewService service) {
            return new DiagnosisReviewController(service);
        }
    }
}
