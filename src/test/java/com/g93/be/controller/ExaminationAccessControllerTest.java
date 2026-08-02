package com.g93.be.controller;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.AccessControlService;
import com.g93.be.service.ExaminationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ExaminationAccessControllerTest.Config.class)
class ExaminationAccessControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ExaminationController examinationController;
    @org.springframework.beans.factory.annotation.Autowired
    private ExaminationService examinationService;
    @org.springframework.beans.factory.annotation.Autowired
    private AccessControlService accessControl;

    @BeforeEach
    void resetMocks() {
        reset(examinationService, accessControl);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "VIEW_PENDING_DIAGNOSIS"})
    void assignedDoctorCanReadOwnExamination() {
        when(accessControl.canAccessExamination(eq(11L), any(Authentication.class))).thenReturn(true);
        when(examinationService.getExaminationById(11L)).thenReturn(new ExaminationDto());

        assertDoesNotThrow(() -> examinationController.getExaminationById(11L));
        verify(examinationService).getExaminationById(11L);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "VIEW_PENDING_DIAGNOSIS"})
    void doctorCannotReadAnotherDoctorsExamination() {
        when(accessControl.canAccessExamination(eq(12L), any(Authentication.class))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> examinationController.getExaminationById(12L));
        verify(examinationService, never()).getExaminationById(12L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotReadAnotherUsersDashboardTotal() {
        when(accessControl.canAccessUser(eq(99L), any(Authentication.class))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> examinationController.getTotalExaminations(99L));
        verify(examinationService, never()).getTotalExaminations(99L);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "VIEW_PENDING_DIAGNOSIS"})
    void adminCannotReadClinicalExaminationEvenWithStalePermission() {
        assertThrows(AccessDeniedException.class, () -> examinationController.getExaminationById(11L));
        verify(examinationService, never()).getExaminationById(11L);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        ExaminationService examinationService() {
            return mock(ExaminationService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean(name = "accessControl")
        AccessControlService accessControl() {
            return mock(AccessControlService.class);
        }

        @Bean
        ExaminationController examinationController(
                ExaminationService examinationService,
                UserRepository userRepository) {
            return new ExaminationController(examinationService, userRepository);
        }
    }
}
