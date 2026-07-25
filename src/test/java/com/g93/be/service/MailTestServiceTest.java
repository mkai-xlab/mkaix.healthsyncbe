package com.g93.be.service;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.SmtpTestResponse;
import com.g93.be.service.impl.MailTestServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailTestServiceTest {

    @Test
    void queuesTestEmailUsingConfiguredProvider() {
        MailUtil mailUtil = mock(MailUtil.class);
        MailTestService service = new MailTestServiceImpl(mailUtil, "gmail");

        SmtpTestResponse response = service.queueTestEmail("recipient@example.com");

        assertEquals("QUEUED", response.status());
        assertEquals("gmail", response.provider());
        assertEquals("recipient@example.com", response.recipient());
        verify(mailUtil).sendPlainTextMail(
                "recipient@example.com",
                "HealthSync SMTP test",
                "HealthSync successfully queued this test email using the gmail provider."
        );
    }
}
