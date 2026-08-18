package com.g93.be.controller;

import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.DicomVerifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DicomVerifyControllerAccessTest.Config.class)
class DicomVerifyControllerAccessTest {

    @Autowired
    private DicomVerifyController controller;
    @Autowired
    private DicomVerifyService dicomVerifyService;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetMocks() {
        reset(dicomVerifyService, userRepository);
    }

    @Test
    @WithMockUser(authorities = {
            "ROLE_DOCTOR", "UPLOAD_DICOM_IMAGE", "TRIGGER_AI_ANALYSIS"
    })
    void doctorWithUploadAndAiTriggerPermissionsCanReachOwnershipCheck() {
        DicomVerifyRequest request = new DicomVerifyRequest("session-1", List.of());
        User doctor = user(7L, "doctor", "DOCTOR");
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(dicomVerifyService.verifySession(request, 7L, false))
                .thenReturn(new com.g93.be.dto.VerifySessionResultDto(List.of(), List.of()));

        assertEquals(200, controller.verifyUploadSession(request, () -> "doctor")
                .getStatusCode().value());
        verify(dicomVerifyService).verifySession(request, 7L, false);
    }

    @Test
    @WithMockUser(authorities = { "ROLE_DOCTOR", "UPLOAD_DICOM_IMAGE" })
    void doctorWithoutAiTriggerPermissionCannotVerifySession() {
        DicomVerifyRequest request = new DicomVerifyRequest("session-1", List.of());

        assertThrows(AccessDeniedException.class,
                () -> controller.verifyUploadSession(request, () -> "doctor"));
    }

    @Test
    @WithMockUser(authorities = { "ROLE_ADMIN", "UPLOAD_DICOM_IMAGE", "TRIGGER_AI_ANALYSIS" })
    void adminCannotVerifyEvenWithClinicalPermissionsInAnExistingToken() {
        DicomVerifyRequest request = new DicomVerifyRequest("session-1", List.of());

        assertThrows(AccessDeniedException.class,
                () -> controller.verifyUploadSession(request, () -> "admin"));
        org.mockito.Mockito.verifyNoInteractions(dicomVerifyService, userRepository);
    }

    @Test
    @WithMockUser(roles = "HEAD_OF_DEPARTMENT")
    void departmentHeadCanReachVerifyServiceAsClinicalSupervisor() {
        DicomVerifyRequest request = new DicomVerifyRequest("session-1", List.of());
        User departmentHead = user(2L, "head", "HEAD_OF_DEPARTMENT");
        when(userRepository.findByUsername("head")).thenReturn(Optional.of(departmentHead));
        when(dicomVerifyService.verifySession(request, 2L, true))
                .thenReturn(new com.g93.be.dto.VerifySessionResultDto(List.of(), List.of()));

        assertEquals(200, controller.verifyUploadSession(request, () -> "head")
                .getStatusCode().value());
        verify(dicomVerifyService).verifySession(request, 2L, true);
    }

    private User user(Long id, String username, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {

        @Bean
        DicomVerifyService dicomVerifyService() {
            return mock(DicomVerifyService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        DicomVerifyController dicomVerifyController(
                DicomVerifyService dicomVerifyService,
                UserRepository userRepository) {
            return new DicomVerifyController(
                    dicomVerifyService,
                    userRepository);
        }
    }
}
