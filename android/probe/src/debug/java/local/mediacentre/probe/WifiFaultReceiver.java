package local.mediacentre.probe;
import android.app.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Log;

/** API19 test only. A system alarm restores Wi-Fi even if the probe process exits. */
public final class WifiFaultReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){toggle(context,true);}
    static void toggle(Context context,boolean enabled){
        if(Build.VERSION.SDK_INT!=19)throw new IllegalStateException("API19 test only");
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent restore=PendingIntent.getBroadcast(context,603,new Intent(context,WifiFaultReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT);
        if(enabled)alarms.cancel(restore);else alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+180000,restore);
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean accepted=wifi.setWifiEnabled(enabled);Log.i("MediaCentreH6","H6_WIFI_"+(enabled?"ON":"OFF")+" accepted="+accepted);
    }
}
