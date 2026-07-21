package com.g93.be;

import com.g93.be.common.util.MailUtil;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.util.List;

@SpringBootTest
@Transactional
public class StaffListIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private MailUtil mailUtil;

    private Role headOfDepartmentRole;
    private Role adminRole;
    private Role doctorRole;

    private String headOfDepartmentToken;
    private String adminToken;
    private String doctorToken;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM dicom_instances");
        jdbcTemplate.update("DELETE FROM examinations");
        jdbcTemplate.update("DELETE FROM patients");
        jdbcTemplate.update("DELETE FROM doctors");
        jdbcTemplate.update("DELETE FROM admins");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");

        // Ensure roles exist
        headOfDepartmentRole = getOrCreateRole("HEAD_OF_DEPARTMENT", "Head of Department");
        adminRole = getOrCreateRole("ADMIN", "Administrator");
        doctorRole = getOrCreateRole("DOCTOR", "Doctor");

        // Create Users
        User headUser = createUser("head_doc", headOfDepartmentRole);
        User adminUser = createUser("admin_user", adminRole);
        User doctorUser = createUser("simple_doc", doctorRole);
        
        // Generate tokens
        headOfDepartmentToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(headUser, List.of()));
        adminToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(adminUser, List.of()));
        doctorToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctorUser, List.of()));
    }

    private Role getOrCreateRole(String code, String name) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            Role r = new Role();
            r.setCode(code);
            r.setName(name);
            return roleRepository.save(r);
        });
    }

    private User createUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFullName(username + " FullName");
        user.setEmail(username + "@test.com");
        user.setRole(role);
        user.setUserType(role.getCode());
        user.setStatus(UserStatus.ACTIVE);
        user.setIsFirstActivated(false);
        return userRepository.save(user);
    }

    @Test
    void testRetrieveStaffList_AsHeadOfDepartment() throws Exception {
        mockMvc.perform(get("/users/staff")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // HEAD_OF_DEPARTMENT + DOCTOR
                .andExpect(jsonPath("$[*].userType", hasItems("HEAD_OF_DEPARTMENT", "DOCTOR")));
    }

    @Test
    void testRetrieveStaffList_AsAdmin() throws Exception {
        mockMvc.perform(get("/users/staff")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].userType", hasItems("HEAD_OF_DEPARTMENT", "DOCTOR")));
    }

    @Test
    void testRetrieveStaffList_AsDoctor_Forbidden() throws Exception {
        mockMvc.perform(get("/users/staff")
                .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRetrieveStaffList_Unauthenticated() throws Exception {
        mockMvc.perform(get("/users/staff"))
                .andExpect(status().isForbidden());
    }
}
