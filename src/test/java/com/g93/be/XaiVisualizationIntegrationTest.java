package com.g93.be;

import com.g93.be.entity.*;
import com.g93.be.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class XaiVisualizationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AiResultRepository aiResultRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExaminationRepository examinationRepository;

    @Autowired
    private DicomInstanceRepository dicomInstanceRepository;

    @Autowired
    private AiAnalysisRepository aiAnalysisRepository;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    private AiAnalysis defaultAiAnalysis;

    private List<Path> tempFilesCreated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        patientRepository.deleteAll();

        Patient patient = new Patient();
        patient.setFullName("XAI Patient");
        patient.setPatientCode("PAT-XAI");
        patient.setGender(Gender.FEMALE);
        patient = patientRepository.save(patient);

        Examination exam = new Examination();
        exam.setPatient(patient);
        exam.setEncounterCode("ENC-XAI");
        exam.setStatus(ExaminationStatus.AI_PROCESSING);
        exam.setStudyDate(java.time.LocalDate.now());
        exam.setVisitTime(java.time.LocalDateTime.now());
        exam = examinationRepository.save(exam);

        DicomInstance instance = new DicomInstance();
        instance.setSopInstanceUid("1.2.3.4.99.20");
        instance.setStatus(DicomInstanceStatus.GET_RESULTED);
        instance.setExamination(exam);
        instance = dicomInstanceRepository.save(instance);

        defaultAiAnalysis = new AiAnalysis();
        defaultAiAnalysis.setDicomInstance(instance);
        defaultAiAnalysis.setStatus("SUCCESS");
        defaultAiAnalysis.setStartTime(java.time.LocalDateTime.now());
        defaultAiAnalysis.setDuration(100L);
        defaultAiAnalysis = aiAnalysisRepository.save(defaultAiAnalysis);
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
    void testGetHeatmapImage_Success() throws Exception {
        String relativeHeatmapPath = "heatmap/xai_success.jpg";
        String expectedContent = "xai heatmap raw image bytes";
        createTempStorageFile(relativeHeatmapPath, expectedContent);

        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("/" + relativeHeatmapPath);
        result.setPredictedGrade(0);
        result.setConfidence(0.99);
        result.setAiAnalysis(defaultAiAnalysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().string(expectedContent));
    }

    @Test
    void testGetHeatmapImage_NotFound_ResultDoesNotExist() throws Exception {
        mockMvc.perform(get("/ai/heatmap/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Failure_FileDoesNotExistOnDisk() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("/heatmap/nonexistent_file_on_disk.jpg");
        result.setPredictedGrade(3);
        result.setConfidence(0.72);
        result.setAiAnalysis(defaultAiAnalysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Failure_NullFilePath() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath(null);
        result.setPredictedGrade(1);
        result.setConfidence(0.55);
        result.setAiAnalysis(defaultAiAnalysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Failure_EmptyFilePath() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("");
        result.setPredictedGrade(2);
        result.setConfidence(0.65);
        result.setAiAnalysis(defaultAiAnalysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void testGetHeatmapImage_Failure_InvalidPathTraversal() throws Exception {
        // Path traversal attempt in DB path
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("/../etc/passwd");
        result.setPredictedGrade(4);
        result.setConfidence(1.0);
        result.setAiAnalysis(defaultAiAnalysis);
        result = aiResultRepository.save(result);

        mockMvc.perform(get("/ai/heatmap/" + result.getId()))
                .andExpect(status().isNotFound());
    }
}
