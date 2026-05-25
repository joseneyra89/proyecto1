package com.businessName.ticketService;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;

public class SmtpEmailSender implements EmailSender {
    private static final String MODE_LOG = "log";
    private static final String MODE_SMTP = "smtp";
    private static final String MODE_DISABLED = "disabled";

    @Override
    public EmailResult send(String recipientEmail, String subject, String body) {
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            return EmailResult.failure("Recipient email is required");
        }
        String mode = env("EMAIL_DELIVERY_MODE", MODE_LOG).toLowerCase();
        if (MODE_DISABLED.equals(mode)) {
            return EmailResult.failure("Email delivery is disabled");
        }
        if (!MODE_SMTP.equals(mode)) {
            System.out.println("[email-log] to=" + recipientEmail + " subject=" + subject);
            return EmailResult.success("log:" + UUID.randomUUID().toString());
        }

        String host = env("SMTP_HOST", "");
        if (host.trim().isEmpty()) {
            return EmailResult.failure("SMTP_HOST is required when EMAIL_DELIVERY_MODE=smtp");
        }
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", env("SMTP_PORT", "25"));
            properties.put("mail.smtp.auth", env("SMTP_AUTH", "false"));
            properties.put("mail.smtp.starttls.enable", env("SMTP_STARTTLS", "false"));
            properties.put("mail.smtp.connectiontimeout", env("SMTP_TIMEOUT_MS", "10000"));
            properties.put("mail.smtp.timeout", env("SMTP_TIMEOUT_MS", "10000"));

            final String username = env("SMTP_USERNAME", "");
            final String password = env("SMTP_PASSWORD", "");
            Session session;
            if (!username.trim().isEmpty()) {
                session = Session.getInstance(properties, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
            } else {
                session = Session.getInstance(properties);
            }

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(env("EMAIL_FROM", "mesa-ayuda@localhost")));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail, false));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
            String messageId = message.getMessageID() == null
                    ? "smtp:" + UUID.randomUUID().toString()
                    : message.getMessageID();
            return EmailResult.success(messageId);
        } catch (Exception e) {
            return EmailResult.failure(e.getMessage());
        }
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
