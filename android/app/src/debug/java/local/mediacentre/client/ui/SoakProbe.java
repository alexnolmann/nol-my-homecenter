package local.mediacentre.client.ui;
import android.app.Activity;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;
import local.mediacentre.client.radio.*;
/** Explicit debug-only soak setup. Backup is private, atomic and deliberately excludes pairing. */
public final class SoakProbe {
    public static void run(Activity activity,String action)throws Exception{
        AtomicFile file=new AtomicFile(new File(activity.getFilesDir(),"h6-soak-settings.json"));
        SharedPreferences display=DisplaySettings.preferences(activity);
        if("begin".equals(action)){
            if(file.getBaseFile().exists()||new File(file.getBaseFile().getPath()+".bak").exists())throw new IOException("Restore existing soak settings first");
            JSONObject saved=new JSONObject().put("volume",RadioStore.volume(activity)).put("night",display.getBoolean("night",false)).put("keep_screen",display.getBoolean("keep_screen",true));
            FileOutputStream out=null;try{out=file.startWrite();out.write(saved.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(IOException error){if(out!=null)file.failWrite(out);throw error;}
            RadioPlaybackService.volume(activity,0);if(!display.edit().putBoolean("night",true).putBoolean("keep_screen",true).commit())throw new IOException("Display setup failed");
        }else if("restore".equals(action)){
            JSONObject saved;try(InputStream input=file.openRead()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[512];int n;while((n=input.read(block))!=-1){if(bytes.size()+n>4096)throw new IOException("Invalid backup");bytes.write(block,0,n);}saved=new JSONObject(bytes.toString("UTF-8"));}
            RadioPlaybackService.stop(activity);RadioPlaybackService.volume(activity,saved.getInt("volume"));
            if(!display.edit().putBoolean("night",saved.getBoolean("night")).putBoolean("keep_screen",saved.getBoolean("keep_screen")).commit())throw new IOException("Display restore failed");file.delete();
        }else throw new IllegalArgumentException("Unknown soak action");
    }
}
