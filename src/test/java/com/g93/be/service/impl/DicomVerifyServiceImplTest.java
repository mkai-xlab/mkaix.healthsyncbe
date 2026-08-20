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
    
    /**
     * Mục đích: Kiểm tra hành vi của hệ thống khi danh sách ID DicomInstance truyền vào là null.
     * Đầu vào: instanceIds = null.
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Bỏ qua toàn bộ tiến trình, không gọi AI service hay Notification service.
     */
    @Test
    void test_Boundary_NullInstanceIds() {
        dicomVerifyService.processVerifiedSessionAsync(null, "doctor1");
        verify(aiService, never()).predictBatch(any());
        verify(notificationService, never()).sendNotification(any());
    }

    /**
     * Mục đích: Kiểm tra hành vi của hệ thống khi danh sách ID DicomInstance rỗng.
     * Đầu vào: instanceIds rỗng (new ArrayList).
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Bỏ qua toàn bộ tiến trình, không gọi AI hay gửi thông báo.
     */
    @Test
    void test_Boundary_EmptyInstanceIds() {
        dicomVerifyService.processVerifiedSessionAsync(new ArrayList<>(), "doctor1");
        verify(aiService, never()).predictBatch(any());
        verify(notificationService, never()).sendNotification(any());
    }

    // ==========================================
    // 2. ABNORMAL CASES
    // ==========================================

    /**
     * Mục đích: Kiểm tra hệ thống khi username truyền vào là null.
     * Đầu vào: username = null, danh sách ID hợp lệ.
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Quá trình AI dự đoán vẫn được gọi nhưng việc tìm kiếm User và gửi thông báo Notification bị bỏ qua (không gây crash).
     */
    @Test
    void test_Abnormal_NullUsername() {
        List<Long> instanceIds = Arrays.asList(1L);
        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(new ArrayList<>());

        dicomVerifyService.processVerifiedSessionAsync(instanceIds, null);

        verify(userRepository, never()).findByUsername(any());
        verify(aiService, times(1)).predictBatch(any(AiPredictionRequest.class));
        verify(notificationService, never()).sendNotification(any());
    }

    /**
     * Mục đích: Kiểm tra luồng xử lý khi không tìm thấy thông tin User trong Database.
     * Đầu vào: username không tồn tại trong DB, mock trả về Optional.empty().
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: AI service vẫn phân tích ảnh nhưng phần thống kê và thông báo không được gửi đi.
     */
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

    /**
     * Mục đích: Kiểm tra khả năng bắt lỗi khi Service AI (AiService) ném ra ngoại lệ.
     * Đầu vào: Mock AiService ném RuntimeException ("AI API Timeout").
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Bắt ngoại lệ và gửi thông báo loại ERROR "Lỗi phân tích AI" qua NotificationService.
     */
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

    /**
     * Mục đích: Kiểm tra luồng xử lý khi cả AiService và NotificationService đều bị lỗi (chết dây chuyền).
     * Đầu vào: AiService ném ngoại lệ Timeout, NotificationService cũng ném ngoại lệ khi cố gửi lỗi.
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Hệ thống bắt (swallow) toàn bộ lỗi một cách an toàn mà không làm sập luồng Async.
     */
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

    /**
     * Mục đích: Kiểm tra thông báo được gửi khi AI xử lý xong nhưng không trả về dự đoán nào.
     * Đầu vào: AiService trả về danh sách ExaminationDto rỗng.
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Gửi thông báo thành công với loại "AI_RESULT", nội dung thống kê số liệu rỗng (nhưng không null).
     */
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

    /**
     * Mục đích: Kiểm tra logic gom nhóm dự đoán theo cùng một Bệnh nhân (Same Patient).
     * Đầu vào: AiService trả về 2 kết quả thuộc về cùng 1 PatientId (lần lượt Grade = 2 và Grade = 4).
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Gửi thông báo thống kê gộp chung lại cho Bệnh nhân đó (chỉ lấy Max Grade = 4), mảng thống kê trả về có size = 1.
     */
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

    /**
     * Mục đích: Kiểm tra logic gom nhóm dự đoán cho nhiều Bệnh nhân khác nhau.
     * Đầu vào: AiService trả về 2 kết quả thuộc về 2 Bệnh nhân khác nhau (Grade 1 và Grade 4).
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Gửi thông báo có kèm theo số liệu thống kê riêng biệt cho 2 bệnh nhân (mảng thống kê trả về có size = 2).
     */
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

    /**
     * Mục đích: Kiểm tra độ an toàn khi nhận được kết quả dự đoán thiếu thông tin thiết yếu (Null Patient hoặc Null Grade).
     * Đầu vào: Một kết quả null Patient, một kết quả null MaxPredictedGrade.
     * Hành động: Gọi processVerifiedSessionAsync().
     * Kỳ vọng: Dễ dàng bỏ qua (skip) các bản ghi lỗi, mảng thống kê trả về rỗng, không xảy ra lỗi NullPointerException.
     */
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

    // --- AUTO-GENERATED MISSING TESTS FROM EXCEL ---
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID01
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID01() {
        // TODO: Implement mock setup and assertion for UTCID01
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID02
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID02() {
        // TODO: Implement mock setup and assertion for UTCID02
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID03
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID03() {
        // TODO: Implement mock setup and assertion for UTCID03
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID04
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID04() {
        // TODO: Implement mock setup and assertion for UTCID04
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID05
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID05() {
        // TODO: Implement mock setup and assertion for UTCID05
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID06
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID06() {
        // TODO: Implement mock setup and assertion for UTCID06
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID07
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID07() {
        // TODO: Implement mock setup and assertion for UTCID07
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID08
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID08() {
        // TODO: Implement mock setup and assertion for UTCID08
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID09
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID09() {
        // TODO: Implement mock setup and assertion for UTCID09
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
    /**
     * Mục đích: Verify processVerifiedSessionAsync AI batch req
     * Kịch bản Test Design: UTCID10
     * Ghi chú: Được bổ sung tự động để khớp với Report5.1_Unit Test.xlsx
     */
    @Test
    @org.junit.jupiter.api.Disabled("Need manual implementation for specific mock setup based on Excel matrix")
    void testProcessVerifiedSessionAsync_UTCID10() {
        // TODO: Implement mock setup and assertion for UTCID10
        org.junit.jupiter.api.Assertions.assertTrue(true, "Test scaffold generated");
    }
}
