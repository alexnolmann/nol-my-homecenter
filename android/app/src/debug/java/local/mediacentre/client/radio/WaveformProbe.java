package local.mediacentre.client.radio;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Bounded debug-only capture. Exports aggregate measurements, never audio samples or URLs. */
public final class WaveformProbe {
    private static Capture active;
    private static MediaPlayer player()throws Exception{
        Field service=RadioPlaybackService.class.getDeclaredField("running");service.setAccessible(true);
        Object owner=service.get(null);if(owner==null)return null;
        Field field=RadioPlaybackService.class.getDeclaredField("player");field.setAccessible(true);
        return (MediaPlayer)field.get(owner);
    }
    public static void run(Activity activity,String action)throws Exception{
        if("shared".equals(action)){shared(activity);return;}
        if(active!=null)active.finish("cancelled");
        if("stop".equals(action))return;
        if(!"speakers".equals(action)&&!"bluetooth".equals(action)&&!"repeat".equals(action))throw new IllegalArgumentException("Unknown test");
        Capture capture=new Capture(activity,action);active=capture;capture.start();
    }
    /** Observe the production capture without registering a viewer or opening another effect. */
    private static void shared(Activity activity){
        Handler handler=new Handler();float[] samples=new float[512];int[] counts=new int[4];float[] peak={0};int[] previous={0};
        handler.post(new Runnable(){public void run(){boolean received=AudioWaveform.read(samples);if(received){counts[1]++;int hash=Arrays.hashCode(samples);if(counts[1]>1&&hash!=previous[0])counts[2]++;previous[0]=hash;for(float value:samples)peak[0]=Math.max(peak[0],Math.abs(value));}if(++counts[0]<16){handler.postDelayed(this,75);return;}
            try{JSONObject stats=new JSONObject().put("result","complete").put("frames",counts[0]).put("received",counts[1]).put("changed",counts[2]).put("peak",peak[0]).put("source",AudioPlayback.music()?"music":"radio").put("state",AudioPlayback.state()).put("radio_players",RadioPlaybackService.players()).put("music_players",MusicPlaybackService.players());
                try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"shared-waveform-probe.json"))){out.write(stats.toString().getBytes("UTF-8"));}Log.i("MediaCentreWaveform","SHARED_WAVEFORM_PROBE "+stats);
            }catch(Exception error){Log.e("MediaCentreWaveform","SHARED_WAVEFORM_PROBE_ERROR",error);}
        }});
    }
    private static final class Capture implements Runnable{
        final Activity activity;final String label;final Handler handler=new Handler();
        MediaPlayer player;Visualizer visualizer;byte[] data;long started;
        int frames,nonSilent,changed,errors,previousHash,min=255,max,session;
        double squares;boolean previous,finished;
        Capture(Activity activity,String label){this.activity=activity;this.label=label;}
        void start(){
            try{
                player=WaveformProbe.player();
                if(player==null||!"playing".equals(RadioPlaybackService.state())){finish("not_playing");return;}
                session=player.getAudioSessionId();if(session<=0){finish("no_session");return;}
                visualizer=new Visualizer(session);
                int[] range=Visualizer.getCaptureSizeRange();int size=Math.min(512,range[1]);size=Math.max(size,range[0]);
                if(visualizer.setCaptureSize(size)!=Visualizer.SUCCESS||visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)!=Visualizer.SUCCESS||visualizer.setEnabled(true)!=Visualizer.SUCCESS){finish("setup_failed");return;}
                data=new byte[size];started=SystemClock.elapsedRealtime();
                Log.i("MediaCentreWaveform","WAVEFORM_PROBE_START "+label+" session="+session+" size="+size);
                handler.post(this);
            }catch(Exception failed){Log.w("MediaCentreWaveform","WAVEFORM_PROBE_SETUP "+failed.getClass().getSimpleName());finish("setup_exception");}
        }
        public void run(){
            if(finished)return;
            try{
                if(activity.isFinishing()||!activity.hasWindowFocus()){finish("hidden");return;}
                if(player!=WaveformProbe.player()||!"playing".equals(RadioPlaybackService.state())){finish("session_changed");return;}
                int result=visualizer.getWaveForm(data);
                if(result!=Visualizer.SUCCESS){errors++;finish("capture_failed");return;}
                int low=255,high=0;
                for(byte raw:data){int value=raw&255;low=Math.min(low,value);high=Math.max(high,value);double centered=value-128;squares+=centered*centered;}
                min=Math.min(min,low);max=Math.max(max,high);if(high-low>2)nonSilent++;
                int hash=Arrays.hashCode(data);if(previous&&hash!=previousHash)changed++;previous=true;previousHash=hash;frames++;
                if(SystemClock.elapsedRealtime()-started>=12000){finish("complete");return;}
                handler.postDelayed(this,50);
            }catch(Exception failed){errors++;finish("capture_exception");}
        }
        void finish(String reason){
            if(finished)return;finished=true;handler.removeCallbacks(this);
            if(visualizer!=null){try{visualizer.release();}catch(RuntimeException ignored){}visualizer=null;}
            if(active==this)active=null;
            try{
                JSONObject stats=new JSONObject().put("label",label).put("result",reason).put("frames",frames)
                    .put("non_silent_frames",nonSilent).put("changed_frames",changed).put("errors",errors)
                    .put("min",frames==0?128:min).put("max",frames==0?128:max).put("capture_size",data==null?0:data.length)
                    .put("rms",frames==0?0:Math.sqrt(squares/(frames*(double)data.length)))
                    .put("session",session).put("elapsed_ms",started==0?0:SystemClock.elapsedRealtime()-started)
                    .put("bluetooth_route",AudioOutputController.get(activity).routedBluetooth())
                    .put("radio",RadioPlaybackService.state()).put("volume",RadioStore.volume(activity))
                    .put("players",RadioPlaybackService.players()).put("retries",RadioPlaybackService.retryCount());
                File directory=activity.getExternalFilesDir(null);if(directory==null)throw new IOException();
                AtomicFile file=new AtomicFile(new File(directory,"waveform-probe.json"));FileOutputStream out=null;
                try{out=file.startWrite();out.write(stats.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(IOException error){if(out!=null)file.failWrite(out);throw error;}
                Log.i("MediaCentreWaveform","WAVEFORM_PROBE "+stats);
            }catch(Exception error){Log.e("MediaCentreWaveform","WAVEFORM_PROBE_EXPORT_FAILED",error);}
        }
    }
}
