package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.dto.ReportResponse;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class ClinicalReportIntegrationTest {

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
    private ReportRepository reportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Value("${app.pdf.export-dir}")
    private String exportDir;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
    private DiagnosisReview review1;

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
        reportRepository.deleteAll();
        diagnosisReviewRepository.deleteAll();
        aiResultRepository.deleteAll();
        aiAnalysisRepository.deleteAll();
        dicomInstanceRepository.deleteAll();
        examinationRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // Fetch or create roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        hodRole = roleRepository.findByCode("DEPARTMENT_HEAD").orElseGet(() -> {
            Role r = new Role();
            r.setCode("DEPARTMENT_HEAD");
            r.setName("Department Head");
            return roleRepository.save(r);
        });

        // Create Doctors
        assignedDoctor = new Doctor();
        assignedDoctor.setUsername("assigned_doc");
        assignedDoctor.setPassword(passwordEncoder.encode("password"));
        assignedDoctor.setFullName("Assigned Doctor");
        assignedDoctor.setEmail("assigned.doc@hospital.com");
        assignedDoctor.setPhone("0987654321");
        assignedDoctor.setRole(doctorRole);
        assignedDoctor.setStatus(UserStatus.ACTIVE);
        assignedDoctor.setIsFirstActivated(false);
        assignedDoctor.setYearsOfExperience(6);
        assignedDoctor = userRepository.save(assignedDoctor);

        unassignedDoctor = new Doctor();
        unassignedDoctor.setUsername("unassigned_doc");
        unassignedDoctor.setPassword(passwordEncoder.encode("password"));
        unassignedDoctor.setFullName("Unassigned Doctor");
        unassignedDoctor.setEmail("unassigned.doc@hospital.com");
        unassignedDoctor.setPhone("0987654322");
        unassignedDoctor.setRole(doctorRole);
        unassignedDoctor.setStatus(UserStatus.ACTIVE);
        unassignedDoctor.setIsFirstActivated(false);
        unassignedDoctor.setYearsOfExperience(4);
        unassignedDoctor = userRepository.save(unassignedDoctor);

        doctorNoPerms = new Doctor();
        doctorNoPerms.setUsername("noperms_doc");
        doctorNoPerms.setPassword(passwordEncoder.encode("password"));
        doctorNoPerms.setFullName("No Perms Doctor");
        doctorNoPerms.setEmail("noperms.doc@hospital.com");
        doctorNoPerms.setPhone("0987654323");
        doctorNoPerms.setRole(doctorRole);
        doctorNoPerms.setStatus(UserStatus.ACTIVE);
        doctorNoPerms.setIsFirstActivated(false);
        doctorNoPerms.setYearsOfExperience(2);
        doctorNoPerms = userRepository.save(doctorNoPerms);

        hodDoctor = new Doctor();
        hodDoctor.setUsername("hod_doc");
        hodDoctor.setPassword(passwordEncoder.encode("password"));
        hodDoctor.setFullName("HOD Doctor");
        hodDoctor.setEmail("hod.doc@hospital.com");
        hodDoctor.setPhone("0987654324");
        hodDoctor.setRole(hodRole);
        hodDoctor.setStatus(UserStatus.ACTIVE);
        hodDoctor.setIsFirstActivated(false);
        hodDoctor.setYearsOfExperience(12);
        hodDoctor = userRepository.save(hodDoctor);

        // Generate tokens with full required authorities
        PermissionResponse generateReportPerm = new PermissionResponse(5L, "GENERATE_PDF_REPORT", "Generate PDF Report", 1, "GENERATE_PDF_REPORT", null);
        PermissionResponse downloadReportPerm = new PermissionResponse(6L, "EXPORT_DOWNLOAD_PDF", "Export Download PDF", 1, "EXPORT_DOWNLOAD_PDF", null);
        PermissionResponse viewPendingPerm = new PermissionResponse(3L, "VIEW_PENDING_DIAGNOSIS", "View Pending Diagnosis", 1, "VIEW_PENDING_DIAGNOSIS", null);
        PermissionResponse viewAiResultPerm = new PermissionResponse(4L, "VIEW_AI_RESULT", "View AI Result", 1, "VIEW_AI_RESULT", null);
        PermissionResponse overridePerm = new PermissionResponse(1L, "OVERRIDE_AI_GRADE", "Override AI KL Grade", 1, "OVERRIDE_AI_GRADE", null);
        PermissionResponse confirmPerm = new PermissionResponse(2L, "CONFIRM_CONCLUSION", "Confirm Conclusion", 1, "CONFIRM_CONCLUSION", null);

        assignedDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(assignedDoctor, List.of(generateReportPerm, downloadReportPerm, viewPendingPerm, viewAiResultPerm, overridePerm, confirmPerm)));

        unassignedDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(unassignedDoctor, List.of(generateReportPerm, downloadReportPerm, viewPendingPerm, viewAiResultPerm, overridePerm, confirmPerm)));

        doctorNoPermsToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorNoPerms, Collections.emptyList()));

        hodDoctorToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(hodDoctor, Collections.emptyList()));

        // Set up mock clinical entities
        defaultPatient = new Patient();
        defaultPatient.setFullName("Clinical Patient");
        defaultPatient.setPatientCode("PAT-REPORT-01");
        defaultPatient.setGender(Gender.FEMALE);
        defaultPatient.setDob(LocalDate.of(1985, 5, 15));
        defaultPatient = patientRepository.save(defaultPatient);

        exam1 = new Examination();
        exam1.setPatient(defaultPatient);
        exam1.setDoctor(assignedDoctor);
        exam1.setEncounterCode("ENC-REPORT-01");
        exam1.setStatus(ExaminationStatus.VERIFIED);
        exam1.setStudyDate(LocalDate.now());
        exam1.setVisitTime(LocalDateTime.now());
        exam1.setFinalDiagnosis("Osteoarthritis Grade 2 confirmed.");
        exam1 = examinationRepository.save(exam1);

        Image rawImage = new Image();
        rawImage.setFilePath("/images/report_raw.jpg");
        rawImage = imageRepository.save(rawImage);

        Image heatmapImage = new Image();
        heatmapImage.setFilePath("/images/report_heatmap.jpg");
        heatmapImage = imageRepository.save(heatmapImage);

        dicom1 = new DicomInstance();
        dicom1.setExamination(exam1);
        dicom1.setSopInstanceUid("1.2.3.4.5.6.7");
        dicom1.setStatus(DicomInstanceStatus.GET_RESULTED);
        dicom1.setImage(rawImage);
        dicom1 = dicomInstanceRepository.save(dicom1);

        aiAnalysis1 = new AiAnalysis();
        aiAnalysis1.setDicomInstance(dicom1);
        aiAnalysis1.setStartTime(LocalDateTime.now());
        aiAnalysis1.setDuration(120L);
        aiAnalysis1.setStatus("SUCCESS");
        aiAnalysis1 = aiAnalysisRepository.save(aiAnalysis1);

        aiResult1 = new AiResult();
        aiResult1.setAiAnalysis(aiAnalysis1);
        aiResult1.setPredictedGrade(2);
        aiResult1.setConfidence(0.92);
        aiResult1.setGradcamImage(heatmapImage);
        aiResult1.setKneeSide("RIGHT");
        aiResult1.setDescription("Joint space narrowing observed.");
        aiResult1 = aiResultRepository.save(aiResult1);

        // Link bi-directional collections explicitly to prevent cache gaps
        aiAnalysis1.setAiResults(new ArrayList<>(List.of(aiResult1)));
        aiAnalysisRepository.save(aiAnalysis1);

        dicom1.setAiAnalysis(aiAnalysis1);
        dicomInstanceRepository.save(dicom1);

        review1 = new DiagnosisReview();
        review1.setExamination(exam1);
        review1.setDoctor(assignedDoctor);
        review1.setAiResult(aiResult1);
        review1.setConfirmedKlGrade(2);
        review1.setDecision(DiagnosisReviewDecision.AI_CONFIRMED);
        review1.setReviewNote("AI result confirmed");
        review1.setReviewedAt(LocalDateTime.now());
        review1 = diagnosisReviewRepository.save(review1);

        aiResult1.setDiagnosisReview(review1);
        aiResultRepository.save(aiResult1);

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

    private void registerGeneratedReportFileForCleanup(String fileName) {
        Path reportPath = Paths.get(exportDir, fileName).toAbsolutePath().normalize();
        tempFilesCreated.add(reportPath);
    }

    //Export Pdf report integration tests

    @Test
    void testGeneratePdfReport_Success() throws Exception {
        // Post generate-report for verified examination
        MvcResult result = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", notNullValue()))
                .andExpect(jsonPath("$.examinationId", is(exam1.getId().intValue())))
                .andExpect(jsonPath("$.fileName", containsString("report_ENC-REPORT-01_")))
                .andExpect(jsonPath("$.previewUrl", containsString("/preview")))
                .andExpect(jsonPath("$.downloadUrl", containsString("/download")))
                .andReturn();

        ReportResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(response.fileName());

        // Verify PDF report file physically exists on disk and is non-empty
        Path pdfPath = Paths.get(exportDir, response.fileName()).toAbsolutePath().normalize();
        assertTrue(Files.exists(pdfPath));
        assertTrue(Files.size(pdfPath) > 0);

        // Verify database state updated
        Examination dbExam = examinationRepository.findById(exam1.getId()).orElseThrow();
        assertEquals(ExaminationStatus.REPORT_GENERATED, dbExam.getStatus());
    }

    @Test
    void testGeneratePdfReport_RepeatedRequest_ReturnsCachedReport() throws Exception {
        // First generation
        MvcResult result1 = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse response1 = objectMapper.readValue(result1.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(response1.fileName());

        // Second generation should return the cached report because the status is REPORT_GENERATED
        MvcResult result2 = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", is(response1.reportId().intValue())))
                .andExpect(jsonPath("$.fileName", is(response1.fileName())))
                .andReturn();
    }

    @Test
    void testGeneratePdfReport_ExaminationNotVerified_ThrowsException() throws Exception {
        //Change examination status back to NEED_VERIFY
        Examination testExam = examinationRepository.findById(exam1.getId()).orElseThrow();
        testExam.setStatus(ExaminationStatus.NEED_VERIFY);
        examinationRepository.save(testExam);

        entityManager.flush();
        entityManager.clear();

        // Request generate-report on unverified exam should return 400 Bad Request
        mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGeneratePdfReport_AccessDenied() throws Exception {
        // Unassigned doctor cannot generate report for exam assigned to another doctor
        mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + unassignedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGeneratePdfReport_HodDoctor_Success() throws Exception {
        // HOD Doctor can generate report for any doctor's exam (bypasses assignment check)
        MvcResult result = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + hodDoctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId", notNullValue()))
                .andReturn();

        ReportResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(response.fileName());
    }

    @Test
    void testGeneratePdfReport_NoPerms_Forbidden() throws Exception {
        // Doctor without GENERATE_PDF_REPORT authority is rejected
        mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + doctorNoPermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreviewReport_Success() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // Request preview
        mockMvc.perform(get("/reports/" + report.examinationId() + "/preview")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", is("application/pdf")))
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void testPreviewReport_AccessDenied() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // Unassigned doctor is blocked from previewing
        mockMvc.perform(get("/reports/" + report.examinationId() + "/preview")
                        .header("Authorization", "Bearer " + unassignedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPreviewReport_FileNotFound_Returns500() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);

        // Delete the PDF report file on disk to simulate file missing
        Path pdfPath = Paths.get(exportDir, report.fileName()).toAbsolutePath().normalize();
        Files.deleteIfExists(pdfPath);

        // Preview should return 500 since file is missing from storage
        mockMvc.perform(get("/reports/" + report.examinationId() + "/preview")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testDownloadReport_Success() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // Request download
        mockMvc.perform(get("/reports/" + report.examinationId() + "/download")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", is("application/pdf")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(report.fileName())));
    }

    @Test
    void testDownloadReport_AccessDenied() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // Unassigned doctor cannot download report
        mockMvc.perform(get("/reports/" + report.examinationId() + "/download")
                        .header("Authorization", "Bearer " + unassignedDoctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDownloadReport_NoPerms_Forbidden() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // Doctor with no authorities is forbidden from downloading
        mockMvc.perform(get("/reports/" + report.examinationId() + "/download")
                        .header("Authorization", "Bearer " + doctorNoPermsToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDownloadReport_HodDoctor_Success() throws Exception {
        // First generate report
        MvcResult genResult = mockMvc.perform(post("/examinations/" + exam1.getId() + "/generate-report")
                        .header("Authorization", "Bearer " + assignedDoctorToken))
                .andExpect(status().isOk())
                .andReturn();

        ReportResponse report = objectMapper.readValue(genResult.getResponse().getContentAsString(), ReportResponse.class);
        registerGeneratedReportFileForCleanup(report.fileName());

        // HOD Doctor can download any report
        mockMvc.perform(get("/reports/" + report.examinationId() + "/download")
                        .header("Authorization", "Bearer " + hodDoctorToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }
}
