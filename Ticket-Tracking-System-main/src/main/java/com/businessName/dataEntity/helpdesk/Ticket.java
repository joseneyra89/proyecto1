package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.Priority;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.WorkStatus;

import java.time.OffsetDateTime;

public class Ticket extends HelpdeskRecord {
    public Long ticketId;
    public String ticketNumber;
    public Long serviceCaseId;
    public Long assignedToUserId;
    public Long createdByUserId;
    public Integer slaPolicyId;
    public String categoryCode;
    public String summary;
    public String description;
    public String resolution;
    public WorkStatus status = WorkStatus.OPEN;
    public Priority priority = Priority.MEDIUM;
    public OffsetDateTime dueAt;
    public Integer legacyTicketId;
    public OffsetDateTime resolvedAt;
    public OffsetDateTime closedAt;
}
