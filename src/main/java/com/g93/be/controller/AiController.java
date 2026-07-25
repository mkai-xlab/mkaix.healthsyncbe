package com.g93.be.controller;


import com.g93.be.entity.AiResult;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.Image;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.ImageRepository;
import com.g93.be.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;
    private final AiResultRepository aiResultRepository;
    private final ImageRepository imageRepository;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @PostMapping("/predict-batch")
    public ResponseEntity<List<ExaminationDto>> predictBatch(@RequestBody AiPredictionRequest request) {
        log.info("Received request to predict AI for {} instances", request.getDicomInstanceIds().size());
        List<ExaminationDto> results = aiService.predictBatch(request);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/heatmap/{aiResultId}")
    public ResponseEntity<Resource> getHeatmapImage(@PathVariable Long aiResultId) {
        AiResult result = aiResultRepository.findById(aiResultId).orElse(null);
        if (result != null && result.getStorageHeatmapFilePath() != null) {
            String imagePath = result.getStorageHeatmapFilePath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Failed to read heatmap image", e);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/image/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable Long imageId) {
        Image image = imageRepository.findById(imageId).orElse(null);
        if (image != null && image.getFilePath() != null) {
            String imagePath = image.getFilePath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                            .body(resource);
                }
            } catch (Exception e) {
                log.error("Failed to read image with id: {}", imageId, e);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
