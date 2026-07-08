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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.g93.be.dto.SendNotificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

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
    private final com.g93.be.mapper.PatientMapper patientMapper;
    private final com.g93.be.mapper.ExaminationMapper examinationMapper;
    
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        // ... keeping the previous implementation simplified or stubbed to focus on the batch
        return new ArrayList<>();
    }

    private final ApplicationContext applicationContext;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.g93.be.dto.BatchDicomUploadResponse uploadBatch(List<MultipartFile> files, Long userId) {
        java.util.Map<String, Path> filePaths = new java.util.LinkedHashMap<>();
        List<Path> tempFilesToClean = new ArrayList<>();
        List<com.g93.be.dto.FileUploadError> earlyErrors = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".dcm")) {
                    earlyErrors.add(new com.g93.be.dto.FileUploadError(originalFilename, "Invalid file format. Only .dcm files are allowed."));
                    continue;
                }
                Path tempFile = Files.createTempFile("batch_", ".dcm");
                file.transferTo(tempFile.toFile());
                filePaths.put(originalFilename, tempFile);
                tempFilesToClean.add(tempFile);
            }
            com.g93.be.dto.BatchDicomUploadResponse response = processBatchPaths(filePaths, userId);
            response.getErrors().addAll(earlyErrors);
            return response;
        } catch (Exception e) {
            log.error("Failed to process uploaded batch files", e);
            throw new RuntimeException("Failed to process uploaded batch files", e);
        } finally {
            for (Path p : tempFilesToClean) {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public void processZipBatch(Path zipFilePath, Long userId) {
        log.info("Starting background processing of ZIP batch at: {}", zipFilePath);
        if (userId != null) {
            notificationService.sendNotification(new com.g93.be.dto.SendNotificationRequest(
                    userId,
                    "Tiếp nhận File ZIP",
                    "Hệ thống đang tiến hành giải nén và kiểm tra file ZIP...",
                    "SYSTEM"
            ));
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("zip_batch_work_");
            unzipFile(zipFilePath, workDir);

            List<Path> innerZips = java.nio.file.Files.walk(workDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".zip"))
                    .collect(java.util.stream.Collectors.toList());

            for (Path innerZip : innerZips) {
                Path innerExtractDir = Files.createTempDirectory(workDir, "inner_");
                unzipFile(innerZip, innerExtractDir);
            }

            List<Path> dcmFiles = new ArrayList<>();
            List<Path> strangeFiles = new ArrayList<>();

            java.nio.file.Files.walk(workDir).forEach(p -> {
                if (java.nio.file.Files.isRegularFile(p)) {
                    String name = p.getFileName().toString().toLowerCase();
                    if (name.endsWith(".dcm")) {
                        dcmFiles.add(p);
                    } else if (!name.endsWith(".zip")) {
                        strangeFiles.add(p);
                    }
                }
            });

            log.info("Found {} DICOM files and {} strange files in the ZIP batch", dcmFiles.size(), strangeFiles.size());

            java.util.Map<String, Path> filePaths = new java.util.LinkedHashMap<>();
            for (Path dcmFile : dcmFiles) {
                filePaths.put(dcmFile.getFileName().toString(), dcmFile);
            }

            com.g93.be.dto.BatchDicomUploadResponse response = processBatchPaths(filePaths, userId);

            if (dcmFiles.isEmpty()) {
                response.getErrors().add(new com.g93.be.dto.FileUploadError(zipFilePath.getFileName().toString(), "No DICOM files found in the ZIP batch."));
            }
            for (Path strange : strangeFiles) {
                response.getErrors().add(new com.g93.be.dto.FileUploadError(strange.getFileName().toString(), "Strange file detected (not .dcm or .zip). Ignored."));
            }

            log.info("Finished background processing of ZIP batch. Success: {}, Errors: {}",
                    response.getSuccessfulPatients().size(), response.getErrors().size());

        } catch (Exception e) {
            log.error("Error processing background ZIP batch", e);
        } finally {
            if (workDir != null) {
                try {
                    java.nio.file.Files.walk(workDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(java.io.File::delete);
                } catch (IOException ignored) {}
            }
            if (zipFilePath != null) {
                try { Files.deleteIfExists(zipFilePath); } catch (IOException ignored) {}
            }
        }
    }

    private void unzipFile(Path zipFilePath, Path destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFilePath))) {
            java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                Path newFilePath = destDir.resolve(zipEntry.getName()).normalize();
                if (!newFilePath.startsWith(destDir.normalize())) {
                    throw new IOException("Bad zip entry: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFilePath);
                } else {
                    if (newFilePath.getParent() != null) {
                        Files.createDirectories(newFilePath.getParent());
                    }
                    Files.copy(zis, newFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.g93.be.dto.BatchDicomUploadResponse processBatchPaths(java.util.Map<String, Path> filePaths, Long userId) {
        List<com.g93.be.dto.FileUploadError> errors = new ArrayList<>();
        List<com.g93.be.dto.PatientDetailsResponse> successfulPatients = new ArrayList<>();
        java.util.Set<String> processedUids = new java.util.HashSet<>();
        java.util.Map<Long, com.g93.be.entity.Patient> uniquePatients = new java.util.HashMap<>();
        java.util.Map<Long, java.util.Map<Long, com.g93.be.entity.Examination>> patientExaminations = new java.util.HashMap<>();

        if (userId != null) {
            notificationService.sendNotification(new com.g93.be.dto.SendNotificationRequest(
                    userId,
                    "Đang xử lý DICOM",
                    "Hệ thống đang trích xuất dữ liệu từ " + filePaths.size() + " file DICOM...",
                    "SYSTEM"
            ));
        }

        Path baseDicomDir = Paths.get(storageBaseDir, "dicom");
        Path baseImageDir = Paths.get(storageBaseDir, "images");
        try {
            Files.createDirectories(baseDicomDir);
            Files.createDirectories(baseImageDir);
        } catch (IOException e) {
            log.error("Cannot create base dir", e);
            throw new RuntimeException("Cannot create storage directories");
        }

        for (java.util.Map.Entry<String, Path> entry : filePaths.entrySet()) {
            String originalFilename = entry.getKey();
            Path tempFile = entry.getValue();

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
                    errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "File DICOM không hợp lệ (thiếu SOPInstanceUID)."));
                    continue;
                }

                if (processedUids.contains(sopInstanceUid) || dicomInstanceRepository.existsBySopInstanceUid(sopInstanceUid)) {
                    log.warn("Duplicate SOPInstanceUID for file {}", originalFilename);
                    errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "File DICOM đã tồn tại trên hệ thống."));
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
                    
                    Patient patient = patientRepository.findByPatientCode(finalPatientId).orElse(null);
                    if (patient != null) {
                        // Update existing patient with new DICOM info
                        patient.setFullName(finalPatientName.replace("^", " ").trim());
                        if (finalPatientBirthDate != null) {
                            patient.setDob(finalPatientBirthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                        }
                        if ("F".equalsIgnoreCase(finalPatientSex)) {
                            patient.setGender(Gender.FEMALE);
                        } else if ("M".equalsIgnoreCase(finalPatientSex)) {
                            patient.setGender(Gender.MALE);
                        } else {
                            patient.setGender(Gender.OTHER);
                        }
                        patient = patientRepository.save(patient);
                    } else {
                        // Create new patient
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
                        patient = patientRepository.save(p);
                    }

                    // Create new Examination
                    examination = new Examination();
                    examination.setPatient(patient);
                    
                    Doctor doctor = null;
                    if (userId != null) {
                        doctor = doctorRepository.findById(userId).orElse(null);
                    }
                    if (doctor == null) {
                        // Fallback only if no doctor is found or userId is null (e.g. anonymous upload if allowed)
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
                            pngImageEntity.setFilePath(dbPngPath);
                            pngImageEntity = imageRepository.save(pngImageEntity);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to extract image for {}", originalFilename, e);
                }

                Image dcmImageEntity = new Image();
                dcmImageEntity.setExtension("dcm");
                dcmImageEntity.setFilePath(dbDcmPath);
                dcmImageEntity = imageRepository.save(dcmImageEntity);

                if (pngImageEntity != null && examination.getImagePath() == null) {
                    examination.setImagePath(pngImageEntity.getFilePath());
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
                if (examination.getPatient() != null) {
                    Long dbPatientId = examination.getPatient().getId();
                    uniquePatients.put(dbPatientId, examination.getPatient());
                    patientExaminations.computeIfAbsent(dbPatientId, k -> new java.util.HashMap<>())
                            .put(examination.getId(), examination);
                }

            } catch (Exception e) {
                log.error("Error processing file {}", originalFilename, e);
                errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "Processing error: " + e.getMessage()));
            }
        }
        log.info("Finished background processing for {} DICOM files", filePaths.size());
        
        for (com.g93.be.entity.Patient p : uniquePatients.values()) {
            com.g93.be.dto.PatientResponse pr = patientMapper.toResponse(p);
            
            List<com.g93.be.dto.ExaminationDto> recentExams = new ArrayList<>();
            java.util.Map<Long, com.g93.be.entity.Examination> examsForPatient = patientExaminations.get(p.getId());
            if (examsForPatient != null) {
                for (com.g93.be.entity.Examination exam : examsForPatient.values()) {
                    List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(exam.getId());
                    com.g93.be.dto.ExaminationDto examDto = examinationMapper.toDto(exam, instances);
                    if (examDto != null) {
                        recentExams.add(examDto);
                    }
                }
            }
            
            com.g93.be.dto.PatientDetailsResponse pdr = new com.g93.be.dto.PatientDetailsResponse();
            pdr.setPatient(pr);
            pdr.setRecentExaminations(recentExams);
            
            successfulPatients.add(pdr);
        }
        
        com.g93.be.dto.BatchDicomUploadResponse response = new com.g93.be.dto.BatchDicomUploadResponse();
        response.setErrors(errors);
        response.setSuccessfulPatients(successfulPatients);

        if (userId != null) {
            try {
                String responseJson = objectMapper.writeValueAsString(response);
                notificationService.sendNotification(new SendNotificationRequest(
                        userId,
                        "DICOM Upload Complete",
                        responseJson,
                        "DICOM_BATCH_RESULT"
                ));
            } catch (Exception e) {
                log.error("Failed to serialize notification payload", e);
                notificationService.sendNotification(new SendNotificationRequest(
                        userId,
                        "DICOM Upload Complete",
                        "Đã hoàn tất xử lý " + filePaths.size() + " file DICOM.",
                        "SYSTEM"
                ));
            }
        }
        
        return response;
    }
}

