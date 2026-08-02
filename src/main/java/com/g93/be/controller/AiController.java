package com.g93.be.controller;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.exception.ResourceNotFoundException;
import com.g93.be.service.AiService;
import com.g93.be.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;
    private final ImageService imageService;

    @PostMapping("/predict-batch")
    public ResponseEntity<List<ExaminationDto>> predictBatch(@RequestBody AiPredictionRequest request) {
        log.info("Received request to predict AI for {} instances", request.getDicomInstanceIds().size());
        List<ExaminationDto> results = aiService.predictBatch(request);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/heatmap/{aiResultId}")
    public ResponseEntity<Resource> getHeatmapImage(@PathVariable Long aiResultId) {
        Resource resource = aiService.getHeatmapImageResource(aiResultId);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .body(resource);
        }
        throw new ResourceNotFoundException("Không thể tải ảnh nhiệt Grad-CAM của ca khám này. Vui lòng thử lại hoặc liên hệ kỹ thuật.");
    }

    @GetMapping("/image/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable Long imageId) {
        Resource resource = imageService.getImageResource(imageId);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/jpeg")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }
}
