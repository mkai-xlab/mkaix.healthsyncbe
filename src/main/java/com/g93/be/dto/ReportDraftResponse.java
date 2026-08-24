package com.g93.be.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Pre-filled preview of the X-ray report form, returned before any PDF exists.
 *
 * <p>The doctor reviews this on screen, edits any field by hand, and posts the confirmed values
 * back to the generate endpoint, which is the only step that renders a PDF. The letterhead,
 * {@code clinicalDepartment}, {@code doctorName}, {@code patientCode}, and the two grades are
 * read-only context; every other field is editable. {@code findings} and {@code conclusion} start
 * out stating the Kellgren-Lawrence grades confirmed during verification.
 */
public record ReportDraftResponse(
        Long examinationId,
        String patientCode,

        // Read-only, printed from configuration
        String ministryName,
        String hospitalName,
        String departmentName,
        String formCode,
        String clinicalDepartment,
        /** The authenticated doctor, printed on the signature line; not editable. */
        String doctorName,

        // Grades the auto-filled result text was composed from, shown for reference
        String leftKlGrade,
        String rightKlGrade,

        // Editable form fields
        String documentNumber,
        String attemptNumber,
        String patientName,
        String age,
        String gender,
        String address,
        List<String> findings,
        String conclusion,
        String signaturePlace,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate signatureDate) {
}
