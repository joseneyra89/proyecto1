package com.businessName.ticketService;

import org.json.JSONObject;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

public final class SlaEngine {
    public static final int REQUEST_MINUTES = 72 * 60;
    public static final int INCIDENT_MINUTES = 24 * 60;
    private static final double DEFAULT_WARNING_PERCENT = 80.0;

    private SlaEngine() {
    }

    public static Timestamp calculateDueAt(String caseType, Timestamp startAt, Integer resolutionMinutes,
                                           String calendarCode, boolean businessHoursOnly) {
        Timestamp start = startAt == null ? Timestamp.from(Instant.now()) : startAt;
        int minutes = resolutionMinutes == null || resolutionMinutes <= 0
                ? defaultResolutionMinutes(caseType)
                : resolutionMinutes;
        // Initial engine uses a 24x7 calendar. calendarCode/businessHoursOnly are kept for future calendar providers.
        return Timestamp.from(start.toInstant().plus(Duration.ofMinutes(minutes)));
    }

    public static SlaSnapshot evaluate(String caseType, Timestamp createdAt, Timestamp dueAt, Timestamp resolvedAt,
                                       String status, Double warningPercent) {
        return evaluate(caseType, createdAt, dueAt, resolvedAt, status, warningPercent, Instant.now());
    }

    public static SlaSnapshot evaluate(String caseType, Timestamp createdAt, Timestamp dueAt, Timestamp resolvedAt,
                                       String status, Double warningPercent, Instant now) {
        Timestamp start = createdAt == null ? Timestamp.from(now) : createdAt;
        Timestamp due = dueAt == null ? calculateDueAt(caseType, start, null, "24x7", false) : dueAt;
        double warning = clampWarningPercent(warningPercent);
        Instant startInstant = start.toInstant();
        Instant dueInstant = due.toInstant();
        Instant endInstant = resolvedAt == null ? now : resolvedAt.toInstant();
        long totalSeconds = Math.max(1, Duration.between(startInstant, dueInstant).getSeconds());
        long elapsedSeconds = Math.max(0, Duration.between(startInstant, endInstant).getSeconds());
        double elapsedPercent = Math.min(999.0, (elapsedSeconds * 100.0) / totalSeconds);
        long remainingMinutes = Duration.between(now, dueInstant).toMinutes();

        if (isClosedStatus(status)) {
            if (!endInstant.isAfter(dueInstant)) {
                return new SlaSnapshot("MET", "Cumplido", "[OK]", "sla-ok", due, elapsedPercent, remainingMinutes, false);
            }
            return new SlaSnapshot("MET_LATE", "Cumplido tarde", "[X]", "sla-breached", due, elapsedPercent, remainingMinutes, true);
        }
        if (now.isAfter(dueInstant)) {
            return new SlaSnapshot("BREACHED", "Vencido", "[X]", "sla-breached", due, elapsedPercent, remainingMinutes, true);
        }
        if (elapsedPercent >= warning) {
            return new SlaSnapshot("WARNING", "En advertencia", "[!]", "sla-warning", due, elapsedPercent, remainingMinutes, false);
        }
        return new SlaSnapshot("ON_TRACK", "En plazo", "[OK]", "sla-ok", due, elapsedPercent, remainingMinutes, false);
    }

    public static int defaultResolutionMinutes(String caseType) {
        if ("INCIDENT".equalsIgnoreCase(caseType)) {
            return intEnv("SLA_INCIDENT_HOURS", 24) * 60;
        }
        return intEnv("SLA_REQUEST_HOURS", 72) * 60;
    }

    public static double defaultWarningPercent() {
        return doubleEnv("SLA_WARNING_PERCENT", DEFAULT_WARNING_PERCENT);
    }

    private static double clampWarningPercent(Double warningPercent) {
        double warning = warningPercent == null ? defaultWarningPercent() : warningPercent;
        if (warning <= 0.0 || warning >= 100.0) {
            return DEFAULT_WARNING_PERCENT;
        }
        return warning;
    }

    private static boolean isClosedStatus(String status) {
        return "RESOLVED".equalsIgnoreCase(status)
                || "CLOSED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status);
    }

    private static int intEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double doubleEnv(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static class SlaSnapshot {
        public final String code;
        public final String text;
        public final String icon;
        public final String colorClass;
        public final Timestamp dueAt;
        public final double elapsedPercent;
        public final long remainingMinutes;
        public final boolean breached;

        private SlaSnapshot(String code, String text, String icon, String colorClass, Timestamp dueAt,
                            double elapsedPercent, long remainingMinutes, boolean breached) {
            this.code = code;
            this.text = text;
            this.icon = icon;
            this.colorClass = colorClass;
            this.dueAt = dueAt;
            this.elapsedPercent = elapsedPercent;
            this.remainingMinutes = remainingMinutes;
            this.breached = breached;
        }

        public JSONObject toJson() {
            return new JSONObject()
                    .put("code", code)
                    .put("text", text)
                    .put("icon", icon)
                    .put("colorClass", colorClass)
                    .put("dueAt", dueAt == null ? JSONObject.NULL : dueAt.toInstant().toString())
                    .put("elapsedPercent", Math.round(elapsedPercent * 10.0) / 10.0)
                    .put("remainingMinutes", remainingMinutes)
                    .put("breached", breached);
        }
    }
}
