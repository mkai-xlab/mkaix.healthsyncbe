package com.g93.be.service.impl;

import com.g93.be.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service("localStorageService")
@Primary
@RequiredArgsConstructor
@Slf4j
public class LocalStorageServiceImpl implements StorageService {

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public String uploadFile(String folderName, String fileName, MultipartFile file) {
        try {
            // e.g. folderName = "images/avatar"
            Path targetLocation = Paths.get(storageBaseDir, folderName).toAbsolutePath().normalize();
            Files.createDirectories(targetLocation);

            Path targetFile = targetLocation.resolve(fileName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Successfully uploaded local file: {}", targetFile.toString());
            
            // Return DB relative path, e.g. "/images/avatar/filename.png"
            String relativePath = "/" + folderName;
            if (!relativePath.endsWith("/")) {
                relativePath += "/";
            }
            return relativePath + fileName;
        } catch (Exception ex) {
            log.error("Could not store file " + fileName + ". Please try again!", ex);
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }
}
