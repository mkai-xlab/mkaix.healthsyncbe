package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DicomUploadSessionDTO implements Serializable {
    private String uploadSessionId;
    private Long uploaderUserId; // Doctor who uploaded
    private Map<String, PendingDicomUploadDTO> patients; // key: patientCode
    private List<FileUploadError> errors; // Any initial errors
    private long createdAt;
}
