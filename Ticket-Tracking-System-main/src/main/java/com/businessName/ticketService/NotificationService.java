package com.businessName.ticketService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

public class NotificationService {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private final EmailSender emailSender;

    public NotificationService() {
        this(new SmtpEmailSender());
    }

    public NotificationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void notifyCaseCreated(Connection connection, Long caseId) {
        try {
            JSONObject details = caseDetails(connection, caseId);
            enqueueAndAttempt(connection,
                    details.getLong("requesterUserId"),
                    caseId,
                    null,
                    "case.created",
                    "Caso creado " + details.getString("caseNumber"),
                    "Se registro el caso " + details.getString("caseNumber") + ": " + details.getString("title"));
        } catch (Exception ignored) {
        }
    }

    public void notifyTicketCreated(Connection connection, Long ticketId) {
        try {
            JSONObject details = ticketDetails(connection, ticketId);
            enqueueAndAttempt(connection,
                    details.getLong("requesterUserId"),
                    details.getLong("serviceCaseId"),
                    ticketId,
                    "ticket.created",
                    "Ticket creado " + details.getString("ticketNumber"),
                    "Se creo el ticket " + details.getString("ticketNumber") + " para el caso "
                            + details.getString("caseNumber") + ".");
        } catch (Exception ignored) {
        }
    }

    public void notifyTicketAssigned(Connection connection, Long ticketId) {
        try {
            JSONObject details = ticketDetails(connection, ticketId);
            if (details.isNull("assignedToUserId")) {
                return;
            }
            enqueueAndAttempt(connection,
                    details.getLong("assignedToUserId"),
                    details.getLong("serviceCaseId"),
                    ticketId,
                    "ticket.assigned",
                    "Ticket asignado " + details.getString("ticketNumber"),
                    "Se asigno el ticket " + details.getString("ticketNumber") + " a su bandeja.");
        } catch (Exception ignored) {
        }
    }

    public void notifyTicketResolved(Connection connection, Long ticketId) {
        try {
            JSONObject details = ticketDetails(connection, ticketId);
            enqueueAndAttempt(connection,
                    details.getLong("requesterUserId"),
                    details.getLong("serviceCaseId"),
                    ticketId,
                    "ticket.resolved",
                    "Ticket resuelto " + details.getString("ticketNumber"),
                    "El ticket " + details.getString("ticketNumber") + " fue marcado como resuelto.");
        } catch (Exception ignored) {
        }
    }

