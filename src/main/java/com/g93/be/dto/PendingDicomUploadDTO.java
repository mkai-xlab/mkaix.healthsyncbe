package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingDicomUploadDTO implements Serializable {
    private String patientCode;
    private String patientName;
    private Date patientBirthDate;
    private String patientSex;

    private String studyInstanceUid;
    private Date studyDate;
    private Date studyTime;
    private String description;
    private String referringPhysician;
    
    // We store minimal fields to reconstruct the entities during save
    private Map<String, String> physicalFilePaths; // key: originalFilename, value: physical file path (.dcm or .zip)
    private List<ImageCacheDTO> parsedImages;
    private List<InstanceCacheDTO> parsedInstances;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageCacheDTO implements Serializable {
        private String sopInstanceUid;
        private String originalFilename;
        private String storedFilePath; // For PNG/DCM
        private Long fileSize;
        private String mimeType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstanceCacheDTO implements Serializable {
        private String sopInstanceUid;
        private String filePath; // for DICOM raw
        private String bodyPart;
        private Integer instanceNumber;
    }
}
