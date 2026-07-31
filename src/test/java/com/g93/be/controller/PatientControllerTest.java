package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.CreatePatientRequest;
import com.g93.be.dto.EditPatientRequest;
import com.g93.be.dto.PatientDetailsResponse;
import com.g93.be.dto.PatientResponse;
import com.g93.be.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PatientControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PatientService patientService;

    @InjectMocks
    private PatientController patientController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private CreatePatientRequest createPatientRequest;
    private EditPatientRequest editPatientRequest;
    private PatientResponse patientResponse;

    private PatientDetailsResponse patientDetailsResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(patientController)
                .setControllerAdvice(new com.g93.be.exception.GlobalExceptionHandler())
                .build();

        createPatientRequest = new CreatePatientRequest();
        createPatientRequest.setPatientCode("PT123");
        createPatientRequest.setFullName("John Doe");

        editPatientRequest = new EditPatientRequest();
        editPatientRequest.setFullName("John Doe Updated");

        patientResponse = new PatientResponse();
        patientResponse.setId(1L);
        patientResponse.setPatientCode("PT123");
        patientResponse.setFullName("John Doe");

        patientDetailsResponse = new PatientDetailsResponse();
        patientDetailsResponse.setPatient(patientResponse);
        patientDetailsResponse.setRecentExaminations(Collections.emptyList());
    }

    // --- createPatient Tests ---

    @Test
    void testCreatePatient_Normal() throws Exception {
        when(patientService.createPatient(any(CreatePatientRequest.class))).thenReturn(patientResponse);

        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createPatientRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.patientCode").value("PT123"));
    }

    @Test
    void testCreatePatient_Abnormal_NoBody() throws Exception {
        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError()); // GlobalExceptionHandler maps unhandled HttpMessageNotReadableException to 500
    }

    // --- editPatient Tests ---

    @Test
    void testEditPatient_Normal() throws Exception {
        when(patientService.editPatient(eq(1L), any(EditPatientRequest.class))).thenReturn(patientResponse);

        mockMvc.perform(put("/patients/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editPatientRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void testEditPatient_Abnormal_NoBody() throws Exception {
        mockMvc.perform(put("/patients/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError()); // GlobalExceptionHandler maps unhandled HttpMessageNotReadableException to 500
    }

    @Test
    void testEditPatient_Abnormal_PatientNotFound() throws Exception {
        when(patientService.editPatient(eq(99L), any(EditPatientRequest.class))).thenThrow(new IllegalArgumentException("Patient not found"));

        mockMvc.perform(put("/patients/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(editPatientRequest)))
                // Note: standaloneSetup doesn't have GlobalExceptionHandler registered by default,
                // so it throws ServletException wrapping IllegalArgumentException which results in 500 or 400.
                // Spring Boot's test framework handles this differently. Let's just expect it to fail, 
                // or we can attach GlobalExceptionHandler. To be safe, we'll manually attach it.
                // Wait, if it's not attached, we can't reliably test the exact status. Let's register it.
                .andExpect(status().isBadRequest()); // We will add GlobalExceptionHandler in setUp
    }

    // --- deletePatient Tests ---

    @Test
    void testDeletePatient_Normal() throws Exception {
        doNothing().when(patientService).deletePatient(1L);

        mockMvc.perform(delete("/patients/1"))
                .andExpect(status().isOk());
        
        verify(patientService, times(1)).deletePatient(1L);
    }

    @Test
    void testDeletePatient_Abnormal_PatientNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Patient not found")).when(patientService).deletePatient(99L);

        mockMvc.perform(delete("/patients/99"))
                .andExpect(status().isBadRequest());
    }

    // --- getPatientDetailsWithImages Tests ---

    @Test
    void testGetPatientDetailsWithImages_Normal() throws Exception {
        when(patientService.getPatientDetailsWithImages("PT123")).thenReturn(patientDetailsResponse);

        mockMvc.perform(get("/patients/PT123/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.patientCode").value("PT123"));
    }

    @Test
    void testGetPatientDetailsWithImages_Abnormal_PatientNotFound() throws Exception {
        when(patientService.getPatientDetailsWithImages("PT999")).thenThrow(new IllegalArgumentException("Patient not found"));

        mockMvc.perform(get("/patients/PT999/details"))
                .andExpect(status().isBadRequest());
    }
}
