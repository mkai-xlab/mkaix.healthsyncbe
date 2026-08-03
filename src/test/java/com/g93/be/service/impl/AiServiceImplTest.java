package com.g93.be.service.impl;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.ExaminationImageDto;
import com.g93.be.dto.FastApiPredictionResponse;
import com.g93.be.entity.*;
import com.g93.be.mapper.ExaminationMapper;
import com.g93.be.repository.*;
import com.g93.be.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private ExaminationRepository examinationRepository;
    @Mock
    private AiAnalysisRepository aiAnalysisRepository;
    @Mock
    private AiResultRepository aiResultRepository;
    @Mock
    private AiResultConfidenceScoreRepository aiResultConfidenceScoreRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private ExaminationMapper examinationMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AiServiceImpl aiService;

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test_storage");
        ReflectionTestUtils.setField(aiService, "storageBaseDir", tempDir.toString());
        ReflectionTestUtils.setField(aiService, "aiApiUrl", "http://localhost:8000/predict");
    }

    @Test
    void testPredictBatch_Normal() throws Exception {
        // Arrange
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        DicomInstance instance = new DicomInstance();
        instance.setId(1L);
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        
        Image image = new Image();
        image.setFilePath("test_image.png");
        instance.setImage(image);

        Examination exam = new Examination();
        exam.setId(10L);
        Doctor doctor = new Doctor();
        doctor.setId(100L);
        exam.setDoctor(doctor);
        Patient patient = new Patient();
        patient.setId(200L);
        exam.setPatient(patient);
        instance.setExamination(exam);

        File imageFile = tempDir.resolve("test_image.png").toFile();
        Files.write(imageFile.toPath(), "dummy image data".getBytes());

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        when(dicomInstanceRepository.save(any(DicomInstance.class))).thenReturn(instance);
        when(examinationRepository.save(any(Examination.class))).thenReturn(exam);
        when(aiAnalysisRepository.save(any(AiAnalysis.class))).thenAnswer(i -> {
            AiAnalysis a = i.getArgument(0);
            a.setId(5L);
            return a;
        });
        when(aiResultRepository.save(any(AiResult.class))).thenAnswer(i -> {
            AiResult r = i.getArgument(0);
            r.setId(7L);
            return r;
        });
        when(imageRepository.save(any(Image.class))).thenAnswer(i -> i.getArgument(0));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto = new ExaminationImageDto();
        imgDto.setDicomInstanceId(1L);
        examDto.setImages(Arrays.asList(imgDto));
        when(examinationMapper.toDto(any(Examination.class), anyList())).thenReturn(examDto);

        // Fake AI Response
        FastApiPredictionResponse fakeResponse = new FastApiPredictionResponse();
        FastApiPredictionResponse.AiPredictionData pData = new FastApiPredictionResponse.AiPredictionData();
        pData.setPredictedClass(4);
        pData.setConfidence(0.95);
        pData.setDescription("Severe OA");
        pData.setKneeSide("LEFT");
        Map<String, Double> details = new HashMap<>();
        details.put("4Severe", 0.95);
        pData.setDetails(details);
        // Add fake base64 images to trigger the decoding logic
        // A minimal valid base64 image (1x1 pixel)
        String fakeBase64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
        pData.setRoiImage(fakeBase64);
        pData.setGradcamImage(fakeBase64);
        
        fakeResponse.setPredictions(Arrays.asList(pData));
        fakeResponse.setAnnotatedImage(fakeBase64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {
            
            // Act
            List<ExaminationDto> result = aiService.predictBatch(request);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance.getStatus());
            assertEquals(ExaminationStatus.NEED_VERIFY, exam.getStatus());
            assertEquals(4, exam.getMaxPredictedGrade());
            
            verify(notificationService, times(1)).sendNotification(any());
            verify(aiAnalysisRepository, times(1)).save(any(AiAnalysis.class));
            verify(aiResultRepository, times(1)).save(any(AiResult.class));
            verify(imageRepository, atLeastOnce()).save(any(Image.class));
        }
    }

    @Test
    void testPredictBatch_InstanceNotFound() {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertEquals("DicomInstance not found for ID: 1", exception.getMessage());
    }

    @Test
    void testPredictBatch_StatusNotSending() {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        DicomInstance instance = new DicomInstance();
        instance.setId(1L);
        instance.setStatus(DicomInstanceStatus.GET_RESULTED); // Not AI_SENDING

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));

        List<ExaminationDto> result = aiService.predictBatch(request);
        assertTrue(result.isEmpty());
        verify(dicomInstanceRepository, never()).save(any());
    }

    @Test
    void testPredictBatch_ImageNull() {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        DicomInstance instance = new DicomInstance();
        instance.setId(1L);
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        instance.setImage(null);

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertTrue(exception.getMessage().contains("Image/PNG path is NULL"));
    }

    @Test
    void testPredictBatch_FileNotExist() {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        DicomInstance instance = new DicomInstance();
        instance.setId(1L);
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        Image image = new Image();
        image.setFilePath("missing_image.png");
        instance.setImage(image);

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertTrue(exception.getMessage().contains("Image file does not exist on disk"));
    }

    @Test
    void testPredictBatch_ApiFailed() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L));

        DicomInstance instance = new DicomInstance();
        instance.setId(1L);
        instance.setStatus(DicomInstanceStatus.AI_SENDING);
        Image image = new Image();
        image.setFilePath("test_image2.png");
        instance.setImage(image);

        File imageFile = tempDir.resolve("test_image2.png").toFile();
        Files.write(imageFile.toPath(), "dummy".getBytes());

        when(dicomInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>((FastApiPredictionResponse) null, HttpStatus.INTERNAL_SERVER_ERROR));
                })) {
            
            // Should not throw exception anymore
            assertDoesNotThrow(() -> aiService.predictBatch(request));
            
            // Verify instance status updated to AI_FAILED
            assertEquals(DicomInstanceStatus.AI_FAILED, instance.getStatus());
            verify(dicomInstanceRepository, atLeastOnce()).save(instance);
        }
    }
}
