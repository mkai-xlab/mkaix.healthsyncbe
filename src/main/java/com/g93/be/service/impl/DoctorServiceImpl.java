package com.g93.be.service.impl;

import com.g93.be.entity.Doctor;
import com.g93.be.entity.UserStatus;
import com.g93.be.entity.Image;
import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.PageResponse;
import com.g93.be.aspect.LogAction;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.service.DoctorService;
import com.g93.be.specification.DoctorSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.g93.be.mapper.DoctorMapper;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.g93.be.dto.EditDoctorRequest;
import com.g93.be.dto.EditDoctorProfileRequest;
import com.g93.be.service.AvatarStorageService;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailUtil mailUtil;
    private final DoctorMapper doctorMapper;
    private final AvatarStorageService avatarStorageService;

    @Value("${app.login-url:http://localhost:3000/login}")
    private String loginUrl;

    @Override
    public PageResponse<DoctorResponse> searchDoctors(String keyword, String specialization, UserStatus status,
            Pageable pageable) {
        Specification<Doctor> spec = DoctorSpecification.searchAndFilter(keyword, specialization, status);
        Page<Doctor> doctorPage = doctorRepository.findAll(spec, pageable);

        Page<DoctorResponse> responsePage = doctorPage.map(doctorMapper::toResponse);
        return PageResponse.of(responsePage);
    }

    @Override
    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findAll().stream()
                .map(doctorMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DoctorResponse> getActiveDoctors() {
        return doctorRepository.findAllByStatus(UserStatus.ACTIVE)
                .stream()
                .map(doctorMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @com.g93.be.aspect.LogAction("DEACTIVATE_DOCTOR")
    public void softDeleteDoctor(Long id, String reason) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        doctor.setStatus(UserStatus.INACTIVE);
        doctor.setInactiveReason(reason);
        doctorRepository.save(doctor);
        log.info("Soft-deleted doctor with id {}", id);
        
        // Gửi email thông báo
        if (doctor.getEmail() != null && !doctor.getEmail().isEmpty()) {
            java.util.Map<String, Object> vars = new java.util.HashMap<>();
            vars.put("doctorName", doctor.getFullName());
            vars.put("reason", reason != null && !reason.trim().isEmpty() ? reason : "Không có lý do cụ thể");
            
            try {
                mailUtil.sendTemplateMail(doctor.getEmail(), "Thông báo vô hiệu hóa tài khoản", "deactivate_doctor_mail", vars);
            } catch (Exception e) {
                log.error("Failed to send deactivation email to doctor {}", doctor.getEmail(), e);
            }
        }
    }

    @Override
    @com.g93.be.aspect.LogAction("ACTIVATE_DOCTOR")
    public void activateDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        doctor.setStatus(UserStatus.ACTIVE);
        doctor.setInactiveReason(null);
        doctorRepository.save(doctor);
        log.info("Activated doctor with id {}", id);
    }

    @Override
    @LogAction("EDIT_DOCTOR")
    public DoctorResponse editDoctor(Long id, EditDoctorRequest request) {
        Doctor doctor = doctorRepository.findDetailsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Doctor with id " + id + " not found"));
        return updateDoctorFields(doctor, request);
    }

    @Override
    public DoctorResponse getDoctorProfile(String username) {
        Doctor doctor = doctorRepository.findProfileByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for username: " + username));
        return doctorMapper.toResponse(doctor);
    }

    @Override
    @LogAction("EDIT_DOCTOR_PROFILE")
    public DoctorResponse editDoctorProfile(String username, EditDoctorProfileRequest request) {
        Doctor doctor = doctorRepository.findProfileByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for username: " + username));
        if (request.getFullName() != null)
            doctor.setFullName(request.getFullName().replaceAll("\\s+", " ").trim());
        if (request.getEmail() != null)
            doctor.setEmail(request.getEmail());
        if (request.getPhone() != null)
            doctor.setPhone(request.getPhone());
        if (request.getYearsOfExperience() != null)
            doctor.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getDegree() != null)
            doctor.setDegree(request.getDegree());
        if (request.getBiography() != null)
            doctor.setBiography(request.getBiography());
        return saveAndMap(doctor);
    }

    @Override
    @Transactional
    @LogAction("UPDATE_DOCTOR_AVATAR")
    public DoctorResponse updateDoctorAvatar(String username, MultipartFile file) {
        Doctor doctor = doctorRepository.findProfileByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for username: " + username));
        AvatarStorageService.StoredAvatar storedAvatar = avatarStorageService.store(doctor.getId(), file);
        String previousAvatarUrl = doctor.getAvatar() != null ? doctor.getAvatar().getFilePath() : null;
        try {
            Image avatar = doctor.getAvatar();
            if (avatar == null) {
                avatar = new Image();
                doctor.setAvatar(avatar);
            }
            avatar.setFilePath(storedAvatar.publicUrl());
            avatar.setExtension(storedAvatar.extension());

            DoctorResponse response = saveAndMap(doctor);
            scheduleAvatarCleanup(previousAvatarUrl, storedAvatar.publicUrl());
            return response;
        } catch (RuntimeException exception) {
            deleteAvatarQuietly(storedAvatar.publicUrl());
            throw exception;
        }
    }

    private void scheduleAvatarCleanup(String previousAvatarUrl, String newAvatarUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAvatarQuietly(previousAvatarUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteAvatarQuietly(previousAvatarUrl);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    deleteAvatarQuietly(newAvatarUrl);
                }
            }
        });
    }

    private void deleteAvatarQuietly(String avatarUrl) {
        try {
            avatarStorageService.delete(avatarUrl);
        } catch (RuntimeException exception) {
            log.warn("Could not delete avatar file {}: {}", avatarUrl, exception.getMessage());
        }
    }

    private DoctorResponse updateDoctorFields(Doctor doctor, EditDoctorRequest request) {
        // Update mutable fields
        if (request.getFullName() != null)
            doctor.setFullName(request.getFullName().replaceAll("\\s+", " ").trim());
        if (request.getEmail() != null)
            doctor.setEmail(request.getEmail());
        if (request.getPhone() != null)
            doctor.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null)
            updateAvatar(doctor, request.getAvatarUrl());

        if (request.getYearsOfExperience() != null)
            doctor.setYearsOfExperience(request.getYearsOfExperience());
        if (request.getDegree() != null)
            doctor.setDegree(request.getDegree());
        if (request.getBiography() != null)
            doctor.setBiography(request.getBiography());

        return saveAndMap(doctor);
    }

    private DoctorResponse saveAndMap(Doctor doctor) {
        Doctor saved = doctorRepository.save(doctor);
        log.info("Edited doctor with id {}", saved.getId());
        return doctorMapper.toResponse(saved);
    }

    private void updateAvatar(Doctor doctor, String avatarUrl) {
        Image avatar = doctor.getAvatar();
        if (avatar == null) {
            avatar = new Image();
            doctor.setAvatar(avatar);
        }
        avatar.setFilePath(avatarUrl);
        avatar.setExtension(extractExtension(avatarUrl));
    }

    private String extractExtension(String avatarUrl) {
        String path;
        try {
            path = java.net.URI.create(avatarUrl).getPath();
        } catch (IllegalArgumentException exception) {
            path = avatarUrl;
        }
        if (path == null)
            return null;
        int separatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex <= separatorIndex || dotIndex == path.length() - 1) {
            return null;
        }
        String extension = path.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT);
        return extension.length() <= 20 ? extension : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogAction("CREATE_DOCTOR")
    public DoctorResponse createDoctor(CreateDoctorRequest request) {
        log.info("Starting registration for doctor email: {}", request.getEmail());

        // 1. Validation
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered");
        }

        // 2. Generate credentials
        String tempUsername = generateUniqueUsername(request.getEmail());
        String tempPassword = generateSecurePassword();

        // 3. Validate unique phone
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (userRepository.findByPhone(request.getPhone()).isPresent()) {
                throw new IllegalArgumentException("Phone '" + request.getPhone() + "' is already registered");
            }
        }

        // 3. Create Doctor entity
        Doctor doctor = new Doctor();
        // Base user fields
        doctor.setUsername(tempUsername);
        doctor.setPassword(passwordEncoder.encode(tempPassword));
        doctor.setFullName(request.getFullName().replaceAll("\\s+", " ").trim());
        doctor.setEmail(request.getEmail());
        doctor.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) {
            Image avatar = new Image();
            avatar.setExtension("png");
            avatar.setFilePath(request.getAvatarUrl());
            doctor.setAvatar(avatar);
        }
        doctor.setRole(roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found in database")));
        doctor.setStatus(UserStatus.ACTIVE);

        // Doctor specific fields
        doctor.setYearsOfExperience(request.getYearsOfExperience());
        doctor.setDegree(request.getDegree());
        doctor.setBiography(request.getBiography());

        // Save to database
        Doctor savedDoctor = doctorRepository.save(doctor);
        log.info("Doctor saved successfully with ID: {}", savedDoctor.getId());

        // 4. Send email notification
        sendWelcomeEmail(savedDoctor, tempPassword);

        // 5. Map to response
        return doctorMapper.toResponse(savedDoctor);
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

        // Fill the rest to 12 characters
        for (int i = 4; i < 12; i++) {
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
            Map<String, Object> variables = new java.util.HashMap<>();
            variables.put("fullName", doctor.getFullName() != null ? doctor.getFullName() : "");
            variables.put("username", doctor.getUsername());
            variables.put("password", rawPassword);
            variables.put("loginUrl", loginUrl);

            mailUtil.sendTemplateMail(
                    doctor.getEmail(),
                    "Welcome to HealthSync - Your Practitioner Account Credentials",
                    "doctor-welcome",
                    variables);
            log.info("Welcome email sent successfully to {}", doctor.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}", doctor.getEmail(), e);
        }
    }
}

