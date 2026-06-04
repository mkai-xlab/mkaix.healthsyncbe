package com.g93.be.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MailUtil {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    /**
     * Sends a plain text email.
     *
     * @param to      recipient email address
     * @param subject email subject
     * @param body    plain text body
     */
    public void sendPlainTextMail(String to, String subject, String body) {
        log.info("Sending plain text email to {}", to);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Successfully sent plain text email to {}", to);
        } catch (Exception e) {
            log.error("Failed to send plain text email to {}", to, e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /**
     * Sends an HTML email using a Thymeleaf template.
     *
     * @param to           recipient email address
     * @param subject      email subject
     * @param templateName Thymeleaf template name (located under templates/)
     * @param variables    model attributes to be used in the template
     */
    public void sendTemplateMail(String to, String subject, String templateName, Map<String, Object> variables) {
        log.info("Sending HTML email template '{}' to {}", templateName, to);
        try {
            Context context = new Context();
            context.setVariables(variables);
            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent template email to {}", to);
        } catch (MessagingException e) {
            log.error("Messaging exception while sending template email to {}", to, e);
            throw new RuntimeException("Email template sending failed", e);
        } catch (Exception e) {
            log.error("Unexpected exception while sending template email to {}", to, e);
            throw new RuntimeException("Email template sending failed", e);
        }
    }
}
