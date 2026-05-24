package com.businessName.ticketApi;

import com.businessName.security.AuthException;
import com.businessName.security.AuthService;
import com.businessName.security.AuthenticatedUser;
import io.javalin.http.Handler;
import org.json.JSONObject;

public class AuthController {
    private AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public Handler login = ctx -> {
        try {
            ctx.status(201).result(authService.login(ctx.body(), ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler logout = ctx -> {
        try {
            ctx.status(200).result(authService.logout(ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler me = ctx -> {
        AuthenticatedUser user = ctx.attribute("authUser");
        ctx.status(200).result(authService.me(user).toString());
    };

    public Handler updateMe = ctx -> {
        try {
            AuthenticatedUser user = ctx.attribute("authUser");
            ctx.status(200).result(authService.updateOwnProfile(user, ctx.body(), ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler forgotPassword = ctx -> {
        try {
            ctx.status(200).result(authService.requestPasswordReset(ctx.body(), ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler resetPassword = ctx -> {
        try {
            ctx.status(200).result(authService.resetPassword(ctx.body(), ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler listUsers = ctx -> {
        try {
            ctx.status(200).result(authService.listUsers().toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler updateUser = ctx -> {
        try {
            AuthenticatedUser actor = ctx.attribute("authUser");
            Long userId = Long.parseLong(ctx.pathParam("userId"));
            ctx.status(200).result(authService.updateUser(userId, ctx.body(), actor, ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler updateUserStatus = ctx -> {
        try {
            AuthenticatedUser actor = ctx.attribute("authUser");
            Long userId = Long.parseLong(ctx.pathParam("userId"));
            ctx.status(200).result(authService.updateUserStatus(userId, ctx.body(), actor, ctx).toString());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };
}
