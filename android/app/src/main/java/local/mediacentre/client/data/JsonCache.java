package local.mediacentre.client.data;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Iterator;

/** One bounded index, replaced atomically by Storage; source bodies are never rewritten. */
public final class JsonCache {
    public interface Storage {String read() throws IOException;void write(String value)throws IOException;void delete();}
    public static final int MAX_BODY=262144,MAX_TOTAL=2097152,MAX_ENTRIES=32;
    private final Storage storage;
    private final String profile;
    private JSONObject entries=new JSONObject();
    private String serverId;
    public static final class Entry {
        public final String body,etag;
        Entry(String body,String etag){this.body=body;this.etag=etag;}
    }
    public JsonCache(Storage storage,String profile) throws Exception {
        this.storage=storage;this.profile=profile;
        try {
            String saved=storage.read();
            if(saved!=null && saved.getBytes("UTF-8").length<=MAX_TOTAL) {
                JSONObject root=new JSONObject(saved);
                if(root.getInt("version")==1 && profile.equals(root.getString("profile"))) {
                    JSONObject candidate=root.getJSONObject("entries");
                    if(candidate.length()<=MAX_ENTRIES){entries=candidate;serverId=root.isNull("server_id")?null:root.getString("server_id");}else storage.delete();
                }else storage.delete();
            }else if(saved!=null)storage.delete();
        }catch(IOException|JSONException corrupt){storage.delete();}
    }
    public static String profile(String origin,String token)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest((origin+"\n"+token).getBytes("UTF-8"));
        StringBuilder text=new StringBuilder();for(byte value:digest)text.append(String.format(java.util.Locale.US,"%02x",value&255));return text.toString();
    }
    public synchronized Entry get(String path)throws Exception {
        JSONObject record=entries.optJSONObject(path);if(record==null)return null;
        try{
            String body=record.getString("body");
            if(body.getBytes("UTF-8").length>MAX_BODY){remove(path);return null;}
            return new Entry(body,record.isNull("etag")?null:record.getString("etag"));
        }catch(JSONException corrupt){remove(path);return null;}
    }
    private String encode(JSONObject values)throws Exception {return new JSONObject().put("version",1).put("profile",profile).put("server_id",serverId==null?JSONObject.NULL:serverId).put("entries",values).toString();}
    public synchronized void acceptServer(String id)throws Exception {
        if(!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw new JSONException("Invalid server UUID.");
        if(serverId!=null && !serverId.equals(id)){storage.delete();entries=new JSONObject();}
        serverId=id;
    }
    public synchronized void put(String path,String body,String etag)throws Exception {
        replace(path,body,etag,new String[0]);
    }
    /** Configuration and removal of its obsolete views commit in one AtomicFile write. */
    public synchronized void replace(String path,String body,String etag,String[] invalidated)throws Exception {
        if(body.getBytes("UTF-8").length>MAX_BODY)throw new IOException("Cache body too large.");
        JSONObject next=new JSONObject(entries.toString());
        Iterator<String> keys=entries.keys();while(keys.hasNext()){String key=keys.next();for(String prefix:invalidated)if(key.startsWith(prefix)){next.remove(key);break;}}
        next.remove(path);
        long order=0;Iterator<String> ordered=next.keys();while(ordered.hasNext())order=Math.max(order,next.getJSONObject(ordered.next()).optLong("order",0));
        while(next.length()>=MAX_ENTRIES)next.remove(victim(next,path));
        next.put(path,new JSONObject().put("body",body).put("etag",etag==null?JSONObject.NULL:etag).put("order",order+1));
        String encoded=encode(next);
        while(encoded.getBytes("UTF-8").length>MAX_TOTAL && next.length()>1){
            next.remove(victim(next,path));encoded=encode(next);
        }
        if(encoded.getBytes("UTF-8").length>MAX_TOTAL)throw new IOException("Cache index too large.");
        storage.write(encoded);entries=next;
    }
    private static boolean primary(String path){return path.startsWith("/api/v1/bootstrap")||path.startsWith("/api/v1/dashboard")||path.startsWith("/api/v1/weather")||path.startsWith("/api/v1/finance")||path.startsWith("/api/v1/client/preferences");}
    private static String victim(JSONObject values,String protectedPath)throws Exception{
        String candidate=null;long oldest=Long.MAX_VALUE;Iterator<String> keys=values.keys();
        while(keys.hasNext()){String key=keys.next();if(key.equals(protectedPath))continue;long order=values.getJSONObject(key).optLong("order",0);if(!primary(key)&&(candidate==null||primary(candidate)||order<oldest)){candidate=key;oldest=order;}else if(candidate==null||(primary(key)&&primary(candidate)&&order<oldest)){candidate=key;oldest=order;}}
        if(candidate==null)throw new IOException("No cache eviction candidate.");return candidate;
    }
    public synchronized void remove(String path)throws Exception {
        JSONObject next=new JSONObject(entries.toString());next.remove(path);storage.write(encode(next));entries=next;
    }
    public synchronized void clear(){storage.delete();entries=new JSONObject();serverId=null;}
    public synchronized void invalidate(String... prefixes)throws Exception {
        JSONObject next=new JSONObject(entries.toString());Iterator<String> keys=entries.keys();
        while(keys.hasNext()){String key=keys.next();for(String prefix:prefixes)if(key.startsWith(prefix)){next.remove(key);break;}}
        storage.write(encode(next));entries=next;
    }
}
