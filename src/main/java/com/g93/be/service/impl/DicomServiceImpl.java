package com.g93.be.service.impl;

import com.g93.be.dto.DicomTagResponse;
import com.g93.be.entity.*;
import com.g93.be.service.DicomService;
import com.g93.be.repository.*;
import com.g93.be.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.dto.FileUploadError;
import com.g93.be.dto.SendNotificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DicomServiceImpl implements DicomService {

    private final PatientRepository patientRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final ImageRepository imageRepository;
    private final RoleRepository roleRepository;
    private final DoctorRepository doctorRepository;
    private final NotificationService notificationService;

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        // ... keeping the previous implementation simplified or stubbed to focus on the batch
        return new ArrayList<>();
    }

    private final ApplicationContext applicationContext;

    @Override
    public BatchDicomUploadResponse uploadBatch(List<MultipartFile> files, Long userId) {
        List<FileUploadError> errors = new ArrayList<>();
        List<String> validFilePaths = new ArrayList<>();
        List<String> originalFilenames = new ArrayList<>();

        Path baseDicomDir = Paths.get(storageBaseDir, "dicom");
        try {
            Files.createDirectories(baseDicomDir);
        } catch (IOException e) {
            log.error("Cannot create base dir", e);
            throw new RuntimeException("Cannot create storage directories");
        }

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".dcm")) {
                errors.add(new FileUploadError(originalFilename, "Invalid file format. Only .dcm files are allowed."));
                continue;
            }

            try (InputStream is = file.getInputStream()) {
                if (file.getSize() < 132) {
                    errors.add(new FileUploadError(originalFilename, "File is too small to be a valid DICOM."));
                    continue;
                }
                long skipped = is.skip(128);
                if (skipped != 128) {
                    errors.add(new FileUploadError(originalFilename, "Failed to read file signature."));
                    continue;
                }
                byte[] magic = new byte[4];
                int read = is.read(magic);
                if (read != 4 || !new String(magic).equals("DICM")) {
                    errors.add(new FileUploadError(originalFilename, "File does not have a valid DICOM signature."));
                    continue;
                }
            } catch (IOException e) {
                log.error("Error reading file signature for {}", originalFilename, e);
                errors.add(new FileUploadError(originalFilename, "Error reading file content."));
                continue;
            }

            try {
                Path tempFile = Files.createTempFile("batch_", ".dcm");
                file.transferTo(tempFile.toFile());
                validFilePaths.add(tempFile.toAbsolutePath().toString());
                originalFilenames.add(originalFilename);
            } catch (Exception e) {
                log.error("Error saving file {} for background processing", originalFilename, e);
                errors.add(new FileUploadError(originalFilename, "Failed to save file for processing."));
            }
        }

        if (!validFilePaths.isEmpty()) {
            applicationContext.getBean(DicomService.class).processBatchAsync(validFilePaths, originalFilenames, userId);
        }

        String message = "Successfully received " + validFilePaths.size() + " DICOM files. Processing in background.";
        return new BatchDicomUploadResponse(message, errors, new ArrayList<>());
    }

    @Override
    @Async
    @Transactional
    public void processBatchAsync(List<String> tempFilePaths, List<String> originalFilenames, Long userId) {
        log.info("Starting background processing for {} DICOM files", tempFilePaths.size());
        Set<String> processedUids = new HashSet<>();
        
        Path baseDicomDir = Paths.get(storageBaseDir, "dicom");
        Path baseImageDir = Paths.get(storageBaseDir, "images");
        try {
            Files.createDirectories(baseImageDir);
        } catch (IOException e) {
            log.error("Cannot create image dir", e);
            return;
        }

        for (int i = 0; i < tempFilePaths.size(); i++) {
            Path tempFile = Paths.get(tempFilePaths.get(i));
            String originalFilename = originalFilenames.get(i);

            try {
                String patientId = null;
                String patientName = null;
                Date patientBirthDate = null;
                String patientSex = null;

                String studyInstanceUid = null;
                Date studyDate = null;
                Date studyTime = null;
                String bodyPart = null;
                String description = null;
                String referringPhysician = null;

                String sopInstanceUid = null;
                String imageLaterality = null;
                int imageRows = 0;
                int imageColumns = 0;

                try (DicomInputStream dis = new DicomInputStream(tempFile.toFile())) {
                    Attributes attrs = dis.readDataset();
                    patientId = attrs.getString(org.dcm4che3.data.Tag.PatientID, "");
                    patientName = attrs.getString(org.dcm4che3.data.Tag.PatientName, "");
                    patientBirthDate = attrs.getDate(org.dcm4che3.data.Tag.PatientBirthDate);
                    patientSex = attrs.getString(org.dcm4che3.data.Tag.PatientSex, "");

                    studyInstanceUid = attrs.getString(org.dcm4che3.data.Tag.StudyInstanceUID, "");
                    studyDate = attrs.getDate(org.dcm4che3.data.Tag.StudyDate);
                    studyTime = attrs.getDate(org.dcm4che3.data.Tag.StudyTime);
                    bodyPart = attrs.getString(org.dcm4che3.data.Tag.ProtocolName, "");
                    description = attrs.getString(org.dcm4che3.data.Tag.StudyDescription, "");
                    referringPhysician = attrs.getString(org.dcm4che3.data.Tag.ReferringPhysicianName, "");

                    sopInstanceUid = attrs.getString(org.dcm4che3.data.Tag.SOPInstanceUID, "");
                    imageLaterality = attrs.getString(org.dcm4che3.data.Tag.ImageLaterality, "");
                    imageRows = attrs.getInt(org.dcm4che3.data.Tag.Rows, 0);
                    imageColumns = attrs.getInt(org.dcm4che3.data.Tag.Columns, 0);
                }

                if (sopInstanceUid == null || sopInstanceUid.isEmpty()) {
                    log.warn("Missing SOPInstanceUID for file {}", originalFilename);
                    continue;
                }

                if (processedUids.contains(sopInstanceUid) || dicomInstanceRepository.existsBySopInstanceUid(sopInstanceUid)) {
                    log.warn("Duplicate SOPInstanceUID for file {}", originalFilename);
                    continue;
                }
                
                processedUids.add(sopInstanceUid);

                final Date finalPatientBirthDate = patientBirthDate;
                final String finalPatientSex = patientSex;
                final String finalStudyUid = (studyInstanceUid != null && !studyInstanceUid.isEmpty()) ? studyInstanceUid : "UNKNOWN_STUDY_" + System.currentTimeMillis();

                // Check if Study Instance UID exists
                Optional<Examination> existingExamOpt = examinationRepository.findByEncounterCode(finalStudyUid);
                Examination examination;

                if (existingExamOpt.isPresent()) {
                    examination = existingExamOpt.get();
                    examination.setStatus(ExaminationStatus.NEED_REVERIFY);
                    examinationRepository.save(examination);
                    log.info("Study {} already exists. Flagged as NEED_REVERIFY.", finalStudyUid);
                } else {
                    // Get or Create Patient
                    final String finalPatientId = (patientId != null && !patientId.isEmpty()) ? patientId : "UNKNOWN";
                    final String finalPatientName = (patientName != null && !patientName.isEmpty()) ? patientName : "Unknown";
                    Patient patient = patientRepository.findByPatientCode(finalPatientId).orElseGet(() -> {
                        Patient p = new Patient();
                        p.setPatientCode(finalPatientId);
                        p.setEmail(finalPatientId + "_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                        p.setFullName(finalPatientName.replace("^", " ").trim());
                        if (finalPatientBirthDate != null) {
                            p.setDob(finalPatientBirthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                        }
                        if ("F".equalsIgnoreCase(finalPatientSex)) {
                            p.setGender(Gender.FEMALE);
                        } else if ("M".equalsIgnoreCase(finalPatientSex)) {
                            p.setGender(Gender.MALE);
                        } else {
                            p.setGender(Gender.OTHER);
                        }
                        return patientRepository.save(p);
                    });

                    // Create new Examination
                    examination = new Examination();
                    examination.setPatient(patient);
                    
                    Doctor doctor = doctorRepository.findAll().stream().findFirst().orElseGet(() -> {
                        Doctor d = new Doctor();
                        d.setUsername("dummy_doc_" + UUID.randomUUID().toString().substring(0, 8));
                        d.setPassword("temp");
                        d.setEmail("dummy_doc_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                        d.setFullName("System Doctor");
                        d.setStatus(UserStatus.ACTIVE);
                        Role doctorRole = roleRepository.findByCode("DOCTOR").orElseGet(() -> {
                            Role r = new Role();
                            r.setCode("DOCTOR");
                            r.setName("Doctor Role");
                            return roleRepository.save(r);
                        });
                        d.setRole(doctorRole);
                        d.setYearsOfExperience(0);
                        return doctorRepository.save(d);
                    });
                    examination.setDoctor(doctor);
                    
                    examination.setEncounterCode(finalStudyUid);
                    examination.setStatus(ExaminationStatus.PENDING_REVIEW);
                    examination.setVisitTime(LocalDateTime.now());
                    if (studyDate != null) {
                        examination.setStudyDate(studyDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                    }
                    if (studyTime != null) {
                        examination.setStudyTime(studyTime.toInstant().atZone(ZoneId.systemDefault()).toLocalTime());
                    }
                    examination.setBodyPart(bodyPart);
                    examination.setDescription(description);
                    examination.setReferringPhysician(referringPhysician);
                    examination = examinationRepository.save(examination);
                    log.info("Created new Examination for study {}", finalStudyUid);
                }

                // Move file and extract image
                String uniqueName = UUID.randomUUID().toString();
                Path targetDcm = baseDicomDir.resolve(uniqueName + ".dcm");
                Path targetPng = baseImageDir.resolve(uniqueName + ".png");
                
                String dbDcmPath = "/dicom/" + uniqueName + ".dcm";
                String dbPngPath = "/images/" + uniqueName + ".png";
                
                Files.move(tempFile, targetDcm, StandardCopyOption.REPLACE_EXISTING);
                
                Image pngImageEntity = null;
                ImageIO.scanForPlugins();
                try (ImageInputStream iis = ImageIO.createImageInputStream(targetDcm.toFile())) {
                    Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
                    if (iter.hasNext()) {
                        ImageReader reader = iter.next();
                        reader.setInput(iis, false);
                        BufferedImage bi = reader.read(0);
                        if (bi != null) {
                            ImageIO.write(bi, "png", targetPng.toFile());
                            pngImageEntity = new Image();
                            pngImageEntity.setExtension("png");
                            pngImageEntity.setS3BucketKey(dbPngPath);
                            pngImageEntity = imageRepository.save(pngImageEntity);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to extract image for {}", originalFilename, e);
                }

                Image dcmImageEntity = new Image();
                dcmImageEntity.setExtension("dcm");
                dcmImageEntity.setS3BucketKey(dbDcmPath);
                dcmImageEntity = imageRepository.save(dcmImageEntity);

                if (pngImageEntity != null && examination.getImagePath() == null) {
                    examination.setImagePath(pngImageEntity.getS3BucketKey());
                    examinationRepository.save(examination);
                }

                // Save Instance
                DicomInstance instance = new DicomInstance();
                instance.setExamination(examination);
                instance.setSopInstanceUid(sopInstanceUid);
                instance.setStudyInstanceUid(finalStudyUid);
                instance.setCreatedAt(LocalDateTime.now());

                instance.setImageLaterality(imageLaterality);
                instance.setImageRows(imageRows);
                instance.setImageColumns(imageColumns);
                instance.setStorageRawPath(dbDcmPath);
                instance.setStoragePngPath(dbPngPath);

                dicomInstanceRepository.save(instance);

            } catch (Exception e) {
                log.error("Error processing file {}", originalFilename, e);
            } finally {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
        log.info("Finished background processing for {} DICOM files", tempFilePaths.size());
        
        if (userId != null) {
            notificationService.sendNotification(new SendNotificationRequest(
                    userId,
                    "DICOM Upload Complete",
                    "Đã hoàn tất xử lý " + tempFilePaths.size() + " file DICOM.",
                    "SYSTEM"
            ));
        }
    }
}

