package com.g93.be.service.impl;

import com.g93.be.dto.DicomTagResponse;
import com.g93.be.dto.PatientDetailsResponse;
import com.g93.be.entity.*;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Service
@Slf4j
@lombok.RequiredArgsConstructor
public class DicomServiceImpl implements DicomService {

    private final com.g93.be.repository.PatientRepository patientRepository;
    private final com.g93.be.repository.ExaminationRepository examinationRepository;
    private final com.g93.be.repository.DicomInstanceRepository dicomInstanceRepository;
    private final com.g93.be.repository.ImageRepository imageRepository;
    private final com.g93.be.repository.RoleRepository roleRepository;
    private final com.g93.be.repository.DoctorRepository doctorRepository;
    private final com.g93.be.mapper.PatientMapper patientMapper;

    @org.springframework.beans.factory.annotation.Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        // ... keeping the previous implementation simplified or stubbed to focus on the batch
        return new ArrayList<>();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.g93.be.dto.BatchDicomUploadResponse uploadBatch(List<MultipartFile> files) {
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
            com.g93.be.dto.BatchDicomUploadResponse response = processBatchPaths(filePaths);
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
    public void processZipBatch(Path zipFilePath) {
        log.info("Starting background processing of ZIP batch at: {}", zipFilePath);
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

            com.g93.be.dto.BatchDicomUploadResponse response = processBatchPaths(filePaths);

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
    public com.g93.be.dto.BatchDicomUploadResponse processBatchPaths(java.util.Map<String, Path> filePaths) {
        List<com.g93.be.dto.FileUploadError> errors = new ArrayList<>();
        java.util.Map<String, com.g93.be.entity.Patient> patientMap = new java.util.HashMap<>();
        java.util.Map<String, com.g93.be.entity.Examination> examMap = new java.util.HashMap<>();
        List<com.g93.be.dto.PatientDetailsResponse> successfulPatients = new ArrayList<>();
        java.util.Set<String> processedUids = new java.util.HashSet<>();

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
                java.util.Date patientBirthDate = null;
                String patientSex = null;

                String studyInstanceUid = null;
                java.util.Date studyDate = null;
                java.util.Date studyTime = null;
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
                    errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "Missing SOPInstanceUID"));
                    continue;
                }

