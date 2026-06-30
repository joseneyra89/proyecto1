package com.businessName.ticketApi;

import com.businessName.security.AccessPolicy;
import com.businessName.security.AuthMiddleware;
import com.businessName.security.AuthService;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;


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

        HelpTicketController controller = new HelpTicketController();
        AuthService authService = new AuthService();
        AuthController authController = new AuthController(authService);
        HelpdeskFlowController helpdeskFlowController = new HelpdeskFlowController();
        AccessPolicy accessPolicy = new AccessPolicy();

        app.exception(UnauthorizedResponse.class, (e, ctx) -> {
            ctx.status(401).result(new JSONObject().put("message", e.getMessage()).toString());
        });
        app.exception(ForbiddenResponse.class, (e, ctx) -> {
            ctx.status(403).result(new JSONObject().put("message", e.getMessage()).toString());
        });

        app.before(ctx -> AuthMiddleware.enforce(ctx, authService, accessPolicy));

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

        app.post("/user/requests", controller.userCreateHelpRequest);

        app.put("/user/requests", controller.viewRequestStatus);

        app.post("/technician/", controller.viewOpenRequestsTech);

        app.put("/technician/", controller.viewOpenResolveTech);

        app.patch("/technician/", controller.fillCreateFormTech);

        app.post("/technician/requests", controller.createTicketTech);

        app.patch("/user/requests", controller.userUpdateHelpRequest);

        app.delete("/user/requests", controller.userCancelHelpRequest);

        // Legacy aliases kept while the frontend and Postman collections move from "client" to "user".
        app.post("/client/requests", controller.clientCreateHelpRequest);

        app.put("/client/requests", controller.viewRequestStatus);

        app.patch("/client/requests", controller.clientUpdateHelpRequest);

        app.delete("/client/requests", controller.clientCancelHelpRequest);

        app.patch("/technician/requests", controller.updateTicketTech);

        app.put("/technician/requests", controller.viewOpenTicketTech);

        app.delete("/technician/requests", controller.resolveTicketTech);

        app.post("/", controller.updatePersonalInfo);

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
