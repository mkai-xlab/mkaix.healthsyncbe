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
     * Uploads a DICOM file, extracts metadata and image, creates patient/examination,
     * and returns patient details.
     *
     * @param file The uploaded DICOM file.
     * @return Patient details and examinations.
     */
    com.g93.be.dto.PatientDetailsResponse uploadAndProcessDicom(MultipartFile file);
}
