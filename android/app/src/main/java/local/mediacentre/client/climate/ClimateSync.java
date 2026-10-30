package local.mediacentre.client.climate;

import android.content.*;
import android.os.*;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import org.json.*;
import local.mediacentre.client.data.ApiClient;

/** Bounded foreground synchronization; no requests or timers while the screen is off. */
public final class ClimateSync {
    private final Context context;
    private final ClimateController climate;
    private final SharedPreferences connection;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile long epoch;
    private boolean started,running,closed;
    private String status="";
    private ClimateConfiguration conflict;
    private final Runnable timer=()->synchronize();
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        epoch++;ui.removeCallbacks(timer);
        if(Intent.ACTION_SCREEN_ON.equals(i.getAction())&&started)schedule(1000);
    }};
    public ClimateSync(Context context,ClimateController climate){
        this.context=context.getApplicationContext();this.climate=climate;connection=this.context.getSharedPreferences("connection",Context.MODE_PRIVATE);
    }
    public String status(){return status;}
    public boolean conflicting(){return conflict!=null;}
    public void start(){
        if(started||closed)return;started=true;
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(screen,filter,Context.RECEIVER_EXPORTED);else context.registerReceiver(screen,filter);
        schedule(1000);
    }
    public void stop(){if(!started)return;started=false;epoch++;ui.removeCallbacks(timer);context.unregisterReceiver(screen);}
    public void close(){stop();closed=true;worker.shutdownNow();}
    private boolean awake(){PowerManager p=(PowerManager)context.getSystemService(Context.POWER_SERVICE);return p!=null&&p.isScreenOn();}
    private void schedule(long delay){ui.removeCallbacks(timer);if(started&&awake()&&!closed)ui.postDelayed(timer,delay);}
    /** Local edits and BLE packets are coalesced, never one request per redraw. */
    public void changed(){schedule(2000);}
    public void resolve(boolean usePanel){
        if(conflict==null)return;ClimateConfiguration remote=conflict;conflict=null;
        if(usePanel)climate.store().accept(remote);else climate.store().keepLocal(remote);
        status="";climate.configurationChanged();schedule(1000);
    }
    private static JSONObject report(List<ClimateSensor> sensors,ClimateController climate,long revision)throws JSONException{
        JSONArray items=new JSONArray();
        boolean unavailable=BluetoothAccess.unavailable(climate.contextForSync())!=null;
        for(ClimateSensor s:sensors){
            JSONObject reading=null;if(s.latest!=null)reading=new JSONObject().put("temperature",s.latest.temperature).put("humidity",s.latest.humidity).put("battery_millivolts",s.latest.batteryMillivolts).put("measured_at",s.latest.measuredAt);
            String state=unavailable?"bluetooth_off":climate.error(s.address)!=null?"unavailable":s.latest==null?"pending":"ok";
            items.put(new JSONObject().put("address",s.address).put("latest",reading==null?JSONObject.NULL:reading).put("status",state));
        }
        return new JSONObject().put("revision",revision).put("sensors",items);
    }
    private void synchronize(){
        if(!started||!awake()||closed)return;
        if(running){schedule(5000);return;}
        String identity=connection.getString("server_id",""),baseUrl=connection.getString("base_url",""),token=connection.getString("token","");
        if(identity.isEmpty()||token.isEmpty()||!"database".equals(connection.getString("scenario","database"))){status="";schedule(60000);return;}
        if(climate.store().bind(ClimateConfiguration.binding(identity,baseUrl,token))){conflict=null;status="";}
        final ClimateConfiguration local=climate.store().configuration(),baseline=climate.store().base();
        final boolean dirty=climate.store().dirty();final long generation=climate.store().generation(),requestEpoch=epoch;
        final JSONObject samples;
        try{samples=report(climate.store().sensors(),climate,baseline.revision);}
        catch(JSONException invalid){schedule(60000);return;}
        running=true;
        worker.execute(()->{
            ClimateConfiguration result=null,conflicting=null;String message=null;
            try{
                check(requestEpoch);ApiClient api=new ApiClient(context,baseUrl,token,identity);
                ClimateConfiguration remote=ClimateConfiguration.fromJson(api.climate(null).getJSONObject("data"));
                check(requestEpoch);
                ClimateConfiguration wanted;
                if(!remote.initialized)wanted=new ClimateConfiguration(remote.revision,remote.pollMinutes,true,local.sensors);
                else if(baseline.revision==0&&!dirty)wanted=remote;
                else if(dirty){
                    try{wanted=ClimateConfiguration.merge(baseline,local,remote);}
                    catch(IllegalStateException collision){conflicting=remote;throw collision;}
                }else wanted=remote;
                if(!remote.initialized||!wanted.sameSensors(remote)){
                    check(requestEpoch);result=ClimateConfiguration.fromJson(api.climate(wanted.request(remote.revision,remote.pollMinutes)).getJSONObject("data"));
                }else result=remote;
                // Only report entries which belong to the server's current catalogue.
                JSONArray filtered=new JSONArray(),previous=samples.getJSONArray("sensors");
                for(ClimateSensor s:result.sensors)for(int n=0;n<previous.length();n++)if(s.address.equals(previous.getJSONObject(n).getString("address")))filtered.put(previous.getJSONObject(n));
                check(requestEpoch);api.climateReport(new JSONObject().put("revision",result.revision).put("sensors",filtered));
                Log.i("MediaCentreClimate","CLIMATE_SYNC_OK sensors="+result.sensors.size()+" interval="+result.pollMinutes);
            }catch(Exception error){
                if(conflicting!=null)message="Настройки изменены в двух местах · выберите вариант";
                else if(error instanceof ApiClient.HttpError){
                    int code=((ApiClient.HttpError)error).code;
                    message=code==409?"Настройки изменились · повторная синхронизация":code==401?"Нет доступа к серверу · проверьте сопряжение":code==403?"Нет прав на синхронизацию климата":code==404?"Обновите сервер для синхронизации климата":"Панель недоступна · Bluetooth работает автономно";
                }else message="Панель недоступна · Bluetooth работает автономно";
                Log.i("MediaCentreClimate","CLIMATE_SYNC_UNAVAILABLE type="+error.getClass().getSimpleName());
            }
            final ClimateConfiguration accepted=result,pending=conflicting;final String finalStatus=message;
            ui.post(()->{
                running=false;
                if(closed)return;
                boolean locallyEdited=generation!=climate.store().generation();
                if(started&&awake()&&epoch==requestEpoch&&identity.equals(connection.getString("server_id",""))&&token.equals(connection.getString("token",""))&&baseUrl.equals(connection.getString("base_url",""))){
                    if(generation==climate.store().generation()){
                        if(accepted!=null){boolean changed=climate.store().accept(accepted);conflict=null;status=finalStatus==null?"":finalStatus;if(changed)climate.configurationChanged();else climate.synchronizationChanged();}
                        else if(pending!=null){conflict=pending;status=finalStatus;climate.synchronizationChanged();}
                        else{status=finalStatus;climate.synchronizationChanged();}
                    }else if(accepted!=null){
                        try{
                            ClimateConfiguration merged=ClimateConfiguration.merge(local,climate.store().configuration(),accepted);
                            climate.store().rebasePending(accepted,merged);conflict=null;status=finalStatus==null?"":finalStatus;climate.configurationChanged();
                        }catch(IllegalStateException collision){conflict=accepted;status="Настройки изменены в двух местах · выберите вариант";climate.synchronizationChanged();}
                    }
                }
                schedule(locallyEdited?2000:60000);
            });
        });
    }
    private void check(long expected)throws IOException{if(epoch!=expected||!awake()||Thread.currentThread().isInterrupted())throw new IOException("Sync stopped");}
}
