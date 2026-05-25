import com.businessName.ticketService.DashboardMetrics;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DashboardMetricsTests {

    @Test
    public void percentReturnsZeroWhenTotalIsZero() {
        Assert.assertEquals(DashboardMetrics.percent(3, 0), 0.0);
    }

    @Test
    public void percentRoundsToOneDecimal() {
        Assert.assertEquals(DashboardMetrics.percent(2, 3), 66.7);
    }

    @Test
    public void slaBucketsClassifyWithinAndOutside() {
        Assert.assertTrue(DashboardMetrics.isWithinSla("ON_TRACK"));
        Assert.assertTrue(DashboardMetrics.isWithinSla("WARNING"));
        Assert.assertTrue(DashboardMetrics.isWithinSla("MET"));
        Assert.assertTrue(DashboardMetrics.isOutsideSla("BREACHED"));
        Assert.assertTrue(DashboardMetrics.isOutsideSla("MET_LATE"));
    }

    @Test
    public void typeCountersProduceDashboardJson() {
        DashboardMetrics.TypeCounters counters = new DashboardMetrics.TypeCounters("REQUEST");
        counters.casesWithoutTicket = 2;
        counters.addTicket("OPEN", "ON_TRACK");
        counters.addTicket("IN_PROGRESS", "WARNING");
        counters.addTicket("RESOLVED", "MET_LATE");

        JSONObject json = DashboardMetrics.toJson(counters);

        Assert.assertEquals(json.getString("type"), "REQUEST");
        Assert.assertEquals(json.getLong("casesWithoutTicket"), 2L);
        Assert.assertEquals(json.getLong("ticketsTotal"), 3L);
        Assert.assertEquals(json.getLong("withinSla"), 2L);
        Assert.assertEquals(json.getLong("outsideSla"), 1L);
        Assert.assertEquals(json.getDouble("withinSlaPercent"), 66.7);
        Assert.assertEquals(json.getDouble("outsideSlaPercent"), 33.3);
    }
}
