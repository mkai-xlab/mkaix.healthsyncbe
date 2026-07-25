package com.g93.be.common.util;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.thymeleaf.TemplateEngine;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailUtilTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;

    private MailUtil mailUtil;

    @BeforeEach
    void setUp() {
        mailUtil = new MailUtil(mailSender, templateEngine, "sender@healthsync.test");
    }

    @Test
    void sendsPlainTextMailWithConfiguredFromAddress() {
        mailUtil.sendPlainTextMail("recipient@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("sender@healthsync.test", message.getFrom());
        assertEquals("recipient@example.com", message.getTo()[0]);
        assertEquals("Subject", message.getSubject());
        assertEquals("Body", message.getText());
    }

    @Test
    void sendsTemplateMailWithConfiguredFromAddress() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(templateEngine.process(org.mockito.ArgumentMatchers.eq("welcome"), any(org.thymeleaf.context.Context.class)))
                .thenReturn("<p>Welcome</p>");
        when(mailSender.createMimeMessage()).thenReturn(message);

        mailUtil.sendTemplateMail(
                "recipient@example.com", "Welcome", "welcome", Map.of("name", "Doctor"));

        verify(mailSender).send(message);
        assertEquals("sender@healthsync.test", message.getFrom()[0].toString());
        assertEquals("recipient@example.com", message.getAllRecipients()[0].toString());
        assertEquals("Welcome", message.getSubject());
    }

    @Test
    void mailMethodsUseDedicatedAsyncExecutor() throws Exception {
        Async plainTextAsync = MailUtil.class
                .getMethod("sendPlainTextMail", String.class, String.class, String.class)
                .getAnnotation(Async.class);
        Async templateAsync = MailUtil.class
                .getMethod("sendTemplateMail", String.class, String.class, String.class, Map.class)
                .getAnnotation(Async.class);

        assertNotNull(plainTextAsync);
        assertNotNull(templateAsync);
        assertEquals("mailTaskExecutor", plainTextAsync.value());
        assertEquals("mailTaskExecutor", templateAsync.value());
    }
}
