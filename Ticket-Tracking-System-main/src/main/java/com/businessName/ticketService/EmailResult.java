package com.businessName.ticketService;

public class EmailResult {
    public final boolean success;
    public final String providerMessageId;
    public final String errorMessage;

    private EmailResult(boolean success, String providerMessageId, String errorMessage) {
        this.success = success;
        this.providerMessageId = providerMessageId;
        this.errorMessage = errorMessage;
    }

    public static EmailResult success(String providerMessageId) {
        return new EmailResult(true, providerMessageId, null);
    }

    public static EmailResult failure(String errorMessage) {
        return new EmailResult(false, null, errorMessage);
    }
}
