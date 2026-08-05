package com.g93.be.controller;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.PatientFilterRequest;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AiService;
import com.g93.be.service.DicomService;
import com.g93.be.service.ImageService;
import com.g93.be.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(ClinicalAdminControllerRbacTest.Config.class)
class ClinicalAdminControllerRbacTest {

    @Autowired
    private PatientController patientController;
    @Autowired
    private AiController aiController;
    @Autowired
    private DicomController dicomController;
    @Autowired
    private PatientService patientService;
    @Autowired
    private AiService aiService;
    @Autowired
    private DicomService dicomService;
    @Autowired
    private DicomInstanceRepository dicomInstanceRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetMocks() {
        reset(patientService, aiService, dicomService, dicomInstanceRepository, userRepository);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "READ_PATIENT_LIST"})
    void adminCanReadPatients() {
        patientController.getAllPatients(
                new PatientFilterRequest(), PageRequest.of(0, 10));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "TRIGGER_AI_ANALYSIS"})
    void adminCannotTriggerAiEvenWithStaleClinicalPermission() {
        assertThrows(AccessDeniedException.class,
                () -> aiController.predictBatch(new AiPredictionRequest(List.of(11L))));
        verifyNoInteractions(aiService);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "UPLOAD_DICOM_IMAGE"})
    void adminCannotReadDicomUploadSessionEvenWithStaleClinicalPermission() {
        assertThrows(AccessDeniedException.class,
                () -> dicomController.getUploadSession("session-1", () -> "admin"));
        verifyNoInteractions(dicomService, userRepository);
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "VIEW_ANALYTIC_HISTORY"})
    void adminCanReadClinicalDicomStatistics() {
        dicomController.getTotalStudies();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {

        @Bean
        PatientService patientService() {
            return mock(PatientService.class);
        }

        @Bean
        AiService aiService() {
            return mock(AiService.class);
        }

        @Bean
        ImageService imageService() {
            return mock(ImageService.class);
        }

        @Bean
        DicomService dicomService() {
            return mock(DicomService.class);
        }

        @Bean
        DicomInstanceRepository dicomInstanceRepository() {
            return mock(DicomInstanceRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PatientController patientController(PatientService patientService) {
            return new PatientController(patientService);
        }

        @Bean
        AiController aiController(AiService aiService, ImageService imageService) {
            return new AiController(aiService, imageService);
        }

        @Bean
        DicomController dicomController(
                DicomService dicomService,
                DicomInstanceRepository dicomInstanceRepository,
                UserRepository userRepository) {
            return new DicomController(dicomService, dicomInstanceRepository, userRepository);
        }
    }
}
