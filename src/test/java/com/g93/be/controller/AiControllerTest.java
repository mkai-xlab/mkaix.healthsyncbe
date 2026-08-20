package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.service.AiService;
import com.g93.be.service.ImageService;
import com.g93.be.exception.GlobalExceptionHandler;
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

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import java.util.Arrays;
import java.util.Collections;

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
    private ImageService imageService;

    @InjectMocks
    private AiController aiController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(aiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
        Resource dummyResource = new ByteArrayResource("dummy data".getBytes());
        when(aiService.getHeatmapImageResource(1L)).thenReturn(dummyResource);

        mockMvc.perform(get("/ai/heatmap/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes("dummy data".getBytes()));
    }

    @Test
    void testGetHeatmapImage_Abnormal_FileNotFound() throws Exception {
        when(aiService.getHeatmapImageResource(2L)).thenReturn(null);

        mockMvc.perform(get("/ai/heatmap/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Abnormal_PathIsNull() throws Exception {
        when(aiService.getHeatmapImageResource(3L)).thenReturn(null);

        mockMvc.perform(get("/ai/heatmap/3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetHeatmapImage_Abnormal_ResultNotFound() throws Exception {
        when(aiService.getHeatmapImageResource(99L)).thenReturn(null);

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
        Resource dummyResource = new ByteArrayResource("dummy image data".getBytes());
        when(imageService.getImageResource(1L)).thenReturn(dummyResource);

        mockMvc.perform(get("/ai/image/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes("dummy image data".getBytes()));
    }

    @Test
    void testGetImage_Abnormal_FileNotFound() throws Exception {
        when(imageService.getImageResource(2L)).thenReturn(null);

        mockMvc.perform(get("/ai/image/2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetImage_Abnormal_ImageNotFound() throws Exception {
        when(imageService.getImageResource(99L)).thenReturn(null);

        mockMvc.perform(get("/ai/image/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetImage_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/ai/image/abc"))
                .andExpect(status().isBadRequest());
    }
}
