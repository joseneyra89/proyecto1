package com.businessName.ticketApi;

import com.businessName.security.AccessPolicy;
import com.businessName.security.AuthMiddleware;
import com.businessName.security.AuthService;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.time.Instant;


public class HelpTicketApi {
    /*
        javalin will be used to handle our http requests
     */

    public static Logger logger = LogManager.getLogger(HelpTicketApi.class);

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            logger.info("Javalin starting...");
            config.enableCorsForAllOrigins();
            config.enableDevLogging();
            logger.info("Javalin started...");
        });

        AuthService authService = new AuthService();
        AuthController authController = new AuthController(authService);
        HelpdeskFlowController helpdeskFlowController = new HelpdeskFlowController();
        AccessPolicy accessPolicy = new AccessPolicy();
        Handler legacyGone = ctx -> ctx.status(410).result(new JSONObject()
                .put("message", "Legacy endpoint retired. Use /service-cases, /tickets, /me or /admin/users.")
                .toString());

        app.exception(UnauthorizedResponse.class, (e, ctx) -> {
            ctx.status(401).result(new JSONObject().put("message", e.getMessage()).toString());
        });
        app.exception(ForbiddenResponse.class, (e, ctx) -> {
            ctx.status(403).result(new JSONObject().put("message", e.getMessage()).toString());
        });

        app.before(ctx -> AuthMiddleware.enforce(ctx, authService, accessPolicy));

        app.get("/health", ctx -> ctx.status(200).result(new JSONObject()
                .put("status", "UP")
                .put("service", "ticket-tracking-api")
                .put("timestamp", Instant.now().toString())
                .toString()));

        app.post("/login", authController.login);

        app.post("/logout", authController.logout);

        app.get("/me", authController.me);

        app.patch("/me", authController.updateMe);

        app.post("/password/forgot", authController.forgotPassword);

        app.post("/password/reset", authController.resetPassword);

        app.get("/admin/users", authController.listUsers);

        app.post("/admin/users", authController.createUser);

        app.patch("/admin/users/{userId}", authController.updateUser);

        app.patch("/admin/users/{userId}/status", authController.updateUserStatus);

        app.get("/sites", helpdeskFlowController.listSites);

        app.post("/sites", helpdeskFlowController.createSite);

        app.patch("/sites/{siteId}", helpdeskFlowController.updateSite);

        app.patch("/sites/{siteId}/status", helpdeskFlowController.updateSiteStatus);

        app.get("/sites/{siteId}/locations", helpdeskFlowController.listLocations);

        app.post("/sites/{siteId}/locations", helpdeskFlowController.createLocation);

        app.patch("/locations/{locationId}", helpdeskFlowController.updateLocation);

        app.patch("/locations/{locationId}/status", helpdeskFlowController.updateLocationStatus);

        app.get("/users/technicians", helpdeskFlowController.listTechnicians);

        app.get("/dashboard/technician", helpdeskFlowController.technicianDashboard);

        app.post("/service-cases", helpdeskFlowController.createServiceCase);

        app.get("/service-cases", helpdeskFlowController.listServiceCases);

        app.get("/service-cases/{caseId}", helpdeskFlowController.getServiceCase);

        app.get("/queues/service-cases", helpdeskFlowController.listQueues);

        app.get("/tickets", helpdeskFlowController.listTickets);

        app.get("/tickets/{ticketId}", helpdeskFlowController.getTicket);

        app.get("/tickets/{ticketId}/audit", helpdeskFlowController.listTicketAudit);

        app.post("/service-cases/{caseId}/tickets", helpdeskFlowController.createTicket);

        app.patch("/tickets/{ticketId}", helpdeskFlowController.updateTicket);

        app.post("/tickets/{ticketId}/assign", helpdeskFlowController.assignTicket);

        app.post("/tickets/{ticketId}/resolve", helpdeskFlowController.resolveTicket);

        app.post("/notifications/retry", helpdeskFlowController.retryNotifications);

        app.post("/user/requests", legacyGone);

        app.put("/user/requests", legacyGone);

        app.post("/technician/", legacyGone);

        app.put("/technician/", legacyGone);

        app.patch("/technician/", legacyGone);

        app.post("/technician/requests", legacyGone);

        app.patch("/user/requests", legacyGone);

        app.delete("/user/requests", legacyGone);

        app.post("/client/requests", legacyGone);

        app.put("/client/requests", legacyGone);

        app.patch("/client/requests", legacyGone);

        app.delete("/client/requests", legacyGone);

        app.patch("/technician/requests", legacyGone);

        app.put("/technician/requests", legacyGone);

        app.delete("/technician/requests", legacyGone);

        app.post("/", legacyGone);

        app.start(getPort());

    }

    private static int getPort() {
        String port = System.getenv("PORT");
        if (port == null || port.isEmpty()) {
            return 8081;
        }
        return Integer.parseInt(port);
    }
}
