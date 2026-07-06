import com.businessName.security.AuthenticatedUser;
import com.businessName.ticketDao.ConnectionObject;
import com.businessName.ticketService.HelpdeskFlowService;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class HelpdeskFlowServiceTests {

    @Test
    public void createServiceCaseCreatesOpenUnassignedTicket() throws Exception {
        AuthenticatedUser user = firstActiveUser();
        Integer siteId = firstActiveSiteId();
        String title = "Ticket directo " + Instant.now().toEpochMilli();
        JSONObject payload = new JSONObject()
                .put("type", "REQUEST")
                .put("title", title)
                .put("description", "El usuario registra directamente un ticket desde el portal.")
                .put("priority", "MEDIUM")
                .put("siteId", siteId);

        Long caseId = null;
        Long ticketId = null;
        try {
            JSONObject created = new HelpdeskFlowService().createServiceCase(user, payload.toString(), null);
            caseId = created.getLong("caseId");
            JSONObject ticket = created.getJSONObject("ticket");
            ticketId = ticket.getLong("ticketId");

            Assert.assertEquals(created.getString("type"), "REQUEST");
            Assert.assertTrue(created.getBoolean("hasTicket"));
            Assert.assertEquals(ticket.getString("status"), "OPEN");
            Assert.assertEquals(ticket.getString("summary"), title);
            Assert.assertEquals(ticket.getString("caseType"), "REQUEST");
            Assert.assertEquals(ticket.getLong("requesterUserId"), user.userId.longValue());
            Assert.assertTrue(ticket.isNull("assignedToUserId"));
        } finally {
            cleanupCreatedTicket(caseId, ticketId);
        }
    }

    private AuthenticatedUser firstActiveUser() throws Exception {
        try (Connection connection = ConnectionObject.createConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT u.user_id, u.username, u.first_name, u.last_name, u.email, u.notification_email, " +
                             "u.job_title, u.phone, u.is_active, r.code AS role_code " +
                             "FROM p2_sandbox.app_users u " +
                             "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                             "WHERE r.code = 'USER' AND u.is_active = TRUE AND u.deleted_at IS NULL " +
                             "ORDER BY u.user_id LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertTrue(rs.next(), "Expected at least one active USER seed");
                AuthenticatedUser user = new AuthenticatedUser();
                user.userId = rs.getLong("user_id");
                user.roleCode = rs.getString("role_code");
                user.username = rs.getString("username");
                user.firstName = rs.getString("first_name");
                user.lastName = rs.getString("last_name");
                user.email = rs.getString("email");
                user.notificationEmail = rs.getString("notification_email");
                user.jobTitle = rs.getString("job_title");
                user.phone = rs.getString("phone");
                user.isActive = rs.getBoolean("is_active");
                return user;
            }
        }
    }

    private Integer firstActiveSiteId() throws Exception {
        try (Connection connection = ConnectionObject.createConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT site_id FROM p2_sandbox.sites " +
                             "WHERE is_active = TRUE AND deleted_at IS NULL ORDER BY site_id LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                Assert.assertTrue(rs.next(), "Expected at least one active site seed");
                return rs.getInt("site_id");
            }
        }
    }

    private void cleanupCreatedTicket(Long caseId, Long ticketId) throws Exception {
        if (caseId == null && ticketId == null) {
            return;
        }
        try (Connection connection = ConnectionObject.createConnection()) {
            if (ticketId != null || caseId != null) {
                deleteNotificationAttempts(connection, caseId, ticketId);
                deleteNotifications(connection, caseId, ticketId);
            }
            if (ticketId != null) {
                deleteByLong(connection, "DELETE FROM p2_sandbox.ticket_updates WHERE ticket_id = ?", ticketId);
                deleteAudit(connection, "ticket", ticketId);
                deleteByLong(connection, "DELETE FROM p2_sandbox.ticket WHERE ticket_id = ?", ticketId);
            }
            if (caseId != null) {
                deleteAudit(connection, "service_case", caseId);
                deleteByLong(connection, "DELETE FROM p2_sandbox.service_case WHERE case_id = ?", caseId);
            }
        }
    }

    private void deleteNotificationAttempts(Connection connection, Long caseId, Long ticketId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM p2_sandbox.notification_attempts WHERE notification_id IN (" +
                        "SELECT notification_id FROM p2_sandbox.notifications " +
                        "WHERE (? IS NOT NULL AND service_case_id = ?) OR (? IS NOT NULL AND ticket_id = ?))")) {
            bindNullableLong(ps, 1, caseId);
            bindNullableLong(ps, 2, caseId);
            bindNullableLong(ps, 3, ticketId);
            bindNullableLong(ps, 4, ticketId);
            ps.executeUpdate();
        }
    }

    private void deleteNotifications(Connection connection, Long caseId, Long ticketId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM p2_sandbox.notifications " +
                        "WHERE (? IS NOT NULL AND service_case_id = ?) OR (? IS NOT NULL AND ticket_id = ?)")) {
            bindNullableLong(ps, 1, caseId);
            bindNullableLong(ps, 2, caseId);
            bindNullableLong(ps, 3, ticketId);
            bindNullableLong(ps, 4, ticketId);
            ps.executeUpdate();
        }
    }

    private void deleteAudit(Connection connection, String entityType, Long entityId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM p2_sandbox.audit_logs WHERE entity_type = ? AND entity_id = ?")) {
            ps.setString(1, entityType);
            ps.setString(2, String.valueOf(entityId));
            ps.executeUpdate();
        }
    }

    private void deleteByLong(Connection connection, String sql, Long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private void bindNullableLong(PreparedStatement ps, int index, Long value) throws Exception {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }
}
