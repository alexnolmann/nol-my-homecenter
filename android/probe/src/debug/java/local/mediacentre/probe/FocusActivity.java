package local.mediacentre.probe;
import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.*;
import android.util.Log;
import android.widget.TextView;

/** Requests genuine competing audio focus, without playing any sound. */
public final class FocusActivity extends Activity implements AudioManager.OnAudioFocusChangeListener {
    private AudioManager audio;private final Handler handler=new Handler();
    @Override public void onCreate(Bundle state){super.onCreate(state);audio=(AudioManager)getSystemService(AUDIO_SERVICE);TextView text=new TextView(this);text.setTextSize(24);text.setText("H6: другое приложение удерживает аудиофокус 10 секунд. Звук не воспроизводится.");setContentView(text);acquire();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acquire();}
    private void acquire(){int type=getIntent().getBooleanExtra("transient",false)?AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:AudioManager.AUDIOFOCUS_GAIN;int result=audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,type);Log.i("MediaCentreH6","H6_COMPETING_FOCUS result="+result);handler.removeCallbacksAndMessages(null);handler.postDelayed(this::finish,10000);}
    public void onAudioFocusChange(int change){}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);audio.abandonAudioFocus(this);super.onDestroy();}
}
