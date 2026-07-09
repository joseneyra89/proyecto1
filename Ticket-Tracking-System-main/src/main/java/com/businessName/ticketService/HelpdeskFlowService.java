package com.businessName.ticketService;

import com.businessName.common.PageRequest;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HelpdeskFlowService {
    private final NotificationService notificationService = new NotificationService();

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

    public JSONObject createSite(AuthenticatedUser user, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String code = requireText(request.optString("code", ""), "code", 2, 40).toUpperCase();
        String name = requireText(request.optString("name", ""), "name", 3, 160);
        String country = trimToNull(request.optString("country", "PE"));
        if (country == null) {
            country = "PE";
        }
        country = country.toUpperCase();
        if (country.length() != 2) {
            throw new AuthException(400, "country must have 2 characters");
        }

        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO p2_sandbox.sites (code, name, address, city, province, country, is_active) " +
                             "VALUES (?, ?, ?, COALESCE(?, 'Canete'), COALESCE(?, 'Canete'), ?, TRUE) " +
                             "RETURNING site_id, code, name, address, city, province, country, is_active")) {
            ps.setString(1, code);
            ps.setString(2, name);
            setNullableString(ps, 3, trimToNull(request.optString("address", "")));
            setNullableString(ps, 4, trimToNull(request.optString("city", "")));
            setNullableString(ps, 5, trimToNull(request.optString("province", "")));
            ps.setString(6, country);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                JSONObject created = siteJson(rs);
                audit(connection, user.userId, "sites", String.valueOf(created.getInt("siteId")),
                        "SITE_CREATED", null, created, ctx);
                return created;
            }
        } catch (AuthException e) {
            throw e;
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AuthException(409, "Site code already exists");
            }
            throw new AuthException(500, "Unable to create site");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create site");
        }
    }

    public JSONObject updateSite(AuthenticatedUser user, Integer siteId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String code = trimToNull(request.optString("code", ""));
        if (code != null) {
            code = requireText(code, "code", 2, 40).toUpperCase();
        }
        String name = trimToNull(request.optString("name", ""));
        if (name != null) {
            name = requireText(name, "name", 3, 160);
        }
        String country = trimToNull(request.optString("country", ""));
        if (country != null) {
            country = country.toUpperCase();
            if (country.length() != 2) {
                throw new AuthException(400, "country must have 2 characters");
            }
        }
        try (Connection connection = requireConnection()) {
            JSONObject before = getSiteById(connection, siteId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.sites SET code = COALESCE(?, code), name = COALESCE(?, name), " +
                            "address = COALESCE(?, address), city = COALESCE(?, city), province = COALESCE(?, province), " +
                            "country = COALESCE(?, country), updated_at = NOW() " +
                            "WHERE site_id = ? AND deleted_at IS NULL " +
                            "RETURNING site_id, code, name, address, city, province, country, is_active")) {
                setNullableString(ps, 1, code);
                setNullableString(ps, 2, name);
                setNullableString(ps, 3, trimToNull(request.optString("address", "")));
                setNullableString(ps, 4, trimToNull(request.optString("city", "")));
                setNullableString(ps, 5, trimToNull(request.optString("province", "")));
                setNullableString(ps, 6, country);
                ps.setInt(7, siteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "Site not found");
                    }
                    JSONObject updated = siteJson(rs);
                    audit(connection, user.userId, "sites", String.valueOf(siteId),
                            "SITE_UPDATED", before, updated, ctx);
                    return updated;
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AuthException(409, "Site code already exists");
            }
            throw new AuthException(500, "Unable to update site");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update site");
        }
    }

    public JSONObject updateSiteStatus(AuthenticatedUser user, Integer siteId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        if (!request.has("isActive")) {
            throw new AuthException(400, "isActive is required");
        }
        boolean isActive = request.getBoolean("isActive");
        try (Connection connection = requireConnection()) {
            JSONObject before = getSiteById(connection, siteId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.sites SET is_active = ?, deleted_at = CASE WHEN ? THEN NULL ELSE NOW() END, " +
                            "updated_at = NOW() WHERE site_id = ? " +
                            "RETURNING site_id, code, name, address, city, province, country, is_active")) {
                ps.setBoolean(1, isActive);
                ps.setBoolean(2, isActive);
                ps.setInt(3, siteId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "Site not found");
                    }
                    JSONObject updated = siteJson(rs);
                    audit(connection, user.userId, "sites", String.valueOf(siteId),
                            isActive ? "SITE_ACTIVATED" : "SITE_INACTIVATED", before, updated, ctx);
                    return updated;
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update site status");
        }
    }

    public JSONObject createLocation(AuthenticatedUser user, Integer siteId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String type = requireEnum(request.optString("type", ""), "type", "AULA", "LAB", "AREA");
        String code = requireText(request.optString("code", ""), "code", 2, 60).toUpperCase();
        String name = requireText(request.optString("name", ""), "name", 3, 160);
        Long parentLocationId = request.has("parentLocationId") && !request.isNull("parentLocationId")
                ? request.getLong("parentLocationId") : null;

        try (Connection connection = requireConnection()) {
            validateSiteAndLocation(connection, siteId, null);
            if (parentLocationId != null) {
                validateSiteAndLocation(connection, siteId, parentLocationId);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO p2_sandbox.locations " +
                            "(site_id, parent_location_id, type, code, name, floor, description, is_active) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE) " +
                            "RETURNING location_id, site_id, parent_location_id, type, code, name, floor, description, is_active")) {
                ps.setInt(1, siteId);
                if (parentLocationId == null) {
                    ps.setNull(2, Types.BIGINT);
                } else {
                    ps.setLong(2, parentLocationId);
                }
                ps.setString(3, type);
                ps.setString(4, code);
                ps.setString(5, name);
                setNullableString(ps, 6, trimToNull(request.optString("floor", "")));
                setNullableString(ps, 7, trimToNull(request.optString("description", "")));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    JSONObject created = locationJson(rs);
                    audit(connection, user.userId, "locations", String.valueOf(created.getLong("locationId")),
                            "LOCATION_CREATED", null, created, ctx);
                    return created;
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AuthException(409, "Location code already exists for this site");
            }
            throw new AuthException(500, "Unable to create location");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create location");
        }
    }

    public JSONObject updateLocation(AuthenticatedUser user, Long locationId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String type = trimToNull(request.optString("type", ""));
        if (type != null) {
            type = requireEnum(type, "type", "AULA", "LAB", "AREA");
        }
        String code = trimToNull(request.optString("code", ""));
        if (code != null) {
            code = requireText(code, "code", 2, 60).toUpperCase();
        }
        String name = trimToNull(request.optString("name", ""));
        if (name != null) {
            name = requireText(name, "name", 3, 160);
        }
        try (Connection connection = requireConnection()) {
            JSONObject before = getLocationById(connection, locationId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.locations SET type = COALESCE(?, type), code = COALESCE(?, code), " +
                            "name = COALESCE(?, name), floor = COALESCE(?, floor), description = COALESCE(?, description), " +
                            "updated_at = NOW() WHERE location_id = ? AND deleted_at IS NULL " +
                            "RETURNING location_id, site_id, parent_location_id, type, code, name, floor, description, is_active")) {
                setNullableString(ps, 1, type);
                setNullableString(ps, 2, code);
                setNullableString(ps, 3, name);
                setNullableString(ps, 4, trimToNull(request.optString("floor", "")));
                setNullableString(ps, 5, trimToNull(request.optString("description", "")));
                ps.setLong(6, locationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "Location not found");
                    }
                    JSONObject updated = locationJson(rs);
                    audit(connection, user.userId, "locations", String.valueOf(locationId),
                            "LOCATION_UPDATED", before, updated, ctx);
                    return updated;
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AuthException(409, "Location code already exists for this site");
            }
            throw new AuthException(500, "Unable to update location");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update location");
        }
    }

    public JSONObject updateLocationStatus(AuthenticatedUser user, Long locationId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        if (!request.has("isActive")) {
            throw new AuthException(400, "isActive is required");
        }
        boolean isActive = request.getBoolean("isActive");
        try (Connection connection = requireConnection()) {
            JSONObject before = getLocationById(connection, locationId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.locations SET is_active = ?, deleted_at = CASE WHEN ? THEN NULL ELSE NOW() END, " +
                            "updated_at = NOW() WHERE location_id = ? " +
                            "RETURNING location_id, site_id, parent_location_id, type, code, name, floor, description, is_active")) {
                ps.setBoolean(1, isActive);
                ps.setBoolean(2, isActive);
                ps.setLong(3, locationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "Location not found");
                    }
                    JSONObject updated = locationJson(rs);
                    audit(connection, user.userId, "locations", String.valueOf(locationId),
                            isActive ? "LOCATION_ACTIVATED" : "LOCATION_INACTIVATED", before, updated, ctx);
                    return updated;
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update location status");
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

    public JSONObject technicianDashboard(AuthenticatedUser user, String dateFrom, String dateTo) {
        if ("USER".equals(user.roleCode)) {
            throw new AuthException(403, "Access denied");
        }
        LocalDate to = parseDateOrDefault(dateTo, LocalDate.now());
        LocalDate from = parseDateOrDefault(dateFrom, to.minusDays(30));
        if (from.isAfter(to)) {
            throw new AuthException(400, "dateFrom must be before or equal to dateTo");
        }
        LocalDate toExclusive = to.plusDays(1);
        try (Connection connection = requireConnection()) {
            closeExpiredResolvedTickets(connection);
            JSONArray byType = new JSONArray()
                    .put(dashboardMetricsForType(connection, user, "REQUEST", from, toExclusive))
                    .put(dashboardMetricsForType(connection, user, "INCIDENT", from, toExclusive));
            JSONObject totals = totalDashboardMetrics(byType);
            return new JSONObject()
                    .put("scope", "ADMIN".equals(user.roleCode) ? "ALL" : "OWN_ASSIGNED")
                    .put("dateFrom", from.toString())
                    .put("dateTo", to.toString())
                    .put("generatedAt", Instant.now().toString())
                    .put("byType", byType)
                    .put("totals", totals);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to build technician dashboard");
        }
    }

    public JSONObject createServiceCase(AuthenticatedUser user, String body, Context ctx) {
        if (!"USER".equals(user.roleCode)) {
            throw new AuthException(403, "Only end users can create tickets");
        }
        JSONObject request = parseJson(body);
        String type = requireEnum(request.optString("type", ""), "type", "REQUEST", "INCIDENT");
        String title = requireText(request.optString("title", ""), "title", 8, 160);
        String description = requireText(request.optString("description", ""), "description", 20, 4000);
        String priority = optionalEnum(request.optString("priority", "MEDIUM"), "priority", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        Integer siteId = request.has("siteId") && !request.isNull("siteId") ? request.getInt("siteId") : null;
        Long locationId = request.has("locationId") && !request.isNull("locationId") ? request.getLong("locationId") : null;
        Long requesterUserId = user.userId;
        String categoryCode = trimToNull(request.optString("categoryCode", "GENERAL"));
        if (categoryCode == null) {
            categoryCode = "GENERAL";
        }
        String organization = trimToNull(request.optString("organization", ""));
        String businessService = trimToNull(request.optString("businessService", "Mesa de Ayuda"));
        if (businessService == null) {
            businessService = "Mesa de Ayuda";
        }

        if (siteId == null) {
            throw new AuthException(400, "siteId is required");
        }

        try (Connection connection = requireConnection()) {
            connection.setAutoCommit(false);
            try {
                validateSiteAndLocation(connection, siteId, locationId);
                ensureActiveUser(connection, requesterUserId);
                SlaPolicyDetails policy = findSlaPolicyDetails(connection, type, priority);
                Timestamp startAt = Timestamp.from(Instant.now());
                Timestamp dueAt = SlaEngine.calculateDueAt(type, startAt, policy.resolutionMinutes,
                        policy.calendarCode, policy.businessHoursOnly);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO p2_sandbox.service_case " +
                                "(type, title, description, requester_user_id, affected_user_id, site_id, location_id, " +
                                "organization, reported_by, business_service, status, priority, sla_policy_id, due_at, " +
                                "created_by_user_id, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, (SELECT name FROM p2_sandbox.sites WHERE site_id = ?)), " +
                                "?, ?, 'NEW', ?, ?, ?, ?, ?, ?) " +
                                "RETURNING case_id")) {
                    ps.setString(1, type);
                    ps.setString(2, title);
                    ps.setString(3, description);
                    ps.setLong(4, requesterUserId);
                    ps.setLong(5, requesterUserId);
                    ps.setInt(6, siteId);
                    if (locationId == null) {
                        ps.setNull(7, Types.BIGINT);
                    } else {
                        ps.setLong(7, locationId);
                    }
                    setNullableString(ps, 8, organization);
                    ps.setInt(9, siteId);
                    ps.setString(10, user.username);
                    ps.setString(11, businessService);
                    ps.setString(12, priority);
                    if (policy.slaPolicyId == null) {
                        ps.setNull(13, Types.INTEGER);
                    } else {
                        ps.setInt(13, policy.slaPolicyId);
                    }
                    ps.setTimestamp(14, dueAt);
                    ps.setLong(15, user.userId);
                    ps.setTimestamp(16, startAt);
                    ps.setTimestamp(17, startAt);
                    long caseId;
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        caseId = rs.getLong("case_id");
                    }
                    long ticketId;
                    try (PreparedStatement ticketPs = connection.prepareStatement(
                            "INSERT INTO p2_sandbox.ticket " +
                                "(service_case_id, assigned_to_user_id, created_by_user_id, sla_policy_id, category_code, " +
                                    "summary, description, resolver_team, status, priority, due_at) " +
                                    "VALUES (?, NULL, ?, ?, ?, ?, ?, 'Mesa de Ayuda', 'NEW', ?, ?) RETURNING ticket_id")) {
                        ticketPs.setLong(1, caseId);
                        ticketPs.setLong(2, user.userId);
                        if (policy.slaPolicyId == null) {
                            ticketPs.setNull(3, Types.INTEGER);
                        } else {
                            ticketPs.setInt(3, policy.slaPolicyId);
                        }
                        ticketPs.setString(4, categoryCode);
                        ticketPs.setString(5, title);
                        ticketPs.setString(6, description);
                        ticketPs.setString(7, priority);
                        ticketPs.setTimestamp(8, dueAt);
                        try (ResultSet ticketRs = ticketPs.executeQuery()) {
                            ticketRs.next();
                            ticketId = ticketRs.getLong("ticket_id");
                        }
                    }
                    addTicketUpdate(connection, ticketId, user.userId, "PUBLIC", "SYSTEM",
                            "Ticket registrado por el usuario.", null, "NEW");
                    JSONObject created = getServiceCase(connection, user, caseId);
                    JSONObject ticket = getTicket(connection, user, ticketId);
                    audit(connection, user.userId, "service_case", String.valueOf(caseId), "SERVICE_CASE_CREATED",
                            null, created, ctx);
                    audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_CREATED", null, ticket, ctx);
                    notificationService.notifyTicketCreated(connection, ticketId);
                    connection.commit();
                    created.put("ticket", ticket);
                    return created;
                }
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create service case");
        }
    }

    public JSONObject listServiceCases(AuthenticatedUser user, String type, String status, PageRequest pageRequest) {
        StringBuilder sql = new StringBuilder(
                "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                        "sc.created_at, sc.updated_at, sc.resolved_at, sc.due_at, s.site_id, s.name AS site_name, " +
                        "l.name AS location_name, sp.warning_percent, sp.resolution_minutes, sp.calendar_code, " +
                        "sp.business_hours_only, " +
                        "u.username AS requester_username, EXISTS (" +
                        "SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL" +
                        ") AS has_ticket " +
                        "FROM p2_sandbox.service_case sc " +
                        "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                        "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                        "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                        "LEFT JOIN p2_sandbox.sla_policies sp ON sp.sla_policy_id = sc.sla_policy_id " +
                        "WHERE sc.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if ("USER".equals(user.roleCode)) {
            sql.append(" AND sc.requester_user_id = ?");
            params.add(user.userId);
        } else if ("TECH".equals(user.roleCode)) {
            sql.append(" AND EXISTS (SELECT 1 FROM p2_sandbox.ticket assigned_ticket " +
                    "WHERE assigned_ticket.service_case_id = sc.case_id " +
                    "AND assigned_ticket.deleted_at IS NULL AND assigned_ticket.assigned_to_user_id = ?)");
            params.add(user.userId);
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND sc.type = ?");
            params.add(requireEnum(type, "type", "REQUEST", "INCIDENT"));
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND sc.status = ?");
            params.add(requireWorkflowStatus(status));
        }
        JSONObject result = queryPagedServiceCases(sql.toString(), " ORDER BY sc.created_at DESC", params, pageRequest);
        if ("USER".equals(user.roleCode)) {
            JSONArray cases = result.getJSONArray("data");
            for (int i = 0; i < cases.length(); i++) {
                cases.put(i, serviceCaseForRole(cases.getJSONObject(i), user));
            }
        }
        return result;
    }

    public JSONArray listQueues(String type) {
        StringBuilder sql = new StringBuilder(
                "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                        "sc.created_at, sc.updated_at, sc.resolved_at, sc.due_at, s.site_id, s.name AS site_name, " +
                        "l.name AS location_name, sp.warning_percent, sp.resolution_minutes, sp.calendar_code, " +
                        "sp.business_hours_only, " +
                        "u.username AS requester_username, FALSE AS has_ticket " +
                        "FROM p2_sandbox.service_case sc " +
                        "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                        "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                        "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                        "LEFT JOIN p2_sandbox.sla_policies sp ON sp.sla_policy_id = sc.sla_policy_id " +
                        "WHERE sc.deleted_at IS NULL AND sc.status = 'NEW' " +
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
            closeExpiredResolvedTickets(connection);
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
            CaseTicketEligibility caseInfo = ensureCaseCanReceiveTicket(connection, caseId);
            SlaPolicyDetails ticketPolicy = findSlaPolicyDetails(connection, caseInfo.caseType, priority);
            Integer slaPolicyId = ticketPolicy.slaPolicyId == null ? caseInfo.slaPolicyId : ticketPolicy.slaPolicyId;
            Timestamp dueAt = caseInfo.dueAt == null
                    ? SlaEngine.calculateDueAt(caseInfo.caseType, Timestamp.from(Instant.now()),
                    ticketPolicy.resolutionMinutes, ticketPolicy.calendarCode, ticketPolicy.businessHoursOnly)
                    : caseInfo.dueAt;
            long ticketId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO p2_sandbox.ticket " +
                            "(service_case_id, assigned_to_user_id, created_by_user_id, sla_policy_id, category_code, " +
                            "summary, description, status, priority, due_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'ASSIGNED', ?, ?) RETURNING ticket_id")) {
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
                ps.setTimestamp(9, dueAt);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ticketId = rs.getLong("ticket_id");
                }
            }
            updateCaseStatus(connection, caseId, "ASSIGNED", false);
            addTicketUpdate(connection, ticketId, user.userId, "INTERNAL", "ASSIGNMENT",
                    "Ticket created and assigned.", null, "ASSIGNED");
            JSONObject ticket = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_CREATED", null, ticket, ctx);
            notificationService.notifyTicketCreated(connection, ticketId);
            notificationService.notifyTicketAssigned(connection, ticketId);
            return ticket;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create ticket");
        }
    }

    public JSONObject listTickets(AuthenticatedUser user, String status, String type, String query,
                                  String siteId, String dateFrom, String dateTo, String due,
                                  String ticketNumber, String userFilter, String technicianFilter,
                                  String emailFilter, String titleFilter, PageRequest pageRequest) {
        StringBuilder sql = new StringBuilder(ticketBaseSql() + " WHERE t.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if ("USER".equals(user.roleCode)) {
            sql.append(" AND sc.requester_user_id = ?");
            params.add(user.userId);
        }
        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND t.status = ?");
            params.add(requireWorkflowStatus(status));
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND sc.type = ?");
            params.add(requireEnum(type, "type", "REQUEST", "INCIDENT"));
        }
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (LOWER(t.ticket_number) LIKE LOWER(?) OR LOWER(sc.case_number) LIKE LOWER(?) " +
                    "OR LOWER(sc.title) LIKE LOWER(?) OR LOWER(req.username) LIKE LOWER(?) " +
                    "OR LOWER(req.first_name || ' ' || req.last_name) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(req.email, '')) LIKE LOWER(?) OR LOWER(COALESCE(req.notification_email, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.username, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.first_name || ' ' || assignee.last_name, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.email, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.notification_email, '')) LIKE LOWER(?))");
            String like = "%" + query.trim() + "%";
            for (int i = 0; i < 11; i++) {
                params.add(like);
            }
        }
        if (ticketNumber != null && !ticketNumber.trim().isEmpty()) {
            sql.append(" AND LOWER(t.ticket_number) LIKE LOWER(?)");
            params.add("%" + ticketNumber.trim() + "%");
        }
        if (userFilter != null && !userFilter.trim().isEmpty()) {
            sql.append(" AND (LOWER(req.username) LIKE LOWER(?) OR LOWER(req.first_name || ' ' || req.last_name) LIKE LOWER(?))");
            String like = "%" + userFilter.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (technicianFilter != null && !technicianFilter.trim().isEmpty()) {
            sql.append(" AND (LOWER(COALESCE(assignee.username, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.first_name || ' ' || assignee.last_name, '')) LIKE LOWER(?))");
            String like = "%" + technicianFilter.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (emailFilter != null && !emailFilter.trim().isEmpty()) {
            sql.append(" AND (LOWER(COALESCE(req.email, '')) LIKE LOWER(?) OR LOWER(COALESCE(req.notification_email, '')) LIKE LOWER(?) " +
                    "OR LOWER(COALESCE(assignee.email, '')) LIKE LOWER(?) OR LOWER(COALESCE(assignee.notification_email, '')) LIKE LOWER(?))");
            String like = "%" + emailFilter.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (titleFilter != null && !titleFilter.trim().isEmpty()) {
            sql.append(" AND LOWER(sc.title) LIKE LOWER(?)");
            params.add("%" + titleFilter.trim() + "%");
        }
        if (siteId != null && !siteId.trim().isEmpty()) {
            sql.append(" AND sc.site_id = ?");
            params.add(Integer.parseInt(siteId));
        }
        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            sql.append(" AND sc.created_at >= CAST(? AS date)");
            params.add(dateFrom.trim());
        }
        if (dateTo != null && !dateTo.trim().isEmpty()) {
            sql.append(" AND sc.created_at < CAST(? AS date) + INTERVAL '1 day'");
            params.add(dateTo.trim());
        }
        String orderBy = " ORDER BY t.updated_at DESC, t.created_at DESC";
        String baseSql = sql.toString();
        String dueFilter = normalizeDueFilter(due);
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(dueFilter == null
                     ? baseSql + orderBy + " LIMIT ? OFFSET ?"
                     : baseSql + orderBy)) {
            closeExpiredResolvedTickets(connection);
            List<Object> queryParams = new ArrayList<>(params);
            if (dueFilter == null) {
                queryParams.add(pageRequest.pageSize);
                queryParams.add(pageRequest.offset);
            }
            long total = dueFilter == null
                    ? countRows(connection, "SELECT COUNT(*) AS total FROM (" + baseSql + ") ticket_rows", params)
                    : -1;
            bind(ps, queryParams);
            JSONArray tickets = new JSONArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject ticket = ticketSummaryJson(rs);
                    if (dueFilter == null || dueFilter.equals(ticket.getJSONObject("sla").getString("code"))) {
                        tickets.put(ticketForRole(ticket, user));
                    }
                }
            }
            return dueFilter == null ? pageRequest.toResponse(tickets, total) : pageRequest.toResponseFromFullList(tickets);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list tickets");
        }
    }

    public JSONObject getTicket(AuthenticatedUser user, Long ticketId) {
        try (Connection connection = requireConnection()) {
            closeExpiredResolvedTickets(connection);
            return getTicket(connection, user, ticketId);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to get ticket");
        }
    }

    public JSONArray listTicketAudit(AuthenticatedUser user, Long ticketId) {
        if ("USER".equals(user.roleCode)) {
            throw new AuthException(403, "Technical audit is not available to end users");
        }
        try (Connection connection = requireConnection()) {
            getTicket(connection, user, ticketId);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT al.audit_log_id, al.actor_user_id, au.username AS actor_username, al.action, " +
                            "al.before_data::text AS before_data, al.after_data::text AS after_data, al.created_at " +
                            "FROM p2_sandbox.audit_logs al " +
                            "LEFT JOIN p2_sandbox.app_users au ON au.user_id = al.actor_user_id " +
                            "WHERE al.entity_type = 'ticket' AND al.entity_id = ? " +
                            "ORDER BY al.created_at ASC")) {
                ps.setString(1, String.valueOf(ticketId));
                JSONArray entries = new JSONArray();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject before = parseJsonOrNull(rs.getString("before_data"));
                        JSONObject after = parseJsonOrNull(rs.getString("after_data"));
                        entries.put(new JSONObject()
                                .put("auditLogId", rs.getLong("audit_log_id"))
                                .put("actorUserId", longOrNull(rs, "actor_user_id"))
                                .put("actorUsername", nullToJson(rs.getString("actor_username")))
                                .put("action", rs.getString("action"))
                                .put("createdAt", timestampString(rs, "created_at"))
                                .put("changes", auditChanges(before, after)));
                    }
                }
                return entries;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list ticket audit");
        }
    }

    public JSONObject updateTicket(AuthenticatedUser user, Long ticketId, String body, Context ctx) {
        JSONObject request = parseJson(body);
        String updateBody = trimToNull(request.optString("body", ""));
        String visibility = optionalEnum(request.optString("visibility", "INTERNAL"), "visibility", "PUBLIC", "INTERNAL");
        String status = request.has("status") && !request.isNull("status")
                ? requireWorkflowStatus(request.optString("status"))
                : null;
        String priority = request.has("priority") && !request.isNull("priority")
                ? requireEnum(request.optString("priority"), "priority", "LOW", "MEDIUM", "HIGH", "CRITICAL")
                : null;
        String summary = trimToNull(request.optString("summary", ""));
        String description = trimToNull(request.optString("description", ""));
        String categoryCode = trimToNull(request.optString("categoryCode", ""));
        String caseTitle = trimToNull(request.optString("caseTitle", ""));
        String caseDescription = trimToNull(request.optString("caseDescription", ""));
        String organization = trimToNull(request.optString("organization", ""));
        String reportedBy = trimToNull(request.optString("reportedBy", ""));
        String businessService = trimToNull(request.optString("businessService", ""));
        String resolverTeam = trimToNull(request.optString("resolverTeam", ""));
        Long assignedToUserId = request.has("assignedToUserId") && !request.isNull("assignedToUserId")
                ? request.getLong("assignedToUserId") : null;
        Long requesterUserId = request.has("requesterUserId") && !request.isNull("requesterUserId")
                ? request.getLong("requesterUserId") : null;
        Integer siteId = request.has("siteId") && !request.isNull("siteId")
                ? request.getInt("siteId") : null;
        boolean locationTouched = request.has("locationId") || siteId != null;
        Long locationId = request.has("locationId") && !request.isNull("locationId")
                ? request.getLong("locationId") : null;
        boolean scheduledAtTouched = request.has("scheduledAt");
        Timestamp scheduledAt = scheduledAtTouched && !request.isNull("scheduledAt")
                ? parseTimestamp(request.optString("scheduledAt"), "scheduledAt") : null;

        boolean admin = "ADMIN".equals(user.roleCode);
        if (!admin && !"TECH".equals(user.roleCode)) {
            throw new AuthException(403, "Only technicians and administrators can update tickets");
        }
        if (updateBody == null && status == null && priority == null && summary == null && description == null
                && categoryCode == null && caseTitle == null && caseDescription == null && assignedToUserId == null
                && requesterUserId == null && siteId == null && locationId == null && organization == null
                && reportedBy == null && businessService == null && resolverTeam == null && !scheduledAtTouched) {
            throw new AuthException(400, "At least one ticket field or update body is required");
        }

        try (Connection connection = requireConnection()) {
            closeExpiredResolvedTickets(connection);
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before, user);
            boolean protectedFieldsTouched = summary != null || categoryCode != null || caseTitle != null
                    || requesterUserId != null || siteId != null || locationTouched || organization != null
                    || reportedBy != null || businessService != null || resolverTeam != null;
            if (!admin && protectedFieldsTouched
                    && !TicketWorkflow.protectedFieldsEditable(Instant.parse(before.getString("createdAt")), Instant.now())) {
                throw new AuthException(403, "Protected ticket fields can only be changed during the first two hours");
            }
            if ("RESOLVED".equals(status)) {
                throw new AuthException(400, "Use the resolve action and provide a resolution comment");
            }
            if (assignedToUserId != null) {
                ensureTechnician(connection, assignedToUserId);
            }
            if (requesterUserId != null) {
                ensureActiveUser(connection, requesterUserId);
            }
            if (siteId != null || locationTouched) {
                Integer targetSiteId = siteId != null ? siteId : before.optInt("siteId", 0);
                if (targetSiteId <= 0) {
                    throw new AuthException(400, "siteId is required when locationId is changed");
                }
                validateSiteAndLocation(connection, targetSiteId, locationId);
            }
            String previousStatus = before.optString("status", null);
            String effectiveStatus = status;
            if (updateBody != null && !"RESOLVED".equals(previousStatus)) {
                effectiveStatus = "IN_PROGRESS";
            } else if (assignedToUserId != null && effectiveStatus == null) {
                effectiveStatus = "ASSIGNED";
            }
            String resultingStatus = effectiveStatus == null ? previousStatus : effectiveStatus;
            if (scheduledAtTouched && !"SCHEDULED".equals(resultingStatus)) {
                throw new AuthException(400, "scheduledAt can only be set when status is SCHEDULED");
            }
            if ("SCHEDULED".equals(resultingStatus) && scheduledAt == null
                    && (before.isNull("scheduledAt") || !"SCHEDULED".equals(previousStatus))) {
                throw new AuthException(400, "scheduledAt is required when status is SCHEDULED");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.ticket SET " +
                            "summary = COALESCE(?, summary), " +
                            "description = COALESCE(?, description), " +
                            "category_code = COALESCE(?, category_code), " +
                            "assigned_to_user_id = COALESCE(?, assigned_to_user_id), " +
                            "resolver_team = COALESCE(?, resolver_team), " +
                            "priority = COALESCE(?, priority), " +
                            "status = COALESCE(?, status), " +
                            "scheduled_at = CASE WHEN ? THEN ? WHEN COALESCE(?, status) <> 'SCHEDULED' THEN NULL ELSE scheduled_at END, " +
                            "resolved_at = CASE WHEN status = 'RESOLVED' AND COALESCE(?, status) <> 'RESOLVED' THEN NULL ELSE resolved_at END, " +
                            "updated_at = NOW() " +
                            "WHERE ticket_id = ? AND deleted_at IS NULL")) {
                setNullableString(ps, 1, summary);
                setNullableString(ps, 2, description);
                setNullableString(ps, 3, categoryCode);
                setNullableLong(ps, 4, assignedToUserId);
                setNullableString(ps, 5, resolverTeam);
                setNullableString(ps, 6, priority);
                setNullableString(ps, 7, effectiveStatus);
                ps.setBoolean(8, scheduledAtTouched);
                if (scheduledAt == null) {
                    ps.setNull(9, Types.TIMESTAMP);
                } else {
                    ps.setTimestamp(9, scheduledAt);
                }
                setNullableString(ps, 10, effectiveStatus);
                setNullableString(ps, 11, effectiveStatus);
                ps.setLong(12, ticketId);
                ps.executeUpdate();
            }
            if (caseTitle != null || caseDescription != null || description != null || requesterUserId != null
                    || siteId != null || locationTouched || organization != null || reportedBy != null || businessService != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE p2_sandbox.service_case SET " +
                                "title = COALESCE(?, title), " +
                                "description = COALESCE(?, description), " +
                                "requester_user_id = COALESCE(?, requester_user_id), " +
                                "affected_user_id = COALESCE(?, affected_user_id), " +
                                "site_id = COALESCE(?, site_id), " +
                                "location_id = CASE WHEN ? THEN ? ELSE location_id END, " +
                                "organization = COALESCE(?, organization), " +
                                "reported_by = COALESCE(?, reported_by), " +
                                "business_service = COALESCE(?, business_service), " +
                                "updated_at = NOW() WHERE case_id = ? AND deleted_at IS NULL")) {
                    setNullableString(ps, 1, caseTitle == null ? summary : caseTitle);
                    setNullableString(ps, 2, caseDescription == null ? description : caseDescription);
                    setNullableLong(ps, 3, requesterUserId);
                    setNullableLong(ps, 4, requesterUserId);
                    if (siteId == null) {
                        ps.setNull(5, Types.INTEGER);
                    } else {
                        ps.setInt(5, siteId);
                    }
                    ps.setBoolean(6, locationTouched);
                    setNullableLong(ps, 7, locationId);
                    setNullableString(ps, 8, organization);
                    setNullableString(ps, 9, reportedBy);
                    setNullableString(ps, 10, businessService);
                    ps.setLong(11, before.getLong("serviceCaseId"));
                    ps.executeUpdate();
                }
            }
            if (effectiveStatus != null) {
                updateCaseStatus(connection, before.getLong("serviceCaseId"), effectiveStatus, false);
            }
            if (updateBody != null || effectiveStatus != null || assignedToUserId != null) {
                String updateType = assignedToUserId != null ? "ASSIGNMENT" : (status == null ? "COMMENT" : "STATUS_CHANGE");
                String generatedBody = assignedToUserId != null ? "Ticket reassigned."
                        : (effectiveStatus == null ? updateBody : "Status changed to " + effectiveStatus);
                addTicketUpdate(connection, ticketId, user.userId, visibility, updateType,
                        updateBody == null ? generatedBody : updateBody,
                        previousStatus, effectiveStatus);
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
            closeExpiredResolvedTickets(connection);
            ensureTechnician(connection, assignedToUserId);
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before, user);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.ticket SET assigned_to_user_id = ?, status = 'ASSIGNED', " +
                            "resolved_at = NULL, closed_at = NULL, updated_at = NOW() " +
                            "WHERE ticket_id = ? AND deleted_at IS NULL")) {
                ps.setLong(1, assignedToUserId);
                ps.setLong(2, ticketId);
                ps.executeUpdate();
            }
            updateCaseStatus(connection, before.getLong("serviceCaseId"), "ASSIGNED", false);
            addTicketUpdate(connection, ticketId, user.userId, "INTERNAL", "ASSIGNMENT",
                    "Ticket assigned.", before.optString("status"), "ASSIGNED");
            JSONObject after = getTicket(connection, user, ticketId);
            audit(connection, user.userId, "ticket", String.valueOf(ticketId), "TICKET_ASSIGNED", before, after, ctx);
            notificationService.notifyTicketAssigned(connection, ticketId);
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
            closeExpiredResolvedTickets(connection);
            JSONObject before = getTicket(connection, user, ticketId);
            ensureTicketCanChange(before, user);
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
            notificationService.notifyTicketResolved(connection, ticketId);
            return after;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to resolve ticket");
        }
    }

    public JSONObject retryDueNotifications() {
        try (Connection connection = requireConnection()) {
            return notificationService.retryDueEmailNotifications(connection);
        } catch (Exception e) {
            throw new AuthException(500, "Unable to retry notifications");
        }
    }

    public int closeExpiredResolvedTickets() {
        try (Connection connection = requireConnection()) {
            return closeExpiredResolvedTickets(connection);
        } catch (Exception e) {
            throw new AuthException(500, "Unable to close expired resolved tickets");
        }
    }

    private int closeExpiredResolvedTickets(Connection connection) throws Exception {
        int closed;
        try (PreparedStatement ps = connection.prepareStatement(
                "WITH expired AS (" +
                        "UPDATE p2_sandbox.ticket SET status = 'CLOSED', closed_at = NOW(), updated_at = NOW() " +
                        "WHERE status = 'RESOLVED' AND resolved_at <= NOW() - INTERVAL '30 minutes' " +
                        "AND deleted_at IS NULL RETURNING ticket_id, service_case_id" +
                        ") INSERT INTO p2_sandbox.ticket_updates " +
                        "(ticket_id, author_user_id, visibility, update_type, body, previous_status, new_status) " +
                        "SELECT ticket_id, NULL, 'PUBLIC', 'SYSTEM', " +
                        "'Ticket cerrado automaticamente 30 minutos despues de su resolucion.', 'RESOLVED', 'CLOSED' " +
                        "FROM expired")) {
            closed = ps.executeUpdate();
        }
        if (closed > 0) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.service_case sc SET status = 'CLOSED', closed_at = NOW(), updated_at = NOW() " +
                            "WHERE sc.status = 'RESOLVED' AND EXISTS (" +
                            "SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id " +
                            "AND t.status = 'CLOSED' AND t.deleted_at IS NULL)")) {
                ps.executeUpdate();
            }
        }
        return closed;
    }

    private JSONObject dashboardMetricsForType(Connection connection, AuthenticatedUser user, String type,
                                               LocalDate from, LocalDate toExclusive) throws Exception {
        DashboardMetrics.TypeCounters counters = new DashboardMetrics.TypeCounters(type);
        counters.casesWithoutTicket = countCasesWithoutTicket(connection, type, from, toExclusive);
        StringBuilder sql = new StringBuilder(
                "SELECT t.status, sc.created_at AS case_created_at, t.resolved_at, " +
                        "COALESCE(t.due_at, sc.due_at) AS due_at, sp.warning_percent " +
                        "FROM p2_sandbox.ticket t " +
                        "JOIN p2_sandbox.service_case sc ON sc.case_id = t.service_case_id " +
                        "LEFT JOIN p2_sandbox.sla_policies sp ON sp.sla_policy_id = COALESCE(t.sla_policy_id, sc.sla_policy_id) " +
                        "WHERE t.deleted_at IS NULL AND sc.deleted_at IS NULL AND sc.type = ? " +
                        "AND sc.created_at >= ? AND sc.created_at < ?");
        List<Object> params = new ArrayList<>();
        params.add(type);
        params.add(java.sql.Date.valueOf(from));
        params.add(java.sql.Date.valueOf(toExclusive));
        if ("TECH".equals(user.roleCode)) {
            sql.append(" AND t.assigned_to_user_id = ?");
            params.add(user.userId);
        }
        sql.append(" ORDER BY sc.created_at DESC");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SlaEngine.SlaSnapshot snapshot = SlaEngine.evaluate(
                            type,
                            rs.getTimestamp("case_created_at"),
                            rs.getTimestamp("due_at"),
                            rs.getTimestamp("resolved_at"),
                            rs.getString("status"),
                            doubleOrNull(rs, "warning_percent"));
                    counters.addTicket(rs.getString("status"), snapshot.code);
                }
            }
        }
        return DashboardMetrics.toJson(counters);
    }

    private long countCasesWithoutTicket(Connection connection, String type, LocalDate from, LocalDate toExclusive) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) AS total " +
                        "FROM p2_sandbox.service_case sc " +
                        "WHERE sc.deleted_at IS NULL AND sc.status = 'NEW' AND sc.type = ? " +
                        "AND sc.created_at >= ? AND sc.created_at < ? " +
                        "AND NOT EXISTS (SELECT 1 FROM p2_sandbox.ticket t " +
                        "WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL)")) {
            ps.setString(1, type);
            ps.setDate(2, java.sql.Date.valueOf(from));
            ps.setDate(3, java.sql.Date.valueOf(toExclusive));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("total");
            }
        }
    }

    private JSONObject totalDashboardMetrics(JSONArray byType) {
        DashboardMetrics.TypeCounters counters = new DashboardMetrics.TypeCounters("TOTAL");
        for (int i = 0; i < byType.length(); i++) {
            JSONObject item = byType.getJSONObject(i);
            counters.casesWithoutTicket += item.getLong("casesWithoutTicket");
            counters.ticketsTotal += item.getLong("ticketsTotal");
            counters.openTickets += item.getLong("openTickets");
            counters.inProgressTickets += item.getLong("inProgressTickets");
            counters.resolvedTickets += item.getLong("resolvedTickets");
            counters.warningTickets += item.getLong("warningTickets");
            counters.withinSla += item.getLong("withinSla");
            counters.outsideSla += item.getLong("outsideSla");
        }
        return DashboardMetrics.toJson(counters);
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new AuthException(400, "Invalid date format. Use YYYY-MM-DD");
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

    private JSONObject queryPagedServiceCases(String baseSql, String orderBy, List<Object> params, PageRequest pageRequest) {
        try (Connection connection = requireConnection()) {
            long total = countRows(connection, "SELECT COUNT(*) AS total FROM (" + baseSql + ") service_case_rows", params);
            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(pageRequest.pageSize);
            queryParams.add(pageRequest.offset);
            try (PreparedStatement ps = connection.prepareStatement(baseSql + orderBy + " LIMIT ? OFFSET ?")) {
                bind(ps, queryParams);
                JSONArray cases = new JSONArray();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cases.put(serviceCaseSummaryJson(rs));
                    }
                }
                return pageRequest.toResponse(cases, total);
            }
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list service cases");
        }
    }

    private long countRows(Connection connection, String sql, List<Object> params) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("total");
            }
        }
    }

    private JSONObject getServiceCase(Connection connection, AuthenticatedUser user, Long caseId) throws Exception {
        String sql = "SELECT sc.case_id, sc.case_number, sc.type, sc.title, sc.description, sc.status, sc.priority, " +
                "sc.created_at, sc.updated_at, sc.resolved_at, sc.due_at, s.site_id, s.name AS site_name, " +
                "l.name AS location_name, sp.warning_percent, sp.resolution_minutes, sp.calendar_code, " +
                "sp.business_hours_only, " +
                "u.username AS requester_username, EXISTS (" +
                "SELECT 1 FROM p2_sandbox.ticket t WHERE t.service_case_id = sc.case_id AND t.deleted_at IS NULL" +
                ") AS has_ticket " +
                "FROM p2_sandbox.service_case sc " +
                "JOIN p2_sandbox.app_users u ON u.user_id = sc.requester_user_id " +
                "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                "LEFT JOIN p2_sandbox.sla_policies sp ON sp.sla_policy_id = sc.sla_policy_id " +
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
                return serviceCaseForRole(serviceCase, user);
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
                JSONObject ticket = ticketForRole(ticketSummaryJson(rs), user);
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
                JSONObject ticket = ticketForRole(ticketSummaryJson(rs), user);
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
                "t.status, t.priority, t.category_code, t.resolver_team, t.scheduled_at, t.created_at, t.updated_at, " +
                "t.resolved_at, t.closed_at, t.due_at AS ticket_due_at, " +
                "sc.case_number, sc.type AS case_type, sc.title AS case_title, sc.requester_user_id, " +
                "sc.organization, sc.reported_by, sc.business_service, sc.created_at AS case_created_at, " +
                "sc.due_at AS service_case_due_at, sc.site_id, s.name AS site_name, " +
                "l.location_id, l.name AS location_name, " +
                "sp.warning_percent, sp.resolution_minutes, sp.calendar_code, sp.business_hours_only, " +
                "req.first_name AS requester_first_name, req.last_name AS requester_last_name, " +
                "req.email AS requester_email, req.notification_email AS requester_notification_email, " +
                "req.username AS requester_username, assignee.user_id AS assigned_to_user_id, " +
                "assignee.username AS assigned_to_username, assignee.first_name AS assigned_to_first_name, " +
                "assignee.last_name AS assigned_to_last_name, assignee.email AS assigned_to_email, " +
                "assignee.notification_email AS assigned_to_notification_email " +
                "FROM p2_sandbox.ticket t " +
                "JOIN p2_sandbox.service_case sc ON sc.case_id = t.service_case_id " +
                "JOIN p2_sandbox.app_users req ON req.user_id = sc.requester_user_id " +
                "LEFT JOIN p2_sandbox.app_users assignee ON assignee.user_id = t.assigned_to_user_id " +
                "LEFT JOIN p2_sandbox.sites s ON s.site_id = sc.site_id " +
                "LEFT JOIN p2_sandbox.locations l ON l.location_id = sc.location_id " +
                "LEFT JOIN p2_sandbox.sla_policies sp ON sp.sla_policy_id = COALESCE(t.sla_policy_id, sc.sla_policy_id)";
    }

    private JSONObject serviceCaseSummaryJson(ResultSet rs) throws Exception {
        Timestamp dueAt = rs.getTimestamp("due_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        SlaEngine.SlaSnapshot sla = SlaEngine.evaluate(
                rs.getString("type"),
                createdAt,
                dueAt,
                resolvedAt,
                rs.getString("status"),
                doubleOrNull(rs, "warning_percent"));
        return new JSONObject()
                .put("caseId", rs.getLong("case_id"))
                .put("caseNumber", rs.getString("case_number"))
                .put("type", rs.getString("type"))
                .put("title", rs.getString("title"))
                .put("description", rs.getString("description"))
                .put("status", rs.getString("status"))
                .put("priority", rs.getString("priority"))
                .put("siteId", longOrNull(rs, "site_id"))
                .put("siteName", nullToJson(rs.getString("site_name")))
                .put("locationName", nullToJson(rs.getString("location_name")))
                .put("requesterUsername", rs.getString("requester_username"))
                .put("hasTicket", rs.getBoolean("has_ticket"))
                .put("createdAt", timestampString(rs, "created_at"))
                .put("updatedAt", timestampString(rs, "updated_at"))
                .put("dueAt", dueAt == null ? JSONObject.NULL : dueAt.toInstant().toString())
                .put("sla", sla.toJson());
    }

    private JSONObject ticketSummaryJson(ResultSet rs) throws Exception {
        Timestamp dueAt = rs.getTimestamp("ticket_due_at");
        if (dueAt == null) {
            dueAt = rs.getTimestamp("service_case_due_at");
        }
        Timestamp createdAt = rs.getTimestamp("case_created_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        SlaEngine.SlaSnapshot sla = SlaEngine.evaluate(
                rs.getString("case_type"),
                createdAt,
                dueAt,
                resolvedAt,
                rs.getString("status"),
                doubleOrNull(rs, "warning_percent"));
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
                .put("organization", nullToJson(rs.getString("organization")))
                .put("reportedBy", nullToJson(rs.getString("reported_by")))
                .put("businessService", nullToJson(rs.getString("business_service")))
                .put("resolverTeam", nullToJson(rs.getString("resolver_team")))
                .put("siteId", longOrNull(rs, "site_id"))
                .put("siteName", nullToJson(rs.getString("site_name")))
                .put("locationId", longOrNull(rs, "location_id"))
                .put("locationName", nullToJson(rs.getString("location_name")))
                .put("requesterUserId", rs.getLong("requester_user_id"))
                .put("requesterUsername", rs.getString("requester_username"))
                .put("requesterName", fullName(rs.getString("requester_first_name"), rs.getString("requester_last_name")))
                .put("requesterEmail", firstNonEmpty(rs.getString("requester_notification_email"), rs.getString("requester_email")))
                .put("assignedToUserId", longOrNull(rs, "assigned_to_user_id"))
                .put("assignedToUsername", nullToJson(rs.getString("assigned_to_username")))
                .put("assignedToName", fullName(rs.getString("assigned_to_first_name"), rs.getString("assigned_to_last_name")))
                .put("assignedToEmail", firstNonEmpty(rs.getString("assigned_to_notification_email"), rs.getString("assigned_to_email")))
                .put("createdAt", timestampString(rs, "created_at"))
                .put("updatedAt", timestampString(rs, "updated_at"))
                .put("resolvedAt", timestampString(rs, "resolved_at"))
                .put("closedAt", timestampString(rs, "closed_at"))
                .put("scheduledAt", timestampString(rs, "scheduled_at"))
                .put("dueAt", dueAt == null ? JSONObject.NULL : dueAt.toInstant().toString())
                .put("protectedFieldsEditableUntil", createdAt == null ? JSONObject.NULL
                        : createdAt.toInstant().plus(TicketWorkflow.PROTECTED_FIELD_WINDOW).toString())
                .put("resolutionEditableUntil", resolvedAt == null ? JSONObject.NULL
                        : resolvedAt.toInstant().plus(TicketWorkflow.RESOLUTION_GRACE_PERIOD).toString())
                .put("locked", TicketWorkflow.isLocked(rs.getString("status"),
                        resolvedAt == null ? null : resolvedAt.toInstant(), Instant.now()))
                .put("sla", sla.toJson());
    }

    private JSONObject ticketForRole(JSONObject ticket, AuthenticatedUser user) {
        if (!"USER".equals(user.roleCode)) {
            return ticket;
        }
        return new JSONObject()
                .put("ticketId", ticket.opt("ticketId"))
                .put("ticketNumber", ticket.opt("ticketNumber"))
                .put("serviceCaseId", ticket.opt("serviceCaseId"))
                .put("caseNumber", ticket.opt("caseNumber"))
                .put("caseType", ticket.opt("caseType"))
                .put("caseTitle", ticket.opt("caseTitle"))
                .put("summary", ticket.opt("summary"))
                .put("description", ticket.opt("description"))
                .put("status", ticket.opt("status"))
                .put("resolution", ticket.opt("resolution"))
                .put("createdAt", ticket.opt("createdAt"))
                .put("resolvedAt", ticket.opt("resolvedAt"))
                .put("closedAt", ticket.opt("closedAt"))
                .put("locked", ticket.opt("locked"));
    }

    private JSONObject serviceCaseForRole(JSONObject serviceCase, AuthenticatedUser user) {
        if (!"USER".equals(user.roleCode)) {
            return serviceCase;
        }
        JSONObject result = new JSONObject()
                .put("caseId", serviceCase.opt("caseId"))
                .put("caseNumber", serviceCase.opt("caseNumber"))
                .put("type", serviceCase.opt("type"))
                .put("title", serviceCase.opt("title"))
                .put("description", serviceCase.opt("description"))
                .put("status", serviceCase.opt("status"))
                .put("createdAt", serviceCase.opt("createdAt"));
        if (serviceCase.has("hasTicket")) {
            result.put("hasTicket", serviceCase.opt("hasTicket"));
        }
        if (serviceCase.has("ticket")) {
            result.put("ticket", serviceCase.opt("ticket"));
        }
        return result;
    }

    private JSONObject getSiteById(Connection connection, Integer siteId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT site_id, code, name, address, city, province, country, is_active " +
                        "FROM p2_sandbox.sites WHERE site_id = ?")) {
            ps.setInt(1, siteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Site not found");
                }
                return siteJson(rs);
            }
        }
    }

    private JSONObject getLocationById(Connection connection, Long locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT location_id, site_id, parent_location_id, type, code, name, floor, description, is_active " +
                        "FROM p2_sandbox.locations WHERE location_id = ?")) {
            ps.setLong(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Location not found");
                }
                return locationJson(rs);
            }
        }
    }

    private JSONObject siteJson(ResultSet rs) throws Exception {
        return new JSONObject()
                .put("siteId", rs.getInt("site_id"))
                .put("code", rs.getString("code"))
                .put("name", rs.getString("name"))
                .put("address", nullToJson(rs.getString("address")))
                .put("city", nullToJson(rs.getString("city")))
                .put("province", nullToJson(rs.getString("province")))
                .put("country", rs.getString("country"))
                .put("isActive", rs.getBoolean("is_active"));
    }

    private JSONObject locationJson(ResultSet rs) throws Exception {
        return new JSONObject()
                .put("locationId", rs.getLong("location_id"))
                .put("siteId", rs.getInt("site_id"))
                .put("parentLocationId", longOrNull(rs, "parent_location_id"))
                .put("type", rs.getString("type"))
                .put("code", rs.getString("code"))
                .put("name", rs.getString("name"))
                .put("floor", nullToJson(rs.getString("floor")))
                .put("description", nullToJson(rs.getString("description")))
                .put("isActive", rs.getBoolean("is_active"));
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

    private CaseTicketEligibility ensureCaseCanReceiveTicket(Connection connection, Long caseId) throws Exception {
        CaseTicketEligibility caseInfo = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type, status, sla_policy_id, due_at FROM p2_sandbox.service_case WHERE case_id = ? AND deleted_at IS NULL")) {
            ps.setLong(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "Service case not found");
                }
                if (!"NEW".equals(rs.getString("status"))) {
                    throw new AuthException(400, "Only open service cases can receive a ticket");
                }
                Integer slaPolicyId = intOrNull(rs, "sla_policy_id");
                caseInfo = new CaseTicketEligibility(rs.getString("type"), slaPolicyId, rs.getTimestamp("due_at"));
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
        return caseInfo;
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

    private void ensureActiveUser(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM p2_sandbox.app_users WHERE user_id = ? AND is_active = TRUE AND deleted_at IS NULL")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(400, "requesterUserId must reference an active user");
                }
            }
        }
    }

    private void ensureTicketCanChange(JSONObject ticket, AuthenticatedUser user) {
        String status = ticket.optString("status", "");
        Instant resolvedAt = ticket.isNull("resolvedAt") ? null : Instant.parse(ticket.getString("resolvedAt"));
        if (TicketWorkflow.isLocked(status, resolvedAt, Instant.now())) {
            throw new AuthException(409, "Closed tickets cannot be changed");
        }
    }

    private SlaPolicyDetails findSlaPolicyDetails(Connection connection, String caseType, String priority) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sla_policy_id, resolution_minutes, warning_percent, calendar_code, business_hours_only " +
                        "FROM p2_sandbox.sla_policies " +
                        "WHERE case_type = ? AND is_active = TRUE AND deleted_at IS NULL " +
                        "ORDER BY CASE WHEN priority = ? THEN 0 WHEN is_default THEN 1 ELSE 2 END, sla_policy_id ASC LIMIT 1")) {
            ps.setString(1, caseType);
            ps.setString(2, priority);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return SlaPolicyDetails.defaultFor(caseType);
                }
                return new SlaPolicyDetails(
                        rs.getInt("sla_policy_id"),
                        rs.getInt("resolution_minutes"),
                        doubleOrNull(rs, "warning_percent"),
                        rs.getString("calendar_code"),
                        rs.getBoolean("business_hours_only"));
            }
        }
    }

    private void updateCaseStatus(Connection connection, Long caseId, String status, boolean resolved) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.service_case SET status = ?, updated_at = NOW(), " +
                        "resolved_at = CASE WHEN ? THEN NOW() WHEN ? NOT IN ('RESOLVED', 'CLOSED') THEN NULL ELSE resolved_at END, " +
                        "closed_at = CASE WHEN ? = 'CLOSED' THEN NOW() WHEN ? <> 'CLOSED' THEN NULL ELSE closed_at END " +
                        "WHERE case_id = ?")) {
            ps.setString(1, status);
            ps.setBoolean(2, resolved);
            ps.setString(3, status);
            ps.setString(4, status);
            ps.setString(5, status);
            ps.setLong(6, caseId);
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

    private JSONArray auditChanges(JSONObject before, JSONObject after) {
        JSONArray changes = new JSONArray();
        if (before == null && after == null) {
            return changes;
        }
        Set<String> keys = new LinkedHashSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        for (String key : keys) {
            Object previousValue = before == null || !before.has(key) ? JSONObject.NULL : before.opt(key);
            Object newValue = after == null || !after.has(key) ? JSONObject.NULL : after.opt(key);
            if (!jsonComparable(previousValue).equals(jsonComparable(newValue))) {
                changes.put(new JSONObject()
                        .put("field", key)
                        .put("previousValue", jsonValue(previousValue))
                        .put("newValue", jsonValue(newValue)));
            }
        }
        return changes;
    }

    private Connection requireConnection() {
        Connection connection = ConnectionObject.createConnection();
        if (connection == null) {
            throw new AuthException(500, "Database connection unavailable");
        }
        return connection;
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (Exception ignored) {
        }
    }

    private JSONObject parseJson(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(body);
    }

    private JSONObject parseJsonOrNull(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
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

    private String requireWorkflowStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!TicketWorkflow.isWorkflowStatus(normalized)) {
            throw new AuthException(400, "Invalid status");
        }
        return normalized;
    }

    private Timestamp parseTimestamp(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            if (normalized.endsWith("Z")) {
                return Timestamp.from(Instant.parse(normalized));
            }
            if (normalized.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return Timestamp.from(OffsetDateTime.parse(normalized).toInstant());
            }
            return Timestamp.valueOf(normalized.replace('T', ' ') + (normalized.length() == 16 ? ":00" : ""));
        } catch (Exception e) {
            throw new AuthException(400, "Invalid " + field + ". Use ISO-8601 date and time");
        }
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
            } else if (param instanceof java.sql.Date) {
                ps.setDate(i + 1, (java.sql.Date) param);
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

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws Exception {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private Object nullToJson(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private Object jsonValue(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private String jsonComparable(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private Object longOrNull(ResultSet rs, String column) throws Exception {
        long value = rs.getLong(column);
        return rs.wasNull() ? JSONObject.NULL : value;
    }

    private Integer intOrNull(ResultSet rs, String column) throws Exception {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Double doubleOrNull(ResultSet rs, String column) throws Exception {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private Object timestampString(ResultSet rs, String column) throws Exception {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? JSONObject.NULL : timestamp.toInstant().toString();
    }

    private Object firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return JSONObject.NULL;
    }

    private Object fullName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String fullName = (first + " " + last).trim();
        return fullName.isEmpty() ? JSONObject.NULL : fullName;
    }

    private String normalizeDueFilter(String due) {
        if (due == null || due.trim().isEmpty()) {
            return null;
        }
        String normalized = due.trim().toUpperCase();
        if ("ON_TRACK".equals(normalized) || "WARNING".equals(normalized) || "BREACHED".equals(normalized)
                || "MET".equals(normalized) || "MET_LATE".equals(normalized)) {
            return normalized;
        }
        throw new AuthException(400, "Invalid due filter");
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

    private static class SlaPolicyDetails {
        private final Integer slaPolicyId;
        private final Integer resolutionMinutes;
        private final Double warningPercent;
        private final String calendarCode;
        private final boolean businessHoursOnly;

        private SlaPolicyDetails(Integer slaPolicyId, Integer resolutionMinutes, Double warningPercent,
                                 String calendarCode, boolean businessHoursOnly) {
            this.slaPolicyId = slaPolicyId;
            this.resolutionMinutes = resolutionMinutes;
            this.warningPercent = warningPercent;
            this.calendarCode = calendarCode == null || calendarCode.trim().isEmpty() ? "24x7" : calendarCode;
            this.businessHoursOnly = businessHoursOnly;
        }

        private static SlaPolicyDetails defaultFor(String caseType) {
            return new SlaPolicyDetails(null, SlaEngine.defaultResolutionMinutes(caseType),
                    SlaEngine.defaultWarningPercent(), "24x7", false);
        }
    }

    private static class CaseTicketEligibility {
        private final String caseType;
        private final Integer slaPolicyId;
        private final Timestamp dueAt;

        private CaseTicketEligibility(String caseType, Integer slaPolicyId, Timestamp dueAt) {
            this.caseType = caseType;
            this.slaPolicyId = slaPolicyId;
            this.dueAt = dueAt;
        }
    }
}
