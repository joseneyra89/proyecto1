import com.businessName.common.PageRequest;
import com.businessName.security.AuthException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PaginationTests {

    @Test
    public void defaultsToFirstPageAndDefaultPageSize() {
        PageRequest request = PageRequest.from(null, null);

        Assert.assertEquals(request.page, 1);
        Assert.assertEquals(request.pageSize, 50);
        Assert.assertEquals(request.offset, 0);
    }

    @Test
    public void calculatesOffsetFromPageAndSize() {
        PageRequest request = PageRequest.from("3", "25");

        Assert.assertEquals(request.page, 3);
        Assert.assertEquals(request.pageSize, 25);
        Assert.assertEquals(request.offset, 50);
    }

    @Test(expectedExceptions = AuthException.class, expectedExceptionsMessageRegExp = "page must be a positive integer")
    public void rejectsInvalidPage() {
        PageRequest.from("0", "25");
    }

    @Test(expectedExceptions = AuthException.class, expectedExceptionsMessageRegExp = "pageSize must be between 1 and 100")
    public void rejectsOversizedPage() {
        PageRequest.from("1", "101");
    }

    @Test
    public void slicesFullListResponses() {
        JSONArray allItems = new JSONArray()
                .put(new JSONObject().put("id", 1))
                .put(new JSONObject().put("id", 2))
                .put(new JSONObject().put("id", 3));
        JSONObject response = PageRequest.from("2", "2").toResponseFromFullList(allItems);

        Assert.assertEquals(response.getJSONArray("data").length(), 1);
        Assert.assertEquals(response.getJSONArray("data").getJSONObject(0).getInt("id"), 3);
        Assert.assertEquals(response.getJSONObject("pagination").getLong("total"), 3L);
        Assert.assertTrue(response.getJSONObject("pagination").getBoolean("hasPrevious"));
        Assert.assertFalse(response.getJSONObject("pagination").getBoolean("hasNext"));
    }
}
