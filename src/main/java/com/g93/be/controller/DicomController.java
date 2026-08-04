package com.g93.be.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.dto.DicomUploadSessionDTO;
import com.g93.be.dto.DicomTagResponse;
import com.g93.be.entity.User;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.DicomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.security.Principal;

/**
 * Controller for DICOM file operations.
 */
@RestController
@RequestMapping("/dicom")
@RequiredArgsConstructor
@Slf4j
public class DicomController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final DicomService dicomService;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final UserRepository userRepository;

    /**
     * Uploads a DICOM file and returns its extracted metadata.
     *
     * @param file The multipart DICOM file.
     * @return A list of extracted DICOM tags.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('UPLOAD_DICOM_IMAGE'))")
    public ResponseEntity<List<DicomTagResponse>> uploadDicomFile(@RequestParam("file") MultipartFile file) {
        log.info("Received request to upload DICOM file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        List<DicomTagResponse> metadata = dicomService.extractMetadata(file);
        log.info("Successfully extracted {} tags from DICOM file.", metadata.size());

        return ResponseEntity.ok(metadata);
    }

    @PostMapping(value = "/upload/batch", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('UPLOAD_DICOM_IMAGE'))")
    public ResponseEntity<BatchDicomUploadResponse> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            Principal principal) {
        log.info("Received request to upload batch of {} DICOM files", files.size());
        if (principal == null || principal.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated user was not found");
        }
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Authenticated user was not found"));
        BatchDicomUploadResponse response = dicomService.uploadBatchFiles(files, user.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload/zip-batch", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('UPLOAD_DICOM_IMAGE'))")
    public ResponseEntity<?> uploadZipBatch(
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        log.info("Received request to upload ZIP batch DICOM file");
        try {
            if (principal == null || principal.getName() == null) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Authenticated user was not found");
            }
            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                            "Authenticated user was not found"));
            BatchDicomUploadResponse response = dicomService.uploadZipBatchFile(file, user.getUsername());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errResponse = new HashMap<>();
            errResponse.put("error", e.getMessage());
            errResponse.put("status", "FAILED");
            return ResponseEntity.badRequest().body(errResponse);
        }
    }



    @GetMapping("/total-studies")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('VIEW_ANALYTIC_HISTORY'))")
    public ResponseEntity<Long> getTotalStudies() {
        log.info("Received request to get total unique DICOM studies");
        return ResponseEntity.ok(dicomInstanceRepository.countUniqueStudies());
    }

    /**
     * Retrieves the JSON string of the upload session from Redis.
     */
    @GetMapping(value = "/upload-session/{sessionId}", produces = "application/json")
    @PreAuthorize("hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('UPLOAD_DICOM_IMAGE'))")
    public ResponseEntity<String> getUploadSession(
            @PathVariable String sessionId,
            java.security.Principal principal) {
        log.info("Received request to get upload session: {}", sessionId);
        String sessionJson = dicomService.getUploadSession(sessionId);
        if (sessionJson == null) {
            return ResponseEntity.notFound().build();
        }
        User user = principal == null ? null : userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null || !canAccessUploadSession(sessionJson, user)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to access this upload session");
        }
        return ResponseEntity.ok(sessionJson);
    }

    private boolean canAccessUploadSession(String sessionJson, User user) {
        try {
            DicomUploadSessionDTO session = OBJECT_MAPPER.readValue(sessionJson, DicomUploadSessionDTO.class);
            String roleCode = user.getRole() == null ? null : user.getRole().getCode();
            boolean privileged = "DEPARTMENT_HEAD".equalsIgnoreCase(roleCode)
                    || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(roleCode);
            return privileged || java.util.Objects.equals(session.getUploaderUserId(), user.getId());
        } catch (Exception exception) {
            log.warn("Unable to authorize upload session access", exception);
            return false;
        }
    }

    @GetMapping("/instances/{id}/image")
    @Transactional(readOnly = true)
    @PreAuthorize("(hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('VIEW_IMAGE_LIST'))) and @accessControl.canAccessDicomInstance(#p0, authentication)")
    public ResponseEntity<Resource> getInstanceImage(@PathVariable Long id) {
        Resource resource = dicomService.getInstanceImageResource(id);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/instances/{id}/raw")
    @Transactional(readOnly = true)
    @PreAuthorize("(hasAnyRole('DEPARTMENT_HEAD', 'HEAD_OF_DEPARTMENT') or (hasRole('DOCTOR') and hasAuthority('VIEW_IMAGE_LIST'))) and @accessControl.canAccessDicomInstance(#p0, authentication)")
    public ResponseEntity<Resource> getInstanceRaw(@PathVariable Long id) {
        Resource resource = dicomService.getInstanceRawResource(id);
        if (resource != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/dicom")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dicom_file.dcm\"")
                    .body(resource);
        }
        return ResponseEntity.notFound().build();
    }
}

