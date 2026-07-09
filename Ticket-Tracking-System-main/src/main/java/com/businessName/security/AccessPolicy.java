package com.businessName.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AccessPolicy {
    private final Map<String, String> routePermissions = new HashMap<>();
    private final Map<String, Set<String>> rolePermissions = new HashMap<>();
    private final Set<String> publicRoutes = new HashSet<>();

    public AccessPolicy() {
        addPublic("POST", "/login");
        addPublic("GET", "/health");
        addPublic("POST", "/password/forgot");
        addPublic("POST", "/password/reset");

        add("POST", "/logout", "AUTH_LOGOUT");
        add("GET", "/me", "PROFILE_READ");
        add("PATCH", "/me", "PROFILE_UPDATE");
        add("POST", "/", "PROFILE_UPDATE");

        add("POST", "/user/requests", "USER_REQUEST_CREATE");
        add("PUT", "/user/requests", "USER_REQUEST_READ");
        add("PATCH", "/user/requests", "USER_REQUEST_UPDATE");
        add("DELETE", "/user/requests", "USER_REQUEST_CANCEL");
        add("POST", "/client/requests", "USER_REQUEST_CREATE");
        add("PUT", "/client/requests", "USER_REQUEST_READ");
        add("PATCH", "/client/requests", "USER_REQUEST_UPDATE");
        add("DELETE", "/client/requests", "USER_REQUEST_CANCEL");

        add("POST", "/technician/", "TECH_REQUEST_POOL_READ");
        add("PUT", "/technician/", "TECH_REQUEST_POOL_READ");
        add("PATCH", "/technician/", "TICKET_CREATE");
        add("POST", "/technician/requests", "TICKET_CREATE");
        add("PUT", "/technician/requests", "TICKET_READ");
        add("PATCH", "/technician/requests", "TICKET_UPDATE");
        add("DELETE", "/technician/requests", "TICKET_RESOLVE");

        add("GET", "/admin/users", "ADMIN_USERS_READ");
        add("POST", "/admin/users", "ADMIN_USERS_CREATE");
        add("PATCH", "/admin/users/*", "ADMIN_USERS_UPDATE_PROFILE");
        add("PATCH", "/admin/users/*/status", "ADMIN_USERS_UPDATE_STATUS");

        add("GET", "/sites", "METADATA_READ");
        add("POST", "/sites", "CATALOG_MANAGE");
        add("PATCH", "/sites/*", "CATALOG_MANAGE");
        add("PATCH", "/sites/*/status", "CATALOG_MANAGE");
        add("GET", "/sites/*/locations", "METADATA_READ");
        add("POST", "/sites/*/locations", "CATALOG_MANAGE");
        add("PATCH", "/locations/*", "CATALOG_MANAGE");
        add("PATCH", "/locations/*/status", "CATALOG_MANAGE");
        add("GET", "/users/requesters", "USER_DIRECTORY_READ");
        add("GET", "/users/technicians", "TICKET_ASSIGN");
        add("GET", "/dashboard/technician", "DASHBOARD_TECH_READ");
        add("GET", "/reports/requests", "MANAGEMENT_REPORT_READ");
        add("GET", "/reports/requests/excel", "MANAGEMENT_REPORT_READ");

        add("POST", "/service-cases", "SERVICE_CASE_CREATE");
        add("GET", "/service-cases", "SERVICE_CASE_READ");
        add("GET", "/service-cases/*", "SERVICE_CASE_READ");
        add("GET", "/queues/service-cases", "QUEUE_READ");

        add("GET", "/tickets", "TICKET_READ");
        add("GET", "/tickets/*", "TICKET_READ");
        add("GET", "/tickets/*/audit", "TICKET_READ");
        add("POST", "/service-cases/*/tickets", "TICKET_CREATE");
        add("PATCH", "/tickets/*", "TICKET_UPDATE");
        add("POST", "/tickets/*/assign", "TICKET_ASSIGN");
        add("POST", "/tickets/*/resolve", "TICKET_RESOLVE");
        add("POST", "/notifications/retry", "NOTIFICATIONS_RETRY");

        grant("USER", "AUTH_LOGOUT", "PROFILE_READ", "PROFILE_UPDATE",
                "USER_REQUEST_CREATE", "USER_REQUEST_READ", "USER_REQUEST_UPDATE", "USER_REQUEST_CANCEL",
                "METADATA_READ", "SERVICE_CASE_CREATE", "SERVICE_CASE_READ", "TICKET_READ");
        grant("TECH", "AUTH_LOGOUT", "PROFILE_READ", "PROFILE_UPDATE",
                "METADATA_READ", "USER_DIRECTORY_READ", "SERVICE_CASE_READ", "TECH_REQUEST_POOL_READ", "QUEUE_READ",
                "DASHBOARD_TECH_READ", "TICKET_CREATE", "TICKET_READ", "TICKET_UPDATE", "TICKET_ASSIGN", "TICKET_RESOLVE");
        grant("ADMIN", "AUTH_LOGOUT", "PROFILE_READ", "PROFILE_UPDATE",
                "USER_REQUEST_READ", "USER_REQUEST_UPDATE", "USER_REQUEST_CANCEL",
                "METADATA_READ", "USER_DIRECTORY_READ", "SERVICE_CASE_READ",
                "TECH_REQUEST_POOL_READ", "QUEUE_READ", "TICKET_CREATE", "TICKET_READ", "TICKET_UPDATE",
                "TICKET_ASSIGN", "TICKET_RESOLVE",
                "DASHBOARD_TECH_READ", "NOTIFICATIONS_RETRY", "ADMIN_USERS_READ", "ADMIN_USERS_CREATE",
                "ADMIN_USERS_UPDATE_PROFILE", "ADMIN_USERS_UPDATE_STATUS", "CATALOG_MANAGE", "MANAGEMENT_REPORT_READ");
    }

    public boolean isPublic(String method, String path) {
        return publicRoutes.contains(key(method, normalize(path)));
    }

    public String requiredPermission(String method, String path) {
        String normalizedPath = normalize(path);
        String exact = routePermissions.get(key(method, normalizedPath));
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : routePermissions.entrySet()) {
            if (matches(entry.getKey(), method, normalizedPath)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean isAllowed(String roleCode, String requiredPermission) {
        if (roleCode == null || requiredPermission == null) {
            return false;
        }
        Set<String> permissions = rolePermissions.get(roleCode);
        return permissions != null && permissions.contains(requiredPermission);
    }

    private void addPublic(String method, String path) {
        publicRoutes.add(key(method, normalize(path)));
    }

    private void add(String method, String path, String permission) {
        routePermissions.put(key(method, normalize(path)), permission);
    }

    private void grant(String role, String... permissions) {
        rolePermissions.put(role, new HashSet<>(Arrays.asList(permissions)));
    }

    private String key(String method, String path) {
        return method.toUpperCase() + " " + path;
    }

    private boolean matches(String routeKey, String method, String requestPath) {
        String prefix = method.toUpperCase() + " ";
        if (!routeKey.startsWith(prefix)) {
            return false;
        }
        String policyPath = routeKey.substring(prefix.length());
        if (!policyPath.contains("*")) {
            return policyPath.equals(requestPath);
        }
        String regex = policyPath.replace("*", "[^/]+");
        return requestPath.matches(regex);
    }

    private String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if ("/technician/".equals(path) || "/".equals(path)) {
            return path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
