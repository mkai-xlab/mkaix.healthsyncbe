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

@SpringBootTest
@Transactional
public class PatientGradeStatisticsIntegrationTest {

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
    void setUp() throws Exception {
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
                null, "VIEW_ANALYTIC_HISTORY", "Xem lịch sử phân tích", 10, null, null));
        doctor1Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor1, doctorPermissions));
        doctor2Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor2, doctorPermissions));
        headOfDepartmentToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(headOfDepartment, List.of()));

        // Create Patients
        Patient patient1 = createPatient("P001", "Patient 1");
        Patient patient2 = createPatient("P002", "Patient 2");
        Patient patient3 = createPatient("P003", "Patient 3");
        Patient patient4 = createPatient("P004", "Patient 4");

        // Doctor 1 interactions:
        // Patient 1: exam 1 (grade 1), exam 2 (grade 2) -> latest is grade 2
        createExam(patient1, doctor1, 1);
        Thread.sleep(100);
        createExam(patient1, doctor1, 2);

        // Patient 2: exam 1 (grade 2) -> latest is grade 2
        createExam(patient2, doctor1, 2);
        
        // Patient 3: exam 1 (grade 3) -> latest is grade 3
        createExam(patient3, doctor1, 3);

        // Doctor 2 interactions:
        // Patient 4: exam 1 (grade 4) -> latest is grade 4
        createExam(patient4, doctor2, 4);
        
        // Wait to ensure timestamp ordering
        Thread.sleep(100);

        // Doctor 2 examines Patient 1 (cross doctor patient)
        // Patient 1: exam 3 (grade 4) -> latest for patient 1 globally is grade 4. 
        // For doctor 1, latest is grade 2. For doctor 2, latest is grade 4.
        createExam(patient1, doctor2, 4);
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

    private Patient createPatient(String code, String name) {
        Patient patient = new Patient();
        patient.setPatientCode(code);
        patient.setFullName(name);
        patient.setGender(Gender.MALE);
        return patientRepository.save(patient);
    }

    private Examination createExam(Patient patient, Doctor doctor, Integer maxPredictedGrade) {
        Examination exam = new Examination();
        exam.setPatient(patient);
        exam.setDoctor(doctor);
        exam.setStatus(ExaminationStatus.VERIFIED);
        exam.setEncounterCode(java.util.UUID.randomUUID().toString());
        exam.setStudyDate(LocalDate.now());
        exam.setMaxPredictedGrade(maxPredictedGrade);
        return examinationRepository.save(exam);
    }

    @Test
    void testGetPatientGradeStatistics_AsDoctor1() throws Exception {
        // Doctor 1 has patients:
        // P1: grade 2 (latest for Doc1)
        // P2: grade 2 (latest for Doc1)
        // P3: grade 3 (latest for Doc1)
        // Total: 2 patients with grade 2, 1 patient with grade 3
        mockMvc.perform(get("/examinations/statistics/patients-by-grade")
                .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // Two distinct grades: 2 and 3
                // Check grade 2 has count 2
                .andExpect(jsonPath("$[?(@.grade == 2)].patientCount", hasItem(2)))
                // Check grade 3 has count 1
                .andExpect(jsonPath("$[?(@.grade == 3)].patientCount", hasItem(1)));
    }

    @Test
    void testGetPatientGradeStatistics_AsDoctor2() throws Exception {
        // Doctor 2 has patients:
        // P4: grade 4 (latest for Doc2)
        // P1: grade 4 (latest for Doc2)
        // Total: 2 patients with grade 4
        mockMvc.perform(get("/examinations/statistics/patients-by-grade")
                .header("Authorization", "Bearer " + doctor2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // One distinct grade: 4
                // Check grade 4 has count 2
                .andExpect(jsonPath("$[?(@.grade == 4)].patientCount", hasItem(2)));
    }

    @Test
    void testGetPatientGradeStatistics_AsHeadOfDepartment() throws Exception {
        // Head of department sees latest across all doctors:
        // P1: globally latest is grade 4 (by Doc2)
        // P2: globally latest is grade 2 (by Doc1)
        // P3: globally latest is grade 3 (by Doc1)
        // P4: globally latest is grade 4 (by Doc2)
        // Total: Grade 2 (1 patient: P2), Grade 3 (1 patient: P3), Grade 4 (2 patients: P1, P4)
        mockMvc.perform(get("/examinations/statistics/patients-by-grade")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // Distinct grades: 2, 3, 4
                .andExpect(jsonPath("$[?(@.grade == 2)].patientCount", hasItem(1)))
                .andExpect(jsonPath("$[?(@.grade == 3)].patientCount", hasItem(1)))
                .andExpect(jsonPath("$[?(@.grade == 4)].patientCount", hasItem(2)));
    }

    @Test
    void testGetPatientGradeStatistics_Unauthenticated() throws Exception {
        mockMvc.perform(get("/examinations/statistics/patients-by-grade"))
                .andExpect(status().isForbidden());
    }
}
