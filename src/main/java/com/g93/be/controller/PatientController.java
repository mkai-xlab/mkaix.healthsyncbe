package com.g93.be.controller;
import com.g93.be.dto.PatientDetailsResponse;


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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDate;


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
    @PreAuthorize("hasAuthority('CREATE_PATIENT_EXAM')")
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
    @PreAuthorize("hasAuthority('READ_PATIENT_LIST')")
    public ResponseEntity<PageResponse<PatientResponse>> getAllPatients(
            @Valid @ModelAttribute PatientFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(patientService.getAllPatients(filter, pageable, username));
    }

    /**
     * Updates a patient's information.
     *
     * @param id The ID of the patient to update.
     * @param request The edit request payload.
     * @return The updated PatientResponse.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CREATE_PATIENT_EXAM')")
    public ResponseEntity<PatientResponse> editPatient(@PathVariable Long id, @Valid @RequestBody EditPatientRequest request) {
        return ResponseEntity.ok(patientService.editPatient(id, request));
    }

    /**
     * Deletes a patient by ID.
     *
     * @param id The ID of the patient to delete.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePatient(@PathVariable Long id) {
        patientService.deletePatient(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Retrieves a patient's details and their examination images.
     *
     * @param patientId The patient code.
     * @return Patient details and image URLs.
     */
    @GetMapping("/{patientId}/details")
    @PreAuthorize("hasAuthority('VIEW_PATIENT_DETAIL')")
    public ResponseEntity<PatientDetailsResponse> getPatientDetailsWithImages(@PathVariable String patientId, java.security.Principal principal) {
        return ResponseEntity.ok(patientService.getPatientDetailsWithImages(patientId, principal.getName()));
    }

    /**
     * Retrieves patients filtered by upload date.
     *
     * @param date     The upload date to filter by.
     * @param pageable The pagination parameters.
     * @return A paginated list of patients.
     */
    @GetMapping("/filter/upload-date")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<PatientResponse>> getPatientsByUploadDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PageableDefault(size = 10) Pageable pageable) {
        log.info("Received request to get patients by upload date: {}", date);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(patientService.getPatientsByUploadDate(date, pageable, username));
    }
}

