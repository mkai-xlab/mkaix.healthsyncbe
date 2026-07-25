package com.g93.be.controller;

import com.g93.be.dto.SmtpTestRequest;
import com.g93.be.dto.SmtpTestResponse;
import com.g93.be.service.MailTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary local endpoint for checking the active SMTP configuration.
 * Remove this controller before deploying the application.
 */
@RestController
@RequestMapping("/mail")
@RequiredArgsConstructor
public class MailTestController {

    private final MailTestService mailTestService;

    @PostMapping("/test")
    public ResponseEntity<SmtpTestResponse> testSmtp(@Valid @RequestBody SmtpTestRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(mailTestService.queueTestEmail(request.recipient()));
    }
}
