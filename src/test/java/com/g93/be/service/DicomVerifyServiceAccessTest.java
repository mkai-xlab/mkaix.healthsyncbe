package com.g93.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.SendNotificationRequest;
import com.g93.be.entity.User;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.DicomRawRepository;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.ImageRepository;
import com.g93.be.repository.PatientRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.impl.DicomVerifyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import com.g93.be.dto.VerifySessionResultDto;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DicomVerifyServiceAccessTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private ExaminationRepository examinationRepository;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private ImageRepository imageRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DicomRawRepository dicomRawRepository;
    @Mock
    private AiService aiService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private DicomVerifyServiceImpl dicomVerifyService;

    @Test
    void doctorCannotVerifyAnotherUsersUploadSession() throws Exception {
        DicomVerifyRequest request = new DicomVerifyRequest();
        request.setUploadSessionId("session-1");
        DicomUploadSessionDTO session = DicomUploadSessionDTO.builder()
                .uploadSessionId("session-1")
                .uploaderUserId(7L)
                .patients(Map.of())
                .build();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("uploadSession:session-1"))
                .thenReturn(new ObjectMapper().writeValueAsString(session));

        assertThrows(AccessDeniedException.class,
                () -> dicomVerifyService.verifySession(request, 8L, false));
    }

    @Test
    void sameDoctorCanVerifyOwnSessionAfterLoggingInAgain() throws Exception {
        DicomVerifyRequest request = request("session-own");
        stubSession("session-own", 7L);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        VerifySessionResultDto result = dicomVerifyService.verifySession(request, 7L, false);

        assertEquals(List.of(), result.getSavedInstanceIds());
        verify(stringRedisTemplate).delete("uploadSession:session-own");
        verify(zSetOperations).remove("uploadSessionTimeouts", "session-own");
    }

    @Test
    void departmentHeadCanVerifyAnotherUsersSession() throws Exception {
        DicomVerifyRequest request = request("session-head");
        stubSession("session-head", 7L);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        VerifySessionResultDto result = dicomVerifyService.verifySession(request, 1L, true);

        assertEquals(List.of(), result.getSavedInstanceIds());
    }

    @Test
    void unresolvedRequesterNeverFallsBackToAnotherNotificationRecipient() {
        when(userRepository.findByUsername("missing-doctor")).thenReturn(Optional.empty());
        when(aiService.predictBatch(any(AiPredictionRequest.class))).thenReturn(List.of());

        dicomVerifyService.processVerifiedSessionAsync(List.of(101L), "missing-doctor");

        verify(notificationService, never()).sendNotification(any(SendNotificationRequest.class));
    }

    private DicomVerifyRequest request(String sessionId) {
        DicomVerifyRequest request = new DicomVerifyRequest();
        request.setUploadSessionId(sessionId);
        return request;
    }

    private void stubSession(String sessionId, Long uploaderUserId) throws Exception {
        DicomUploadSessionDTO session = DicomUploadSessionDTO.builder()
                .uploadSessionId(sessionId)
                .uploaderUserId(uploaderUserId)
                .patients(Map.of())
                .build();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("uploadSession:" + sessionId))
                .thenReturn(new ObjectMapper().writeValueAsString(session));
    }
}

