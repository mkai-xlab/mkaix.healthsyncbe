package com.g93.be.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailProviderConfigurationTest {

    @Test
    void loadsMailDevConfigurationByDefault() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertEquals("maildev", context.getEnvironment().getProperty("app.mail.provider"));
                    assertEquals("localhost", context.getEnvironment().getProperty("spring.mail.host"));
                    assertEquals("1025", context.getEnvironment().getProperty("spring.mail.port"));
                    assertEquals("false", context.getEnvironment()
                            .getProperty("spring.mail.properties.mail.smtp.auth"));
                });
    }

    @Test
    void loadsGoogleSmtpConfigurationFromEnvironment() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "MAIL_PROVIDER=gmail",
                        "MAIL_SMTP_HOST=smtp.gmail.com",
                        "MAIL_SMTP_PORT=587",
                        "MAIL_SMTP_USERNAME=sender@gmail.com",
                        "MAIL_SMTP_PASSWORD=app-password",
                        "MAIL_SMTP_AUTH=true",
                        "MAIL_SMTP_STARTTLS_ENABLE=true",
                        "MAIL_SMTP_STARTTLS_REQUIRED=true"
                )
                .run(context -> {
                    assertEquals("gmail", context.getEnvironment().getProperty("app.mail.provider"));
                    assertEquals("smtp.gmail.com", context.getEnvironment().getProperty("spring.mail.host"));
                    assertEquals("587", context.getEnvironment().getProperty("spring.mail.port"));
                    assertEquals("sender@gmail.com", context.getEnvironment().getProperty("spring.mail.username"));
                    assertEquals("true", context.getEnvironment()
                            .getProperty("spring.mail.properties.mail.smtp.auth"));
                    assertEquals("true", context.getEnvironment()
                            .getProperty("spring.mail.properties.mail.smtp.starttls.required"));
                });
    }
}
