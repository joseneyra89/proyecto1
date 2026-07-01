package com.businessName.security;

import org.json.JSONObject;

public class AuthenticatedUser {
    public Long userId;
    public Integer legacyEmployeeId;
    public String roleCode;
    public String username;
    public String firstName;
    public String lastName;
    public String email;
    public String notificationEmail;
    public String jobTitle;
    public String phone;
    public Boolean isActive;

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("legacyEmployeeId", legacyEmployeeId);
        json.put("role", roleCode);
        json.put("username", username);
        json.put("firstName", firstName);
        json.put("lastName", lastName);
        json.put("email", email == null ? JSONObject.NULL : email);
        json.put("notificationEmail", notificationEmail == null ? JSONObject.NULL : notificationEmail);
        json.put("jobTitle", jobTitle == null ? JSONObject.NULL : jobTitle);
        json.put("phone", phone == null ? JSONObject.NULL : phone);
        json.put("isActive", isActive);
        return json;
    }
}
