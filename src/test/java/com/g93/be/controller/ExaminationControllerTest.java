package com.g93.be.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g93.be.dto.ExaminationDto;
import com.g93.be.exception.GlobalExceptionHandler;
import com.g93.be.repository.UserRepository;
import com.g93.be.service.ExaminationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.g93.be.entity.User;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExaminationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExaminationService examinationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExaminationController examinationController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(examinationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username) {
        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        Mockito.when(authentication.getPrincipal()).thenReturn(username);
        SecurityContextHolder.setContext(securityContext);
    }

    // ==========================================
    // 1. getExaminationById
    // ==========================================

    @Test
    void testGetExaminationById_Normal() throws Exception {
        ExaminationDto mockDto = new ExaminationDto();
        mockDto.setExaminationId(1L);
        Mockito.when(examinationService.getExaminationById(1L, "testUser")).thenReturn(mockDto);

        java.security.Principal mockPrincipal = Mockito.mock(java.security.Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("testUser");

        mockMvc.perform(get("/examinations/1").principal(mockPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examinationId").value(1L));
    }

    @Test
    void testGetExaminationById_Abnormal_NotFound() throws Exception {
        Mockito.when(examinationService.getExaminationById(999L, "testUser"))
                .thenThrow(new IllegalArgumentException("Examination with id 999 not found"));

        java.security.Principal mockPrincipal = Mockito.mock(java.security.Principal.class);
        Mockito.when(mockPrincipal.getName()).thenReturn("testUser");

        mockMvc.perform(get("/examinations/999").principal(mockPrincipal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Examination with id 999 not found"));
    }

    @Test
    void testGetExaminationById_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/examinations/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: id"));
    }

    // ==========================================
    // 2. markAsViewed
    // ==========================================

    @Test
    void testMarkAsViewed_Normal() throws Exception {
        Mockito.doNothing().when(examinationService).markAsViewed(1L);

        mockMvc.perform(put("/examinations/1/view"))
                .andExpect(status().isOk());
    }

    @Test
    void testMarkAsViewed_Abnormal_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("Examination with id 999 not found"))
                .when(examinationService).markAsViewed(999L);

        mockMvc.perform(put("/examinations/999/view"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Examination with id 999 not found"));
    }

    @Test
    void testMarkAsViewed_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(put("/examinations/abc/view"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: id"));
    }

    // ==========================================
    // 3. getTotalExaminations
    // ==========================================

    @Test
    void testGetTotalExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalExaminations(eq(1L), anyBoolean())).thenReturn(10L);

        mockMvc.perform(get("/examinations/total")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    void testGetTotalExaminations_Abnormal_MissingParam() throws Exception {
        mockMvc.perform(get("/examinations/total"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: userId"));
    }

    @Test
    void testGetTotalExaminations_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/examinations/total")
                .param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: userId"));
    }

    @Test
    void testGetTotalExaminations_Abnormal_Unauthenticated() throws Exception {
        Mockito.when(examinationService.getTotalExaminations(eq(1L), anyBoolean()))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }
    
    @Test
    void testGetTotalExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 4. getTotalSevereExaminations
    // ==========================================

    @Test
    void testGetTotalSevereExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalSevereExaminations(eq(1L), anyBoolean())).thenReturn(5L);

        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void testGetTotalSevereExaminations_Abnormal_MissingParam() throws Exception {
        mockMvc.perform(get("/examinations/total-severe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: userId"));
    }

    @Test
    void testGetTotalSevereExaminations_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: userId"));
    }

    @Test
    void testGetTotalSevereExaminations_Abnormal_Unauthenticated() throws Exception {
        Mockito.when(examinationService.getTotalSevereExaminations(eq(1L), anyBoolean()))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetTotalSevereExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalSevereExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 5. getTotalVerifiedExaminations
    // ==========================================

    @Test
    void testGetTotalVerifiedExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalVerifiedExaminations(eq(1L), anyBoolean())).thenReturn(7L);

        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void testGetTotalVerifiedExaminations_Abnormal_MissingParam() throws Exception {
        mockMvc.perform(get("/examinations/total-verified"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: userId"));
    }

    @Test
    void testGetTotalVerifiedExaminations_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: userId"));
    }

    @Test
    void testGetTotalVerifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        Mockito.when(examinationService.getTotalVerifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetTotalVerifiedExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalVerifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 6. getTotalUnverifiedExaminations
    // ==========================================

    @Test
    void testGetTotalUnverifiedExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalUnverifiedExaminations(eq(1L), anyBoolean())).thenReturn(3L);

        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_MissingParam() throws Exception {
        mockMvc.perform(get("/examinations/total-unverified"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: userId"));
    }

    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_TypeMismatch() throws Exception {
        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid parameter type for: userId"));
    }

    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        Mockito.when(examinationService.getTotalUnverifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new BadCredentialsException("TÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªn ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ng nhÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­p hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­t khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©u khÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ng chÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nh xÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡c."));

        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalUnverifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 7. getMyTotalExaminations
    // ==========================================

    @Test
    void testGetMyTotalExaminations_Normal() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalExaminations(eq(1L), anyBoolean())).thenReturn(10L);

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("TÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªn ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ng nhÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­p hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­t khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©u khÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ng chÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nh xÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡c."));

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 8. getMyTotalSevereExaminations
    // ==========================================

    @Test
    void testGetMyTotalSevereExaminations_Normal() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalSevereExaminations(eq(1L), anyBoolean())).thenReturn(5L);

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("TÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªn ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ng nhÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­p hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­t khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©u khÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ng chÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nh xÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡c."));

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalSevereExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 9. getMyTotalVerifiedExaminations
    // ==========================================

    @Test
    void testGetMyTotalVerifiedExaminations_Normal() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalVerifiedExaminations(eq(1L), anyBoolean())).thenReturn(7L);

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("TÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªn ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ng nhÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­p hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­t khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©u khÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ng chÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nh xÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡c."));

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalVerifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }

    // ==========================================
    // 10. getMyTotalUnverifiedExaminations
    // ==========================================

    @Test
    void testGetMyTotalUnverifiedExaminations_Normal() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalUnverifiedExaminations(eq(1L), anyBoolean())).thenReturn(3L);

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("TÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âªn ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¹Ã…â€œÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¾ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ng nhÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­p hoÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â·c mÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­t khÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂºÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â©u khÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â´ng chÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â­nh xÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡c."));

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalUnverifiedExaminations(eq(1L), anyBoolean()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Access Denied")));
    }
}
