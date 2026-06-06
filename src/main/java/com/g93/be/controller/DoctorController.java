package com.g93.be.controller;

import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Controller for managing doctor-related operations.
 */
@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
@Slf4j
public class DoctorController {

    private final DoctorService doctorService;

    /**
     * Registers a new doctor.
     *
     * @param request The doctor creation request payload.
     * @return The created DoctorResponse.
     */
    @PostMapping
    public ResponseEntity<DoctorResponse> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        log.info("Received request to register a new doctor with code: {}", request.getDoctorCode());
        DoctorResponse response = doctorService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all doctors in the system.
     *
     * @return A list of DoctorResponse objects representing all doctors.
     */
    @GetMapping
    public List<DoctorResponse> getAllDoctors() {
        return doctorService.getAllDoctors();
    }

    /**
     * Retrieves only active doctors.
     *
     * @return A list of DoctorResponse objects for active doctors.
     */
    @GetMapping("/active")
    public List<DoctorResponse> getActiveDoctors() {
        return doctorService.getActiveDoctors();
    }

    /**
     * Activates a doctor by ID.
     *
     * @param id The ID of the doctor to activate.
     */
    @PostMapping("/{id}/activate")
    public void activateDoctor(@PathVariable Long id) {
        doctorService.activateDoctor(id);
    }
}
