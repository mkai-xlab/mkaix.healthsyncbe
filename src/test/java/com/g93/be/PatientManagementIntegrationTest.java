package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.PatientDetailsResponse;
import com.g93.be.dto.PatientResponse;
import com.g93.be.dto.PermissionResponse;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class PatientManagementIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExaminationRepository examinationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Role adminRole;
    private Role doctorRole;
    private Role guestRole;

    private User adminUser;
    private User doctorUser;
    private User guestUser;

    private String adminToken;
    private String doctorToken;
    private String guestToken;

    private Patient patientMale;
    private Patient patientFemale;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();
        patientRepository.deleteAll();

        adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        guestRole = roleRepository.findByCode("GUEST").orElseGet(() -> {
            Role r = new Role();
            r.setCode("GUEST");
            r.setName("Guest");
            return roleRepository.save(r);
        });

        // Set up admin user
        adminUser = new User();
        adminUser.setUsername("patient_admin");
        adminUser.setPassword(passwordEncoder.encode("admin_password"));
        adminUser.setFullName("Patient Admin");
        adminUser.setEmail("patient_admin@hospital.com");
        adminUser.setPhone("0123456780");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        userRepository.save(adminUser);

        // Set up doctor user )
        doctorUser = new User();
        doctorUser.setUsername("patient_doctor");
        doctorUser.setPassword(passwordEncoder.encode("doctor_password"));
        doctorUser.setFullName("Patient Doctor");
        doctorUser.setEmail("patient_doctor@hospital.com");
        doctorUser.setPhone("0123456784");
        doctorUser.setRole(doctorRole);
        doctorUser.setStatus(UserStatus.ACTIVE);
        doctorUser.setIsFirstActivated(false);
        userRepository.save(doctorUser);

        // Set up guest user 
        guestUser = new User();
        guestUser.setUsername("patient_guest");
        guestUser.setPassword(passwordEncoder.encode("guest_password"));
        guestUser.setFullName("Patient Guest");
        guestUser.setEmail("patient_guest@hospital.com");
        guestUser.setPhone("0123456788");
        guestUser.setRole(guestRole);
        guestUser.setStatus(UserStatus.ACTIVE);
        guestUser.setIsFirstActivated(false);
        userRepository.save(guestUser);

        // Generate tokens with permissions
        List<PermissionResponse> adminPerms = List.of(
            new PermissionResponse(1L, "READ_PATIENT_LIST", "Read Patient List", 1, "READ_PATIENT_LIST", null),
            new PermissionResponse(2L, "VIEW_PATIENT_DETAIL", "View Patient Detail", 1, "VIEW_PATIENT_DETAIL", null),
            new PermissionResponse(3L, "CREATE_PATIENT_EXAM", "Create Patient Exam", 1, "CREATE_PATIENT_EXAM", null)
        );
        List<PermissionResponse> doctorPerms = List.of(
            new PermissionResponse(1L, "READ_PATIENT_LIST", "Read Patient List", 1, "READ_PATIENT_LIST", null),
            new PermissionResponse(2L, "VIEW_PATIENT_DETAIL", "View Patient Detail", 1, "VIEW_PATIENT_DETAIL", null),
            new PermissionResponse(3L, "CREATE_PATIENT_EXAM", "Create Patient Exam", 1, "CREATE_PATIENT_EXAM", null)
        );

        adminToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(adminUser, adminPerms));
        doctorToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctorUser, doctorPerms));
        guestToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(guestUser, new ArrayList<>()));

        // Create mock patients
        patientMale = new Patient();
        patientMale.setFullName("Alex Mercer");
        patientMale.setPatientCode("PAT-MALE-1");
        patientMale.setGender(Gender.MALE);
        patientMale.setDob(LocalDate.of(1990, 5, 15));
        patientMale.setPhone("0912345678");
        patientMale.setEmail("alex.mercer@gmail.com");
        patientRepository.save(patientMale);

        patientFemale = new Patient();
        patientFemale.setFullName("Claire Redfield");
        patientFemale.setPatientCode("PAT-FEMALE-2");
        patientFemale.setGender(Gender.FEMALE);
        patientFemale.setDob(LocalDate.of(1993, 10, 20));
        patientFemale.setPhone("0987654321");
        patientFemale.setEmail("claire.redfield@gmail.com");
        patientRepository.save(patientFemale);
    }

    // View list patient record tests

    @Test
    void testGetAllPatients_Success_AsAdmin() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void testGetAllPatients_Forbidden_WithoutAuthority() throws Exception {
        // Guest user has no authority, should be forbidden (403)
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetAllPatients_Success_WithAuthority() throws Exception {
        // Doctor user has read patient list authority
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void testGetAllPatients_Unauthorized_WithoutToken() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().isForbidden());
    }

    // Search patient record tests 
    @Test
    void testSearchPatients_FilterByDob_Success() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("dateOfBirth", "1990-05-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].patientCode", is("PAT-MALE-1")))
                .andExpect(jsonPath("$.content[0].fullName", is("Alex Mercer")));
    }

    @Test
    void testSearchPatients_FilterByFullName_Success() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("fullName", "Claire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].patientCode", is("PAT-FEMALE-2")));
    }

    @Test
    void testSearchPatients_FilterByGender_Success() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("gender", "MALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].gender", is("MALE")));
    }

    @Test
    void testSearchPatients_FilterByPhone_Success() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("phone", "0987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].phone", is("0987654321")));
    }

    @Test
    void testSearchPatients_FilterByEmail_Success() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("email", "alex.mercer@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email", is("alex.mercer@gmail.com")));
    }

    @Test
    void testSearchPatients_NoResults() throws Exception {
        mockMvc.perform(get("/patients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("fullName", "Non-existent Patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    // View paitent's examinattion history tests

    @Test
    void testGetPatientDetailsWithImages_Success_WithExaminations() throws Exception {
        // Create mock examination
        Examination exam = new Examination();
        exam.setPatient(patientMale);
        exam.setEncounterCode("ENC-100");
        exam.setStatus(ExaminationStatus.VERIFIED);
        exam.setStudyDate(LocalDate.now());
        exam.setVisitTime(LocalDateTime.now());
        exam.setReferringPhysician("Dr. Strange");
        examinationRepository.save(exam);

        Permission detailPerm = permissionRepository.findByCode("VIEW_PATIENT_DETAIL")
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode("VIEW_PATIENT_DETAIL");
                    p.setName("View Patient Detail");
                    return permissionRepository.save(p);
                });

        boolean hasPerm = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .anyMatch(rp -> rp.getPermission().getCode().equals("VIEW_PATIENT_DETAIL"));
        if (!hasPerm) {
            rolePermissionRepository.save(new RolePermission(null, adminRole, detailPerm));
        }

        mockMvc.perform(get("/patients/" + patientMale.getPatientCode() + "/details")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.patientCode", is("PAT-MALE-1")))
                .andExpect(jsonPath("$.recentExaminations", hasSize(1)))
                .andExpect(jsonPath("$.recentExaminations[0].encounterCode", is("ENC-100")));
    }

    @Test
    void testGetPatientDetailsWithImages_Success_EmptyHistory() throws Exception {
        Permission detailPerm = permissionRepository.findByCode("VIEW_PATIENT_DETAIL")
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode("VIEW_PATIENT_DETAIL");
                    p.setName("View Patient Detail");
                    return permissionRepository.save(p);
                });

        boolean hasPerm = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .anyMatch(rp -> rp.getPermission().getCode().equals("VIEW_PATIENT_DETAIL"));
        if (!hasPerm) {
            rolePermissionRepository.save(new RolePermission(null, adminRole, detailPerm));
        }

        mockMvc.perform(get("/patients/" + patientFemale.getPatientCode() + "/details")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.patientCode", is("PAT-FEMALE-2")))
                .andExpect(jsonPath("$.recentExaminations", hasSize(0)));
    }

    @Test
    void testGetPatientDetailsWithImages_Forbidden_WithoutAuthority() throws Exception {
        // Guest user has no authority, should be forbidden (403)
        mockMvc.perform(get("/patients/" + patientMale.getPatientCode() + "/details")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetPatientDetailsWithImages_NotFound() throws Exception {
        Permission detailPerm = permissionRepository.findByCode("VIEW_PATIENT_DETAIL")
                .orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode("VIEW_PATIENT_DETAIL");
                    p.setName("View Patient Detail");
                    return permissionRepository.save(p);
                });

        boolean hasPerm = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .anyMatch(rp -> rp.getPermission().getCode().equals("VIEW_PATIENT_DETAIL"));
        if (!hasPerm) {
            rolePermissionRepository.save(new RolePermission(null, adminRole, detailPerm));
        }

        mockMvc.perform(get("/patients/PAT-UNKNOWN/details")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }
}
