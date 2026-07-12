package com.g93.be.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.DicomVerifyRequest;
import com.g93.be.dto.PendingDicomUploadDTO;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.service.DicomVerifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final RoleRepository roleRepository;
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    @Transactional
    public void verifySession(DicomVerifyRequest request) {
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

        List<String> acceptedCodes = request.getAcceptedPatientCodes() != null ? request.getAcceptedPatientCodes() : List.of();

        for (PendingDicomUploadDTO pending : sessionDTO.getPatients().values()) {
            if (acceptedCodes.contains(pending.getPatientCode())) {
                savePatientData(pending, sessionDTO.getUploaderUserId());
            } else {
                deletePhysicalFiles(pending);
            }
        }

        // Cleanup Redis
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.opsForZSet().remove("uploadSessionTimeouts", sessionId);
        log.info("Session {} verified and cleaned from Redis.", sessionId);
    }

    private void savePatientData(PendingDicomUploadDTO pending, Long uploaderUserId) {
        final String finalStudyUid = (pending.getStudyInstanceUid() != null && !pending.getStudyInstanceUid().isEmpty())
                ? pending.getStudyInstanceUid()
                : "UNKNOWN_STUDY_" + System.currentTimeMillis();

        Optional<Examination> existingExamOpt = examinationRepository.findByEncounterCode(finalStudyUid);
        Examination examination;

        if (existingExamOpt.isPresent()) {
            examination = existingExamOpt.get();
            examination.setStatus(ExaminationStatus.NEED_REVERIFY);
            examinationRepository.save(examination);
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
                doctor = doctorRepository.findAll().stream().findFirst().orElseGet(() -> {
                    Doctor d = new Doctor();
                    d.setUsername("dummy_doc_" + UUID.randomUUID().toString().substring(0, 8));
                    d.setPassword("temp");
                    d.setEmail("dummy_doc_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                    d.setFullName("System Doctor");
                    d.setStatus(com.g93.be.entity.UserStatus.ACTIVE);
                    com.g93.be.entity.Role doctorRole = roleRepository.findByCode("DOCTOR").orElseGet(() -> {
                        com.g93.be.entity.Role r = new com.g93.be.entity.Role();
                        r.setCode("DOCTOR");
                        r.setName("Doctor Role");
                        return roleRepository.save(r);
                    });
                    d.setRole(doctorRole);
                    d.setYearsOfExperience(0);
                    return doctorRepository.save(d);
                });
            }
            examination.setDoctor(doctor);

            examination.setEncounterCode(finalStudyUid);
            examination.setStatus(ExaminationStatus.NEED_VERIFY);
            examination.setVisitTime(LocalDateTime.now());
            if (pending.getStudyDate() != null) {
                examination.setStudyDate(pending.getStudyDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            }
            if (pending.getStudyTime() != null) {
                examination.setStudyTime(pending.getStudyTime().toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
            }
            examination.setBodyPart(pending.getBodyPart());
            examination.setDescription(pending.getDescription());
            examination.setReferringPhysician(pending.getReferringPhysician());
            examination = examinationRepository.save(examination);
        }

        // Images and Instances
        String firstPngPath = null;
        for (PendingDicomUploadDTO.ImageCacheDTO imageCache : pending.getParsedImages()) {
            Image image = new Image();
            image.setFilePath(imageCache.getStoredFilePath());
            if ("image/png".equals(imageCache.getMimeType())) {
                image.setExtension("png");
                if (firstPngPath == null) {
                    firstPngPath = imageCache.getStoredFilePath();
                }
            } else {
                image.setExtension("dcm");
            }
            imageRepository.save(image);
        }

        if (firstPngPath != null && examination.getImagePath() == null) {
            examination.setImagePath(firstPngPath);
            examinationRepository.save(examination);
        }

        for (PendingDicomUploadDTO.InstanceCacheDTO instCache : pending.getParsedInstances()) {
            DicomInstance instance = new DicomInstance();
            instance.setExamination(examination);
            instance.setSopInstanceUid(instCache.getSopInstanceUid());
            instance.setStudyInstanceUid(finalStudyUid);
            instance.setCreatedAt(LocalDateTime.now());
            instance.setStorageRawPath(instCache.getFilePath());
            // Need to match PNG path properly, but for simplicity:
            if (firstPngPath != null) {
                instance.setStoragePngPath(firstPngPath); // this might overwrite if multiple instances
            }
            dicomInstanceRepository.save(instance);
        }
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
}
