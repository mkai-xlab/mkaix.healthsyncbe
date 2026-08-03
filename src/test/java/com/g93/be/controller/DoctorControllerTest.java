package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.CreateDoctorRequest;
import com.g93.be.dto.DoctorResponse;
import com.g93.be.dto.EditDoctorProfileRequest;
import com.g93.be.dto.EditDoctorRequest;
import com.g93.be.dto.PageResponse;
import com.g93.be.entity.UserStatus;
import com.g93.be.exception.GlobalExceptionHandler;
import com.g93.be.service.DoctorService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DoctorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DoctorService doctorService;

    @InjectMocks
    private DoctorController doctorController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CreateDoctorRequest validCreateRequest;
    private EditDoctorRequest validEditRequest;
    private EditDoctorProfileRequest validEditProfileRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(doctorController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        validCreateRequest = new CreateDoctorRequest(
                "Test Doctor", "doctor@test.com", "0987654321", null, 5, "MD", "Bio"
        );
        validEditRequest = new EditDoctorRequest(
                "Updated Doctor", "doctor_updated@test.com", "0123456789", null, 10, "PhD", "Updated Bio"
        );
        validEditProfileRequest = new EditDoctorProfileRequest(
                "My Profile", "myprofile@test.com", "0999999999", 7, "Master", "My Bio"
        );
    }

    // ==========================================
    // 1. createDoctor Tests
    // ==========================================

    @Test
    void testCreateDoctor_Normal() throws Exception {
        DoctorResponse response = new DoctorResponse();
        response.setEmail(validCreateRequest.getEmail());
        Mockito.when(doctorService.createDoctor(any(CreateDoctorRequest.class))).thenReturn(response);

        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    void testCreateDoctor_Abnormal_NoBody() throws Exception {
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_FullName_Null() throws Exception {
        validCreateRequest.setFullName(null);
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_FullName_Blank() throws Exception {
        validCreateRequest.setFullName("   ");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_FullName_TooLong() throws Exception {
        validCreateRequest.setFullName("a".repeat(101));
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Email_Null() throws Exception {
        validCreateRequest.setEmail(null);
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Email_Blank() throws Exception {
        validCreateRequest.setEmail("   ");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Email_InvalidFormat() throws Exception {
        validCreateRequest.setEmail("invalid-email");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Email_TooLong() throws Exception {
        validCreateRequest.setEmail("a".repeat(151) + "@test.com");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Phone_Null() throws Exception {
        validCreateRequest.setPhone(null);
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Phone_Blank() throws Exception {
        validCreateRequest.setPhone("   ");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Phone_ContainsLetters() throws Exception {
        validCreateRequest.setPhone("098abc");
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Phone_TooLong() throws Exception {
        validCreateRequest.setPhone("1".repeat(21));
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateDoctor_Abnormal_Degree_TooLong() throws Exception {
        validCreateRequest.setDegree("a".repeat(101));
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 2. editDoctor Tests
    // ==========================================

    @Test
    void testEditDoctor_Normal() throws Exception {
        DoctorResponse response = new DoctorResponse();
        Mockito.when(doctorService.editDoctor(eq(1L), any(EditDoctorRequest.class))).thenReturn(response);

        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void testEditDoctor_Abnormal_NoBody() throws Exception {
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEditDoctor_Abnormal_Email_InvalidFormat() throws Exception {
        validEditRequest.setEmail("invalid-email");
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEditDoctor_Abnormal_Degree_TooLong() throws Exception {
        validEditRequest.setDegree("a".repeat(101));
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEditDoctor_Abnormal_NotFound() throws Exception {
        Mockito.when(doctorService.editDoctor(eq(999L), any(EditDoctorRequest.class)))
               .thenThrow(new IllegalArgumentException("Doctor not found"));

        mockMvc.perform(put("/doctors/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isBadRequest()); 
    }

    // ==========================================
    // 3. getProfile Tests
    // ==========================================

    @Test
    void testGetProfile_Normal() throws Exception {
        DoctorResponse response = new DoctorResponse();
        Mockito.when(doctorService.getDoctorProfile("doctor@test.com")).thenReturn(response);

        mockMvc.perform(get("/doctors/profile")
                .principal(() -> "doctor@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetProfile_Abnormal_NotFound() throws Exception {
        Mockito.when(doctorService.getDoctorProfile("doctor@test.com"))
               .thenThrow(new IllegalArgumentException("Not found"));

        mockMvc.perform(get("/doctors/profile")
                .principal(() -> "doctor@test.com"))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 4. editProfile Tests
    // ==========================================

    @Test
    void testEditProfile_Normal() throws Exception {
        DoctorResponse response = new DoctorResponse();
        Mockito.when(doctorService.editDoctorProfile(eq("doctor@test.com"), any(EditDoctorProfileRequest.class)))
               .thenReturn(response);

        mockMvc.perform(put("/doctors/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditProfileRequest))
                .principal(() -> "doctor@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    void testEditProfile_Abnormal_Email_InvalidFormat() throws Exception {
        validEditProfileRequest.setEmail("invalid-email");
        mockMvc.perform(put("/doctors/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditProfileRequest))
                .principal(() -> "doctor@test.com"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEditProfile_Abnormal_Degree_TooLong() throws Exception {
        validEditProfileRequest.setDegree("a".repeat(101));
        mockMvc.perform(put("/doctors/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditProfileRequest))
                .principal(() -> "doctor@test.com"))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 5. getActiveDoctors Tests
    // ==========================================

    @Test
    void testGetActiveDoctors_Normal() throws Exception {
        Mockito.when(doctorService.getActiveDoctors()).thenReturn(List.of(new DoctorResponse()));

        mockMvc.perform(get("/doctors/active"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 6. activateDoctor Tests
    // ==========================================

    @Test
    void testActivateDoctor_Normal() throws Exception {
        Mockito.doNothing().when(doctorService).activateDoctor(1L);

        mockMvc.perform(post("/doctors/1/activate"))
                .andExpect(status().isOk());
    }

    @Test
    void testActivateDoctor_Abnormal_NotFound() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Not found")).when(doctorService).activateDoctor(999L);

        mockMvc.perform(post("/doctors/999/activate"))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 7. deactivateDoctorPost Tests
    // ==========================================

    @Test
    void testDeactivateDoctorPost_Normal() throws Exception {
        Mockito.doNothing().when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/doctors/1/deactivate"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 8. deactivateDoctor (DELETE) Tests
    // ==========================================

    @Test
    void testDeactivateDoctor_Normal() throws Exception {
        Mockito.doNothing().when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());

        mockMvc.perform(delete("/doctors/1"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // Security Tests (401, 403) & Edge Cases
    // ==========================================

    @Test
    void testCreateDoctor_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).createDoctor(any());
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateDoctor_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).createDoctor(any());
        mockMvc.perform(post("/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEditDoctor_Abnormal_DuplicateEmail() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Email is already registered")).when(doctorService).editDoctor(eq(1L), any());
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testEditDoctor_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).editDoctor(eq(1L), any());
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testEditDoctor_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).editDoctor(eq(1L), any());
        mockMvc.perform(put("/doctors/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProfile_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).getDoctorProfile(any());
        mockMvc.perform(get("/doctors/profile").principal(() -> "user@test.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetProfile_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).getDoctorProfile(any());
        mockMvc.perform(get("/doctors/profile").principal(() -> "user@test.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testEditProfile_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).editDoctorProfile(any(), any());
        mockMvc.perform(put("/doctors/profile")
                .principal(() -> "user@test.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditProfileRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testEditProfile_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).editDoctorProfile(any(), any());
        mockMvc.perform(put("/doctors/profile")
                .principal(() -> "user@test.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validEditProfileRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetActiveDoctors_Abnormal_Empty() throws Exception {
        Mockito.when(doctorService.getActiveDoctors()).thenReturn(List.of());
        mockMvc.perform(get("/doctors/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetActiveDoctors_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).getActiveDoctors();
        mockMvc.perform(get("/doctors/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetActiveDoctors_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).getActiveDoctors();
        mockMvc.perform(get("/doctors/active"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testActivateDoctor_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).activateDoctor(1L);
        mockMvc.perform(post("/doctors/1/activate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testActivateDoctor_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).activateDoctor(1L);
        mockMvc.perform(post("/doctors/1/activate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeactivateDoctorPost_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/doctors/1/deactivate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeactivateDoctorPost_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/doctors/1/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeactivateDoctor_Abnormal_401() throws Exception {
        Mockito.doThrow(new BadCredentialsException("Unauthorized")).when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(delete("/doctors/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeactivateDoctor_Abnormal_403() throws Exception {
        Mockito.doThrow(new AccessDeniedException("Forbidden")).when(doctorService).softDeleteDoctor(1L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(delete("/doctors/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDeactivateDoctorPost_Abnormal_NotFound() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Doctor not found")).when(doctorService).softDeleteDoctor(999L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(post("/doctors/999/deactivate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeactivateDoctor_Abnormal_NotFound() throws Exception {
        Mockito.doThrow(new IllegalArgumentException("Doctor not found")).when(doctorService).softDeleteDoctor(999L, org.mockito.ArgumentMatchers.any());
        mockMvc.perform(delete("/doctors/999"))
                .andExpect(status().isBadRequest());
    }
}

