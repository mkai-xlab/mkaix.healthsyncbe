package com.g93.be.service.impl;

import com.g93.be.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket}")
    private String bucketName;

    @Override
    public String uploadFile(String folderName, String fileName, MultipartFile file) {
        String objectKey = folderName.endsWith("/") ? folderName + fileName : folderName + "/" + fileName;

        log.info("Attempting to upload file to S3. Bucket: {}, Key: {}", bucketName, objectKey);

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putOb, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            log.info("Successfully uploaded file to S3: s3://{}/{}", bucketName, objectKey);
            return "Successfully uploaded to S3: s3://" + bucketName + "/" + objectKey;
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }
}
