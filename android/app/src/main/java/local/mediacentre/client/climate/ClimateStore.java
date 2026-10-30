package local.mediacentre.client.climate;

import android.content.*;
import java.util.*;
import org.json.*;

/** Tablet cache and server configuration baseline. All access runs on the main thread. */
public final class ClimateStore {
    private final SharedPreferences preferences;
    private final List<ClimateSensor> sensors=new ArrayList<>();
    private ClimateConfiguration base;
    private int pollMinutes=15;
    private long generation;
    private boolean dirty;
    private String server;
    public ClimateStore(Context context){
        preferences=context.getSharedPreferences("climate",Context.MODE_PRIVATE);
        pollMinutes=Math.max(1,Math.min(120,preferences.getInt("poll_minutes",15)));dirty=preferences.getBoolean("dirty",false);server=preferences.getString("binding_id","");
        try{
            JSONArray values=new JSONArray(preferences.getString("sensors","[]"));Set<String> seen=new HashSet<>();
            for(int n=0;n<values.length()&&sensors.size()<ClimateSensor.MAX_SENSORS;n++){
                try{ClimateSensor sensor=ClimateSensor.fromJson(values.getJSONObject(n));if(seen.add(sensor.address))sensors.add(sensor);}
                catch(Exception invalid){/* Keep valid saved sensors. */}
            }
        }catch(JSONException invalid){/* Empty cache remains usable. */}
        try{base=ClimateConfiguration.fromJson(new JSONObject(preferences.getString("base","{}")));}
        catch(Exception invalid){base=new ClimateConfiguration(0,pollMinutes,false,Collections.emptyList());}
    }
    public List<ClimateSensor> sensors(){return Collections.unmodifiableList(new ArrayList<>(sensors));}
    public ClimateSensor find(String address){for(ClimateSensor sensor:sensors)if(sensor.address.equalsIgnoreCase(address))return sensor;return null;}
    public int pollMinutes(){return pollMinutes;}
    public long pollMs(){return pollMinutes*60_000L;}
    public long generation(){return generation;}
    public boolean dirty(){return dirty;}
    public ClimateConfiguration base(){return base;}
    public ClimateConfiguration configuration(){return new ClimateConfiguration(base.revision,pollMinutes,true,sensors);}
    public boolean bind(String identity){
        if(identity.equals(server))return false;
        boolean first=server.isEmpty();server=identity;base=new ClimateConfiguration(0,pollMinutes,false,Collections.emptyList());if(!first)dirty=false;generation++;save();return true;
    }
    private void edited(){dirty=true;generation++;save();}
    public void add(String address,String name){
        ClimateSensor sensor=new ClimateSensor(address,name,null,Collections.emptyList());
        if(find(address)!=null)throw new IllegalArgumentException("Sensor already saved");
        if(sensors.size()>=ClimateSensor.MAX_SENSORS)throw new IllegalStateException("Three sensors already saved");
        sensors.add(sensor);edited();
    }
    public void rename(String address,String name){ClimateSensor old=find(address);if(old!=null){replace(old.renamed(name));edited();}}
    public void remove(String address){ClimateSensor sensor=find(address);if(sensor!=null){sensors.remove(sensor);edited();}}
    public void record(String address,ClimateReading reading){ClimateSensor sensor=find(address);if(sensor!=null){replace(sensor.measured(reading));save();}}
    private void replace(ClimateSensor sensor){for(int n=0;n<sensors.size();n++)if(sensors.get(n).address.equals(sensor.address)){sensors.set(n,sensor);return;}}
    /** Preserve the latest local samples even if they arrived while HTTP was in flight. */
    public boolean accept(ClimateConfiguration remote){
        if(!dirty&&base.revision==remote.revision&&pollMinutes==remote.pollMinutes&&configuration().sameSensors(remote))return false;
        applyCatalogue(remote);base=remote;pollMinutes=remote.pollMinutes;dirty=false;generation++;save();return true;
    }
    private void applyCatalogue(ClimateConfiguration remote){
        List<ClimateSensor> replacement=new ArrayList<>();
        for(ClimateSensor configured:remote.sensors){ClimateSensor old=find(configured.address);replacement.add(old==null?configured:old.renamed(configured.name));}
        sensors.clear();sensors.addAll(replacement);
    }
    /** A newer local edit survives acknowledgement of an older request from this tablet. */
    public void rebasePending(ClimateConfiguration remote,ClimateConfiguration merged){
        applyCatalogue(merged);base=remote;pollMinutes=remote.pollMinutes;dirty=!merged.sameSensors(remote);generation++;save();
    }
    /** Explicit conflict choice: retain local catalogue, rebase CAS and use the panel interval. */
    public void keepLocal(ClimateConfiguration remote){base=remote;pollMinutes=remote.pollMinutes;dirty=true;generation++;save();}
    private void save(){
        JSONArray values=new JSONArray();
        try{
            for(ClimateSensor sensor:sensors)values.put(sensor.toJson());
            preferences.edit().putString("sensors",values.toString()).putString("base",base.toJson().toString()).putInt("poll_minutes",pollMinutes).putBoolean("dirty",dirty).putString("binding_id",server).remove("server_id").apply();
        }catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
}
