package com.g93.be.controller;

import com.g93.be.dto.ExaminationDto;
import com.g93.be.dto.PageResponse;
import com.g93.be.service.ExaminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/examinations")
@RequiredArgsConstructor
@Slf4j
public class ExaminationController {

    private final ExaminationService examinationService;

    /**
     * Retrieves examinations with pagination.
     *
     * @param pageable The pagination parameters (default 10 per page).
     * @return A paginated list of examinations.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ExaminationDto>> getAllExaminations(
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get all examinations");
        return ResponseEntity.ok(examinationService.getAllExaminations(pageable));
    }

    /**
     * Retrieves an examination by ID.
     *
     * @param id The ID of the examination to retrieve.
     * @return The examination details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExaminationDto> getExaminationById(@PathVariable Long id) {
        log.info("Received request to get examination with id: {}", id);
        return ResponseEntity.ok(examinationService.getExaminationById(id));
    }

    /**
     * Retrieves examinations by doctor ID with pagination.
     *
     * @param doctorId The doctor ID.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the doctor.
     */
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByDoctorId(
            @PathVariable Long doctorId,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by doctor id: {}", doctorId);
        return ResponseEntity.ok(examinationService.getExaminationsByDoctorId(doctorId, pageable));
    }

    /**
     * Retrieves examinations by patient ID with pagination.
     *
     * @param patientId The patient ID.
     * @param pageable The pagination parameters.
     * @return A paginated list of examinations for the patient.
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<PageResponse<ExaminationDto>> getExaminationsByPatientId(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get examinations by patient id: {}", patientId);
        return ResponseEntity.ok(examinationService.getExaminationsByPatientId(patientId, pageable));
    }

    /**
     * Marks an examination as viewed.
     *
     * @param id The ID of the examination to mark as viewed.
     * @return 200 OK.
     */
    @PutMapping("/{id}/view")
    public ResponseEntity<Void> markAsViewed(@PathVariable Long id) {
        log.info("Received request to mark examination {} as viewed", id);
        examinationService.markAsViewed(id);
        return ResponseEntity.ok().build();
    }
}

