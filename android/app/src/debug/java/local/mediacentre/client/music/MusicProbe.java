package local.mediacentre.client.music;
import android.content.*;
import android.os.*;
import android.util.Log;
import local.mediacentre.client.data.ApiClient;
import local.mediacentre.client.radio.*;
import org.json.*;
import java.util.*;
import java.io.*;
/** Debug-only physical API19 verification; exports stay in ignored local artifacts. */
public final class MusicProbe {
 public static void run(Context context,String action)throws Exception{
  Context c=context.getApplicationContext();Handler ui=new Handler(Looper.getMainLooper());
  if("start".equals(action)){
   SharedPreferences p=c.getSharedPreferences("connection",0);ApiClient api=new ApiClient(c,p.getString("base_url",""),p.getString("token",""),p.getString("server_id",null));
   JSONArray libraries=new JSONObject(api.fetch("/api/v1/music/libraries",null).body).getJSONObject("data").getJSONArray("items");
   String library=libraries.getJSONObject(0).getString("id");
   JSONArray rows=new JSONObject(api.fetch("/api/v1/music/browse?library="+library+"&recursive=1&limit=50",null).body).getJSONObject("data").getJSONArray("items");List<MusicTrack> tracks=new ArrayList<>();for(int i=0;i<rows.length();i++)tracks.add(new MusicTrack(rows.getJSONObject(i)));
   ui.post(()->{SharedPreferences probe=c.getSharedPreferences("music-probe",0);if(!probe.contains("volume"))probe.edit().putInt("volume",RadioStore.volume(c)).commit();AudioPlayback.volume(c,0);MusicPlaybackService.load(c,tracks,0);ui.postDelayed(()->export(c,"start"),7000);});return;
  }
  ui.post(()->{
   if("missing".equals(action))try{MusicTrack current=MusicPlaybackService.track();if(current!=null){String[] parts=current.url.split("/");JSONObject row=new JSONObject().put("id",current.id).put("name",current.name).put("artist",current.artist).put("album",current.album).put("bytes",current.bytes).put("url",current.url.replace("/"+parts[6]+"/","/99999999999999/"));MusicPlaybackService.load(c,Collections.singletonList(new MusicTrack(row)),0);}}catch(Exception error){Log.e("MediaCentreMusic","MUSIC_MISSING_PROBE_ERROR",error);}
   if("pause".equals(action))MusicPlaybackService.pause(c);
   if("resume".equals(action))MusicPlaybackService.play(c);
   if("next".equals(action))MusicPlaybackService.next(c);
   if("previous".equals(action))MusicPlaybackService.previous(c);
   if("seek".equals(action))MusicPlaybackService.seek(c,Math.min(20000,MusicPlaybackService.duration()/2));
   if("shuffle".equals(action))MusicPlaybackService.shuffle(c);
   if("repeat".equals(action))MusicPlaybackService.repeat(c);
   if("rapid".equals(action)){MusicPlaybackService.play(c);MusicPlaybackService.pause(c);MusicPlaybackService.play(c);MusicPlaybackService.stop(c);}
   if("stop".equals(action)){MusicPlaybackService.stop(c);SharedPreferences probe=c.getSharedPreferences("music-probe",0);AudioPlayback.volume(c,probe.getInt("volume",40));probe.edit().clear().commit();}
   if("radio".equals(action))RadioPlaybackService.play(c);
   ui.postDelayed(()->export(c,action),action.equals("pause")||action.equals("stop")?1000:7000);
  });
 }
 private static void export(Context c,String action){
  try{JSONObject data=new JSONObject().put("theme",c.getSharedPreferences("display",0).getString("theme","auto")).put("action",action).put("state",MusicPlaybackService.state()).put("position",MusicPlaybackService.position()).put("duration",MusicPlaybackService.duration()).put("music_players",MusicPlaybackService.players()).put("radio_players",RadioPlaybackService.players()).put("radio_state",RadioPlaybackService.state()).put("wake",MusicPlaybackService.wakeHeld()).put("wifi",MusicPlaybackService.wifiHeld()).put("selected_music",MusicPlaybackService.selected()).put("shuffle",MusicPlaybackService.shuffled()).put("repeat",MusicPlaybackService.repeat()).put("track",MusicPlaybackService.track()==null?JSONObject.NULL:MusicPlaybackService.track().id);
   try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"music-probe.json"))){out.write(data.toString().getBytes("UTF-8"));}Log.i("MediaCentreMusic","MUSIC_PROBE "+data);
  }catch(Exception error){Log.e("MediaCentreMusic","MUSIC_PROBE_ERROR",error);}
 }
}
