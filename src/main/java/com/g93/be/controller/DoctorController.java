package com.g93.be.controller;

import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.service.DoctorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
@Slf4j
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping
    public ResponseEntity<DoctorResponse> createDoctor(@RequestBody CreateDoctorRequest request) {
        log.info("Received request to register a new doctor with code: {}", request.getDoctorCode());
        DoctorResponse response = doctorService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get all doctor
    @GetMapping()
    public List<DoctorResponse> getAllDoctors() {
        return doctorService.getAllDoctors();
    }

    // Get active doctors
    @GetMapping("/active")
    public List<DoctorResponse> getActiveDoctors() {
        return doctorService.getActiveDoctors();
    }

    // Activate doctor
    @PostMapping("/{id}/activate")
    public void activateDoctor(@PathVariable Long id) {
        doctorService.activateDoctor(id);
    }


    // Update doctor
    // @PutMapping("/{id}")
    // public ResponseEntity<DoctorResponse> updateDoctor(@PathVariable Long id,
    // @RequestBody CreateDoctorRequest request) {
    // log.info("Received request to update doctor with id: {}", id);
    // DoctorResponse response = doctorService.editDoctor(id, request);
    // return ResponseEntity.status(HttpStatus.OK).body(response);
    // }

}
