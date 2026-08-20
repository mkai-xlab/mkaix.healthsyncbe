package com.g93.be.service.impl;

import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.entity.Patient;
import com.g93.be.dto.PatientResponse;
import com.g93.be.entity.User;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    // ==========================================
    // 1. BOUNDARY CASES
    // ==========================================
    
    @Test
    void test_Boundary_NullInstanceIds() {
        dicomVerifyService.processVerifiedSessionAsync(null, "doctor1");
        verify(aiService, never()).predictBatch(any());
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void test_Boundary_EmptyInstanceIds() {
        dicomVerifyService.processVerifiedSessionAsync(new ArrayList<>(), "doctor1");
        verify(aiService, never()).predictBatch(any());
        verify(notificationService, never()).sendNotification(any());
    }

    // ==========================================
    // 2. ABNORMAL CASES
    // ==========================================

    @Test
    void test_Abnormal_NullUsername() {
        List<Long> instanceIds = Arrays.asList(1L);
        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(new ArrayList<>());

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, null);

        verify(userRepository, never()).findByUsername(any());
        verify(aiService, times(1)).predictBatch(any(AiPredictionRequest.class));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void test_Abnormal_UserNotFound() {
        List<Long> instanceIds = Arrays.asList(1L);
        String username = "unknown_user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(new ArrayList<>());

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);

        verify(aiService, times(1)).predictBatch(any(AiPredictionRequest.class));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void test_Abnormal_AiServiceThrowsException() {
        List<Long> instanceIds = Arrays.asList(1L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(aiService.predictBatch(any())).thenThrow(new RuntimeException("AI API Timeout"));

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(notifCaptor.capture());
        
        SendNotificationRequest sentNotif = notifCaptor.getValue();
        assertEquals(99L, sentNotif.userId());
        assertEquals("ERROR", sentNotif.type());
        assertEquals("Lỗi phân tích AI", sentNotif.title());
    }

    @Test
    void test_Abnormal_AiServiceAndNotificationThrowException() {
        List<Long> instanceIds = Arrays.asList(1L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        
        when(aiService.predictBatch(any())).thenThrow(new RuntimeException("AI API Timeout"));
        doThrow(new RuntimeException("Websocket connection refused")).when(notificationService).sendNotification(any());

        assertDoesNotThrow(() -> {
            dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);
        }, "Exception should be swallowed gracefully");
    }

    // ==========================================
    // 3. NORMAL CASES
    // ==========================================

    @Test
    void test_Normal_NoPredictions() {
        List<Long> instanceIds = Arrays.asList(1L, 2L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(aiService.predictBatch(any())).thenReturn(new ArrayList<>());

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(notifCaptor.capture());
        
        SendNotificationRequest sentNotif = notifCaptor.getValue();
        assertEquals("AI_RESULT", sentNotif.type());
        assertNotNull(sentNotif.data()); // Stats list should be empty but present
    }

    @Test
    void test_Normal_ValidPredictions_SamePatient() {
        List<Long> instanceIds = Arrays.asList(1L, 2L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        PatientResponse patient1 = new PatientResponse();
        patient1.setId(10L);

        ExaminationDto exam1 = new ExaminationDto();
        exam1.setPatient(patient1);
        exam1.setMaxPredictedGrade(2);

        ExaminationDto exam2 = new ExaminationDto();
        exam2.setPatient(patient1);
        exam2.setMaxPredictedGrade(4); // Max grade for patient 10 is 4

        when(aiService.predictBatch(any())).thenReturn(Arrays.asList(exam1, exam2));

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(notifCaptor.capture());
        
        SendNotificationRequest sentNotif = notifCaptor.getValue();
        List<?> statsList = (List<?>) sentNotif.data();
        assertEquals(1, statsList.size(), "Should have exactly 1 grade count object (grade 4 -> count 1)");
    }

    @Test
    void test_Normal_ValidPredictions_MultiplePatients() {
        List<Long> instanceIds = Arrays.asList(1L, 2L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        PatientResponse patient1 = new PatientResponse();
        patient1.setId(10L);
        PatientResponse patient2 = new PatientResponse();
        patient2.setId(20L);

        ExaminationDto exam1 = new ExaminationDto();
        exam1.setPatient(patient1);
        exam1.setMaxPredictedGrade(1); // Pat 10 -> Grade 1

        ExaminationDto exam2 = new ExaminationDto();
        exam2.setPatient(patient2);
        exam2.setMaxPredictedGrade(4); // Pat 20 -> Grade 4

        when(aiService.predictBatch(any())).thenReturn(Arrays.asList(exam1, exam2));

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(notifCaptor.capture());
        
        SendNotificationRequest sentNotif = notifCaptor.getValue();
        List<?> statsList = (List<?>) sentNotif.data();
        assertEquals(2, statsList.size(), "Should have counts for grade 1 and grade 4");
    }

    @Test
    void test_Normal_PredictionWithNullPatientOrGrade() {
        List<Long> instanceIds = Arrays.asList(1L, 2L);
        String username = "doctor1";
        User user = new User();
        user.setId(99L);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        ExaminationDto examNullPatient = new ExaminationDto();
        examNullPatient.setPatient(null);
        examNullPatient.setMaxPredictedGrade(2);

        ExaminationDto examNullGrade = new ExaminationDto();
        PatientResponse patient1 = new PatientResponse();
        patient1.setId(10L);
        examNullGrade.setPatient(patient1);
        examNullGrade.setMaxPredictedGrade(null);

        when(aiService.predictBatch(any())).thenReturn(Arrays.asList(examNullPatient, examNullGrade));

        assertDoesNotThrow(() -> {
            dicomVerifyService.processVerifiedSessionAsync(instanceIds, username);
        });

        ArgumentCaptor<SendNotificationRequest> notifCaptor = ArgumentCaptor.forClass(SendNotificationRequest.class);
        verify(notificationService, times(1)).sendNotification(notifCaptor.capture());
        
        SendNotificationRequest sentNotif = notifCaptor.getValue();
        List<?> statsList = (List<?>) sentNotif.data();
        assertEquals(0, statsList.size(), "Stats should be empty as all invalid exams were skipped");
    }
}
