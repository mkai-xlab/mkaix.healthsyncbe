package com.g93.be.controller;

import com.g93.be.dto.SmtpTestRequest;
import com.g93.be.dto.SmtpTestResponse;
import com.g93.be.service.MailTestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailTestControllerTest {

    @Test
    void queuesSmtpTestEmailWithoutAuthentication() throws Exception {
        MailTestService mailTestService = mock(MailTestService.class);
        MailTestController controller = new MailTestController(mailTestService);
        SmtpTestRequest request = new SmtpTestRequest("recipient@example.com");
        SmtpTestResponse response = new SmtpTestResponse("QUEUED", "gmail", request.recipient());
        when(mailTestService.queueTestEmail(request.recipient())).thenReturn(response);

        var result = controller.testSmtp(request);

        assertNull(MailTestController.class
                .getMethod("testSmtp", SmtpTestRequest.class)
                .getAnnotation(PreAuthorize.class));
        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(mailTestService).queueTestEmail(request.recipient());
    }
}
