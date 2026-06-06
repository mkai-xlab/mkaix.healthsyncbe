package com.g93.be.controller;

import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PatientResponse;
import com.g93.be.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientFilterRequest;


/**
 * Controller for managing patient-related operations.
 */
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
@Slf4j
public class PatientController {

    private final PatientService patientService;

    /**
     * Registers a new patient.
     *
     * @param request The patient creation request payload.
     * @return The created PatientResponse.
     */
    @PostMapping
    public ResponseEntity<PatientResponse> createPatient(@Valid @RequestBody CreatePatientRequest request) {
        log.info("Received request to register a new patient with code: {}", request.getPatientCode());
        PatientResponse response = patientService.createPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves patients with filtering and pagination.
     *
     * @param filter   The filter criteria.
     * @param pageable The pagination parameters (default 10 per page).
     * @return A paginated list of patients.
     */
    @GetMapping
    public ResponseEntity<PageResponse<PatientResponse>> getAllPatients(
            @ModelAttribute PatientFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(patientService.getAllPatients(filter, pageable));
    }

    /**
     * Updates a patient's information.
     *
     * @param id The ID of the patient to update.
     * @param request The edit request payload.
     * @return The updated PatientResponse.
     */
    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> editPatient(@PathVariable Long id, @Valid @RequestBody EditPatientRequest request) {
        return ResponseEntity.ok(patientService.editPatient(id, request));
    }

    /**
     * Deletes a patient by ID.
     *
     * @param id The ID of the patient to delete.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePatient(@PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.ok().build();
    }
}
