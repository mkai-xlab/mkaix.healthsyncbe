package com.g93.be.service.impl;

import com.g93.be.entity.Image;
import com.g93.be.repository.ImageRepository;
import com.g93.be.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public Resource getImageResource(Long imageId) {
        Image image = imageRepository.findById(imageId).orElse(null);
        if (image != null && image.getFilePath() != null) {
            String imagePath = image.getFilePath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                }
            } catch (Exception e) {
                log.error("Failed to read image with id: {}", imageId, e);
            }
        }
        return null;
    }
}
