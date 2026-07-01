package com.businessName.dataEntity.helpdesk;

public final class HelpdeskEnums {
    private HelpdeskEnums() {
    }

    public enum UserRole {
        USER,
        TECH,
        ADMIN
    }

    public enum LocationType {
        AULA,
        LAB,
        AREA
    }

    public enum ServiceCaseType {
        REQUEST,
        INCIDENT
    }

    public enum WorkStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        CLOSED,
        CANCELLED
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum TicketUpdateVisibility {
        PUBLIC,
        INTERNAL
    }

    public enum TicketUpdateType {
        COMMENT,
        STATUS_CHANGE,
        ASSIGNMENT,
        SLA,
        SYSTEM
    }

    public enum NotificationChannel {
        IN_APP,
        EMAIL
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        READ,
        FAILED
    }
}
