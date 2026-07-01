package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.NotificationChannel;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.NotificationStatus;

import java.time.OffsetDateTime;

public class Notification extends HelpdeskRecord {
    public Long notificationId;
    public Long recipientUserId;
    public Long serviceCaseId;
    public Long ticketId;
    public String type;
    public NotificationChannel channel = NotificationChannel.IN_APP;
    public String subject;
    public String body;
    public NotificationStatus status = NotificationStatus.PENDING;
    public OffsetDateTime readAt;
    public OffsetDateTime sentAt;
}
