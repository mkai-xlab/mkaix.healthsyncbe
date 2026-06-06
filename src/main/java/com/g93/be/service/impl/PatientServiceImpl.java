package com.g93.be.service.impl;

import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientFilterRequest;
import com.g93.be.dto.PatientResponse;
import com.g93.be.entity.Patient;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.PatientRepository;
import com.g93.be.repository.specification.PatientSpecification;
import com.g93.be.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> getAllPatients(PatientFilterRequest filter, Pageable pageable) {
        Page<Patient> patientPage = patientRepository.findAll(PatientSpecification.filter(filter), pageable);
        List<PatientResponse> content = patientPage.getContent().stream()
                .map(patientMapper::toResponse)
                .toList();
        
        return new PageResponse<>(
                content,
                patientPage.getNumber(),
                patientPage.getSize(),
                patientPage.getTotalElements(),
                patientPage.getTotalPages(),
                patientPage.isLast()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePatient(Long id) {
        if (!patientRepository.existsById(id)) {
            throw new IllegalArgumentException("Patient with id " + id + " not found");
        }
        patientRepository.deleteById(id);
        log.info("Deleted patient with id {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientResponse editPatient(Long id, EditPatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient with id " + id + " not found"));

        if (request.getFullName() != null) patient.setFullName(request.getFullName());
        if (request.getDateOfBirth() != null) patient.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (patientRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
            }
            patient.setEmail(request.getEmail());
        }
        if (request.getAddress() != null) patient.setAddress(request.getAddress());
        if (request.getEmergencyContactName() != null) patient.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) patient.setEmergencyContactPhone(request.getEmergencyContactPhone());

        Patient saved = patientRepository.save(patient);
        log.info("Edited patient with id {}", saved.getId());
        return patientMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientResponse createPatient(CreatePatientRequest request) {
        log.info("Starting registration for patient code: {}", request.getPatientCode());

        if (request.getPatientCode() == null || request.getPatientCode().isBlank()) {
            throw new IllegalArgumentException("Patient code is required");
        }
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (patientRepository.existsByPatientCode(request.getPatientCode())) {
            throw new IllegalArgumentException("Patient code '" + request.getPatientCode() + "' is already in use");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (patientRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
            }
        }

        Patient patient = new Patient();
        patient.setPatientCode(request.getPatientCode());
        patient.setFullName(request.getFullName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient saved successfully with ID: {}", savedPatient.getId());

        return patientMapper.toResponse(savedPatient);
    }
}
