package local.mediacentre.client.radio;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import local.mediacentre.client.music.MusicTrack;
import java.util.*;

/** Local A2DP control for legacy tablets. No discovery, pairing, Bluetooth toggles or permanent poll. */
@SuppressWarnings({"deprecation","MissingPermission"})
public final class AudioOutputController {
    private static volatile AudioOutputController instance;
    public static synchronized AudioOutputController get(Context c){
        if(instance==null)instance=new AudioOutputController(c.getApplicationContext());return instance;
    }
    /** Every explicit transport command revokes a pending automatic restart. */
    public static void playbackCommand(){
        AudioOutputController c=instance;if(c!=null){c.plan.cancelResume();}
    }
    /** Starting another stream mid-switch is deferred to a fresh tap after the route is ready. */
    public static boolean beforePlay(Context context){
        playbackCommand();AudioOutputController c=instance;
        if(c!=null&&c.busy()){c.handler.post(()->Toast.makeText(c.context,"Дождитесь переключения аудиовыхода",Toast.LENGTH_SHORT).show());return false;}
        return true;
    }
    private final Context context;
    private final SharedPreferences preferences;
    private final AudioManager audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final OutputSwitch plan=new OutputSwitch();
    private final Set<Runnable> listeners=new HashSet<>();
    private final Set<String> disconnectSent=new HashSet<>();
    private BluetoothAdapter adapter;
    private BluetoothA2dp a2dp;
    private boolean opened,registered,connectSent;
    private long backendEpoch,request;
    private BluetoothDevice destination;
    private Resume resume;
    private String error="";
    private final Runnable poll=this::advance;

