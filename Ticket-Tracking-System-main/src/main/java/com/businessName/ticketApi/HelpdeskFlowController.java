package com.businessName.ticketApi;

import com.businessName.common.PageRequest;
import com.businessName.security.AuthException;
import com.businessName.security.AuthenticatedUser;
import com.businessName.ticketService.HelpdeskFlowService;
import com.businessName.ticketService.ManagementReportService;
import io.javalin.http.Handler;
import org.json.JSONObject;

public class HelpdeskFlowController {
    private HelpdeskFlowService service = new HelpdeskFlowService();
    private ManagementReportService reportService = new ManagementReportService();

    public Handler listSites = ctx -> handle(ctx, () ->
            service.listSites().toString());

    public Handler listLocations = ctx -> handle(ctx, () ->
            service.listLocations(Integer.parseInt(ctx.pathParam("siteId"))).toString());

    public Handler createSite = ctx -> handle(ctx, () ->
            service.createSite(authUser(ctx), ctx.body(), ctx).toString(), 201);

    public Handler updateSite = ctx -> handle(ctx, () ->
            service.updateSite(authUser(ctx), Integer.parseInt(ctx.pathParam("siteId")), ctx.body(), ctx).toString());

    public Handler updateSiteStatus = ctx -> handle(ctx, () ->
            service.updateSiteStatus(authUser(ctx), Integer.parseInt(ctx.pathParam("siteId")), ctx.body(), ctx).toString());

    public Handler createLocation = ctx -> handle(ctx, () ->
            service.createLocation(authUser(ctx), Integer.parseInt(ctx.pathParam("siteId")), ctx.body(), ctx).toString(), 201);

    public Handler updateLocation = ctx -> handle(ctx, () ->
            service.updateLocation(authUser(ctx), Long.parseLong(ctx.pathParam("locationId")), ctx.body(), ctx).toString());

    public Handler updateLocationStatus = ctx -> handle(ctx, () ->
            service.updateLocationStatus(authUser(ctx), Long.parseLong(ctx.pathParam("locationId")), ctx.body(), ctx).toString());

    public Handler listTechnicians = ctx -> handle(ctx, () ->
            service.listTechnicians().toString());

    public Handler technicianDashboard = ctx -> handle(ctx, () ->
            service.technicianDashboard(authUser(ctx), ctx.queryParam("dateFrom"), ctx.queryParam("dateTo")).toString());

    public Handler managementReport = ctx -> handle(ctx, () ->
            reportService.build(ctx.queryParam("dateFrom"), ctx.queryParam("dateTo"), ctx.queryParam("type")).toString());

    public Handler managementReportExcel = ctx -> {
        try {
            JSONObject report = reportService.build(ctx.queryParam("dateFrom"), ctx.queryParam("dateTo"), ctx.queryParam("type"));
            ctx.contentType("application/vnd.ms-excel; charset=UTF-8");
            ctx.header("Content-Disposition", "attachment; filename=\"informe-gestion-tickets.xls\"");
            ctx.status(200).result(reportService.toExcelXml(report));
        } catch (AuthException e) {
            ctx.status(e.getStatusCode()).result(new JSONObject().put("message", e.getMessage()).toString());
        }
    };

    public Handler createServiceCase = ctx -> handle(ctx, () ->
            service.createServiceCase(authUser(ctx), ctx.body(), ctx).toString(), 201);

    public Handler listServiceCases = ctx -> handle(ctx, () ->
            service.listServiceCases(authUser(ctx), ctx.queryParam("type"), ctx.queryParam("status"),
                    PageRequest.from(ctx.queryParam("page"), ctx.queryParam("pageSize"))).toString());

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
                    ctx.queryParam("email"), ctx.queryParam("title"),
                    PageRequest.from(ctx.queryParam("page"), ctx.queryParam("pageSize"))).toString());

    public Handler getTicket = ctx -> handle(ctx, () ->
            service.getTicket(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId"))).toString());

    public Handler listTicketAudit = ctx -> handle(ctx, () ->
            service.listTicketAudit(authUser(ctx), Long.parseLong(ctx.pathParam("ticketId"))).toString());

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
