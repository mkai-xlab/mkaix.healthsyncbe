package com.g93.be.security;

import com.g93.be.entity.User;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("accessControl")
@RequiredArgsConstructor
public class AccessControlService {

    private final UserRepository userRepository;
    private final ExaminationRepository examinationRepository;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final AiResultRepository aiResultRepository;

    public boolean canAccessExamination(Long examinationId, Authentication authentication) {
        return canAccessAssignedDoctor(
                examinationRepository.findAssignedDoctorIdById(examinationId).orElse(null), authentication);
    }

    public boolean canAccessDicomInstance(Long dicomInstanceId, Authentication authentication) {
        return canAccessAssignedDoctor(
                dicomInstanceRepository.findAssignedDoctorIdById(dicomInstanceId).orElse(null), authentication);
    }

    public boolean canAccessAiResult(Long aiResultId, Authentication authentication) {
        return canAccessAssignedDoctor(
                aiResultRepository.findAssignedDoctorIdById(aiResultId).orElse(null), authentication);
    }

    public boolean canAccessClinicalImage(Long imageId, Authentication authentication) {
        Long assignedDoctorId = dicomInstanceRepository.findAssignedDoctorIdByImageId(imageId)
                .or(() -> aiResultRepository.findAssignedDoctorIdByImageId(imageId))
                .orElse(null);
        return canAccessAssignedDoctor(assignedDoctorId, authentication);
    }

    public boolean canAccessDoctor(Long doctorId, Authentication authentication) {
        User user = currentUser(authentication);
        return user != null && isClinicalUser(user)
                && (isClinicalSupervisor(user) || user.getId().equals(doctorId));
    }

    public boolean canAccessUser(Long userId, Authentication authentication) {
        User user = currentUser(authentication);
        return user != null && isClinicalUser(user)
                && (isClinicalSupervisor(user) || user.getId().equals(userId));
    }

    private boolean canAccessAssignedDoctor(Long assignedDoctorId, Authentication authentication) {
        User user = currentUser(authentication);
        return user != null && isClinicalUser(user) && (isClinicalSupervisor(user)
                || (assignedDoctorId != null && user.getId().equals(assignedDoctorId)));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsernameOrEmail(authentication.getName(), authentication.getName())
                .orElse(null);
    }

    private boolean isClinicalSupervisor(User user) {
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return false;
        }
        String role = user.getRole().getCode();
        return "DEPARTMENT_HEAD".equalsIgnoreCase(role)
                || "HEAD_OF_DEPARTMENT".equalsIgnoreCase(role);
    }

    private boolean isClinicalUser(User user) {
        if (user.getRole() == null || user.getRole().getCode() == null) {
            return false;
        }
        String role = user.getRole().getCode();
        return "DOCTOR".equalsIgnoreCase(role) || isClinicalSupervisor(user);
    }
}
