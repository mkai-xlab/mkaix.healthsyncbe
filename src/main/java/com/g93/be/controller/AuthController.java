package com.g93.be.controller;

import com.g93.be.dto.LoginRequest;
import com.g93.be.dto.LoginResponse;
import com.g93.be.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận các yêu cầu REST API liên quan đến xác thực.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint đăng nhập hệ thống.
     * 
     * @param request dữ liệu gồm username và password của người dùng.
     * @return ResponseEntity chứa thông tin token, role và username.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // Thực hiện đăng nhập qua AuthService
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
