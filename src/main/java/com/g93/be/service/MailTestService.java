package com.g93.be.service;

import com.g93.be.dto.SmtpTestResponse;

public interface MailTestService {

    SmtpTestResponse queueTestEmail(String recipient);
}
