package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.UserRole;

public class AppUser extends HelpdeskRecord {
    public Long userId;
    public Integer legacyEmployeeId;
    public Short roleId;
    public UserRole roleCode;
    public String username;
    public String passwordHash;
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
}
