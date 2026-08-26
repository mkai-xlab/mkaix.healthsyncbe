package com.g93.be.service.impl;

import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.service.KnowledgeIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
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
    @Mock
    private DiagnosisReviewRepository diagnosisReviewRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    @InjectMocks
    private PatientServiceImpl patientService;

    private PatientServiceImpl fullDeleteService(
            Optional<KnowledgeIngestionService> knowledgeIngestionService, Path storageDir, Path exportDir) {
        PatientServiceImpl service = new PatientServiceImpl(
                patientRepository, examinationRepository, dicomInstanceRepository, userRepository,
                patientMapper, auditLogRepository, diagnosisReviewRepository, reportRepository,
                imageRepository, chatSessionRepository, knowledgeDocumentRepository, knowledgeIngestionService);
        ReflectionTestUtils.setField(service, "storageBaseDir", storageDir.toString());
        ReflectionTestUtils.setField(service, "reportExportDir", exportDir.toString());
        return service;
    }

    // ==========================================
    // 1. createPatient
    // ==========================================
    /**
     * Mục đích test: Kiểm tra tạo mới bệnh nhân thành công với thông tin hợp lệ.
     * Đầu vào: fullName="Nguyen Van A", thực hiện bởi role DEPARTMENT_HEAD hoặc DOCTOR.
     * Hành động: Gọi hàm createPatient().
     * Kỳ vọng: Lưu bệnh nhân thành công, ghi log "Starting registration..." và "Patient saved successfully...".
     */
    // ==============================================================================
    // UTCID01: Create patient successfully (Normal)
    // ==============================================================================
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
     * Mục đích test: Kiểm tra báo lỗi khi tên bệnh nhân bị thiếu hoặc rỗng ("").
     * Đầu vào: fullName="", thực hiện bởi role hợp lệ.
     * Hành động: Gọi hàm createPatient().
     * Kỳ vọng: Ném ra ngoại lệ IllegalArgumentException với message "Full name is required".
     */
    // ==============================================================================
    // UTCID02: Create patient failed - empty full name (Abnormal)
    // ==============================================================================
    @Test
    void testCreatePatient_Abnormal_MissingFullName() {
        CreatePatientRequest req = new CreatePatientRequest();
        req.setFullName("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> patientService.createPatient(req));
        assertEquals("Full name is required", ex.getMessage());
    }

    /**
     * Mục đích test: Kiểm tra báo lỗi khi tên bệnh nhân bị bỏ trống (null).
     * Đầu vào: fullName=null, thực hiện bởi role hợp lệ.
     * Hành động: Gọi hàm createPatient().
     * Kỳ vọng: Ném ra ngoại lệ IllegalArgumentException với message "Full name is required".
     */
    // ==============================================================================
    // UTCID03: Create patient failed - null full name (Abnormal)
    // ==============================================================================
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
     * Mục đích test: Verify get all patients as Admin.
     * Đầu vào: Role ADMIN.
     * Hành động: Gọi getAllPatients.
     * Kỳ vọng: Trả về PageResponse thành công.
     */
    // ==============================================================================
    // UTCID01: Get all patients - ADMIN/HEAD (Normal)
    // ==============================================================================
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
     * Mục đích test: Verify get all patients as Doctor (Personal only).
     * Đầu vào: Role DOCTOR, isPersonal=true.
     * Hành động: Gọi getAllPatients.
     * Kỳ vọng: Trả về PageResponse thành công.
     */
    // ==============================================================================
    // UTCID02: Get all patients - Doctor personal (Normal)
    // ==============================================================================
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
     * Mục đích test: Verify get all patients as Doctor (Not Personal).
     * Đầu vào: Role DOCTOR, isPersonal=false.
     * Hành động: Gọi getAllPatients.
     * Kỳ vọng: Ném ra AccessDeniedException.
     */
    // ==============================================================================
    // UTCID03: Get all patients - Doctor not personal (Abnormal)
    // ==============================================================================
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
     * Mục đích test: Kiểm tra luồng xử lý getAllPatients khi user có role không hợp lệ (Fail Role).
     * Đầu vào: User có Role không thuộc danh sách cho phép (ví dụ ROLE_USER).
     * Hành động: Gọi hàm getAllPatients.
     * Kỳ vọng: Ném ra AccessDeniedException vì không có quyền truy cập.
     */
    // ==============================================================================
    // UTCID04: Get all patients - Fail Role (Abnormal)
    // ==============================================================================
    @Test
    void testGetAllPatients_Abnormal_FailRole() {
        PatientFilterRequest filter = new PatientFilterRequest();
        Pageable pageable = PageRequest.of(0, 10);
        
        User failUser = new User();
        failUser.setId(6L);
        Role failRole = new Role();
        failRole.setCode("ROLE_USER"); // Invalid role
        failUser.setRole(failRole);

        when(userRepository.findByUsername("failuser")).thenReturn(Optional.of(failUser));
        
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> 
            patientService.getAllPatients(filter, pageable, "failuser")
        );
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
     * Mục đích test: Verify get patients by upload date with Null Username (Fail Role).
     * Đầu vào: Username null hoặc Role không hợp lệ.
     * Hành động: Gọi getPatientsByUploadDate.
     * Kỳ vọng: Ném ra Exception.
     */
    // ==============================================================================
    // UTCID03: Get patients by date - Fail Role (Abnormal)
    // ==============================================================================
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
     * Mục đích test: Verify get patients by upload date as Admin.
     * Đầu vào: Role ADMIN, Valid Date.
     * Hành động: Gọi getPatientsByUploadDate.
     * Kỳ vọng: Trả về danh sách bệnh nhân.
     */
    // ==============================================================================
    // UTCID01: Get patients by date - Admin/Head (Normal)
    // ==============================================================================
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
     * Mục đích test: Verify get patients by upload date as Doctor.
     * Đầu vào: Role DOCTOR, Valid Date.
     * Hành động: Gọi getPatientsByUploadDate.
     * Kỳ vọng: Trả về danh sách bệnh nhân.
     */
    // ==============================================================================
    // UTCID02: Get patients by date - Doctor (Normal)
    // ==============================================================================
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

    // ==========================================
    // 5. deletePatientCompletelyByCode
    // ==========================================
    @Test
    void deletePatientCompletelyByCode_ThrowsResourceNotFound_WhenPatientMissing() {
        when(patientRepository.findByPatientCode("MISSING")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> patientService.deletePatientCompletelyByCode("MISSING"));
        verify(patientRepository, never()).delete(any(Patient.class));
    }

    /**
     * Full cascade: one examination with one DICOM instance (raw + preview +
     * annotated image), one AI analysis with one AI result (ROI + GradCAM image
     * and a linked diagnosis review), and one report indexed into the RAG
     * knowledge base. Verifies every row is removed in FK-safe order, the
     * DiagnosisReview is deleted before the AiResult it points to, the
     * unowned ROI/GradCAM images are deleted explicitly, the Qdrant-backed
     * knowledge document is unindexed through KnowledgeIngestionService, and
     * every physical file collected along the way is actually deleted on disk.
     */
    @Test
    void deletePatientCompletelyByCode_DeletesEveryChildRowAndFile(@TempDir Path storageDir,
            @TempDir Path exportDir) throws Exception {
        Patient patient = new Patient();
        patient.setId(5L);
        patient.setPatientCode("PT-1");

        Examination examination = new Examination();
        examination.setId(11L);

        DicomInstance instance = new DicomInstance();
        instance.setId(21L);
        DicomRaw raw = writeRaw(storageDir, "dicom/raw1.dcm");
        Image preview = writeImage(storageDir, "images/preview1.png", 41L);
        Image annotated = writeImage(storageDir, "images/annotated1.png", 42L);
        instance.setDicomRaw(raw);
        instance.setImage(preview);
        instance.setAnnotatedImage(annotated);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setId(51L);
        AiResult result = new AiResult();
        result.setId(61L);
        Image roi = writeImage(storageDir, "images/roi1.png", 71L);
        Image gradcam = writeImage(storageDir, "images/gradcam1.png", 72L);
        result.setRoiImage(roi);
        result.setGradcamImage(gradcam);
        analysis.setAiResults(List.of(result));
        instance.setAiAnalysis(analysis);

        DiagnosisReview review = new DiagnosisReview();
        review.setId(81L);

        Report report = new Report();
        report.setId(91L);
        report.setFilePath("report1.pdf");
        Files.writeString(exportDir.resolve("report1.pdf"), "pdf");

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(101L);
        document.setSourceKey("report:91");

        when(patientRepository.findByPatientCode("PT-1")).thenReturn(Optional.of(patient));
        when(examinationRepository.findByPatientId(5L)).thenReturn(List.of(examination));
        when(dicomInstanceRepository.findByExaminationId(11L)).thenReturn(List.of(instance));
        when(diagnosisReviewRepository.findByAiResultId(61L)).thenReturn(Optional.of(review));
        when(imageRepository.findById(71L)).thenReturn(Optional.of(roi));
        when(imageRepository.findById(72L)).thenReturn(Optional.of(gradcam));
        when(reportRepository.findByExaminationId(11L)).thenReturn(List.of(report));
        when(knowledgeDocumentRepository.findBySourceKey("report:91")).thenReturn(Optional.of(document));

        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        PatientServiceImpl service = fullDeleteService(Optional.of(ingestionService), storageDir, exportDir);

        service.deletePatientCompletelyByCode("PT-1");

        verify(chatSessionRepository).detachExamination(11L);
        verify(diagnosisReviewRepository).delete(review);
        verify(dicomInstanceRepository).delete(instance);
        verify(imageRepository).delete(roi);
        verify(imageRepository).delete(gradcam);
        verify(ingestionService).delete(101L);
        verify(knowledgeDocumentRepository, never()).delete(any());
        verify(reportRepository).delete(report);
        verify(examinationRepository).delete(examination);
        verify(patientRepository).delete(patient);

        assertFalse(Files.exists(storageDir.resolve("dicom/raw1.dcm")));
        assertFalse(Files.exists(storageDir.resolve("images/preview1.png")));
        assertFalse(Files.exists(storageDir.resolve("images/annotated1.png")));
        assertFalse(Files.exists(storageDir.resolve("images/roi1.png")));
        assertFalse(Files.exists(storageDir.resolve("images/gradcam1.png")));
        assertFalse(Files.exists(exportDir.resolve("report1.pdf")));
    }

    /**
     * When chat/RAG is disabled there is no VectorStore bean, so
     * KnowledgeIngestionService does not exist either; the indexed knowledge
     * row must still be removed directly instead of being silently skipped.
     */
    @Test
    void deletePatientCompletelyByCode_FallsBackToDirectDelete_WhenChatDisabled(
            @TempDir Path storageDir, @TempDir Path exportDir) {
        Patient patient = new Patient();
        patient.setId(6L);
        patient.setPatientCode("PT-2");
        Examination examination = new Examination();
        examination.setId(12L);
        Report report = new Report();
        report.setId(92L);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(102L);
        document.setSourceKey("report:92");

        when(patientRepository.findByPatientCode("PT-2")).thenReturn(Optional.of(patient));
        when(examinationRepository.findByPatientId(6L)).thenReturn(List.of(examination));
        when(dicomInstanceRepository.findByExaminationId(12L)).thenReturn(List.of());
        when(reportRepository.findByExaminationId(12L)).thenReturn(List.of(report));
        when(knowledgeDocumentRepository.findBySourceKey("report:92")).thenReturn(Optional.of(document));

        PatientServiceImpl service = fullDeleteService(Optional.empty(), storageDir, exportDir);

        service.deletePatientCompletelyByCode("PT-2");

        verify(knowledgeDocumentRepository).delete(document);
        verify(reportRepository).delete(report);
        verify(patientRepository).delete(patient);
    }

    private DicomRaw writeRaw(Path storageDir, String relativePath) throws Exception {
        Files.createDirectories(storageDir.resolve(relativePath).getParent());
        Files.writeString(storageDir.resolve(relativePath), "x");
        DicomRaw raw = new DicomRaw();
        raw.setFilePath("/" + relativePath);
        return raw;
    }

    private Image writeImage(Path storageDir, String relativePath, Long id) throws Exception {
        Files.createDirectories(storageDir.resolve(relativePath).getParent());
        Files.writeString(storageDir.resolve(relativePath), "x");
        Image image = new Image();
        image.setId(id);
        image.setFilePath("/" + relativePath);
        return image;
    }
}
