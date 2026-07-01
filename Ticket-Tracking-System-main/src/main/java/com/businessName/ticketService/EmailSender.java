package com.businessName.ticketService;

public interface EmailSender {
    EmailResult send(String recipientEmail, String subject, String body);
}
