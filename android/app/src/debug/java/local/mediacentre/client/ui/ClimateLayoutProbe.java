package local.mediacentre.client.ui;

import android.app.Activity;
import android.view.*;
import android.widget.TextView;
import java.util.*;
import java.io.*;
import org.json.JSONObject;
import local.mediacentre.client.R;
import local.mediacentre.client.climate.*;

/** Visual-only previews. Never edits the catalogue, samples, BLE state or sync preferences. */
public final class ClimateLayoutProbe {
    public static void run(Activity a,String mode)throws Exception{
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("climate");field.setAccessible(true);ClimateController climate=(ClimateController)field.get(a);
        List<ClimateSensor> actual=climate.store().sensors(),preview=new ArrayList<>();
        if("snapshot".equals(mode)){
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long latest=0;int samples=0;
            for(ClimateSensor sensor:actual){digest.update(sensor.address.getBytes("UTF-8"));digest.update((byte)0);digest.update(sensor.name.getBytes("UTF-8"));digest.update((byte)0);samples+=sensor.history.size();if(sensor.latest!=null)latest=Math.max(latest,sensor.latest.measuredAt);}
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));
            JSONObject report=new JSONObject().put("catalogue_sha256",hash.toString()).put("sensors",actual.size()).put("samples",samples).put("latest_measurement",latest).put("poll_minutes",climate.store().pollMinutes());
            File directory=a.getExternalFilesDir(null);if(directory==null)throw new IOException("No diagnostic directory");try(OutputStream out=new FileOutputStream(new File(directory,"climate-layout-snapshot.json"))){out.write(report.toString().getBytes("UTF-8"));}return;
        }
        PosterClimateView frame=find(a.findViewById(R.id.content));
        if(frame==null)throw new IllegalStateException("Open Neo Poster climate first");
        int count="empty".equals(mode)?0:"one".equals(mode)?1:"two".equals(mode)?2:3;
        if("missing".equals(mode))preview.add(new ClimateSensor("00:00:00:00:00:01","Длинное название комнаты без измерений",null,Collections.emptyList()));
        else for(int i=0;i<Math.min(count,actual.size());i++)preview.add(actual.get(i));
        frame.sensors(preview,address->{});frame.state(climate);frame.conflicting("conflict".equals(mode));
        ((TextView)a.findViewById(R.id.weather_service_status)).setText("ПРОВЕРКА КОМПОНОВКИ · сохранённые датчики и показания не менялись");
        android.util.Log.i("MediaCentreClimate","CLIMATE_LAYOUT_PREVIEW "+mode+" rooms="+preview.size());
    }
    private static PosterClimateView find(View view){if(view instanceof PosterClimateView)return (PosterClimateView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){PosterClimateView hit=find(group.getChildAt(n));if(hit!=null)return hit;}}return null;}
}
