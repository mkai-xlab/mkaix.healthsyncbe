package com.g93.be.dto;

/**
 * Data Transfer Object for returning parsed DICOM metadata.
 * 
 * @param tagId   The DICOM tag identifier in hex format (e.g., "0010,0010").
 * @param tagName The readable name of the DICOM tag (e.g., "Patient's Name").
 * @param value   The string representation of the value for the tag.
 */
public record DicomTagResponse(
    String tagId,
    String tagName,
    String value
) {
}
