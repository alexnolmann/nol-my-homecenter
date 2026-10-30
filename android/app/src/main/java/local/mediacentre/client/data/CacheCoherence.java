package local.mediacentre.client.data;
import org.json.*;
import java.util.*;

/** Remove obsolete representations as soon as a new configuration is known. */
public final class CacheCoherence {
    private CacheCoherence(){}
    public static String[] invalidated(String path,String previous,JSONObject response)throws JSONException {
        if(!path.startsWith("/api/v1/bootstrap")||previous==null)return new String[0];
        JSONObject before=new JSONObject(previous).getJSONObject("data"),after=response.getJSONObject("data");
        Set<String> paths=new LinkedHashSet<>();
        if(!before.getJSONObject("settings").getJSONObject("location").toString().equals(after.getJSONObject("settings").getJSONObject("location").toString())){paths.add("/api/v1/weather");paths.add("/api/v1/dashboard");}
        if(before.optInt("finance_list_revision",-1)!=after.optInt("finance_list_revision",-1)){paths.add("/api/v1/finance");paths.add("/api/v1/dashboard");}
        JSONObject oldFeatures=before.getJSONObject("features"),features=after.getJSONObject("features");
        for(String feature:new String[]{"weather","finance","radio"})if(oldFeatures.optBoolean(feature,true)!=features.optBoolean(feature,true)){
            paths.add("/api/v1/"+(feature.equals("radio")?"radio/":feature));paths.add("/api/v1/dashboard");
        }
        return paths.toArray(new String[paths.size()]);
    }
    public static boolean affects(JSONObject bootstrap,String screen){
        JSONObject cache=bootstrap.optJSONObject("_client_cache");JSONArray paths=cache==null?null:cache.optJSONArray("invalidated");
        String path="/api/v1/"+(screen.equals("home")?"dashboard":screen.equals("stations")||screen.equals("radio")?"radio/":screen);
        if(paths!=null)for(int n=0;n<paths.length();n++)if(path.startsWith(paths.optString(n)))return true;
        return false;
    }
}
