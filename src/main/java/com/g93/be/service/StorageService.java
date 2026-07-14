package com.g93.be.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    
    /**
     * Uploads a file to the configured S3 bucket.
     * 
     * @param folderName The folder/prefix in the S3 bucket.
     * @param fileName The name of the file to create.
     * @param file The file to upload.
     * @return A success message if uploaded successfully.
     */
    String uploadFile(String folderName, String fileName, MultipartFile file);
}
