package com.businessName.ticketService;

import com.businessName.security.AuthException;
import com.businessName.security.AuthenticatedUser;
import com.businessName.ticketDao.ConnectionObject;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class HelpdeskFlowService {

    public JSONArray listSites() {
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT site_id, code, name, address, city, province, country " +
                             "FROM p2_sandbox.sites WHERE is_active = TRUE AND deleted_at IS NULL ORDER BY name")) {
            JSONArray sites = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sites.put(new JSONObject()
                            .put("siteId", rs.getInt("site_id"))
                            .put("code", rs.getString("code"))
                            .put("name", rs.getString("name"))
                            .put("address", nullToJson(rs.getString("address")))
                            .put("city", nullToJson(rs.getString("city")))
                            .put("province", nullToJson(rs.getString("province")))
                            .put("country", rs.getString("country")));
                }
            }
            return sites;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list sites");
        }
    }

    public JSONArray listLocations(Integer siteId) {
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT location_id, site_id, parent_location_id, type, code, name, floor, description " +
                             "FROM p2_sandbox.locations " +
                             "WHERE site_id = ? AND is_active = TRUE AND deleted_at IS NULL ORDER BY type, name")) {
            ps.setInt(1, siteId);
            JSONArray locations = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    locations.put(new JSONObject()
                            .put("locationId", rs.getLong("location_id"))
                            .put("siteId", rs.getInt("site_id"))
                            .put("parentLocationId", longOrNull(rs, "parent_location_id"))
                            .put("type", rs.getString("type"))
                            .put("code", rs.getString("code"))
                            .put("name", rs.getString("name"))
                            .put("floor", nullToJson(rs.getString("floor")))
                            .put("description", nullToJson(rs.getString("description"))));
                }
            }
            return locations;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list locations");
        }
    }

    public JSONArray listTechnicians() {
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT u.user_id, u.username, u.first_name, u.last_name, u.job_title " +
                             "FROM p2_sandbox.app_users u " +
                             "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                             "WHERE r.code = 'TECH' AND u.is_active = TRUE AND u.deleted_at IS NULL " +
                             "ORDER BY u.first_name, u.last_name")) {
            JSONArray users = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.put(new JSONObject()
                            .put("userId", rs.getLong("user_id"))
                            .put("username", rs.getString("username"))
                            .put("name", rs.getString("first_name") + " " + rs.getString("last_name"))
                            .put("jobTitle", nullToJson(rs.getString("job_title"))));
                }
            }
            return users;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list technicians");
        }
    }

    public JSONObject createServiceCase(AuthenticatedUser user, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String type = requireEnum(request.optString("type", ""), "type", "REQUEST", "INCIDENT");
        String title = requireText(request.optString("title", ""), "title", 8, 160);
        String description = requireText(request.optString("description", ""), "description", 20, 4000);
        String priority = optionalEnum(request.optString("priority", "MEDIUM"), "priority", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        Integer siteId = request.has("siteId") && !request.isNull("siteId") ? request.getInt("siteId") : null;
        Long locationId = request.has("locationId") && !request.isNull("locationId") ? request.getLong("locationId") : null;

        if (siteId == null) {
            throw new AuthException(400, "siteId is required");
        }

        try (Connection connection = requireConnection()) {
            validateSiteAndLocation(connection, siteId, locationId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO p2_sandbox.service_case " +
                            "(type, title, description, requester_user_id, affected_user_id, site_id, location_id, " +
                            "status, priority, created_by_user_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?) " +
                            "RETURNING case_id")) {
                ps.setString(1, type);
                ps.setString(2, title);
                ps.setString(3, description);
                ps.setLong(4, user.userId);
                ps.setLong(5, user.userId);
                ps.setInt(6, siteId);
                if (locationId == null) {
                    ps.setNull(7, Types.BIGINT);
                } else {
                    ps.setLong(7, locationId);
                }
                ps.setString(8, priority);
                ps.setLong(9, user.userId);
                long caseId;
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    caseId = rs.getLong("case_id");
                }
                JSONObject created = getServiceCase(connection, user, caseId);
                audit(connection, user.userId, "service_case", String.valueOf(caseId), "SERVICE_CASE_CREATED",
                        null, created, ctx);
                return created;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create service case");
        }
    }

    public JSONArray listServiceCases(AuthenticatedUser user, String type, String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                        "sc.created_at, sc.updated_at, s.name AS site_name, l.name AS location_name, " +
                        "u.username AS requester_username, EXISTS (" +
                        "SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL" +
                        ") AS has_ticket " +
                        "FROM p2_sandbox.service_case sc " +
                        "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                        "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                        "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                        "WHERE sc.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if ("USER".equals(user.roleCode)) {
            sql.append(" AND sc.requester_user_id = ?");
            params.add(user.userId);
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND sc.type = ?");
            params.add(requireEnum(type, "type", "REQUEST", "INCIDENT"));
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND sc.status = ?");
            params.add(requireEnum(status, "status", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"));
        }
        sql.append(" ORDER BY sc.created_at DESC");
        return queryServiceCases(sql.toString(), params);
    }

    public JSONArray listQueues(String type) {
        StringBuilder sql = new StringBuilder(
                "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                        "sc.created_at, sc.updated_at, s.name AS site_name, l.name AS location_name, " +
                        "u.username AS requester_username, FALSE AS has_ticket " +
                        "FROM p2_sandbox.service_case sc " +
                        "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                        "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                        "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                        "WHERE sc.deleted_at IS NULL AND sc.status = 'OPEN' " +
                        "AND NOT EXISTS (SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL)");
        List<Object> params = new ArrayList<>();
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND sc.type = ?");
            params.add(requireEnum(type, "type", "REQUEST", "INCIDENT"));
        }
        sql.append(" ORDER BY sc.priority DESC, sc.created_at ASC");
        return queryServiceCases(sql.toString(), params);
    }

    public JSONObject getServiceCase(AuthenticatedUser user, Long caseId) {
        try (Connection connection = requireConnection()) {
            return getServiceCase(connection, user, caseId);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to get service case");
        }
    }

    public JSONObject createTicket(AuthenticatedUser user, Long caseId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        Long assignedToUserId = request.has("assignedToUserId") && !request.isNull("assignedToUserId")
                ? request.getLong("assignedToUserId") : user.userId;
        String summary = requireText(request.optString("summary", ""), "summary", 8, 180);
        String description = request.optString("description", "");
        String priority = optionalEnum(request.optString("priority", "MEDIUM"), "priority", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        String categoryCode = trimToNull(request.optString("categoryCode", "GENERAL"));

        try (Connection connection = requireConnection()) {
            ensureTechnician(connection, assignedToUserId);
            ensureCaseCanReceiveTicket(connection, caseId);
            Integer slaPolicyId = findSlaPolicy(connection, caseId, priority);
            long ticketId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO p2_sandbox.ticket " +
                            "(service_case_id, assigned_to_user_id, created_by_user_id, sla_policy_id, category_code, " +
                            "summary, description, status, priority) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?) RETURNING ticket_id")) {
                ps.setLong(1, caseId);
                ps.setLong(2, assignedToUserId);
                ps.setLong(3, user.userId);
                if (slaPolicyId == null) {
                    ps.setNull(4, Types.INTEGER);
                } else {
                    ps.setInt(4, slaPolicyId);
                }
                ps.setString(5, categoryCode);
                ps.setString(6, summary);
                ps.setString(7, trimToNull(description));
                ps.setString(8, priority);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ticketId = rs.getLong("ticket_id");
                }
            }
            updateCaseStatus(connection, caseId, "IN_PROGRESS", false);
            addTicketUpdate(connection, ticketId, user.userId, "INTERNAL", "ASSIGNMENT",
                    "Ticket created and assigned.", null, "IN_PROGRESS");
            JSONObject ticket = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_CREATED", null, ticket, ctx);
            return ticket;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create ticket");
        }
    }

    public JSONArray listTickets(AuthenticatedUser user, String status, String type, String query) {
        StringBuilder sql = new StringBuilder(ticketBaseSql() + " WHERE t.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if ("USER".equals(user.roleCode)) {
            sql.append(" AND sc.requester_user_id = ?");
            params.add(user.userId);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND t.status = ?");
            params.add(requireEnum(status, "status", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"));
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND sc.type = ?");
            params.add(requireEnum(type, "type", "REQUEST", "INCIDENT"));
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (LOWER(t.ticket_number) LIKE LOWER(?) OR LOWER(sc.case_number) LIKE LOWER(?) OR LOWER(sc.title) LIKE LOWER(?))");
            String like = "%" + query.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY t.updated_at DESC, t.created_at DESC");
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            bind(ps, params);
            JSONArray tickets = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tickets.put(ticketSummaryJson(rs));
                }
            }
            return tickets;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list tickets");
        }
    }

    public JSONObject getTicket(AuthenticatedUser user, Long ticketId) {
        try (Connection connection = requireConnection()) {
            return getTicket(connection, user, ticketId);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to get ticket");
        }
    }

    public JSONObject updateTicket(AuthenticatedUser user, Long ticketId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String updateBody = trimToNull(request.optString("body", ""));
        String visibility = optionalEnum(request.optString("visibility", "INTERNAL"), "visibility", "PUBLIC", "INTERNAL");
        String status = request.has("status") && !request.isNull("status")
                ? requireEnum(request.optString("status"), "status", "OPEN", "IN_PROGRESS")
                : null;
        String priority = request.has("priority") && !request.isNull("priority")
                ? requireEnum(request.optString("priority"), "priority", "LOW", "MEDIUM", "HIGH", "CRITICAL")
                : null;
        String summary = trimToNull(request.optString("summary", ""));
        String description = trimToNull(request.optString("description", ""));
        if (updateBody == null && status == null && priority == null && summary == null && description == null) {
            throw new AuthException(400, "At least one ticket field or update body is required");
        }

        try (Connection connection = requireConnection()) {
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before);
            String previousStatus = before.optString("status", null);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.ticket SET " +
                            "summary = COALESCE(?, summary), " +
                            "description = COALESCE(?, description), " +
                            "priority = COALESCE(?, priority), " +
                            "status = COALESCE(?, status), updated_at = NOW() " +
                            "WHERE ticket_id = ? AND deleted_at IS NULL")) {
                setNullableString(ps, 1, summary);
                setNullableString(ps, 2, description);
                setNullableString(ps, 3, priority);
                setNullableString(ps, 4, status);
                ps.setLong(5, ticketId);
                ps.executeUpdate();
            }
            if (status != null) {
                updateCaseStatus(connection, before.getLong("serviceCaseId"), status, false);
            }
            if (updateBody != null || status != null) {
                addTicketUpdate(connection, ticketId, user.userId, visibility, status == null ? "COMMENT" : "STATUS_CHANGE",
                        updateBody == null ? "Status changed to " + status : updateBody,
                        previousStatus, status);
            }
            JSONObject after = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_UPDATED", before, after, ctx);
            return after;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update ticket");
        }
    }

    public JSONObject assignTicket(AuthenticatedUser user, Long ticketId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        if (!request.has("assignedToUserId") || request.isNull("assignedToUserId")) {
            throw new AuthException(400, "assignedToUserId is required");
        }
        Long assignedToUserId = request.getLong("assignedToUserId");
        try (Connection connection = requireConnection()) {
            ensureTechnician(connection, assignedToUserId);
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.ticket SET assigned_to_user_id = ?, status = 'IN_PROGRESS', updated_at = NOW() " +
                            "WHERE ticket_id = ? AND deleted_at IS NULL")) {
                ps.setLong(1, assignedToUserId);
                ps.setLong(2, ticketId);
                ps.executeUpdate();
            }
            updateCaseStatus(connection, before.getLong("serviceCaseId"), "IN_PROGRESS", false);
            addTicketUpdate(connection, ticketId, user.userId, "INTERNAL", "ASSIGNMENT",
                    "Ticket reassigned.", before.optString("status"), "IN_PROGRESS");
            JSONObject after = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_ASSIGNED", before, after, ctx);
            return after;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to assign ticket");
        }
    }

    public JSONObject resolveTicket(AuthenticatedUser user, Long ticketId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String resolution = requireText(request.optString("resolution", ""), "resolution", 12, 4000);
        String publicUpdate = trimToNull(request.optString("publicUpdate", resolution));
        try (Connection connection = requireConnection()) {
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.ticket SET status = 'RESOLVED', resolution = ?, resolved_at = NOW(), updated_at = NOW() " +
                            "WHERE ticket_id = ? AND deleted_at IS NULL")) {
                ps.setString(1, resolution);
                ps.setLong(2, ticketId);
                ps.executeUpdate();
            }
            updateCaseStatus(connection, before.getLong("serviceCaseId"), "RESOLVED", true);
            addTicketUpdate(connection, ticketId, user.userId, "PUBLIC", "STATUS_CHANGE",
                    publicUpdate, before.optString("status"), "RESOLVED");
            JSONObject after = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_RESOLVED", before, after, ctx);
            return after;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to resolve ticket");
        }
    }

    private JSONArray queryServiceCases(String sql, List<Object> params) {
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            JSONArray cases = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cases.put(serviceCaseSummaryJson(rs));
                }
            }
            return cases;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list service cases");
        }
    }

    private JSONObject getServiceCase(Connection connection, AuthenticatedUser user, Long caseId) throws Exception {
        String sql = "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                "sc.created_at, sc.updated_at, s.name AS site_name, l.name AS location_name, " +
                "u.username AS requester_username, EXISTS (" +
                "SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL" +
                ") AS has_ticket " +
                "FROM p2_sandbox.service_case sc " +
                "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                "WHERE sc.case_id = ? AND sc.deleted_at IS NULL";
        if ("USER".equals(user.roleCode)) {
            sql += " AND sc.requester_user_id = ?";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, caseId);
            if ("USER".equals(user.roleCode)) {
                ps.setLong(2, user.userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Service case not found");
                }
                JSONObject serviceCase = serviceCaseSummaryJson(rs);
                serviceCase.put("description", rs.getString("description"));
                serviceCase.put("ticket", findTicketForCase(connection, user, caseId));
                return serviceCase;
            }
        }
    }

    private Object findTicketForCase(Connection connection, AuthenticatedUser user, Long caseId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(ticketBaseSql() + " WHERE t.service_case_id = ? AND t.deleted_at IS NULL")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return JSONObject.NULL;
                }
                JSONObject ticket = ticketSummaryJson(rs);
                ticket.put("updates", listTicketUpdates(connection, user, ticket.getLong("ticketId")));
                return ticket;
            }
        }
    }

    private JSONObject getTicket(Connection connection, AuthenticatedUser user, Long ticketId) throws Exception {
        String sql = ticketBaseSql() + " WHERE t.ticket_id = ? AND t.deleted_at IS NULL";
        if ("USER".equals(user.roleCode)) {
            sql += " AND sc.requester_user_id = ?";
        }
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            if ("USER".equals(user.roleCode)) {
                ps.setLong(2, user.userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Ticket not found");
                }
                JSONObject ticket = ticketSummaryJson(rs);
                ticket.put("updates", listTicketUpdates(connection, user, ticketId));
                return ticket;
            }
        }
    }

    private JSONArray listTicketUpdates(Connection connection, AuthenticatedUser user, Long ticketId) throws Exception {
        String sql = "SELECT tu.ticket_update_id, tu.visibility, tu.update_type, tu.body, tu.previous_status, tu.new_status, " +
                "tu.created_at, au.username AS author_username " +
                "FROM p2_sandbox.ticket_updates tu " +
                "LEFT JOIN p2_sandbox.app_users au ON au.user_id = tu.author_user_id " +
                "WHERE tu.ticket_id = ? AND tu.deleted_at IS NULL";
        if ("USER".equals(user.roleCode)) {
            sql += " AND tu.visibility = 'PUBLIC'";
        }
        sql += " ORDER BY tu.created_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, ticketId);
            JSONArray updates = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    updates.put(new JSONObject()
                            .put("ticketUpdateId", rs.getLong("ticket_update_id"))
                            .put("visibility", rs.getString("visibility"))
                            .put("updateType", rs.getString("update_type"))
                            .put("body", rs.getString("body"))
                            .put("previousStatus", nullToJson(rs.getString("previous_status")))
                            .put("newStatus", nullToJson(rs.getString("new_status")))
                            .put("authorUsername", nullToJson(rs.getString("author_username")))
                            .put("createdAt", timestampString(rs, "created_at")));
                }
            }
            return updates;
        }
    }

    private String ticketBaseSql() {
        return "SELECT t.ticket_id, t.ticket_number, t.service_case_id, t.summary, t.description, t.resolution, " +
                "t.status, t.priority, t.category_code, t.created_at, t.updated_at, t.resolved_at, " +
                "sc.case_number, sc.type AS case_type, sc.title AS case_title, sc.requester_user_id, " +
                "req.username AS requester_username, assignee.user_id AS assigned_to_user_id, " +
                "assignee.username AS assigned_to_username " +
                "FROM p2_sandbox.ticket t " +
                "JOIN p2_sandbox.service_case sc ON sc.case_id = t.service_case_id " +
                "JOIN p2_sandbox.app_users req ON req.user_id = sc.requester_user_id " +
                "LEFT JOIN p2_sandbox.app_users assignee ON assignee.user_id = t.assigned_to_user_id";
    }

    private JSONObject serviceCaseSummaryJson(ResultSet rs) throws Exception {
        return new JSONObject()
                .put("caseId", rs.getLong("case_id"))
                .put("caseNumber", rs.getString("case_number"))
                .put("type", rs.getString("type"))
                .put("title", rs.getString("title"))
                .put("description", rs.getString("description"))
                .put("status", rs.getString("status"))
                .put("priority", rs.getString("priority"))
                .put("siteName", nullToJson(rs.getString("site_name")))
                .put("locationName", nullToJson(rs.getString("location_name")))
                .put("requesterUsername", rs.getString("requester_username"))
                .put("hasTicket", rs.getBoolean("has_ticket"))
                .put("createdAt", timestampString(rs, "created_at"))
                .put("updatedAt", timestampString(rs, "updated_at"));
    }

    private JSONObject ticketSummaryJson(ResultSet rs) throws Exception {
        return new JSONObject()
                .put("ticketId", rs.getLong("ticket_id"))
                .put("ticketNumber", rs.getString("ticket_number"))
                .put("serviceCaseId", rs.getLong("service_case_id"))
                .put("caseNumber", rs.getString("case_number"))
                .put("caseType", rs.getString("case_type"))
                .put("caseTitle", rs.getString("case_title"))
                .put("summary", rs.getString("summary"))
                .put("description", nullToJson(rs.getString("description")))
                .put("resolution", nullToJson(rs.getString("resolution")))
                .put("status", rs.getString("status"))
                .put("priority", rs.getString("priority"))
                .put("categoryCode", nullToJson(rs.getString("category_code")))
                .put("requesterUserId", rs.getLong("requester_user_id"))
                .put("requesterUsername", rs.getString("requester_username"))
                .put("assignedToUserId", longOrNull(rs, "assigned_to_user_id"))
                .put("assignedToUsername", nullToJson(rs.getString("assigned_to_username")))
                .put("createdAt", timestampString(rs, "created_at"))
                .put("updatedAt", timestampString(rs, "updated_at"))
                .put("resolvedAt", timestampString(rs, "resolved_at"));
    }

    private void validateSiteAndLocation(Connection connection, Integer siteId, Long locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM p2_sandbox.sites WHERE site_id = ? AND is_active = TRUE AND deleted_at IS NULL")) {
            ps.setInt(1, siteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(400, "Invalid siteId");
                }
            }
        }
        if (locationId != null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM p2_sandbox.locations WHERE location_id = ? AND site_id = ? " +
                            "AND is_active = TRUE AND deleted_at IS NULL")) {
                ps.setLong(1, locationId);
                ps.setInt(2, siteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(400, "Invalid locationId for site");
                    }
                }
            }
        }
    }

    private void ensureCaseCanReceiveTicket(Connection connection, Long caseId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM p2_sandbox.service_case WHERE case_id = ? AND deleted_at IS NULL")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Service case not found");
                }
                if (!"OPEN".equals(rs.getString("status"))) {
                    throw new AuthException(400, "Only open service cases can receive a ticket");
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM p2_sandbox.ticket WHERE service_case_id = ? AND deleted_at IS NULL")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new AuthException(400, "Service case already has a ticket");
                }
            }
        }
    }

    private void ensureTechnician(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM p2_sandbox.app_users u JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                        "WHERE u.user_id = ? AND r.code = 'TECH' AND u.is_active = TRUE AND u.deleted_at IS NULL")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(400, "Assigned user must be an active technician");
                }
            }
        }
    }

    private void ensureTicketCanChange(JSONObject ticket) {
        String status = ticket.optString("status", "");
        if ("RESOLVED".equals(status) || "CLOSED".equals(status) || "CANCELLED".equals(status)) {
            throw new AuthException(400, "Resolved, closed or cancelled tickets cannot be changed");
        }
    }

    private Integer findSlaPolicy(Connection connection, Long caseId, String priority) throws Exception {
        String caseType;
        try (PreparedStatement ps = connection.prepareStatement("SELECT type FROM p2_sandbox.service_case WHERE case_id = ?")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                caseType = rs.getString("type");
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sla_policy_id FROM p2_sandbox.sla_policies " +
                        "WHERE case_type = ? AND priority = ? AND is_active = TRUE AND deleted_at IS NULL " +
                        "ORDER BY is_default DESC, sla_policy_id ASC LIMIT 1")) {
            ps.setString(1, caseType);
            ps.setString(2, priority);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("sla_policy_id") : null;
            }
        }
    }

    private void updateCaseStatus(Connection connection, Long caseId, String status, boolean resolved) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.service_case SET status = ?, updated_at = NOW(), " +
                        "resolved_at = CASE WHEN ? THEN NOW() ELSE resolved_at END " +
                        "WHERE case_id = ?")) {
            ps.setString(1, status);
            ps.setBoolean(2, resolved);
            ps.setLong(3, caseId);
            ps.executeUpdate();
        }
    }

    private void addTicketUpdate(Connection connection, Long ticketId, Long authorUserId, String visibility,
                                 String updateType, String body, String previousStatus, String newStatus) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO p2_sandbox.ticket_updates " +
                        "(ticket_id, author_user_id, visibility, update_type, body, previous_status, new_status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, ticketId);
            ps.setLong(2, authorUserId);
            ps.setString(3, visibility);
            ps.setString(4, updateType);
            ps.setString(5, body);
            setNullableString(ps, 6, previousStatus);
            setNullableString(ps, 7, newStatus);
            ps.executeUpdate();
        }
    }

    private void audit(Connection connection, Long actorUserId, String entityType, String entityId, String action,
                       JSONObject beforeData, JSONObject afterData, Context ctx) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO p2_sandbox.audit_logs " +
                        "(actor_user_id, entity_type, entity_id, action, before_data, after_data, ip_address, user_agent) " +
                        "VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), NULLIF(?, '')::inet, ?)")) {
            if (actorUserId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, actorUserId);
            }
            ps.setString(2, entityType);
            ps.setString(3, entityId);
            ps.setString(4, action);
            ps.setString(5, beforeData == null ? null : beforeData.toString());
            ps.setString(6, afterData == null ? null : afterData.toString());
            ps.setString(7, "");
            ps.setString(8, userAgent(ctx));
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private Connection requireConnection() {
        Connection connection = ConnectionObject.createConnection();
        if (connection == null) {
            throw new AuthException(500, "Database connection unavailable");
        }
        return connection;
    }

    private JSONObject parseJson(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    private String requireText(String value, String field, int minLength, int maxLength) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() < minLength || trimmed.length() > maxLength) {
            throw new AuthException(400, field + " must be between " + minLength + " and " + maxLength + " characters");
        }
        return trimmed;
    }

    private String requireEnum(String value, String field, String... allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        for (String option : allowed) {
            if (option.equals(normalized)) {
                return normalized;
            }
        }
        throw new AuthException(400, "Invalid " + field);
    }

    private String optionalEnum(String value, String field, String... allowed) {
        return requireEnum(value, field, allowed);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void bind(PreparedStatement ps, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long) {
                ps.setLong(i + 1, (Long) param);
            } else if (param instanceof Integer) {
                ps.setInt(i + 1, (Integer) param);
            } else {
                ps.setString(i + 1, String.valueOf(param));
            }
        }
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws Exception {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private Object nullToJson(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private Object longOrNull(ResultSet rs, String column) throws Exception {
        long value = rs.getLong(column);
        return rs.wasNull() ? JSONObject.NULL : value;
    }

    private Object timestampString(ResultSet rs, String column) throws Exception {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? JSONObject.NULL : timestamp.toInstant().toString();
    }

    private String userAgent(Context ctx) {
        if (ctx == null) {
            return null;
        }
        String value = ctx.header("User-Agent");
        if (value == null) {
            return null;
        }
        return value.length() > 250 ? value.substring(0, 250) : value;
    }
}
