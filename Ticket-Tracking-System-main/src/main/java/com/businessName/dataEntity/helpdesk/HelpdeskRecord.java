package com.businessName.dataEntity.helpdesk;

import java.time.OffsetDateTime;

public abstract class HelpdeskRecord {
    public Boolean isActive = true;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public OffsetDateTime deletedAt;

    public void deactivate(OffsetDateTime deletedAt) {
        this.isActive = false;
        this.deletedAt = deletedAt;
        this.updatedAt = deletedAt;
    }
}
