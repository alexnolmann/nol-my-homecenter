package local.mediacentre.client.radio;
import android.content.Context;
import local.mediacentre.client.music.MusicTrack;
/** Common footer/photo controls operate on whichever audio source the user selected. */
public final class AudioPlayback {
    public static boolean music(){return MusicPlaybackService.selected();}
    public static boolean active(){return music()?MusicPlaybackService.active():RadioPlaybackService.active();}
    public static String state(){return music()?MusicPlaybackService.state():RadioPlaybackService.state();}
    public static String name(Context c){if(music()){MusicTrack track=MusicPlaybackService.track();return track==null?"Выберите музыку":track.name;}RadioStation saved=RadioStore.saved(c);return RadioPlaybackService.active()?RadioPlaybackService.name():saved==null?"Выберите радиостанцию":saved.name;}
    public static void toggle(Context c){if(music()){if(active())MusicPlaybackService.pause(c);else MusicPlaybackService.play(c);}else{if(active())RadioPlaybackService.stop(c);else RadioPlaybackService.play(c);}}
    public static void volume(Context c,int value){RadioPlaybackService.volume(c,value);}
    /** App-only mute: keep the last audible level across screens and process restarts. */
    public static void toggleMute(Context c){volume(c,RadioStore.volume(c)>0?0:RadioStore.audibleVolume(c));}
    public static void stopAll(Context c){MusicPlaybackService.forget();RadioPlaybackService.stop(c);}
}
