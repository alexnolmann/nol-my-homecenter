package local.mediacentre.client.presentation;
import local.mediacentre.client.data.PanelRepository;
import org.json.*;

/** Ignore request timestamps, but retain data, source chronology and visible freshness. */
public final class ScreenSnapshot {
    private ScreenSnapshot(){}
    public static String of(JSONObject envelope)throws JSONException {
        return of(envelope,System.currentTimeMillis());
    }
    public static String of(JSONObject envelope,long now)throws JSONException {
        JSONObject meta=envelope.getJSONObject("meta"), visible=new JSONObject();
        for(String key:new String[]{"test_data","scenario","freshness","source","fetched_at","valid_until","data_as_of"})if(meta.has(key))visible.put(key,meta.get(key));
        visible.put("expired",!meta.isNull("valid_until")&&PanelRepository.expired(meta.optString("valid_until",null),now));
        JSONObject cache=envelope.optJSONObject("_client_cache");visible.put("offline",cache!=null&&cache.optBoolean("offline",false));visible.put("revalidating",cache!=null&&cache.optBoolean("revalidating",false));
        JSONObject data=new JSONObject(envelope.getJSONObject("data").toString());data.remove("server_time");
        expiry(data,now);
        return data.toString()+"\n"+visible.toString()+"\n";
    }
    private static void expiry(Object value,long now)throws JSONException {
        if(value instanceof JSONArray){JSONArray list=(JSONArray)value;for(int n=0;n<list.length();n++)expiry(list.get(n),now);}
        else if(value instanceof JSONObject){JSONObject item=(JSONObject)value;java.util.List<String> keys=new java.util.ArrayList<>();java.util.Iterator<String> it=item.keys();while(it.hasNext())keys.add(it.next());for(String key:keys)expiry(item.get(key),now);if(item.has("valid_until"))item.put("__local_expired",!item.isNull("valid_until")&&PanelRepository.expired(item.optString("valid_until",null),now));}
    }
}
