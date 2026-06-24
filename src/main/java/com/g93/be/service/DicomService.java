package com.g93.be.service;

import com.g93.be.dto.DicomTagResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Service interface for handling DICOM files.
 */
public interface DicomService {

    /**
     * Extracts all metadata tags from the uploaded DICOM file.
     *
     * @param file The uploaded DICOM file payload.
     * @return A list of extracted DICOM tags.
     */
    List<DicomTagResponse> extractMetadata(MultipartFile file);
    /**
     * Uploads a batch of DICOM files, extracting metadata, creating patient/examination records,
     * grouping them by study, and returning a summary including any errors.
     *
     * @param files The uploaded DICOM files.
     * @return Batch upload response with successful patients and errors.
     */
    com.g93.be.dto.BatchDicomUploadResponse uploadBatch(List<MultipartFile> files);
}
