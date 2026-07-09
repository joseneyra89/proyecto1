package com.businessName.ticketService;

import org.json.JSONObject;

public final class DashboardMetrics {
    private DashboardMetrics() {
    }

    public static boolean isWithinSla(String slaCode) {
        return "ON_TRACK".equals(slaCode) || "WARNING".equals(slaCode) || "MET".equals(slaCode);
    }

    public static boolean isOutsideSla(String slaCode) {
        return "BREACHED".equals(slaCode) || "MET_LATE".equals(slaCode);
    }

    public static double percent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((part * 1000.0) / total) / 10.0;
    }

    public static JSONObject toJson(TypeCounters counters) {
        return new JSONObject()
                .put("type", counters.type)
                .put("casesWithoutTicket", counters.casesWithoutTicket)
                .put("ticketsTotal", counters.ticketsTotal)
                .put("openTickets", counters.openTickets)
                .put("inProgressTickets", counters.inProgressTickets)
                .put("resolvedTickets", counters.resolvedTickets)
                .put("warningTickets", counters.warningTickets)
                .put("withinSla", counters.withinSla)
                .put("outsideSla", counters.outsideSla)
                .put("withinSlaPercent", percent(counters.withinSla, counters.ticketsTotal))
                .put("outsideSlaPercent", percent(counters.outsideSla, counters.ticketsTotal));
    }

    public static class TypeCounters {
        public final String type;
        public long casesWithoutTicket;
        public long ticketsTotal;
        public long openTickets;
        public long inProgressTickets;
        public long resolvedTickets;
        public long warningTickets;
        public long withinSla;
        public long outsideSla;

        public TypeCounters(String type) {
            this.type = type;
        }

        public void addTicket(String status, String slaCode) {
            ticketsTotal++;
            if ("NEW".equals(status) || "ASSIGNED".equals(status)) {
                openTickets++;
            } else if ("IN_PROGRESS".equals(status) || "SCHEDULED".equals(status)) {
                inProgressTickets++;
            } else if ("RESOLVED".equals(status) || "CLOSED".equals(status) || "CANCELLED".equals(status)) {
                resolvedTickets++;
            }
            if ("WARNING".equals(slaCode)) {
                warningTickets++;
            }
            if (isWithinSla(slaCode)) {
                withinSla++;
            } else if (isOutsideSla(slaCode)) {
                outsideSla++;
            }
        }
    }
}
