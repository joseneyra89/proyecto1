import com.businessName.ticketService.TicketWorkflow;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

public class TicketWorkflowTests {
    @Test
    public void protectedFieldsExpireAfterTwoHours() {
        Instant createdAt = Instant.parse("2026-07-09T10:00:00Z");
        Assert.assertTrue(TicketWorkflow.protectedFieldsEditable(createdAt, Instant.parse("2026-07-09T11:59:59Z")));
        Assert.assertFalse(TicketWorkflow.protectedFieldsEditable(createdAt, Instant.parse("2026-07-09T12:00:00Z")));
    }

    @Test
    public void resolvedTicketLocksAfterThirtyMinutes() {
        Instant resolvedAt = Instant.parse("2026-07-09T10:00:00Z");
        Assert.assertFalse(TicketWorkflow.isLocked("RESOLVED", resolvedAt, Instant.parse("2026-07-09T10:29:59Z")));
        Assert.assertTrue(TicketWorkflow.isLocked("RESOLVED", resolvedAt, Instant.parse("2026-07-09T10:30:00Z")));
    }

    @Test
    public void closedTicketsAreAlwaysLocked() {
        Assert.assertTrue(TicketWorkflow.isLocked("CLOSED", null, Instant.now()));
    }

    @Test
    public void scheduledAndAssignedAreValidWorkflowStates() {
        Assert.assertTrue(TicketWorkflow.isWorkflowStatus("NEW"));
        Assert.assertTrue(TicketWorkflow.isWorkflowStatus("ASSIGNED"));
        Assert.assertTrue(TicketWorkflow.isWorkflowStatus("SCHEDULED"));
        Assert.assertFalse(TicketWorkflow.isWorkflowStatus("OPEN"));
    }
}
