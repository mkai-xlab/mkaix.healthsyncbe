package com.g93.be.config;

import com.g93.be.entity.Image;
import com.g93.be.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageDirectoryMigration implements CommandLineRunner {

    private final ImageRepository imageRepository;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Image Directory Migration...");
        List<Image> allImages = imageRepository.findAll();
        List<Image> updatedImages = new ArrayList<>();
        int migratedCount = 0;

        for (Image image : allImages) {
            String filePath = image.getFilePath();
            // Only process if it is in /images/ but not already in a subdirectory
            if (filePath != null && filePath.startsWith("/images/")) {
                String fileName = filePath.substring("/images/".length());
                // If the fileName still contains '/', it means it's already in a subfolder
                if (fileName.contains("/")) {
                    continue;
                }

                String subDir;
                if (fileName.contains("_annotated")) {
                    subDir = "anno";
                } else if (fileName.contains("_roi")) {
                    subDir = "roi";
                } else if (fileName.contains("_gradcam")) {
                    subDir = "gradcam";
                } else if (fileName.contains("avatar")) {
                    subDir = "avatar";
                } else {
                    subDir = "raw_dicom_image";
                }

                String newDbPath = "/images/" + subDir + "/" + fileName;
                
                // Move physical file
                Path oldPath = Paths.get(storageBaseDir, "images", fileName);
                Path newDirPath = Paths.get(storageBaseDir, "images", subDir);
                Path newPath = newDirPath.resolve(fileName);

                if (Files.exists(oldPath)) {
                    try {
                        Files.createDirectories(newDirPath);
                        Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Moved file {} to {}", oldPath, newPath);
                        
                        image.setFilePath(newDbPath);
                        updatedImages.add(image);
                        migratedCount++;
                    } catch (Exception e) {
                        log.error("Failed to move file {}", oldPath, e);
                    }
                } else {
                    // Even if the file doesn't exist on disk (maybe deleted or just dummy data),
                    // we still want to fix the DB path so it's consistent.
                    image.setFilePath(newDbPath);
                    updatedImages.add(image);
                    migratedCount++;
                }
            }
        }

        if (!updatedImages.isEmpty()) {
            imageRepository.saveAll(updatedImages);
            log.info("Finished Image Directory Migration. Total migrated: {}", migratedCount);
        } else {
            log.info("Image Directory Migration completed with no new files to migrate.");
        }
    }
}
