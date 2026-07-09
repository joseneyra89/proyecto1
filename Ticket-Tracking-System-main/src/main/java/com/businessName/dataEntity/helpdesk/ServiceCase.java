package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.Priority;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.ServiceCaseType;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.WorkStatus;

import java.time.OffsetDateTime;

public class ServiceCase extends HelpdeskRecord {
    public Long caseId;
    public String caseNumber;
    public ServiceCaseType type;
    public String title;
    public String description;
    public Long requesterUserId;
    public Long affectedUserId;
    public Integer siteId;
    public Long locationId;
    public String organization;
    public String reportedBy;
    public String businessService;
    public WorkStatus status = WorkStatus.NEW;
    public Priority priority = Priority.MEDIUM;
    public Integer legacyTicketRequestId;
    public Long createdByUserId;
    public OffsetDateTime resolvedAt;
    public OffsetDateTime closedAt;
}
