package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.UserRole;

public class Role extends HelpdeskRecord {
    public Short roleId;
    public UserRole code;
    public String name;
    public String description;
}
