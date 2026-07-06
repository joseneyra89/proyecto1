package com.businessName.dataEntity.helpdesk;

public class Attachment extends HelpdeskRecord {
    public Long attachmentId;
    public Long serviceCaseId;
    public Long ticketId;
    public Long ticketUpdateId;
    public Long uploadedByUserId;
    public String fileName;
    public String contentType;
    public String storageKey;
    public Long byteSize;
    public String checksumSha256;
}
