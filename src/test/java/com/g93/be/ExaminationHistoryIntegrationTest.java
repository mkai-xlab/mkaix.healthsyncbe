package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class ExaminationHistoryIntegrationTest {

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
    private ImageRepository imageRepository;

    @Autowired
    private AiResultRepository aiResultRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Role doctorRole;
    private Role hodRole;
    private Role guestRole;

    private Doctor doctorUser;
    private User hodUser;
    private User guestUser;

    private String doctorToken;
    private String hodToken;
    private String guestToken;

    private Patient patientWithHistory;
    private Patient patientNoHistory;

    private Examination exam2023;
    private Examination exam2026;

    private DicomInstance dicom2023;
    private DicomInstance dicom2026;

    private AiResult result2023;
    private AiResult result2026;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        patientRepository.deleteAll();

        // 1. Setup Roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));
        
        hodRole = roleRepository.findByCode("HOD").orElseGet(() -> {
            Role r = new Role();
            r.setCode("HOD");
            r.setName("Head of Department");
            return roleRepository.save(r);
        });

        guestRole = roleRepository.findByCode("GUEST").orElseGet(() -> {
            Role r = new Role();
            r.setCode("GUEST");
            r.setName("Guest User");
            return roleRepository.save(r);
        });

        // 2. Setup Users
        Doctor doc = new Doctor();
        doc.setUsername("history_doctor");
        doc.setPassword(passwordEncoder.encode("doctor_password"));
        doc.setFullName("History Doctor");
        doc.setEmail("doc_history@hospital.com");
        doc.setPhone("0123456781");
        doc.setRole(doctorRole);
        doc.setStatus(UserStatus.ACTIVE);
        doc.setIsFirstActivated(false);
        doc.setYearsOfExperience(5);
        doctorUser = (Doctor) userRepository.save(doc);

        hodUser = new User();
        hodUser.setUsername("history_hod");
        hodUser.setPassword(passwordEncoder.encode("hod_password"));
        hodUser.setFullName("History HOD");
        hodUser.setEmail("hod_history@hospital.com");
        hodUser.setPhone("0123456782");
        hodUser.setRole(hodRole);
        hodUser.setStatus(UserStatus.ACTIVE);
        hodUser.setIsFirstActivated(false);
        userRepository.save(hodUser);

        guestUser = new User();
        guestUser.setUsername("history_guest");
        guestUser.setPassword(passwordEncoder.encode("guest_password"));
        guestUser.setFullName("History Guest");
        guestUser.setEmail("guest_history@hospital.com");
        guestUser.setPhone("0123456783");
        guestUser.setRole(guestRole);
        guestUser.setStatus(UserStatus.ACTIVE);
        guestUser.setIsFirstActivated(false);
        userRepository.save(guestUser);

        // 3. Generate tokens (Stateless JWT token claims)
        List<PermissionResponse> doctorPerms = List.of(
            new PermissionResponse(1L, "READ_PATIENT_LIST", "Read Patient List", 1, "READ_PATIENT_LIST", null),
            new PermissionResponse(2L, "VIEW_PATIENT_DETAIL", "View Patient Detail", 1, "VIEW_PATIENT_DETAIL", null)
        );
        doctorToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctorUser, doctorPerms));
        hodToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(hodUser, doctorPerms));
        guestToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(guestUser, new ArrayList<>()));

        // 4. Create patients
        patientWithHistory = new Patient();
        patientWithHistory.setFullName("Patient With History");
        patientWithHistory.setPatientCode("PAT-HIST-123");
        patientWithHistory.setGender(Gender.MALE);
        patientWithHistory.setDob(LocalDate.of(1980, 1, 1));
        patientWithHistory = patientRepository.save(patientWithHistory);

        patientNoHistory = new Patient();
        patientNoHistory.setFullName("Patient No History");
        patientNoHistory.setPatientCode("PAT-NOHIST-456");
        patientNoHistory.setGender(Gender.FEMALE);
        patientNoHistory.setDob(LocalDate.of(1995, 5, 5));
        patientNoHistory = patientRepository.save(patientNoHistory);

        // 5. Setup historical scan and active scan for patientWithHistory
        // 2023 Scan (Historical)
        exam2023 = new Examination();
        exam2023.setPatient(patientWithHistory);
        exam2023.setEncounterCode("ENC-2023");
        exam2023.setStatus(ExaminationStatus.VERIFIED);
        exam2023.setStudyDate(LocalDate.of(2023, 5, 10));
        exam2023.setVisitTime(LocalDateTime.of(2023, 5, 10, 10, 0));
        exam2023.setDoctor(doctorUser);
        exam2023.setMaxPredictedGrade(2);
        exam2023 = examinationRepository.save(exam2023);

        Image img2023 = new Image();
        img2023.setFilePath("/images/test_img_2023.png");
        img2023 = imageRepository.save(img2023);

        dicom2023 = new DicomInstance();
        dicom2023.setSopInstanceUid("1.2.3.4.999.2023");
        dicom2023.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicom2023.setExamination(exam2023);
        dicom2023.setImage(img2023);
        dicom2023 = dicomInstanceRepository.save(dicom2023);

        AiAnalysis analysis2023 = new AiAnalysis();
        analysis2023.setDicomInstance(dicom2023);
        analysis2023.setStatus("SUCCESS");
        analysis2023.setStartTime(LocalDateTime.now());
        analysis2023.setDuration(100L);
        analysis2023 = aiAnalysisRepository.save(analysis2023);

        result2023 = new AiResult();
        result2023.setStorageHeatmapFilePath("/heatmap/heatmap_2023.jpg");
        result2023.setPredictedGrade(2);
        result2023.setConfidence(0.75);
        result2023.setAiAnalysis(analysis2023);
        result2023 = aiResultRepository.save(result2023);

        // 2026 Scan (Current Active)
        exam2026 = new Examination();
        exam2026.setPatient(patientWithHistory);
        exam2026.setEncounterCode("ENC-2026");
        exam2026.setStatus(ExaminationStatus.AI_PROCESSING);
        exam2026.setStudyDate(LocalDate.of(2026, 7, 20));
        exam2026.setVisitTime(LocalDateTime.of(2026, 7, 20, 14, 30));
        exam2026.setDoctor(doctorUser);
        exam2026.setMaxPredictedGrade(4);
        exam2026 = examinationRepository.save(exam2026);

        Image img2026 = new Image();
        img2026.setFilePath("/images/test_img_2026.png");
        img2026 = imageRepository.save(img2026);

        dicom2026 = new DicomInstance();
        dicom2026.setSopInstanceUid("1.2.3.4.999.2026");
        dicom2026.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicom2026.setExamination(exam2026);
        dicom2026.setImage(img2026);
        dicom2026 = dicomInstanceRepository.save(dicom2026);

        AiAnalysis analysis2026 = new AiAnalysis();
        analysis2026.setDicomInstance(dicom2026);
        analysis2026.setStatus("SUCCESS");
        analysis2026.setStartTime(LocalDateTime.now());
        analysis2026.setDuration(120L);
        analysis2026 = aiAnalysisRepository.save(analysis2026);

        result2026 = new AiResult();
        result2026.setStorageHeatmapFilePath("/heatmap/heatmap_2026.jpg");
        result2026.setPredictedGrade(4);
        result2026.setConfidence(0.92);
        result2026.setAiAnalysis(analysis2026);
        result2026 = aiResultRepository.save(result2026);

        entityManager.flush();
        entityManager.clear();
    }

    // REVIEW PREVIOUS AI RESULTS & DUAL-CANVAS

    @Test
    void testWorkstationMounting_AndDualCanvasRendering() throws Exception {
        // Dual-canvas workstations retrieve historical and active encounters
        // Verify we can fetch historical scan (ENC-2023) and active scan (ENC-2026)
        mockMvc.perform(get("/examinations/" + exam2023.getId())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterCode", is("ENC-2023")))
                .andExpect(jsonPath("$.images[0].imageUrl", containsString("/dicom/instances/")));

        mockMvc.perform(get("/examinations/" + exam2026.getId())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterCode", is("ENC-2026")))
                .andExpect(jsonPath("$.images[0].imageUrl", containsString("/dicom/instances/")));
    }

    @Test
    void testVectorTrendLineChartGeneration_HistoricalData() throws Exception {
        // Retrieve examinations by patient ID to construct vector trend charts
        mockMvc.perform(get("/examinations/patient/" + patientWithHistory.getId())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Verify historical trend metrics: KL Grades 2 and 4 are present
                .andExpect(jsonPath("$.content[*].maxPredictedGrade", containsInAnyOrder(2, 4)))
                .andExpect(jsonPath("$.content[*].studyDate", containsInAnyOrder("2023-05-10", "2026-07-20")));
    }

    @Test
    void testPlaceholder_WhenNoHistoricalAssets() throws Exception {
        // Verify patient details returns empty recent examinations list if patient has no scan assets
        mockMvc.perform(get("/patients/" + patientNoHistory.getPatientCode() + "/details")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.patientCode", is("PAT-NOHIST-456")))
                .andExpect(jsonPath("$.recentExaminations", hasSize(0)));
    }

    @Test
    void testSideBySideHeatmaps() throws Exception {
        // Verify AI heatmap images for 2023 and 2026 are viewable
        // Create mock heatmap files on disk to prevent 404
        Path file2023 = Paths.get(storageBaseDir, "heatmap/heatmap_2023.jpg");
        Path file2026 = Paths.get(storageBaseDir, "heatmap/heatmap_2026.jpg");
        Files.createDirectories(file2023.getParent());
        Files.write(file2023, "fake 2023 heatmap".getBytes());
        Files.write(file2026, "fake 2026 heatmap".getBytes());

        try {
            mockMvc.perform(get("/ai/heatmap/" + result2023.getId())
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/ai/heatmap/" + result2026.getId())
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk());
        } finally {
            Files.deleteIfExists(file2023);
            Files.deleteIfExists(file2026);
        }
    }

    @Test
    void testCompareScansAcrossDistinctYears() throws Exception {
        // Verify retrieval of scan details from distinct years 2023 and 2026
        mockMvc.perform(get("/examinations/patient/" + patientWithHistory.getId())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].studyDate", containsString("202")))
                .andExpect(jsonPath("$.content[1].studyDate", containsString("202")));
    }
  
    // ADVANCED FILTERING & SEARCH HISTORY

    @Test
    void testFilterHistoryByClinician() throws Exception {
        // Filter by assigned doctor/clinician
        mockMvc.perform(get("/examinations/doctor/" + doctorUser.getId())
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void testFilterHistoryBySeverityGrade() throws Exception {
        // Filter by Kellgren-Lawrence severity chips (Grade 4)
        mockMvc.perform(get("/examinations/grade?grade=4")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].maxPredictedGrade", is(4)));
    }

    @Test
    void testFilterHistoryByStudyDate() throws Exception {
        // Filter by study date
        mockMvc.perform(get("/examinations/filter/study-date?date=2023-05-10")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].studyDate", is("2023-05-10")));
    }

    // ACCESS CONTROL & ROLE VERIFICATION

    @Test
    void testHistoryAccess_RoleDoctor_Allowed() throws Exception {
        mockMvc.perform(get("/examinations")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void testHistoryAccess_RoleHod_Allowed() throws Exception {
        mockMvc.perform(get("/examinations")
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk());
    }

    @Test
    void testHistoryAccess_GuestRole_Rejected() throws Exception {
        // Unauthenticated or guest role attempts should be blocked/rejected
        mockMvc.perform(get("/examinations/sort/study-date"))
                .andExpect(status().isForbidden());
    }
}