    private AudioOutputController(Context c){
        context=c;preferences=c.getSharedPreferences("audio_output",0);
        audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
    }
    public static boolean legacySupported(){return Build.VERSION.SDK_INT>=19&&Build.VERSION.SDK_INT<28;}
    public boolean permitted(){return Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    public boolean busy(){return plan.busy();}
    public String error(){return error;}
    public String primary(){return preferences.getString("primary","");}
    public void primary(BluetoothDevice device){preferences.edit().putString("primary",device.getAddress()).apply();publish();}
    public String name(BluetoothDevice device){
        String alias=preferences.getString("alias_"+device.getAddress(),"");
        if(!alias.isEmpty())return alias;
        String name=device.getName();return name==null||name.trim().isEmpty()?"Bluetooth-колонка":name;
    }
    public void rename(BluetoothDevice device,String value){
        String name=value.trim();if(name.length()>40)name=name.substring(0,40);
        SharedPreferences.Editor e=preferences.edit();String key="alias_"+device.getAddress();
        if(name.isEmpty())e.remove(key);else e.putString(key,name);e.apply();publish();
    }
    public void listen(Runnable listener){listeners.add(listener);open();listener.run();}
    public void unlisten(Runnable listener){listeners.remove(listener);closeIfIdle();}
    public void refresh(){open();publish();}
    public boolean bluetoothOn(){try{return adapter!=null&&permitted()&&adapter.isEnabled();}catch(SecurityException denied){return false;}}
    public List<BluetoothDevice> devices(){
        List<BluetoothDevice> result=new ArrayList<>();
        if(adapter==null||!permitted())return result;
        try{
            for(BluetoothDevice d:adapter.getBondedDevices()){
                BluetoothClass kind=d.getBluetoothClass();
                boolean known=primary().equals(d.getAddress())||a2dp!=null&&a2dp.getConnectionState(d)==BluetoothProfile.STATE_CONNECTED;
                boolean audioDevice=kind!=null&&(kind.getMajorDeviceClass()==BluetoothClass.Device.Major.AUDIO_VIDEO||kind.hasService(BluetoothClass.Service.RENDER));
                if(d.getType()!=BluetoothDevice.DEVICE_TYPE_LE&&(known||audioDevice||kind==null))result.add(d);
            }
            Collections.sort(result,(a,b)->name(a).compareToIgnoreCase(name(b)));
        }catch(SecurityException denied){result.clear();}
        return result;
    }
    public BluetoothDevice primaryDevice(){for(BluetoothDevice d:devices())if(primary().equals(d.getAddress()))return d;return null;}
    public boolean connected(BluetoothDevice device){try{return a2dp!=null&&a2dp.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED;}catch(SecurityException denied){return false;}}
    public boolean routedBluetooth(){return audio!=null&&audio.isBluetoothA2dpOn();}
    public String label(){
        if(busy())return destination==null?"На динамики…":"Подключение…";
        if(routedBluetooth()){
            try{if(a2dp!=null)for(BluetoothDevice d:a2dp.getConnectedDevices())return name(d);}catch(SecurityException ignored){}
            return "Bluetooth";
        }
        if(audio!=null&&audio.isWiredHeadsetOn())return "Наушники";
        if(audio!=null&&audio.isBluetoothScoOn())return "Bluetooth-гарнитура";
        return "Динамики";
    }
    public void select(BluetoothDevice device){
        if(busy())return;
        open();
        if(!legacySupported()){fail("На этом Android подключите колонку через настройки Bluetooth");return;}
        if(!permitted()){fail("Разрешите доступ к Bluetooth");return;}
        if(device!=null&&(!bluetoothOn()||device.getBondState()!=BluetoothDevice.BOND_BONDED)){fail("Включите Bluetooth и спарьте колонку в настройках");return;}
        if(bluetoothOn()&&a2dp==null){fail("Bluetooth ещё готовится · повторите через секунду");return;}
        if(device!=null&&connected(device)&&routedBluetooth()&&!audio.isWiredHeadsetOn())return;
        if(device==null&&!routedBluetooth()&&(a2dp==null||a2dp.getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_DISCONNECTING}).isEmpty()))return;
        resume=new Resume(context);
        // Stop decoder/network synchronously before disconnecting a live route.
        if(resume.music)MusicPlaybackService.pause(context);else RadioPlaybackService.suspendForOutput();
        destination=device;disconnectSent.clear();connectSent=false;error="";
        request=plan.begin(SystemClock.elapsedRealtime(),resume.active);
        Log.i("MediaCentreOutput","OUTPUT_SWITCH_BEGIN bluetooth="+(device!=null)+" resume="+resume.active);
        publish();advance();
    }
    public void cancel(){
        if(!busy())return;plan.cancel();resume=null;handler.removeCallbacks(poll);error="Переключение отменено · звук остановлен";publish();closeIfIdle();
    }
    private void open(){
        if(opened||!permitted())return;
        adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null)return;
        opened=true;final long epoch=++backendEpoch;
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(events,filter,Context.RECEIVER_EXPORTED);
        else context.registerReceiver(events,filter);
        registered=true;
        adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
            @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
                if(!opened||epoch!=backendEpoch){adapter.closeProfileProxy(profile,proxy);return;}
                a2dp=(BluetoothA2dp)proxy;
                try{List<BluetoothDevice> connected=a2dp.getConnectedDevices();if(primary().isEmpty()&&connected.size()==1)primary(connected.get(0));}catch(SecurityException ignored){}
                publish();
            }
            @Override public void onServiceDisconnected(int profile){
                if(epoch!=backendEpoch)return;a2dp=null;if(busy())fail("Связь с Bluetooth потеряна · звук остановлен");else publish();
            }
        },BluetoothProfile.A2DP);
    }
    private final BroadcastReceiver events=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            String action=intent.getAction();
            if(Intent.ACTION_HEADSET_PLUG.equals(action)&&!isInitialStickyBroadcast())plan.cancelResume();
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)&&!bluetoothOn()&&busy()&&destination!=null){fail("Bluetooth выключен · звук остановлен");return;}
            // During an intentional A2DP disconnect the player is already released. No global noisy suppression.
            publish();
        }
    };
    private boolean invoke(String method,BluetoothDevice device){
        try{
            boolean accepted=(Boolean)BluetoothA2dp.class.getMethod(method,BluetoothDevice.class).invoke(a2dp,device);
            if(!accepted)fail("Не удалось "+("connect".equals(method)?"подключить колонку":"отключить колонку")+" · повторите попытку");
            return accepted;
        }catch(Exception unavailable){fail("Управление этой колонкой недоступно · откройте настройки Bluetooth");return false;}
    }
    private void advance(){
        handler.removeCallbacks(poll);if(!busy())return;
        long now=SystemClock.elapsedRealtime();boolean linkReady=false,routeReady=false;
        try{
            if(destination!=null&&!bluetoothOn()){fail("Bluetooth выключен · звук остановлен");return;}
            List<BluetoothDevice> changing=a2dp==null?Collections.emptyList():a2dp.getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_DISCONNECTING});
            boolean oldGone=true;
            for(BluetoothDevice d:changing){
                if(destination!=null&&d.equals(destination))continue;
                oldGone=false;
                if(a2dp.getConnectionState(d)!=BluetoothProfile.STATE_DISCONNECTING&&disconnectSent.add(d.getAddress()))if(!invoke("disconnect",d))return;
            }
            if(destination==null){
                linkReady=oldGone;routeReady=!routedBluetooth();
            }else if(a2dp!=null&&oldGone){
                int state=a2dp.getConnectionState(destination);
                if(state==BluetoothProfile.STATE_DISCONNECTED&&!connectSent){connectSent=true;if(!invoke("connect",destination))return;}
                linkReady=state==BluetoothProfile.STATE_CONNECTED;
                routeReady=routedBluetooth()&&(audio==null||!audio.isWiredHeadsetOn());
            }
            int result=plan.poll(request,now,linkReady,routeReady);
            if(result==OutputSwitch.TIMED_OUT){fail("Колонка или аудиовыход не ответили · звук остановлен");return;}
            if(result==OutputSwitch.READY_IDLE||result==OutputSwitch.READY_RESUME){
                Resume previous=resume;resume=null;error="";
                boolean restart=result==OutputSwitch.READY_RESUME&&previous!=null&&previous.matches(context)&&!audio.isMusicActive();
                Log.i("MediaCentreOutput","OUTPUT_SWITCH_READY bluetooth="+(destination!=null)+" resume="+restart);
                if(restart){if(previous.music)MusicPlaybackService.play(context);else RadioPlaybackService.play(context);}
                publish();closeIfIdle();return;
            }
        }catch(RuntimeException unavailable){fail("Не удалось переключить аудиовыход · звук остановлен");return;}
        handler.postDelayed(poll,250);
    }
    private void fail(String message){
        plan.cancel();resume=null;handler.removeCallbacks(poll);error=message;
        Log.i("MediaCentreOutput","OUTPUT_SWITCH_FAILED");Toast.makeText(context,message,Toast.LENGTH_LONG).show();publish();closeIfIdle();
    }
    private void publish(){for(Runnable listener:new ArrayList<>(listeners))listener.run();}
    private void closeIfIdle(){
        if(!listeners.isEmpty()||busy()||!opened)return;
        handler.removeCallbacks(poll);opened=false;backendEpoch++;
        if(registered){context.unregisterReceiver(events);registered=false;}
        if(a2dp!=null){adapter.closeProfileProxy(BluetoothProfile.A2DP,a2dp);a2dp=null;}
    }
    private static final class Resume {
        final boolean music,active;final MusicTrack track;final RadioStation station;
        Resume(Context c){music=MusicPlaybackService.selected();active=music?MusicPlaybackService.active():RadioPlaybackService.active()||RadioPlaybackService.starting();track=MusicPlaybackService.track();station=RadioStore.saved(c);}
        boolean matches(Context c){
            if(music!=MusicPlaybackService.selected())return false;
            if(music){MusicTrack current=MusicPlaybackService.track();return track!=null&&current!=null&&track.id.equals(current.id)&&track.url.equals(current.url);}
            RadioStation current=RadioStore.saved(c);return station!=null&&current!=null&&station.id.equals(current.id)&&station.url.equals(current.url);
        }
    }
}
