package com.g93.be.service.impl;

import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PatientServiceImplTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ExaminationRepository examinationRepository;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private PatientServiceImpl patientService;

    // ==========================================
    // 1. createPatient
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng tạo mới.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức CreatePatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testCreatePatient_Normal() {
        CreatePatientRequest req = new CreatePatientRequest();
        req.setFullName("Nguyen Van A");
        req.setDateOfBirth(LocalDate.of(1990, 1, 1));
        req.setGender(Gender.MALE);
        req.setPhone("0901234567");
        req.setEmail("test@gmail.com");

        Patient savedPatient = new Patient();
        savedPatient.setId(1L);
        savedPatient.setFullName("Nguyen Van A");
        savedPatient.setPatientCode("PAT_12345678");

        PatientResponse response = new PatientResponse();
        
        when(patientRepository.save(any(Patient.class))).thenReturn(savedPatient);
        when(patientMapper.toResponse(savedPatient)).thenReturn(response);

        PatientResponse result = patientService.createPatient(req);

        assertNotNull(result);
        verify(patientRepository).save(any(Patient.class));
    }

    /**
     * Mục đích: Kiểm tra chức năng tạo mới.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức CreatePatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testCreatePatient_Abnormal_MissingFullName() {
        CreatePatientRequest req = new CreatePatientRequest();
        req.setFullName("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(req));
        assertEquals("Full name is required", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra chức năng tạo mới.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức CreatePatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testCreatePatient_Abnormal_NullFullName() {
        CreatePatientRequest req = new CreatePatientRequest();
        req.setFullName(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(req));
        assertEquals("Full name is required", ex.getMessage());
    }

    // ==========================================
    // 2. editPatient
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng cập nhật.
     * Đầu vào: Kịch bản: Luồng chuẩn (dữ liệu hợp lệ).
     * Hành động: Gọi phương thức EditPatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testEditPatient_Normal() {
        EditPatientRequest req = new EditPatientRequest();
        req.setFullName("Updated Name");
        req.setEmail("update@gmail.com");

        Patient existing = new Patient();
        existing.setId(1L);
        existing.setFullName("Old Name");

        PatientResponse response = new PatientResponse();

        when(patientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenReturn(existing);
        when(patientMapper.toResponse(existing)).thenReturn(response);

        PatientResponse result = patientService.editPatient(1L, req);

        assertNotNull(result);
        assertEquals("Updated Name", existing.getFullName());
        assertEquals("update@gmail.com", existing.getEmail());
        verify(patientRepository).save(existing);
    }

    /**
     * Mục đích: Kiểm tra chức năng cập nhật.
     * Đầu vào: Kịch bản: Luồng lỗi (Abnormal/Invalid).
     * Hành động: Gọi phương thức EditPatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID01 (Dự kiến) */
    @Test
    void testEditPatient_Abnormal_NotFound() {
        EditPatientRequest req = new EditPatientRequest();
        req.setFullName("Updated Name");

        when(patientRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> patientService.editPatient(999L, req));
        assertEquals("Patient with id 999 not found", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra chức năng cập nhật.
     * Đầu vào: Kịch bản: PartialUpdate.
     * Hành động: Gọi phương thức EditPatient().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: UTCID03 (Dự kiến) */
    @Test
    void testEditPatient_PartialUpdate() {
        EditPatientRequest req = new EditPatientRequest();
        req.setFullName(null);
        req.setEmail(" "); // Blank email should not update if using !isBlank()

        Patient existing = new Patient();
        existing.setId(1L);
        existing.setFullName("Old Name");
        existing.setEmail("old@gmail.com");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenReturn(existing);
        when(patientMapper.toResponse(existing)).thenReturn(new PatientResponse());

        patientService.editPatient(1L, req);

        assertEquals("Old Name", existing.getFullName());
        assertEquals("old@gmail.com", existing.getEmail());
    }

    // ==========================================
    // 3. getAllPatients
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Admin Success.
     * Hành động: Gọi phương thức GetAllPatients().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetAllPatients_Admin_Success() {
        PatientFilterRequest filter = new PatientFilterRequest();
        filter.setIsPersonal(false);
        Pageable pageable = PageRequest.of(0, 10);
        
        User adminUser = new User();
        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminUser.setRole(adminRole);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        
        Page<Patient> page = new PageImpl<>(List.of(new Patient()));
        when(patientRepository.findAllByCustomFilters(null, false, null, false, null, null, pageable)).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getAllPatients(filter, pageable, "admin");
        
        assertNotNull(res);
        assertEquals(1, res.content().size());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: DoctorPersonal Success.
     * Hành động: Gọi phương thức GetAllPatients().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetAllPatients_DoctorPersonal_Success() {
        PatientFilterRequest filter = new PatientFilterRequest();
        filter.setIsPersonal(true);
        Pageable pageable = PageRequest.of(0, 10);
        
        User docUser = new User();
        docUser.setId(5L);
        Role docRole = new Role();
        docRole.setCode("DOCTOR");
        docUser.setRole(docRole);

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(docUser));
        
        Page<Patient> page = new PageImpl<>(List.of(new Patient()));
        // Note: doctorId = 5L should be passed
        when(patientRepository.findAllByCustomFilters(null, false, null, false, null, 5L, pageable)).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getAllPatients(filter, pageable, "doctor");
        
        assertNotNull(res);
        verify(patientRepository).findAllByCustomFilters(null, false, null, false, null, 5L, pageable);
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: Ném ngoại lệ (Exception).
     * Hành động: Gọi phương thức GetAllPatients().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetAllPatients_DoctorNotPersonal_ThrowsException() {
        PatientFilterRequest filter = new PatientFilterRequest();
        filter.setIsPersonal(false); // DOCTOR trying to view all
        Pageable pageable = PageRequest.of(0, 10);
        
        User docUser = new User();
        docUser.setId(5L);
        Role docRole = new Role();
        docRole.setCode("DOCTOR");
        docUser.setRole(docRole);

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(docUser));
        
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> patientService.getAllPatients(filter, pageable, "doctor"));
        assertEquals("Bạn không có quyền xem toàn bộ danh sách bệnh nhân của hệ thống.", ex.getMessage());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: WithStatusesAndSeverities.
     * Hành động: Gọi phương thức GetAllPatients().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetAllPatients_WithStatusesAndSeverities() {
        PatientFilterRequest filter = new PatientFilterRequest();
        filter.setKeyword("abc");
        filter.setStatuses(List.of("COMPLETED", "INVALID_STATUS")); // Contains an invalid enum string
        filter.setSeverities(List.of(1, 2));
        Pageable pageable = PageRequest.of(0, 10);
        
        Page<Patient> page = new PageImpl<>(List.of());
        when(patientRepository.findAllByCustomFilters(
                eq("abc"), 
                anyBoolean(), 
                any(), 
                anyBoolean(), 
                any(), 
                isNull(), 
                any())
        ).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getAllPatients(filter, pageable, null);
        assertNotNull(res);
        verify(patientRepository).findAllByCustomFilters(anyString(), anyBoolean(), any(), anyBoolean(), any(), isNull(), any());
    }

    // ==========================================
    // 4. getPatientsByUploadDate
    // ==========================================
    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: WithNullUsername.
     * Hành động: Gọi phương thức GetPatientsByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetPatientsByUploadDate_WithNullUsername() {
        LocalDate date = LocalDate.of(2023, 5, 15);
        Pageable pageable = PageRequest.of(0, 10);
        
        Page<Patient> page = new PageImpl<>(List.of(new Patient()));
        when(patientRepository.findPatientsByUploadDateAndDoctor(
                eq(date.atStartOfDay()), 
                eq(date.plusDays(1).atStartOfDay()), 
                isNull(), 
                eq(pageable))
        ).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getPatientsByUploadDate(date, pageable, null);
        
        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(patientRepository).findPatientsByUploadDateAndDoctor(any(), any(), isNull(), any());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: WithAdminUsername.
     * Hành động: Gọi phương thức GetPatientsByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetPatientsByUploadDate_WithAdminUsername() {
        LocalDate date = LocalDate.of(2023, 5, 15);
        Pageable pageable = PageRequest.of(0, 10);
        
        User adminUser = new User();
        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminUser.setRole(adminRole);
        
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        
        Page<Patient> page = new PageImpl<>(List.of());
        when(patientRepository.findPatientsByUploadDateAndDoctor(
                eq(date.atStartOfDay()), 
                eq(date.plusDays(1).atStartOfDay()), 
                isNull(), 
                eq(pageable))
        ).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getPatientsByUploadDate(date, pageable, "admin");
        
        assertNotNull(res);
        assertEquals(0, res.content().size());
        verify(patientRepository).findPatientsByUploadDateAndDoctor(any(), any(), isNull(), any());
    }

    /**
     * Mục đích: Kiểm tra chức năng lấy.
     * Đầu vào: Kịch bản: WithDoctorUsername.
     * Hành động: Gọi phương thức GetPatientsByUploadDate().
     * Kỳ vọng: Hoạt động đúng như thiết kế, trả về kết quả tương ứng hoặc báo lỗi.
     
     * Kịch bản Test Design: N/A (Extra Test Case) */
    @Test
    void testGetPatientsByUploadDate_WithDoctorUsername() {
        LocalDate date = LocalDate.of(2023, 5, 15);
        Pageable pageable = PageRequest.of(0, 10);
        
        User docUser = new User();
        docUser.setId(99L);
        Role docRole = new Role();
        docRole.setCode("DOCTOR");
        docUser.setRole(docRole);
        
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(docUser));
        
        Page<Patient> page = new PageImpl<>(List.of(new Patient()));
        when(patientRepository.findPatientsByUploadDateAndDoctor(
                eq(date.atStartOfDay()), 
                eq(date.plusDays(1).atStartOfDay()), 
                eq(99L), 
                eq(pageable))
        ).thenReturn(page);
        
        PageResponse<PatientResponse> res = patientService.getPatientsByUploadDate(date, pageable, "doctor");
        
        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(patientRepository).findPatientsByUploadDateAndDoctor(any(), any(), eq(99L), any());
    }

    @Test
    void getPatientDetailsWithImagesMapsExaminationsImagesAndWritesAuditLog() {
        Patient patient = new Patient();
        patient.setId(5L);
        patient.setPatientCode("PAT_001");
        User admin = new User();
        admin.setUsername("admin");
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);
        Examination examination = new Examination();
        examination.setId(11L);
        examination.setEncounterCode("ENC-11");
        examination.setStatus(ExaminationStatus.VERIFIED);
        DicomInstance instance = new DicomInstance();
        instance.setId(21L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/PAT_001");
        request.setContextPath("/api/v1");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(patientRepository.findByPatientCode("PAT_001")).thenReturn(Optional.of(patient));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());
        when(examinationRepository.findByPatientIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(examination));
        when(dicomInstanceRepository.findByExaminationId(11L)).thenReturn(List.of(instance));

        try {
            PatientDetailsResponse result = patientService.getPatientDetailsWithImages("PAT_001", "admin");

            assertEquals(1, result.getRecentExaminations().size());
            assertEquals(1, result.getRecentExaminations().getFirst().getImages().size());
            assertEquals(true, result.getRecentExaminations().getFirst().getThumbnailUrl().endsWith("/dicom/instances/21/image"));
            verify(auditLogRepository).save(any(AuditLog.class));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void getPatientDetailsWithImagesRejectsDoctorWithoutPatientAccess() {
        Patient patient = new Patient();
        patient.setId(5L);
        patient.setPatientCode("PAT_001");
        User doctor = new User();
        doctor.setId(9L);
        Role role = new Role();
        role.setCode("DOCTOR");
        doctor.setRole(role);
        when(patientRepository.findByPatientCode("PAT_001")).thenReturn(Optional.of(patient));
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(doctor));
        when(examinationRepository.existsByPatientIdAndDoctorId(5L, 9L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> patientService.getPatientDetailsWithImages("PAT_001", "doctor"));

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void getPatientDetailsWithImagesRejectsUnknownPatient() {
        when(patientRepository.findByPatientCode("MISSING")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> patientService.getPatientDetailsWithImages("MISSING", null));
    }

    @Test
    void getPatientDetailsWithImagesAllowsNullUsernameAndReturnsEmptyExaminations() {
        Patient patient = new Patient();
        patient.setId(5L);
        patient.setPatientCode("PAT_001");
        when(patientRepository.findByPatientCode("PAT_001")).thenReturn(Optional.of(patient));
        when(patientMapper.toResponse(patient)).thenReturn(new PatientResponse());
        when(examinationRepository.findByPatientIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/PAT_001");
        request.setContextPath("/api/v1");
        request.setServerName("localhost");
        request.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            PatientDetailsResponse result = patientService.getPatientDetailsWithImages("PAT_001", null);

            assertNotNull(result);
            assertEquals(0, result.getRecentExaminations().size());
            verify(auditLogRepository, never()).save(any());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
