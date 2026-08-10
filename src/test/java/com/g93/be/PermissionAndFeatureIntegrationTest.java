package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.*;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class PermissionAndFeatureIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;


    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExaminationRepository examinationRepository;
    @Autowired
    private DicomInstanceRepository dicomInstanceRepository;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Role adminRole;
    private Role doctorRole;

    private User adminUser;
    private User doctorUser;

    private String adminToken;
    private String doctorToken;

    @BeforeEach
    void setUp() {
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
            java.util.List<String> tables = jdbcTemplate.queryForList("SHOW TABLES", String.class);
            for (String table : tables) {
                if (!table.equalsIgnoreCase("roles") && !table.equalsIgnoreCase("permissions") && !table.equalsIgnoreCase("role_permissions") && !table.equalsIgnoreCase("features")) {
                    jdbcTemplate.execute("TRUNCATE TABLE " + table + ";");
                }
            }
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1;");
        } catch (Exception e) {
            e.printStackTrace();
        }

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Clear repositories to ensure isolation (Users only, lookup tables will be handled inside transactional rollback)
        

        // Fetch existing Roles from database
        adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        // Create admin user
        adminUser = new User();
        adminUser.setUsername("integration_admin");
        adminUser.setPassword(passwordEncoder.encode("admin_password"));
        adminUser.setFullName("Integration Admin");
        adminUser.setEmail("integration_admin@hospital.com");
        adminUser.setPhone("0123456780");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        userRepository.save(adminUser);

        // Create doctor user
        doctorUser = new User();
        doctorUser.setUsername("integration_doctor");
        doctorUser.setPassword(passwordEncoder.encode("doctor_password"));
        doctorUser.setFullName("Integration Doctor");
        doctorUser.setEmail("integration_doctor@hospital.com");
        doctorUser.setPhone("0123456784");
        doctorUser.setRole(doctorRole);
        doctorUser.setStatus(UserStatus.ACTIVE);
        doctorUser.setIsFirstActivated(false);
        userRepository.save(doctorUser);

        // Generate tokens
        CustomUserDetails adminDetails = new CustomUserDetails(adminUser, new ArrayList<>());
        adminToken = jwtTokenProvider.generateAccessToken(adminDetails);

        CustomUserDetails doctorDetails = new CustomUserDetails(doctorUser, new ArrayList<>());
        doctorToken = jwtTokenProvider.generateAccessToken(doctorDetails);
    }


    @Test
    void testCreateFeature_Success_AsAdmin() throws Exception {
        CreateFeatureRequest request = new CreateFeatureRequest("New Custom Module", "Description of custom module");

        mockMvc.perform(post("/features")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("New Custom Module")))
                .andExpect(jsonPath("$.description", is("Description of custom module")));
    }

    @Test
    void testCreateFeature_Forbidden_AsDoctor() throws Exception {
        CreateFeatureRequest request = new CreateFeatureRequest("New Custom Module", "Description");

        mockMvc.perform(post("/features")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateFeature_RejectsDuplicateName() throws Exception {
        // Create first feature
        CreateFeatureRequest request1 = new CreateFeatureRequest("Unique Module", "Description");
        mockMvc.perform(post("/features")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Create second feature with same name
        CreateFeatureRequest request2 = new CreateFeatureRequest("Unique Module", "Another Description");
        mockMvc.perform(post("/features")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateFeature_Success_AsAdmin() throws Exception {
        // Create feature first
        Feature feature = new Feature(null, "Initial Module", "Initial Desc");
        feature = featureRepository.save(feature);

        UpdateFeatureRequest request = new UpdateFeatureRequest("Updated Module Name", "Updated Desc");

        mockMvc.perform(put("/features/" + feature.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Module Name")))
                .andExpect(jsonPath("$.description", is("Updated Desc")));
    }

    @Test
    void testDeleteFeature_Success_AsAdmin() throws Exception {
        // Create feature first
        Feature feature = new Feature(null, "Module to Delete", "Desc");
        feature = featureRepository.save(feature);

        // Delete feature
        mockMvc.perform(delete("/features/" + feature.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify deleted from DB
        assertFalse(featureRepository.findById(feature.getId()).isPresent());
    }


    @Test
    void testGetPermissionTree_Success_AsAdmin() throws Exception {
        mockMvc.perform(get("/permissions/tree")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()))
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    void testCreatePermission_Success_AsAdmin() throws Exception {
        // Create feature first
        Feature feature = new Feature(null, "Auth Feature", "Desc");
        feature = featureRepository.save(feature);

        CreatePermissionRequest request = new CreatePermissionRequest(
                "CUSTOM_VIEW_PERM",
                "Custom View Permission",
                5,
                "Custom View",
                feature.getId(),
                null
        );

        mockMvc.perform(post("/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.code", is("CUSTOM_VIEW_PERM")))
                .andExpect(jsonPath("$.priority", is(5)))
                .andExpect(jsonPath("$.presentation", is("Custom View")));
    }

    @Test
    void testCreatePermission_RejectsDuplicateCode() throws Exception {
        Feature feature = new Feature(null, "Auth Feature 2", "Desc");
        feature = featureRepository.save(feature);

        // Save first permission
        Permission p = new Permission(null, "DUP_CODE_PERM", "Dup Name", 1, "Dup Pres", feature, null);
        permissionRepository.save(p);

        CreatePermissionRequest request = new CreatePermissionRequest(
                "DUP_CODE_PERM",
                "Another Name",
                2,
                "Another Pres",
                feature.getId(),
                null
        );

        mockMvc.perform(post("/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdatePermission_Success_AsAdmin() throws Exception {
        Feature feature = new Feature(null, "Auth Feature 3", "Desc");
        feature = featureRepository.save(feature);

        Permission p = new Permission(null, "ORIGINAL_PERM", "Original Name", 1, "Original Pres", feature, null);
        p = permissionRepository.save(p);

        UpdatePermissionRequest request = new UpdatePermissionRequest(
                "UPDATED_PERM_CODE",
                "Updated Name",
                3,
                "Updated Pres",
                null
        );

        mockMvc.perform(put("/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("UPDATED_PERM_CODE")))
                .andExpect(jsonPath("$.priority", is(3)));
    }

    @Test
    void testUpdatePermission_CircularDependency_Rejects() throws Exception {
        Feature feature = new Feature(null, "Auth Feature 4", "Desc");
        feature = featureRepository.save(feature);

        Permission p = new Permission(null, "CIRCULAR_PERM", "Circular Name", 1, "Circular Pres", feature, null);
        p = permissionRepository.save(p);

        UpdatePermissionRequest request = new UpdatePermissionRequest(
                "CIRCULAR_PERM",
                "Circular Name",
                1,
                "Circular Pres",
                p.getId() // Require itself
        );

        mockMvc.perform(put("/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeletePermission_Success_AsAdmin() throws Exception {
        Feature feature = new Feature(null, "Auth Feature 5", "Desc");
        feature = featureRepository.save(feature);

        Permission p = new Permission(null, "DEL_PERM", "Del Name", 1, "Del Pres", feature, null);
        p = permissionRepository.save(p);

        mockMvc.perform(delete("/permissions/" + p.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertFalse(permissionRepository.findById(p.getId()).isPresent());
    }


    @Test
    void testGetRolePermissions_Success_AsAdmin() throws Exception {
        mockMvc.perform(get("/permissions/role/DOCTOR")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    void testUpdateRolePermissions_Success_AsAdmin() throws Exception {
        Feature feature = new Feature(null, "New Feature", "Desc");
        feature = featureRepository.save(feature);

        Permission p1 = new Permission(null, "PERM_TEST_1", "Test 1", 1, "Pres 1", feature, null);
        Permission p2 = new Permission(null, "PERM_TEST_2", "Test 2", 2, "Pres 2", feature, null);
        p1 = permissionRepository.save(p1);
        p2 = permissionRepository.save(p2);

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest(List.of(p1.getId(), p2.getId()));

        mockMvc.perform(put("/permissions/role/DOCTOR")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify updated role permissions in database
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleId(doctorRole.getId());
        assertEquals(2, rolePermissions.size());
        assertTrue(rolePermissions.stream().anyMatch(rp -> rp.getPermission().getCode().equals("PERM_TEST_1")));
        assertTrue(rolePermissions.stream().anyMatch(rp -> rp.getPermission().getCode().equals("PERM_TEST_2")));
    }
}
