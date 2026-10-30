package local.mediacentre.client.radio;
import android.content.Context;
import java.io.*;
import org.json.JSONObject;
/** Debug-only diagnostics contain neither URLs, names, registration nor provider responses. */
public final class RadioProbe {
    public static void export(Context c)throws Exception{JSONObject summary=new JSONObject().put("state",RadioPlaybackService.state()).put("players",RadioPlaybackService.players()).put("position_ms",RadioPlaybackService.position()).put("saved_station",RadioStore.saved(c)!=null).put("matches_saved",RadioPlaybackService.matchesSaved(c)).put("retries",RadioPlaybackService.retryCount()).put("wake_lock",RadioPlaybackService.wakeHeld()).put("wifi_lock",RadioPlaybackService.wifiHeld()).put("foreground",RadioPlaybackService.foregroundActive()).put("volume",RadioStore.volume(c)).put("applied_volume",RadioPlaybackService.appliedVolume());File d=c.getExternalFilesDir(null);if(d==null)throw new IOException("No diagnostics directory.");try(OutputStream out=new FileOutputStream(new File(d,"h4-radio-summary.json"))){out.write(summary.toString().getBytes("UTF-8"));}}
}
