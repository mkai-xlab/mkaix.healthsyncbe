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

import java.util.List;

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
     * Retrieves all patients in the system.
     *
     * @return A list of PatientResponse objects representing all patients.
     */
    @GetMapping
    public ResponseEntity<List<PatientResponse>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
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
