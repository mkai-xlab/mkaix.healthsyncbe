package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.AiResult;
import com.g93.be.entity.Image;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.ImageRepository;
import com.g93.be.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiService aiService;

    @Mock
    private AiResultRepository aiResultRepository;

    @Mock
    private ImageRepository imageRepository;

    @InjectMocks
    private AiController aiController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();

        Path tempDir = Files.createTempDirectory("test_storage");
        ReflectionTestUtils.setField(aiController, "storageBaseDir", tempDir.toString());

        Path dummyHeatmap = tempDir.resolve("dummy_heatmap.jpg");
        Files.write(dummyHeatmap, "dummy data".getBytes());

        Path dummyImage = tempDir.resolve("dummy_image.jpg");
        Files.write(dummyImage, "dummy image data".getBytes());
    }

    // --- predictBatch Tests ---

    @Test
    void testPredictBatch_Normal() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Arrays.asList(1L, 2L));

        when(aiService.predictBatch(any(AiPredictionRequest.class)))
                .thenReturn(Arrays.asList(new ExaminationDto(), new ExaminationDto()));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testPredictBatch_Boundary_EmptyList() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        request.setDicomInstanceIds(Collections.emptyList());

        when(aiService.predictBatch(any(AiPredictionRequest.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testPredictBatch_Boundary_MaxCapacity() throws Exception {
        AiPredictionRequest request = new AiPredictionRequest();
        Long[] ids = new Long[100];
        Arrays.fill(ids, 1L);
        request.setDicomInstanceIds(Arrays.asList(ids));

        ExaminationDto[] dtos = new ExaminationDto[100];
        Arrays.fill(dtos, new ExaminationDto());
        when(aiService.predictBatch(any(AiPredictionRequest.class)))
                .thenReturn(Arrays.asList(dtos));

        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100));
    }

    @Test
    void testPredictBatch_Abnormal_NoBody() throws Exception {
        mockMvc.perform(post("/ai/predict-batch")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // --- getHeatmapImage Tests ---

    @Test
    void testGetHeatmapImage_Normal() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("dummy_heatmap.jpg");
        when(aiResultRepository.findById(1L)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/ai/heatmap/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes("dummy data".getBytes()));
    }

    @Test
    void testGetHeatmapImage_Abnormal_FileNotFound() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath("non_existent_heatmap.jpg");
        when(aiResultRepository.findById(2L)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/ai/heatmap/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Abnormal_PathIsNull() throws Exception {
        AiResult result = new AiResult();
        result.setStorageHeatmapFilePath(null);
        when(aiResultRepository.findById(3L)).thenReturn(Optional.of(result));

        mockMvc.perform(get("/ai/heatmap/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Abnormal_ResultNotFound() throws Exception {
        when(aiResultRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ai/heatmap/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/ai/heatmap/abc"))
                .andExpect(status().isBadRequest());
    }

    // --- getImage Tests ---

    @Test
    void testGetImage_Normal() throws Exception {
        Image image = new Image();
        image.setFilePath("dummy_image.jpg");
        when(imageRepository.findById(1L)).thenReturn(Optional.of(image));

        mockMvc.perform(get("/ai/image/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes("dummy image data".getBytes()));
    }

    @Test
    void testGetImage_Abnormal_FileNotFound() throws Exception {
        Image image = new Image();
        image.setFilePath("non_existent_image.jpg");
        when(imageRepository.findById(2L)).thenReturn(Optional.of(image));

        mockMvc.perform(get("/ai/image/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetImage_Abnormal_ImageNotFound() throws Exception {
        when(imageRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/ai/image/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetImage_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/ai/image/abc"))
                .andExpect(status().isBadRequest());
    }
}
