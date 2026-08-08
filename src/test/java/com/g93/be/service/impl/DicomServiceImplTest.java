package com.g93.be.service.impl;

import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.dto.DicomTagResponse;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.Image;
import com.g93.be.entity.DicomRaw;
import com.g93.be.entity.User;
import com.g93.be.repository.AuditLogRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DicomServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private NotificationService notificationService;

    @Spy
    @InjectMocks
    private DicomServiceImpl dicomService;

    private Path tempStorageDir;

    @BeforeEach
    void setUp() throws IOException {
        tempStorageDir = Files.createTempDirectory("dicom_storage");
        ReflectionTestUtils.setField(dicomService, "storageBaseDir", tempStorageDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempStorageDir)
                .sorted((a, b) -> b.compareTo(a))
                .map(Path::toFile)
                .forEach(java.io.File::delete);
    }

    // ==========================================
    // Phase 1 Tests
    // ==========================================

    @Test
    void testGetUploadSession_Normal_Exists() {
        // Arrange
        String sessionId = "sess-123";
        String expectedStatus = "{\"status\":\"PROCESSING\"}";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("uploadSession:" + sessionId)).thenReturn(expectedStatus);

        // Act
        String result = dicomService.getUploadSession(sessionId);

        // Assert
        assertEquals(expectedStatus, result);
        verify(valueOperations).get("uploadSession:" + sessionId);
    }

    @Test
    void testGetUploadSession_Abnormal_NotFound() {
        // Arrange
        String sessionId = "sess-404";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("uploadSession:" + sessionId)).thenReturn(null);

        // Act
        String result = dicomService.getUploadSession(sessionId);

        // Assert
        assertNull(result);
    }

    @Test
    void testGetInstanceImageResource_Normal_Readable() throws IOException {
        // Arrange
        Long id = 1L;
        DicomInstance instance = new DicomInstance();
        Image image = new Image();
        
        // Create a real temp file
        Path imageFile = tempStorageDir.resolve("test_image.jpg");
        Files.writeString(imageFile, "fake-image-content");
        
        image.setFilePath("/test_image.jpg"); // starts with slash to test substring logic
        instance.setImage(image);

        when(dicomInstanceRepository.findById(id)).thenReturn(Optional.of(instance));

        // Act
        Resource resource = dicomService.getInstanceImageResource(id);

        // Assert
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void testGetInstanceImageResource_Abnormal_NotFound() {
        // Arrange
        Long id = 2L;
        when(dicomInstanceRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        Resource resource = dicomService.getInstanceImageResource(id);

        // Assert
        assertNull(resource);
    }

    @Test
    void testGetInstanceImageResource_Abnormal_FileMissing() {
        // Arrange
        Long id = 3L;
        DicomInstance instance = new DicomInstance();
        Image image = new Image();
        image.setFilePath("missing_image.jpg"); // File does not exist
        instance.setImage(image);

        when(dicomInstanceRepository.findById(id)).thenReturn(Optional.of(instance));

        // Act
        Resource resource = dicomService.getInstanceImageResource(id);

        // Assert
        // URLResource existence check happens inside the method
        assertNull(resource);
    }

    @Test
    void testGetInstanceRawResource_Normal_Readable() throws IOException {
        // Arrange
        Long id = 1L;
        DicomInstance instance = new DicomInstance();
        DicomRaw raw = new DicomRaw();
        
        Path rawFile = tempStorageDir.resolve("test_raw.dcm");
        Files.writeString(rawFile, "fake-raw-content");
        
        raw.setFilePath("test_raw.dcm");
        instance.setDicomRaw(raw);

        when(dicomInstanceRepository.findById(id)).thenReturn(Optional.of(instance));

        // Act
        Resource resource = dicomService.getInstanceRawResource(id);

        // Assert
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    void testGetInstanceRawResource_Abnormal_NotFound() {
        // Arrange
        Long id = 2L;
        when(dicomInstanceRepository.findById(id)).thenReturn(Optional.empty());

        // Act
        Resource resource = dicomService.getInstanceRawResource(id);

        // Assert
        assertNull(resource);
    }

    @Test
    void testExtractMetadata_Normal() {
        // Currently extractMetadata returns empty list as stub
        MultipartFile file = mock(MultipartFile.class);
        List<DicomTagResponse> result = dicomService.extractMetadata(file);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUploadBatchFiles_Normal() {
        // Arrange
        String username = "doctor1";
        User user = new User();
        user.setId(10L);
        user.setUsername(username);
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        
        List<MultipartFile> files = new ArrayList<>();
        files.add(mock(MultipartFile.class));

        BatchDicomUploadResponse mockResponse = new BatchDicomUploadResponse();
        mockResponse.setMessage("Success");
        
        // Mock the uploadBatch call to avoid executing real logic
        doReturn(mockResponse).when(dicomService).uploadBatch(files, 10L);

        // Act
        BatchDicomUploadResponse result = dicomService.uploadBatchFiles(files, username);

        // Assert
        assertNotNull(result);
        assertEquals("Success", result.getMessage());
        verify(dicomService).uploadBatch(files, 10L);
    }

    @Test
    void testUploadBatchFiles_Abnormal_EmptyFiles() {
        // Arrange
        List<MultipartFile> files = new ArrayList<>();
        String username = "doctor1";

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            dicomService.uploadBatchFiles(files, username);
        });
        assertEquals("Uploaded files list is empty", ex.getMessage());
    }
    
    @Test
    void testUploadBatchFiles_Abnormal_UserNotFound_Fallback() {
        // Arrange
        String username = "unknown_doc";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        
        List<MultipartFile> files = new ArrayList<>();
        files.add(mock(MultipartFile.class));

        BatchDicomUploadResponse mockResponse = new BatchDicomUploadResponse();
        
        // Mock the uploadBatch call. Since user not found, it falls back to 1L
        doReturn(mockResponse).when(dicomService).uploadBatch(files, 1L);

        // Act
        BatchDicomUploadResponse result = dicomService.uploadBatchFiles(files, username);

        // Assert
        assertNotNull(result);
        verify(dicomService).uploadBatch(files, 1L);
    }

    // ==========================================
    // Phase 2 Tests
    // ==========================================

    @Test
    void testUploadBatch_Normal() throws Exception {
        // Arrange
        Long userId = 1L;
        List<MultipartFile> files = new ArrayList<>();
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("valid.dcm");
        when(mockFile.getSize()).thenReturn(1024L);
        files.add(mockFile);

        // Mock processBatchPaths
        BatchDicomUploadResponse mockResponse = new BatchDicomUploadResponse();
        mockResponse.setErrors(new ArrayList<>());
        mockResponse.setSuccessfulPatients(new ArrayList<>());
        mockResponse.getSuccessfulPatients().add(new com.g93.be.dto.PatientDetailsResponse()); // to pass empty check
        doReturn(mockResponse).when(dicomService).processBatchPaths(any(), eq(userId), anyString());

        // Mock isDicomFile (it uses DicomInputStream, but we can spy it)
        doReturn(true).when(dicomService).isDicomFile(any(Path.class));
        
        // Mock auditLog dependencies
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        // Act
        BatchDicomUploadResponse result = dicomService.uploadBatch(files, userId);

        // Assert
        assertNotNull(result);
        verify(dicomService).processBatchPaths(anyMap(), eq(userId), anyString());
        verify(auditLogRepository).save(any());
    }

    @Test
    void testUploadBatch_Abnormal_InvalidExtension() throws Exception {
        // Arrange
        Long userId = 1L;
        List<MultipartFile> files = new ArrayList<>();
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("invalid.txt");
        when(mockFile.getSize()).thenReturn(1024L);
        files.add(mockFile);

        // Mock processBatchPaths
        BatchDicomUploadResponse mockResponse = new BatchDicomUploadResponse();
        mockResponse.setErrors(new ArrayList<>());
        mockResponse.setSuccessfulPatients(new ArrayList<>());
        doReturn(mockResponse).when(dicomService).processBatchPaths(any(), eq(userId), anyString());

        // Mock auditLog dependencies
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        // Act
        BatchDicomUploadResponse result = dicomService.uploadBatch(files, userId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getErrors().size());
        assertEquals("invalid.txt", result.getErrors().get(0).getFilename());
        verify(dicomService).processBatchPaths(anyMap(), eq(userId), anyString());
    }

    @Test
    void testUploadBatch_Abnormal_ExceptionInProcessing() throws Exception {
        // Arrange
        Long userId = 1L;
        List<MultipartFile> files = new ArrayList<>();
        
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("valid.dcm");
        when(mockFile.getSize()).thenReturn(1024L);
        files.add(mockFile);

        doReturn(true).when(dicomService).isDicomFile(any(Path.class));
        
        // Throw exception
        doThrow(new RuntimeException("DB Error")).when(dicomService).processBatchPaths(any(), eq(userId), anyString());

        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            dicomService.uploadBatch(files, userId);
        });
        
        assertTrue(ex.getMessage().contains("Failed to process uploaded batch files"));
        // verify audit log for failure is called
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    void testUploadZipBatchFiles_Normal() throws Exception {
        // Arrange
        String username = "doctor1";
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        
        List<MultipartFile> files = new ArrayList<>();
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("valid.zip");
        when(mockFile.getSize()).thenReturn(2048L);
        files.add(mockFile);

        doReturn(true).when(dicomService).isZipFile(any(Path.class));
        
        BatchDicomUploadResponse mockResponse = new BatchDicomUploadResponse();
        mockResponse.setErrors(new ArrayList<>());
        mockResponse.setSuccessfulPatients(new ArrayList<>());
        mockResponse.getSuccessfulPatients().add(new com.g93.be.dto.PatientDetailsResponse()); // to pass empty check
        
        doReturn(mockResponse).when(dicomService).processMultipleZipBatches(anyList(), eq(userId), anyString());
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        BatchDicomUploadResponse result = dicomService.uploadZipBatchFiles(files, username);

        // Assert
        assertNotNull(result);
        verify(dicomService).processMultipleZipBatches(anyList(), eq(userId), anyString());
    }

    @Test
    void testUploadZipBatchFiles_Abnormal_EmptyFiles() {
        // Arrange
        String username = "doctor1";
        List<MultipartFile> files = new ArrayList<>();

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            dicomService.uploadZipBatchFiles(files, username);
        });
        assertEquals("Uploaded files are empty", ex.getMessage());
    }
}
