package local.mediacentre.client.ui;
import android.content.*;
import android.os.Build;
import android.util.Log;
import local.mediacentre.client.presentation.DisplayPolicy;
/** Opens only the opted-in panel on compatible devices. There is no audio/service start path. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        if(!DisplayPolicy.bootAllowed(DisplaySettings.preferences(c).getBoolean("autostart",false),Build.VERSION.SDK_INT))return;
        try{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));Log.i("MediaCentreH5","H5_BOOT_INTERFACE_ONLY");}catch(RuntimeException unavailable){Log.i("MediaCentreH5","H5_BOOT_UNAVAILABLE");}
    }
}
