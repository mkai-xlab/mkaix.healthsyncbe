package com.g93.be.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * View model of the "PHIEU CHUP XQUANG" (X-ray imaging report) PDF template.
 *
 * <p>Everything above the result block is derived from the examination record, while
 * {@code findings} and {@code conclusion} carry the text the doctor confirmed: pre-filled from the
 * verified Kellgren-Lawrence grades, then optionally rewritten by hand before the PDF is rendered.
 */
@Data
@Builder
public class XrayReportDataDto {

    // Letterhead
    private String ministryName;
    private String hospitalName;
    private String departmentName;
    private String formCode;
    /** Base64 data URI of the hospital emblem; null when the asset is not packaged. */
    private String hospitalLogo;
    private String documentNumber;
    private String attemptNumber;

    // Patient info
    private String patientName;
    private String age;
    private String gender;
    private String address;
    private String room;
    private String bed;
    private String clinicalDepartment;

    // Examination info
    private String diagnosis;
    private String examRequest;
    private String requestDay;
    private String requestMonth;
    private String requestYear;
    private String referringPhysician;

    // Result block filled in (and editable) by the reading doctor
    private List<String> findings;
    private List<String> conclusionLines;

    // Signature block
    private String signaturePlace;
    private String signatureDay;
    private String signatureMonth;
    private String signatureYear;
    private String doctorName;
}
