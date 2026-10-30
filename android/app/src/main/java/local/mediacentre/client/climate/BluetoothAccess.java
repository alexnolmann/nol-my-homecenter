package local.mediacentre.client.climate;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

public final class BluetoothAccess {
    private BluetoothAccess(){}
    public static String[] permissions(){
        if(Build.VERSION.SDK_INT>=31)return new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT};
        if(Build.VERSION.SDK_INT>=23)return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        return new String[0];
    }
    public static boolean granted(Context context){
        if(Build.VERSION.SDK_INT>=23)for(String permission:permissions())if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)return false;
        return true;
    }
    public static void request(Activity activity,int code){if(Build.VERSION.SDK_INT>=23)activity.requestPermissions(permissions(),code);}
    public static BluetoothAdapter adapter(Context context){
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager==null?null:manager.getAdapter();
    }
    public static String unavailable(Context context){
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))return "Планшет не поддерживает Bluetooth LE";
        if(!granted(context))return "Разрешите доступ к Bluetooth";
        BluetoothAdapter adapter=adapter(context);if(adapter==null)return "Bluetooth недоступен";
        try{if(!adapter.isEnabled())return "Bluetooth выключен";}catch(SecurityException denied){return "Разрешите доступ к Bluetooth";}
        return null;
    }
    public static boolean locationEnabledForScan(Context context){
        if(Build.VERSION.SDK_INT<23||Build.VERSION.SDK_INT>=31)return true;
        LocationManager manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        return manager!=null&&(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)||manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }
}
