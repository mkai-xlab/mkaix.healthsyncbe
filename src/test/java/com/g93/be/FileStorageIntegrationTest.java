package com.g93.be;

import com.g93.be.service.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
public class FileStorageIntegrationTest {

    @Autowired
    @Qualifier("localStorageService")
    private StorageService localStorageService;

    @Autowired
    @Qualifier("s3StorageServiceImpl")
    private StorageService s3StorageService;

    @MockitoBean
    private S3Client s3Client;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Value("${app.aws.s3.bucket:healthsync-bucket}")
    private String s3BucketName;

    private final List<Path> tempFilesCreated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clear mock invocations before each test
        reset(s3Client);
    }

    @AfterEach
    void tearDown() {
        // Cleanup physical files created during local storage testing
        for (Path path : tempFilesCreated) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testLocalStorage_UploadFile_Success() throws Exception {
        String folderName = "test/avatars";
        String fileName = "avatar1.png";
        byte[] fileContent = "dummy image content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", fileName, "image/png", fileContent);

        // Upload using local storage service
        String relativeUrl = localStorageService.uploadFile(folderName, fileName, file);

        // Check returned URL path
        assertEquals("/test/avatars/avatar1.png", relativeUrl);

        // Verify file is physically created on disk
        Path expectedFile = Paths.get(storageBaseDir, folderName, fileName).toAbsolutePath().normalize();
        tempFilesCreated.add(expectedFile);

        assertTrue(Files.exists(expectedFile));
        assertArrayEquals(fileContent, Files.readAllBytes(expectedFile));
    }

    @Test
    void testS3Storage_UploadFile_Success() throws Exception {
        String folderName = "dicom/scans";
        String fileName = "scan_001.dcm";
        byte[] fileContent = "mock dicom bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/dicom", fileContent);

        // Mock putObject call to return a mock response
        PutObjectResponse mockPutResponse = PutObjectResponse.builder().build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(mockPutResponse);

        String uploadResult = s3StorageService.uploadFile(folderName, fileName, file);

        assertTrue(uploadResult.contains("Successfully uploaded to S3: s3://"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(s3BucketName, capturedRequest.bucket());
        assertEquals("dicom/scans/scan_001.dcm", capturedRequest.key());
        assertEquals("application/dicom", capturedRequest.contentType());
    }
)


    private RestoreObjectResponse initiateS3ObjectRestoration(String bucket, String key, int days, Tier tier) {
        RestoreRequest restoreRequest = RestoreRequest.builder()
                .days(days)
                .glacierJobParameters(GlacierJobParameters.builder().tier(tier).build())
                .build();

        RestoreObjectRequest request = RestoreObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .restoreRequest(restoreRequest)
                .build();

        return s3Client.restoreObject(request);
    }

    @Test
    void testRestoreArchivedData_Success() {
        String objectKey = "archive/patient_scans/2023/scan_102.dcm";
        RestoreObjectResponse mockResponse = RestoreObjectResponse.builder().build();

        when(s3Client.restoreObject(any(RestoreObjectRequest.class)))
                .thenReturn(mockResponse);
        RestoreObjectResponse response = initiateS3ObjectRestoration(s3BucketName, objectKey, 7, Tier.STANDARD);

        assertNotNull(response);

        ArgumentCaptor<RestoreObjectRequest> requestCaptor = ArgumentCaptor.forClass(RestoreObjectRequest.class);
        verify(s3Client, times(1)).restoreObject(requestCaptor.capture());

        RestoreObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(s3BucketName, capturedRequest.bucket());
        assertEquals(objectKey, capturedRequest.key());
        assertNotNull(capturedRequest.restoreRequest());
        assertEquals(7, capturedRequest.restoreRequest().days());
        assertEquals(Tier.STANDARD, capturedRequest.restoreRequest().glacierJobParameters().tier());
    }

    @Test
    void testRestoreArchivedData_AlreadyRestoredOrInProgress() {
        String objectKey = "archive/patient_scans/2023/scan_102.dcm";
        S3Exception mockException = (S3Exception) S3Exception.builder()
                .message("Restore is already in progress or object is already restored")
                .statusCode(409)
                .build();

        when(s3Client.restoreObject(any(RestoreObjectRequest.class)))
                .thenThrow(mockException);

        S3Exception exception = assertThrows(S3Exception.class, () -> {
            initiateS3ObjectRestoration(s3BucketName, objectKey, 5, Tier.EXPEDITED);
        });

        assertEquals(409, exception.statusCode());
        assertTrue(exception.getMessage().contains("Restore is already in progress"));
    }

    @Test
    void testRestoreArchivedData_ObjectNotFound() {
        String objectKey = "nonexistent/scan.dcm";

        NoSuchKeyException mockException = (NoSuchKeyException) NoSuchKeyException.builder()
                .message("The specified key does not exist.")
                .statusCode(404)
                .build();

        when(s3Client.restoreObject(any(RestoreObjectRequest.class)))
                .thenThrow(mockException);

        // Verify correct exception is thrown when restoring non-existent data
        NoSuchKeyException exception = assertThrows(NoSuchKeyException.class, () -> {
            initiateS3ObjectRestoration(s3BucketName, objectKey, 14, Tier.BULK);
        });

        assertEquals(404, exception.statusCode());
        assertTrue(exception.getMessage().contains("specified key does not exist"));
    }
}
