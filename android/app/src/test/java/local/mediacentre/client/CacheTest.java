package local.mediacentre.client;
import local.mediacentre.client.data.JsonCache;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
public final class CacheTest {
    private static final class Storage implements JsonCache.Storage {
        String value;boolean fail;
        public String read(){return value;}
        public void write(String text)throws IOException{if(fail)throw new IOException("Injected interrupted write");value=text;}
        public void delete(){value=null;}
    }
    @Test public void committedSnapshotSurvivesReopenAndFailedWrite()throws Exception{
        Storage file=new Storage();String profile=JsonCache.profile("https://panel.invalid","private-token");
        JsonCache cache=new JsonCache(file,profile);
        String body="{\"data\":{\"price\":\"9876.543210987\",\"trade_at\":\"2026-09-27T07:30:00Z\",\"fetched_at\":\"2026-09-27T08:00:00Z\",\"stream_url\":\"https://example.invalid/original.mp3\"},\"meta\":{\"source\":\"synthetic\"}}";
        cache.put("/finance",body,"\"etag\"");
        file.fail=true;try{cache.put("/finance","changed",null);fail("Failed write accepted");}catch(IOException expected){}
        assertEquals(body,cache.get("/finance").body);
        assertEquals(body,new JsonCache(file,profile).get("/finance").body);
        assertFalse(file.value.contains("private-token"));
    }
    @Test public void differentProfileAndServerDiscardPreviousEntries()throws Exception{
        Storage file=new Storage();JsonCache cache=new JsonCache(file,"a");
        cache.acceptServer("10000000-0000-4000-8000-000000000100");cache.put("/finance","first",null);
        cache=new JsonCache(file,"a");cache.acceptServer("20000000-0000-4000-8000-000000000100");assertNull(cache.get("/finance"));
        cache.put("/weather","second",null);assertNull(new JsonCache(file,"b").get("/weather"));
    }
    @Test public void indexAndBodyAreBounded()throws Exception{
        Storage file=new Storage();JsonCache cache=new JsonCache(file,"profile");
        for(int n=0;n<80;n++)cache.put("/search/"+n,"{}",null);
        assertEquals(JsonCache.MAX_ENTRIES,new JSONObject(file.value).getJSONObject("entries").length());
        StringBuilder text=new StringBuilder();for(int n=0;n<JsonCache.MAX_BODY+1;n++)text.append('x');
        try{cache.put("/oversized",text.toString(),null);fail("Oversized body accepted");}catch(IOException expected){}
        assertTrue(file.value.getBytes("UTF-8").length<=JsonCache.MAX_TOTAL);
        file.value="broken";assertNull(new JsonCache(file,"profile").get("/finance"));
    }
    @Test public void mainSnapshotSurvivesSearchesAndTotalByteEviction()throws Exception{
        Storage file=new Storage();JsonCache cache=new JsonCache(file,"profile");
        cache.put("/api/v1/finance?scenario=database","original",null);
        StringBuilder payload=new StringBuilder();for(int n=0;n<200000;n++)payload.append('x');
        for(int n=0;n<20;n++)cache.put("/api/v1/radio/stations?q="+n,payload.toString(),null);
        assertEquals("original",cache.get("/api/v1/finance?scenario=database").body);
        assertTrue(file.value.getBytes("UTF-8").length<=JsonCache.MAX_TOTAL);
    }
    @Test public void radioWritesInvalidateEveryRadioViewButPreserveWeatherAndMoney()throws Exception{
        Storage file=new Storage();JsonCache cache=new JsonCache(file,"profile");
        cache.put("/api/v1/radio/stations?q=old","old stars",null);cache.put("/api/v1/radio/stations/one","old URL",null);cache.put("/api/v1/dashboard","old favorites",null);
        cache.put("/api/v1/weather","weather",null);cache.put("/api/v1/finance","exact price",null);
        file.fail=true;try{cache.invalidate("/api/v1/radio/","/api/v1/dashboard");fail("Interrupted invalidation accepted");}catch(IOException expected){}
        assertNotNull(cache.get("/api/v1/radio/stations/one"));file.fail=false;cache.invalidate("/api/v1/radio/","/api/v1/dashboard");
        cache=new JsonCache(file,"profile");assertNull(cache.get("/api/v1/radio/stations?q=old"));assertNull(cache.get("/api/v1/radio/stations/one"));assertNull(cache.get("/api/v1/dashboard"));
        assertEquals("weather",cache.get("/api/v1/weather").body);assertEquals("exact price",cache.get("/api/v1/finance").body);
    }
}
