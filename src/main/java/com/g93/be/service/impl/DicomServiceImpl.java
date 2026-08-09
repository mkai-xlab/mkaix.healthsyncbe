package com.g93.be.service.impl;

import com.fasterxml.jackson.databind.SerializationFeature;

import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.dto.FileUploadError;
import com.g93.be.dto.PatientDetailsResponse;
import com.g93.be.dto.PendingDicomUploadDTO;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.PatientResponse;
import com.g93.be.dto.ExaminationDto;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import com.g93.be.dto.SendNotificationRequest;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.dcm4che3.data.Tag;

@Service
@Slf4j
@RequiredArgsConstructor
public class DicomServiceImpl implements DicomService {

    private final DicomInstanceRepository dicomInstanceRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataSource dataSource;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${app.storage.base-dir:D:/Capstone/data}")
    private String storageBaseDir;

    @Override
    public List<DicomTagResponse> extractMetadata(MultipartFile file) {
        // ... keeping the previous implementation simplified or stubbed to focus on the
        // batch
        return new ArrayList<>();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public BatchDicomUploadResponse uploadBatch(List<MultipartFile> files, Long userId) {
        Map<String, Path> filePaths = new LinkedHashMap<>();
        List<Path> tempFilesToClean = new ArrayList<>();
        List<FileUploadError> earlyErrors = new ArrayList<>();
        long totalSize = 0;
        try {
            for (MultipartFile file : files) {
                totalSize += file.getSize();
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".dcm")) {
                    earlyErrors.add(
                            new FileUploadError(originalFilename, "Invalid file format. Only .dcm files are allowed."));
                    continue;
                }
                Path tempFile = Files.createTempFile("batch_", ".dcm");
                file.transferTo(tempFile.toFile());

                if (!isDicomFile(tempFile)) {
                    earlyErrors.add(new FileUploadError(originalFilename,
                            "Tệp tin không đúng định dạng DICOM hoặc bị lỗi cấu trúc."));
                    Files.deleteIfExists(tempFile);
                    continue;
                }

                filePaths.put(originalFilename, tempFile);
                tempFilesToClean.add(tempFile);
            }
            String uploadSessionId = UUID.randomUUID().toString();
            BatchDicomUploadResponse response = processBatchPaths(filePaths, userId, uploadSessionId);
            response.getErrors().addAll(earlyErrors);

            if (response.getSuccessfulPatients().isEmpty()) {
                response.setMessage(
                        "Tải lên thất bại hoặc không có file nào được thêm mới. Vui lòng xem chi tiết lỗi bên dưới.");
            }

            saveAuditLog(userId, "DICOM Batch Upload",
                    "Uploaded " + files.size() + " files (" + totalSize + " bytes). Success: "
                            + response.getSuccessfulPatients().size() + ", Errors: " + response.getErrors().size());

            return response;
        } catch (Exception e) {
            saveAuditLog(userId, "DICOM Batch Upload Failed", "Error processing batch upload: " + e.getMessage());
            log.error("Failed to process uploaded batch files", e);
            throw new RuntimeException("Failed to process uploaded batch files", e);
        } finally {
            for (Path p : tempFilesToClean) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void saveAuditLog(Long userId, String title, String description) {
        if (userId != null && auditLogRepository != null) {
            try {
                User user = userRepository.findById(userId).orElse(null);
                AuditLog log = new AuditLog();
                log.setUser(user);
                log.setTitle(title);
                log.setDescription(description);
                log.setTimeStamp(LocalDateTime.now());
                auditLogRepository.save(log);
            } catch (Exception e) {
                log.error("Failed to save audit log", e);
            }
        }
    }

    private Long resolveUserId(String username) {
        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                return user.getId();
            }
        }
        return 1L; // Fallback
    }

    /**
     * Xử lý tải lên một mẻ nhiều file DICOM độc lập.
     * 
     * @param files    Danh sách các file DICOM được gửi từ client.
     * @param username Tên đăng nhập của người dùng thực hiện tải lên.
     * @return BatchDicomUploadResponse chứa thông tin uploadSessionId và kết quả
     *         phân tích sơ bộ.
     */
    @Override
    public BatchDicomUploadResponse uploadBatchFiles(List<MultipartFile> files, String username) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files list is empty");
        }
        Long userId = resolveUserId(username);
        return uploadBatch(files, userId);
    }

    /**
     * Xử lý tải lên một file nén (.zip) chứa nhiều file DICOM bên trong.
     * 
     * @param file     File nén (.zip) chứa các file DICOM.
     * @param username Tên đăng nhập của bác sĩ đang thực hiện thao tác.
     * @return BatchDicomUploadResponse chứa uploadSessionId để frontend có thể theo
     *         dõi.
     */
    @Override
    public BatchDicomUploadResponse uploadZipBatchFiles(java.util.List<MultipartFile> files, String username) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Uploaded files are empty");
        }
        Long userId = resolveUserId(username);
        long totalSize = 0;
        int errorCount = 0;
        try {
            List<Path> tempZipFiles = new ArrayList<>();
            List<FileUploadError> earlyErrors = new ArrayList<>();
            for (MultipartFile file : files) {
                totalSize += file.getSize();
                String filename = file.getOriginalFilename();
                if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
                    earlyErrors.add(new FileUploadError(filename,
                            "Invalid file format. Only .zip files are allowed for batch upload."));
                    continue;
                }
                Path tempZipFile = Files.createTempFile("main_batch_", ".zip");
                file.transferTo(tempZipFile.toFile());
                if (!isZipFile(tempZipFile)) {
                    Files.deleteIfExists(tempZipFile);
                    earlyErrors.add(
                            new FileUploadError(filename, "Tệp tin không đúng định dạng ZIP hoặc bị lỗi cấu trúc."));
                    continue;
                }
                tempZipFiles.add(tempZipFile);
            }

            if (tempZipFiles.isEmpty() && !earlyErrors.isEmpty()) {
                BatchDicomUploadResponse errRes = new BatchDicomUploadResponse();
                errRes.setMessage("Tải lên thất bại. Toàn bộ tệp tin không đúng định dạng DICOM hoặc bị lỗi cấu trúc.");
                errRes.setErrors(earlyErrors);
                errRes.setSuccessfulPatients(new ArrayList<>());
                saveAuditLog(userId, "DICOM ZIP Upload Failed", "All uploaded files are invalid/corrupted.");
                return errRes;
            }

            String uploadSessionId = UUID.randomUUID().toString();
            BatchDicomUploadResponse response = processMultipleZipBatches(tempZipFiles, userId, uploadSessionId);
            response.getErrors().addAll(earlyErrors);

            if (response.getSuccessfulPatients().isEmpty()) {
                response.setMessage(
                        "Tải lên thất bại hoặc không có file nào được thêm mới. Vui lòng xem chi tiết lỗi bên dưới.");
            }

            saveAuditLog(userId, "DICOM ZIP Upload",
                    "Uploaded " + files.size() + " ZIP files (" + totalSize + " bytes). Success: "
                            + response.getSuccessfulPatients().size() + ", Errors: " + response.getErrors().size());

            return response;
        } catch (Exception e) {
            saveAuditLog(userId, "DICOM ZIP Upload Failed", "Error saving uploaded ZIP files: " + e.getMessage());
            log.error("Failed to save uploaded ZIP files", e);
            throw new RuntimeException("Failed to save uploaded ZIP files", e);
        }
    }

    @Override
    public Resource getInstanceImageResource(Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getImage() != null && instance.getImage().getFilePath() != null) {
            String imagePath = instance.getImage().getFilePath();
            try {
                String relPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                }
            } catch (Exception e) {
                log.error("Failed to read image", e);
            }
        }
        return null;
    }

    @Override
    public Resource getInstanceRawResource(Long id) {
        DicomInstance instance = dicomInstanceRepository.findById(id).orElse(null);
        if (instance != null && instance.getDicomRaw() != null && instance.getDicomRaw().getFilePath() != null) {
            String rawPath = instance.getDicomRaw().getFilePath();
            try {
                String relPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
                Path path = Paths.get(storageBaseDir, relPath);
                Resource resource = new UrlResource(path.toUri());
                if (resource.exists() || resource.isReadable()) {
                    return resource;
                }
            } catch (Exception e) {
                log.error("Failed to read raw dicom", e);
            }
        }
        return null;
    }

    @Override
    public BatchDicomUploadResponse processMultipleZipBatches(List<Path> zipFilePaths, Long userId,
            String uploadSessionId) {
        log.info("Starting background processing of {} ZIP batches", zipFilePaths.size());
        if (userId != null) {
            notificationService.sendNotification(new SendNotificationRequest(
                    userId,
                    "Tiếp nhận File ZIP",
                    "Hệ thống đang tiến hành giải nén và kiểm tra " + zipFilePaths.size() + " file ZIP...",
                    "SYSTEM",
                    null));
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("zip_batch_work_");

            for (Path zipFilePath : zipFilePaths) {
                unzipFile(zipFilePath, workDir);
            }

            List<Path> innerZips = Files.walk(workDir)
                    .filter(p -> p.toString().toLowerCase().endsWith(".zip"))
                    .collect(Collectors.toList());

            for (Path innerZip : innerZips) {
                Path innerExtractDir = Files.createTempDirectory(workDir, "inner_");
                unzipFile(innerZip, innerExtractDir);
            }

            List<Path> dcmFiles = new ArrayList<>();
            List<Path> strangeFiles = new ArrayList<>();

            Files.walk(workDir).forEach(p -> {
                if (Files.isRegularFile(p)) {
                    String name = p.getFileName().toString().toLowerCase();
                    if (name.endsWith(".dcm")) {
                        dcmFiles.add(p);
                    } else if (!name.endsWith(".zip")) {
                        strangeFiles.add(p);
                    }
                }
            });

            log.info("Found {} DICOM files and {} strange files in the ZIP batches", dcmFiles.size(),
                    strangeFiles.size());

            Map<String, Path> filePaths = new LinkedHashMap<>();
            for (Path dcmFile : dcmFiles) {
                filePaths.put(dcmFile.getFileName().toString(), dcmFile);
            }

            BatchDicomUploadResponse response = processBatchPaths(filePaths, userId, uploadSessionId);

            if (dcmFiles.isEmpty()) {
                response.getErrors().add(new FileUploadError("multiple_zips",
                        "No DICOM files found in the ZIP batches."));
            }
            for (Path strange : strangeFiles) {
                response.getErrors().add(new FileUploadError(strange.getFileName().toString(),
                        "Strange file detected (not .dcm or .zip). Ignored."));
            }

            log.info("Finished background processing of ZIP batches. Success: {}, Errors: {}",
                    response.getSuccessfulPatients().size(), response.getErrors().size());
            return response;

        } catch (Exception e) {
            log.error("Error processing background ZIP batches", e);
            throw new RuntimeException("Error processing background ZIP batches", e);
        } finally {
            if (workDir != null) {
                try {
                    Files.walk(workDir)
                            .sorted(Comparator.reverseOrder())
                            .map(p -> p.toFile())
                            .forEach(f -> f.delete());
                } catch (IOException ignored) {
                }
            }
            if (zipFilePaths != null) {
                for (Path zipFilePath : zipFilePaths) {
                    try {
                        Files.deleteIfExists(zipFilePath);
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private void unzipFile(Path zipFilePath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
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
                    Files.copy(zis, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public BatchDicomUploadResponse processBatchPaths(Map<String, Path> filePaths, Long userId,
            String uploadSessionId) {
        List<FileUploadError> errors = new ArrayList<>();
        List<PatientDetailsResponse> successfulPatients = new ArrayList<>();
        Set<String> processedUids = new HashSet<>();

        Map<String, PendingDicomUploadDTO> patientsMap = new HashMap<>();

        try {

            if (userId != null) {
                notificationService.sendNotification(new SendNotificationRequest(
                        userId,
                        "Đang xử lý DICOM",
                        "Hệ thống đang trích xuất dữ liệu từ " + filePaths.size() + " file DICOM...",
                        "SYSTEM",
                        null));
            }

            Path baseDicomDir = Paths.get(storageBaseDir, "dicom");
            Path baseImageDir = Paths.get(storageBaseDir, "images", "raw_dicom_image");
            try {
                Files.createDirectories(baseDicomDir);
                Files.createDirectories(baseImageDir);
            } catch (IOException e) {
                log.error("Cannot create base dir", e);
                throw new RuntimeException("Cannot create storage directories");
            }

            for (Map.Entry<String, Path> entry : filePaths.entrySet()) {
                String originalFilename = entry.getKey();
                Path tempFile = entry.getValue();

                try {
                    if (!isDicomFile(tempFile)) {
                        errors.add(new FileUploadError(originalFilename,
                                "Tệp tin không đúng định dạng DICOM hoặc bị lỗi cấu trúc."));
                        continue;
                    }

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
                        patientId = attrs.getString(Tag.PatientID, "");
                        patientName = attrs.getString(Tag.PatientName, "");
                        patientBirthDate = attrs.getDate(Tag.PatientBirthDate);
                        patientSex = attrs.getString(Tag.PatientSex, "");

                        studyInstanceUid = attrs.getString(Tag.StudyInstanceUID, "");
                        studyDate = attrs.getDate(Tag.StudyDate);
                        studyTime = attrs.getDate(Tag.StudyTime);
                        description = attrs.getString(Tag.StudyDescription, "");
                        bodyPart = attrs.getString(Tag.BodyPartExamined, "");
                        referringPhysician = attrs.getString(Tag.ReferringPhysicianName, "");

                        sopInstanceUid = attrs.getString(Tag.SOPInstanceUID, "");
                    }

                    if (sopInstanceUid == null || sopInstanceUid.isEmpty()) {
                        log.warn("Missing SOPInstanceUID for file {}", originalFilename);
                        errors.add(new FileUploadError(originalFilename,
                                "File DICOM không hợp lệ (thiếu SOPInstanceUID)."));
                        continue;
                    }

                    if (processedUids.contains(sopInstanceUid)
                            || dicomInstanceRepository.existsBySopInstanceUid(sopInstanceUid)) {
                        log.warn("Duplicate SOPInstanceUID for file {}", originalFilename);
                        errors.add(new FileUploadError(originalFilename, "File DICOM đã tồn tại trên hệ thống."));
                        continue;
                    }

                    processedUids.add(sopInstanceUid);

                    final String finalPatientId = (patientId != null && !patientId.isEmpty()) ? patientId : "UNKNOWN";

                    String dateStr = "nodate";
                    if (studyDate != null) {
                        dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(studyDate);
                    } else {
                        dateStr = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
                    }
                    String groupKey = finalPatientId + "_" + dateStr;

                    PendingDicomUploadDTO pendingUpload = patientsMap.get(groupKey);
                    if (pendingUpload == null) {
                        pendingUpload = PendingDicomUploadDTO.builder()
                                .patientCode(finalPatientId)
                                .patientName(patientName)
                                .patientBirthDate(patientBirthDate)
                                .patientSex(patientSex)
                                .studyInstanceUid(studyInstanceUid)
                                .studyDate(studyDate)
                                .studyTime(studyTime)
                                .description(description)
                                .referringPhysician(referringPhysician)
                                .physicalFilePaths(new HashMap<>())
                                .parsedImages(new ArrayList<>())
                                .parsedInstances(new ArrayList<>())
                                .build();
                        patientsMap.put(groupKey, pendingUpload);
                    }

                    // Move file and extract image
                    String uniqueName = UUID.randomUUID().toString();
                    Path targetDcm = baseDicomDir.resolve(uniqueName + ".dcm");
                    Path targetPng = baseImageDir.resolve(uniqueName + ".png");

                    String dbDcmPath = "/dicom/" + uniqueName + ".dcm";
                    String dbPngPath = "/images/raw_dicom_image/" + uniqueName + ".png";

                    Files.move(tempFile, targetDcm, StandardCopyOption.REPLACE_EXISTING);

                    // Store physical file paths for potential cleanup
                    pendingUpload.getPhysicalFilePaths().put(dbDcmPath, targetDcm.toAbsolutePath().toString());

                    boolean hasPng = false;
                    ImageIO.scanForPlugins();
                    try (ImageInputStream iis = ImageIO.createImageInputStream(targetDcm.toFile())) {
                        Iterator<ImageReader> iter = ImageIO.getImageReadersByFormatName("DICOM");
                        if (iter.hasNext()) {
                            ImageReader reader = iter.next();
                            reader.setInput(iis, false);
                            BufferedImage bi = reader.read(0);
                            if (bi != null) {
                                ImageIO.write(bi, "png", targetPng.toFile());
                                pendingUpload.getPhysicalFilePaths().put(dbPngPath,
                                        targetPng.toAbsolutePath().toString());

                                pendingUpload.getParsedImages().add(PendingDicomUploadDTO.ImageCacheDTO.builder()
                                        .sopInstanceUid(sopInstanceUid)
                                        .originalFilename(originalFilename)
                                        .storedFilePath(dbPngPath)
                                        .mimeType("image/png")
                                        .build());
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to extract image for {}", originalFilename, e);
                    }

                    pendingUpload.getParsedImages().add(PendingDicomUploadDTO.ImageCacheDTO.builder()
                            .sopInstanceUid(sopInstanceUid)
                            .originalFilename(originalFilename)
                            .storedFilePath(dbDcmPath)
                            .mimeType("application/dicom")
                            .build());

                    pendingUpload.getParsedInstances().add(PendingDicomUploadDTO.InstanceCacheDTO.builder()
                            .sopInstanceUid(sopInstanceUid)
                            .filePath(dbDcmPath)
                            .bodyPart(bodyPart)
                            .build());

                } catch (Exception e) {
                    log.error("Error processing file {}", originalFilename, e);
                    errors.add(new FileUploadError(originalFilename, "Processing error: " + e.getMessage()));
                }
            }
            log.info("Finished background processing for {} DICOM files, mapping to DTOs", filePaths.size());

            // Cache session to Redis
            try {
                DicomUploadSessionDTO sessionDTO = DicomUploadSessionDTO.builder()
                        .uploadSessionId(uploadSessionId)
                        .uploaderUserId(userId)
                        .patients(patientsMap)
                        .errors(errors)
                        .createdAt(System.currentTimeMillis())
                        .build();

                String json = objectMapper.writeValueAsString(sessionDTO);
                stringRedisTemplate.opsForValue().set("uploadSession:" + uploadSessionId, json,
                        Duration.ofMinutes(15));
                stringRedisTemplate.opsForZSet().add("uploadSessionTimeouts", uploadSessionId,
                        System.currentTimeMillis());
                log.info("Saved upload session {} to Redis", uploadSessionId);

                // Build temporary responses for the user to review
                for (PendingDicomUploadDTO pending : patientsMap.values()) {
                    PatientResponse pr = new PatientResponse();
                    pr.setPatientCode(pending.getPatientCode());
                    pr.setFullName(pending.getPatientName() != null ? pending.getPatientName().replace("^", " ").trim()
                            : "Unknown");
                    if (pending.getPatientBirthDate() != null) {
                        pr.setDateOfBirth(Instant.ofEpochMilli(pending.getPatientBirthDate().getTime())
                                .atZone(ZoneId.systemDefault()).toLocalDate());
                    }
                    if ("F".equalsIgnoreCase(pending.getPatientSex())) {
                        pr.setGender(Gender.FEMALE);
                    } else if ("M".equalsIgnoreCase(pending.getPatientSex())) {
                        pr.setGender(Gender.MALE);
                    } else {
                        pr.setGender(Gender.OTHER);
                    }

                    ExaminationDto examDto = new ExaminationDto();
                    examDto.setEncounterCode(pending.getStudyInstanceUid());
                    examDto.setDescription(pending.getDescription());
                    examDto.setReferringPhysician(pending.getReferringPhysician());
                    examDto.setStatus(ExaminationStatus.AI_PROCESSING.name());
                    if (pending.getStudyDate() != null) {
                        examDto.setStudyDate(Instant.ofEpochMilli(pending.getStudyDate().getTime())
                                .atZone(ZoneId.systemDefault()).toLocalDate());
                    }
                    if (pending.getStudyTime() != null) {
                        examDto.setStudyTime(Instant.ofEpochMilli(pending.getStudyTime().getTime())
                                .atZone(ZoneId.systemDefault()).toLocalTime());
                    }

                    PatientDetailsResponse pdr = new PatientDetailsResponse();
                    pdr.setPatient(pr);
                    pdr.setRecentExaminations(Collections.singletonList(examDto));
                    successfulPatients.add(pdr);
                }
            } catch (Exception e) {
                log.error("Failed to cache upload session", e);
                String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                throw new RuntimeException(
                        "Failed to cache upload session: " + e.getClass().getSimpleName() + " - " + rootCause, e);
            }

            BatchDicomUploadResponse response = new BatchDicomUploadResponse();
            response.setUploadSessionId(uploadSessionId);
            response.setErrors(errors);
            response.setSuccessfulPatients(successfulPatients);

            if (userId != null) {
                try {
                    if (successfulPatients.isEmpty()) {
                        notificationService.sendNotification(new SendNotificationRequest(
                                userId,
                                "Tải lên DICOM thất bại",
                                "Toàn bộ file tải lên bị lỗi hoặc không chứa file DICOM hợp lệ. Vui lòng thử lại.",
                                "SYSTEM",
                                null));
                    } else if (!errors.isEmpty()) {
                        notificationService.sendNotification(new SendNotificationRequest(
                                userId,
                                "Tải lên DICOM hoàn tất (có lỗi)",
                                "Đã xử lý xong nhưng có " + errors.size()
                                        + " file bị lỗi. Vui lòng xem chi tiết trên giao diện.",
                                "DICOM_BATCH_RESULT",
                                null));
                    } else {
                        notificationService.sendNotification(new SendNotificationRequest(
                                userId,
                                "Tải lên DICOM hoàn tất",
                                "Quá trình tải lên ảnh DICOM đã hoàn tất trọn vẹn.",
                                "DICOM_BATCH_RESULT",
                                null));
                    }
                } catch (Exception e) {
                    log.error("Failed to send notification", e);
                }
            }

            return response;

        } catch (Exception globalEx) {
            log.error("Fatal error during background DICOM processing", globalEx);
            if (userId != null) {
                notificationService.sendNotification(new SendNotificationRequest(
                        userId,
                        "Lỗi xử lý DICOM",
                        "Đã xảy ra lỗi nghiêm trọng: " + globalEx.getMessage(),
                        "SYSTEM",
                        null));
            }
            throw new RuntimeException("Background processing failed", globalEx);
        }
    }

    @Override
    public String getUploadSession(String sessionId) {
        return stringRedisTemplate.opsForValue().get("uploadSession:" + sessionId);
    }

    protected boolean isDicomFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            is.skip(128);
            byte[] b = new byte[4];
            int read = is.read(b);
            if (read == 4) {
                String magic = new String(b, StandardCharsets.US_ASCII);
                return "DICM".equals(magic);
            }
        } catch (Exception e) {
            log.error("Failed to read magic bytes for DICOM: {}", path, e);
        }
        return false;
    }

    protected boolean isZipFile(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] b = new byte[4];
            int read = is.read(b);
            if (read >= 2) {
                return b[0] == 0x50 && b[1] == 0x4B;
            }
        } catch (Exception e) {
            log.error("Failed to read magic bytes for ZIP: {}", path, e);
        }
        return false;
    }
}
