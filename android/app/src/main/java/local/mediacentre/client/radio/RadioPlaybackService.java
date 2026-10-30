package local.mediacentre.client.radio;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.util.Log;
import local.mediacentre.client.ui.MainActivity;

/** One player, independent of API/cache lifecycles. Callbacks are fenced by attempt and session. */
public final class RadioPlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static final String PLAY="radio.play",STOP="radio.stop",CHANNEL="radio";
    private static RadioPlaybackService running;
    private static long commandSequence;private static boolean playEnqueued;
    private static String state="stopped",stationName="";
    private static int appliedVolume=40;
    public static boolean waveformAllowed(Context c){return AudioWaveform.allowed(c);}
    private static void syncWaveform(){
        int session=0;
        if(running!=null&&running.player!=null&&"playing".equals(state))try{session=running.player.getAudioSessionId();}catch(IllegalStateException ignored){}
        AudioWaveform.radioSession(session);
    }
    private MediaPlayer player;private AudioManager audio;private PlaybackLocks locks;
    private final Handler handler=new Handler();private final RadioRetry retry=new RadioRetry();
    private int generation;private RadioStation station;private boolean foregroundActive;
    private Runnable timeout,pendingRetry,monitor;
    private long lastProgress,playingSince;private int lastPosition;
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()))finish("headphones_disconnected");}};
    public static void play(Context c){if(!AudioOutputController.beforePlay(c))return;MusicPlaybackService.stopForRadio();playEnqueued=true;Intent i=new Intent(c,RadioPlaybackService.class).setAction(PLAY).putExtra("sequence",++commandSequence);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    public static void stop(Context c){AudioOutputController.playbackCommand();long sequence=++commandSequence;if(running!=null||playEnqueued)c.startService(new Intent(c,RadioPlaybackService.class).setAction(STOP).putExtra("sequence",sequence));playEnqueued=false;}
    public static void suspendForOutput(){AudioOutputController.playbackCommand();commandSequence++;playEnqueued=false;if(running!=null)running.finish("stopped");}
    public static void stopForMusic(){AudioOutputController.playbackCommand();commandSequence++;playEnqueued=false;if(running!=null)running.finish("stopped");}
    public static String state(){return state;}
    public static boolean starting(){return playEnqueued;}
    public static String name(){return stationName;}
    public static String stationId(){return active()&&running!=null&&running.station!=null?running.station.id:null;}
    public static boolean active(){return state.equals("playing")||state.equals("connecting")||state.equals("retrying");}
    public static int appliedVolume(){return appliedVolume;}
    public static void volume(Context c,int value){MusicPlaybackService.volume(c,value);if(running!=null&&running.player!=null)try{running.player.setVolume(value/100f,value/100f);appliedVolume=value;}catch(IllegalStateException error){running.reconnect("error");}}
    public static int position(){try{return running!=null&&running.player!=null&&state.equals("playing")?running.player.getCurrentPosition():0;}catch(IllegalStateException error){return 0;}}
    public static int players(){return running!=null&&running.player!=null?1:0;}
    public static int retryCount(){return running==null?0:running.retry.failures();}
    public static boolean wakeHeld(){return running!=null&&running.locks!=null&&running.locks.wakeHeld();}
    public static boolean wifiHeld(){return running!=null&&running.locks!=null&&running.locks.wifiHeld();}
    public static boolean foregroundActive(){return running!=null&&running.foregroundActive;}
    public static boolean matchesSaved(Context c){RadioStation saved=RadioStore.saved(c);return running!=null&&same(running.station,saved);}
    private static boolean same(RadioStation a,RadioStation b){return a!=null&&b!=null&&a.id.equals(b.id)&&a.url.equals(b.url);}
    /** Selecting another card never lets an old preparation/retry start unexpected sound. */
    public static void selectionChanged(Context c){if(running!=null&&!same(running.station,RadioStore.saved(c))&&(state.equals("connecting")||state.equals("retrying")))running.finish("selection_changed");}
    @Override public void onCreate(){
        super.onCreate();running=this;audio=(AudioManager)getSystemService(AUDIO_SERVICE);locks=new PlaybackLocks(this);
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_EXPORTED);else registerReceiver(noisy,filter);
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int id){
        // A Stop can arrive before Service.onCreate; fence already queued Play intents too.
        if(i!=null&&i.hasExtra("sequence")&&i.getLongExtra("sequence",-1)!=commandSequence)return START_NOT_STICKY;
        if(i!=null&&!i.hasExtra("sequence")){AudioOutputController.playbackCommand();commandSequence++;}
        playEnqueued=false;
        if(i==null||!PLAY.equals(i.getAction())){finish("stopped");return START_NOT_STICKY;}
        RadioStation next=RadioStore.saved(this);
        if(next==null){foreground();finish("unavailable");return START_NOT_STICKY;}
        if(same(station,next)&&active())return START_NOT_STICKY;
        release();station=next;stationName=next.name;retry.start();state="connecting";foreground();
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){finish("focus_lost");return START_NOT_STICKY;}
        prepare();return START_NOT_STICKY;
    }
    private void prepare(){
        release();if(!retry.current(retry.epoch())||!same(station,RadioStore.saved(this))){finish("selection_changed");return;}
        state="connecting";foreground();final int request=++generation;final long session=retry.epoch();
        final MediaPlayer current=new MediaPlayer();player=current;
        try{
            locks.renew();current.setAudioStreamType(AudioManager.STREAM_MUSIC);
            appliedVolume=RadioStore.volume(this);current.setVolume(appliedVolume/100f,appliedVolume/100f);
            current.setOnPreparedListener(p->{if(!valid(request,session,p))return;handler.removeCallbacks(timeout);try{
                p.start();state="playing";syncWaveform();lastPosition=0;lastProgress=playingSince=SystemClock.elapsedRealtime();foreground();watch(request,session,current);Log.i("MediaCentreH4","H4_RADIO_PLAYING players=1");
            }catch(IllegalStateException error){reconnect("error");}});
            current.setOnErrorListener((p,what,extra)->{if(valid(request,session,p)){if(extra==MediaPlayer.MEDIA_ERROR_MALFORMED||extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED)finish("unavailable");else reconnect("error");}return true;});
            current.setOnCompletionListener(p->{if(valid(request,session,p))reconnect("ended");});
            // Only the original URL: no backend, API token, cookie or private trust manager.
            current.setDataSource(station.url);
            timeout=()->{if(valid(request,session,current))reconnect("timeout");};handler.postDelayed(timeout,20000);current.prepareAsync();
        }catch(Exception error){reconnect("error");}
    }
    private boolean valid(int attempt,long session,MediaPlayer p){return attempt==generation&&retry.current(session)&&p==player;}
    private void watch(int attempt,long session,MediaPlayer p){
        monitor=new Runnable(){public void run(){
            if(!valid(attempt,session,p)||!state.equals("playing"))return;
            long now=SystemClock.elapsedRealtime();int position=position();if(position!=lastPosition){lastPosition=position;lastProgress=now;}
            if(now-lastProgress>=20000){reconnect("stalled");return;}
            if(now-playingSince>=30000)retry.stable(session);
            locks.renew();handler.postDelayed(this,5000);
        }};handler.postDelayed(monitor,5000);
    }
    private void reconnect(String reason){
        long session=retry.epoch();release();
        if(!same(station,RadioStore.saved(this))){finish("selection_changed");return;}
        long delay=retry.failed(session);if(delay<0){finish("stopped");return;}
        state="retrying";foreground();final int request=generation;
        pendingRetry=()->{if(request==generation&&retry.current(session))prepare();};handler.postDelayed(pendingRetry,delay);
        Log.i("MediaCentreH6","H6_RADIO_RETRY reason="+reason+" delay_ms="+delay+" players=0 locks=false");
    }
    private void foreground(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Радио",NotificationManager.IMPORTANCE_LOW));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),flags);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RadioPlaybackService.class).setAction(STOP),flags);
        Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        String label=state.equals("playing")?"Прямой эфир":state.equals("retrying")?"Нет потока · повторное подключение":"Подключение…";
        n.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(stationName.isEmpty()?"Радио":stationName).setContentText(label).setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Остановить",stop);
        startForeground(406,n.build());foregroundActive=true;
    }
    private void release(){
        generation++;if(timeout!=null)handler.removeCallbacks(timeout);if(pendingRetry!=null)handler.removeCallbacks(pendingRetry);if(monitor!=null)handler.removeCallbacks(monitor);timeout=pendingRetry=monitor=null;
        AudioWaveform.radioSession(0); // Release the effect before its MediaPlayer, including route switches and retries.
        MediaPlayer old=player;player=null;if(old!=null){old.setOnPreparedListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.release();}if(locks!=null)locks.release();
    }
    private void finish(String result){retry.stop();release();if(audio!=null)audio.abandonAudioFocus(this);state=result;stopForeground(true);foregroundActive=false;stopSelf();Log.i("MediaCentreH4","H4_RADIO_STOP state="+result+" players=0");}
    // A focus loss, including a transient one, requires a fresh explicit Play.
    @Override public void onAudioFocusChange(int focus){if(focus<0)finish("focus_lost");}
    @Override public void onDestroy(){retry.stop();release();locks.close();unregisterReceiver(noisy);if(audio!=null)audio.abandonAudioFocus(this);if(running==this)running=null;if(active())state="stopped";foregroundActive=false;super.onDestroy();}
}
