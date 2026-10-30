package local.mediacentre.client.climate;

import java.util.*;
import org.json.*;

/** Immutable saved sensor and a bounded rolling day of samples. */
public final class ClimateSensor {
    public static final int MAX_SENSORS=3,MAX_SAMPLES=288;
    public static final long HISTORY_MS=24*60*60_000L;
    public final String address,name;
    public final ClimateReading latest;
    public final List<ClimateReading> history;

    public ClimateSensor(String address,String name,ClimateReading latest,List<ClimateReading> history){
        if(address==null||!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new IllegalArgumentException("Invalid Bluetooth address");
        if(name==null||name.trim().isEmpty()||name.trim().codePointCount(0,name.trim().length())>40||name.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw new IllegalArgumentException("Invalid room name");
        this.address=address.toUpperCase(Locale.ROOT);this.name=name.trim();this.latest=latest;
        this.history=Collections.unmodifiableList(new ArrayList<>(history));
    }
    public ClimateSensor renamed(String name){return new ClimateSensor(address,name,latest,history);}
    public ClimateSensor measured(ClimateReading reading){
        List<ClimateReading> samples=new ArrayList<>();
        for(ClimateReading previous:history)if(previous.measuredAt>=reading.measuredAt-HISTORY_MS&&previous.measuredAt<=reading.measuredAt)samples.add(previous);
        // Manual refreshes must not crowd a whole day's history out of the cache.
        if(!samples.isEmpty()&&reading.measuredAt/ClimateController.HISTORY_BUCKET_MS==samples.get(samples.size()-1).measuredAt/ClimateController.HISTORY_BUCKET_MS)samples.remove(samples.size()-1);
        samples.add(reading);while(samples.size()>MAX_SAMPLES)samples.remove(0);
        return new ClimateSensor(address,name,reading,samples);
    }
    private static JSONObject json(ClimateReading reading)throws JSONException{
        return new JSONObject().put("temperature",reading.temperature).put("humidity",reading.humidity).put("millivolts",reading.batteryMillivolts).put("time",reading.measuredAt);
    }
    private static ClimateReading reading(JSONObject value)throws JSONException{
        return new ClimateReading(value.getDouble("temperature"),value.getInt("humidity"),value.getInt("millivolts"),value.getLong("time"));
    }
    public JSONObject toJson()throws JSONException{
        JSONArray samples=new JSONArray();for(ClimateReading sample:history)samples.put(json(sample));
        JSONObject value=new JSONObject().put("address",address).put("name",name).put("history",samples);
        if(latest!=null)value.put("latest",json(latest));return value;
    }
    public static ClimateSensor fromJson(JSONObject value)throws JSONException{
        JSONArray items=value.optJSONArray("history");List<ClimateReading> samples=new ArrayList<>();
        if(items!=null)for(int n=Math.max(0,items.length()-MAX_SAMPLES);n<items.length();n++)samples.add(reading(items.getJSONObject(n)));
        return new ClimateSensor(value.getString("address"),value.getString("name"),value.has("latest")?reading(value.getJSONObject("latest")):null,samples);
    }
}
