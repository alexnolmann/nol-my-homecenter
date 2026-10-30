package local.mediacentre.client.ui;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.WindowManager;
import org.json.JSONObject;
import java.io.*;
/** Display diagnostics only; synthetic policy checks never change system battery state. */
public final class DisplayProbe {
    public static void verifyPowerPolicy(Activity a)throws Exception{
        String zone=DisplaySettings.preferences(a).getString("timezone","Europe/Moscow");
        try{
            // Exercise the real Android window through a disconnect/reconnect sequence.
            check(a,true,power(BatteryManager.BATTERY_PLUGGED_USB,BatteryManager.BATTERY_STATUS_CHARGING),true);
            check(a,true,power(0,BatteryManager.BATTERY_STATUS_DISCHARGING),false);
            check(a,true,power(BatteryManager.BATTERY_PLUGGED_AC,BatteryManager.BATTERY_STATUS_FULL),true);
            check(a,false,power(BatteryManager.BATTERY_PLUGGED_AC,BatteryManager.BATTERY_STATUS_CHARGING),false);
            check(a,true,power(BatteryManager.BATTERY_PLUGGED_WIRELESS,BatteryManager.BATTERY_STATUS_CHARGING),true);
            check(a,true,null,false);
            check(a,true,power(BatteryManager.BATTERY_PLUGGED_USB,BatteryManager.BATTERY_STATUS_CHARGING),true);
            check(a,true,new Intent(Intent.ACTION_BATTERY_CHANGED),false);
        }finally{DisplaySettings.apply(a,zone);}
        export(a);
    }
    private static Intent power(int plugged,int status){return new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,plugged).putExtra(BatteryManager.EXTRA_STATUS,status);}
    private static void check(Activity a,boolean requested,Intent battery,boolean expected){
        DisplaySettings.applyKeepScreen(a,requested,battery);
        if(((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)!=expected)throw new IllegalStateException("Unexpected keep-screen flag");
    }
    public static void export(Activity a)throws Exception{
        SharedPreferences p=DisplaySettings.preferences(a);String zone=p.getString("timezone","Europe/Moscow");JSONObject j=new JSONObject().put("brightness",p.getInt("brightness",80)).put("night_brightness",p.getInt("night_brightness",10)).put("night",p.getBoolean("night",false)).put("night_start",DisplaySettings.hour(p,"night_start",22)).put("night_end",DisplaySettings.hour(p,"night_end",7)).put("autostart",p.getBoolean("autostart",false)).put("keep_screen",p.getBoolean("keep_screen",true)).put("effective",DisplaySettings.effective(a,zone)).put("night_active",DisplaySettings.isNight(a,zone)).put("window_brightness",a.getWindow().getAttributes().screenBrightness).put("window_keep_screen",(a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0).put("system_brightness",Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,-1)).put("system_mode",Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,-1));
        j.put("external_power",DisplaySettings.externallyPowered(DisplaySettings.battery(a))).put("system_screen_timeout_ms",Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,-1));
        File dir=a.getExternalFilesDir(null);if(dir==null)throw new IOException("No diagnostic folder.");try(OutputStream out=new FileOutputStream(new File(dir,"h5-display-summary.json"))){out.write(j.toString().getBytes("UTF-8"));}
    }
}