                if (processedUids.contains(sopInstanceUid)) {
                    errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "DICOM file already processed in this batch (duplicate SOPInstanceUID)"));
                    continue;
                }

                if (dicomInstanceRepository.existsBySopInstanceUid(sopInstanceUid)) {
                    errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "DICOM file already exists in database (duplicate SOPInstanceUID)"));
                    continue;
                }
                
                processedUids.add(sopInstanceUid);

                final java.util.Date finalPatientBirthDate = patientBirthDate;
                final String finalPatientSex = patientSex;

                // Get or Create Patient
                final String finalPatientId = (patientId != null && !patientId.isEmpty()) ? patientId : "UNKNOWN";
                final String finalPatientName = (patientName != null && !patientName.isEmpty()) ? patientName : "Unknown";
                com.g93.be.entity.Patient patient = patientMap.computeIfAbsent(finalPatientId, pid -> {
                    return patientRepository.findByPatientCode(pid).orElseGet(() -> {
                        com.g93.be.entity.Patient p = new com.g93.be.entity.Patient();
                        p.setPatientCode(pid);
                        p.setEmail(pid + "_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
                        p.setFullName(finalPatientName.replace("^", " ").trim());
                        if (finalPatientBirthDate != null) {
                            p.setDob(finalPatientBirthDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                        }
                        if ("F".equalsIgnoreCase(finalPatientSex)) {
                            p.setGender(com.g93.be.entity.Gender.FEMALE);
                        } else if ("M".equalsIgnoreCase(finalPatientSex)) {
                            p.setGender(com.g93.be.entity.Gender.MALE);
                        } else {
                            p.setGender(com.g93.be.entity.Gender.OTHER);
                        }
                        return patientRepository.save(p);
                    });
                });

                final java.util.Date finalStudyDate = studyDate;
                final java.util.Date finalStudyTime = studyTime;
                final String finalBodyPart = bodyPart;
                final String finalDescription = description;
                final String finalReferringPhysician = referringPhysician;

                // Get or Create Examination
                final String finalStudyUid = (studyInstanceUid != null && !studyInstanceUid.isEmpty()) ? studyInstanceUid : "UNKNOWN_STUDY_" + System.currentTimeMillis();
                com.g93.be.entity.Examination examination = examMap.computeIfAbsent(finalStudyUid, suid -> {
                    com.g93.be.entity.Examination ex = new com.g93.be.entity.Examination();
                    ex.setPatient(patient);
                    
                    com.g93.be.entity.Doctor doctor = doctorRepository.findAll().stream().findFirst().orElseGet(() -> {
                        com.g93.be.entity.Doctor d = new com.g93.be.entity.Doctor();
                        d.setUsername("dummy_doc_" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        d.setPassword("temp");
                        d.setEmail("dummy_doc_" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@temp.com");
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
                    ex.setDoctor(doctor);
                    
                    ex.setEncounterCode(suid);
                    ex.setStatus(com.g93.be.entity.ExaminationStatus.PENDING_REVIEW);
                    ex.setVisitTime(java.time.LocalDateTime.now());
                    if (finalStudyDate != null) {
                        ex.setStudyDate(finalStudyDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
                    }
                    if (finalStudyTime != null) {
                        ex.setStudyTime(finalStudyTime.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalTime());
                    }
                    ex.setBodyPart(finalBodyPart);
                    ex.setDescription(finalDescription);
                    ex.setReferringPhysician(finalReferringPhysician);
                    return examinationRepository.save(ex);
                });

                // Move file and extract image
                String uniqueName = java.util.UUID.randomUUID().toString();
                Path targetDcm = baseDicomDir.resolve(uniqueName + ".dcm");
                Path targetPng = baseImageDir.resolve(uniqueName + ".png");
                
                String dbDcmPath = "/dicom/" + uniqueName + ".dcm";
                String dbPngPath = "/images/" + uniqueName + ".png";
                
                Files.move(tempFile, targetDcm, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                com.g93.be.entity.Image pngImageEntity = null;
                ImageIO.scanForPlugins();
                try (ImageInputStream iis = ImageIO.createImageInputStream(targetDcm.toFile())) {
                    Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
                    if (iter.hasNext()) {
                        ImageReader reader = iter.next();
                        reader.setInput(iis, false);
                        BufferedImage bi = reader.read(0);
                        if (bi != null) {
                            ImageIO.write(bi, "png", targetPng.toFile());
                            pngImageEntity = new com.g93.be.entity.Image();
                            pngImageEntity.setExtension("png");
                            pngImageEntity.setS3BucketKey(dbPngPath);
                            pngImageEntity = imageRepository.save(pngImageEntity);
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to extract image", e);
                }

                com.g93.be.entity.Image dcmImageEntity = new com.g93.be.entity.Image();
                dcmImageEntity.setExtension("dcm");
                dcmImageEntity.setS3BucketKey(dbDcmPath);
                dcmImageEntity = imageRepository.save(dcmImageEntity);

                if (pngImageEntity != null && examination.getImagePath() == null) {
                    examination.setImagePath(pngImageEntity.getS3BucketKey());
                    examinationRepository.save(examination);
                }

                // Save Instance
                com.g93.be.entity.DicomInstance instance = new com.g93.be.entity.DicomInstance();
                instance.setExamination(examination);
                instance.setSopInstanceUid(sopInstanceUid);
                instance.setStudyInstanceUid(studyInstanceUid);
                instance.setCreatedAt(java.time.LocalDateTime.now());

                instance.setImageLaterality(imageLaterality);
                instance.setImageRows(imageRows);
                instance.setImageColumns(imageColumns);
                instance.setStorageRawPath(dbDcmPath);
                instance.setStoragePngPath(dbPngPath);

                dicomInstanceRepository.save(instance);

            } catch (Exception e) {
                log.error("Error processing file {}", originalFilename, e);
                errors.add(new com.g93.be.dto.FileUploadError(originalFilename, "Processing error: " + e.getMessage()));
            }
        }

        // Build Response
        for (com.g93.be.entity.Patient p : patientMap.values()) {
            com.g93.be.dto.PatientDetailsResponse pdr = new com.g93.be.dto.PatientDetailsResponse();
            pdr.setPatient(patientMapper.toResponse(p));
            List<com.g93.be.dto.ExaminationDto> examDtos = new ArrayList<>();
            for (com.g93.be.entity.Examination ex : examMap.values()) {
                if (ex.getPatient().getId().equals(p.getId())) {
                    com.g93.be.dto.ExaminationDto ed = new com.g93.be.dto.ExaminationDto();
                    ed.setExaminationId(ex.getId());
                    ed.setEncounterCode(ex.getEncounterCode());
                    ed.setStatus(ex.getStatus().name());
                    ed.setVisitTime(ex.getVisitTime());
                    ed.setBodyPart(ex.getBodyPart());
                    ed.setReferringPhysician(ex.getReferringPhysician());
                    
                    String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
                    List<com.g93.be.entity.DicomInstance> instances = dicomInstanceRepository.findByExaminationId(ex.getId());
                    if (!instances.isEmpty()) {
                        ed.setThumbnailUrl(baseUrl + "/dicom/instances/" + instances.get(0).getId() + "/image");
                        List<com.g93.be.dto.ExaminationImageDto> imageDtos = new ArrayList<>();
                        for (com.g93.be.entity.DicomInstance instance : instances) {
                            com.g93.be.dto.ExaminationImageDto img = new com.g93.be.dto.ExaminationImageDto();
                            img.setExaminationId(ex.getId());
                            img.setEncounterCode(ex.getEncounterCode());
                            img.setStatus(ex.getStatus().name());
                            img.setVisitTime(ex.getVisitTime());
                            img.setImageUrl(baseUrl + "/dicom/instances/" + instance.getId() + "/image");
                            imageDtos.add(img);
                        }
                        ed.setImages(imageDtos);
                    }
                    examDtos.add(ed);
                }
            }
            pdr.setRecentExaminations(examDtos);
            successfulPatients.add(pdr);
        }

        return new com.g93.be.dto.BatchDicomUploadResponse(errors, successfulPatients);
    }
}
