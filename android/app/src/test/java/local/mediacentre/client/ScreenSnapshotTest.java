package local.mediacentre.client;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import local.mediacentre.client.presentation.ScreenSnapshot;

public final class ScreenSnapshotTest {
    private JSONObject envelope()throws Exception {
        return new JSONObject("{\"data\":{\"items\":[{\"symbol\":\"SBER\",\"enabled\":true,\"price\":\"321.123456789\"},{\"symbol\":\"GAZP\"}]},\"meta\":{\"freshness\":\"fresh\",\"fetched_at\":\"2026-09-27T12:00:00Z\",\"valid_until\":null,\"test_data\":false,\"scenario\":\"database\",\"generated_at\":\"2026-09-27T12:01:00Z\"}}");
    }
    @Test public void requestTimeDoesNotRedrawButConfigurationAndChronologyDo()throws Exception {
        JSONObject e=envelope();String original=ScreenSnapshot.of(e);
        e.getJSONObject("meta").put("generated_at","2026-09-27T12:02:00Z");assertEquals(original,ScreenSnapshot.of(e));
        e.getJSONObject("data").put("server_time","2026-09-27T12:03:00Z");assertEquals(original,ScreenSnapshot.of(e));assertTrue(e.getJSONObject("data").has("server_time"));
        e.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("enabled",false);assertNotEquals(original,ScreenSnapshot.of(e));
        e=envelope();e.getJSONObject("meta").put("fetched_at","2026-09-27T12:02:00Z");assertNotEquals(original,ScreenSnapshot.of(e));
        e=envelope();JSONArray a=e.getJSONObject("data").getJSONArray("items");JSONObject first=a.getJSONObject(0);a.put(0,a.getJSONObject(1));a.put(1,first);assertNotEquals(original,ScreenSnapshot.of(e));
    }
    @Test public void OfflineAndExpiryRemainVisibleWithoutNewBody()throws Exception {
        JSONObject e=envelope();String original=ScreenSnapshot.of(e);
        e.put("_client_cache",new JSONObject().put("offline",true));assertNotEquals(original,ScreenSnapshot.of(e));
        e=envelope();e.getJSONObject("meta").put("valid_until","2000-01-01T00:00:00Z");assertNotEquals(original,ScreenSnapshot.of(e));assertTrue(ScreenSnapshot.of(e).contains("\"expired\":true"));
        e=envelope();e.getJSONObject("data").put("stream_url","http://example.invalid/new");assertNotEquals(original,ScreenSnapshot.of(e));
    }
}
