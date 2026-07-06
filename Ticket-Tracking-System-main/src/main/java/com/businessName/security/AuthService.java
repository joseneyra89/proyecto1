package com.businessName.security;

import com.businessName.common.PageRequest;
import com.businessName.ticketDao.ConnectionObject;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class AuthService {
    private static final int SESSION_TTL_MINUTES = 480;
    private static final int RESET_TTL_MINUTES = 30;

    public JSONObject login(String body, Context ctx) {
        JSONObject request = parseJson(body);
        String username = request.optString("username", "").trim();
        String password = request.optString("pass", request.optString("password", ""));
        if (username.isEmpty() || password.isEmpty()) {
            throw new AuthException(400, "Username and password are required");
        }

        try (Connection connection = requireConnection()) {
            UserCredentials credentials = findCredentialsByUsername(connection, username);
            if (credentials == null || !Boolean.TRUE.equals(credentials.user.isActive)) {
                audit(connection, null, "auth", username, "LOGIN_FAILED",
                        null, jsonObject("reason", "INVALID_USERNAME_OR_INACTIVE"), ctx);
                throw new AuthException(401, "Invalid credentials");
            }
            if (!PasswordHasher.verifyPassword(password, credentials.passwordHash)) {
                audit(connection, credentials.user.userId, "auth", credentials.user.userId.toString(), "LOGIN_FAILED",
                        null, jsonObject("reason", "INVALID_PASSWORD"), ctx);
                throw new AuthException(401, "Invalid credentials");
            }

            if (PasswordHasher.isLegacyHash(credentials.passwordHash)) {
                updatePasswordHash(connection, credentials.user.userId, PasswordHasher.hashPassword(password));
            }

            String token = PasswordHasher.generateToken();
            String tokenHash = PasswordHasher.sha256Hex(token);
            Instant expiresAt = Instant.now().plus(SESSION_TTL_MINUTES, ChronoUnit.MINUTES);
            insertSession(connection, credentials.user.userId, tokenHash, expiresAt, ctx);
            updateLastLogin(connection, credentials.user.userId);
            audit(connection, credentials.user.userId, "auth", credentials.user.userId.toString(), "LOGIN_SUCCESS",
                    null, credentials.user.toJson(), ctx);

            return new JSONObject()
                    .put("token", token)
                    .put("expiresAt", expiresAt.toString())
                    .put("user", credentials.user.toJson());
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to login");
        }
    }

    public JSONObject logout(Context ctx) {
        String token = extractToken(ctx);
        AuthenticatedUser user = authenticate(ctx);
        if (token == null || token.isEmpty() || user == null) {
            throw new AuthException(401, "Authentication required");
        }
        try (Connection connection = requireConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.auth_sessions SET revoked_at = NOW() WHERE token_hash = ? AND revoked_at IS NULL")) {
                ps.setString(1, PasswordHasher.sha256Hex(token));
                ps.executeUpdate();
            }
            audit(connection, user.userId, "auth", user.userId.toString(), "LOGOUT",
                    null, jsonObject("session", "revoked"), ctx);
            return new JSONObject().put("message", "Logout successful");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to logout");
        }
    }

    public AuthenticatedUser authenticate(Context ctx) {
        String token = extractToken(ctx);
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT u.user_id, u.legacy_employee_id, r.code AS role_code, u.username, " +
                             "u.first_name, u.last_name, u.email, u.notification_email, u.job_title, u.phone, u.is_active " +
                             "FROM p2_sandbox.auth_sessions s " +
                             "JOIN p2_sandbox.app_users u ON u.user_id = s.user_id " +
                             "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                             "WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > NOW() " +
                             "AND u.is_active = TRUE AND u.deleted_at IS NULL")) {
            ps.setString(1, PasswordHasher.sha256Hex(token));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readUser(rs);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public JSONObject me(AuthenticatedUser user) {
        return user.toJson();
    }

    public JSONObject updateOwnProfile(AuthenticatedUser user, String body, Context ctx) {
        JSONObject request = parseJson(body);
        try (Connection connection = requireConnection()) {
            JSONObject before = getUserById(connection, user.userId).toJson();
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.app_users SET " +
                            "first_name = COALESCE(?, first_name), " +
                            "last_name = COALESCE(?, last_name), " +
                            "email = COALESCE(?, email), " +
                            "notification_email = COALESCE(?, notification_email), " +
                            "job_title = COALESCE(?, job_title), " +
                            "phone = COALESCE(?, phone), " +
                            "updated_at = NOW() " +
                            "WHERE user_id = ? RETURNING user_id, legacy_employee_id, " +
                            "(SELECT code FROM p2_sandbox.roles WHERE role_id = app_users.role_id) AS role_code, " +
                            "username, first_name, last_name, email, notification_email, job_title, phone, is_active")) {
                setNullableString(ps, 1, optionalString(request, "firstName"));
                setNullableString(ps, 2, optionalString(request, "lastName"));
                setNullableString(ps, 3, optionalString(request, "email"));
                setNullableString(ps, 4, optionalString(request, "notificationEmail"));
                setNullableString(ps, 5, optionalString(request, "jobTitle"));
                setNullableString(ps, 6, optionalString(request, "phone"));
                ps.setLong(7, user.userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "User not found");
                    }
                    AuthenticatedUser updated = readUser(rs);
                    audit(connection, user.userId, "app_users", user.userId.toString(), "PROFILE_UPDATED",
                            before, updated.toJson(), ctx);
                    return updated.toJson();
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update profile");
        }
    }

    public JSONObject requestPasswordReset(String body, Context ctx) {
        JSONObject request = parseJson(body);
        String lookup = request.optString("username", request.optString("email", "")).trim();
        JSONObject response = new JSONObject()
                .put("message", "If the account exists, a reset token has been generated.");
        if (lookup.isEmpty()) {
            return response;
        }

        try (Connection connection = requireConnection()) {
            AuthenticatedUser user = findUserByLookup(connection, lookup);
            if (user == null) {
                audit(connection, null, "auth", lookup, "PASSWORD_RESET_REQUESTED",
                        null, jsonObject("accountFound", false), ctx);
                return response;
            }

            String resetToken = PasswordHasher.generateToken();
            String tokenHash = PasswordHasher.sha256Hex(resetToken);
            Instant expiresAt = Instant.now().plus(RESET_TTL_MINUTES, ChronoUnit.MINUTES);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO p2_sandbox.password_reset_tokens " +
                            "(user_id, token_hash, expires_at, ip_address, user_agent) " +
                            "VALUES (?, ?, ?, NULLIF(?, '')::inet, ?)")) {
                ps.setLong(1, user.userId);
                ps.setString(2, tokenHash);
                ps.setTimestamp(3, Timestamp.from(expiresAt));
                ps.setString(4, ipAddress(ctx));
                ps.setString(5, userAgent(ctx));
                ps.executeUpdate();
            }
            audit(connection, user.userId, "auth", user.userId.toString(), "PASSWORD_RESET_REQUESTED",
                    null, jsonObject("expiresAt", expiresAt.toString()), ctx);

            // Local app has no email service yet, so return the token explicitly for the UI/operator flow.
            response.put("resetToken", resetToken);
            response.put("expiresAt", expiresAt.toString());
            return response;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to request password reset");
        }
    }

    public JSONObject resetPassword(String body, Context ctx) {
        JSONObject request = parseJson(body);
        String token = request.optString("token", "").trim();
        String newPassword = request.optString("newPassword", request.optString("password", ""));
        if (token.isEmpty() || newPassword.length() < 8) {
            throw new AuthException(400, "Token and a password of at least 8 characters are required");
        }

        try (Connection connection = requireConnection()) {
            Long userId = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT user_id FROM p2_sandbox.password_reset_tokens " +
                            "WHERE token_hash = ? AND used_at IS NULL AND expires_at > NOW()")) {
                ps.setString(1, PasswordHasher.sha256Hex(token));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("user_id");
                    }
                }
            }
            if (userId == null) {
                throw new AuthException(400, "Invalid or expired reset token");
            }

            updatePasswordHash(connection, userId, PasswordHasher.hashPassword(newPassword));
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.password_reset_tokens SET used_at = NOW() WHERE token_hash = ?")) {
                ps.setString(1, PasswordHasher.sha256Hex(token));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.auth_sessions SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL")) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            }
            audit(connection, userId, "auth", userId.toString(), "PASSWORD_RESET_COMPLETED",
                    null, jsonObject("sessionsRevoked", true), ctx);
            return new JSONObject().put("message", "Password reset successful");
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to reset password");
        }
    }

    public JSONObject listUsers(PageRequest pageRequest) {
        JSONArray users = new JSONArray();
        String fromSql = "FROM p2_sandbox.app_users u " +
                "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                "WHERE u.deleted_at IS NULL";
        try (Connection connection = requireConnection()) {
            long total;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS total " + fromSql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getLong("total");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT u.user_id, u.legacy_employee_id, r.code AS role_code, u.username, " +
                            "u.first_name, u.last_name, u.email, u.notification_email, u.job_title, u.phone, u.is_active " +
                            fromSql + " ORDER BY u.user_id LIMIT ? OFFSET ?")) {
                ps.setInt(1, pageRequest.pageSize);
                ps.setInt(2, pageRequest.offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        users.put(readUser(rs).toJson());
                    }
                }
            }
            return pageRequest.toResponse(users, total);
        } catch (Exception e) {
            throw new AuthException(500, "Unable to list users");
        }
    }

    public JSONObject createUser(String body, AuthenticatedUser actor, Context ctx) {
        JSONObject request = parseJson(body);
        String role = request.optString("role", "USER").trim().toUpperCase();
        validateRole(role);
        String username = requireString(request, "username", 3, 80);
        String firstName = requireString(request, "firstName", 1, 80);
        String lastName = requireString(request, "lastName", 1, 80);
        String password = request.optString("password", request.optString("temporaryPassword", ""));
        if (password.length() < 8 || password.length() > 120) {
            throw new AuthException(400, "Temporary password must be between 8 and 120 characters");
        }
        String email = optionalString(request, "email");
        String notificationEmail = optionalString(request, "notificationEmail");
        if (notificationEmail == null) {
            notificationEmail = email;
        }
        boolean isActive = !request.has("isActive") || request.getBoolean("isActive");

        try (Connection connection = requireConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO p2_sandbox.app_users " +
                             "(role_id, username, password_hash, first_name, last_name, email, notification_email, " +
                             "job_title, phone, is_active, created_at, updated_at) " +
                             "VALUES ((SELECT role_id FROM p2_sandbox.roles WHERE code = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW()) " +
                             "RETURNING user_id, legacy_employee_id, " +
                             "(SELECT code FROM p2_sandbox.roles WHERE role_id = app_users.role_id) AS role_code, " +
                             "username, first_name, last_name, email, notification_email, job_title, phone, is_active")) {
            ps.setString(1, role);
            ps.setString(2, username);
            ps.setString(3, PasswordHasher.hashPassword(password));
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            setNullableString(ps, 6, email);
            setNullableString(ps, 7, notificationEmail);
            setNullableString(ps, 8, optionalString(request, "jobTitle"));
            setNullableString(ps, 9, optionalString(request, "phone"));
            ps.setBoolean(10, isActive);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                AuthenticatedUser created = readUser(rs);
                audit(connection, actor.userId, "app_users", created.userId.toString(), "ADMIN_USER_CREATED",
                        null, created.toJson(), ctx);
                return created.toJson();
            }
        } catch (AuthException e) {
            throw e;
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new AuthException(409, "Username already exists");
            }
            throw new AuthException(500, "Unable to create user");
        } catch (Exception e) {
            throw new AuthException(500, "Unable to create user");
        }
    }

    public JSONObject updateUser(Long targetUserId, String body, AuthenticatedUser actor, Context ctx) {
        JSONObject request = parseJson(body);
        validateRole(request.optString("role", null));
        try (Connection connection = requireConnection()) {
            JSONObject before = getUserById(connection, targetUserId).toJson();
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.app_users SET " +
                            "username = COALESCE(?, username), " +
                            "first_name = COALESCE(?, first_name), " +
                            "last_name = COALESCE(?, last_name), " +
                            "email = COALESCE(?, email), " +
                            "notification_email = COALESCE(?, notification_email), " +
                            "job_title = COALESCE(?, job_title), " +
                            "phone = COALESCE(?, phone), " +
                            "role_id = COALESCE((SELECT role_id FROM p2_sandbox.roles WHERE code = ?), role_id), " +
                            "updated_at = NOW() " +
                            "WHERE user_id = ? AND deleted_at IS NULL RETURNING user_id, legacy_employee_id, " +
                            "(SELECT code FROM p2_sandbox.roles WHERE role_id = app_users.role_id) AS role_code, " +
                            "username, first_name, last_name, email, notification_email, job_title, phone, is_active")) {
                setNullableString(ps, 1, optionalString(request, "username"));
                setNullableString(ps, 2, optionalString(request, "firstName"));
                setNullableString(ps, 3, optionalString(request, "lastName"));
                setNullableString(ps, 4, optionalString(request, "email"));
                setNullableString(ps, 5, optionalString(request, "notificationEmail"));
                setNullableString(ps, 6, optionalString(request, "jobTitle"));
                setNullableString(ps, 7, optionalString(request, "phone"));
                setNullableString(ps, 8, optionalString(request, "role"));
                ps.setLong(9, targetUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "User not found");
                    }
                    AuthenticatedUser updated = readUser(rs);
                    audit(connection, actor.userId, "app_users", targetUserId.toString(), "ADMIN_USER_UPDATED",
                            before, updated.toJson(), ctx);
                    return updated.toJson();
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update user");
        }
    }

    public JSONObject updateUserStatus(Long targetUserId, String body, AuthenticatedUser actor, Context ctx) {
        JSONObject request = parseJson(body);
        if (!request.has("isActive")) {
            throw new AuthException(400, "isActive is required");
        }
        boolean isActive = request.getBoolean("isActive");
        try (Connection connection = requireConnection()) {
            JSONObject before = getUserById(connection, targetUserId).toJson();
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE p2_sandbox.app_users SET is_active = ?, updated_at = NOW() " +
                            "WHERE user_id = ? AND deleted_at IS NULL RETURNING user_id, legacy_employee_id, " +
                            "(SELECT code FROM p2_sandbox.roles WHERE role_id = app_users.role_id) AS role_code, " +
                            "username, first_name, last_name, email, notification_email, job_title, phone, is_active")) {
                ps.setBoolean(1, isActive);
                ps.setLong(2, targetUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AuthException(404, "User not found");
                    }
                    if (!isActive) {
                        revokeUserSessions(connection, targetUserId);
                    }
                    AuthenticatedUser updated = readUser(rs);
                    audit(connection, actor.userId, "app_users", targetUserId.toString(),
                            isActive ? "USER_ACTIVATED" : "USER_INACTIVATED",
                            before, updated.toJson(), ctx);
                    return updated.toJson();
                }
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(500, "Unable to update user status");
        }
    }

    public void auditAccessDenied(AuthenticatedUser user, String method, String path, String reason, Context ctx) {
        try (Connection connection = requireConnection()) {
            Long actorId = user == null ? null : user.userId;
            audit(connection, actorId, "http_request", method + " " + path, "ACCESS_DENIED",
                    null, jsonObject("reason", reason), ctx);
        } catch (Exception ignored) {
        }
    }

    public String extractToken(Context ctx) {
        String authorization = ctx.header("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String headerToken = ctx.header("X-Auth-Token");
        if (headerToken != null && !headerToken.trim().isEmpty()) {
            return headerToken.trim();
        }
        String body = ctx.body();
        if (body != null && body.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(body);
                if (json.has("token") && !json.isNull("token")) {
                    return json.optString("token", "").trim();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private UserCredentials findCredentialsByUsername(Connection connection, String username) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT u.user_id, u.legacy_employee_id, r.code AS role_code, u.username, u.password_hash, " +
                        "u.first_name, u.last_name, u.email, u.notification_email, u.job_title, u.phone, u.is_active " +
                        "FROM p2_sandbox.app_users u " +
                        "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                        "WHERE LOWER(u.username) = LOWER(?) AND u.deleted_at IS NULL")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                UserCredentials credentials = new UserCredentials();
                credentials.user = readUser(rs);
                credentials.passwordHash = rs.getString("password_hash");
                return credentials;
            }
        }
    }

    private AuthenticatedUser findUserByLookup(Connection connection, String lookup) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT u.user_id, u.legacy_employee_id, r.code AS role_code, u.username, " +
                        "u.first_name, u.last_name, u.email, u.notification_email, u.job_title, u.phone, u.is_active " +
                        "FROM p2_sandbox.app_users u " +
                        "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                        "WHERE u.deleted_at IS NULL AND u.is_active = TRUE AND " +
                        "(LOWER(u.username) = LOWER(?) OR LOWER(COALESCE(u.email, '')) = LOWER(?) " +
                        "OR LOWER(COALESCE(u.notification_email, '')) = LOWER(?))")) {
            ps.setString(1, lookup);
            ps.setString(2, lookup);
            ps.setString(3, lookup);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readUser(rs) : null;
            }
        }
    }

    private AuthenticatedUser getUserById(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT u.user_id, u.legacy_employee_id, r.code AS role_code, u.username, " +
                        "u.first_name, u.last_name, u.email, u.notification_email, u.job_title, u.phone, u.is_active " +
                        "FROM p2_sandbox.app_users u " +
                        "JOIN p2_sandbox.roles r ON r.role_id = u.role_id " +
                        "WHERE u.user_id = ? AND u.deleted_at IS NULL")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AuthException(404, "User not found");
                }
                return readUser(rs);
            }
        }
    }

    private AuthenticatedUser readUser(ResultSet rs) throws Exception {
        AuthenticatedUser user = new AuthenticatedUser();
        user.userId = rs.getLong("user_id");
        int legacyEmployeeId = rs.getInt("legacy_employee_id");
        user.legacyEmployeeId = rs.wasNull() ? null : legacyEmployeeId;
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

    private void insertSession(Connection connection, Long userId, String tokenHash, Instant expiresAt, Context ctx) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO p2_sandbox.auth_sessions " +
                        "(user_id, token_hash, expires_at, ip_address, user_agent) " +
                        "VALUES (?, ?, ?, NULLIF(?, '')::inet, ?)")) {
            ps.setLong(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.setString(4, ipAddress(ctx));
            ps.setString(5, userAgent(ctx));
            ps.executeUpdate();
        }
    }

    private void updateLastLogin(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.app_users SET last_login_at = NOW(), updated_at = NOW() WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    private void updatePasswordHash(Connection connection, Long userId, String passwordHash) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.app_users SET password_hash = ?, password_changed_at = NOW(), updated_at = NOW() WHERE user_id = ?")) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    private void revokeUserSessions(Connection connection, Long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE p2_sandbox.auth_sessions SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL")) {
            ps.setLong(1, userId);
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
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, actorUserId);
            }
            ps.setString(2, entityType);
            ps.setString(3, entityId);
            ps.setString(4, action);
            ps.setString(5, beforeData == null ? null : beforeData.toString());
            ps.setString(6, afterData == null ? null : afterData.toString());
            ps.setString(7, ipAddress(ctx));
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

    private JSONObject jsonObject(String key, Object value) {
        return new JSONObject().put(key, value);
    }

    private String optionalString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String requireString(JSONObject json, String key, int minLength, int maxLength) {
        String value = optionalString(json, key);
        if (value == null || value.length() < minLength || value.length() > maxLength) {
            throw new AuthException(400, key + " must be between " + minLength + " and " + maxLength + " characters");
        }
        return value;
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws Exception {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private void validateRole(String role) {
        if (role == null || role.isEmpty()) {
            return;
        }
        if (!"USER".equals(role) && !"TECH".equals(role) && !"ADMIN".equals(role)) {
            throw new AuthException(400, "Invalid role");
        }
    }

    private String userAgent(Context ctx) {
        String userAgent = ctx.header("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 250 ? userAgent.substring(0, 250) : userAgent;
    }

    private String ipAddress(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return "";
    }

    private static class UserCredentials {
        private AuthenticatedUser user;
        private String passwordHash;
    }
}
