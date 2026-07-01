package com.businessName.dataEntity.helpdesk;

import com.businessName.dataEntity.helpdesk.HelpdeskEnums.LocationType;

public class Location extends HelpdeskRecord {
    public Long locationId;
    public Integer siteId;
    public Long parentLocationId;
    public LocationType type;
    public String code;
    public String name;
    public String floor;
    public String description;
}
