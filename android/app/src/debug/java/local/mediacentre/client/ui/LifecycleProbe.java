package local.mediacentre.client.ui;
import android.app.Activity;
import android.os.Handler;
import java.io.*;
import java.lang.reflect.Field;
import org.json.JSONObject;
/** Debug only: real lifecycle actions, exporting no connection or station credentials. */
public final class LifecycleProbe {
    public static void run(Activity activity,String action)throws Exception{
        if("recreate".equals(action)){activity.recreate();return;}
        if("die".equals(action)){new Handler().postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),300);return;}
        JSONObject result=new JSONObject().put("pid",android.os.Process.myPid()).put("activity",System.identityHashCode(activity));
        for(String name:new String[]{"screen","query","genre","stationOffset","favoritesOnly"}){
            Field field=activity.getClass().getDeclaredField(name);field.setAccessible(true);result.put(name,field.get(activity));
        }
        File folder=activity.getExternalFilesDir(null);if(folder==null)throw new IOException("No diagnostics directory");
        try(OutputStream out=new FileOutputStream(new File(folder,"h6-lifecycle-summary.json"))){out.write(result.toString().getBytes("UTF-8"));}
    }
}
