package com.g93.be;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.DoctorPosition;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.service.DoctorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class DoctorRegistrationIntegrationTest {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailUtil mailUtil;

    @BeforeEach
    void setUp() {
        doctorRepository.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateDoctor_Success() {
        // Given
        CreateDoctorRequest request = new CreateDoctorRequest(
                "John Doe",
                "john.doe@hospital.com",
                "123456789",
                "http://avatar.url",
                "DOC001",
                "LIC12345",
                "Orthopedics",
                "General Hospital",
                10,
                "Prof",
                "PhD",
                "http://signature.url",
                "Orthopedics expert",
                DoctorPosition.DEPARTMENT_HEAD
        );

        // When
        DoctorResponse response = doctorService.createDoctor(request);

        // Then
        assertNotNull(response.getId());
        assertEquals("john.doe", response.getUsername());
        assertEquals("John Doe", response.getFullName());
        assertEquals("john.doe@hospital.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertEquals("Orthopedics", response.getSpecialization());

        // Verify entity in DB
        Optional<Doctor> savedDocOpt = doctorRepository.findById(response.getId());
        assertTrue(savedDocOpt.isPresent());
        Doctor savedDoc = savedDocOpt.get();
        assertEquals("john.doe", savedDoc.getUsername());

        // Let's capture the raw password from email mock to verify encoder matches
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mailUtil).sendTemplateMail(
                eq("john.doe@hospital.com"),
                eq("Welcome to HealthSync - Your Practitioner Account Credentials"),
                eq("doctor-welcome"),
                variablesCaptor.capture()
        );

        Map<String, Object> emailVariables = variablesCaptor.getValue();
        assertEquals("John Doe", emailVariables.get("fullName"));
        assertEquals("john.doe", emailVariables.get("username"));

        String sentRawPassword = (String) emailVariables.get("password");
        assertNotNull(sentRawPassword);
        assertTrue(passwordEncoder.matches(sentRawPassword, savedDoc.getPassword()));
    }

    @Test
    void testCreateDoctor_DuplicateDoctorCode() {
        // Given
        CreateDoctorRequest request1 = new CreateDoctorRequest(
                "John Doe",
                "john.doe@hospital.com",
                "123456789",
                null,
                "DOC001",
                null, null, null, null, null, null, null, null,
                DoctorPosition.NORMAL
        );
        doctorService.createDoctor(request1);

        CreateDoctorRequest request2 = new CreateDoctorRequest(
                "Jane Doe",
                "jane.doe@hospital.com",
                "987654321",
                null,
                "DOC001", // Duplicate code
                null, null, null, null, null, null, null, null,
                DoctorPosition.NORMAL
        );

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            doctorService.createDoctor(request2);
        });
        assertTrue(exception.getMessage().contains("already in use"));
    }

    @Test
    void testSearchDoctors_SubstringMatch() {
        // Given
        CreateDoctorRequest request = new CreateDoctorRequest(
                "Nguyễn Hoàng Duy",
                "hoangduy@hospital.com",
                "123456789",
                null,
                "DOC002",
                "LIC999",
                "Cardiology",
                null,
                5,
                null, null, null, null,
                DoctorPosition.NORMAL
        );
        doctorService.createDoctor(request);

        // When
        var response = doctorService.searchDoctors("du", null, null, org.springframework.data.domain.PageRequest.of(0, 10));

        // Then
        assertFalse(response.content().isEmpty());
        assertEquals("Nguyễn Hoàng Duy", response.content().get(0).getFullName());
    }

    @Test
    void testSearchDoctors_UsernameMatch() {
        // Given
        CreateDoctorRequest request = new CreateDoctorRequest(
                "Nguyễn Hoàng Duy",
                "hoangduy2@hospital.com",
                "987654321",
                null,
                "DOC003",
                "LIC888",
                "Cardiology",
                null,
                5,
                null, null, null, null,
                DoctorPosition.NORMAL
        );
        doctorService.createDoctor(request);

        // When searching "hoangduy" (matches username "hoangduy2")
        var response = doctorService.searchDoctors("hoangduy", null, null, org.springframework.data.domain.PageRequest.of(0, 10));

        // Then
        assertFalse(response.content().isEmpty());
        assertEquals("Nguyễn Hoàng Duy", response.content().get(0).getFullName());
    }
}

