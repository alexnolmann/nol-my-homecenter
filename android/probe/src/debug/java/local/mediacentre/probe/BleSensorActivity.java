package local.mediacentre.probe;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.widget.*;
import java.util.*;
/** Debug-only isolated single-sensor test. Does not pair or persist device identifiers. */
@SuppressWarnings("deprecation")
public final class BleSensorActivity extends Activity {
    private static final String TAG="MediaCentreBleProbe";
    private static final UUID SERVICE=UUID.fromString("ebe0ccb0-7a0a-4b0c-8a1a-6ff2997da3a6");
    private static final UUID DATA=UUID.fromString("ebe0ccc1-7a0a-4b0c-8a1a-6ff2997da3a6");
    private static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Handler ui=new Handler();
    private BluetoothAdapter adapter;
    private BluetoothDevice target;
    private BluetoothGatt gatt;
    private BluetoothAdapter.LeScanCallback scan;
    private TextView status;
    private boolean visible;
    private long epoch;
    private int found;
    private final Runnable deadline=()->finish("TIMEOUT");
    private final Runnable scanDeadline=()->{stopScan();report("SCAN_DONE target_found="+(found>0));};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(30,30,30,30);
        TextView title=new TextView(this);title.setText("BLE: отдельная проверка датчика");title.setTextSize(28);box.addView(title);
        status=new TextView(this);status.setTextSize(20);box.addView(status);
        button(box,"Прямое подключение",()->connect(target));
        button(box,"Найти датчик, затем подключить",this::scanConnect);
        setContentView(box);
        if(Build.VERSION.SDK_INT!=19){report("API19_ONLY");return;}
        adapter=BluetoothAdapter.getDefaultAdapter();
        String address=getIntent().getStringExtra("sensor_address");
        if(adapter!=null&&BluetoothAdapter.checkBluetoothAddress(address))target=adapter.getRemoteDevice(address);
        report("READY target_valid="+(target!=null));
    }
    private void button(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());parent.addView(b);}
    @Override protected void onStart(){super.onStart();visible=true;}
    @Override protected void onStop(){visible=false;stopScan();finish("STOP");super.onStop();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(Build.VERSION.SDK_INT!=19||adapter==null)return;String action=intent.getStringExtra("ble_action");if("direct".equals(action))connect(target);else if("scan".equals(action))scanConnect();else if("reset".equals(action)){stopScan();finish("RESET_START");boolean accepted=adapter.disable();report("DISABLE accepted="+accepted);if(accepted)ui.postDelayed(()->report("ENABLE accepted="+adapter.enable()),4000);}else if("stop".equals(action)){stopScan();finish("MANUAL_STOP");}}
    private void report(String value){status.setText(value);Log.i(TAG,value);}
    private void scanConnect(){
        if(!visible||target==null||gatt!=null||scan!=null)return;
        found=0;report("SCAN_START");
        scan=new BluetoothAdapter.LeScanCallback(){public void onLeScan(BluetoothDevice device,int rssi,byte[] bytes){
            if(!device.getAddress().equals(target.getAddress()))return;
            ui.post(()->{if(scan!=this||!visible)return;found++;report("SCAN_TARGET rssi="+rssi+" type="+device.getType());stopScan();final long scannedEpoch=epoch;ui.postDelayed(()->{if(visible&&scannedEpoch==epoch)connect(device);},300);});
        }};
        boolean started=adapter.startLeScan(scan);report("SCAN_ACCEPTED "+started);
        if(started)ui.postDelayed(scanDeadline,12000);else stopScan();
    }
    private void stopScan(){ui.removeCallbacks(scanDeadline);if(scan!=null){adapter.stopLeScan(scan);scan=null;}}
    private void connect(BluetoothDevice device){
        if(!visible||device==null||gatt!=null||scan!=null){report("NOT_READY");return;}
        final long attempt=++epoch;report("CONNECT type="+device.getType());
        gatt=device.connectGatt(this,false,new BluetoothGattCallback(){
            private void main(BluetoothGatt client,Runnable action){ui.post(()->{if(visible&&attempt==epoch&&client==gatt)action.run();});}
            @Override public void onConnectionStateChange(BluetoothGatt client,int code,int state){main(client,()->{
                report("CONNECTION status="+code+" state="+state);
                if(code!=0){finish("ERROR_"+code);return;}
                if(state==BluetoothProfile.STATE_CONNECTED)report("DISCOVER accepted="+client.discoverServices());
                else if(state==BluetoothProfile.STATE_DISCONNECTED)finish("DISCONNECTED");
            });}
            @Override public void onServicesDiscovered(BluetoothGatt client,int code){main(client,()->{
                BluetoothGattService service=client.getService(SERVICE);BluetoothGattCharacteristic data=service==null?null:service.getCharacteristic(DATA);
                report("SERVICES status="+code+" measurement="+(data!=null));
                if(code!=0||data==null){finish("NO_MEASUREMENT");return;}
                BluetoothGattDescriptor descriptor=data.getDescriptor(CCC);
                if(descriptor!=null&&(data.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0){
                    boolean enabled=client.setCharacteristicNotification(data,true);descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    report("NOTIFY local="+enabled+" write="+client.writeDescriptor(descriptor));
                }else report("READ accepted="+client.readCharacteristic(data));
            });}
            @Override public void onDescriptorWrite(BluetoothGatt client,BluetoothGattDescriptor descriptor,int code){main(client,()->report("DESCRIPTOR status="+code));}
            private void receive(BluetoothGatt client,BluetoothGattCharacteristic characteristic){
                byte[] raw=characteristic.getValue();final byte[] bytes=raw==null?null:raw.clone();
                main(client,()->{if(!DATA.equals(characteristic.getUuid()))return;
                    if(bytes==null||bytes.length<5){finish("INVALID_READING");return;}
                    int temp=(short)((bytes[0]&255)|((bytes[1]&255)<<8)),humidity=bytes[2]&255,mv=(bytes[3]&255)|((bytes[4]&255)<<8);
                    finish("READING temperature="+temp/100.0+" humidity="+humidity+" millivolts="+mv);
                });
            }
            @Override public void onCharacteristicChanged(BluetoothGatt client,BluetoothGattCharacteristic characteristic){receive(client,characteristic);}
            @Override public void onCharacteristicRead(BluetoothGatt client,BluetoothGattCharacteristic characteristic,int code){if(code==0)receive(client,characteristic);else main(client,()->report("READ_ERROR "+code));}
        });
        if(gatt==null){finish("NULL_GATT");return;}ui.postDelayed(deadline,35000);
    }
    private void finish(String result){
        epoch++;ui.removeCallbacks(deadline);BluetoothGatt old=gatt;gatt=null;
        if(old!=null){old.disconnect();ui.postDelayed(()->{try{old.close();}catch(RuntimeException ignored){}},2000);}
        report(result);
    }
}
