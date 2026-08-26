package com.g93.be.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * The X-ray report form as confirmed by the reading doctor.
 *
 * <p>Posted when the doctor presses confirm on the preview returned by the report-draft endpoint.
 * Every field is optional and falls back independently to the pre-filled value, so the doctor may
 * correct a single line without resending the whole form. Sending a body always re-renders the PDF
 * so the confirmed values reach the file.
 *
 * <p>The letterhead and the imaging department are fixed by configuration, and the signing doctor
 * is taken from the authenticated account, so none of them are accepted here: a report can only be
 * signed in the name of the doctor who generated it.
 */
public record GenerateReportRequest(
        @Size(max = 100, message = "Document number must not exceed 100 characters")
        String documentNumber,
        @Size(max = 20, message = "Attempt number must not exceed 20 characters")
        String attemptNumber,
        @Size(max = 150, message = "Patient name must not exceed 150 characters")
        String patientName,
        @Size(max = 20, message = "Age must not exceed 20 characters")
        String age,
        @Size(max = 20, message = "Gender must not exceed 20 characters")
        String gender,
        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address,
        List<@Size(max = 1000, message = "Each finding line must not exceed 1000 characters")
                String> findings,
        @Size(max = 2000, message = "Conclusion must not exceed 2000 characters")
        String conclusion,
        @Size(max = 100, message = "Signature place must not exceed 100 characters")
        String signaturePlace,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate signatureDate) {
}
