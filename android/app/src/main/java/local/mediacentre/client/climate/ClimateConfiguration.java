package local.mediacentre.client.climate;

import java.util.*;
import org.json.*;

/** Immutable wire configuration; measurements never participate in configuration conflicts. */
public final class ClimateConfiguration {
    public final long revision;
    public final int pollMinutes;
    public final boolean initialized;
    public final List<ClimateSensor> sensors;
    public ClimateConfiguration(long revision,int minutes,boolean initialized,List<ClimateSensor> sensors){
        if(revision<0||minutes<1||minutes>120||sensors.size()>ClimateSensor.MAX_SENSORS)throw new IllegalArgumentException("Invalid climate configuration");
        this.revision=revision;pollMinutes=minutes;this.initialized=initialized;
        Set<String> addresses=new HashSet<>();List<ClimateSensor> copy=new ArrayList<>();
        for(ClimateSensor s:sensors){if(!addresses.add(s.address))throw new IllegalArgumentException("Duplicate sensor");copy.add(new ClimateSensor(s.address,s.name,null,Collections.emptyList()));}
        this.sensors=Collections.unmodifiableList(copy);
    }
    public static ClimateConfiguration fromJson(JSONObject data)throws JSONException{
        JSONArray array=data.getJSONArray("sensors");List<ClimateSensor> items=new ArrayList<>();
        for(int n=0;n<array.length();n++){JSONObject s=array.getJSONObject(n);items.add(new ClimateSensor(s.getString("address"),s.getString("name"),null,Collections.emptyList()));}
        return new ClimateConfiguration(data.getLong("revision"),data.getInt("poll_minutes"),data.optBoolean("initialized",true),items);
    }
    public JSONObject toJson()throws JSONException{
        JSONArray items=new JSONArray();for(ClimateSensor s:sensors)items.put(new JSONObject().put("address",s.address).put("name",s.name));
        return new JSONObject().put("revision",revision).put("poll_minutes",pollMinutes).put("initialized",initialized).put("sensors",items);
    }
    public JSONObject request(long expected,int minutes)throws JSONException{JSONObject json=toJson();json.remove("initialized");return json.put("revision",expected).put("poll_minutes",minutes);}
    /** A pairing change on the same server must not reuse another tablet's CAS baseline. */
    public static String binding(String server,String base,String token){
        try{
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest((server+"\n"+base+"\n"+token).getBytes("UTF-8"));
            StringBuilder result=new StringBuilder();for(byte value:digest)result.append(String.format(Locale.US,"%02x",value&255));return result.toString();
        }catch(java.security.NoSuchAlgorithmException|java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
    }
    private static Map<String,String> names(ClimateConfiguration c){Map<String,String> names=new LinkedHashMap<>();for(ClimateSensor s:c.sensors)names.put(s.address,s.name);return names;}
    public boolean sameSensors(ClimateConfiguration other){return names(this).equals(names(other));}
    private static boolean equal(String a,String b){return a==null?b==null:a.equals(b);}
    /** Non-conflicting offline edits merge; deletion/rename conflicts require an explicit choice. */
    public static ClimateConfiguration merge(ClimateConfiguration base,ClimateConfiguration local,ClimateConfiguration remote){
        Map<String,String> b=names(base),l=names(local),r=names(remote);
        Set<String> order=new LinkedHashSet<>(r.keySet());order.addAll(l.keySet());order.addAll(b.keySet());
        List<ClimateSensor> result=new ArrayList<>();
        for(String address:order){
            String before=b.get(address),mine=l.get(address),theirs=r.get(address),name;
            if(equal(mine,before))name=theirs;
            else if(equal(theirs,before)||equal(mine,theirs))name=mine;
            else throw new IllegalStateException("Conflicting sensor edits");
            if(name!=null)result.add(new ClimateSensor(address,name,null,Collections.emptyList()));
        }
        if(result.size()>ClimateSensor.MAX_SENSORS)throw new IllegalStateException("Too many merged sensors");
        return new ClimateConfiguration(remote.revision,remote.pollMinutes,true,result);
    }
}
