package com.g93.be.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PdfReportDataDto {
    // Patient Info
    private String patientCode;
    private String patientName;
    private String dob;
    private String gender;
    private String address;

    // Examination Info
    private String encounterCode;
    private String visitTime;
    private String doctorName;
    private String clinicalNotes;
    private String finalDiagnosis;

    // AI Results
    private List<AiResultExportDto> aiResults;

    @Data
    @Builder
    public static class AiResultExportDto {
        private String klGrade;
        private String aiPredictedGrade;
        private String decision;
        private String confidence;
        private String interpretation;
        private String reviewNote;
        private String gradcamBase64;
    }
}
