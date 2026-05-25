package com.businessName.ticketApi;

import com.businessName.security.AuthException;
import com.businessName.security.AuthenticatedUser;
import com.businessName.ticketService.HelpdeskFlowService;
import io.javalin.http.Handler;
import org.json.JSONObject;

public class HelpdeskFlowController {
    private HelpdeskFlowService service = new HelpdeskFlowService();

    public Handler listSites = ctx -> handle(ctx, () ->
            service.listSites().toString());

    public Handler listLocations = ctx -> handle(ctx, () ->
            service.listLocations(Integer.parseInt(ctx.pathParam("siteId"))).toString());

    public Handler listTechnicians = ctx -> handle(ctx, () ->
            service.listTechnicians().toString());

    public Handler technicianDashboard = ctx -> handle(ctx, () ->
            service.technicianDashboard(authUser(ctx), ctx.queryParam("dateFrom"), ctx.queryParam("dateTo")).toString());

    public Handler createServiceCase = ctx -> handle(ctx, () ->
            service.createServiceCase(authUser(ctx), ctx.body(), ctx).toString(), 201);

    public Handler listServiceCases = ctx -> handle(ctx, () ->
            service.listServiceCases(authUser(ctx), ctx.queryParam("type"), ctx.queryParam("status")).toString());

    public Handler getServiceCase = ctx -> handle(ctx, () ->
            service.getServiceCase(authUser(ctx), Long.parseLong(ctx.pathParam("caseId"))).toString());

    public Handler listQueues = ctx -> handle(ctx, () ->
            service.listQueues(ctx.queryParam("type")).toString());

    public Handler createTicket = ctx -> handle(ctx, () ->
            service.createTicket(authUser(ctx), Long.parseLong(ctx.pathParam("caseId")), ctx.body(), ctx).toString(), 201);

    public Handler listTickets = ctx -> handle(ctx, () ->
            service.listTickets(authUser(ctx), ctx.queryParam("status"), ctx.queryParam("type"), ctx.queryParam("q"),
                    ctx.queryParam("siteId"), ctx.queryParam("dateFrom"), ctx.queryParam("dateTo"), ctx.queryParam("due"),
                    ctx.queryParam("ticketNumber"), ctx.queryParam("user"), ctx.queryParam("technician"),
                    ctx.queryParam("email"), ctx.queryParam("title")).toString());

    public Handler getTicket = ctx -> handle(ctx, () ->
            service.getTicket(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId"))).toString());

    public Handler updateTicket = ctx -> handle(ctx, () ->
            service.updateTicket(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId")), ctx.body(), ctx).toString());

    public Handler assignTicket = ctx -> handle(ctx, () ->
            service.assignTicket(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId")), ctx.body(), ctx).toString());

    public Handler resolveTicket = ctx -> handle(ctx, () ->
            service.resolveTicket(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId")), ctx.body(), ctx).toString());

    public Handler retryNotifications = ctx -> handle(ctx, () ->
            service.retryDueNotifications().toString());

    private AuthenticatedUser authUser(io.javalin.http.Context ctx) {
        return ctx.attribute("authUser");
    }

    private void handle(io.javalin.http.Context ctx, Action action) throws Exception {
        handle(ctx, action, 200);
    }

    private void handle(io.javalin.http.Context ctx, Action action, int status) throws Exception {
        try {
            ctx.status(status).result(action.run());
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    }

    private interface Action {
        String run() throws Exception;
    }
}
