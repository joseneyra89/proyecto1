package com.businessName.ticketApi;

import com.businessName.security.AccessPolicy;
import com.businessName.security.AuthMiddleware;
import com.businessName.security.AuthService;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.businessName.ticketService.HelpdeskFlowService;


public class HelpTicketApi {
    /*
        javalin will be used to handle our http requests
     */

    public static Logger logger = LogManager.getLogger(HelpTicketApi.class);

    public static void main(String[] args) {
        AuthService authService = new AuthService();
        AuthController authController = new AuthController(authService);
        HelpdeskFlowController helpdeskFlowController = new HelpdeskFlowController();
        AccessPolicy accessPolicy = new AccessPolicy();

        Javalin app = Javalin.create(config -> {
            logger.info("Javalin starting...");
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            config.bundledPlugins.enableDevLogging();
            registerRoutes(config.routes, authService, authController, helpdeskFlowController, accessPolicy);
            logger.info("Javalin configured.");
        });

        ScheduledExecutorService autoCloseScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ticket-auto-close");
            thread.setDaemon(true);
            return thread;
        });
        HelpdeskFlowService workflowService = new HelpdeskFlowService();
        autoCloseScheduler.scheduleWithFixedDelay(() -> {
            try {
                int closed = workflowService.closeExpiredResolvedTickets();
                if (closed > 0) {
                    logger.info("Automatically closed {} resolved tickets", closed);
                }
            } catch (Exception e) {
                logger.warn("Automatic ticket closure skipped: {}", e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);

        app.start(getPort());

    }

    private static void registerRoutes(RoutesConfig routes,
                                       AuthService authService,
                                       AuthController authController,
                                       HelpdeskFlowController helpdeskFlowController,
                                       AccessPolicy accessPolicy) {
        Handler legacyGone = ctx -> ctx.status(410).result(new JSONObject()
                .put("message", "Legacy endpoint retired. Use /service-cases, /tickets, /me or /admin/users.")
                .toString());

        routes.exception(UnauthorizedResponse.class, (e, ctx) -> {
            ctx.status(401).result(new JSONObject().put("message", e.getMessage()).toString());
        });
        routes.exception(ForbiddenResponse.class, (e, ctx) -> {
            ctx.status(403).result(new JSONObject().put("message", e.getMessage()).toString());
        });

        routes.before(ctx -> AuthMiddleware.enforce(ctx, authService, accessPolicy));

        routes.get("/health", ctx -> ctx.status(200).result(new JSONObject()
                .put("status", "UP")
                .put("service", "ticket-tracking-api")
                .put("timestamp", Instant.now().toString())
                .toString()));

        routes.post("/login", authController.login);

        routes.post("/logout", authController.logout);

        routes.get("/me", authController.me);

        routes.patch("/me", authController.updateMe);

        routes.post("/password/forgot", authController.forgotPassword);

        routes.post("/password/reset", authController.resetPassword);

        routes.get("/admin/users", authController.listUsers);

        routes.post("/admin/users", authController.createUser);

        routes.patch("/admin/users/{userId}", authController.updateUser);

        routes.patch("/admin/users/{userId}/status", authController.updateUserStatus);

        routes.get("/sites", helpdeskFlowController.listSites);

        routes.post("/sites", helpdeskFlowController.createSite);

        routes.patch("/sites/{siteId}", helpdeskFlowController.updateSite);

        routes.patch("/sites/{siteId}/status", helpdeskFlowController.updateSiteStatus);

        routes.get("/sites/{siteId}/locations", helpdeskFlowController.listLocations);

        routes.post("/sites/{siteId}/locations", helpdeskFlowController.createLocation);

        routes.patch("/locations/{locationId}", helpdeskFlowController.updateLocation);

        routes.patch("/locations/{locationId}/status", helpdeskFlowController.updateLocationStatus);

        routes.get("/users/requesters", authController.listRequesters);

        routes.get("/users/technicians", helpdeskFlowController.listTechnicians);

        routes.get("/dashboard/technician", helpdeskFlowController.technicianDashboard);

        routes.get("/reports/requests", helpdeskFlowController.managementReport);

        routes.get("/reports/requests/excel", helpdeskFlowController.managementReportExcel);

        routes.post("/service-cases", helpdeskFlowController.createServiceCase);

        routes.get("/service-cases", helpdeskFlowController.listServiceCases);

        routes.get("/service-cases/{caseId}", helpdeskFlowController.getServiceCase);

        routes.get("/queues/service-cases", helpdeskFlowController.listQueues);

        routes.get("/tickets", helpdeskFlowController.listTickets);

        routes.get("/tickets/{ticketId}", helpdeskFlowController.getTicket);

        routes.get("/tickets/{ticketId}/audit", helpdeskFlowController.listTicketAudit);

        routes.post("/service-cases/{caseId}/tickets", helpdeskFlowController.createTicket);

        routes.patch("/tickets/{ticketId}", helpdeskFlowController.updateTicket);

        routes.post("/tickets/{ticketId}/assign", helpdeskFlowController.assignTicket);

        routes.post("/tickets/{ticketId}/resolve", helpdeskFlowController.resolveTicket);

        routes.post("/notifications/retry", helpdeskFlowController.retryNotifications);

        routes.post("/user/requests", legacyGone);

        routes.put("/user/requests", legacyGone);

        routes.post("/technician/", legacyGone);

        routes.put("/technician/", legacyGone);

        routes.patch("/technician/", legacyGone);

        routes.post("/technician/requests", legacyGone);

        routes.patch("/user/requests", legacyGone);

        routes.delete("/user/requests", legacyGone);

        routes.post("/client/requests", legacyGone);

        routes.put("/client/requests", legacyGone);

        routes.patch("/client/requests", legacyGone);

        routes.delete("/client/requests", legacyGone);

        routes.patch("/technician/requests", legacyGone);

        routes.put("/technician/requests", legacyGone);

        routes.delete("/technician/requests", legacyGone);

        routes.post("/", legacyGone);
    }

    private static int getPort() {
        String port = System.getenv("PORT");
        if (port == null || port.isEmpty()) {
            return 8081;
        }
        return Integer.parseInt(port);
    }
}
