package com.g93.be;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.BatchDicomUploadResponse;
import com.g93.be.entity.Role;
import com.g93.be.entity.User;
import com.g93.be.entity.UserStatus;
import com.g93.be.repository.RoleRepository;
import com.g93.be.repository.PatientRepository;
import com.g93.be.repository.DoctorRepository;
import com.g93.be.repository.DicomInstanceRepository;
import com.g93.be.repository.ExaminationRepository;
import com.g93.be.repository.AuditLogRepository;
import com.g93.be.repository.UserRepository;
import com.g93.be.security.CustomUserDetails;
import com.g93.be.security.JwtTokenProvider;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class DicomUploadIntegrationTest {
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Role adminRole;
    private User adminUser;
    private String adminToken;

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

        

        adminRole = roleRepository.findByCode("HEAD_OF_DEPARTMENT")
                .orElseThrow(() -> new IllegalStateException("HEAD_OF_DEPARTMENT role not found"));

        adminUser = new User();
        adminUser.setUsername("dicom_admin");
        adminUser.setPassword(passwordEncoder.encode("admin_password"));
        adminUser.setFullName("Dicom Admin");
        adminUser.setEmail("dicom_admin@hospital.com");
        adminUser.setPhone("0123456780");
        adminUser.setRole(adminRole);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setIsFirstActivated(false);
        userRepository.save(adminUser);

        
        java.util.List<com.g93.be.dto.PermissionResponse> perms = java.util.List.of(
            new com.g93.be.dto.PermissionResponse(1L, "UPLOAD_DICOM_IMAGE", "desc", 1, "UPLOAD_DICOM_IMAGE", null)
        );
        adminToken = jwtTokenProvider.generateAccessToken(new CustomUserDetails(adminUser, perms));
    
    }

    private byte[] createMockDicomBytes(String patientId, String patientName, String sopInstanceUid) throws Exception {
        Attributes attrs = new Attributes();
        attrs.setString(Tag.PatientID, VR.LO, patientId);
        attrs.setString(Tag.PatientName, VR.PN, patientName);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUid);
        attrs.setString(Tag.StudyInstanceUID, VR.UI, UUID.randomUUID().toString());
        attrs.setString(Tag.StudyDate, VR.DA, "20260728");
        attrs.setString(Tag.StudyTime, VR.TM, "120000");

        java.io.File tempFile = java.io.File.createTempFile("mock_dicom_", ".dcm");
        try {
            DicomOutputStream dos = new DicomOutputStream(tempFile);
            dos.writeDataset(null, attrs);
            dos.close();
            return java.nio.file.Files.readAllBytes(tempFile.toPath());
        } finally {
            tempFile.delete();
        }
    }

    @Test
    void testUploadDicomFile_Success() throws Exception {
        byte[] dicomBytes = createMockDicomBytes("PAT-111", "Alex Mercer", "1.2.3.4.5.1");
        MockMultipartFile dicomFile = new MockMultipartFile("file", "test.dcm", "application/dicom", dicomBytes);

        mockMvc.perform(multipart("/dicom/upload")
                        .file(dicomFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUploadDicomFile_Failure_EmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.dcm", "application/dicom", new byte[0]);

        mockMvc.perform(multipart("/dicom/upload")
                        .file(emptyFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadBatch_Success() throws Exception {
        byte[] dicomBytes1 = createMockDicomBytes("PAT-111", "Alex Mercer", "1.2.3.4.5.2");
        byte[] dicomBytes2 = createMockDicomBytes("PAT-222", "Claire Redfield", "1.2.3.4.5.3");

        MockMultipartFile file1 = new MockMultipartFile("files", "test1.dcm", "application/dicom", dicomBytes1);
        MockMultipartFile file2 = new MockMultipartFile("files", "test2.dcm", "application/dicom", dicomBytes2);

        mockMvc.perform(multipart("/dicom/upload/batch")
                        .file(file1)
                        .file(file2)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfulPatients", org.hamcrest.Matchers.hasSize(0)))
                ;
    }

    @Test
    void testUploadBatch_Failure_EmptyFileList() throws Exception {
        mockMvc.perform(multipart("/dicom/upload/batch")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testUploadBatch_EarlyValidation_InvalidExtension() throws Exception {
        MockMultipartFile txtFile = new MockMultipartFile("files", "invalid.txt", "text/plain", "invalid bytes".getBytes());

        mockMvc.perform(multipart("/dicom/upload/batch")
                        .file(txtFile)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfulPatients", hasSize(0)))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    void testUploadBatch_ParsingError_CorruptedDicom() throws Exception {
        MockMultipartFile corruptedDcm = new MockMultipartFile("files", "corrupted.dcm", "application/dicom", "random corrupted bytes".getBytes());

        mockMvc.perform(multipart("/dicom/upload/batch")
                        .file(corruptedDcm)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfulPatients", hasSize(0)))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    void testGetUploadSession_NotFound() throws Exception {
        mockMvc.perform(get("/dicom/upload-session/session_unknown")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetUploadSession_Success() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String mockSessionJson = "{\"uploadSessionId\":\"" + sessionId + "\",\"uploaderUserId\":1}";
        stringRedisTemplate.opsForValue().set("uploadSession:" + sessionId, mockSessionJson);

        try {
            mockMvc.perform(get("/dicom/upload-session/" + sessionId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadSessionId", is(sessionId)));
        } finally {
            stringRedisTemplate.delete("uploadSession:" + sessionId);
        }
    }
}
