import com.businessName.ticketService.SlaEngine;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SlaEngineTests {

    @Test
    public void requestDefaultDueDateIsSeventyTwoHours() {
        Timestamp start = Timestamp.from(Instant.parse("2026-05-24T00:00:00Z"));
        Timestamp due = SlaEngine.calculateDueAt("REQUEST", start, null, "24x7", false);
        Assert.assertEquals(due.toInstant(), start.toInstant().plus(72, ChronoUnit.HOURS));
    }

    @Test
    public void incidentDefaultDueDateIsTwentyFourHours() {
        Timestamp start = Timestamp.from(Instant.parse("2026-05-24T00:00:00Z"));
        Timestamp due = SlaEngine.calculateDueAt("INCIDENT", start, null, "24x7", false);
        Assert.assertEquals(due.toInstant(), start.toInstant().plus(24, ChronoUnit.HOURS));
    }

    @Test
    public void warningStartsAtConfiguredPercent() {
        Instant start = Instant.parse("2026-05-24T00:00:00Z");
        Timestamp createdAt = Timestamp.from(start);
        Timestamp dueAt = Timestamp.from(start.plus(100, ChronoUnit.MINUTES));
        SlaEngine.SlaSnapshot snapshot = SlaEngine.evaluate(
                "REQUEST",
                createdAt,
                dueAt,
                null,
                "IN_PROGRESS",
                75.0,
                start.plus(75, ChronoUnit.MINUTES));
        Assert.assertEquals(snapshot.code, "WARNING");
        Assert.assertEquals(snapshot.text, "En advertencia");
        Assert.assertEquals(snapshot.icon, "[!]");
    }

    @Test
    public void openTicketIsBreachedAfterDueDate() {
        Instant start = Instant.parse("2026-05-24T00:00:00Z");
        SlaEngine.SlaSnapshot snapshot = SlaEngine.evaluate(
                "INCIDENT",
                Timestamp.from(start),
                Timestamp.from(start.plus(24, ChronoUnit.HOURS)),
                null,
                "IN_PROGRESS",
                80.0,
                start.plus(25, ChronoUnit.HOURS));
        Assert.assertEquals(snapshot.code, "BREACHED");
        Assert.assertTrue(snapshot.breached);
        Assert.assertEquals(snapshot.icon, "[X]");
    }

    @Test
    public void resolvedAfterDueDateIsMetLate() {
        Instant start = Instant.parse("2026-05-24T00:00:00Z");
        SlaEngine.SlaSnapshot snapshot = SlaEngine.evaluate(
                "REQUEST",
                Timestamp.from(start),
                Timestamp.from(start.plus(72, ChronoUnit.HOURS)),
                Timestamp.from(start.plus(73, ChronoUnit.HOURS)),
                "RESOLVED",
                80.0,
                start.plus(74, ChronoUnit.HOURS));
        Assert.assertEquals(snapshot.code, "MET_LATE");
        Assert.assertTrue(snapshot.breached);
    }
}
