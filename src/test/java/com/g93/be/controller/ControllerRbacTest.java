package com.g93.be.controller;

import com.g93.be.dto.CreateFeatureRequest;
import com.g93.be.dto.CreatePermissionRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.FeatureResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.dto.ReportResponse;
import com.g93.be.dto.UpdateFeatureRequest;
import com.g93.be.dto.UpdatePermissionRequest;
import com.g93.be.dto.UpdateRolePermissionsRequest;
import com.g93.be.service.DoctorService;
import com.g93.be.service.PdfExportService;
import com.g93.be.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.time.LocalDateTime;

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
    void adminCanUpdateFeature() {
        UpdateFeatureRequest request = new UpdateFeatureRequest("Clinical reports", "Updated");
        FeatureResponse response = new FeatureResponse(10L, "Clinical reports", "Updated", List.of());
        when(permissionService.updateFeature(10L, request)).thenReturn(response);

        assertEquals(response, featureController.updateFeature(10L, request).getBody());
        verify(permissionService).updateFeature(10L, request);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotUpdateFeature() {
        UpdateFeatureRequest request = new UpdateFeatureRequest("Clinical reports", "Updated");

        assertThrows(AccessDeniedException.class,
                () -> featureController.updateFeature(10L, request));
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
    void adminCanGetPermissionTree() {
        FeatureResponse feature = new FeatureResponse(10L, "Reports", "Reports", List.of());
        when(permissionService.getPermissionTree()).thenReturn(List.of(feature));

        assertEquals(List.of(feature), permissionController.getPermissionTree().getBody());
        verify(permissionService).getPermissionTree();
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotGetPermissionTree() {
        assertThrows(AccessDeniedException.class, permissionController::getPermissionTree);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanGetRolePermissions() {
        when(permissionService.getRolePermissions("DOCTOR")).thenReturn(List.of(20L, 21L));

        assertEquals(List.of(20L, 21L),
                permissionController.getRolePermissions("DOCTOR").getBody());
        verify(permissionService).getRolePermissions("DOCTOR");
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotGetRolePermissions() {
        assertThrows(AccessDeniedException.class,
                () -> permissionController.getRolePermissions("DOCTOR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateRolePermissions() {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest(List.of(20L, 21L));

        assertEquals(200,
                permissionController.updateRolePermissions("DOCTOR", request).getStatusCode().value());
        verify(permissionService).updateRolePermissions("DOCTOR", request);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotUpdateRolePermissions() {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest(List.of(20L));

        assertThrows(AccessDeniedException.class,
                () -> permissionController.updateRolePermissions("DOCTOR", request));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreatePermission() {
        CreatePermissionRequest request = new CreatePermissionRequest(
                "VIEW_REPORT", "View report", 1, "View", 10L, null);
        PermissionResponse response = new PermissionResponse(
                20L, "VIEW_REPORT", "View report", 1, "View", null);
        when(permissionService.createPermission(request)).thenReturn(response);

        var controllerResponse = permissionController.createPermission(request);

        assertEquals(201, controllerResponse.getStatusCode().value());
        assertEquals(response, controllerResponse.getBody());
        verify(permissionService).createPermission(request);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotCreatePermission() {
        CreatePermissionRequest request = new CreatePermissionRequest(
                "VIEW_REPORT", "View report", 1, "View", 10L, null);

        assertThrows(AccessDeniedException.class,
                () -> permissionController.createPermission(request));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdatePermission() {
        UpdatePermissionRequest request = new UpdatePermissionRequest(
                "VIEW_REPORT", "View updated", 2, "View", null);
        PermissionResponse response = new PermissionResponse(
                20L, "VIEW_REPORT", "View updated", 2, "View", null);
        when(permissionService.updatePermission(20L, request)).thenReturn(response);

        assertEquals(response, permissionController.updatePermission(20L, request).getBody());
        verify(permissionService).updatePermission(20L, request);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotUpdatePermission() {
        UpdatePermissionRequest request = new UpdatePermissionRequest(
                "VIEW_REPORT", "View updated", 2, "View", null);

        assertThrows(AccessDeniedException.class,
                () -> permissionController.updatePermission(20L, request));
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
    @WithMockUser(authorities = {"ROLE_DOCTOR", "GENERATE_PDF_REPORT"})
    void userWithPdfAuthorityCanGenerateReport() {
        ReportResponse response = new ReportResponse(
                9L, 42L, "report.pdf", 100L, "application/pdf", LocalDateTime.now(),
                "/api/v1/reports/42/preview", "/api/v1/reports/42/download");
        when(pdfExportService.generateAndSavePdfReport(42L, "doctor")).thenReturn(response);

        assertEquals(response,
                reportController.generatePdfReport(42L, () -> "doctor").getBody());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "GENERATE_PDF_REPORT"})
    void adminCannotGenerateReportEvenWithStaleClinicalPermission() {
        assertThrows(AccessDeniedException.class,
                () -> reportController.generatePdfReport(42L, () -> "admin"));
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "GENERATE_PDF_REPORT"})
    void userWithGenerateAuthorityCanPreviewReport() {
        when(pdfExportService.getReportFileByExaminationId(9L, "doctor")).thenReturn(reportFile());

        assertEquals(200, reportController.previewReport(9L, () -> "doctor")
                .getStatusCode().value());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "EXPORT_DOWNLOAD_PDF"})
    void userWithDownloadAuthorityCanDownloadReport() {
        when(pdfExportService.getReportFileByExaminationId(9L, "doctor")).thenReturn(reportFile());

        assertEquals(200, reportController.downloadReport(9L, () -> "doctor")
                .getStatusCode().value());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_DOCTOR", "GENERATE_PDF_REPORT"})
    void generateAuthorityAloneCannotDownloadReport() {
        assertThrows(AccessDeniedException.class,
                () -> reportController.downloadReport(9L, () -> "doctor"));
    }

    @Test
    @WithMockUser(roles = "HEAD_OF_DEPARTMENT")
    void departmentHeadCanDownloadReportWithoutExplicitAuthority() {
        when(pdfExportService.getReportFileByExaminationId(9L, "head")).thenReturn(reportFile());

        assertEquals(200, reportController.downloadReport(9L, () -> "head")
                .getStatusCode().value());
    }

    private PdfExportService.ReportFile reportFile() {
        return new PdfExportService.ReportFile(
                new ByteArrayResource(new byte[]{1}),
                "report.pdf",
                "application/pdf",
                1L);
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
