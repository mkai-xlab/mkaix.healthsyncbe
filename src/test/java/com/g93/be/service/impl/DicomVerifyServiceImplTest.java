package com.g93.be.service.impl;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.entity.DicomInstance;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DicomVerifyServiceImplTest {

    @Mock
    private AiService aiService;

    @Mock
    private NotificationService notificationService;
    
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DicomVerifyServiceImpl dicomVerifyService;

    @Test
    void testProcessVerifiedSessionAsync_Normal() {
        // Arrange
        List<Long> savedInstanceIds = Arrays.asList(1L, 2L);
        String username = "doctor1";
        User mockUser = new User();
        mockUser.setId(1L);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));
        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(new ArrayList<>());

        // Act
        dicomVerifyService.processVerifiedSessionAsync(savedInstanceIds, username);

        // Assert
        verify(aiService).predictBatch(any(AiPredictionRequest.class));
    }

    @Test
    void testProcessVerifiedSessionAsync_Abnormal_EmptyList() {
        // Arrange
        List<Long> savedInstanceIds = new ArrayList<>();
        String username = "doctor1";

        // Act
        dicomVerifyService.processVerifiedSessionAsync(savedInstanceIds, username);

        // Assert
        verify(aiService, never()).predictBatch(any());
    }
}
