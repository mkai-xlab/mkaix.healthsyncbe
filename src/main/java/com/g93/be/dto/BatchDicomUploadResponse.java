package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Data Transfer Object for responding to a batch DICOM upload request.
 * Contains a success message, a list of errors for invalid files, and
 * a list of successfully processed patients (if processed synchronously).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDicomUploadResponse {
    
    /**
     * General message about the upload status.
     */
    private String message;
    
    /**
     * List of files that failed validation during upload.
     */
    private List<FileUploadError> errors;
    
    /**
     * List of patients whose DICOM files were successfully processed.
     */
    private List<PatientDetailsResponse> successfulPatients;
}
