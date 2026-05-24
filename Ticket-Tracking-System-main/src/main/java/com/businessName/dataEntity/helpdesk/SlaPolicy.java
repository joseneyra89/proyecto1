package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.Priority;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.ServiceCaseType;

public class SlaPolicy extends HelpdeskRecord {
    public Integer slaPolicyId;
    public String code;
    public String name;
    public ServiceCaseType caseType;
    public Priority priority;
    public Integer responseMinutes;
    public Integer resolutionMinutes;
    public Boolean businessHoursOnly = true;
    public Boolean isDefault = false;
}
