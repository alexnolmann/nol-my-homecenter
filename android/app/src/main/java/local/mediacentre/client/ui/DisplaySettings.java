package local.mediacentre.client.ui;
import android.app.Activity;
import android.content.*;
import android.os.BatteryManager;
import android.view.WindowManager;
import java.util.*;
import local.mediacentre.client.presentation.DisplayPolicy;
public final class DisplaySettings {
    private DisplaySettings(){}
    public static SharedPreferences preferences(Context c){return c.getSharedPreferences("display",Context.MODE_PRIVATE);}
    public static int hour(SharedPreferences p,String key,int fallback){int h=p.getInt(key,fallback);return h>=0&&h<=23?h:fallback;}
    public static boolean isNight(Context c,String zone){SharedPreferences p=preferences(c);Calendar now=Calendar.getInstance(TimeZone.getTimeZone(zone));return p.getBoolean("night",false)&&DisplayPolicy.night(now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE),hour(p,"night_start",22),hour(p,"night_end",7));}
    public static int effective(Context c,String zone){SharedPreferences p=preferences(c);Calendar now=Calendar.getInstance(TimeZone.getTimeZone(zone));return DisplayPolicy.brightness(p.getInt("brightness",80),p.getInt("night_brightness",10),p.getBoolean("night",false),now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE),hour(p,"night_start",22),hour(p,"night_end",7));}
    static boolean externallyPowered(Intent battery){
        return battery!=null && (battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)&(BatteryManager.BATTERY_PLUGGED_AC|BatteryManager.BATTERY_PLUGGED_USB|BatteryManager.BATTERY_PLUGGED_WIRELESS))!=0;
    }
    static Intent battery(Context c){return c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
    static void applyKeepScreen(Activity a,boolean requested,Intent battery){
        // A USB cable connected to a sleeping PC does not imply external power.
        if(requested && externallyPowered(battery))a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    static void apply(Activity a,String zone){SharedPreferences p=preferences(a);WindowManager.LayoutParams params=a.getWindow().getAttributes();float target=effective(a,zone)/100f;if(params.screenBrightness!=target){params.screenBrightness=target;a.getWindow().setAttributes(params);}applyKeepScreen(a,p.getBoolean("keep_screen",true),battery(a));}
}
