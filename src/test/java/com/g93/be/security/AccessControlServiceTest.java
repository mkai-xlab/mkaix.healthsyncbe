package com.g93.be.security;

import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.repository.AiResultRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExaminationRepository examinationRepository;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @Mock
    private AiResultRepository aiResultRepository;

    @InjectMocks
    private AccessControlService accessControl;

    @Test
    void assignedDoctorCanAccessOwnExamination() {
        Authentication authentication = authentication("doctor");
        when(userRepository.findByUsernameOrEmail("doctor", "doctor"))
                .thenReturn(Optional.of(user(7L, "DOCTOR")));
        when(examinationRepository.findAssignedDoctorIdById(15L)).thenReturn(Optional.of(7L));

        assertTrue(accessControl.canAccessExamination(15L, authentication));
    }

    @Test
    void doctorCannotAccessAnotherDoctorsExaminationOrDicom() {
        Authentication authentication = authentication("doctor");
        when(userRepository.findByUsernameOrEmail("doctor", "doctor"))
                .thenReturn(Optional.of(user(7L, "DOCTOR")));
        when(examinationRepository.findAssignedDoctorIdById(15L)).thenReturn(Optional.of(8L));
        when(dicomInstanceRepository.findAssignedDoctorIdById(21L)).thenReturn(Optional.of(8L));

        assertFalse(accessControl.canAccessExamination(15L, authentication));
        assertFalse(accessControl.canAccessDicomInstance(21L, authentication));
    }

    @Test
    void adminCannotAccessClinicalResultsOrDoctorDashboardTotals() {
        Authentication authentication = authentication("admin");
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user(1L, "ADMIN")));
        when(aiResultRepository.findAssignedDoctorIdById(44L)).thenReturn(Optional.of(7L));

        assertFalse(accessControl.canAccessAiResult(44L, authentication));
    }

    @Test
    void unauthenticatedRequestCannotAccessProtectedResource() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("doctor", null);

        assertFalse(accessControl.canAccessDoctor(7L, authentication));
    }

    private Authentication authentication(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private User user(Long id, String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
