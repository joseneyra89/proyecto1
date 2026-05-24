package com.businessName.ticketService;

import com.businessName.ticketDao.DataAccessInterface;

@Deprecated
public class ClientInteractions extends UserInteractions {
    public ClientInteractions(DataAccessInterface daoObject) {
        super(daoObject);
    }
}
