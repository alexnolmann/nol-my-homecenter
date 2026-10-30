package local.mediacentre.client.radio;
import android.content.Context;
import java.io.*;
import org.json.JSONObject;

/** Exercise the real 60-second independent lease without any player renewing it. */
public final class RadioLockProbe {
    public static void run(Context context)throws Exception{
        PlaybackLocks locks=new PlaybackLocks(context);
        try{
            locks.renew();if(!locks.wakeHeld()||!locks.wifiHeld())throw new IOException("Lease not acquired");
            Thread.sleep(PlaybackLocks.LEASE_MS+2000);
            if(locks.wakeHeld()||locks.wifiHeld())throw new IOException("Lease watchdog did not release both locks");
            File directory=context.getExternalFilesDir(null);if(directory==null)throw new IOException("Diagnostics absent");
            try(OutputStream out=new FileOutputStream(new File(directory,"h6-locks-summary.json"))){out.write(new JSONObject().put("lease_ms",PlaybackLocks.LEASE_MS).put("wake_released",true).put("wifi_released",true).toString().getBytes("UTF-8"));}
        }finally{locks.close();}
    }
}
