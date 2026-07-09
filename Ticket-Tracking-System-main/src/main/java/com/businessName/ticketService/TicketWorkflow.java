package com.businessName.ticketService;

import java.time.Duration;
import java.time.Instant;

public final class TicketWorkflow {
    public static final Duration PROTECTED_FIELD_WINDOW = Duration.ofHours(2);
    public static final Duration RESOLUTION_GRACE_PERIOD = Duration.ofMinutes(30);

    private TicketWorkflow() {
    }

    public static boolean isWorkflowStatus(String status) {
        return "NEW".equals(status)
                || "ASSIGNED".equals(status)
                || "IN_PROGRESS".equals(status)
                || "SCHEDULED".equals(status)
                || "RESOLVED".equals(status)
                || "CLOSED".equals(status)
                || "CANCELLED".equals(status);
    }

    public static boolean protectedFieldsEditable(Instant createdAt, Instant now) {
        return createdAt != null && now.isBefore(createdAt.plus(PROTECTED_FIELD_WINDOW));
    }

    public static boolean resolutionGracePeriodActive(Instant resolvedAt, Instant now) {
        return resolvedAt != null && now.isBefore(resolvedAt.plus(RESOLUTION_GRACE_PERIOD));
    }

    public static boolean isLocked(String status, Instant resolvedAt, Instant now) {
        if ("CLOSED".equals(status) || "CANCELLED".equals(status)) {
            return true;
        }
        return "RESOLVED".equals(status) && !resolutionGracePeriodActive(resolvedAt, now);
    }
}

