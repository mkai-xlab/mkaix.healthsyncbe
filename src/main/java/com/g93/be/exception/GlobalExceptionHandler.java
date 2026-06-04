package com.g93.be.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Xử lý ngoại lệ khi người dùng đăng nhập lần đầu tiên mà chưa đổi mật khẩu.
     * Chỉ ghi nhận log thông báo ngắn gọn, tuyệt đối không in ra stack trace lỗi lên màn hình/console.
     */
    @ExceptionHandler(FirstTimeLoginException.class)
    public ResponseEntity<Map<String, Object>> handleFirstTimeLogin(FirstTimeLoginException ex) {
        // Ghi log ngắn gọn mức độ WARN/INFO, không in toàn bộ stack trace (ex)
        log.warn("Đăng nhập thất bại: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "FIRST_TIME_LOGIN_REQUIRED");
        response.put("message", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}
