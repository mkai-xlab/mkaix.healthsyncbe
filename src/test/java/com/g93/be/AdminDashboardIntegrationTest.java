package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import com.g93.be.service.AiService;
import com.g93.be.service.DicomVerifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class AdminDashboardIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExaminationRepository examinationRepository;

    @Autowired
    private DicomInstanceRepository dicomInstanceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoBean
    private DicomVerifyService dicomVerifyService;

    @MockitoBean
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Role adminRole;
    private Role doctorRole;

    private User adminUser;
    private Doctor doctorUser;

    private String adminToken;
    private String doctorToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Alter database columns that might have been added by other branches to avoid default value constraints
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY failed_login_attempts INT DEFAULT 0 NULL").executeUpdate();
        } catch (Exception ignored) {}
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY lockout_until DATETIME NULL").executeUpdate();
        } catch (Exception ignored) {}
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY lockout_end DATETIME NULL").executeUpdate();
        } catch (Exception ignored) {}

        // Cleanup tables
        auditLogRepository.deleteAll();
        dicomInstanceRepository.deleteAll();
        examinationRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup Roles
        adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        // 2. Setup Users
        adminUser = new User();
        adminUser.setUsername("sys_admin");
        adminUser.setPassword(passwordEncoder.encode("password"));
        adminUser.setFullName("System Admin");
        adminUser.setEmail("admin.dash@hospital.com");
        adminUser.setPhone("0771111111");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        adminUser = userRepository.save(adminUser);

        doctorUser = new Doctor();
        doctorUser.setUsername("doc_staff");
        doctorUser.setPassword(passwordEncoder.encode("password"));
        doctorUser.setFullName("Doctor Staff");
        doctorUser.setEmail("doc.staff@hospital.com");
        doctorUser.setPhone("0772222222");
        doctorUser.setRole(doctorRole);
        doctorUser.setStatus(UserStatus.ACTIVE);
        doctorUser.setIsFirstActivated(false);
        doctorUser.setYearsOfExperience(4);
        doctorUser = userRepository.save(doctorUser);

        PermissionResponse uploadDicomPerm = new com.g93.be.dto.PermissionResponse(1L, "UPLOAD_DICOM_IMAGE", "Upload DICOM", 1, "UPLOAD_DICOM_IMAGE", null);
        PermissionResponse triggerAiPerm = new com.g93.be.dto.PermissionResponse(2L, "TRIGGER_AI_ANALYSIS", "Trigger AI", 1, "TRIGGER_AI_ANALYSIS", null);

        // 3. Generate tokens
        adminToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(adminUser, Collections.emptyList()));
        doctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser, java.util.List.of(uploadDicomPerm, triggerAiPerm)));

        entityManager.flush();
        entityManager.clear();
    }

    // View admin dashboard

    @Test
    void testViewAdminDashboard_StaffList_Success() throws Exception {
        // Admin gets the medical staff list
        mockMvc.perform(get("/users/staff")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("doc.staff@hospital.com")))
                .andExpect(jsonPath("$[0].fullName", is("Doctor Staff")));
    }

    @Test
    void testViewAdminDashboard_StaffList_NonAdmin_Forbidden() throws Exception {
        // Doctor is forbidden from retrieving the staff list directly
        mockMvc.perform(get("/users/staff")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    //View user activities
    @Test
    void testViewUserActivities_Success() throws Exception {
        // Setup mock audit log entries
        AuditLog log1 = new AuditLog();
        log1.setUser(adminUser);
        log1.setTitle("USER_LOGIN");
        log1.setDescription("Admin logged in successfully");
        log1.setIpAddress("127.0.0.1");
        log1.setTimeStamp(LocalDateTime.now());
        auditLogRepository.save(log1);

        AuditLog log2 = new AuditLog();
        log2.setUser(doctorUser);
        log2.setTitle("EXPORT_DOWNLOAD_PDF");
        log2.setDescription("Doctor downloaded pdf report");
        log2.setIpAddress("192.168.1.10");
        log2.setTimeStamp(LocalDateTime.now());
        auditLogRepository.save(log2);

        entityManager.flush();
        entityManager.clear();

        // Admin requests audit logs
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("USER_LOGIN", "EXPORT_DOWNLOAD_PDF")))
                .andExpect(jsonPath("$.content[?(@.title == 'USER_LOGIN')].username", contains("sys_admin")));
    }

    @Test
    void testViewUserActivities_NonAdmin_Forbidden() throws Exception {
        // Non-admin user is rejected from accessing audit logs
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    //View operational statistics
    @Test
    void testViewOperationalStatistics_TotalStudies() throws Exception {
        // Setup mock dicom instance with study uid
        Patient p = new Patient();
        p.setFullName("Dicom Patient");
        p.setPatientCode("PAT-OP-01");
        p.setGender(Gender.MALE);
        p.setDob(LocalDate.of(1980, 1, 1));
        p = patientRepository.save(p);

        Examination ex = new Examination();
        ex.setPatient(p);
        ex.setEncounterCode("ENC-OP-01");
        ex.setStatus(ExaminationStatus.NEED_VERIFY);
        ex.setStudyDate(LocalDate.now());
        ex = examinationRepository.save(ex);

        DicomInstance d1 = new DicomInstance();
        d1.setExamination(ex);
        d1.setSopInstanceUid("9.9.9.1");
        d1.setStudyInstanceUid("STUDY-100");
        d1.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicomInstanceRepository.save(d1);

        DicomInstance d2 = new DicomInstance();
        d2.setExamination(ex);
        d2.setSopInstanceUid("9.9.9.2");
        d2.setStudyInstanceUid("STUDY-100"); // Same study UID
        d2.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicomInstanceRepository.save(d2);

        DicomInstance d3 = new DicomInstance();
        d3.setExamination(ex);
        d3.setSopInstanceUid("9.9.9.3");
        d3.setStudyInstanceUid("STUDY-200"); // Different study UID
        d3.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicomInstanceRepository.save(d3);

        entityManager.flush();
        entityManager.clear();

        // Get total studies count
        mockMvc.perform(get("/dicom/total-studies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(2)));
    }

    // View AI analysis operations & statistics

    @Test
    void testViewAiAnalysisOperations_Success() throws Exception {
        DicomVerifyRequest verifyRequest = new DicomVerifyRequest("session_123", List.of("PAT-OP-01"));

        // Setup mock return values for verify session
        when(dicomVerifyService.verifySession(any(DicomVerifyRequest.class), any(Long.class), anyBoolean()))
                .thenReturn(new VerifySessionResultDto(List.of(101L, 102L), List.of()));

        // Setup mock response from AI prediction batch
        PatientResponse patResponse = new PatientResponse();
        patResponse.setId(10L);
        patResponse.setPatientCode("PAT-OP-01");
        patResponse.setFullName("Dicom Patient");

        ExaminationDto mockExam = new ExaminationDto();
        mockExam.setExaminationId(50L);
        mockExam.setEncounterCode("ENC-OP-01");
        mockExam.setStatus("NEED_VERIFY");
        mockExam.setMaxPredictedGrade(3);
        mockExam.setPatient(patResponse);

        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(List.of(mockExam));

        // Execute post request to verify session
        mockMvc.perform(post("/dicom/verify")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedInstanceIds", hasSize(2)))
                .andExpect(jsonPath("$.savedInstanceIds[0]", is(101)))
                .andExpect(jsonPath("$.failedPatients", hasSize(0)));
    }

    // View system performance

    @Test
    void testViewSystemPerformance_AdminExamsTotal() throws Exception {
        // Calling examinations total with adminUserId should return 0 since admin has no patients/exams assigned
        mockMvc.perform(get("/examinations/total?userId=" + adminUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(0)));
    }
}
