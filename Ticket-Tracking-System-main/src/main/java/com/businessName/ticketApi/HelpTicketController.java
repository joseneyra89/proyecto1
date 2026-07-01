package com.businessName.ticketApi;

import com.businessName.CustomerExceptions.LoginFailedException;
import com.businessName.CustomerExceptions.MalformedObjectException;
import com.businessName.CustomerExceptions.RecordNotFound;
import com.businessName.security.AuthenticatedUser;
import com.businessName.ticketDao.DataAccessImp;
import com.businessName.ticketDao.DataAccessInterface;
import com.businessName.ticketService.EmployeeInteractions;
import com.businessName.ticketService.TechnicianInteractions;
import com.businessName.ticketService.UserInteractions;
import io.javalin.http.Handler;
import io.javalin.http.ForbiddenResponse;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HelpTicketController {

    public DataAccessInterface daoObject = new DataAccessImp();
    public EmployeeInteractions eiObject = new EmployeeInteractions(daoObject);
    public UserInteractions uiObject = new UserInteractions(daoObject);
    public TechnicianInteractions tiObject = new TechnicianInteractions(daoObject);

    public static Logger logger = LogManager.getLogger(HelpTicketController.class);

    public HelpTicketController() {
    }

    public Handler employeeLogin = ctx -> {
        String body = ctx.body();
        logger.info("login attempt with: "+body);
        try {
            String response = "{\"token\":\"" + eiObject.doLogin(body) + "\"}";
            ctx.result(response);
            ctx.status(201);
            logger.info("login success!");
        } catch (LoginFailedException e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
            logger.info("login fail!");
        }


    };

    public Handler userCreateHelpRequest = ctx -> {
        String body = ctx.body();
        logger.info("user create request attempt with: "+body);
        try {
            String response = uiObject.createHelpRequest(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("user create request success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
            logger.info("user create request fail: "+e.getMessage());
        }
    };

    public Handler viewOpenRequestsTech = ctx -> {
        String body = ctx.body();
        logger.info("tech view request attempt with: "+body);
        try {
            String response = tiObject.viewOpenRequests(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("tech view request success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
            logger.info("tech view request fail: "+e.getMessage());
        }
    };

    public Handler viewOpenResolveTech = ctx -> {
        String body = ctx.body();
        try {
            String response = tiObject.viewOpenRequests(body);
            ctx.result(response);
            ctx.status(201);
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
        }
    };

    public Handler createTicketTech = ctx -> {
        String body = ctx.body();
        logger.info("tech create ticket attempt with: "+body);
        try {
            String response = tiObject.createTicket(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("tech create ticket success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
            logger.info("tech create ticket fail: "+e.getMessage());
        } catch (Exception e) {
            ctx.result(new JSONObject()
                    .put("message", "Error creating ticket: " + e.getMessage())
                    .toString());
            ctx.status(500);
            logger.error("unexpected tech create ticket fail", e);
        }
    };

    public Handler fillCreateFormTech = ctx -> {
        String body = ctx.body();
        logger.info("filling create ticket form: "+body);
        try {
            String response = tiObject.fillCategory(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("fill form success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
            logger.info("fill form fail: "+e.getMessage());
        }
    };

    public Handler viewRequestStatus = ctx -> {
        String body = ctx.body();
        logger.info("user view request attempt with: "+body);
        try {
            String response = uiObject.viewHelpRequest(body);
            ctx.result(response);
            ctx.status(201);
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
        }

    };

    public Handler userUpdateHelpRequest = ctx -> {
        String body = ctx.body();
        logger.info("user update request attempt with: "+body);
        try {
            String response = uiObject.updateHelpRequest(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("user update request success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
            logger.info("user update request fail: "+e.getMessage());
        }


    };

    public Handler userCancelHelpRequest = ctx -> {
        String body = ctx.body();
        logger.info("user cancel request attempt with: "+body);
        try {
            String response = uiObject.cancelHelpRequest(body);
            ctx.result("{\"message\":\"" + response + "\"}");
            ctx.status(201);
            logger.info("user cancel request success!");
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
            logger.info("user cancel request fail: "+e.getMessage());
        }

    };

    @Deprecated
    public Handler clientCreateHelpRequest = userCreateHelpRequest;

    @Deprecated
    public Handler clientUpdateHelpRequest = userUpdateHelpRequest;

    @Deprecated
    public Handler clientCancelHelpRequest = userCancelHelpRequest;

        public Handler updateTicketTech = ctx -> {
            String body = ctx.body();
            logger.info("tech update ticket attempt with: "+body);
            try {
                String response = tiObject.updateTicket(body);
                ctx.result(response);
                ctx.status(201);
                logger.info("tech update ticket success!");
            } catch (RecordNotFound e) {
                ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
                ctx.status(400);
                logger.info("tech update ticket fail: "+e.getMessage());
            }


        };

    public Handler viewOpenTicketTech = ctx -> {
        String body = ctx.body();
        logger.info("tech view ticket attempt with: "+body);
        try {
            String response = tiObject.viewOpenTicket(body);
            ctx.result(response);
            ctx.status(201);
            logger.info("tech view ticket success!");
        } catch (RecordNotFound e) {
            ctx.result("{\"message\":\""+e.getMessage()+"\"}");
            ctx.status(400);
            logger.info("tech view ticket fail: "+e.getMessage());
        }

    };




    public Handler resolveTicketTech = ctx -> {
        String body = ctx.body();
        try {
            String response = tiObject.resolveTicket(body);
            ctx.result(response);
            ctx.status(201);
        } catch (MalformedObjectException | RecordNotFound e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
        }


    };

    public Handler updatePersonalInfo = ctx -> {
        String body = ctx.body();
        try {
            AuthenticatedUser authUser = ctx.attribute("authUser");
            JSONObject request = new JSONObject(body);
            int targetEmployeeId = request.optInt("employees_id", -1);
            if (authUser == null || authUser.legacyEmployeeId == null) {
                throw new ForbiddenResponse("Forbidden");
            }
            if (!"ADMIN".equals(authUser.roleCode) && targetEmployeeId != authUser.legacyEmployeeId) {
                throw new ForbiddenResponse("Forbidden");
            }
            String response = eiObject.updatePersonalInfo(body);
            ctx.result(response);
            ctx.status(201);
        } catch (MalformedObjectException e) {
            ctx.result("{\"message\":\"" + e.getMessage() + "\"}");
            ctx.status(400);
        }


    };
}
