package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UpdateUserRoleRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailUtil mailUtil;
    @Spy
    private com.g93.be.mapper.UserMapper userMapper = new com.g93.be.mapper.UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    private Validator validator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "loginUrl", "http://localhost:3000/login");
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==========================================
    // 1. createUser (11 Test Cases)
    // ==========================================

    @Test
    void testCreateUser_Normal_Doctor() { // UTC01
        CreateUserRequest req = new CreateUserRequest("New Doctor", "doc@gmail.com", "0901234567", 2L);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Validation should pass");

        Role role = new Role();
        role.setId(2L);
        role.setCode("DOCTOR");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setUsername("doc");
        savedUser.setFullName("New Doctor");
        savedUser.setEmail("doc@gmail.com");
        savedUser.setRole(role);
        savedUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("doc@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        assertEquals("doc", res.getUsername());
        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        assertEquals("DOCTOR", savedUserCaptor.getValue().getUserType());
        verify(mailUtil).sendTemplateMail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testCreateUser_Normal_HeadOfDepartment() { // UTC02
        CreateUserRequest req = new CreateUserRequest("Head Doctor", "head@test.com", "0902223334", 3L);

        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());

        Role role = new Role();
        role.setId(3L);
        role.setCode("HEAD_OF_DEPARTMENT");

        User savedUser = new User();
        savedUser.setId(11L);
        savedUser.setUsername("head");
        savedUser.setFullName("Head Doctor");
        savedUser.setEmail("head@test.com");
        savedUser.setRole(role);
        savedUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("head@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0902223334")).thenReturn(Optional.empty());
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testCreateUser_Abnormal_EmailExists() { // UTC03
        CreateUserRequest req = new CreateUserRequest("New Doctor", "doc@gmail.com", "0901234567", 2L);

        when(userRepository.findByEmail("doc@gmail.com")).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Email is already registered", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateUser_Boundary_UsernameDuplicate() { // UTC04
        CreateUserRequest req = new CreateUserRequest("New Doctor", "abc@gmail.com", "0901234567", 2L);

        Role role = new Role();
        role.setCode("DOCTOR");

        User savedUser = new User();
        savedUser.setId(13L);
        savedUser.setUsername("abc2"); // System auto adds 2
        savedUser.setFullName("New Doctor");
        savedUser.setEmail("abc@gmail.com");
        savedUser.setRole(role);
        savedUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("abc@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        
        // Mock DB returns existing user for "abc" and "abc1", but empty for "abc2"
        when(userRepository.findByUsername("abc")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("abc1")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("abc2")).thenReturn(Optional.empty());

        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse res = userService.createUser(req);
        assertEquals("abc2", res.getUsername()); // Username tự sinh thêm hậu tố
    }

    @Test
    void testCreateUser_Abnormal_PhoneExists() { // UTC05
        CreateUserRequest req = new CreateUserRequest("New Doctor", "new@gmail.com", "0901234567", 2L);

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Phone '0901234567' is already registered", ex.getMessage());
    }

    @Test
    void testCreateUser_Abnormal_AdminRole() { // UTC06
        CreateUserRequest req = new CreateUserRequest("New Doctor", "new@gmail.com", "0901234567", 1L);

        Role role = new Role();
        role.setCode("ADMIN");

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.empty());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Cannot create a user with the ADMIN role via this endpoint", ex.getMessage());
    }

    @Test
    void testCreateUser_Abnormal_RoleNotFound() { // UTC07
        CreateUserRequest req = new CreateUserRequest("New Doctor", "new@gmail.com", "0901234567", 99L);

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("0901234567")).thenReturn(Optional.empty());
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Role not found", ex.getMessage());
    }

    @Test
    void testCreateUser_Abnormal_NameHasNumbers() { // UTC08
        CreateUserRequest req = new CreateUserRequest("John123", "new@gmail.com", "0901234567", 2L);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Họ và tên chỉ được chứa chữ cái và khoảng trắng")));
    }

    @Test
    void testCreateUser_Abnormal_PhoneTooShort() { // UTC09
        CreateUserRequest req = new CreateUserRequest("John Doe", "new@gmail.com", "0901", 2L);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Phone must be exactly 10 digits")));
    }

    @Test
    void testCreateUser_Abnormal_PhoneBlank() { // UTC10
        CreateUserRequest req = new CreateUserRequest("John Doe", "new@gmail.com", "", 2L);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Phone number cannot be blank") || v.getMessage().contains("Phone must be exactly 10 digits")));
    }

    @Test
    void testCreateUser_Abnormal_NameBlank() { // UTC11
        CreateUserRequest req = new CreateUserRequest("", "new@gmail.com", "0901234567", 2L);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(req);
        
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Full name cannot be blank")));
    }

    @Test
    void testCreateUser_Abnormal_DbConnectionFailure() { // UTC11_DB
        CreateUserRequest req = new CreateUserRequest("New Doctor", "new@gmail.com", "0901234567", 2L);

        Role role = new Role();
        role.setId(2L);
        role.setCode("DOCTOR");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("DB Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createUser(req));
        assertEquals("DB Connection refused", ex.getMessage());
    }

    // ==========================================
    // 2. countDoctors (5 Test Cases)
    // ==========================================

    @Test
    void testCountDoctors_Normal_Admin() { // UTC12
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("DOCTOR")).thenReturn(15L);

        long count = userService.countDoctors("adminUser");
        assertEquals(15L, count);
    }

    @Test
    void testCountDoctors_Normal_HeadOfDepartment() { // UTC13
        User head = new User();
        Role role = new Role();
        role.setCode("HEAD_OF_DEPARTMENT");
        head.setRole(role);

        when(userRepository.findByUsername("headUser")).thenReturn(Optional.of(head));
        when(userRepository.countByRoleCode("DOCTOR")).thenReturn(10L);

        long count = userService.countDoctors("headUser");
        assertEquals(10L, count);
    }

    @Test
    void testCountDoctors_Abnormal_DoctorRole() { // UTC14
        User doc = new User();
        Role role = new Role();
        role.setCode("DOCTOR");
        doc.setRole(role);

        when(userRepository.findByUsername("docUser")).thenReturn(Optional.of(doc));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.countDoctors("docUser"));
        assertEquals("Only Admin or Head of Department can view the total number of doctors.", ex.getMessage());
    }

    @Test
    void testCountDoctors_Abnormal_UserNotFound() { // UTC15
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.countDoctors("ghost"));
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void testCountDoctors_Boundary_EmptyDB() { // UTC16
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("DOCTOR")).thenReturn(0L);

        long count = userService.countDoctors("adminUser");
        assertEquals(0L, count);
    }

    @Test
    void testCountDoctors_Abnormal_DbConnectionFailure() { // UTC16_DB
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("DOCTOR")).thenThrow(new RuntimeException("DB Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.countDoctors("adminUser"));
        assertEquals("DB Connection refused", ex.getMessage());
    }

    // ==========================================
    // 3. countHeads (4 Test Cases)
    // ==========================================

    @Test
    void testCountHeads_Normal_Admin() { // UTC17
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("HEAD_OF_DEPARTMENT")).thenReturn(5L);

        long count = userService.countHeads("adminUser");
        assertEquals(5L, count);
    }

    @Test
    void testCountHeads_Abnormal_HeadRole() { // UTC18
        User head = new User();
        Role role = new Role();
        role.setCode("HEAD_OF_DEPARTMENT");
        head.setRole(role);

        when(userRepository.findByUsername("headUser")).thenReturn(Optional.of(head));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.countHeads("headUser"));
        assertEquals("Only Admin can view the total number of heads of department.", ex.getMessage());
    }

    @Test
    void testCountHeads_Abnormal_DoctorRole() { // UTC19
        User doc = new User();
        Role role = new Role();
        role.setCode("DOCTOR");
        doc.setRole(role);

        when(userRepository.findByUsername("docUser")).thenReturn(Optional.of(doc));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.countHeads("docUser"));
        assertEquals("Only Admin can view the total number of heads of department.", ex.getMessage());
    }

    @Test
    void testCountHeads_Boundary_EmptyDB() { // UTC20
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("HEAD_OF_DEPARTMENT")).thenReturn(0L);

        long count = userService.countHeads("adminUser");
        assertEquals(0L, count);
    }

    @Test
    void testCountHeads_Abnormal_DbConnectionFailure() { // UTC20_DB
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("HEAD_OF_DEPARTMENT")).thenThrow(new RuntimeException("DB Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.countHeads("adminUser"));
        assertEquals("DB Connection refused", ex.getMessage());
    }

    @Test
    void updateUserRole_AdminCanPromoteDoctorToHeadOfDepartment() {
        User actor = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "DOCTOR");
        Role headRole = role(3L, "HEAD_OF_DEPARTMENT");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(headRole));
        when(userRepository.save(target)).thenReturn(target);

        UserResponse response = userService.updateUserRole(2L, new UpdateUserRoleRequest(3L), "admin");

        assertSame(headRole, target.getRole());
        assertEquals("DOCTOR", target.getUserType());
        assertEquals("HEAD_OF_DEPARTMENT", response.getRole().getCode());
        assertEquals("DOCTOR", response.getUserType());
        verify(userRepository).save(target);
    }

    @Test
    void updateUserRole_AdminCanDemoteHeadOfDepartmentToDoctor() {
        User actor = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "HEAD_OF_DEPARTMENT");
        Role doctorRole = role(2L, "DOCTOR");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(doctorRole));
        when(userRepository.save(target)).thenReturn(target);

        UserResponse response = userService.updateUserRole(2L, new UpdateUserRoleRequest(2L), "admin");

        assertEquals("DOCTOR", target.getRole().getCode());
        assertEquals("DOCTOR", target.getUserType());
        assertEquals("DOCTOR", response.getRole().getCode());
    }

    @Test
    void updateUserRole_NonAdminIsRejected() {
        User actor = userWithRole(1L, "DOCTOR");

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(actor));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                () -> userService.updateUserRole(2L, new UpdateUserRoleRequest(3L), "doctor"));

        assertEquals("Only Admin can update user roles", exception.getMessage());
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRole_AdminRoleAndSelfChangesAreRejected() {
        User actor = userWithRole(1L, "ADMIN");
        Role adminRole = role(1L, "ADMIN");
        User targetAdmin = userWithRole(2L, "ADMIN");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));

        IllegalArgumentException selfChange = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(1L, new UpdateUserRoleRequest(2L), "admin"));
        assertEquals("An admin cannot change their own role", selfChange.getMessage());

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        IllegalArgumentException adminTarget = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(2L, new UpdateUserRoleRequest(2L), "admin"));
        assertEquals("An admin role cannot be changed via this endpoint", adminTarget.getMessage());

        User target = userWithRole(3L, "DOCTOR");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(adminRole));
        IllegalArgumentException adminAssignment = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUserRole(3L, new UpdateUserRoleRequest(1L), "admin"));
        assertEquals("Cannot assign the ADMIN role via this endpoint", adminAssignment.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    private User userWithRole(Long id, String code) {
        User user = new User();
        user.setId(id);
        user.setRole(role(id, code));
        user.setStatus(UserStatus.ACTIVE);
        user.setUserType("ADMIN".equals(code) ? "ADMIN" : "DOCTOR");
        user.setUsername(code.toLowerCase());
        user.setFullName("Test " + code);
        user.setEmail(code.toLowerCase() + "@test.com");
        return user;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        return role;
    }

    // ==========================================
    // 5. searchStaff (1 Test Case)
    // ==========================================

    @Test
    void testSearchStaff_Normal() {
        org.springframework.data.domain.Page<User> mockPage = new org.springframework.data.domain.PageImpl<>(
                java.util.List.of(userWithRole(2L, "DOCTOR"))
        );
        when(userRepository.searchStaff(
                eq(java.util.List.of("HEAD_OF_DEPARTMENT", "DOCTOR")),
                eq("doctor"),
                eq(UserStatus.ACTIVE),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(mockPage);

        org.springframework.data.domain.Page<UserResponse> result = userService.searchStaff("doctor", UserStatus.ACTIVE, 0, 10);
        
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("doctor", result.getContent().get(0).getUsername());
    }

    // ==========================================
    // 6. toggleUserStatus (5 Test Cases)
    // ==========================================

    @Test
    void testToggleUserStatus_Deactivate_Normal() {
        User admin = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "DOCTOR");
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);

        com.g93.be.dto.ToggleStatusRequest req = new com.g93.be.dto.ToggleStatusRequest();
        req.setInactiveReason("Vi phạm nội quy");

        UserResponse res = userService.toggleUserStatus(2L, req, "admin");

        assertEquals(UserStatus.INACTIVE.name(), res.getStatus());
        assertEquals("Vi phạm nội quy", target.getInactiveReason());
        verify(mailUtil).sendPlainTextMail(eq("doctor@test.com"), anyString(), anyString());
    }

    @Test
    void testToggleUserStatus_Activate_Normal() {
        User admin = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "DOCTOR");
        target.setStatus(UserStatus.INACTIVE);
        target.setInactiveReason("Cũ");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenReturn(target);

        com.g93.be.dto.ToggleStatusRequest req = new com.g93.be.dto.ToggleStatusRequest();

        UserResponse res = userService.toggleUserStatus(2L, req, "admin");

        assertEquals(UserStatus.ACTIVE.name(), res.getStatus());
        assertNull(target.getInactiveReason());
        verify(mailUtil).sendPlainTextMail(eq("doctor@test.com"), anyString(), anyString());
    }

    @Test
    void testToggleUserStatus_Deactivate_MissingReason_ThrowsException() {
        User admin = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "DOCTOR");
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        com.g93.be.dto.ToggleStatusRequest req = new com.g93.be.dto.ToggleStatusRequest();
        req.setInactiveReason(""); // Blank

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> userService.toggleUserStatus(2L, req, "admin"));
        
        assertEquals("Inactive reason is required when deactivating a user", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testToggleUserStatus_TargetIsAdmin_ThrowsException() {
        User admin = userWithRole(1L, "ADMIN");
        User target = userWithRole(2L, "ADMIN"); // Target is ADMIN

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
            () -> userService.toggleUserStatus(2L, new com.g93.be.dto.ToggleStatusRequest(), "admin"));
        
        assertEquals("Cannot modify the status of an ADMIN user via this endpoint", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testToggleUserStatus_UserNotFound_ThrowsException() {
        User admin = userWithRole(1L, "ADMIN");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        com.g93.be.exception.ResourceNotFoundException ex = assertThrows(com.g93.be.exception.ResourceNotFoundException.class, 
            () -> userService.toggleUserStatus(99L, new com.g93.be.dto.ToggleStatusRequest(), "admin"));
        
        assertTrue(ex.getMessage().contains("not found"));
    }
}
