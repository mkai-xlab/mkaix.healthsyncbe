package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.NotificationDto;
import com.g93.be.dto.SendNotificationRequest;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class NotificationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Role doctorRole;
    private Role adminRole;

    private Doctor doctorUser1;
    private Doctor doctorUser2;
    private User adminUser;

    private String doctor1Token;
    private String doctor2Token;
    private String adminToken;

    private Notification notif1;
    private Notification notif2;
    private Notification notif3;

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
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        // Setup Roles
        doctorRole = roleRepository.findByCode("DOCTOR")
                .orElseThrow(() -> new IllegalStateException("DOCTOR role not found"));

        adminRole = roleRepository.findByCode("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setCode("ADMIN");
            r.setName("Administrator");
            return roleRepository.save(r);
        });

        // Setup Users
        doctorUser1 = new Doctor();
        doctorUser1.setUsername("doctor_one");
        doctorUser1.setPassword(passwordEncoder.encode("password"));
        doctorUser1.setFullName("Doctor One");
        doctorUser1.setEmail("doc1@hospital.com");
        doctorUser1.setPhone("0991111111");
        doctorUser1.setRole(doctorRole);
        doctorUser1.setStatus(UserStatus.ACTIVE);
        doctorUser1.setIsFirstActivated(false);
        doctorUser1.setYearsOfExperience(5);
        doctorUser1 = userRepository.save(doctorUser1);

        doctorUser2 = new Doctor();
        doctorUser2.setUsername("doctor_two");
        doctorUser2.setPassword(passwordEncoder.encode("password"));
        doctorUser2.setFullName("Doctor Two");
        doctorUser2.setEmail("doc2@hospital.com");
        doctorUser2.setPhone("0992222222");
        doctorUser2.setRole(doctorRole);
        doctorUser2.setStatus(UserStatus.ACTIVE);
        doctorUser2.setIsFirstActivated(false);
        doctorUser2.setYearsOfExperience(3);
        doctorUser2 = userRepository.save(doctorUser2);

        adminUser = new User();
        adminUser.setUsername("admin_user");
        adminUser.setPassword(passwordEncoder.encode("password"));
        adminUser.setFullName("Admin User");
        adminUser.setEmail("admin@hospital.com");
        adminUser.setPhone("0999999999");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        adminUser = userRepository.save(adminUser);

        // Generate tokens
        doctor1Token = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser1, Collections.emptyList()));
        
        doctor2Token = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(doctorUser2, Collections.emptyList()));

        adminToken = jwtTokenProvider.generateAccessToken(
                new CustomUserDetails(adminUser, Collections.emptyList()));

        // Create Notifications for doctorUser1
        notif1 = new Notification();
        notif1.setUser(doctorUser1);
        notif1.setTitle("New Diagnosis Request");
        notif1.setMessage("Patient PAT-001 is pending your verification.");
        notif1.setType("SYSTEM_ALERT");
        notif1.setIsRead(false);
        notif1 = notificationRepository.save(notif1);

        notif2 = new Notification();
        notif2.setUser(doctorUser1);
        notif2.setTitle("Report Approved");
        notif2.setMessage("Encounter report ENC-100 has been approved.");
        notif2.setType("INFO");
        notif2.setIsRead(true);
        notif2.setReadAt(LocalDateTime.now().minusHours(1));
        notif2 = notificationRepository.save(notif2);

        // Notification for doctorUser2
        notif3 = new Notification();
        notif3.setUser(doctorUser2);
        notif3.setTitle("Welcome");
        notif3.setMessage("Welcome to HealthSync.");
        notif3.setType("WELCOME");
        notif3.setIsRead(false);
        notif3 = notificationRepository.save(notif3);

        entityManager.flush();
        entityManager.clear();
    }


    // Display notification status & alerts

    @Test
    void testGetAllNotifications_Success() throws Exception {
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("New Diagnosis Request", "Report Approved")));
    }

    @Test
    void testGetUnreadNotifications_Success() throws Exception {
        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(notif1.getId().intValue())))
                .andExpect(jsonPath("$[0].title", is("New Diagnosis Request")))
                .andExpect(jsonPath("$[0].isRead", is(false)));
    }

    @Test
    void testMarkAsRead_Success() throws Exception {
        mockMvc.perform(put("/notifications/" + notif1.getId() + "/read")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is("Notification marked as read")));

        Notification dbNotif = notificationRepository.findById(notif1.getId()).orElseThrow();
        assertTrue(dbNotif.getIsRead());
        assertNotNull(dbNotif.getReadAt());
    }

    @Test
    void testMarkAsRead_NotOwner_BadRequest() throws Exception {
        mockMvc.perform(put("/notifications/" + notif1.getId() + "/read")
                        .header("Authorization", "Bearer " + doctor2Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMarkAsRead_NotFound_BadRequest() throws Exception {
        mockMvc.perform(put("/notifications/9999/read")
                        .header("Authorization", "Bearer " + doctor1Token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSendTestNotification_Success() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                doctorUser1.getId(),
                "Critical Diagnostic Alert",
                "Severe degeneration detected.",
                "URGENT",
                null
        );

        // Admin triggers a notification
        mockMvc.perform(post("/notifications/send")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is("Notification sent successfully")));

        List<Notification> alerts = notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(doctorUser1.getId());
        assertEquals(2, alerts.size());
        boolean hasUrgent = alerts.stream().anyMatch(a -> "URGENT".equals(a.getType()) && "Critical Diagnostic Alert".equals(a.getTitle()));
        assertTrue(hasUrgent);
    }

    @Test
    void testSendTestNotification_NonAdmin_Forbidden() throws Exception {
        SendNotificationRequest request = new SendNotificationRequest(
                doctorUser1.getId(),
                "Spam Alert",
                "Spam message.",
                "INFO",
                null
        );

        mockMvc.perform(post("/notifications/send")
                        .header("Authorization", "Bearer " + doctor1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSendTestNotification_ValidationErrors() throws Exception {
        // Validation Case A: userId is null
        SendNotificationRequest reqNullUser = new SendNotificationRequest(
                null,
                "Title",
                "Message",
                "INFO",
                null
        );
        mockMvc.perform(post("/notifications/send")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNullUser)))
                .andExpect(status().isBadRequest());

        // Validation Case B: title blank
        SendNotificationRequest reqBlankTitle = new SendNotificationRequest(
                doctorUser1.getId(),
                "",
                "Message",
                "INFO",
                null
        );
        mockMvc.perform(post("/notifications/send")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBlankTitle)))
                .andExpect(status().isBadRequest());
    }
}
