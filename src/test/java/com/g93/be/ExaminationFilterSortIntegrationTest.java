package com.g93.be;

import com.g93.be.common.util.MailUtil;
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
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
@Transactional
public class ExaminationFilterSortIntegrationTest {

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

        doctor1Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor1, List.of()));
        doctor2Token = jwtTokenProvider.generateAccessToken(new CustomUserDetails(doctor2, List.of()));
        headOfDepartmentToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(headOfDepartment, List.of()));

        Patient patient = new Patient();
        patient.setPatientCode("P001");
        patient.setFullName("Test Patient");
        patient.setGender(Gender.MALE);
        patient = patientRepository.save(patient);

        LocalDate studyDate1 = LocalDate.of(2026, 7, 20);
        LocalDate studyDate2 = LocalDate.of(2026, 7, 21);

        // doc1: exam 1 (studyDate1) - created earliest
        createExam(patient, doctor1, studyDate1, "exam1_doc1");
        Thread.sleep(100);
        
        // doc1: exam 2 (studyDate2) - created later
        createExam(patient, doctor1, studyDate2, "exam2_doc1");
        Thread.sleep(100);

        // doc2: exam 3 (studyDate1) - created latest
        createExam(patient, doctor2, studyDate1, "exam3_doc2");
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

    private Examination createExam(Patient patient, Doctor doctor, LocalDate studyDate, String description) {
        Examination exam = new Examination();
        exam.setPatient(patient);
        exam.setDoctor(doctor);
        exam.setStatus(ExaminationStatus.VERIFIED);
        exam.setEncounterCode(java.util.UUID.randomUUID().toString());
        exam.setStudyDate(studyDate);
        exam.setDescription(description);
        // Note: createdAt is set via @PrePersist automatically
        return examinationRepository.save(exam);
    }

    @Test
    void testSortByStudyDate_Desc_HeadOfDepartment() throws Exception {
        // Head sees all 3 exams.
        // studyDate1: exam1_doc1, exam3_doc2
        // studyDate2: exam2_doc1
        // Descending by studyDate means exam2_doc1 comes first
        mockMvc.perform(get("/examinations/sort/study-date")
                .param("direction", "desc")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].description", is("exam2_doc1")));
    }

    @Test
    void testSortByStudyDate_Asc_Doctor1() throws Exception {
        mockMvc.perform(get("/examinations/sort/study-date")
                .param("direction", "asc")
                .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].description", is("exam1_doc1")));
    }

    @Test
    void testSortByUploadDate_Desc_Doctor1() throws Exception {
        // Doc 1 exams: exam1_doc1 (created first), exam2_doc1 (created second)
        // Descending order means exam2_doc1 first, then exam1_doc1
        mockMvc.perform(get("/examinations/sort/upload-date")
                .param("direction", "desc")
                .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].description", is("exam2_doc1")))
                .andExpect(jsonPath("$.content[1].description", is("exam1_doc1")));
    }

    @Test
    void testFilterByStudyDate_HeadOfDepartment() throws Exception {
        // studyDate1 has 2 exams (1 from doc1, 1 from doc2)
        mockMvc.perform(get("/examinations/filter/study-date")
                .param("date", "2026-07-20")
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void testFilterByStudyDate_Doctor2() throws Exception {
        // Doc 2 has 1 exam on studyDate1
        mockMvc.perform(get("/examinations/filter/study-date")
                .param("date", "2026-07-20")
                .header("Authorization", "Bearer " + doctor2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].description", is("exam3_doc2")));
    }

    @Test
    void testFilterByUploadDate_HeadOfDepartment() throws Exception {
        // All exams created today
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/examinations/filter/upload-date")
                .param("date", today)
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    void testFilterByUploadDate_Empty() throws Exception {
        // No exams created tomorrow
        String tomorrow = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(get("/examinations/filter/upload-date")
                .param("date", tomorrow)
                .header("Authorization", "Bearer " + headOfDepartmentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }
}
