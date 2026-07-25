package com.g93.be.controller;

import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.service.DoctorService;
import com.g93.be.service.PdfExportService;
import com.g93.be.service.PermissionService;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ControllerRbacTest.SecurityTestConfig.class)
class ControllerRbacTest {

    @Autowired
    private DoctorController doctorController;
    @Autowired
    private FeatureController featureController;
    @Autowired
    private PermissionController permissionController;
    @Autowired
    private ReportController reportController;
    @Autowired
    private DoctorService doctorService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private PdfExportService pdfExportService;

    @BeforeEach
    void resetMocks() {
        reset(doctorService, permissionService, pdfExportService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateFeature() {
        CreateFeatureRequest request = new CreateFeatureRequest("Reports", "Report management");
        FeatureResponse response = new FeatureResponse(1L, "Reports", "Report management", List.of());
        when(permissionService.createFeature(request)).thenReturn(response);

        assertEquals(response, featureController.createFeature(request).getBody());
        verify(permissionService).createFeature(request);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotCreateFeature() {
        CreateFeatureRequest request = new CreateFeatureRequest("Reports", "Report management");

        assertThrows(AccessDeniedException.class, () -> featureController.createFeature(request));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteFeature() {
        assertEquals(204, featureController.deleteFeature(10L).getStatusCode().value());
        verify(permissionService).deleteFeature(10L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotDeleteFeature() {
        assertThrows(AccessDeniedException.class, () -> featureController.deleteFeature(10L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeletePermission() {
        assertEquals(204, permissionController.deletePermission(20L).getStatusCode().value());
        verify(permissionService).deletePermission(20L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotDeletePermission() {
        assertThrows(AccessDeniedException.class, () -> permissionController.deletePermission(20L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanActivateDoctor() {
        assertDoesNotThrow(() -> doctorController.activateDoctor(7L));
        verify(doctorService).activateDoctor(7L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotActivateDoctor() {
        assertThrows(AccessDeniedException.class, () -> doctorController.activateDoctor(7L));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCanSearchDoctors() {
        PageRequest pageable = PageRequest.of(0, 10);
        PageResponse<DoctorResponse> response = new PageResponse<>(List.of(), 0, 10, 0, 0, true);
        when(doctorService.searchDoctors(isNull(), isNull(), isNull(), any())).thenReturn(response);

        assertEquals(response, doctorController.getDoctors(null, null, null, pageable).getBody());
    }

    @Test
    @WithMockUser(authorities = "GENERATE_PDF_REPORT")
    void userWithPdfAuthorityCanGenerateReport() {
        when(pdfExportService.generateAndSavePdfReport(42L)).thenReturn("report.pdf");

        assertEquals("Report generated and saved at: report.pdf",
                reportController.generatePdfReport(42L).getBody());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roleWithoutPdfAuthorityCannotGenerateReport() {
        assertThrows(AccessDeniedException.class, () -> reportController.generatePdfReport(42L));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfig {

        @Bean
        DoctorService doctorService() {
            return mock(DoctorService.class);
        }

        @Bean
        PermissionService permissionService() {
            return mock(PermissionService.class);
        }

        @Bean
        PdfExportService pdfExportService() {
            return mock(PdfExportService.class);
        }

        @Bean
        DoctorController doctorController(DoctorService doctorService) {
            return new DoctorController(doctorService);
        }

        @Bean
        FeatureController featureController(PermissionService permissionService) {
            return new FeatureController(permissionService);
        }

        @Bean
        PermissionController permissionController(PermissionService permissionService) {
            return new PermissionController(permissionService);
        }

        @Bean
        ReportController reportController(PdfExportService pdfExportService) {
            return new ReportController(pdfExportService);
        }
    }
}
