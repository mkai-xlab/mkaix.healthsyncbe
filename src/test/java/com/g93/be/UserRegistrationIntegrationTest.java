package com.g93.be;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.CreateUserRequest;
import com.g93.be.dto.UserResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.UserService;
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
class UserRegistrationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MailUtil mailUtil;

    private Role headOfDepartmentRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        
        headOfDepartmentRole = roleRepository.findByName("HEAD_OF_DEPARTMENT")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("HEAD_OF_DEPARTMENT");
                    return roleRepository.save(r);
                });

        adminRole = roleRepository.findByName("ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("ADMIN");
                    return roleRepository.save(r);
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateUser_Success() {
        // Given
        CreateUserRequest request = new CreateUserRequest(
                "Jane Smith",
                "jane.smith@hospital.com",
                "0912345678",
                headOfDepartmentRole.getId()
        );

        // When
        UserResponse response = userService.createUser(request);

        // Then
        assertNotNull(response.getId());
        assertEquals("jane.smith", response.getUsername());
        assertEquals("Jane Smith", response.getFullName());
        assertEquals("jane.smith@hospital.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE.name(), response.getStatus());
        assertEquals("HEAD_OF_DEPARTMENT", response.getUserType());

        // Verify entity in DB
        Optional<User> savedUserOpt = userRepository.findById(response.getId());
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals("jane.smith", savedUser.getUsername());
        assertTrue(savedUser.getIsFirstActivated()); // Should require password change

        // Verify email was sent with password
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mailUtil).sendTemplateMail(
                eq("jane.smith@hospital.com"),
                eq("Welcome to HealthSync - Your Account Credentials"),
                eq("doctor-welcome"),
                variablesCaptor.capture()
        );

        Map<String, Object> emailVariables = variablesCaptor.getValue();
        assertEquals("Jane Smith", emailVariables.get("fullName"));
        assertEquals("jane.smith", emailVariables.get("username"));

        String sentRawPassword = (String) emailVariables.get("password");
        assertNotNull(sentRawPassword);
        assertEquals(12, sentRawPassword.length()); // Ensure length is exactly 12
        assertTrue(passwordEncoder.matches(sentRawPassword, savedUser.getPassword()));
    }

    @Test
    void testCreateUser_DuplicateEmail() {
        // Given
        CreateUserRequest request1 = new CreateUserRequest(
                "Jane Smith",
                "jane.smith@hospital.com",
                "0912345678",
                headOfDepartmentRole.getId()
        );
        userService.createUser(request1);

        CreateUserRequest request2 = new CreateUserRequest(
                "Jane Duplicate",
                "jane.smith@hospital.com", // Duplicate email
                "0987654321",
                headOfDepartmentRole.getId()
        );

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request2);
        });
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    void testCreateUser_AdminRoleBlocked() {
        // Given
        CreateUserRequest request = new CreateUserRequest(
                "Hacker User",
                "hacker@hospital.com",
                "0912345678",
                adminRole.getId() // Try to assign admin role
        );

        // When/Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request);
        });
        assertTrue(exception.getMessage().contains("Cannot create a user with the ADMIN role"));
    }


}
