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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    private final String VALID_BASE64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    private final String CORRUPTED_BASE64 = "data:image/png;base64,iVBO%%%RNOT_BASE64!!!!";

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("test_storage");
        ReflectionTestUtils.setField(aiService, "storageBaseDir", tempDir.toString());
        ReflectionTestUtils.setField(aiService, "aiApiUrl", "http://localhost:8000/predict");
    }

    private void setupCommonRepositoryMocks() {
        lenient().when(dicomInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(examinationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(aiAnalysisRepository.save(any())).thenAnswer(i -> {
            AiAnalysis a = i.getArgument(0);
            if (a.getDicomInstance() != null) {
                a.getDicomInstance().setAiAnalysis(a);
            }
            if (a.getId() == null)
                a.setId(new Random().nextLong());
            return a;
        });
        lenient().when(aiResultRepository.save(any())).thenAnswer(i -> {
            AiResult r = i.getArgument(0);
            if (r.getId() == null)
                r.setId(new Random().nextLong());
            return r;
        });
        lenient().when(imageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private DicomInstance createMockInstance(Long id, DicomInstanceStatus status, String filePath) throws Exception {
        DicomInstance instance = new DicomInstance();
        instance.setId(id);
        instance.setStatus(status);
        if (filePath != null) {
            Image image = new Image();
            image.setFilePath(filePath);
            instance.setImage(image);

            File imgFile = tempDir.resolve(filePath).toFile();
            imgFile.getParentFile().mkdirs();
            Files.write(imgFile.toPath(), "dummy image data".getBytes());
        }

        Examination exam = new Examination();
        exam.setId(id * 10);
        Doctor doctor = new Doctor();
        doctor.setId(100L);
        doctor.setFullName("Bác sĩ Lê Đại Cương");
        exam.setDoctor(doctor);
        Patient patient = new Patient();
        patient.setId(200L);
        patient.setFullName("Bệnh nhân Nguyễn Văn An");
        exam.setPatient(patient);
        instance.setExamination(exam);

        return instance;
    }

    private FastApiPredictionResponse createFakeResponse(Integer predictedClass, String predictedGrade, String base64) {
        FastApiPredictionResponse fakeResponse = new FastApiPredictionResponse();
        FastApiPredictionResponse.AiPredictionData pData = new FastApiPredictionResponse.AiPredictionData();
        pData.setPredictedClass(predictedClass);
        pData.setPredictedGrade(predictedGrade);
        pData.setConfidence(0.95);
        pData.setDescription("Mo ta test");
        pData.setKneeSide("LEFT");
        Map<String, Double> details = new HashMap<>();
        details.put("4Severe", 0.95);
        pData.setDetails(details);
        pData.setRoiImage(base64);
        pData.setGradcamImage(base64);

        fakeResponse.setPredictions(Arrays.asList(pData));
        fakeResponse.setAnnotatedImage(base64);
        return fakeResponse;
    }

    // ==============================================================================
    // UTCID01: 1 Valid ID, DB OK, API 200 OK, Valid Predictions
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID01_Normal() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));

        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto = new ExaminationImageDto();
        imgDto.setDicomInstanceId(101L);
        examDto.setImages(Arrays.asList(imgDto));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse = createFakeResponse(4, "KL4_Severe", VALID_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {

            List<ExaminationDto> result = aiService.predictBatch(request);

            assertFalse(result.isEmpty());
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance.getStatus());
            assertEquals(4, instance.getExamination().getMaxPredictedGrade());
            verify(notificationService, times(1)).sendNotification(any());
        }
    }

    // ==============================================================================
    // UTCID02: Multiple Valid IDs
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID02_MultipleValidInstances() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L, 102L));

        DicomInstance instance1 = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        DicomInstance instance2 = createMockInstance(102L, DicomInstanceStatus.AI_SENDING, "valid_102.png");
        instance2.setExamination(instance1.getExamination());

        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance1));
        when(dicomInstanceRepository.findById(102L)).thenReturn(Optional.of(instance2));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto1 = new ExaminationImageDto();
        imgDto1.setDicomInstanceId(101L);
        ExaminationImageDto imgDto2 = new ExaminationImageDto();
        imgDto2.setDicomInstanceId(102L);
        examDto.setImages(Arrays.asList(imgDto1, imgDto2));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse1 = createFakeResponse(3, "KL3_Moderate", VALID_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse1, HttpStatus.OK));
                })) {

            List<ExaminationDto> result = aiService.predictBatch(request);

            assertFalse(result.isEmpty());
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance1.getStatus());
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance2.getStatus());
            assertEquals(3, instance1.getExamination().getMaxPredictedGrade());
            verify(notificationService, times(1)).sendNotification(any());
        }
    }

    // ==============================================================================
    // UTCID03: Instance Not Found
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID03_InstanceNotFound() {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(999L));
        when(dicomInstanceRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertTrue(exception.getMessage().contains("DicomInstance not found"));
    }

    // ==============================================================================
    // UTCID04: Status != AI_SENDING
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID04_StatusNotSending() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.GET_RESULTED, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        List<ExaminationDto> result = aiService.predictBatch(request);
        assertTrue(result.isEmpty());
        verify(dicomInstanceRepository, never()).save(any());
    }

    // ==============================================================================
    // UTCID05: Image Path NULL
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID05_ImagePathNull() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, null);
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertTrue(exception.getMessage().contains("Image/PNG path is NULL"));
    }

    // ==============================================================================
    // UTCID06: Image File Missing on Disk
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID06_FileNotExist() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "missing_101.png");
        tempDir.resolve("missing_101.png").toFile().delete();

        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> aiService.predictBatch(request));
        assertTrue(exception.getMessage().contains("Image file does not exist on disk"));
    }

    // ==============================================================================
    // UTCID07: API HttpStatusCodeException
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID07_ApiHttpException() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(new ExaminationDto());

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));
                })) {

            aiService.predictBatch(request);
            assertEquals(DicomInstanceStatus.AI_FAILED, instance.getStatus());
            assertEquals(ExaminationStatus.AI_FAILED, instance.getExamination().getStatus());
        }
    }

    // ==============================================================================
    // UTCID08: Generic Connection Exception
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID08_ApiConnectionException() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(new ExaminationDto());

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenThrow(new ResourceAccessException("Connection refused"));
                })) {

            aiService.predictBatch(request);
            assertEquals(DicomInstanceStatus.AI_FAILED, instance.getStatus());
            assertNotNull(instance.getAiAnalysis());
            assertEquals("Connection refused", instance.getAiAnalysis().getErrorMessage());
        }
    }

    // ==============================================================================
    // UTCID09: Error msg length > 500
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID09_ApiErrorMsgTooLong() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(new ExaminationDto());

        String longMsg = "X".repeat(600);
        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenThrow(new ResourceAccessException(longMsg));
                })) {

            aiService.predictBatch(request);
            assertNotNull(instance.getAiAnalysis());
            assertTrue(instance.getAiAnalysis().getErrorMessage().length() <= 500);
            assertTrue(instance.getAiAnalysis().getErrorMessage().endsWith("..."));
        }
    }

    // ==============================================================================
    // UTCID10: Predictions null/empty
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID10_PredictionsNullOrEmpty() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(new ExaminationDto());

        FastApiPredictionResponse fakeResponse = new FastApiPredictionResponse();
        fakeResponse.setPredictions(null);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {

            aiService.predictBatch(request);
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance.getStatus());
            assertNotNull(instance.getAiAnalysis());
            assertEquals("SUCCESS", instance.getAiAnalysis().getStatus());
        }
    }

    // ==============================================================================
    // UTCID11: String Extraction Fallback
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID11_StringExtractionFallback() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto = new ExaminationImageDto();
        imgDto.setDicomInstanceId(101L);
        examDto.setImages(Arrays.asList(imgDto));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse = createFakeResponse(null, "KL3_Moderate", VALID_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {

            aiService.predictBatch(request);
            assertEquals(3, instance.getExamination().getMaxPredictedGrade());
        }
    }

    // ==============================================================================
    // UTCID12: String Invalid Fallback (0)
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID12_StringInvalidFallback() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto = new ExaminationImageDto();
        imgDto.setDicomInstanceId(101L);
        examDto.setImages(Arrays.asList(imgDto));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse = createFakeResponse(null, "Unknown_Grade", VALID_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {

            aiService.predictBatch(request);
            assertEquals(0, instance.getExamination().getMaxPredictedGrade());
        }
    }

    // ==============================================================================
    // UTCID13: Base64 Corrupted
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID13_Base64Corrupted() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L));
        DicomInstance instance = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto = new ExaminationImageDto();
        imgDto.setDicomInstanceId(101L);
        examDto.setImages(Arrays.asList(imgDto));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse = createFakeResponse(2, "KL2", CORRUPTED_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));
                })) {

            aiService.predictBatch(request);
            assertEquals(DicomInstanceStatus.GET_RESULTED, instance.getStatus());
        }
    }

    // ==============================================================================
    // UTCID14: Partial Success
    // ==============================================================================
    @Test
    void testPredictBatch_UTCID14_PartialSuccess() throws Exception {
        setupCommonRepositoryMocks();
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(101L, 102L));

        DicomInstance instance1 = createMockInstance(101L, DicomInstanceStatus.AI_SENDING, "valid_101.png");
        DicomInstance instance2 = createMockInstance(102L, DicomInstanceStatus.AI_SENDING, "valid_102.png");
        instance2.setExamination(instance1.getExamination());

        when(dicomInstanceRepository.findById(101L)).thenReturn(Optional.of(instance1));
        when(dicomInstanceRepository.findById(102L)).thenReturn(Optional.of(instance2));

        ExaminationDto examDto = new ExaminationDto();
        ExaminationImageDto imgDto1 = new ExaminationImageDto();
        imgDto1.setDicomInstanceId(101L);
        ExaminationImageDto imgDto2 = new ExaminationImageDto();
        imgDto2.setDicomInstanceId(102L);
        examDto.setImages(Arrays.asList(imgDto1, imgDto2));
        when(examinationMapper.toDto(any(), anyList())).thenReturn(examDto);

        FastApiPredictionResponse fakeResponse = createFakeResponse(3, "KL3", VALID_BASE64);

        try (MockedConstruction<RestTemplate> mocked = Mockito.mockConstruction(RestTemplate.class,
                (mock, context) -> {
                    when(mock.postForEntity(anyString(), any(), eq(FastApiPredictionResponse.class)))
                            .thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK))
                            .thenThrow(new ResourceAccessException("Connection refused"));
                })) {

            aiService.predictBatch(request);

            assertEquals(DicomInstanceStatus.GET_RESULTED, instance1.getStatus());
            assertEquals(DicomInstanceStatus.AI_FAILED, instance2.getStatus());
            assertEquals(ExaminationStatus.NEED_VERIFY, instance1.getExamination().getStatus());
        }
    }
}
