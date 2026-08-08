package com.g93.be.controller;

import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.service.AuthService;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerLoginTest {

    @Test
    void loginDoesNotApplyBeanValidation() throws NoSuchMethodException {
        Method loginMethod = AuthController.class.getDeclaredMethod("login", LoginRequest.class);
        Annotation[] parameterAnnotations = loginMethod.getParameterAnnotations()[0];

        boolean hasValidAnnotation = List.of(parameterAnnotations).stream()
                .anyMatch(annotation -> annotation.annotationType().equals(Valid.class));

        assertTrue(hasValidAnnotation);
    }

    @Test
    void loginDelegatesRequestToAuthenticationService() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        LoginRequest request = new LoginRequest("doctor.one", "short");
        LoginResponse expected = new LoginResponse(
                "access-token",
                "refresh-token",
                "DOCTOR",
                "doctor.one",
                "Doctor One",
                List.of());
        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<LoginResponse> response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(authService).login(request);
    }
}
