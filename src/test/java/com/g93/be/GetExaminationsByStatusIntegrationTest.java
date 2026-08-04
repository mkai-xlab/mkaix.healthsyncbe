package com.g93.be;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.PermissionResponse;
import com.g93.be.entity.*;
import com.g93.be.repository.*;
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
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Transactional
public class GetExaminationsByStatusIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExaminationRepository examinationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MailUtil mailUtil;

    private Doctor doctor1;
    private Doctor doctor2;
    private User headOfDepartment;
    
    private String doctor1Token;
    private String doctor2Token;
    private String headOfDepartmentToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Safe DB cleanup
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

        Role doctorRole = getOrCreateRole("DOCTOR", "Doctor");
        Role headRole = getOrCreateRole("HEAD_OF_DEPARTMENT", "Head of Department");

        doctor1 = createDoctor("doctor1", doctorRole);
        doctor2 = createDoctor("doctor2", doctorRole);
        headOfDepartment = createUser("head_user", headRole);

        List<PermissionResponse> doctorPermissions = List.of(new PermissionResponse(
                null, "VIEW_PENDING_DIAGNOSIS", "Xem chẩn đoán chờ xác nhận", 11, null, null));
        doctor1Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor1, doctorPermissions));
        doctor2Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor2, doctorPermissions));
        headOfDepartmentToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(headOfDepartment, List.of()));

        Patient patient = new Patient();
        patient.setPatientCode("P001");
        patient.setFullName("Test Patient");
        patient.setGender(Gender.MALE);
        patient = patientRepository.save(patient);

        // doctor1 has 2 NEED_VERIFY, 1 VERIFIED
        createExam(patient, doctor1, ExaminationStatus.NEED_VERIFY);
        createExam(patient, doctor1, ExaminationStatus.NEED_VERIFY);
        createExam(patient, doctor1, ExaminationStatus.VERIFIED);

        // doctor2 has 1 NEED_VERIFY, 2 VERIFIED
        createExam(patient, doctor2, ExaminationStatus.NEED_VERIFY);
        createExam(patient, doctor2, ExaminationStatus.VERIFIED);
        createExam(patient, doctor2, ExaminationStatus.VERIFIED);
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

    private Doctor createDoctor(String username, Role role) {
        Doctor doctor = new Doctor();
        doctor.setUsername(username);
        doctor.setPassword(passwordEncoder.encode("password123"));
        doctor.setFullName(username + " FullName");
        doctor.setEmail(username + "@test.com");
        doctor.setRole(role);
        doctor.setUserType(role.getCode());
        doctor.setStatus(UserStatus.ACTIVE);
        doctor.setIsFirstActivated(false);
        return userRepository.save(doctor);
    }

    private Examination createExam(Patient patient, Doctor doctor, ExaminationStatus status) {
        Examination exam = new Examination();
        exam.setPatient(patient);
        exam.setDoctor(doctor);
        exam.setStatus(status);
        exam.setEncounterCode(java.util.UUID.randomUUID().toString());
        exam.setStudyDate(LocalDate.now());
        return examinationRepository.save(exam);
    }

    @Test
    void testGetExaminationsByStatus_AsDoctor1_NeedVerify() throws Exception {
        mockMvc.perform(get("/examinations/status")
                .param("status", "NEED_VERIFY")
                .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void testGetExaminationsByStatus_AsDoctor1_Verified() throws Exception {
        mockMvc.perform(get("/examinations/status")
                .param("status", "VERIFIED")
                .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void testGetExaminationsByStatus_AsHeadOfDepartment_NeedVerify() throws Exception {
        // Head of department should see exams from all doctors (2 from doctor1 + 1 from doctor2)
        mockMvc.perform(get("/examinations/status")
                .param("status", "NEED_VERIFY")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    void testGetExaminationsByStatus_AsHeadOfDepartment_Verified() throws Exception {
        // Head of department should see exams from all doctors (1 from doctor1 + 2 from doctor2)
        mockMvc.perform(get("/examinations/status")
                .param("status", "VERIFIED")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    void testGetExaminationsByStatus_Unauthenticated() throws Exception {
        mockMvc.perform(get("/examinations/status")
                .param("status", "NEED_VERIFY"))
                .andExpect(status().isForbidden());
    }
}
