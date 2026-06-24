package com.g93.be.service.impl;

import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.mapper.PatientMapper;
import com.g93.be.repository.*;
import com.g93.be.repository.specification.PatientSpecification;
import com.g93.be.service.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
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
            if (patientRepository.findByEmail(request.getEmail()).isPresent() && 
                (patient.getEmail() == null || !patient.getEmail().equals(request.getEmail()))) {
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

        String email = request.getEmail();
        if (email != null && !email.isBlank()) {
            if (patientRepository.findByEmail(email).isPresent()) {
                throw new IllegalArgumentException("Email '" + email + "' is already registered");
            }
        }

        Patient patient = new Patient();
        patient.setPatientCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        patient.setFullName(request.getFullName());
        patient.setDob(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setPhone(request.getPhone());
        patient.setEmail(email);
        patient.setStatus(UserStatus.ACTIVE);

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient saved successfully with ID: {}", savedPatient.getId());

        return patientMapper.toResponse(savedPatient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDetailsResponse getPatientDetailsWithImages(String patientId) {
        Patient patient = patientRepository.findByPatientCode(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient with ID " + patientId + " not found"));

        PatientDetailsResponse response = new PatientDetailsResponse();
        response.setPatient(patientMapper.toResponse(patient));

        List<Examination> examinations = examinationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId());
        List<ExaminationImageDto> examDtos = new ArrayList<>();

        for (Examination exam : examinations) {
            ExaminationImageDto dto = new ExaminationImageDto();
            dto.setExaminationId(exam.getId());
            dto.setEncounterCode(exam.getEncounterCode());
            dto.setStatus(exam.getStatus().name());
            dto.setVisitTime(exam.getVisitTime());

            List<DicomInstance> instances = dicomInstanceRepository.findByExaminationId(exam.getId());
            if (!instances.isEmpty()) {
                DicomInstance instance = instances.get(0); // Take the first one for simplicity
                if (instance.getStoragePngPath() != null) {
                    Path pngPath = Paths.get(instance.getStoragePngPath());
                    if (Files.exists(pngPath)) {
                        try {
                            String baseUrl = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
                            dto.setImageUrl(baseUrl + "/api/v1/dicom/instances/" + instance.getId() + "/image");
                        } catch (Exception e) {
                            log.error("Failed to build image url for instance: " + instance.getId(), e);
                        }
                    }
                }
            }
            examDtos.add(dto);
        }

        response.setExaminations(examDtos);
        return response;
    }
}
