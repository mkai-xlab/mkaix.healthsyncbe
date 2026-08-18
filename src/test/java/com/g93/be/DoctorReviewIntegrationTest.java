package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.AdjustKlGradeRequest;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class DoctorReviewIntegrationTest {

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
    private DiagnosisReviewRepository diagnosisReviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Role doctorRole;
    private Role hodRole;

    private Doctor assignedDoctor;
    private Doctor unassignedDoctor;
    private Doctor doctorNoPerms;
    private Doctor hodDoctor;

    private String assignedDoctorToken;
    private String unassignedDoctorToken;
    private String doctorNoPermsToken;
    private String hodDoctorToken;

    private Patient defaultPatient;
    private Examination exam1;
    private DicomInstance dicom1;
    private AiAnalysis aiAnalysis1;
    private AiResult aiResult1;

    private final List<Path> tempFilesCreated = new ArrayList<>();

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

        // Cleanup database tables in reverse dependency order
        diagnosisReviewRepository.deleteAll();
        aiResultRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        dicomInstanceRepository.deleteAll();
        examinationRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Fetch or create roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        hodRole = roleRepository.findByCode("DEPARTMENT_HEAD").orElseGet(() -> {
            Role r = new Role();
            r.setCode("DEPARTMENT_HEAD");
            r.setName("Department Head");
            return roleRepository.save(r);
        });

        // 2. Create Doctors
        // Assigned Doctor (has VIEW_PENDING_DIAGNOSIS, VIEW_AI_RESULT, OVERRIDE_AI_GRADE & CONFIRM_CONCLUSION in JWT)
        assignedDoctor = new Doctor();
        assignedDoctor.setUsername("assigned_doctor");
        assignedDoctor.setPassword(passwordEncoder.encode("password"));
        assignedDoctor.setFullName("Assigned Doctor");
        assignedDoctor.setEmail("assigned@hospital.com");
        assignedDoctor.setPhone("0123456781");
        assignedDoctor.setRole(doctorRole);
        assignedDoctor.setStatus(UserStatus.ACTIVE);
        assignedDoctor.setIsFirstActivated(false);
        assignedDoctor.setYearsOfExperience(5);
        assignedDoctor = userRepository.save(assignedDoctor);

        // Unassigned Doctor (has VIEW_PENDING_DIAGNOSIS, VIEW_AI_RESULT, OVERRIDE_AI_GRADE & CONFIRM_CONCLUSION in JWT)
        unassignedDoctor = new Doctor();
        unassignedDoctor.setUsername("unassigned_doctor");
        unassignedDoctor.setPassword(passwordEncoder.encode("password"));
        unassignedDoctor.setFullName("Unassigned Doctor");
        unassignedDoctor.setEmail("unassigned@hospital.com");
        unassignedDoctor.setPhone("0123456782");
        unassignedDoctor.setRole(doctorRole);
        unassignedDoctor.setStatus(UserStatus.ACTIVE);
        unassignedDoctor.setIsFirstActivated(false);
        unassignedDoctor.setYearsOfExperience(3);
        unassignedDoctor = userRepository.save(unassignedDoctor);

        // Doctor with NO permissions in JWT (but role = DOCTOR)
        doctorNoPerms = new Doctor();
        doctorNoPerms.setUsername("doctor_noperms");
        doctorNoPerms.setPassword(passwordEncoder.encode("password"));
        doctorNoPerms.setFullName("No Perms Doctor");
        doctorNoPerms.setEmail("noperms@hospital.com");
        doctorNoPerms.setPhone("0123456783");
        doctorNoPerms.setRole(doctorRole);
        doctorNoPerms.setStatus(UserStatus.ACTIVE);
        doctorNoPerms.setIsFirstActivated(false);
        doctorNoPerms.setYearsOfExperience(2);
        doctorNoPerms = userRepository.save(doctorNoPerms);

        // Department Head Doctor (role = DEPARTMENT_HEAD)
        hodDoctor = new Doctor();
        hodDoctor.setUsername("hod_doctor");
        hodDoctor.setPassword(passwordEncoder.encode("password"));
        hodDoctor.setFullName("HOD Doctor");
        hodDoctor.setEmail("hod@hospital.com");
        hodDoctor.setPhone("0123456784");
        hodDoctor.setRole(hodRole);
        hodDoctor.setStatus(UserStatus.ACTIVE);
        hodDoctor.setIsFirstActivated(false);
        hodDoctor.setYearsOfExperience(10);
        hodDoctor = userRepository.save(hodDoctor);

        // 3. Generate tokens with full required authorities
        PermissionResponse viewPendingPerm = new PermissionResponse(3L, "VIEW_PENDING_DIAGNOSIS", "View Pending Diagnosis", 1, "VIEW_PENDING_DIAGNOSIS", null);
        PermissionResponse viewAiResultPerm = new PermissionResponse(4L, "VIEW_AI_RESULT", "View AI Result", 1, "VIEW_AI_RESULT", null);
        PermissionResponse overridePerm = new PermissionResponse(1L, "OVERRIDE_AI_GRADE", "Override AI KL Grade", 1, "OVERRIDE_AI_GRADE", null);
        PermissionResponse confirmPerm = new PermissionResponse(2L, "CONFIRM_CONCLUSION", "Confirm Conclusion", 1, "CONFIRM_CONCLUSION", null);

        assignedDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(assignedDoctor, List.of(viewPendingPerm, viewAiResultPerm, overridePerm, confirmPerm)));
        
        unassignedDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(unassignedDoctor, List.of(viewPendingPerm, viewAiResultPerm, overridePerm, confirmPerm)));

        doctorNoPermsToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorNoPerms, Collections.emptyList()));

        hodDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(hodDoctor, Collections.emptyList())); 

        // 4. Set up mock clinical entities
        defaultPatient = new Patient();
        defaultPatient.setFullName("Patient One");
        defaultPatient.setPatientCode("PAT-REV-01");
        defaultPatient.setGender(Gender.MALE);
        defaultPatient.setDob(LocalDate.of(1990, 1, 1));
        defaultPatient = patientRepository.save(defaultPatient);

        // Examination 1: Status NEED_VERIFY, assigned to assignedDoctor
        exam1 = new Examination();
        exam1.setPatient(defaultPatient);
        exam1.setDoctor(assignedDoctor);
        exam1.setEncounterCode("ENC-REV-01");
        exam1.setStatus(ExaminationStatus.NEED_VERIFY);
        exam1.setStudyDate(LocalDate.now());
        exam1.setVisitTime(LocalDateTime.now());
        exam1 = examinationRepository.save(exam1);

        Image rawImage = new Image();
        rawImage.setFilePath("/images/raw_xray.jpg");
        rawImage = imageRepository.save(rawImage);

        Image heatmapImage = new Image();
        heatmapImage.setFilePath("/images/heatmap_xray.jpg");
        heatmapImage = imageRepository.save(heatmapImage);

        dicom1 = new DicomInstance();
        dicom1.setExamination(exam1);
        dicom1.setSopInstanceUid("1.2.3.4.567.890");
        dicom1.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicom1.setImage(rawImage);
        dicom1 = dicomInstanceRepository.save(dicom1);

        aiAnalysis1 = new AiAnalysis();
        aiAnalysis1.setDicomInstance(dicom1);
        aiAnalysis1.setStartTime(LocalDateTime.now());
        aiAnalysis1.setStatus("SUCCESS");
        aiAnalysis1 = aiAnalysisRepository.save(aiAnalysis1);

        aiResult1 = new AiResult();
        aiResult1.setAiAnalysis(aiAnalysis1);
        aiResult1.setPredictedGrade(2);
        aiResult1.setConfidence(0.85);
        aiResult1.setGradcamImage(heatmapImage);
        aiResult1 = aiResultRepository.save(aiResult1);

        // Link bi-directional collections explicitly to prevent cache gaps
        aiAnalysis1.setAiResults(new ArrayList<>(List.of(aiResult1)));
        aiAnalysisRepository.save(aiAnalysis1);

        dicom1.setAiAnalysis(aiAnalysis1);
        dicomInstanceRepository.save(dicom1);

        // Flush and Clear to push memory state to database and clear Hibernate cache
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        for (Path path : tempFilesCreated) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    private void createTempStorageFile(String relativePath, String content) throws Exception {
        Path path = Paths.get(storageBaseDir, relativePath);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes());
        tempFilesCreated.add(path);
    }


    //Review AI Diagnosis Result 

    @Test
    void testReviewAiDiagnosisResult_Success() throws Exception {
        // Fetching the examination returns the raw image URLs and the AI results
        mockMvc.perform(get("/examinations/" + exam1.getId())
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterCode", is("ENC-REV-01")))
                .andExpect(jsonPath("$.status", is("NEED_VERIFY")))
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].imageUrl", containsString("/dicom/instances/")))
                .andExpect(jsonPath("$.images[0].aiResults", hasSize(1)))
                .andExpect(jsonPath("$.images[0].aiResults[0].predictedGrade", is(2)))
                .andExpect(jsonPath("$.images[0].aiResults[0].confidence", is(0.85)));
    }

    @Test
    void testViewHeatmap_Success() throws Exception {
        String relativeHeatmapPath = "heatmap/heatmap_test.jpg";
        createTempStorageFile(relativeHeatmapPath, "mock heatmap binary data");

        // Set the storage path of the heatmap in DB
        AiResult res = aiResultRepository.findById(aiResult1.getId()).orElseThrow();
        res.setStorageHeatmapFilePath("/" + relativeHeatmapPath);
        aiResultRepository.save(res);

        entityManager.flush();
        entityManager.clear();

        // Fetching the heatmap image file returns 200 OK
        mockMvc.perform(get("/ai/heatmap/" + aiResult1.getId())
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void testViewHeatmap_NotFound() throws Exception {
        // If file doesn't exist on disk, returns 404
        mockMvc.perform(get("/ai/heatmap/" + aiResult1.getId())
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isNotFound());
    }

    // Function 2&3: Add clinical comment & adjust KL grade

    @Test
    void testAdjustKlGrade_Success() throws Exception {
        AdjustKlGradeRequest req = new AdjustKlGradeRequest(3, "Degradation and joint narrowing observed.");

        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + assignedDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedKlGrade", is(3)))
                .andExpect(jsonPath("$.decision", is("DOCTOR_ADJUSTED")))
                .andExpect(jsonPath("$.reviewNote", is("Degradation and joint narrowing observed.")))
                .andExpect(jsonPath("$.reviewedByDoctorId", is(assignedDoctor.getId().intValue())));

        // Verify state in DB
        DiagnosisReview dbReview = diagnosisReviewRepository.findByAiResultId(aiResult1.getId()).orElse(null);
        assertNotNull(dbReview);
        assertEquals(3, dbReview.getConfirmedKlGrade());
        assertEquals(DiagnosisReviewDecision.DOCTOR_ADJUSTED, dbReview.getDecision());
        assertEquals("Degradation and joint narrowing observed.", dbReview.getReviewNote());
    }

    @Test
    void testAdjustKlGrade_UnassignedDoctor_AccessDenied() throws Exception {
        AdjustKlGradeRequest req = new AdjustKlGradeRequest(3, "Access denied clinical note.");

        // Unassigned doctor with valid credentials but not assigned to the examination
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + unassignedDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdjustKlGrade_DoctorNoPerm_Forbidden() throws Exception {
        AdjustKlGradeRequest req = new AdjustKlGradeRequest(3, "No permission note.");

        // Doctor assigned to nothing and has no permission claim
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + doctorNoPermsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAdjustKlGrade_HodDoctor_Success() throws Exception {
        AdjustKlGradeRequest req = new AdjustKlGradeRequest(4, "HOD overrides unassigned exam.");

        // Department heads can adjust KL grades of any examination, even if unassigned
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + hodDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedKlGrade", is(4)))
                .andExpect(jsonPath("$.decision", is("DOCTOR_ADJUSTED")))
                .andExpect(jsonPath("$.reviewNote", is("HOD overrides unassigned exam.")))
                .andExpect(jsonPath("$.reviewedByDoctorId", is(hodDoctor.getId().intValue())));
    }

    @Test
    void testAdjustKlGrade_ValidationErrors() throws Exception {
        // Case A: confirmedKlGrade too high (5)
        AdjustKlGradeRequest reqHigh = new AdjustKlGradeRequest(5, "Too high grade.");
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + assignedDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqHigh)))
                .andExpect(status().isBadRequest());

        // Case B: confirmedKlGrade negative (-1)
        AdjustKlGradeRequest reqNeg = new AdjustKlGradeRequest(-1, "Negative grade.");
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + assignedDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNeg)))
                .andExpect(status().isBadRequest());

        // Case C: reviewNote empty
        AdjustKlGradeRequest reqEmptyNote = new AdjustKlGradeRequest(2, "");
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/kl-grade")
                        .header("Authorization", "Bearer " + assignedDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqEmptyNote)))
                .andExpect(status().isBadRequest());
    }

    //  confirm clinical conclusion

    @Test
    void testConfirmAiGrade_Success() throws Exception {
        // Confirming changes the status of the exam to VERIFIED since there's only 1 analysis to confirm
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/confirm")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedKlGrade", is(2)))
                .andExpect(jsonPath("$.decision", is("AI_CONFIRMED")))
                .andExpect(jsonPath("$.reviewNote", is("AI result confirmed")));

        entityManager.flush();
        entityManager.clear();

        // Verify status changes to VERIFIED
        Examination dbExam = examinationRepository.findById(exam1.getId()).orElse(null);
        assertNotNull(dbExam);
        assertEquals(ExaminationStatus.VERIFIED, dbExam.getStatus());
    }

    @Test
    void testConfirmAiGrade_UnassignedDoctor_AccessDenied() throws Exception {
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/confirm")
                        .header("Authorization", "Bearer " + unassignedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testConfirmAiGrade_HodDoctor_Success() throws Exception {
        // HOD can confirm unassigned
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/confirm")
                        .header("Authorization", "Bearer " + hodDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedKlGrade", is(2)))
                .andExpect(jsonPath("$.decision", is("AI_CONFIRMED")));

        entityManager.flush();
        entityManager.clear();

        // Verify status changes to VERIFIED
        Examination dbExam = examinationRepository.findById(exam1.getId()).orElse(null);
        assertNotNull(dbExam);
        assertEquals(ExaminationStatus.VERIFIED, dbExam.getStatus());
    }

    @Test
    void testConfirmAiGrade_AlreadyCompletedReport_Blocked() throws Exception {
        // Set the examination status to REPORT_GENERATED
        exam1.setStatus(ExaminationStatus.REPORT_GENERATED);
        exam1 = examinationRepository.save(exam1);

        entityManager.flush();
        entityManager.clear();

        // Submitting review on report-generated exam should throw IllegalArgumentException (which maps to 400 Bad Request)
        mockMvc.perform(put("/ai/results/" + aiResult1.getId() + "/confirm")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isBadRequest());
    }
}
