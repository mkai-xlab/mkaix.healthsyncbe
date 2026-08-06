package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "loginUrl", "http://localhost:3000/login");
    }

    // ==========================================
    // 1. createUser
    // ==========================================
    @Test
    void testCreateUser_Normal() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("newuser@gmail.com");
        req.setPhone("090111222");
        req.setFullName("New User");
        req.setRoleId(2L);

        Role role = new Role();
        role.setId(2L);
        role.setCode("DOCTOR");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setUsername("newuser");
        savedUser.setFullName("New User");
        savedUser.setEmail("newuser@gmail.com");
        savedUser.setPhone("090111222");
        savedUser.setRole(role);
        savedUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("newuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("090111222")).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        assertEquals("newuser", res.getUsername());
        verify(userRepository).save(any(User.class));
        verify(mailUtil).sendTemplateMail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testCreateUser_Abnormal_EmailExists() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("exist@gmail.com");

        when(userRepository.findByEmail("exist@gmail.com")).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Email is already registered", ex.getMessage());
    }

    @Test
    void testCreateUser_Abnormal_PhoneExists() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("new@gmail.com");
        req.setPhone("090123");

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("090123")).thenReturn(Optional.of(new User()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Phone '090123' is already registered", ex.getMessage());
    }

    @Test
    void testCreateUser_Abnormal_AdminRole() {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("new@gmail.com");
        req.setRoleId(1L);

        Role role = new Role();
        role.setCode("ADMIN");

        when(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
        assertEquals("Cannot create a user with the ADMIN role via this endpoint", ex.getMessage());
    }

    // ==========================================
    // 2. countDoctors
    // ==========================================
    @Test
    void testCountDoctors_Normal_Admin() {
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
    void testCountDoctors_Abnormal_DoctorRole() {
        User doc = new User();
        Role role = new Role();
        role.setCode("DOCTOR");
        doc.setRole(role);

        when(userRepository.findByUsername("docUser")).thenReturn(Optional.of(doc));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.countDoctors("docUser"));
        assertEquals("Only Admin or Head of Department can view the total number of doctors.", ex.getMessage());
    }

    // ==========================================
    // 3. countHeads
    // ==========================================
    @Test
    void testCountHeads_Normal_Admin() {
        User admin = new User();
        Role role = new Role();
        role.setCode("ADMIN");
        admin.setRole(role);

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleCode("HEAD_OF_DEPARTMENT")).thenReturn(3L);
        when(userRepository.countByRoleCode("DEPARTMENT_HEAD")).thenReturn(2L);

        long count = userService.countHeads("adminUser");
        assertEquals(5L, count);
    }

    @Test
    void testCountHeads_Abnormal_HeadRole() {
        User head = new User();
        Role role = new Role();
        role.setCode("HEAD_OF_DEPARTMENT");
        head.setRole(role);

        when(userRepository.findByUsername("headUser")).thenReturn(Optional.of(head));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.countHeads("headUser"));
        assertEquals("Only Admin can view the total number of heads of department.", ex.getMessage());
    }
}
