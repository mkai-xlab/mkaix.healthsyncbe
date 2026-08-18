package com.g93.be;

import com.g93.be.entity.*;
import com.g93.be.repository.*;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class DashboardAnalyticsIntegrationTest {

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
    private jakarta.persistence.EntityManager entityManager;

    private Role doctorRole;
    private Role hodRole;

    private Doctor doctorUser1;
    private Doctor doctorUser2;
    private Doctor hodDoctor;

    private String doctor1Token;
    private String doctor2Token;
    private String hodToken;

    private Patient patient1;
    private Patient patient2;
    private Patient patient3;

    private Examination exam1;
    private Examination exam2;
    private Examination exam3;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Alter database columns that might have been added by other branches to avoid default value constraints
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY failed_login_attempts INT DEFAULT 0 NULL").executeUpdate();
        } catch (Exception ignored) {}
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY lockout_until DATETIME NULL").executeUpdate();
        } catch (Exception ignored) {}
        try {
            entityManager.createNativeQuery("ALTER TABLE users MODIFY lockout_end DATETIME NULL").executeUpdate();
        } catch (Exception ignored) {}

        // Cleanup tables
        examinationRepository.deleteAll();
        patientRepository.deleteAll();
        userRepository.deleteAll();

        // Setup Roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        hodRole = roleRepository.findByCode("DEPARTMENT_HEAD").orElseGet(() -> {
            Role r = new Role();
            r.setCode("DEPARTMENT_HEAD");
            r.setName("Department Head");
            return roleRepository.save(r);
        });

        // Setup Doctor/HOD Users
        doctorUser1 = new Doctor();
        doctorUser1.setUsername("doc_1");
        doctorUser1.setPassword(passwordEncoder.encode("password"));
        doctorUser1.setFullName("Doctor One");
        doctorUser1.setEmail("doc1.dash@hospital.com");
        doctorUser1.setPhone("0881111111");
        doctorUser1.setRole(doctorRole);
        doctorUser1.setStatus(UserStatus.ACTIVE);
        doctorUser1.setIsFirstActivated(false);
        doctorUser1.setYearsOfExperience(5);
        doctorUser1 = userRepository.save(doctorUser1);

        doctorUser2 = new Doctor();
        doctorUser2.setUsername("doc_2");
        doctorUser2.setPassword(passwordEncoder.encode("password"));
        doctorUser2.setFullName("Doctor Two");
        doctorUser2.setEmail("doc2.dash@hospital.com");
        doctorUser2.setPhone("0882222222");
        doctorUser2.setRole(doctorRole);
        doctorUser2.setStatus(UserStatus.ACTIVE);
        doctorUser2.setIsFirstActivated(false);
        doctorUser2.setYearsOfExperience(3);
        doctorUser2 = userRepository.save(doctorUser2);

        hodDoctor = new Doctor();
        hodDoctor.setUsername("hod_user");
        hodDoctor.setPassword(passwordEncoder.encode("password"));
        hodDoctor.setFullName("HOD Doctor");
        hodDoctor.setEmail("hod.dash@hospital.com");
        hodDoctor.setPhone("0883333333");
        hodDoctor.setRole(hodRole);
        hodDoctor.setStatus(UserStatus.ACTIVE);
        hodDoctor.setIsFirstActivated(false);
        hodDoctor.setYearsOfExperience(15);
        hodDoctor = userRepository.save(hodDoctor);

        // Generate tokens
        doctor1Token = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser1, Collections.emptyList()));
        doctor2Token = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser2, Collections.emptyList()));
        hodToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(hodDoctor, Collections.emptyList()));

        //  Create patients
        patient1 = new Patient();
        patient1.setFullName("Patient One");
        patient1.setPatientCode("PAT-DASH-01");
        patient1.setGender(Gender.MALE);
        patient1.setDob(LocalDate.of(1990, 1, 1));
        patient1 = patientRepository.save(patient1);

        patient2 = new Patient();
        patient2.setFullName("Patient Two");
        patient2.setPatientCode("PAT-DASH-02");
        patient2.setGender(Gender.FEMALE);
        patient2.setDob(LocalDate.of(1985, 6, 20));
        patient2 = patientRepository.save(patient2);

        patient3 = new Patient();
        patient3.setFullName("Patient Three");
        patient3.setPatientCode("PAT-DASH-03");
        patient3.setGender(Gender.MALE);
        patient3.setDob(LocalDate.of(1975, 12, 10));
        patient3 = patientRepository.save(patient3);

        // Create Examinations
        // Exam 1: doc_1, grade 2 (non-severe), NEED_VERIFY
        exam1 = new Examination();
        exam1.setPatient(patient1);
        exam1.setDoctor(doctorUser1);
        exam1.setEncounterCode("ENC-DASH-01");
        exam1.setStatus(ExaminationStatus.NEED_VERIFY);
        exam1.setMaxPredictedGrade(2);
        exam1.setStudyDate(LocalDate.now());
        exam1.setVisitTime(LocalDateTime.now());
        exam1 = examinationRepository.save(exam1);

        // Exam 2: doc_1, grade 3 (severe), VERIFIED
        exam2 = new Examination();
        exam2.setPatient(patient2);
        exam2.setDoctor(doctorUser1);
        exam2.setEncounterCode("ENC-DASH-02");
        exam2.setStatus(ExaminationStatus.VERIFIED);
        exam2.setMaxPredictedGrade(3);
        exam2.setStudyDate(LocalDate.now());
        exam2.setVisitTime(LocalDateTime.now());
        exam2 = examinationRepository.save(exam2);

        // Exam 3: doc_2, grade 4 (severe), NEED_VERIFY
        exam3 = new Examination();
        exam3.setPatient(patient3);
        exam3.setDoctor(doctorUser2);
        exam3.setEncounterCode("ENC-DASH-03");
        exam3.setStatus(ExaminationStatus.NEED_VERIFY);
        exam3.setMaxPredictedGrade(4);
        exam3.setStudyDate(LocalDate.now());
        exam3.setVisitTime(LocalDateTime.now());
        exam3 = examinationRepository.save(exam3);

        entityManager.flush();
        entityManager.clear();
    }

    // view doctor dashboard

    @Test
    void testViewDoctorDashboard_Success() throws Exception {
        // doctorUser1 has 2 exams: 1 severe, 1 verified, 1 unverified 
        mockMvc.perform(get("/examinations/my-total")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(2)));

        mockMvc.perform(get("/examinations/my-total-severe")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(1)));

        mockMvc.perform(get("/examinations/my-total-verified")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(1)));

        mockMvc.perform(get("/examinations/my-total-unverified")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(1)));
    }

    // View hod dashboard

    @Test
    void testViewHodDashboard_Success() throws Exception {
        // HOD views statistics across the whole department 
        mockMvc.perform(get("/examinations/total?userId=" + hodDoctor.getId())
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(3)));

        mockMvc.perform(get("/examinations/total-severe?userId=" + hodDoctor.getId())
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(2)));

        mockMvc.perform(get("/examinations/total-verified?userId=" + hodDoctor.getId())
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(1)));

        mockMvc.perform(get("/examinations/total-unverified?userId=" + hodDoctor.getId())
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(2)));
    }

    //View KL grade distribution

    @Test
    void testViewKlGradeDistribution_Doctor() throws Exception {
        // Doctor 1 sees stats for their own assigned exams 
        mockMvc.perform(get("/examinations/statistics/patients-by-grade")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].grade", containsInAnyOrder(2, 3)))
                .andExpect(jsonPath("$[?(@.grade == 2)].patientCount", contains(1)))
                .andExpect(jsonPath("$[?(@.grade == 3)].patientCount", contains(1)));
    }

    @Test
    void testViewKlGradeDistribution_Hod() throws Exception {
        // HOD views stats for all exams in the department 
        mockMvc.perform(get("/examinations/statistics/patients-by-grade")
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].grade", containsInAnyOrder(2, 3, 4)))
                .andExpect(jsonPath("$[?(@.grade == 2)].patientCount", contains(1)))
                .andExpect(jsonPath("$[?(@.grade == 3)].patientCount", contains(1)))
                .andExpect(jsonPath("$[?(@.grade == 4)].patientCount", contains(1)));
    }


    //Access pending reviews

    @Test
    void testAccessPendingReviews_Doctor() throws Exception {
        // Doctor 1 accesses pending reviews (status = NEED_VERIFY)
        mockMvc.perform(get("/examinations/status?status=NEED_VERIFY")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].encounterCode", is("ENC-DASH-01")))
                .andExpect(jsonPath("$.content[0].patient.id", is(patient1.getId().intValue())));
    }

    @Test
    void testAccessPendingReviews_Hod() throws Exception {
        // HOD accesses all pending reviews in the department (exam1 and exam3)
        mockMvc.perform(get("/examinations/status?status=NEED_VERIFY")
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].encounterCode", containsInAnyOrder("ENC-DASH-01", "ENC-DASH-03")));
    }

    @Test
    void testAccessPendingReviews_SortingAndPagination() throws Exception {
        // Retrieve page size = 1, expecting total elements to be 2
        mockMvc.perform(get("/examinations/status?status=NEED_VERIFY&page=0&size=1")
                        .header("Authorization", "Bearer " + hodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.totalPages", is(2)));
    }

    @Test
    void testAccessPendingReviews_Anonymous_Unauthorized() throws Exception {
        // Accessing status without authorization header returns 401/403
        mockMvc.perform(get("/examinations/status?status=NEED_VERIFY"))
                .andExpect(status().isForbidden());
    }
}
