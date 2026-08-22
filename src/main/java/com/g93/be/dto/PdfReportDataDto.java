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
    private String age;
    private String gender;
    private String address;

    // Examination Info
    private String encounterCode;
    private String studyDateTime;
    private String visitTime;
    private String doctorName;
    private String clinicalNotes;
    private String finalDiagnosis;
    private String leftKlGrade;
    private String rightKlGrade;
    private String processingTime;

    // AI Results
    private List<AiResultExportDto> aiResults;

    @Data
    @Builder
    public static class AiResultExportDto {
        private String dicomInstanceId;
        private String kneeSide;
        private String klGrade;
        private String aiPredictedGrade;
        private String decision;
        private String confidence;
        private String inferenceTime;
        private String modality;
        private String imageFormat;
        private String manufacturer;
        private String acquisitionPosition;
        private String imageQuality;
        private String readerOneOsteophyte;
        private String readerTwoOsteophyte;
        private String readerOneJointSpace;
        private String readerTwoJointSpace;
        private String readerOneSubchondralSclerosis;
        private String readerTwoSubchondralSclerosis;
        private String readerOneBoneDeformity;
        private String readerTwoBoneDeformity;
        private String readerOneKlGrade;
        private String readerTwoKlGrade;
        private String consensusKlGrade;
        private String readerOneProcessingTime;
        private String readerTwoProcessingTime;
        private String osteophyteDetection;
        private String jointSpaceDetection;
        private String comparisonResult;
        private String errorAnalysisNote;
        private String interpretation;
        private String reviewNote;
        private String gradcamBase64;
    }
}
