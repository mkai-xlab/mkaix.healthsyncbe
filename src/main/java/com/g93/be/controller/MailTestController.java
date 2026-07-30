package com.g93.be.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mail-test")
public class MailTestController {

    private final JavaMailSender javaMailSender;

    public MailTestController(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @GetMapping("/send")
    public ResponseEntity<String> sendTestEmail(
            @RequestParam String to, 
            @RequestParam String title, 
            @RequestParam String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(to);
            mailMessage.setSubject(title);
            mailMessage.setText(message);
            javaMailSender.send(mailMessage);
            return ResponseEntity.ok("Thành công! Đã gửi email tới: " + to);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Gửi email thất bại. Lỗi: " + e.getMessage());
        }
    }
}
