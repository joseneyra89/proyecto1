package com.businessName.security;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;

public final class AuthMiddleware {
    private AuthMiddleware() {
    }

    public static void enforce(Context ctx, AuthService authService, AccessPolicy policy) {
        String method = ctx.method().toString().toUpperCase();
        String path = ctx.path();

        if ("OPTIONS".equals(method) || policy.isPublic(method, path)) {
            return;
        }

        AuthenticatedUser user = authService.authenticate(ctx);
        if (user == null) {
            authService.auditAccessDenied(null, method, path, "AUTHENTICATION_REQUIRED", ctx);
            throw new UnauthorizedResponse("Authentication required");
        }

        ctx.attribute("authUser", user);
        String permission = policy.requiredPermission(method, path);
        if (permission == null || !policy.isAllowed(user.roleCode, permission)) {
            authService.auditAccessDenied(user, method, path,
                    permission == null ? "NO_POLICY_MATCH" : "MISSING_PERMISSION:" + permission,
                    ctx);
            throw new ForbiddenResponse("Forbidden");
        }
    }
}
