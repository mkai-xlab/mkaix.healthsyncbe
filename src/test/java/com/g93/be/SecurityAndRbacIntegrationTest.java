package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.ChangePasswordRequest;
import com.g93.be.dto.LoginRequest;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

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
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
        private MailUtil mailUtil; // Mock mail service to prevent real email sending during tests

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

                // Clear repositories to ensure isolation.
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
                jdbcTemplate.update("DELETE FROM audit_logs");
                jdbcTemplate.update("DELETE FROM dicom_instances");
                jdbcTemplate.update("DELETE FROM examinations");
                jdbcTemplate.update("DELETE FROM patients");
                jdbcTemplate.update("DELETE FROM doctors");
                jdbcTemplate.update("DELETE FROM admins");
                jdbcTemplate.update("DELETE FROM users");
                jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");

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
                doctorUser = new User();
                doctorUser.setUsername("test_doctor");
                doctorUser.setPassword(passwordEncoder.encode("doctor_password"));
                doctorUser.setFullName("Test Doctor");
                doctorUser.setEmail("doctor_test@hospital.com");
                doctorUser.setPhone("0123456782");
                doctorUser.setRole(doctorRole);
                doctorUser.setStatus(UserStatus.ACTIVE);
                doctorUser.setIsFirstActivated(false);
                userRepository.save(doctorUser);

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

        // ==========================================
        // 1. AUTHENTICATION & LOGIN FLOW TESTS
        // ==========================================

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
        }

        // ==========================================
        // 2. ROLE-BASED ACCESS CONTROL (RBAC) TESTS
        // ==========================================

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
        void testFineGrainedAuthority_AccessGrantedWhenPermitted() throws Exception {
                // Creating a patient requires authority 'CREATE_PATIENT_EXAM'.
                // Doctor token possesses 'CREATE_PATIENT_EXAM' authority.
                String createPatientPayload = """
                                {
                                    "patientCode": "PAT-9999",
                                    "fullName": "John Doe",
                                    "birthDate": "1990-01-01",
                                    "gender": "MALE",
                                    "phone": "0987654322"
                                }
                                """;

                mockMvc.perform(post("/patients")
                                .header("Authorization", "Bearer " + doctorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createPatientPayload))
                                .andExpect(status().isCreated());
        }

        @Test
        void testFineGrainedAuthority_AccessDeniedWhenNotPermitted() throws Exception {
                // We will create a token for a user with NO permissions
                User unprivilegedUser = new User();
                unprivilegedUser.setUsername("test_unprivileged");
                unprivilegedUser.setPassword(passwordEncoder.encode("password"));
                unprivilegedUser.setFullName("No Perm User");
                unprivilegedUser.setEmail("noperm@hospital.com");
                unprivilegedUser.setPhone("0123456789");
                unprivilegedUser.setRole(doctorRole); // Doctor role, but we don't grant permission in custom user
                                                      // details
                userRepository.save(unprivilegedUser);

                CustomUserDetails unprivilegedDetails = new CustomUserDetails(unprivilegedUser, new ArrayList<>());
                String unprivilegedToken = jwtTokenProvider.generateAccessToken(unprivilegedDetails);

                String createPatientPayload = """
                                {
                                    "patientCode": "PAT-9999",
                                    "fullName": "John Doe",
                                    "birthDate": "1990-01-01",
                                    "gender": "MALE",
                                    "phone": "0987654322"
                                }
                                """;

                // Perform request -> Should be blocked and return 403 Forbidden
                mockMvc.perform(post("/patients")
                                .header("Authorization", "Bearer " + unprivilegedToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createPatientPayload))
                                .andExpect(status().isForbidden());
        }
}
