package com.g93.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing an error that occurred during file upload.
 * It contains the filename and the specific reason why the file was rejected.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadError {
    
    /**
     * The original name of the uploaded file that failed validation.
     */
    private String filename;
    
    /**
     * The reason why the file was rejected (e.g., "Invalid file format").
     */
    private String errorReason;
}
