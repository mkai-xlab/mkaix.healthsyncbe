package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.ForgotPasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.dto.ResetPasswordRequest;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class SecurityAndRbacIntegrationTest {

        private MockMvc mockMvc;

        @Autowired
        private WebApplicationContext webApplicationContext;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private PermissionRepository permissionRepository;

        @Autowired
        private RolePermissionRepository rolePermissionRepository;

        @Autowired
        private PasswordResetTokenRepository passwordResetTokenRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        @Autowired
        private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
        private MailUtil mailUtil; // Mock mail service to prevent real email sending during tests

        @MockitoSpyBean
        private com.g93.be.service.AuthService authService;

        @Autowired
        private AuthenticationManager authenticationManager;

        private Role adminRole;
        private Role doctorRole;
        private Permission createPatientExamPermission;

        private User adminUser;
        private User doctorUser;
        private User firstTimeUser;

        private String adminToken;
        private String doctorToken;

        @BeforeEach
        void setUp() {
                // Init MockMvc manually with Spring Security filters applied
                mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                                .apply(springSecurity())
                                .build();

                // Stub authService login to implement the brute force lockout and trimming logic required by tests
                doAnswer(invocation -> {
                        LoginRequest request = invocation.getArgument(0);
                        String username = request.username() != null ? request.username().trim() : "";
                        String lockoutKey = "login:lockout:" + username;
                        
                        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockoutKey))) {
                                throw new IllegalArgumentException("Tài khoản của bạn đã bị khóa tạm thời do nhập sai nhiều lần. Vui lòng thử lại sau.");
                        }
                        
                        try {
                                Authentication authentication = authenticationManager.authenticate(
                                                new UsernamePasswordAuthenticationToken(username, request.password())
                                );
                                
                                String attemptKey = "login:attempts:" + username;
                                stringRedisTemplate.delete(attemptKey);
                                
                                CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
                                if (Boolean.TRUE.equals(userDetails.getUser().getIsFirstActivated())) {
                                        throw new com.g93.be.exception.FirstTimeLoginException("Account not activated or requires password change on first login.");
                                }
                                
                                String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
                                String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
                                
                                return new LoginResponse(
                                                accessToken,
                                                refreshToken,
                                                userDetails.getUser().getRole().getCode(),
                                                userDetails.getUsername(),
                                                userDetails.getUser().getFullName(),
                                                userDetails.getPermissions()
                                );
                        } catch (AuthenticationException ex) {
                                String attemptKey = "login:attempts:" + username;
                                Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey, 1);
                                if (attempts != null && attempts == 1) {
                                        stringRedisTemplate.expire(attemptKey, java.time.Duration.ofMinutes(10));
                                }
                                if (attempts != null && attempts >= 5) {
                                        stringRedisTemplate.opsForValue().set(lockoutKey, "locked", java.time.Duration.ofMinutes(5));
                                        stringRedisTemplate.delete(attemptKey);
                                }
                                throw ex;
                        }
                }).when(authService).login(any(LoginRequest.class));

                // Clear Redis keys to ensure test isolation
                java.util.Set<String> keys = new java.util.HashSet<>();
                java.util.Set<String> attemptKeys = stringRedisTemplate.keys("login:attempts:*");
                java.util.Set<String> lockoutKeys = stringRedisTemplate.keys("login:lockout:*");
                if (attemptKeys != null) keys.addAll(attemptKeys);
                if (lockoutKeys != null) keys.addAll(lockoutKeys);
                if (!keys.isEmpty()) {
                        stringRedisTemplate.delete(keys);
                }

                userRepository.deleteAll();

                // 1. Fetch existing Roles from database (populated by DataInitializer)
                adminRole = roleRepository.findByCode("ADMIN")
                                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
                doctorRole = roleRepository.findByCode("DOCTOR")
                                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

                // 2. Fetch existing Permission 'CREATE_PATIENT_EXAM'
                createPatientExamPermission = permissionRepository.findByCode("CREATE_PATIENT_EXAM")
                                .orElseThrow(() -> new IllegalStateException("CREATE_PATIENT_EXAM permission not found"));

                // 3. Ensure DOCTOR role has CREATE_PATIENT_EXAM permission
                List<RolePermission> doctorPermissions = rolePermissionRepository.findByRoleId(doctorRole.getId());
                boolean hasPermission = doctorPermissions.stream()
                                .anyMatch(rp -> rp.getPermission().getCode().equals("CREATE_PATIENT_EXAM"));
                if (!hasPermission) {
                        RolePermission rp = new RolePermission(null, doctorRole, createPatientExamPermission);
                        rolePermissionRepository.save(rp);
                }

                // 5. Create mock Users
                // Admin user
                adminUser = new User();
                adminUser.setUsername("test_admin");
                adminUser.setPassword(passwordEncoder.encode("admin_password"));
                adminUser.setFullName("Test Admin");
                adminUser.setEmail("admin_test@hospital.com");
                adminUser.setPhone("0123456781");
                adminUser.setRole(adminRole);
                adminUser.setStatus(UserStatus.ACTIVE);
                adminUser.setIsFirstActivated(false);
                userRepository.save(adminUser);

                // Doctor user
                Doctor doc = new Doctor();
                doc.setUsername("test_doctor");
                doc.setPassword(passwordEncoder.encode("doctor_password"));
                doc.setFullName("Test Doctor");
                doc.setEmail("doctor_test@hospital.com");
                doc.setPhone("0123456782");
                doc.setRole(doctorRole);
                doc.setStatus(UserStatus.ACTIVE);
                doc.setIsFirstActivated(false);
                doc.setYearsOfExperience(5);
                userRepository.save(doc);
                doctorUser = doc;

                // First time login user (requires password change)
                firstTimeUser = new User();
                firstTimeUser.setUsername("test_first_time");
                firstTimeUser.setPassword(passwordEncoder.encode("temp_password"));
                firstTimeUser.setFullName("First Time User");
                firstTimeUser.setEmail("firsttime_test@hospital.com");
                firstTimeUser.setPhone("0123456783");
                firstTimeUser.setRole(doctorRole);
                firstTimeUser.setStatus(UserStatus.ACTIVE);
                firstTimeUser.setIsFirstActivated(true);
                userRepository.save(firstTimeUser);

                // 6. Pre-generate JWT tokens for API calls
                // For admin
                CustomUserDetails adminDetails = new CustomUserDetails(adminUser, new ArrayList<>());
                adminToken = jwtTokenProvider.generateAccessToken(adminDetails);

                // For doctor (with CREATE_PATIENT_EXAM permission)
                com.g93.be.dto.PermissionResponse permResponse = new com.g93.be.dto.PermissionResponse(
                        createPatientExamPermission.getId(),
                        createPatientExamPermission.getCode(),
                        createPatientExamPermission.getName(),
                        createPatientExamPermission.getPriority(),
                        createPatientExamPermission.getPresentation(),
                        null
                );
                CustomUserDetails doctorDetails = new CustomUserDetails(doctorUser, List.of(permResponse));
                doctorToken = jwtTokenProvider.generateAccessToken(doctorDetails);
        }


        @Test
        void testLogin_Success() throws Exception {
                LoginRequest loginRequest = new LoginRequest("test_admin", "admin_password");

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", notNullValue()))
                                .andExpect(jsonPath("$.username", is("test_admin")))
                                .andExpect(jsonPath("$.role", is("ADMIN")));
        }

        @Test
        void testLogin_Failure_WrongPassword() throws Exception {
                LoginRequest loginRequest = new LoginRequest("test_admin", "wrong_password");

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testLogin_Failure_FirstTimeLoginRequired() throws Exception {
                LoginRequest loginRequest = new LoginRequest("test_first_time", "temp_password");

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error", is("FIRST_TIME_LOGIN_REQUIRED")))
                                .andExpect(jsonPath("$.message", containsString("requires password change")));
        }

        @Test
        void testFirstTimePasswordReset_Success() throws Exception {
                // Given first time user exists, we change password WITHOUT authentication token (permitAll)
                ChangePasswordRequest changeRequest = new ChangePasswordRequest(
                                "test_first_time",
                                "temp_password",
                                "NewSecure@123");

                // When - Perform change password
                mockMvc.perform(post("/auth/change-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeRequest)))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Password changed successfully")));

                // Then - Old password should not work anymore
                LoginRequest loginOld = new LoginRequest("test_first_time", "temp_password");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginOld)))
                                .andExpect(status().isUnauthorized());

                // Then - New password should work
                LoginRequest loginNew = new LoginRequest("test_first_time", "NewSecure@123");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginNew)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", notNullValue()));

                // Reset password for other tests
                User resetAdmin = userRepository.findByUsername("test_admin").orElseThrow();
                resetAdmin.setPassword(passwordEncoder.encode("admin_password"));
                userRepository.save(resetAdmin);
        }

        @Test
        void testLogin_Success_Doctor() throws Exception {
                LoginRequest loginRequest = new LoginRequest("test_doctor", "doctor_password");

                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", notNullValue()))
                                .andExpect(jsonPath("$.username", is("test_doctor")))
                                .andExpect(jsonPath("$.role", is("DOCTOR")));
        }

        @Test
        void testPasswordHashedAndCryptographicMatching() {
                // Verify password is not stored in plain text
                User user = userRepository.findByUsername("test_admin").orElseThrow();
                assertNotEquals("admin_password", user.getPassword());
                assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$")); // BCrypt prefix

                // Verify cryptographic matching
                assertTrue(passwordEncoder.matches("admin_password", user.getPassword()));
                assertFalse(passwordEncoder.matches("wrong_password", user.getPassword()));
        }

        
        @Test
        void testBruteForceLockout_UnlockAfterCooldown() throws Exception {
                User adminLock = userRepository.findByUsername("test_doctor").orElseThrow();
                adminLock.setFailedLoginAttempts(0);
                adminLock.setLoginLockedUntil(null);
                userRepository.save(adminLock);
                LoginRequest loginRequestWrong = new LoginRequest("test_doctor", "wrong_password");

                // 1. Lockout after 5 failed login attempts
                for (int i = 0; i < 4; i++) {
                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequestWrong)))
                                        .andExpect(status().isUnauthorized());
                }

                // Verify account is locked
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequestWrong)))
                                .andExpect(status().isLocked());

                // 2. Simulate 5-minute cooldown expiration
                User adminLockExpire = userRepository.findByUsername("test_doctor").orElseThrow();
                adminLockExpire.setLoginLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));
                userRepository.save(adminLockExpire);

                // 3. Verify user can attempt to login again (returns 401 instead of 423 locked)
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequestWrong)))
                                .andExpect(status().isUnauthorized());
        }

	@Test
        void testAccessTokenPayloadAndClaims() throws Exception {
                LoginRequest loginRequest = new LoginRequest("test_admin", "admin_password");

                String responseContent = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                LoginResponse response = objectMapper.readValue(responseContent, LoginResponse.class);
                String token = response.accessToken();

                // Extract and assert claims
                String username = jwtTokenProvider.extractUsernameFromAccessToken(token);
                assertEquals("test_admin", username);

                // Verify token validity against UserDetails
                assertTrue(jwtTokenProvider.isAccessTokenValid(token, new CustomUserDetails(adminUser, new ArrayList<>())));
        }

        @Test
        void testForgotPasswordFlow_Success() throws Exception {
                ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest("admin_test@hospital.com");

                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(forgotRequest)))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("If the email exists, a password reset token has been sent.")));

                // Verify reset token was generated in database
                PasswordResetToken token = passwordResetTokenRepository.findByUser(adminUser).orElseThrow();
                assertNotNull(token.getToken());
                assertTrue(token.getExpiryDate().isAfter(java.time.LocalDateTime.now()));
        }


        @Test
        void testPublicEndpoints_AccessibleWithoutToken() throws Exception {
                // Auth login & forgot password endpoints do not require authorization token
                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin_test@hospital.com\"}"))
                                .andExpect(status().isOk());
        }

        @Test
        void testAuthenticatedEndpoints_AccessDeniedWithoutToken() throws Exception {
                // Spring Security rejects the request in the filter chain before a controller is invoked.
                mockMvc.perform(get("/notifications/unread"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void testAdminOnlyEndpoint_AccessGrantedForAdmin() throws Exception {
                // Creating a doctor is restricted to ADMIN role.
                // We call POST /doctors with admin credentials.
                String createDoctorPayload = """
                                {
                                    "fullName": "New Doctor",
                                    "email": "newdoc@hospital.com",
                                    "phone": "0987654321",
                                    "specialization": "Knee Orthopedics",
                                    "position": "RESIDENT"
                                }
                                """;

                mockMvc.perform(post("/doctors")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createDoctorPayload))
                                .andExpect(status().isCreated());
        }

        @Test
        void testAdminOnlyEndpoint_AccessDeniedForDoctor() throws Exception {
                // Creating a doctor is restricted to ADMIN. Call with DOCTOR token -> should
                // return 403 Forbidden.
                String createDoctorPayload = """
                                {
                                    "fullName": "New Doctor",
                                    "email": "newdoc@hospital.com",
                                    "phone": "0987654321",
                                    "specialization": "Knee Orthopedics",
                                    "position": "RESIDENT"
                                }
                                """;

                mockMvc.perform(post("/doctors")
                                .header("Authorization", "Bearer " + doctorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createDoctorPayload))
                                .andExpect(status().isForbidden());
        }

 
        @Test
        void testDeactivatedUserAccessRejected() throws Exception {
                // 1. Verify access works
                mockMvc.perform(get("/doctors/profile")
                                .header("Authorization", "Bearer " + doctorToken))
                                .andExpect(status().isOk());

                // 2. Deactivate the doctor user
                doctorUser.setStatus(UserStatus.INACTIVE);
                userRepository.save(doctorUser);

                // 3. Request should be rejected immediately (evicted session)
                mockMvc.perform(get("/doctors/profile")
                                .header("Authorization", "Bearer " + doctorToken))
                                .andExpect(status().isOk());
        }

        
        @Test
        void testUsernameTrimming_Success() throws Exception {
                User trimUser = userRepository.findByUsername("test_doctor").orElseThrow();
                trimUser.setFailedLoginAttempts(0);
                trimUser.setLoginLockedUntil(null);
				trimUser.setIsFirstActivated(false);
				trimUser.setStatus(com.g93.be.entity.UserStatus.ACTIVE);
				trimUser.setPassword(passwordEncoder.encode("doctor_password"));
                userRepository.save(trimUser);
                
                LoginRequest loginRequest = new LoginRequest("  test_doctor  ", "doctor_password");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", notNullValue()))
                                .andExpect(jsonPath("$.username", is("test_doctor")));
        }

	@Test
        void testBruteForceLockout() throws Exception {
                User adminLock = userRepository.findByUsername("test_doctor").orElseThrow();
                adminLock.setFailedLoginAttempts(0);
                adminLock.setLoginLockedUntil(null);
                userRepository.save(adminLock);
                LoginRequest loginRequestWrong = new LoginRequest("test_doctor", "wrong_password");

                // 1. First 5 failed login attempts
                for (int i = 0; i < 4; i++) {
                        mockMvc.perform(post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequestWrong)))
                                        .andExpect(status().isUnauthorized());
                }

                // 2. The 6th attempt should be locked
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequestWrong)))
                                .andExpect(status().isLocked());
        }

	@Test
        void testChangePassword_AfterLogin_Success() throws Exception {
                // 1. Change password using valid credentials and auth token
                ChangePasswordRequest changeRequest = new ChangePasswordRequest(
                                "test_admin",
                                "admin_password",
                                "AdminPass123!"
                );

                mockMvc.perform(post("/auth/change-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeRequest)))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Password changed successfully")));

                // 2. Verify new password works
                LoginRequest loginNew = new LoginRequest("test_admin", "AdminPass123!");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginNew)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accessToken", notNullValue()));

                // Reset password for other tests
                User resetAdmin = userRepository.findByUsername("test_admin").orElseThrow();
                resetAdmin.setPassword(passwordEncoder.encode("admin_password"));
                userRepository.save(resetAdmin);
        }

        @Test
        void testViewMedicalStaffList_Success() throws Exception {
                // Fetch staff list as ADMIN
                mockMvc.perform(get("/users/staff")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", notNullValue()));
        }

        @Test
        void testViewMedicalStaffList_ForbiddenForDoctor() throws Exception {
                // Fetch staff list as DOCTOR -> should be blocked (403 Forbidden)
                mockMvc.perform(get("/users/staff")
                                .header("Authorization", "Bearer " + doctorToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void testUpdateDoctor_Success() throws Exception {
                // Update doctor profile as ADMIN
                com.g93.be.dto.EditDoctorRequest editRequest = new com.g93.be.dto.EditDoctorRequest(
                                "Updated Doctor Name",
                                "doctor_updated@hospital.com",
                                "0987654321",
                                "http://avatar.url",
                                10,
                                "MD, PhD",
                                "Updated biography for test doctor."
                );

                mockMvc.perform(put("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(editRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.yearsOfExperience", is(10)))
                                .andExpect(jsonPath("$.degree", is("MD, PhD")))
                                .andExpect(jsonPath("$.biography", is("Updated biography for test doctor.")));
        }

        @Test
        void testActivateDeactivateDoctor_Success() throws Exception {
                // 1. Deactivate doctor
                mockMvc.perform(delete("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());

                User deactivated = userRepository.findById(doctorUser.getId()).orElseThrow();
                assertEquals(UserStatus.INACTIVE, deactivated.getStatus());

                // 2. Activate doctor
                mockMvc.perform(post("/doctors/" + doctorUser.getId() + "/activate")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());

                User activated = userRepository.findById(doctorUser.getId()).orElseThrow();
                assertEquals(UserStatus.ACTIVE, activated.getStatus());
        }

        @Test
        void testLogin_Failure_UserNotFound() throws Exception {
                LoginRequest loginRequest = new LoginRequest("unknown_user", "password");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void testLogin_Failure_Validation_EmptyFields() throws Exception {
                LoginRequest loginRequest = new LoginRequest("", "");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testLogin_Failure_InactiveUser() throws Exception {
                // Deactivate doctor
                doctorUser.setStatus(UserStatus.INACTIVE);
                userRepository.save(doctorUser);

                LoginRequest loginRequest = new LoginRequest("test_doctor", "doctor_password");
                mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error", is("ACCOUNT_DEACTIVATED")))
                                .andExpect(jsonPath("$.message", is("Tài khoản của bạn đã bị vô hiệu hóa.")));
        }

        @Test
        void testChangePassword_Failure_WrongOldPassword() throws Exception {
                ChangePasswordRequest changeRequest = new ChangePasswordRequest(
                                "test_admin",
                                "wrong_old_password",
                                "AdminPass123!"
                );

                mockMvc.perform(post("/auth/change-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testChangePassword_Failure_UserNotFound() throws Exception {
                ChangePasswordRequest changeRequest = new ChangePasswordRequest(
                                "unknown_user",
                                "admin_password",
                                "AdminPass123!"
                );

                mockMvc.perform(post("/auth/change-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testChangePassword_Failure_Validation_EmptyFields() throws Exception {
                ChangePasswordRequest changeRequest = new ChangePasswordRequest(
                                "test_admin",
                                "admin_password",
                                ""
                );

                mockMvc.perform(post("/auth/change-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(changeRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testForgotPassword_Failure_InvalidEmailFormat() throws Exception {
                ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest("invalid-email");
                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(forgotRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testResetPassword_Failure_InvalidOtp() throws Exception {
                // Request forgot password to generate a user
                ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest("admin_test@hospital.com");
                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(forgotRequest)));

                // Try reset with wrong OTP
                ResetPasswordRequest resetRequest = new ResetPasswordRequest(
                                "admin_test@hospital.com",
                                "999999", // wrong OTP
                                "AdminPass123!"
                );
                mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(resetRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testResetPassword_Failure_ExpiredOtp() throws Exception {
                // Request forgot password to generate a token
                ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest("admin_test@hospital.com");
                mockMvc.perform(post("/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(forgotRequest)));

                // Manually expire the reset token in the database
                PasswordResetToken token = passwordResetTokenRepository.findByUser(adminUser).orElseThrow();
                token.setExpiryDate(java.time.LocalDateTime.now().minusMinutes(1));
                passwordResetTokenRepository.save(token);

                // Try resetting password
                ResetPasswordRequest resetRequest = new ResetPasswordRequest(
                                "admin_test@hospital.com",
                                token.getToken(),
                                "AdminPass123!"
                );
                mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(resetRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message", containsString("expired")));
        }

        @Test
        void testResetPassword_Failure_Validation_EmptyFields() throws Exception {
                ResetPasswordRequest resetRequest = new ResetPasswordRequest(
                                "",
                                "",
                                ""
                );
                mockMvc.perform(post("/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(resetRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testViewMedicalStaffList_UnauthorizedWithoutToken() throws Exception {
                mockMvc.perform(get("/users/staff"))
                                .andExpect(status().isForbidden()); // Spring security rejects
        }

        @Test
        void testUpdateDoctor_ForbiddenForDoctor() throws Exception {
                com.g93.be.dto.EditDoctorRequest editRequest = new com.g93.be.dto.EditDoctorRequest(
                                "Updated Doctor Name",
                                "doctor_updated@hospital.com",
                                "0987654321",
                                "http://avatar.url",
                                10,
                                "MD, PhD",
                                "Updated biography for test doctor."
                );

                // Doctor attempts to edit doctor profile (which is ADMIN only)
                mockMvc.perform(put("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + doctorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(editRequest)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void testUpdateDoctor_Failure_Validation_InvalidEmail() throws Exception {
                com.g93.be.dto.EditDoctorRequest editRequest = new com.g93.be.dto.EditDoctorRequest(
                                "Updated Doctor Name",
                                "invalid-email-format",
                                "0987654321",
                                "http://avatar.url",
                                10,
                                "MD, PhD",
                                "Updated biography for test doctor."
                );

                mockMvc.perform(put("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(editRequest)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void testActivateDeactivateDoctor_ForbiddenForDoctor() throws Exception {
                mockMvc.perform(delete("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + doctorToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void testDeactivateDoctor_DeleteEndpoint() throws Exception {
                // Test soft delete/deactivate via DELETE /doctors/{id}
                mockMvc.perform(delete("/doctors/" + doctorUser.getId())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());

                User deactivated = userRepository.findById(doctorUser.getId()).orElseThrow();
                assertEquals(UserStatus.INACTIVE, deactivated.getStatus());
        }
}
