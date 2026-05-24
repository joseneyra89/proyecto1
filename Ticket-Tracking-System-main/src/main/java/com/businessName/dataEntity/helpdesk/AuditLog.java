package com.businessName.dataEntity.helpdesk;

import java.time.OffsetDateTime;

public class AuditLog {
    public Long auditLogId;
    public Long actorUserId;
    public String entityType;
    public String entityId;
    public String action;
    public String beforeDataJson;
    public String afterDataJson;
    public String ipAddress;
    public String userAgent;
    public OffsetDateTime createdAt;
}
