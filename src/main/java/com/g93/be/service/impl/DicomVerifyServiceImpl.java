package com.g93.be.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.PendingDicomUploadDTO;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.service.DicomVerifyService;
import com.g93.be.service.AiService;
import com.g93.be.service.NotificationService;
import com.g93.be.dto.AiPredictionRequest;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PatientGradeStatsDto;
import com.g93.be.dto.SendNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DicomVerifyServiceImpl implements DicomVerifyService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PatientRepository patientRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ImageRepository imageRepository;
    private final DoctorRepository doctorRepository;
    private final DicomRawRepository dicomRawRepository;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    @Transactional
    public java.util.List<Long> verifySession(
            DicomVerifyRequest request,
            Long requestingUserId,
            boolean privilegedUser) {
        String sessionId = request.getUploadSessionId();
        String redisKey = "uploadSession:" + sessionId;

        String sessionJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (sessionJson == null) {
            throw new RuntimeException("Upload session not found or expired: " + sessionId);
        }

        DicomUploadSessionDTO sessionDTO;
        try {
            sessionDTO = objectMapper.readValue(sessionJson, DicomUploadSessionDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse session data", e);
            throw new RuntimeException("Failed to parse session data", e);
        }

        if (!privilegedUser && !java.util.Objects.equals(sessionDTO.getUploaderUserId(), requestingUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to verify this upload session");
        }

        List<String> acceptedCodes = request.getAcceptedPatientCodes() != null ? request.getAcceptedPatientCodes() : List.of();
        List<Long> savedInstanceIds = new ArrayList<>();

        for (PendingDicomUploadDTO pending : sessionDTO.getPatients().values()) {
            if (acceptedCodes.contains(pending.getPatientCode())) {
                savedInstanceIds.addAll(savePatientData(pending, sessionDTO.getUploaderUserId()));
            } else {
                deletePhysicalFiles(pending);
            }
        }

        // Cleanup Redis
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.opsForZSet().remove("uploadSessionTimeouts", sessionId);
        log.info("Session {} verified and cleaned from Redis.", sessionId);
        
        return savedInstanceIds;
    }

    private List<Long> savePatientData(PendingDicomUploadDTO pending, Long uploaderUserId) {
        List<Long> instanceIds = new ArrayList<>();
        final String finalStudyUid = (pending.getStudyInstanceUid() != null && !pending.getStudyInstanceUid().isEmpty())
                ? pending.getStudyInstanceUid()
                : "UNKNOWN_STUDY_" + System.currentTimeMillis();

        LocalDate studyDateForGrouping = (pending.getStudyDate() != null)
                ? pending.getStudyDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        Optional<Examination> existingExamOpt = examinationRepository.findFirstByPatientPatientCodeAndStudyDateOrderByCreatedAtDesc(pending.getPatientCode(), studyDateForGrouping);
        Examination examination;

        if (existingExamOpt.isPresent()) {
            examination = existingExamOpt.get();
            if (examination.getStatus() == ExaminationStatus.REPORT_GENERATED) {
                deletePhysicalFiles(pending);
                throw new RuntimeException("Cannot upload new dicoms to an examination that is already REPORT_GENERATED for patient " + pending.getPatientCode() + " on " + studyDateForGrouping);
            }
            if (examination.getStatus() == ExaminationStatus.NEED_VERIFY || examination.getStatus() == ExaminationStatus.VERIFIED) {
                examination.setStatus(ExaminationStatus.AI_PROCESSING);
                examinationRepository.save(examination);
            }
        } else {
            // Get or Create Patient
            final String finalPatientId = pending.getPatientCode();
            final String finalPatientName = (pending.getPatientName() != null && !pending.getPatientName().isEmpty()) ? pending.getPatientName() : "Unknown";

            Patient patient = patientRepository.findByPatientCode(finalPatientId).orElse(null);
            if (patient != null) {
                patient.setFullName(finalPatientName.replace("^", " ").trim());
                if (pending.getPatientBirthDate() != null) {
                    patient.setDob(pending.getPatientBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                }
                if ("F".equalsIgnoreCase(pending.getPatientSex())) {
                    patient.setGender(Gender.FEMALE);
                } else if ("M".equalsIgnoreCase(pending.getPatientSex())) {
                    patient.setGender(Gender.MALE);
                } else {
                    patient.setGender(Gender.OTHER);
                }
                patient = patientRepository.save(patient);
            } else {
                Patient p = new Patient();
                p.setPatientCode(finalPatientId);
                p.setEmail(finalPatientId + "_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                p.setFullName(finalPatientName.replace("^", " ").trim());
                if (pending.getPatientBirthDate() != null) {
                    p.setDob(pending.getPatientBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                }
                if ("F".equalsIgnoreCase(pending.getPatientSex())) {
                    p.setGender(Gender.FEMALE);
                } else if ("M".equalsIgnoreCase(pending.getPatientSex())) {
                    p.setGender(Gender.MALE);
                } else {
                    p.setGender(Gender.OTHER);
                }
                patient = patientRepository.save(p);
            }

            // Create new Examination
            examination = new Examination();
            examination.setPatient(patient);

            Doctor doctor = null;
            if (uploaderUserId != null) {
                doctor = doctorRepository.findById(uploaderUserId).orElse(null);
            }
            if (doctor == null) {
                throw new AccessDeniedException("Bạn không có quyền truy cập hoặc hệ thống không tìm thấy thông tin Bác sĩ (Missing/Invalid Doctor ID).");
            }
            examination.setDoctor(doctor);

            examination.setEncounterCode(finalStudyUid);
            examination.setStatus(ExaminationStatus.AI_PROCESSING);
            examination.setVisitTime(LocalDateTime.now());
            examination.setStudyDate(studyDateForGrouping);
            if (pending.getStudyTime() != null) {
                examination.setStudyTime(pending.getStudyTime().toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
            }
            examination.setDescription(pending.getDescription());
            examination.setReferringPhysician(pending.getReferringPhysician());
            examination = examinationRepository.save(examination);
        }

        // Images and Instances
        String firstPngPath = null;
        Map<String, Image> pngMap = new HashMap<>();
        Map<String, DicomRaw> rawMap = new HashMap<>();

        for (PendingDicomUploadDTO.ImageCacheDTO imageCache : pending.getParsedImages()) {
            if ("image/png".equals(imageCache.getMimeType())) {
                Image image = new Image();
                image.setFilePath(imageCache.getStoredFilePath());
                image.setExtension("png");
                image = imageRepository.save(image);
                pngMap.put(imageCache.getSopInstanceUid(), image);
                if (firstPngPath == null) {
                    firstPngPath = imageCache.getStoredFilePath();
                }
            } else {
                DicomRaw raw = new DicomRaw();
                raw.setFilePath(imageCache.getStoredFilePath());
                raw.setExtension("dcm");
                raw = dicomRawRepository.save(raw);
                rawMap.put(imageCache.getSopInstanceUid(), raw);
            }
        }

        for (PendingDicomUploadDTO.InstanceCacheDTO instCache : pending.getParsedInstances()) {
            DicomInstance instance = new DicomInstance();
            instance.setExamination(examination);
            instance.setSopInstanceUid(instCache.getSopInstanceUid());
            instance.setStudyInstanceUid(finalStudyUid);
            instance.setBodyPart(instCache.getBodyPart());
            instance.setCreatedAt(LocalDateTime.now());
            instance.setStatus(DicomInstanceStatus.AI_SENDING);
            
            Image matchedImage = pngMap.get(instCache.getSopInstanceUid());
            if (matchedImage == null && !pngMap.isEmpty()) {
                matchedImage = pngMap.values().iterator().next();
            }
            instance.setImage(matchedImage);
            
            DicomRaw matchedRaw = rawMap.get(instCache.getSopInstanceUid());
            if (matchedRaw == null && !rawMap.isEmpty()) {
                matchedRaw = rawMap.values().iterator().next();
            }
            instance.setDicomRaw(matchedRaw);
            
            instance = dicomInstanceRepository.save(instance);
            instanceIds.add(instance.getId());
        }
        
        return instanceIds;
    }

    private void deletePhysicalFiles(PendingDicomUploadDTO pending) {
        if (pending.getPhysicalFilePaths() != null) {
            for (String absolutePath : pending.getPhysicalFilePaths().values()) {
                try {
                    Path path = Paths.get(absolutePath);
                    Files.deleteIfExists(path);
                    log.info("Deleted rejected file: {}", absolutePath);
                } catch (IOException e) {
                    log.error("Failed to delete physical file {}", absolutePath, e);
                }
            }
        }
    }

    @Override
    @org.springframework.scheduling.annotation.Async
    public void processVerifiedSessionAsync(List<Long> savedInstanceIds, String username) {
        if (savedInstanceIds == null || savedInstanceIds.isEmpty()) {
            return;
        }

        Long userId = null;
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        final Long finalUserId = (userId != null) ? userId : 1L;

        try {
            AiPredictionRequest aiRequest = new AiPredictionRequest(savedInstanceIds);
            List<ExaminationDto> aiResultsList = aiService.predictBatch(aiRequest);
            
            // Calculate patient statistics based on max_predicted_grade for this specific batch
            Map<Long, Integer> patientToMaxGrade = new HashMap<>();
            for (ExaminationDto exam : aiResultsList) {
                if (exam.getPatient() != null && exam.getMaxPredictedGrade() != null) {
                    Long patId = exam.getPatient().getId();
                    Integer currentMax = patientToMaxGrade.getOrDefault(patId, -1);
                    if (exam.getMaxPredictedGrade() > currentMax) {
                        patientToMaxGrade.put(patId, exam.getMaxPredictedGrade());
                    }
                }
            }

            Map<Integer, Long> gradeCountMap = new HashMap<>();
            for (Integer grade : patientToMaxGrade.values()) {
                gradeCountMap.put(grade, gradeCountMap.getOrDefault(grade, 0L) + 1);
            }

            List<PatientGradeStatsDto> statsList = gradeCountMap.entrySet().stream()
                    .map(entry -> new PatientGradeStatsDto(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            
            // Send success notification
            SendNotificationRequest notifReq = new SendNotificationRequest(
                    finalUserId,
                    "Phân tích AI hoàn tất",
                    "Hệ thống đã phân tích thành công hình ảnh X-Quang từ phiên xác nhận.",
                    "AI_RESULT",
                    statsList
            );
            notificationService.sendNotification(notifReq);

        } catch (Exception e) {
            log.error("Error during background AI processing", e);
            // Send error notification
            try {
                SendNotificationRequest errReq = new SendNotificationRequest(
                        finalUserId,
                        "Lỗi phân tích AI",
                        "Đã có lỗi xảy ra trong quá trình phân tích AI.",
                        "ERROR",
                        null
                );
                notificationService.sendNotification(errReq);
            } catch (Exception ignored) {}
        }
    }
}
