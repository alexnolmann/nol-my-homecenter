package local.mediacentre.client.radio;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import java.util.concurrent.*;

/** Renewed only by a responsive player; an independent watchdog releases Wi-Fi too. */
final class PlaybackLocks {
    static final long LEASE_MS=60000;
    private final PowerManager.WakeLock wake;
    private final WifiManager.WifiLock wifi;
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> expiry;private long lease;
    PlaybackLocks(Context context){
        wake=((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MediaCentre:radio");wake.setReferenceCounted(false);
        WifiManager manager=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifi=manager==null?null:manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"MediaCentre:radio");if(wifi!=null)wifi.setReferenceCounted(false);
    }
    synchronized void renew(){
        // Do not briefly drop Wi-Fi into power saving on every renewal while asleep.
        lease++;if(expiry!=null)expiry.cancel(false);wake.acquire(LEASE_MS);if(wifi!=null&&!wifi.isHeld())wifi.acquire();
        final long expected=lease;expiry=watchdog.schedule(()->{synchronized(this){if(expected==lease)release();}},LEASE_MS,TimeUnit.MILLISECONDS);
    }
    synchronized void release(){lease++;if(expiry!=null){expiry.cancel(false);expiry=null;}if(wifi!=null&&wifi.isHeld())wifi.release();if(wake.isHeld())wake.release();}
    synchronized boolean wakeHeld(){return wake.isHeld();}
    synchronized boolean wifiHeld(){return wifi!=null&&wifi.isHeld();}
    synchronized void close(){release();watchdog.shutdownNow();}
}
