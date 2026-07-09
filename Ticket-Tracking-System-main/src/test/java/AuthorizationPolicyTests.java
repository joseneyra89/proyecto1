import com.businessName.security.AccessPolicy;
import org.testng.Assert;
import org.testng.annotations.Test;

public class AuthorizationPolicyTests {

    @Test
    public void loginIsPublic() {
        AccessPolicy policy = new AccessPolicy();
        Assert.assertTrue(policy.isPublic("POST", "/login"));
    }

    @Test
    public void healthIsPublic() {
        AccessPolicy policy = new AccessPolicy();
        Assert.assertTrue(policy.isPublic("GET", "/health"));
    }

    @Test
    public void unknownRoutesAreDeniedByDefault() {
        AccessPolicy policy = new AccessPolicy();
        Assert.assertNull(policy.requiredPermission("GET", "/unregistered/admin/export"));
    }

    @Test
    public void userCannotAccessAdminUsers() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("GET", "/admin/users");
        Assert.assertEquals(permission, "ADMIN_USERS_READ");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertFalse(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void userCannotAccessTechnicianTicketUpdate() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("PATCH", "/technician/requests");
        Assert.assertEquals(permission, "TICKET_UPDATE");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void userRequestsAreLimitedToUserAndAdminRoles() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("POST", "/user/requests");
        Assert.assertEquals(permission, "USER_REQUEST_CREATE");
        Assert.assertTrue(policy.isAllowed("USER", permission));
        Assert.assertFalse(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void adminStatusRouteRequiresStatusPermission() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("PATCH", "/admin/users/2/status");
        Assert.assertEquals(permission, "ADMIN_USERS_UPDATE_STATUS");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertFalse(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void adminUserCreationIsAdminOnly() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("POST", "/admin/users");
        Assert.assertEquals(permission, "ADMIN_USERS_CREATE");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertFalse(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void catalogManagementIsAdminOnly() {
        AccessPolicy policy = new AccessPolicy();
        String sitePermission = policy.requiredPermission("POST", "/sites");
        String locationPermission = policy.requiredPermission("POST", "/sites/3/locations");
        Assert.assertEquals(sitePermission, "CATALOG_MANAGE");
        Assert.assertEquals(locationPermission, "CATALOG_MANAGE");
        Assert.assertFalse(policy.isAllowed("USER", sitePermission));
        Assert.assertFalse(policy.isAllowed("TECH", sitePermission));
        Assert.assertTrue(policy.isAllowed("ADMIN", sitePermission));
    }

    @Test
    public void serviceCaseCreationIsAllowedForPortalAndOperators() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("POST", "/service-cases");
        Assert.assertEquals(permission, "SERVICE_CASE_CREATE");
        Assert.assertTrue(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void requesterDirectoryIsOperatorOnly() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("GET", "/users/requesters");
        Assert.assertEquals(permission, "USER_DIRECTORY_READ");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void queuesAreDeniedToRegularUsers() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("GET", "/queues/service-cases");
        Assert.assertEquals(permission, "QUEUE_READ");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void ticketAssignmentRequiresTicketAssignPermission() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("POST", "/tickets/15/assign");
        Assert.assertEquals(permission, "TICKET_ASSIGN");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void usersCanReadOnlyTheirTicketsThroughServiceLayerScope() {
        AccessPolicy policy = new AccessPolicy();
        String readPermission = policy.requiredPermission("GET", "/tickets/3");
        String updatePermission = policy.requiredPermission("PATCH", "/tickets/3");
        Assert.assertEquals(readPermission, "TICKET_READ");
        Assert.assertEquals(updatePermission, "TICKET_UPDATE");
        Assert.assertTrue(policy.isAllowed("USER", readPermission));
        Assert.assertFalse(policy.isAllowed("USER", updatePermission));
    }

    @Test
    public void retryNotificationsIsAdminOnly() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("POST", "/notifications/retry");
        Assert.assertEquals(permission, "NOTIFICATIONS_RETRY");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertFalse(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }

    @Test
    public void technicianDashboardIsTechAndAdminOnly() {
        AccessPolicy policy = new AccessPolicy();
        String permission = policy.requiredPermission("GET", "/dashboard/technician");
        Assert.assertEquals(permission, "DASHBOARD_TECH_READ");
        Assert.assertFalse(policy.isAllowed("USER", permission));
        Assert.assertTrue(policy.isAllowed("TECH", permission));
        Assert.assertTrue(policy.isAllowed("ADMIN", permission));
    }
}
