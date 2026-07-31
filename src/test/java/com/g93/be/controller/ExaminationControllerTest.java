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
        Mockito.when(examinationService.getExaminationById(1L)).thenReturn(mockDto);

        mockMvc.perform(get("/examinations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examinationId").value(1L));
    }

    @Test
    void testGetExaminationById_Abnormal_NotFound() throws Exception {
        Mockito.when(examinationService.getExaminationById(999L))
                .thenThrow(new IllegalArgumentException("Examination with id 999 not found"));

        mockMvc.perform(get("/examinations/999"))
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
        Mockito.when(examinationService.getTotalExaminations(1L)).thenReturn(10L);

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
        Mockito.when(examinationService.getTotalExaminations(1L))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }
    
    @Test
    void testGetTotalExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalExaminations(1L))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
    }

    // ==========================================
    // 4. getTotalSevereExaminations
    // ==========================================

    @Test
    void testGetTotalSevereExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalSevereExaminations(1L)).thenReturn(5L);

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
        Mockito.when(examinationService.getTotalSevereExaminations(1L))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetTotalSevereExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalSevereExaminations(1L))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total-severe")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
    }

    // ==========================================
    // 5. getTotalVerifiedExaminations
    // ==========================================

    @Test
    void testGetTotalVerifiedExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalVerifiedExaminations(1L)).thenReturn(7L);

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
        Mockito.when(examinationService.getTotalVerifiedExaminations(1L))
                .thenThrow(new BadCredentialsException("Not authenticated"));

        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetTotalVerifiedExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalVerifiedExaminations(1L))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/examinations/total-verified")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
    }

    // ==========================================
    // 6. getTotalUnverifiedExaminations
    // ==========================================

    @Test
    void testGetTotalUnverifiedExaminations_Normal() throws Exception {
        Mockito.when(examinationService.getTotalUnverifiedExaminations(1L)).thenReturn(3L);

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
        Mockito.when(examinationService.getTotalUnverifiedExaminations(1L))
                .thenThrow(new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetTotalUnverifiedExaminations_Abnormal_AccessDenied() throws Exception {
        Mockito.when(examinationService.getTotalUnverifiedExaminations(1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền truy cập tính năng này (Access Denied)."));

        mockMvc.perform(get("/examinations/total-unverified")
                .param("userId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
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
        Mockito.when(examinationService.getTotalExaminations(1L)).thenReturn(10L);

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetMyTotalExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalExaminations(1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền truy cập tính năng này (Access Denied)."));

        mockMvc.perform(get("/examinations/my-total"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
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
        Mockito.when(examinationService.getTotalSevereExaminations(1L)).thenReturn(5L);

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetMyTotalSevereExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalSevereExaminations(1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền truy cập tính năng này (Access Denied)."));

        mockMvc.perform(get("/examinations/my-total-severe"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
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
        Mockito.when(examinationService.getTotalVerifiedExaminations(1L)).thenReturn(7L);

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetMyTotalVerifiedExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalVerifiedExaminations(1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền truy cập tính năng này (Access Denied)."));

        mockMvc.perform(get("/examinations/my-total-verified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
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
        Mockito.when(examinationService.getTotalUnverifiedExaminations(1L)).thenReturn(3L);

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_UserNotFound() throws Exception {
        setupSecurityContext("notfound@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("notfound@test.healthsync.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_Unauthenticated() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com"))
                .thenThrow(new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không chính xác."));

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu không chính xác."));
    }

    @Test
    void testGetMyTotalUnverifiedExaminations_Abnormal_AccessDenied() throws Exception {
        setupSecurityContext("user@test.healthsync.com");
        User mockUser = new User();
        mockUser.setId(1L);
        Mockito.when(userRepository.findByUsername("user@test.healthsync.com")).thenReturn(Optional.of(mockUser));
        Mockito.when(examinationService.getTotalUnverifiedExaminations(1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền truy cập tính năng này (Access Denied)."));

        mockMvc.perform(get("/examinations/my-total-unverified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập tính năng này (Access Denied)."));
    }
}
