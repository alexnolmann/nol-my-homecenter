package local.mediacentre.client.climate;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.*;

/**
 * One short GATT session at a time. All state transitions run on the main thread.
 * Foreground and screen-on only; no service, alarm, wakelock or autoConnect.
 */
public final class ClimateController {
    public static final long HISTORY_BUCKET_MS=5*60_000L,SCAN_MS=12_000L,SESSION_MS=35_000L;
    private static final UUID SERVICE=UUID.fromString("ebe0ccb0-7a0a-4b0c-8a1a-6ff2997da3a6");
    private static final UUID MEASUREMENT=UUID.fromString("ebe0ccc1-7a0a-4b0c-8a1a-6ff2997da3a6");
    private static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public interface Listener {void changed();}
    public static final class Nearby {
        public final String address;public final int rssi;
        Nearby(String address,int rssi){this.address=address;this.rssi=rssi;}
    }
    private final Context context;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ClimateStore store;
    private final Map<String,Long> attempted=new HashMap<>();
    private final Map<String,String> errors=new HashMap<>();
    private final LinkedHashMap<String,Nearby> found=new LinkedHashMap<>();
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private Listener listener;
    private Runnable localChanges;
    private boolean started,awake,scanning;
    private BluetoothAdapter scanningAdapter;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private int scanAttempts;
    private final Runnable scanStarter=()->beginScan();
    private BluetoothGatt gatt;
    private final Map<BluetoothGatt,Runnable> retiring=new HashMap<>();
    private String currentAddress;
    private String message="Показания сохраняются на планшете";
    private final Runnable scanTimeout=()->stopScan();
    private final Runnable sessionTimeout=()->finish(null,"Датчик не ответил · проверьте расстояние");
    private final Runnable next=()->connectNext();
    private final Runnable poll=new Runnable(){public void run(){
        if(!enabled())return;
        if(!busy())enqueue(false);
        ui.postDelayed(this,30_000);
    }};
    private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        String action=intent.getAction();
        if(Intent.ACTION_SCREEN_OFF.equals(action)){awake=false;cancelOperations();message="Опрос приостановлен, экран выключен";notifyChanged();}
        else if(Intent.ACTION_SCREEN_ON.equals(action)){awake=true;resume();}
        else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
            String unavailable=BluetoothAccess.unavailable(context);
            if(unavailable!=null){cancelOperations();message=unavailable;notifyChanged();}
            else resume();
        }
    }};
    public ClimateController(Context context){
        this.context=context.getApplicationContext();store=new ClimateStore(this.context);
    }
    public ClimateStore store(){return store;}
    Context contextForSync(){return context;}
    public void onLocalChange(Runnable callback){localChanges=callback;}
    private void localChanged(){if(localChanges!=null)localChanges.run();}
    public void synchronizationChanged(){notifyChanged();}
    public String intervalLabel(){return "Bluetooth · опрос раз в "+store.pollMinutes()+" мин · при включённом экране";}
    public void configurationChanged(){
        queue.retainAll(sensorAddresses());
        if(currentAddress!=null&&store.find(currentAddress)==null)closeGatt();
        errors.keySet().retainAll(sensorAddresses());attempted.keySet().retainAll(sensorAddresses());
        if(enabled()&&!scanning&&gatt==null&&!queue.isEmpty())scheduleNext();
        else if(enabled()&&!busy()&&BluetoothAccess.unavailable(context)==null)enqueue(false);
        notifyChanged();
    }
    private Set<String> sensorAddresses(){Set<String> addresses=new HashSet<>();for(ClimateSensor s:store.sensors())addresses.add(s.address);return addresses;}
    public void listen(Listener listener){this.listener=listener;}
    public boolean scanning(){return scanning;}
    public boolean busy(){return scanning||gatt!=null||!queue.isEmpty()||!retiring.isEmpty();}
    public String currentAddress(){return currentAddress;}
    public String message(){String unavailable=BluetoothAccess.unavailable(context);return unavailable==null?message:unavailable;}
    public String error(String address){return errors.get(address);}
    public List<Nearby> nearby(){return new ArrayList<>(found.values());}
    private boolean enabled(){return started&&awake;}
    private void notifyChanged(){if(listener!=null)listener.changed();}
    public void start(){
        if(started)return;started=true;
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);awake=power!=null&&power.isScreenOn();
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(changes,filter,Context.RECEIVER_EXPORTED);else context.registerReceiver(changes,filter);
        resume();
    }
    public void stop(){
        if(!started)return;started=false;context.unregisterReceiver(changes);cancelOperations();Log.i("MediaCentreClimate","CLIMATE_STOP idle="+!busy());
    }
    private void resume(){
        ui.removeCallbacks(poll);if(!enabled())return;message=intervalLabel();
        if(BluetoothAccess.unavailable(context)==null)enqueue(false);
        notifyChanged();ui.postDelayed(poll,30_000);
    }
    public void refresh(){
        if(!enabled()||busy())return;
        String unavailable=BluetoothAccess.unavailable(context);if(unavailable!=null){message=unavailable;notifyChanged();return;}
        enqueue(true);
    }
    private void enqueue(boolean force){
        if(!enabled()||busy()||BluetoothAccess.unavailable(context)!=null)return;
        long now=SystemClock.elapsedRealtime();
        for(ClimateSensor sensor:store.sensors()){
            Long last=attempted.get(sensor.address);
            if(force||ClimatePollPolicy.due(sensor.latest,last,now,System.currentTimeMillis(),store.pollMs()))queue.add(sensor.address);
        }
        if(!queue.isEmpty())connectNext();
    }
    public void add(String address,String name){store.add(address,name);errors.remove(address);localChanged();notifyChanged();if(!busy())refresh();}
    public void rename(String address,String name){store.rename(address,name);localChanged();notifyChanged();}
    public void remove(String address){
        store.remove(address);attempted.remove(address);errors.remove(address);queue.remove(address);
        if(address.equals(currentAddress))finish(null,null);localChanged();notifyChanged();
    }
    public void scan(){
        if(!enabled()||scanning)return;
        cancelOperations();found.clear();
        String unavailable=BluetoothAccess.unavailable(context);
        if(unavailable!=null){message=unavailable;notifyChanged();return;}
        if(!BluetoothAccess.locationEnabledForScan(context)){message="Для поиска включите геолокацию в Android";notifyChanged();return;}
        scanningAdapter=BluetoothAccess.adapter(context);scanning=true;message="Поиск Xiaomi LYWSD03MMC…";
        scanCallback=new BluetoothAdapter.LeScanCallback(){public void onLeScan(BluetoothDevice device,int rssi,byte[] record){
            final String address=device.getAddress();
            if(!LywsdAdvertisement.matches(device.getName(),record))return;
            ui.post(()->{if(!enabled()||!scanning||scanCallback!=this)return;if(!found.containsKey(address)){found.put(address,new Nearby(address,rssi));notifyChanged();}});
        }};
        scanAttempts=0;ui.postDelayed(scanTimeout,SCAN_MS);notifyChanged();beginScan();
    }
    private void beginScan(){
        if(!enabled()||!scanning)return;
        if(!retiring.isEmpty()){ui.postDelayed(scanStarter,100);return;}
        try{
            if(scanningAdapter.startLeScan(scanCallback))return;
            if(++scanAttempts<4){ui.postDelayed(scanStarter,750);return;}
        }catch(RuntimeException failed){/* Permission or adapter changes are also bounded. */}
        stopScan();message="Не удалось запустить поиск Bluetooth · повторите";notifyChanged();
    }
    public void stopScan(){
        ui.removeCallbacks(scanTimeout);ui.removeCallbacks(scanStarter);
        if(scanning&&scanningAdapter!=null&&scanCallback!=null)try{scanningAdapter.stopLeScan(scanCallback);}catch(RuntimeException ignored){}
        boolean wasScanning=scanning;scanning=false;scanCallback=null;scanningAdapter=null;
        if(wasScanning){message=found.isEmpty()?"Датчики не найдены · поднесите датчик ближе":"Найдено датчиков: "+found.size();notifyChanged();if(enabled()){ui.removeCallbacks(poll);ui.postDelayed(poll,30_000);}}
    }
    private void connectNext(){
        ui.removeCallbacks(next);
        if(!enabled()||scanning||gatt!=null)return;
        // Do not overlap a new connection with the previous KitKat client teardown.
        // The retirement callback schedules the queued connection once close() completes.
        if(!retiring.isEmpty())return;
        String unavailable=BluetoothAccess.unavailable(context);
        if(unavailable!=null){queue.clear();message=unavailable;notifyChanged();return;}
        do{currentAddress=queue.poll();}while(currentAddress!=null&&store.find(currentAddress)==null);
        if(currentAddress==null){notifyChanged();return;}
        attempted.put(currentAddress,SystemClock.elapsedRealtime());errors.remove(currentAddress);
        message="Получение показаний · "+store.find(currentAddress).name;
        try{
            BluetoothDevice device=BluetoothAccess.adapter(context).getRemoteDevice(currentAddress);
            gatt=device.connectGatt(context,false,new BluetoothGattCallback(){
                public void onConnectionStateChange(BluetoothGatt client,int status,int state){ui.post(()->{
                    if(client!=gatt){if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS)releaseSoon(client);return;}
                    if(status!=BluetoothGatt.GATT_SUCCESS){finish(null,"Нет связи с датчиком (Bluetooth "+status+")");return;}
                    if(state==BluetoothProfile.STATE_CONNECTED){if(!client.discoverServices())finish(null,"Не удалось прочитать службы датчика");}
                    else if(state==BluetoothProfile.STATE_DISCONNECTED)finish(null,"Нет связи с датчиком");
                });}
                public void onServicesDiscovered(BluetoothGatt client,int status){ui.post(()->{
                    if(client!=gatt)return;
                    BluetoothGattService service=client.getService(SERVICE);
                    BluetoothGattCharacteristic measurement=service==null?null:service.getCharacteristic(MEASUREMENT);
                    if(status!=BluetoothGatt.GATT_SUCCESS||measurement==null){finish(null,"Прошивка не предоставляет показания по Bluetooth");return;}
                    BluetoothGattDescriptor descriptor=measurement.getDescriptor(CCC);
                    if((measurement.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0&&descriptor!=null){
                        if(!client.setCharacteristicNotification(measurement,true)){finish(null,"Не удалось подписаться на показания");return;}
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        if(!client.writeDescriptor(descriptor))finish(null,"Не удалось включить показания");
                    }else if(!client.readCharacteristic(measurement))finish(null,"Не удалось прочитать показания");
                });}
                public void onDescriptorWrite(BluetoothGatt client,BluetoothGattDescriptor descriptor,int status){ui.post(()->{
                    if(client!=gatt)return;
                    if(status!=BluetoothGatt.GATT_SUCCESS){finish(null,"Датчик отклонил запрос показаний");return;}
                    BluetoothGattCharacteristic measurement=descriptor.getCharacteristic();
                    if((measurement.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)client.readCharacteristic(measurement);
                });}
                public void onCharacteristicChanged(BluetoothGatt client,BluetoothGattCharacteristic characteristic){
                    byte[] value=characteristic.getValue();byte[] copy=value==null?null:value.clone();
                    ui.post(()->receive(client,characteristic.getUuid(),copy));
                }
                public void onCharacteristicRead(BluetoothGatt client,BluetoothGattCharacteristic characteristic,int status){
                    byte[] value=characteristic.getValue();byte[] copy=value==null?null:value.clone();
                    ui.post(()->{if(client!=gatt)return;if(status==BluetoothGatt.GATT_SUCCESS)receive(client,characteristic.getUuid(),copy);});
                }
            });
            if(gatt==null){finish(null,"Не удалось подключиться к датчику");return;}
            ui.postDelayed(sessionTimeout,SESSION_MS);notifyChanged();
            Log.i("MediaCentreClimate","CLIMATE_SESSION_START");
        }catch(RuntimeException failed){finish(null,"Не удалось подключиться к датчику");}
    }
    private void receive(BluetoothGatt client,UUID characteristic,byte[] value){
        if(client!=gatt||!MEASUREMENT.equals(characteristic))return;
        try{finish(ClimateReading.decode(value,System.currentTimeMillis()),null);}
        catch(IllegalArgumentException malformed){finish(null,"Датчик прислал некорректные показания");}
    }
    private void closeGatt(){
        ui.removeCallbacks(sessionTimeout);
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){
            // KitKat nulls its callback in close(). Let the disconnect callback return first.
            Runnable release=()->{
                if(retiring.remove(old)==null)return;
                try{old.close();}catch(RuntimeException ignored){}
                Log.i("MediaCentreClimate","CLIMATE_SESSION_CLOSED");
                scheduleNext();
                notifyChanged();
            };
            retiring.put(old,release);ui.postDelayed(release,2000);
            try{old.disconnect();}catch(RuntimeException ignored){}
        }
        currentAddress=null;
    }
    private void releaseSoon(BluetoothGatt client){
        Runnable release=retiring.get(client);if(release!=null){ui.removeCallbacks(release);ui.postDelayed(release,100);}
    }
    private void finish(ClimateReading reading,String error){
        String address=currentAddress;
        if(reading!=null&&address!=null){store.record(address,reading);errors.remove(address);message="Показания обновлены";Log.i("MediaCentreClimate","CLIMATE_READING_OK temperature="+reading.temperature+" humidity="+reading.humidity+" millivolts="+reading.batteryMillivolts);}
        else if(error!=null){if(address!=null)errors.put(address,error);message=error;Log.i("MediaCentreClimate","CLIMATE_READING_UNAVAILABLE reason="+error);}
        closeGatt();if(reading!=null||error!=null)localChanged();notifyChanged();scheduleNext();
    }
    private void scheduleNext(){
        if(enabled()&&!scanning&&gatt==null&&retiring.isEmpty()&&!queue.isEmpty()){ui.removeCallbacks(next);ui.postDelayed(next,600);}
    }
    private void cancelOperations(){ui.removeCallbacks(poll);ui.removeCallbacks(next);queue.clear();stopScan();closeGatt();}
}
