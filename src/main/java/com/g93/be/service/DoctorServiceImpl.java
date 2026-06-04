package com.g93.be.service;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserRole;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.g93.be.dto.EditDoctorRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailUtil mailUtil;

    @Value("${app.login-url:http://localhost:3000/login}")
    private String loginUrl;

    @Override
    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DoctorResponse> getActiveDoctors() {
        return doctorRepository.findAllByStatus(com.g93.be.entity.UserStatus.ACTIVE)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void softDeleteDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        doctor.setStatus(UserStatus.INACTIVE);
        doctorRepository.save(doctor);
        log.info("Soft-deleted doctor with id {}", id);
    }

    @Override
    public void activateDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        doctor.setStatus(UserStatus.ACTIVE);
        doctorRepository.save(doctor);
        log.info("Activated doctor with id {}", id);
    }

    @Override
    public DoctorResponse editDoctor(Long id, EditDoctorRequest request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        // Update mutable fields
        if (request.getFullName() != null)
            doctor.setFullName(request.getFullName());
        if (request.getEmail() != null)
            doctor.setEmail(request.getEmail());
        if (request.getPhone() != null)
            doctor.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null)
            doctor.setAvatarUrl(request.getAvatarUrl());
        if (request.getLicenseNumber() != null)
            doctor.setLicenseNumber(request.getLicenseNumber());
        if (request.getSpecialization() != null)
            doctor.setSpecialization(request.getSpecialization());
        if (request.getHospitalName() != null)
            doctor.setHospitalName(request.getHospitalName());
        if (request.getYearsOfExperience() != null)
            doctor.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getAcademicTitle() != null)
            doctor.setAcademicTitle(request.getAcademicTitle());
        if (request.getDegree() != null)
            doctor.setDegree(request.getDegree());
        if (request.getSignatureUrl() != null)
            doctor.setSignatureUrl(request.getSignatureUrl());
        if (request.getBio() != null)
            doctor.setBio(request.getBio());
        if (request.getPosition() != null)
            doctor.setPosition(request.getPosition());
        Doctor saved = doctorRepository.save(doctor);
        log.info("Edited doctor with id {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        log.info("Starting registration for doctor code: {}", request.getDoctorCode());

        // 1. Validation
        if (request.getDoctorCode() == null || request.getDoctorCode().isBlank()) {
            throw new IllegalArgumentException("Doctor code is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (doctorRepository.findByDoctorCode(request.getDoctorCode()).isPresent()) {
            throw new IllegalArgumentException("Doctor code '" + request.getDoctorCode() + "' is already in use");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
        }

        // 2. Generate credentials
        String tempUsername = generateUniqueUsername(request.getEmail());
        String tempPassword = generateSecurePassword();

        log.info("Generated temporary username: {}", tempUsername);

        // 3. Create Doctor entity
        Doctor doctor = new Doctor();
        // Base user fields
        doctor.setUsername(tempUsername);
        doctor.setPassword(passwordEncoder.encode(tempPassword));
        doctor.setFullName(request.getFullName());
        doctor.setEmail(request.getEmail());
        doctor.setPhone(request.getPhone());
        doctor.setAvatarUrl(request.getAvatarUrl());
        doctor.setRole(UserRole.DOCTOR);
        doctor.setStatus(UserStatus.ACTIVE);

        // Doctor specific fields
        doctor.setDoctorCode(request.getDoctorCode());
        doctor.setLicenseNumber(request.getLicenseNumber());
        doctor.setSpecialization(request.getSpecialization());
        doctor.setHospitalName(request.getHospitalName());
        doctor.setYearsOfExperience(request.getYearsOfExperience());
        doctor.setAcademicTitle(request.getAcademicTitle());
        doctor.setDegree(request.getDegree());
        doctor.setSignatureUrl(request.getSignatureUrl());
        doctor.setBio(request.getBio());
        doctor.setPosition(request.getPosition());

        // Save to database
        Doctor savedDoctor = doctorRepository.save(doctor);
        log.info("Doctor saved successfully with ID: {}", savedDoctor.getId());

        // 4. Send email notification
        sendWelcomeEmail(savedDoctor, tempPassword);

        // 5. Map to response
        return mapToResponse(savedDoctor);
    }

    private String generateUniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._]", "").toLowerCase();
        if (base.isBlank()) {
            base = "doctor";
        }
        String username = base;
        int suffix = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = base + suffix;
            suffix++;
        }
        return username;
    }

    private String generateSecurePassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specials = "!@#$%^&*";
        String all = upper + lower + digits + specials;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        // Ensure at least one character from each set
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        sb.append(specials.charAt(random.nextInt(specials.length())));

        // Fill the rest to 10 characters
        for (int i = 4; i < 10; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }

        // Shuffle the characters
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            char temp = chars[index];
            chars[index] = chars[i];
            chars[i] = temp;
        }

        return new String(chars);
    }

    private void sendWelcomeEmail(Doctor doctor, String rawPassword) {
        try {
            Map<String, Object> variables = Map.of(
                    "fullName", doctor.getFullName(),
                    "username", doctor.getUsername(),
                    "password", rawPassword,
                    "loginUrl", loginUrl);

            mailUtil.sendTemplateMail(
                    doctor.getEmail(),
                    "Welcome to HealthSync - Your Practitioner Account Credentials",
                    "doctor-welcome",
                    variables);
            log.info("Welcome email sent successfully to {}", doctor.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", doctor.getEmail(), e);
            // Do not abort doctor registration due to email failure
            // Optionally, you could schedule a retry or add to a notification queue
        }
    }

    private DoctorResponse mapToResponse(Doctor doctor) {
        DoctorResponse response = new DoctorResponse();
        response.setId(doctor.getId());
        response.setUsername(doctor.getUsername());
        response.setFullName(doctor.getFullName());
        response.setEmail(doctor.getEmail());
        response.setPhone(doctor.getPhone());
        response.setAvatarUrl(doctor.getAvatarUrl());
        response.setRole(doctor.getRole().name());
        response.setStatus(doctor.getStatus());
        response.setDoctorCode(doctor.getDoctorCode());
        response.setLicenseNumber(doctor.getLicenseNumber());
        response.setSpecialization(doctor.getSpecialization());
        response.setHospitalName(doctor.getHospitalName());
        response.setYearsOfExperience(doctor.getYearsOfExperience());
        response.setAcademicTitle(doctor.getAcademicTitle());
        response.setDegree(doctor.getDegree());
        response.setSignatureUrl(doctor.getSignatureUrl());
        response.setBio(doctor.getBio());
        response.setPosition(doctor.getPosition());
        response.setCreatedAt(doctor.getCreatedAt());
        response.setUpdatedAt(doctor.getUpdatedAt());
        return response;
    }
}
