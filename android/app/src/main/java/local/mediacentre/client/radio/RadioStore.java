package local.mediacentre.client.radio;
import android.content.Context;
import android.content.SharedPreferences;
public final class RadioStore {
    private RadioStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("radio_station",Context.MODE_PRIVATE);}
    public static void remember(Context c,RadioStation s){prefs(c).edit().putString("id",s.id).putString("name",s.name).putString("url",s.url).apply();}
    public static RadioStation saved(Context c){SharedPreferences p=prefs(c);try{return new RadioStation(p.getString("id",null),p.getString("name",null),p.getString("url",null));}catch(IllegalArgumentException error){return null;}}
    public static void clear(Context c){prefs(c).edit().clear().apply();}
    public static int volume(Context c){return prefs(c).getInt("volume",40);}
    public static boolean hasVolume(Context c){return prefs(c).contains("volume");}
    public static int audibleVolume(Context c){return Math.max(1,Math.min(100,prefs(c).getInt("last_audible_volume",40)));}
    public static void volume(Context c,int value){
        if(value<0||value>100)throw new IllegalArgumentException("Volume outside range.");
        SharedPreferences p=prefs(c);int previous=volume(c);
        SharedPreferences.Editor edit=p.edit().putInt("volume",value);
        if(value>0||previous>0)edit.putInt("last_audible_volume",value>0?value:previous);
        edit.apply();
    }
}
