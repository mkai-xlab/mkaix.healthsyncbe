package com.g93.be.service.impl;

import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.dto.PatientFilterRequest;
import com.g93.be.dto.PatientResponse;
import com.g93.be.entity.Patient;
import com.g93.be.entity.Role;
import com.g93.be.entity.UserStatus;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.PatientRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.specification.PatientSpecification;
import com.g93.be.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
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
        if (request.getDateOfBirth() != null) patient.setDob(request.getDateOfBirth());
        if (request.getGender() != null) patient.setGender(request.getGender());
        if (request.getPhone() != null) patient.setPhone(request.getPhone());
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (userRepository.findByEmail(request.getEmail()).isPresent() && 
                !patient.getEmail().equals(request.getEmail())) {
                throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
            }
            patient.setEmail(request.getEmail());
        }

        Patient saved = patientRepository.save(patient);
        log.info("Edited patient with id {}", saved.getId());
        return patientMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatientResponse createPatient(CreatePatientRequest request) {
        log.info("Starting registration for patient name: {}", request.getFullName());

        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        String tempUsername = generateUniqueUsername(request.getEmail(), request.getFullName());
        String tempPassword = UUID.randomUUID().toString().substring(0, 12);

        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = tempUsername + "@healthsync.com";
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email '" + email + "' is already registered");
        }

        Patient patient = new Patient();
        patient.setUsername(tempUsername);
        patient.setPassword(passwordEncoder.encode(tempPassword));
        patient.setFullName(request.getFullName());
        patient.setDob(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(email);
        patient.setStatus(UserStatus.ACTIVE);
        patient.setUserType("PATIENT");

        Role patientRole = roleRepository.findByName("PATIENT")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("PATIENT");
                    return roleRepository.save(r);
                });
        patient.setRole(patientRole);

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient saved successfully with ID: {}", savedPatient.getId());

        return patientMapper.toResponse(savedPatient);
    }

    private String generateUniqueUsername(String email, String fullName) {
        String base = "patient";
        if (email != null && !email.isBlank()) {
            base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
        } else if (fullName != null && !fullName.isBlank()) {
            base = fullName.split(" ")[0].replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
        }
        if (base.isBlank()) {
            base = "patient";
        }
        String username = base;
        int suffix = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = base + suffix;
            suffix++;
        }
        return username;
    }
}