    public JSONObject retryDueEmailNotifications(Connection connection) {
        JSONArray attempted = new JSONArray();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT notification_id FROM p2_sandbox.notifications " +
                        "WHERE channel = 'EMAIL' AND status = 'PENDING' " +
                        "AND (next_attempt_at IS NULL OR next_attempt_at <= NOW()) " +
                        "AND attempt_count < max_attempts AND deleted_at IS NULL " +
                        "ORDER BY created_at ASC LIMIT 50")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long notificationId = rs.getLong("notification_id");
                    attempted.put(attemptNotification(connection, notificationId));
                }
            }
        } catch (Exception ignored) {
        }
        return new JSONObject().put("attempted", attempted).put("count", attempted.length());
    }

    private void enqueueAndAttempt(Connection connection, Long recipientUserId, Long caseId, Long ticketId,
                                   String eventType, String subject, String body) throws Exception {
        Recipient recipient = recipient(connection, recipientUserId);
        long notificationId;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO p2_sandbox.notifications " +
                        "(recipient_user_id, recipient_email, service_case_id, ticket_id, type, channel, subject, body, " +
                        "status, max_attempts, next_attempt_at) " +
                        "VALUES (?, ?, ?, ?, ?, 'EMAIL', ?, ?, 'PENDING', ?, NOW()) RETURNING notification_id")) {
            ps.setLong(1, recipientUserId);
            setNullableString(ps, 2, recipient.email);
            setNullableLong(ps, 3, caseId);
            setNullableLong(ps, 4, ticketId);
            ps.setString(5, eventType);
            ps.setString(6, subject);
            ps.setString(7, body);
            ps.setInt(8, intEnv("EMAIL_MAX_ATTEMPTS", DEFAULT_MAX_ATTEMPTS));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                notificationId = rs.getLong("notification_id");
            }
        }
        try {
            attemptNotification(connection, notificationId);
        } catch (Exception e) {
            markAttemptError(connection, notificationId, e);
        }
    }

    private JSONObject attemptNotification(Connection connection, Long notificationId) throws Exception {
        JSONObject notification;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT notification_id, recipient_email, subject, body, attempt_count, max_attempts " +
                        "FROM p2_sandbox.notifications WHERE notification_id = ? FOR UPDATE")) {
            ps.setLong(1, notificationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new JSONObject().put("notificationId", notificationId).put("status", "NOT_FOUND");
                }
                notification = new JSONObject()
                        .put("notificationId", rs.getLong("notification_id"))
                        .put("recipientEmail", nullToJson(rs.getString("recipient_email")))
                        .put("subject", rs.getString("subject"))
                        .put("body", rs.getString("body"))
                        .put("attemptCount", rs.getInt("attempt_count"))
                        .put("maxAttempts", rs.getInt("max_attempts"));
            }
        }

        int attemptNumber = notification.getInt("attemptCount") + 1;
        String recipientEmail = notification.isNull("recipientEmail") ? null : notification.getString("recipientEmail");
        EmailResult result = emailSender.send(
                recipientEmail,
                notification.getString("subject"),
                notification.getString("body"));
        insertAttempt(connection, notificationId, attemptNumber, result);
        if (result.success) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.notifications SET status = 'SENT', attempt_count = ?, sent_at = NOW(), " +
                            "last_attempt_at = NOW(), last_error = NULL, provider_message_id = ?, next_attempt_at = NULL " +
                            "WHERE notification_id = ?")) {
                ps.setInt(1, attemptNumber);
                ps.setString(2, result.providerMessageId);
                ps.setLong(3, notificationId);
                ps.executeUpdate();
            }
            return new JSONObject().put("notificationId", notificationId).put("status", "SENT");
        }

        boolean exhausted = attemptNumber >= notification.getInt("maxAttempts");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.notifications SET status = ?, attempt_count = ?, last_attempt_at = NOW(), " +
                        "last_error = ?, next_attempt_at = CASE WHEN ? THEN NULL ELSE NOW() + (? || ' minutes')::interval END " +
                        "WHERE notification_id = ?")) {
            ps.setString(1, exhausted ? "FAILED" : "PENDING");
            ps.setInt(2, attemptNumber);
            ps.setString(3, truncate(result.errorMessage, 500));
            ps.setBoolean(4, exhausted);
            ps.setInt(5, intEnv("EMAIL_RETRY_DELAY_MINUTES", 15));
            ps.setLong(6, notificationId);
            ps.executeUpdate();
        }
        return new JSONObject()
                .put("notificationId", notificationId)
                .put("status", exhausted ? "FAILED" : "PENDING")
                .put("error", nullToJson(result.errorMessage));
    }

    private void insertAttempt(Connection connection, Long notificationId, int attemptNumber, EmailResult result) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO p2_sandbox.notification_attempts " +
                        "(notification_id, attempt_number, status, provider_message_id, error_message) " +
                        "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, notificationId);
            ps.setInt(2, attemptNumber);
            ps.setString(3, result.success ? "SENT" : "FAILED");
            setNullableString(ps, 4, result.providerMessageId);
            setNullableString(ps, 5, truncate(result.errorMessage, 500));
            ps.executeUpdate();
        }
    }

    private void markAttemptError(Connection connection, Long notificationId, Exception exception) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.notifications SET status = 'PENDING', last_error = ?, " +
                        "next_attempt_at = NOW() + (? || ' minutes')::interval WHERE notification_id = ?")) {
            ps.setString(1, truncate(exception.getMessage(), 500));
            ps.setInt(2, intEnv("EMAIL_RETRY_DELAY_MINUTES", 15));
            ps.setLong(3, notificationId);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private JSONObject caseDetails(Connection connection, Long caseId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sc.case_id, sc.case_number, sc.title, sc.requester_user_id " +
                        "FROM p2_sandbox.service_case sc WHERE sc.case_id = ?")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new JSONObject()
                        .put("caseId", rs.getLong("case_id"))
                        .put("caseNumber", rs.getString("case_number"))
                        .put("title", rs.getString("title"))
                        .put("requesterUserId", rs.getLong("requester_user_id"));
            }
        }
    }

    private JSONObject ticketDetails(Connection connection, Long ticketId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT t.ticket_id, t.ticket_number, t.service_case_id, t.assigned_to_user_id, " +
                        "sc.case_number, sc.requester_user_id " +
                        "FROM p2_sandbox.ticket t " +
                        "JOIN p2_sandbox.service_case sc ON sc.case_id = t.service_case_id " +
                        "WHERE t.ticket_id = ?")) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long assignedToUserId = rs.getLong("assigned_to_user_id");
                boolean assignedWasNull = rs.wasNull();
                return new JSONObject()
                        .put("ticketId", rs.getLong("ticket_id"))
                        .put("ticketNumber", rs.getString("ticket_number"))
                        .put("serviceCaseId", rs.getLong("service_case_id"))
                        .put("caseNumber", rs.getString("case_number"))
                        .put("requesterUserId", rs.getLong("requester_user_id"))
                        .put("assignedToUserId", assignedWasNull ? JSONObject.NULL : assignedToUserId);
            }
        }
    }

    private Recipient recipient(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(NULLIF(notification_email, ''), NULLIF(email, '')) AS email " +
                        "FROM p2_sandbox.app_users WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new Recipient(null);
                }
                return new Recipient(rs.getString("email"));
            }
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws Exception {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws Exception {
        if (value == null || value.trim().isEmpty()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private Object nullToJson(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static class Recipient {
        private final String email;

        private Recipient(String email) {
            this.email = email;
        }
    }
}
