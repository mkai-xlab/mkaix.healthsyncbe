package com.g93.be.service.impl;

import com.g93.be.common.util.MailUtil;
import com.g93.be.dto.SmtpTestResponse;
import com.g93.be.service.MailTestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MailTestServiceImpl implements MailTestService {

    private final MailUtil mailUtil;
    private final String provider;

    public MailTestServiceImpl(
            MailUtil mailUtil,
            @Value("${app.mail.provider:maildev}") String provider) {
        this.mailUtil = mailUtil;
        this.provider = provider;
    }

    @Override
    public SmtpTestResponse queueTestEmail(String recipient) {
        mailUtil.sendPlainTextMail(
                recipient,
                "HealthSync SMTP test",
                "HealthSync successfully queued this test email using the " + provider + " provider."
        );
        return new SmtpTestResponse("QUEUED", provider, recipient);
    }
}
