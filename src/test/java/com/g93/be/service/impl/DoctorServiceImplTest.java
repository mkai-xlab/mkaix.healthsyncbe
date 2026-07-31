package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.mapper.DoctorMapper;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.AvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import java.util.Set;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DoctorServiceImplTest {

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
    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private DoctorServiceImpl doctorService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(doctorService, "loginUrl", "http://localhost:3000/login");
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==========================================
    // 1. searchDoctors
    // ==========================================
    @Test
    void testSearchDoctors_Normal() {
        Pageable pageable = PageRequest.of(0, 10);
        Doctor doc = new Doctor();
        Page<Doctor> page = new PageImpl<>(List.of(doc));
        DoctorResponse docRes = new DoctorResponse();
        
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        PageResponse<DoctorResponse> res = doctorService.searchDoctors("kw", "spec", UserStatus.ACTIVE, pageable);
        
        assertNotNull(res);
        assertEquals(1, res.content().size());
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void testSearchDoctors_EmptyResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of());
        
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        
        PageResponse<DoctorResponse> res = doctorService.searchDoctors("kw", "spec", UserStatus.ACTIVE, pageable);
        
        assertNotNull(res);
        assertEquals(0, res.content().size());
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void testSearchDoctors_NullFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Doctor> page = new PageImpl<>(List.of());
        
        when(doctorRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        
        PageResponse<DoctorResponse> res = doctorService.searchDoctors(null, null, null, pageable);
        
        assertNotNull(res);
        verify(doctorRepository).findAll(any(Specification.class), eq(pageable));
    }

    // ==========================================
    // 2. getAllDoctors
    // ==========================================
    @Test
    void testGetAllDoctors_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();
        
        when(doctorRepository.findAll()).thenReturn(List.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        List<DoctorResponse> res = doctorService.getAllDoctors();
        
        assertNotNull(res);
        assertEquals(1, res.size());
        verify(doctorRepository).findAll();
    }

    @Test
    void testGetAllDoctors_EmptyList() {
        when(doctorRepository.findAll()).thenReturn(List.of());
        List<DoctorResponse> res = doctorService.getAllDoctors();
        assertTrue(res.isEmpty());
        verify(doctorRepository).findAll();
    }

    // ==========================================
    // 3. getActiveDoctors
    // ==========================================
    @Test
    void testGetActiveDoctors_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();
        
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        List<DoctorResponse> res = doctorService.getActiveDoctors();
        
        assertNotNull(res);
        assertEquals(1, res.size());
        verify(doctorRepository).findAllByStatus(UserStatus.ACTIVE);
    }

    @Test
    void testGetActiveDoctors_EmptyList() {
        when(doctorRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of());
        List<DoctorResponse> res = doctorService.getActiveDoctors();
        assertTrue(res.isEmpty());
        verify(doctorRepository).findAllByStatus(UserStatus.ACTIVE);
    }

    // ==========================================
    // 4. softDeleteDoctor
    // ==========================================
    @Test
    void testSoftDeleteDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setStatus(UserStatus.ACTIVE);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doc));
        
        doctorService.softDeleteDoctor(1L);
        
        assertEquals(UserStatus.INACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    @Test
    void testSoftDeleteDoctor_Abnormal_NotFound() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.empty());
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.softDeleteDoctor(1L));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 5. activateDoctor
    // ==========================================
    @Test
    void testActivateDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setStatus(UserStatus.INACTIVE);
        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doc));
        
        doctorService.activateDoctor(1L);
        
        assertEquals(UserStatus.ACTIVE, doc.getStatus());
        verify(doctorRepository).save(doc);
    }

    @Test
    void testActivateDoctor_Abnormal_NotFound() {
        when(doctorRepository.findById(1L)).thenReturn(Optional.empty());
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.activateDoctor(1L));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 6. editDoctor
    // ==========================================
    @Test
    void testEditDoctor_Normal() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        EditDoctorRequest req = new EditDoctorRequest();
        req.setFullName("Updated Name");
        req.setEmail("updated@test.com");
        req.setPhone("0987654321");
        req.setAvatarUrl("http://avatar.com/new.png");
        req.setYearsOfExperience(10);
        req.setDegree("PhD");
        req.setBiography("Bio updated");
        
        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        DoctorResponse res = doctorService.editDoctor(1L, req);
        
        assertEquals("Updated Name", doc.getFullName());
        assertEquals("updated@test.com", doc.getEmail());
        assertEquals("0987654321", doc.getPhone());
        assertEquals(10, doc.getYearsOfExperience());
        assertEquals("PhD", doc.getDegree());
        assertEquals("Bio updated", doc.getBiography());
        assertNotNull(doc.getAvatar());
        assertEquals("http://avatar.com/new.png", doc.getAvatar().getFilePath());
        assertEquals("png", doc.getAvatar().getExtension());
        assertNotNull(res);
    }

    @Test
    void testEditDoctor_Abnormal_NotFound() {
        EditDoctorRequest req = new EditDoctorRequest();
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.empty());
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.editDoctor(1L, req));
        assertEquals("Doctor with id 1 not found", ex.getMessage());
    }

    // ==========================================
    // 7. getDoctorProfile
    // ==========================================
    @Test
    void testGetDoctorProfile_Normal() {
        Doctor doc = new Doctor();
        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        DoctorResponse res = doctorService.getDoctorProfile("user1");
        
        assertNotNull(res);
        verify(doctorRepository).findProfileByUsername("user1");
    }

    @Test
    void testGetDoctorProfile_Abnormal_NotFound() {
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.empty());
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.getDoctorProfile("user1"));
        assertEquals("Doctor not found for username: user1", ex.getMessage());
    }

    // ==========================================
    // 8. editDoctorProfile
    // ==========================================
    @Test
    void testEditDoctorProfile_Normal() {
        Doctor doc = new Doctor();
        EditDoctorProfileRequest req = new EditDoctorProfileRequest();
        req.setFullName("Updated Profile Name");
        req.setEmail("profile@test.com");
        req.setPhone("111222333");
        req.setYearsOfExperience(5);
        req.setDegree("Master");
        req.setBiography("Profile bio updated");
        
        DoctorResponse docRes = new DoctorResponse();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(docRes);
        
        DoctorResponse res = doctorService.editDoctorProfile("user1", req);
        
        assertEquals("Updated Profile Name", doc.getFullName());
        assertEquals("profile@test.com", doc.getEmail());
        assertEquals("111222333", doc.getPhone());
        assertEquals(5, doc.getYearsOfExperience());
        assertEquals("Master", doc.getDegree());
        assertEquals("Profile bio updated", doc.getBiography());
        assertNotNull(res);
    }

    @Test
    void testEditDoctorProfile_Abnormal_NotFound() {
        EditDoctorProfileRequest req = new EditDoctorProfileRequest();
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.empty());
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.editDoctorProfile("user1", req));
        assertEquals("Doctor not found for username: user1", ex.getMessage());
    }

    // ==========================================
    // 9. createDoctor
    // ==========================================
    @Test
    void testCreateDoctor_Normal() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        req.setPhone("0999888777");
        req.setAvatarUrl("http://image.com/avatar.jpg");
        req.setYearsOfExperience(8);
        req.setDegree("Specialist");
        req.setBiography("Great doctor");

        Role role = new Role();
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        
        Doctor savedDoc = new Doctor();
        savedDoc.setId(99L);
        savedDoc.setEmail(req.getEmail());
        savedDoc.setFullName(req.getFullName());
        savedDoc.setUsername("newdoc");
        
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());
        
        DoctorResponse res = doctorService.createDoctor(req);
        
        assertNotNull(res);
        verify(doctorRepository).save(any(Doctor.class));
        verify(mailUtil).sendTemplateMail(eq("newdoc@test.com"), anyString(), eq("doctor-welcome"), anyMap());
    }

    @Test
    void testCreateDoctor_Abnormal_MissingEmail() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("");
        req.setFullName("New Doctor");
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(req));
        assertEquals("Email is required", ex.getMessage());
    }

    @Test
    void testCreateDoctor_Abnormal_MissingFullName() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName(null);
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(req));
        assertEquals("Full name is required", ex.getMessage());
    }

    @Test
    void testCreateDoctor_Abnormal_DuplicateEmail() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("existing@test.com");
        req.setFullName("New Doctor");
        
        User existingUser = new User();
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.of(existingUser));
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(req));
        assertEquals("Email 'existing@test.com' is already registered", ex.getMessage());
    }

    @Test
    void testCreateDoctor_Abnormal_DuplicatePhone() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        req.setPhone("0999888777");
        
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        
        User existingUser = new User();
        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(existingUser));
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> doctorService.createDoctor(req));
        assertEquals("Phone '0999888777' is already registered", ex.getMessage());
    }

    @Test
    void testCreateDoctor_Abnormal_RoleNotFound() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        
        when(userRepository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> doctorService.createDoctor(req));
        assertEquals("DOCTOR role not found in database", ex.getMessage());
    }
    @Test
    void testEditDoctor_PartialUpdate() {
        Doctor doc = new Doctor();
        doc.setId(1L);
        doc.setFullName("Old Name");
        doc.setEmail("old@test.com");
        
        EditDoctorRequest req = new EditDoctorRequest(); // All fields null
        
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());
        
        doctorService.editDoctor(1L, req);
        
        assertEquals("Old Name", doc.getFullName());
        assertEquals("old@test.com", doc.getEmail());
        assertNull(doc.getPhone());
    }

    @Test
    void testEditDoctor_AvatarEdgeCases() {
        Doctor doc = new Doctor();
        Image existingAvatar = new Image();
        existingAvatar.setFilePath("old.png");
        doc.setAvatar(existingAvatar);
        
        EditDoctorRequest req = new EditDoctorRequest();
        req.setAvatarUrl("http://avatar.com/newfile"); // No extension
        
        when(doctorRepository.findDetailsById(1L)).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());
        
        doctorService.editDoctor(1L, req);
        
        assertEquals("http://avatar.com/newfile", doc.getAvatar().getFilePath());
        assertNull(doc.getAvatar().getExtension());
    }

    @Test
    void testEditDoctorProfile_PartialUpdate() {
        Doctor doc = new Doctor();
        doc.setFullName("Old Name");
        
        EditDoctorProfileRequest req = new EditDoctorProfileRequest(); // All fields null
        
        when(doctorRepository.findProfileByUsername("user1")).thenReturn(Optional.of(doc));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doc);
        when(doctorMapper.toResponse(doc)).thenReturn(new DoctorResponse());
        
        doctorService.editDoctorProfile("user1", req);
        
        assertEquals("Old Name", doc.getFullName());
    }

    @Test
    void testCreateDoctor_NoPhoneAndNoAvatar() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("newdoc@test.com");
        req.setFullName("New Doctor");
        
        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        
        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());
        
        doctorService.createDoctor(req);
        
        verify(userRepository, never()).findByPhone(anyString());
        verify(doctorRepository).save(argThat(d -> d.getPhone() == null && d.getAvatar() == null));
    }

    @Test
    void testCreateDoctor_UsernameCollisionAndMailException() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setFullName("New Doctor");
        
        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        
        // Mock username collision: "test" exists, "test1" exists, "test2" available
        when(userRepository.findByUsername("test")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("test1")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("test2")).thenReturn(Optional.empty());
        
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        
        lenient().doThrow(new RuntimeException("Mail server down")).when(mailUtil)
            .sendTemplateMail(anyString(), anyString(), anyString(), anyMap());
            
        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());
        
        // Should not throw exception despite mail error
        assertDoesNotThrow(() -> doctorService.createDoctor(req));
        
        verify(doctorRepository).save(argThat(d -> d.getUsername().equals("test2")));
    }

    @Test
    void testCreateDoctor_InvalidEmailBase() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("!!!@test.com"); // base will be empty after regex
        req.setFullName("New Doctor");
        
        Role role = new Role();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("DOCTOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        
        Doctor savedDoc = new Doctor();
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoc);
        when(doctorMapper.toResponse(savedDoc)).thenReturn(new DoctorResponse());
        
        doctorService.createDoctor(req);
        
        verify(doctorRepository).save(argThat(d -> d.getUsername().equals("doctor")));
    }
    @Test
    void testCreateDoctorRequest_Validation_Email() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setFullName("Valid Name");
        req.setPhone("0901234567");
        
        // Null
        req.setEmail(null);
        assertFalse(validator.validate(req).isEmpty());
        
        // Blank
        req.setEmail(" ");
        assertFalse(validator.validate(req).isEmpty());
        
        // Invalid format
        req.setEmail("invalid-email");
        assertFalse(validator.validate(req).isEmpty());
        
        // > 150 chars
        req.setEmail("a".repeat(150) + "@test.com");
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void testCreateDoctorRequest_Validation_FullName() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setPhone("0901234567");
        
        // Null
        req.setFullName(null);
        assertFalse(validator.validate(req).isEmpty());
        
        // Blank
        req.setFullName(" ");
        assertFalse(validator.validate(req).isEmpty());
        
        // > 100 chars
        req.setFullName("a".repeat(101));
        assertFalse(validator.validate(req).isEmpty());
    }

    @Test
    void testCreateDoctorRequest_Validation_Phone() {
        CreateDoctorRequest req = new CreateDoctorRequest();
        req.setEmail("test@test.com");
        req.setFullName("Valid Name");
        
        // Null
        req.setPhone(null);
        assertFalse(validator.validate(req).isEmpty());
        
        // Invalid format
        req.setPhone("090abcd123");
        assertFalse(validator.validate(req).isEmpty());
        
        // > 20 chars
        req.setPhone("1".repeat(21));
        assertFalse(validator.validate(req).isEmpty());
    }
}
