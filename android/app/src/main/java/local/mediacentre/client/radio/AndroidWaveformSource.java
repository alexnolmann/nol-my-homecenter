package local.mediacentre.client.radio;

import android.media.audiofx.Visualizer;
import android.util.Log;

/** Captures this player's low-resolution mono waveform. Never uses the microphone or output mix. */
final class AndroidWaveformSource implements WaveformCapture.Source {
    private final Visualizer visualizer;
    private final int size;
    private int frames;
    private AndroidWaveformSource(Visualizer visualizer,int size){this.visualizer=visualizer;this.size=size;}
    static WaveformCapture.Source open(int session){
        if(session<=0)throw new IllegalArgumentException("A player session is required");
        Visualizer effect=null;
        try{
            effect=new Visualizer(session);
            int[] range=Visualizer.getCaptureSizeRange();int size=Math.max(range[0],Math.min(512,range[1]));
            if(effect.setCaptureSize(size)!=Visualizer.SUCCESS||
                effect.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)!=Visualizer.SUCCESS||
                effect.setEnabled(true)!=Visualizer.SUCCESS)throw new IllegalStateException("Visualizer setup failed");
            Log.i("MediaCentreWaveform","WAVEFORM_OPEN session="+session+" size="+size);
            return new AndroidWaveformSource(effect,size);
        }catch(RuntimeException|LinkageError failure){
            if(effect!=null)try{effect.release();}catch(RuntimeException ignored){}
            Log.w("MediaCentreWaveform","WAVEFORM_UNAVAILABLE "+failure.getClass().getSimpleName());
            throw new IllegalStateException("Waveform unavailable",failure);
        }
    }
    public int size(){return size;}
    public boolean read(byte[] data){
        int result=visualizer.getWaveForm(data);if(result==Visualizer.SUCCESS){frames++;return true;}
        Log.w("MediaCentreWaveform","WAVEFORM_READ_FAILED code="+result);return false;
    }
    public void close(){try{visualizer.release();}finally{Log.i("MediaCentreWaveform","WAVEFORM_CLOSE frames="+frames);}}
}
