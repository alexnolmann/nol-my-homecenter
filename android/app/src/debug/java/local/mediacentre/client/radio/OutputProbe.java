package local.mediacentre.client.radio;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
/** Debug-only hardware observations. No device addresses, keys or stream URLs. */
public final class OutputProbe {
    public static void run(Activity activity,String action)throws Exception{
        AudioOutputController output=AudioOutputController.get(activity);
        if("speakers".equals(action))output.select(null);
        else if("primary".equals(action)){BluetoothDevice primary=output.primaryDevice();if(primary==null)throw new IllegalStateException("No primary speaker");output.select(primary);}
        else if("cancel".equals(action))output.cancel();
        export(activity);
        if(!"snapshot".equals(action))new Handler().postDelayed(()->{try{export(activity);}catch(Exception error){Log.e("MediaCentreOutput","OUTPUT_EXPORT_ERROR",error);}},24000);
    }
    private static void export(Activity c)throws Exception{
        AudioOutputController output=AudioOutputController.get(c);
        JSONObject value=new JSONObject().put("busy",output.busy()).put("error",!output.error().isEmpty())
            .put("bluetooth_on",output.bluetoothOn()).put("bluetooth_route",output.routedBluetooth())
            .put("primary_saved",!output.primary().isEmpty()).put("radio",RadioPlaybackService.state())
            .put("radio_players",RadioPlaybackService.players()).put("music",MusicPlaybackService.state())
            .put("music_players",MusicPlaybackService.players()).put("music_position",MusicPlaybackService.position())
            .put("volume",RadioStore.volume(c)).put("audible_volume",RadioStore.audibleVolume(c))
            .put("radio_applied_volume",RadioPlaybackService.appliedVolume());
        File directory=c.getExternalFilesDir(null);if(directory==null)throw new IOException("No diagnostic directory");
        try(OutputStream out=new FileOutputStream(new File(directory,"audio-output-probe.json"))){out.write(value.toString().getBytes("UTF-8"));}
        Log.i("MediaCentreOutput","OUTPUT_PROBE "+value.toString());
    }
}
