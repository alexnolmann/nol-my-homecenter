package local.mediacentre.client.radio;

import android.content.Context;
import android.os.Build;

/** One effect for the selected player's actual session, shared by all visible waveform views. */
public final class AudioWaveform {
    private static final WaveformAudience audience=new WaveformAudience(new WaveformCapture(AndroidWaveformSource::open));
    private static int radioSession,musicSession;
    private AudioWaveform(){}
    public static boolean allowed(Context c){return Build.VERSION.SDK_INT<23||c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    public static void visible(Context c,Object owner,boolean visible){audience.visible(owner,visible&&allowed(c));sync();}
    public static boolean read(float[] output){sync();return audience.read(output);}
    static void radioSession(int session){radioSession=session;sync();}
    static void musicSession(int session){musicSession=session;sync();}
    private static void sync(){audience.target(AudioPlayback.music()?musicSession:radioSession);}
}
