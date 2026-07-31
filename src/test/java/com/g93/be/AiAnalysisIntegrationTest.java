package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.AiPredictionRequest;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class AiAnalysisIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Role adminRole;
    private User adminUser;
    private String adminToken;

    private Patient defaultPatient;
    private Examination defaultExamination;

    private List<Path> tempFilesCreated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        patientRepository.deleteAll();

        adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        adminUser = new User();
        adminUser.setUsername("ai_admin");
        adminUser.setPassword(passwordEncoder.encode("admin_password"));
        adminUser.setFullName("AI Admin");
        adminUser.setEmail("ai_admin@hospital.com");
        adminUser.setPhone("0123456780");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        userRepository.save(adminUser);

        adminToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(adminUser, new ArrayList<>()));

        // Create a default patient
        defaultPatient = new Patient();
        defaultPatient.setFullName("Test Patient");
        defaultPatient.setPatientCode("PAT-TEST-AI");
        defaultPatient.setGender(Gender.MALE);
        defaultPatient = patientRepository.save(defaultPatient);

        // Create a default examination
        defaultExamination = new Examination();
        defaultExamination.setPatient(defaultPatient);
        defaultExamination.setEncounterCode("ENC-TEST-AI");
        defaultExamination.setStatus(ExaminationStatus.AI_PROCESSING);
        defaultExamination.setStudyDate(java.time.LocalDate.now());
        defaultExamination.setVisitTime(java.time.LocalDateTime.now());
        defaultExamination = examinationRepository.save(defaultExamination);
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

    private Path createTempStorageFile(String relativePathStr, String content) throws Exception {
        Path fullPath = Paths.get(storageBaseDir, relativePathStr);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, content.getBytes());
        tempFilesCreated.add(fullPath);
        return fullPath;
    }

    @Test
    void testPredictBatch_Skipped_NotAiSending() throws Exception {
        // Create valid instance with status GET_RESULTED (not AI_SENDING)
        DicomInstance instance = new DicomInstance();
        instance.setSopInstanceUid("1.2.3.4.99.1");
        instance.setStatus(DicomInstanceStatus.GET_RESULTED); 
        instance.setExamination(defaultExamination);
        dicomInstanceRepository.save(instance);

        AiPredictionRequest request = new AiPredictionRequest(Collections.singletonList(instance.getId()));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()); // Skips successfully, returning 200
    }

    @Test
    void testPredictBatch_Failure_InstanceNotFound() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest(Collections.singletonList(99999L));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError()); // Should throw runtime exception and map to 500
    }

    @Test
    void testPredictBatch_Failure_ImageFileMissing() throws Exception {
        Image image = new Image();
        image.setFilePath("/images/nonexistent.png");
        image = imageRepository.save(image);

        DicomInstance instance = new DicomInstance();
        instance.setSopInstanceUid("1.2.3.4.99.2");
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        instance.setImage(image);
        instance.setExamination(defaultExamination);
        dicomInstanceRepository.save(instance);

        AiPredictionRequest request = new AiPredictionRequest(Collections.singletonList(instance.getId()));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testPredictBatch_Failure_ConnectionRefused() throws Exception {
        // Create an image pointing to a real file path but connection to API will fail
        String relativePngPath = "images/test_ai_refused.png";
        createTempStorageFile(relativePngPath, "dummy png bytes");

        Image image = new Image();
        image.setFilePath("/" + relativePngPath);
        image = imageRepository.save(image);

        DicomInstance instance = new DicomInstance();
        instance.setSopInstanceUid("1.2.3.4.99.3");
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        instance.setImage(image);
        instance.setExamination(defaultExamination);
        dicomInstanceRepository.save(instance);

        AiPredictionRequest request = new AiPredictionRequest(Collections.singletonList(instance.getId()));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError()); // Fails with Connection Refused / RestClientException
    }

    @Test
    void testGetHeatmapImage_NotFound() throws Exception {
        mockMvc.perform(get("/ai/heatmap/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Success() throws Exception {
        String relativeHeatmapPath = "heatmap/test_heatmap.jpg";
        createTempStorageFile(relativeHeatmapPath, "dummy heatmap image bytes");

        DicomInstance instance = new DicomInstance();
        instance.setSopInstanceUid("1.2.3.4.99.10");
        instance.setStatus(DicomInstanceStatus.GET_RESULTED);
        instance.setExamination(defaultExamination);
        instance = dicomInstanceRepository.save(instance);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setDicomInstance(instance);
        analysis.setStatus("SUCCESS");
        analysis.setStartTime(java.time.LocalDateTime.now());
        analysis.setDuration(100L);
        analysis = aiAnalysisRepository.save(analysis);

        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("/" + relativeHeatmapPath);
        result.setPredictedGrade(1);
        result.setConfidence(0.85);
        result.setAiAnalysis(analysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testGetImage_NotFound() throws Exception {
        mockMvc.perform(get("/ai/image/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetImage_Success() throws Exception {
        String relativeImagePath = "images/test_get_image.jpg";
        createTempStorageFile(relativeImagePath, "dummy image bytes");

        Image image = new Image();
        image.setFilePath("/" + relativeImagePath);
        image = imageRepository.save(image);

        mockMvc.perform(get("/ai/image/" + image.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
