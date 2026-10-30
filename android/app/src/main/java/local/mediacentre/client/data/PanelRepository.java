package local.mediacentre.client.data;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import javax.net.ssl.SSLException;

public final class PanelRepository {
    static final Object LOCK=new Object();
    private static long cacheEpoch;
    private final java.util.concurrent.locks.ReentrantLock requests=new java.util.concurrent.locks.ReentrantLock();
    private final PanelTransport api;
    public interface WeatherSource {JSONObject fetch(JSONObject location)throws Exception;}
    private final WeatherSource weatherSource;
    private String lastDirectKey;
    private long retryDirectAfter;
    private final JsonCache.Storage storage;
    private final String profile,expectedServer;
    public interface ConnectionGuard {boolean current();void forget();}
    private final ConnectionGuard guard;
    public static final class NoSnapshot extends IOException {public NoSnapshot(){super("No saved snapshot.");}}
    public static final class SnapshotInvalidated extends IOException {public SnapshotInvalidated(){super("Configuration changed during request.");}}
    public PanelRepository(Context context,String base,String token)throws Exception {this(context,base,token,null);}
    public PanelRepository(Context context,String base,String token,String expectedServer)throws Exception {
        this(new ApiClient(context,base,token,expectedServer),atomicStorage(context),JsonCache.profile(base.replaceAll("/+$",""),token.trim()),expectedServer,connectionGuard(context,base,token),new MetNorwayWeatherClient(context));
    }
    /** The production policy is exercised with deterministic transport/storage failures. */
    public PanelRepository(PanelTransport transport,JsonCache.Storage storage,String profile,String expectedServer,ConnectionGuard guard){
        this(transport,storage,profile,expectedServer,guard,null);
    }
    public PanelRepository(PanelTransport transport,JsonCache.Storage storage,String profile,String expectedServer,ConnectionGuard guard,WeatherSource weatherSource){
        this.api=transport;this.storage=storage;this.profile=profile;this.expectedServer=expectedServer;this.guard=guard;this.weatherSource=weatherSource;
    }
    private static ConnectionGuard connectionGuard(Context context,String base,String token){
        android.content.SharedPreferences saved=context.getApplicationContext().getSharedPreferences("connection",Context.MODE_PRIVATE);
        return new ConnectionGuard(){
            public boolean current(){return token.equals(saved.getString("token",""))&&base.equals(saved.getString("base_url",""));}
            public void forget(){if(current())saved.edit().remove("token").remove("server_id").remove("selected_station").commit();}
        };
    }
    private static JsonCache.Storage atomicStorage(Context context){
        final AtomicFile file=new AtomicFile(new File(context.getApplicationContext().getFilesDir(),"panel-cache-v1.json"));
        return new JsonCache.Storage(){
            public String read()throws IOException {
                if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return null;
                try(InputStream input=file.openRead()){
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
                    while((n=input.read(block))!=-1){if(out.size()+n>JsonCache.MAX_TOTAL)throw new IOException("Cache exceeds limit.");out.write(block,0,n);}
                    return out.toString("UTF-8");
                }
            }
            public void write(String value)throws IOException {
                FileOutputStream output=null;
                try{output=file.startWrite();output.write(value.getBytes("UTF-8"));file.finishWrite(output);}
                catch(IOException error){if(output!=null)file.failWrite(output);throw error;}
            }
            public void delete(){file.delete();}
        };
    }
    private JsonCache cache()throws Exception{return new JsonCache(storage,profile);}
    private JsonCache.Entry previous(JsonCache cache,String path)throws Exception {
        JsonCache.Entry entry=cache.get(path);if(entry==null)return null;
        try{validate(path,new JSONObject(entry.body));return entry;}catch(JSONException corrupt){cache.remove(path);return null;}
    }
    private void active()throws InterruptedException{
        if(Thread.currentThread().isInterrupted()||!guard.current())throw new InterruptedException("Obsolete request.");
    }
    public JSONObject read(String path,boolean cacheOnly)throws Exception{return cacheOnly?cached(path):get(path);}
    private static boolean freshWeather(JSONObject value){
        if(value==null)return false;
        try{Dtos.Meta meta=new Dtos.Meta(value);return !meta.offline&&!meta.expired&&"fresh".equals(meta.freshness)&&meta.validUntil!=null&&!expired(meta.validUntil);}
        catch(Exception invalid){return false;}
    }
    private static boolean fallbackAllowed(Exception error){
        if(error instanceof InterruptedException||error instanceof PairIdentity.ServerChanged||error instanceof SnapshotInvalidated||error instanceof SSLException)return false;
        if(error instanceof ApiClient.HttpError){int code=((ApiClient.HttpError)error).code;return code>=500&&code<=599;}
        return error instanceof IOException;
    }
    public static boolean canUseDirectWeather(Exception error){return fallbackAllowed(error);}
    private static JSONObject configuredLocation(JSONObject bootstrap)throws Exception{
        return bootstrap.getJSONObject("data").getJSONObject("settings").getJSONObject("location");
    }
    private static String directKey(JSONObject location){
        if(!location.has("latitude")||!location.has("longitude"))return null;
        return "/direct/met-norway/weather/"+location.optString("id")+"/"+location.optInt("revision")+"/"+location.optString("latitude")+"/"+location.optString("longitude")+"/"+location.optString("timezone");
    }
    private JSONObject directCached(String key)throws Exception{
        synchronized(LOCK){active();JsonCache.Entry entry=cache().get(key);if(entry==null)return null;
            try{JSONObject value=new JSONObject(entry.body);new Dtos.Weather(value);return annotate(value,false,false,new String[0]);}
            catch(JSONException invalid){cache().remove(key);return null;}}
    }
    private JSONObject preferred(JSONObject server,JSONObject direct){
        if(freshWeather(server))return server;
        if(freshWeather(direct))return direct;
        if(server==null)return direct;
        if(direct==null)return server;
        String serverAt=server.optJSONObject("meta").optString("fetched_at","");
        String directAt=direct.optJSONObject("meta").optString("fetched_at","");
        return directAt.compareTo(serverAt)>0?direct:server;
    }
    private JSONObject weatherFallback(JSONObject location,JSONObject server,Exception serverError,boolean cacheOnly,boolean force)throws Exception{
        if(weatherSource==null||directKey(location)==null){if(server!=null)return server;if(serverError!=null)throw serverError;throw new NoSnapshot();}
        String key=directKey(location);JSONObject saved=directCached(key);
        if(!force&&freshWeather(server))return server;
        if(cacheOnly||!force&&freshWeather(saved)){
            JSONObject best=preferred(server,saved);if(best!=null)return best;if(serverError!=null)throw serverError;throw new NoSnapshot();
        }
        synchronized(LOCK){active();if(!force&&key.equals(lastDirectKey)&&System.currentTimeMillis()<retryDirectAfter){
            JSONObject best=preferred(server,saved);if(best!=null)return best;if(serverError!=null)throw serverError;throw new NoSnapshot();
        }}
        try{
            JSONObject direct=weatherSource.fetch(location);new Dtos.Weather(direct);active();
            synchronized(LOCK){active();cache().put(key,direct.toString(),null);lastDirectKey=key;retryDirectAfter=0;}
            return annotate(direct,false,false,new String[0]);
        }catch(Exception error){
            if(error instanceof InterruptedException||error instanceof SnapshotInvalidated)throw error;
            synchronized(LOCK){active();lastDirectKey=key;retryDirectAfter=System.currentTimeMillis()+300000;}
            JSONObject best=preferred(server,saved);if(best!=null)return best;if(serverError!=null)throw serverError;throw error;
        }
    }
    public JSONObject weather(JSONObject bootstrap,String path,boolean cacheOnly)throws Exception{
        JSONObject server=null;Exception failure=null;
        try{server=read(path,cacheOnly);}catch(Exception error){if(!fallbackAllowed(error))throw error;failure=error;}
        return weatherFallback(configuredLocation(bootstrap),server,failure,cacheOnly,false);
    }
    public JSONObject dashboard(JSONObject bootstrap,String path,boolean cacheOnly)throws Exception{
        JSONObject result=read(path,cacheOnly);JSONObject server=result.getJSONObject("data").getJSONObject("weather");
        if(result.optJSONObject("_client_cache")!=null&&result.getJSONObject("_client_cache").optBoolean("offline"))server=annotate(new JSONObject(server.toString()),true,false,new String[0]);
        JSONObject chosen=weatherFallback(configuredLocation(bootstrap),server,null,cacheOnly,false);
        if(chosen!=server){
            JSONObject data=result.getJSONObject("data"),weatherMeta=chosen.getJSONObject("meta"),financeMeta=data.getJSONObject("finance").getJSONObject("meta"),root=result.getJSONObject("meta");
            data.put("weather",chosen);
            String financeState=financeMeta.getString("freshness"),weatherState=weatherMeta.getString("freshness");
            root.put("freshness","stale".equals(financeState)||"stale".equals(weatherState)?"stale":
                "empty".equals(financeState)&&"empty".equals(weatherState)?"empty":
                "fresh".equals(financeState)&&"fresh".equals(weatherState)?"fresh":"partial");
            String weatherUntil=weatherMeta.isNull("valid_until")?"":weatherMeta.optString("valid_until",""),financeUntil=financeMeta.isNull("valid_until")?"":financeMeta.optString("valid_until","");
            root.put("valid_until",weatherUntil.isEmpty()?financeUntil.isEmpty()?JSONObject.NULL:financeUntil:financeUntil.isEmpty()?weatherUntil:weatherUntil.compareTo(financeUntil)<0?weatherUntil:financeUntil);
            String weatherFetched=weatherMeta.optString("fetched_at",""),rootFetched=root.optString("fetched_at","");
            if(weatherFetched.compareTo(rootFetched)>0)root.put("fetched_at",weatherFetched);
            new Dtos.Dashboard(result);
        }
        return result;
    }
    public JSONObject directWeather(JSONObject bootstrap)throws Exception{
        return weatherFallback(configuredLocation(bootstrap),null,null,false,true);
    }
    public JSONObject cached(String path)throws Exception {
        synchronized(LOCK){active();JsonCache cache=cache();JsonCache.Entry entry=previous(cache,path);if(entry==null)throw new NoSnapshot();
            JSONObject data=new JSONObject(entry.body);validate(path,data);verifyIdentity(path,data,cache);return annotate(data,false,true,new String[0]);}
    }
    public JSONObject get(String path)throws Exception {
        requests.lockInterruptibly();try{
            final JsonCache.Entry previous;final long epoch;
            synchronized(LOCK){active();previous=previous(cache(),path);epoch=cacheEpoch;}
            try{
                ApiClient.Response response=api.fetch(path,previous==null?null:previous.etag);
                synchronized(LOCK){
                    active();JsonCache cache=cache();
                    if(response.code==401||response.code==403){cache.clear();cacheEpoch++;if(response.code==401)guard.forget();throw new ApiClient.HttpError(response.code);}
                    if(epoch!=cacheEpoch)throw new SnapshotInvalidated();
                    if(response.code==304&&previous!=null){JSONObject data=new JSONObject(previous.body);validate(path,data);verifyIdentity(path,data,cache);return annotate(data,false,false,new String[0]);}
                    if(response.code==404){cache.remove(path);throw new ApiClient.HttpError(response.code);}
                    if(response.code!=200)throw new ApiClient.HttpError(response.code);
                    JSONObject data=new JSONObject(response.body);validate(path,data);verifyIdentity(path,data,cache);
                    if(path.startsWith("/api/v1/bootstrap"))cache.acceptServer(data.getJSONObject("data").getString("server_id"));
                    String[] invalidated=CacheCoherence.invalidated(path,previous==null?null:previous.body,data);
                    cache.replace(path,response.body,response.etag,invalidated);if(invalidated.length>0)cacheEpoch++;
                    if(path.startsWith("/api/v1/radio/stations?")){
                        JSONArray stations=data.getJSONObject("data").getJSONArray("items");
                        for(int i=0;i<stations.length();i++){
                            JSONObject station=stations.getJSONObject(i);
                            if(station.getBoolean("favorite")){
                                JSONObject envelope=new JSONObject().put("data",station).put("meta",data.getJSONObject("meta"));
                                cache.put("/api/v1/radio/stations/"+station.getString("id")+scenarioSuffix(path),envelope.toString(),null);
                            }
                        }
                    }
                    return annotate(data,false,false,invalidated);
                }
            }catch(IOException unavailable){
                boolean retryable=!(unavailable instanceof SSLException)&&!(unavailable instanceof PairIdentity.ServerChanged)&&!(unavailable instanceof SnapshotInvalidated)&&(!(unavailable instanceof ApiClient.HttpError)||((ApiClient.HttpError)unavailable).code==502||((ApiClient.HttpError)unavailable).code==503||((ApiClient.HttpError)unavailable).code==504);
                if(!retryable||previous==null)throw unavailable;
                synchronized(LOCK){active();if(epoch!=cacheEpoch)throw new SnapshotInvalidated();JsonCache cache=cache();JSONObject data=new JSONObject(previous.body);validate(path,data);verifyIdentity(path,data,cache);return annotate(data,true,false,new String[0]);}
            }
        }finally{requests.unlock();}
    }
    private static String scenarioSuffix(String path){int at=path.indexOf("scenario=");if(at<0)return "";return "?scenario="+path.substring(at+9).split("&")[0];}
    private void verifyIdentity(String path,JSONObject data,JsonCache cache)throws Exception {
        if(path.startsWith("/api/v1/bootstrap"))try{PairIdentity.verifyServer(expectedServer,data.getJSONObject("data").getString("server_id"));}
        catch(PairIdentity.ServerChanged changed){cache.clear();cacheEpoch++;guard.forget();throw changed;}
    }
    public static void clearCache(Context context){synchronized(LOCK){atomicStorage(context).delete();cacheEpoch++;}}
    private void invalidate(String... paths)throws Exception{synchronized(LOCK){active();cache().invalidate(paths);cacheEpoch++;}}
    public void favorite(String id,boolean enabled,String scenario)throws Exception {
        requests.lockInterruptibly();try{active();try{api.favorite(id,enabled,scenario);invalidate("/api/v1/radio/","/api/v1/dashboard");}catch(ApiClient.HttpError error){writeError(error);throw error;}}finally{requests.unlock();}
    }
    public void refreshWeather()throws Exception {
        requests.lockInterruptibly();try{active();try{api.refreshWeather();}catch(ApiClient.HttpError error){writeError(error);throw error;}}finally{requests.unlock();}
    }
    public void preferences(JSONObject patch,String scenario)throws Exception {
        requests.lockInterruptibly();try{
            String path="/api/v1/client/preferences?scenario="+ApiClient.encode(scenario);
            for(int attempt=0;attempt<2;attempt++){
                try{
                    JSONObject current=get(path);if(current.getJSONObject("_client_cache").getBoolean("offline"))throw new IOException("Settings are offline.");
                    JsonCache.Entry cached;synchronized(LOCK){active();cached=cache().get(path);}
                    JSONObject input=new JSONObject(patch.toString()).put("revision",current.getJSONObject("data").getInt("revision"));
                    JSONObject updated=api.preferences(path,input,cached==null?null:cached.etag);validate(path,updated);
                    invalidate("/api/v1/client/preferences","/api/v1/bootstrap","/api/v1/dashboard");return;
                }catch(ApiClient.HttpError error){if(error.code==412&&attempt==0){invalidate("/api/v1/client/preferences");continue;}writeError(error);throw error;}
            }
        }finally{requests.unlock();}
    }
    private void writeError(ApiClient.HttpError error)throws Exception {if(error.code==401)synchronized(LOCK){if(guard.current()){cache().clear();cacheEpoch++;guard.forget();}}}
    public JSONObject heartbeat(JSONObject data)throws Exception{return heartbeat(data,"database");}
    public JSONObject heartbeat(JSONObject data,String scenario)throws Exception {active();try{return api.heartbeat(data,scenario);}catch(ApiClient.HttpError error){writeError(error);throw error;}}
    public static void validate(String path,JSONObject value)throws Exception {
        new Dtos.Meta(value);
        if(path.startsWith("/api/v1/bootstrap"))new Dtos.Bootstrap(value);
        else if(path.startsWith("/api/v1/dashboard"))new Dtos.Dashboard(value);
        else if(path.startsWith("/api/v1/weather"))new Dtos.Weather(value);
        else if(path.startsWith("/api/v1/finance"))new Dtos.Finance(value);
        else if(path.startsWith("/api/v1/radio/stations/"))new Dtos.Station(value.getJSONObject("data"));
        else if(path.startsWith("/api/v1/radio/stations"))new Dtos.Catalogue(value);
        else if(path.startsWith("/api/v1/client/preferences")){JSONObject d=value.getJSONObject("data");d.getInt("revision");d.getJSONObject("location");}
        else throw new IOException("Unsupported cached endpoint.");
    }
    private static JSONObject annotate(JSONObject value,boolean offline,boolean revalidating,String[] invalidated)throws Exception {
        value.put("_client_cache",new JSONObject().put("offline",offline).put("revalidating",revalidating).put("invalidated",new JSONArray(Arrays.asList(invalidated))));return value;
    }
    public static boolean expired(String valid){return expired(valid,System.currentTimeMillis());}
    public static boolean expired(String valid,long now){
        if(valid==null)return false;
        try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));format.setLenient(false);ParsePosition position=new ParsePosition(0);Date parsed=format.parse(valid,position);return parsed==null||position.getIndex()!=valid.length()||parsed.getTime()<=now;}
        catch(Exception bad){return true;}
    }
}
