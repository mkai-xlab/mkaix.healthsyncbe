package com.g93.be.service;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.EditDoctorRequest;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.Doctor;
import com.g93.be.entity.Image;
import com.g93.be.entity.Role;
import com.g93.be.entity.UserStatus;
import com.g93.be.mapper.DoctorMapper;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.impl.DoctorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailUtil mailUtil;
    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorServiceImpl doctorService;

    private Doctor doctor;
    private DoctorResponse response;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(doctorService, "loginUrl", "http://localhost:3000/login");
        doctor = new Doctor();
        doctor.setId(7L);
        doctor.setUsername("doctor.one");
        doctor.setFullName("Doctor One");
        doctor.setEmail("doctor.one@hospital.com");
        doctor.setStatus(UserStatus.ACTIVE);

        response = new DoctorResponse();
        response.setId(7L);
        response.setUsername("doctor.one");
        response.setFullName("Doctor One");
        response.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchDoctorsReturnsMappedPageAndPreservesPagination() {
        Pageable pageable = PageRequest.of(1, 2);
        when(doctorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 3));
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        PageResponse<DoctorResponse> result = doctorService.searchDoctors(
                "one", "orthopedics", UserStatus.ACTIVE, pageable);

        assertEquals(List.of(response), result.content());
        assertEquals(1, result.pageNumber());
        assertEquals(2, result.pageSize());
        assertEquals(3, result.totalElements());
        assertEquals(2, result.totalPages());
        verify(doctorRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getAllDoctorsMapsEveryDoctor() {
        Doctor secondDoctor = new Doctor();
        DoctorResponse secondResponse = new DoctorResponse();
        when(doctorRepository.findAll()).thenReturn(List.of(doctor, secondDoctor));
        when(doctorMapper.toResponse(doctor)).thenReturn(response);
        when(doctorMapper.toResponse(secondDoctor)).thenReturn(secondResponse);

        assertEquals(List.of(response, secondResponse), doctorService.getAllDoctors());
    }

    @Test
    void getActiveDoctorsOnlyQueriesActiveStatus() {
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(doctor));
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        assertEquals(List.of(response), doctorService.getActiveDoctors());
        verify(doctorRepository).findAllByStatus(UserStatus.ACTIVE);
    }

    @Test
    void createDoctorGeneratesCredentialsAndPersistsActiveDoctor() {
        CreateDoctorRequest request = new CreateDoctorRequest(
                "New Doctor", "new.doctor@hospital.com", "0900000000",
                null, 5, "MD", "Biography");
        Role role = new Role();
        role.setCode("DOCTOR");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("new.doctor")).thenReturn(Optional.empty());
        when(userRepository.findByPhone(request.getPhone())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("encoded_temporary_password");
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(doctorMapper.toResponse(any(Doctor.class))).thenReturn(response);

        assertSame(response, doctorService.createDoctor(request));

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        Doctor saved = captor.getValue();
        assertEquals("new.doctor", saved.getUsername());
        assertEquals("encoded_temporary_password", saved.getPassword());
        assertEquals(role, saved.getRole());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertEquals(5, saved.getYearsOfExperience());
        verify(mailUtil).sendTemplateMail(
                org.mockito.ArgumentMatchers.eq("new.doctor@hospital.com"),
                any(), org.mockito.ArgumentMatchers.eq("doctor-welcome"), any());
    }

    @Test
    void createDoctorRejectsDuplicateEmailBeforeSaving() {
        CreateDoctorRequest request = new CreateDoctorRequest(
                "New Doctor", "existing@hospital.com", "0900000000",
                null, 5, "MD", null);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(doctor));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> doctorService.createDoctor(request));

        assertEquals("Email 'existing@hospital.com' is already registered", error.getMessage());
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void editDoctorUpdatesProvidedFieldsAndCreatesAvatar() {
        EditDoctorRequest request = new EditDoctorRequest(
                "Updated Doctor", "updated@hospital.com", "0900000000",
                "https://cdn/avatar.png", 12, "MD", "Biography");
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(doctor)).thenReturn(doctor);
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        DoctorResponse result = doctorService.editDoctor(7L, request);

        assertSame(response, result);
        assertEquals("Updated Doctor", doctor.getFullName());
        assertEquals("updated@hospital.com", doctor.getEmail());
        assertEquals("0900000000", doctor.getPhone());
        assertEquals(12, doctor.getYearsOfExperience());
        assertEquals("MD", doctor.getDegree());
        assertEquals("Biography", doctor.getBiography());
        assertNotNull(doctor.getAvatar());
        assertEquals("https://cdn/avatar.png", doctor.getAvatar().getFilePath());
        assertEquals("png", doctor.getAvatar().getExtension());
    }

    @Test
    void editDoctorLeavesNullFieldsUnchangedAndUpdatesExistingAvatar() {
        Image avatar = new Image();
        avatar.setFilePath("old.png");
        doctor.setAvatar(avatar);
        EditDoctorRequest request = new EditDoctorRequest(
                null, null, null, "new.png", null, null, null);
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(doctor)).thenReturn(doctor);
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        doctorService.editDoctor(7L, request);

        assertEquals("Doctor One", doctor.getFullName());
        assertEquals("new.png", doctor.getAvatar().getFilePath());
    }

    @Test
    void editDoctorRejectsUnknownDoctor() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> doctorService.editDoctor(99L, new EditDoctorRequest()));

        assertEquals("Doctor with id 99 not found", error.getMessage());
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void activateDoctorPersistsActiveStatus() {
        doctor.setStatus(UserStatus.INACTIVE);
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(doctor));

        doctorService.activateDoctor(7L);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertEquals(UserStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void deactivateDoctorPersistsInactiveStatus() {
        when(doctorRepository.findById(7L)).thenReturn(Optional.of(doctor));

        doctorService.softDeleteDoctor(7L);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository).save(captor.capture());
        assertEquals(UserStatus.INACTIVE, captor.getValue().getStatus());
    }

    @Test
    void getDoctorProfileMapsDoctorByUsername() {
        when(doctorRepository.findByUsername("doctor.one")).thenReturn(Optional.of(doctor));
        when(doctorMapper.toResponse(doctor)).thenReturn(response);

        assertSame(response, doctorService.getDoctorProfile("doctor.one"));
    }
}
