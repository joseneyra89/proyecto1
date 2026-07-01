package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.TicketUpdateType;
import com.businessName.dataEntity.helpdesk.HelpdeskEnums.TicketUpdateVisibility;

public class TicketUpdate extends HelpdeskRecord {
    public Long ticketUpdateId;
    public Long ticketId;
    public Long authorUserId;
    public TicketUpdateVisibility visibility = TicketUpdateVisibility.PUBLIC;
    public TicketUpdateType updateType = TicketUpdateType.COMMENT;
    public String body;
    public String previousStatus;
    public String newStatus;
}
