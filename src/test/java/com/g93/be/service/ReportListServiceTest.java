package com.g93.be.service;

import com.g93.be.dto.PageResponse;
import com.g93.be.dto.ReportListItemResponse;
import com.g93.be.entity.Examination;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Report;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.ReportRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportListServiceTest {

    @Mock
    private SpringTemplateEngine templateEngine;
    @Mock
    private ExaminationRepository examinationRepository;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PdfExportService pdfExportService;

    @Test
    void doctorReceivesOnlyReportsForAssignedExaminations() {
        User doctor = user(7L, "doctor", "DOCTOR");
        Report report = report(31L, doctor);
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(reportRepository.findByExamination_Doctor_Id(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));

        PageResponse<ReportListItemResponse> response = pdfExportService.getGeneratedReports(pageable, "doctor");

        assertEquals(1, response.totalElements());
        assertEquals(31L, response.content().get(0).reportId());
        assertEquals("PAT-001", response.content().get(0).patientCode());
        verify(reportRepository).findByExamination_Doctor_Id(7L, pageable);
    }

    @Test
    void departmentHeadReceivesAllReports() {
        User head = user(99L, "head", "HEAD_OF_DEPARTMENT");
        Report report = report(31L, user(7L, "doctor", "DOCTOR"));
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByUsername("head")).thenReturn(Optional.of(head));
        when(reportRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(report), pageable, 1));

        PageResponse<ReportListItemResponse> response = pdfExportService.getGeneratedReports(pageable, "head");

        assertEquals(1, response.content().size());
        assertEquals(7L, response.content().get(0).doctorId());
        verify(reportRepository).findAll(pageable);
    }

    @Test
    void unsupportedRoleCannotListReports() {
        User admin = user(1L, "admin", "ADMIN");
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThrows(AccessDeniedException.class,
                () -> pdfExportService.getGeneratedReports(pageable, "admin"));
    }

    private Report report(Long reportId, User doctor) {
        Patient patient = new Patient();
        patient.setPatientCode("PAT-001");
        patient.setFullName("Patient One");

        Examination examination = new Examination();
        examination.setId(42L);
        examination.setEncounterCode("ENC-42");
        examination.setVisitTime(LocalDateTime.of(2026, 8, 16, 10, 0));
        examination.setPatient(patient);
        examination.setDoctor((Doctor) doctor);

        Report report = new Report();
        report.setId(reportId);
        report.setExamination(examination);
        report.setFileName("report.pdf");
        report.setFileSize(100L);
        report.setContentType("application/pdf");
        report.setCreatedAt(LocalDateTime.of(2026, 8, 16, 11, 0));
        return report;
    }

    private User user(Long id, String username, String roleCode) {
        Doctor doctor = new Doctor();
        doctor.setId(id);
        doctor.setUsername(username);
        doctor.setFullName(username);
        Role role = new Role();
        role.setCode(roleCode);
        doctor.setRole(role);
        return doctor;
    }
}
