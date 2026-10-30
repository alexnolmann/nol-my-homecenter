package local.mediacentre.client;
import local.mediacentre.client.data.*;
import local.mediacentre.client.presentation.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import javax.net.ssl.SSLHandshakeException;

public final class RepositoryCacheTest {
    private static final String BOOT="/api/v1/bootstrap?scenario=success",FIN="/api/v1/finance?scenario=success",WEATHER="/api/v1/weather?scenario=success&days=7",DASH="/api/v1/dashboard?scenario=success";
    private static final class Storage implements JsonCache.Storage {
        String value;int writes;boolean fail;
        public String read(){return value;}
        public void write(String next)throws IOException{if(fail)throw new IOException("Injected disk failure");value=next;writes++;}
        public void delete(){value=null;}
    }
    private static final class Guard implements PanelRepository.ConnectionGuard {
        volatile boolean forgotten;
        public boolean current(){return !forgotten;}
        public void forget(){forgotten=true;}
    }
    private interface Fetch {ApiClient.Response run(String path,String etag)throws Exception;}
    private static final class Transport implements PanelTransport {
        Fetch next;String lastTag;
        public ApiClient.Response fetch(String path,String tag)throws Exception{lastTag=tag;return next.run(path,tag);}
        public JSONObject favorite(String id,boolean enabled,String scenario){throw new AssertionError("Unexpected write");}
        public JSONObject preferences(String path,JSONObject body,String tag){throw new AssertionError("Unexpected write");}
        public JSONObject heartbeat(JSONObject body,String scenario){throw new AssertionError("Unexpected heartbeat");}
        public void refreshWeather(){throw new AssertionError("Unexpected weather refresh");}
    }
    private JSONObject fixture(String name)throws Exception {
        if(name.equals("dashboard"))return new JSONObject().put("data",new JSONObject().put("weather",fixture("weather")).put("finance",fixture("finance")).put("radio",new JSONObject().put("favorites",new JSONArray()))).put("meta",fixture("weather").getJSONObject("meta"));
        try(InputStream in=getClass().getClassLoader().getResourceAsStream("success.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)out.write(block,0,n);return new JSONObject(out.toString("UTF-8")).getJSONObject(name);}
    }
    private PanelRepository repo(Transport t,Storage s,Guard g){return new PanelRepository(t,s,"test-profile",null,g);}
    private void seed(Storage s,String path,JSONObject body)throws Exception{new JsonCache(s,"test-profile").put(path,body.toString(),"\"original\"");}
    private ApiClient.Response ok(JSONObject body){return new ApiClient.Response(200,body.toString(),"\"new\"");}
    private JSONObject weatherBootstrap()throws Exception{
        JSONObject bootstrap=fixture("bootstrap");JSONObject location=bootstrap.getJSONObject("data").getJSONObject("settings").getJSONObject("location");
        location.put("latitude","59.938630").put("longitude","30.314130");return bootstrap;
    }
    private JSONObject freshWeather()throws Exception{
        JSONObject weather=fixture("weather");java.text.SimpleDateFormat iso=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US);iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        long now=System.currentTimeMillis();weather.getJSONObject("meta").put("freshness","fresh").put("fetched_at",iso.format(new java.util.Date(now))).put("valid_until",iso.format(new java.util.Date(now+1800000)));
        return weather;
    }
    @Test public void freshServerWeatherWinsButStaleAndUnavailableWeatherUseDirectCache()throws Exception{
        Storage storage=new Storage();Transport transport=new Transport();Guard guard=new Guard();int[] directCalls={0};JSONObject bootstrap=weatherBootstrap(),fresh=freshWeather();
        PanelRepository repo=new PanelRepository(transport,storage,"test-profile",null,guard,location->{directCalls[0]++;return new JSONObject(fresh.toString());});
        transport.next=(path,etag)->ok(fresh);assertEquals("fresh",new Dtos.Weather(repo.weather(bootstrap,WEATHER,false)).meta.freshness);assertEquals(0,directCalls[0]);
        JSONObject stale=fixture("weather");stale.getJSONObject("meta").put("freshness","stale");transport.next=(path,etag)->ok(stale);
        JSONObject fallback=repo.weather(bootstrap,WEATHER,false);assertEquals(1,directCalls[0]);assertEquals(fresh.getJSONObject("meta").getString("fetched_at"),new Dtos.Weather(fallback).meta.fetchedAt);
        transport.next=(path,etag)->{throw new SocketTimeoutException("Server unavailable");};
        assertEquals(fresh.getJSONObject("meta").getString("fetched_at"),new Dtos.Weather(repo.weather(bootstrap,WEATHER,false)).meta.fetchedAt);assertEquals(1,directCalls[0]);
        assertEquals(fresh.getJSONObject("meta").getString("fetched_at"),new Dtos.Weather(repo.weather(bootstrap,WEATHER,true)).meta.fetchedAt);assertEquals(1,directCalls[0]);
    }
    @Test public void deniedServerWeatherNeverCallsDirectSource()throws Exception{
        Storage storage=new Storage();Transport transport=new Transport();Guard guard=new Guard();int[] directCalls={0};
        PanelRepository repo=new PanelRepository(transport,storage,"test-profile",null,guard,location->{directCalls[0]++;return freshWeather();});
        transport.next=(path,etag)->new ApiClient.Response(401,null,null);
        try{repo.weather(weatherBootstrap(),WEATHER,false);fail();}catch(ApiClient.HttpError expected){assertEquals(401,expected.code);}
        assertEquals(0,directCalls[0]);assertTrue(guard.forgotten);
    }
    @Test public void serverFailureWithoutSnapshotUsesDirectForecast()throws Exception{
        Transport transport=new Transport();JSONObject fresh=freshWeather();
        PanelRepository repo=new PanelRepository(transport,new Storage(),"test-profile",null,new Guard(),location->fresh);
        transport.next=(path,etag)->new ApiClient.Response(500,null,null);
        assertEquals(new Dtos.Weather(fresh).meta.fetchedAt,new Dtos.Weather(repo.weather(weatherBootstrap(),WEATHER,false)).meta.fetchedAt);
    }
    @Test public void failedDirectWeatherIsRateLimitedButManualRetryIsAllowed()throws Exception{
        Storage storage=new Storage();Transport transport=new Transport();int[] directCalls={0};JSONObject bootstrap=weatherBootstrap(),stale=fixture("weather");
        PanelRepository repo=new PanelRepository(transport,storage,"test-profile",null,new Guard(),location->{directCalls[0]++;throw new SocketTimeoutException("Provider unavailable");});
        transport.next=(path,etag)->ok(stale);
        assertEquals(stale.getJSONObject("meta").getString("fetched_at"),new Dtos.Weather(repo.weather(bootstrap,WEATHER,false)).meta.fetchedAt);
        repo.weather(bootstrap,WEATHER,false);assertEquals(1,directCalls[0]);
        try{repo.directWeather(bootstrap);fail();}catch(SocketTimeoutException expected){}
        assertEquals(2,directCalls[0]);
    }
    @Test public void dashboardReplacesOnlyStaleWeatherWithDirectForecast()throws Exception{
        Storage storage=new Storage();Transport transport=new Transport();JSONObject direct=freshWeather(),dashboard=fixture("dashboard");
        dashboard.getJSONObject("data").getJSONObject("weather").getJSONObject("meta").put("freshness","stale");
        PanelRepository repo=new PanelRepository(transport,storage,"test-profile",null,new Guard(),location->direct);
        transport.next=(path,etag)->ok(dashboard);Dtos.Dashboard shown=new Dtos.Dashboard(repo.dashboard(weatherBootstrap(),DASH,false));
        assertEquals(new Dtos.Weather(direct).meta.fetchedAt,shown.weather.meta.fetchedAt);assertEquals(new Dtos.Finance(dashboard.getJSONObject("data").getJSONObject("finance")).items.size(),shown.finance.items.size());
    }
    @Test public void preview304AndOfflineDoNotRewriteSourceDatesOrExtendTtl()throws Exception {
        Storage s=new Storage();Transport t=new Transport();PanelRepository r=repo(t,s,new Guard());JSONObject original=fixture("finance");
        t.next=(p,e)->ok(original);r.get(FIN);String committed=s.value;int writes=s.writes;
        JSONObject preview=r.cached(FIN);assertTrue(new Dtos.Meta(preview).revalidating);assertFalse(new Dtos.Meta(preview).offline);
        t.next=(p,e)->new ApiClient.Response(304,null,"\"new\"");JSONObject unchanged=r.get(FIN);
        assertEquals("\"new\"",t.lastTag);assertFalse(new Dtos.Meta(unchanged).offline);assertEquals(original.getJSONObject("meta").toString(),unchanged.getJSONObject("meta").toString());
        t.next=(p,e)->{throw new SocketTimeoutException("Injected offline");};JSONObject offline=r.get(FIN);
        assertTrue(new Dtos.Meta(offline).offline);assertFalse(new Dtos.Meta(offline).revalidating);
        assertEquals(original.getJSONObject("data").toString(),offline.getJSONObject("data").toString());assertEquals(original.getJSONObject("meta").toString(),offline.getJSONObject("meta").toString());
        assertEquals(committed,s.value);assertEquals(writes,s.writes);
    }
    @Test public void emptyCacheAndAuthenticationRefusalNeverBecomeOfflineSuccess()throws Exception {
        for(int status:new int[]{401,403}){
            Storage s=new Storage();Transport t=new Transport();Guard g=new Guard();PanelRepository r=repo(t,s,g);
            try{r.cached(FIN);fail();}catch(PanelRepository.NoSnapshot expected){}
            t.next=(p,e)->{throw new SocketTimeoutException();};try{r.get(FIN);fail();}catch(SocketTimeoutException expected){}
            seed(s,FIN,fixture("finance"));t.next=(p,e)->new ApiClient.Response(status,null,null);
            try{r.get(FIN);fail();}catch(ApiClient.HttpError expected){assertEquals(status,expected.code);}
            assertNull(s.value);assertEquals(status==401,g.forgotten);
        }
    }
    @Test public void badTlsIsNotReportedAsAnOfflineNetworkSuccess()throws Exception {
        Storage s=new Storage();seed(s,FIN,fixture("finance"));Transport t=new Transport();t.next=(p,e)->{throw new SSLHandshakeException("Injected TLS refusal");};
        try{repo(t,s,new Guard()).get(FIN);fail();}catch(SSLHandshakeException expected){}
    }
    @Test public void changedListAndLocationRemoveObsoleteViewsBeforeNewFetch()throws Exception {
        Storage s=new Storage();Transport t=new Transport();PanelRepository r=repo(t,s,new Guard());JSONObject old=fixture("bootstrap");
        seed(s,BOOT,old);seed(s,FIN,fixture("finance"));seed(s,WEATHER,fixture("weather"));seed(s,DASH,fixture("dashboard"));
        JSONObject next=new JSONObject(old.toString());next.getJSONObject("data").put("finance_list_revision",99);next.getJSONObject("data").getJSONObject("settings").getJSONObject("location").put("revision",2);
        t.next=(p,e)->ok(next);JSONObject received=r.get(BOOT);assertTrue(CacheCoherence.affects(received,"finance"));assertTrue(CacheCoherence.affects(received,"weather"));assertTrue(CacheCoherence.affects(received,"home"));assertFalse(CacheCoherence.affects(received,"radio"));
        for(String path:new String[]{FIN,WEATHER,DASH})try{r.cached(path);fail("Old representation survived configuration change");}catch(PanelRepository.NoSnapshot expected){}
        t.next=(p,e)->{throw new SocketTimeoutException();};try{r.get(FIN);fail("Hidden list used after failed replacement");}catch(SocketTimeoutException expected){}
        JSONObject empty=fixture("finance");empty.getJSONObject("data").put("items",new JSONArray());t.next=(p,e)->ok(empty);assertTrue(new Dtos.Finance(r.get(FIN)).items.isEmpty());assertTrue(new Dtos.Finance(r.cached(FIN)).items.isEmpty());
    }
    @Test public void configurationAndEvictionCommitTogetherOnDiskFailure()throws Exception {
        Storage s=new Storage();seed(s,BOOT,fixture("bootstrap"));seed(s,FIN,fixture("finance"));String committed=s.value;
        JsonCache cache=new JsonCache(s,"test-profile");s.fail=true;
        try{cache.replace(BOOT,"new configuration",null,new String[]{"/api/v1/finance"});fail();}catch(IOException expected){}
        assertEquals(committed,s.value);assertNotNull(cache.get(FIN));assertNotNull(new JsonCache(s,"test-profile").get(FIN));
    }
    @Test public void cachedReadDoesNotWaitForAnUnfinishedNetworkRequest()throws Exception {
        Storage s=new Storage();seed(s,FIN,fixture("finance"));Transport t=new Transport();PanelRepository r=repo(t,s,new Guard());CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        t.next=(p,e)->{started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Test gate timed out");return new ApiClient.Response(304,null,null);};
        try{Future<JSONObject> network=pool.submit(()->r.get(FIN));assertTrue(started.await(2,TimeUnit.SECONDS));Future<JSONObject> local=pool.submit(()->r.cached(FIN));assertTrue(new Dtos.Meta(local.get(1,TimeUnit.SECONDS)).revalidating);assertFalse(network.isDone());release.countDown();network.get(2,TimeUnit.SECONDS);}
        finally{release.countDown();pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);}
    }
    @Test public void delayedOldListCannotResurrectAfterAnotherRequestInvalidatesIt()throws Exception {
        Storage s=new Storage();JSONObject old=fixture("bootstrap"),finance=fixture("finance");seed(s,BOOT,old);seed(s,FIN,finance);Transport a=new Transport(),b=new Transport();Guard guard=new Guard();PanelRepository first=repo(a,s,guard),second=repo(b,s,guard);CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
        a.next=(p,e)->{started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Test gate timed out");return ok(finance);};
        JSONObject next=new JSONObject(old.toString());next.getJSONObject("data").put("finance_list_revision",99);b.next=(p,e)->ok(next);
        try{Future<JSONObject> delayed=pool.submit(()->first.get(FIN));assertTrue(started.await(2,TimeUnit.SECONDS));second.get(BOOT);release.countDown();try{delayed.get(2,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof PanelRepository.SnapshotInvalidated);}assertNull(new JsonCache(s,"test-profile").get(FIN));}
        finally{release.countDown();pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS);}
    }
    @Test public void malformedCachedDtoIsDiscardedBeforeSendingConditionalRequest()throws Exception {
        Storage s=new Storage();new JsonCache(s,"test-profile").put(FIN,"{\"data\":{},\"meta\":{}}","\"bad\"");Transport t=new Transport();JSONObject finance=fixture("finance");t.next=(p,e)->{assertNull(e);return ok(finance);};assertFalse(new Dtos.Finance(repo(t,s,new Guard()).get(FIN)).items.isEmpty());
    }
    @Test public void nestedExpiryChangesSignatureAtBoundaryWithoutChangingAnySourceField()throws Exception {
        JSONObject finance=fixture("finance");finance.getJSONObject("meta").put("valid_until",JSONObject.NULL);finance.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("valid_until","2026-10-03T12:00:00Z");
        String original=finance.toString();java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));long edge=f.parse("2026-10-03T12:00:00Z").getTime();
        assertNotEquals(ScreenSnapshot.of(finance,edge-1),ScreenSnapshot.of(finance,edge));assertEquals(original,finance.toString());
        assertFalse(PanelRepository.expired("2026-10-03T12:00:00Z",edge-1));assertTrue(PanelRepository.expired("2026-10-03T12:00:00Z",edge));assertTrue(PanelRepository.expired("2026-10-03T12:00:00Z trailing",edge-1));
    }
    @Test public void expiredChildMarksAggregateStaleEvenWhenAggregateTtlIsMissing()throws Exception {
        JSONObject dashboard=fixture("dashboard");dashboard.getJSONObject("meta").put("valid_until",JSONObject.NULL).put("freshness","fresh");
        dashboard.getJSONObject("data").getJSONObject("weather").getJSONObject("meta").put("valid_until","2000-01-01T00:00:00Z");
        assertTrue(Presenters.home(new Dtos.Dashboard(dashboard)).status.contains("УСТАРЕЛО"));
    }
    @Test public void kitkatNullTtlDoesNotMakeLocalConfigurationStale()throws Exception {
        // Android's bundled JSONObject converts JSON null to the literal string "null".
        JSONObject meta=new JSONObject(){@Override public String optString(String key,String fallback){return has(key)&&isNull(key)?"null":super.optString(key,fallback);}};
        meta.put("test_data",false).put("scenario","database").put("freshness","fresh").put("fetched_at",JSONObject.NULL).put("data_as_of",JSONObject.NULL).put("valid_until",JSONObject.NULL);
        JSONObject envelope=new JSONObject().put("meta",meta).put("data",new JSONObject());
        assertFalse(new Dtos.Meta(envelope).expired);assertTrue(ScreenSnapshot.of(envelope).contains("\"expired\":false"));
        meta.put("valid_until","2000-01-01T00:00:00Z");assertTrue(new Dtos.Meta(envelope).expired);assertTrue(ScreenSnapshot.of(envelope).contains("\"expired\":true"));
    }
}
