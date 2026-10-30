package local.mediacentre.client.radio;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import local.mediacentre.client.data.ApiClient;
import local.mediacentre.client.music.*;
import local.mediacentre.client.ui.MainActivity;
import java.util.*;
/** Music and radio share volume and audio ownership; pause releases decoder, network and locks. */
public final class MusicPlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static MusicPlaybackService running;
    private static long commands;private static boolean enqueued,selected;
    private static String state="stopped";private static int savedPosition,duration;
    private static List<MusicTrack> tracks=Collections.emptyList();
    private static final MusicQueue queue=new MusicQueue();
    private MediaPlayer player;private MusicStreamBridge bridge;private PlaybackLocks locks;private AudioManager audio;
    private final Handler handler=new Handler();private int epoch,failures;private boolean foreground;
    private long progressed,playingSince;private int lastPosition;
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pauseNow("paused");}};
    public static boolean selected(){return selected;}
    public static boolean active(){return enqueued||"playing".equals(state)||"connecting".equals(state)||"retrying".equals(state);}
    public static String state(){return state;}
    public static MusicTrack track(){int index=queue.current();return index<0||index>=tracks.size()?null:tracks.get(index);}
    public static int position(){if(running!=null&&running.player!=null&&"playing".equals(state))try{return running.player.getCurrentPosition();}catch(IllegalStateException ignored){}return savedPosition;}
    public static int duration(){return duration;}
    public static int players(){return running!=null&&running.player!=null?1:0;}
    public static boolean wakeHeld(){return running!=null&&running.locks!=null&&running.locks.wakeHeld();}
    public static boolean wifiHeld(){return running!=null&&running.locks!=null&&running.locks.wifiHeld();}
    public static boolean shuffled(){return queue.shuffled();}
    public static int repeat(){return queue.repeat();}
    public static void shuffle(Context c){queue.shuffle(!queue.shuffled());c.getSharedPreferences("music",0).edit().putBoolean("shuffle",queue.shuffled()).apply();}
    public static void repeat(Context c){queue.repeat((queue.repeat()+1)%3);c.getSharedPreferences("music",0).edit().putInt("repeat",queue.repeat()).apply();}
    public static void load(Context c,List<MusicTrack> list,int index){
        if(!AudioOutputController.beforePlay(c))return;
        if(list.isEmpty()||list.size()>5000||index<0||index>=list.size())throw new IllegalArgumentException("Music queue size.");
        stopForRadio();selected=true;tracks=Collections.unmodifiableList(new ArrayList<>(list));
        SharedPreferences p=c.getSharedPreferences("music",0);queue.repeat(Math.max(0,Math.min(2,p.getInt("repeat",0))));queue.shuffle(p.getBoolean("shuffle",false));queue.set(list.size(),index);savedPosition=duration=0;play(c);
    }
    public static void play(Context c){
        if(!AudioOutputController.beforePlay(c)||track()==null)return;
        RadioPlaybackService.stopForMusic();selected=true;enqueued=true;
        Intent i=new Intent(c,MusicPlaybackService.class).setAction("play").putExtra("sequence",++commands);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    public static void pause(Context c){AudioOutputController.playbackCommand();commands++;enqueued=false;if(running!=null)running.pauseNow("paused");else state="paused";}
    public static void stop(Context c){AudioOutputController.playbackCommand();commands++;enqueued=false;savedPosition=0;duration=0;if(running!=null)running.pauseNow("stopped");else state="stopped";savedPosition=0;}
    public static void forget(){stopForRadio();tracks=Collections.emptyList();duration=0;}
    public static void stopForRadio(){AudioOutputController.playbackCommand();commands++;enqueued=false;if(running!=null)running.pauseNow("stopped");state="stopped";savedPosition=0;selected=false;}
    public static void next(Context c){AudioOutputController.playbackCommand();move(c,false,false);}
    public static void previous(Context c){AudioOutputController.playbackCommand();move(c,true,false);}
    private static void move(Context c,boolean previous,boolean completed){
        if(track()==null)return;int next=previous?queue.previous():queue.next(completed);
        if(next<0){stop(c);return;}savedPosition=duration=0;
        if(running!=null&&active()){running.release();running.failures=0;running.prepare();}else play(c);
    }
    public static void seek(Context c,int millis){
        AudioOutputController.playbackCommand();if(track()==null)return;savedPosition=Math.max(0,duration>0?Math.min(millis,duration):millis);
        if(active()){if(running!=null){running.release();running.failures=0;running.prepare();}}
    }
    public static void volume(Context c,int volume){RadioStore.volume(c,volume);if(running!=null&&running.player!=null)try{running.player.setVolume(volume/100f,volume/100f);}catch(IllegalStateException ignored){}}
    @Override public void onCreate(){super.onCreate();running=this;locks=new PlaybackLocks(this);audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_EXPORTED);else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&i.hasExtra("sequence")&&i.getLongExtra("sequence",-1)!=commands){if(player==null)stopSelf();return START_NOT_STICKY;}
        if(i!=null&&!i.hasExtra("sequence")){AudioOutputController.playbackCommand();commands++;}
        enqueued=false;if(i==null||!"play".equals(i.getAction())){pauseNow("paused");return START_NOT_STICKY;}
        selected=true;if("playing".equals(state)&&player!=null)return START_NOT_STICKY;
        state="connecting";notifyPlayer();
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){pauseNow("paused");return START_NOT_STICKY;}
        failures=0;prepare();return START_NOT_STICKY;
    }
    private void prepare(){
        release();final MusicTrack song=track();if(song==null){pauseNow("stopped");return;}
        state="connecting";notifyPlayer();final int attempt=epoch;
        try{
            SharedPreferences connection=getSharedPreferences("connection",0);final String base=connection.getString("base_url",""),token=connection.getString("token",""),identity=connection.getString("server_id",null);
            ApiClient api=new ApiClient(this,base,token,identity);
            bridge=new MusicStreamBridge(range->{
                if(!base.equals(connection.getString("base_url",""))||!token.equals(connection.getString("token",""))||!java.util.Objects.equals(identity,connection.getString("server_id",null)))throw new java.io.IOException("Music profile changed.");
                return api.openMusic(song.url,range);
            });final MediaPlayer p=new MediaPlayer();player=p;
            locks.renew();p.setAudioStreamType(AudioManager.STREAM_MUSIC);float volume=RadioStore.volume(this)/100f;p.setVolume(volume,volume);
            p.setOnPreparedListener(current->{if(!valid(attempt,p))return;try{duration=p.getDuration();if(savedPosition>0&&savedPosition<duration){p.setOnSeekCompleteListener(seek->{if(valid(attempt,p))start(attempt,p);});p.seekTo(savedPosition);}else start(attempt,p);}catch(IllegalStateException unavailable){retry();}});
            p.setOnErrorListener((current,what,extra)->{if(valid(attempt,p)){if(extra==MediaPlayer.MEDIA_ERROR_MALFORMED||extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED)pauseNow("unavailable");else retry();}return true;});
            p.setOnCompletionListener(current->{if(valid(attempt,p))move(this,false,true);});
            p.setDataSource(bridge.url());p.prepareAsync();
            handler.postDelayed(()->{if(valid(attempt,p)&&!"playing".equals(state))retry();},20000);
        }catch(Exception unavailable){retry();}
    }
    private boolean valid(int attempt,MediaPlayer p){return epoch==attempt&&player==p;}
    private void start(int attempt,MediaPlayer p){
        if(!valid(attempt,p))return;
        try{p.setOnSeekCompleteListener(null);p.start();state="playing";AudioWaveform.musicSession(p.getAudioSessionId());progressed=playingSince=SystemClock.elapsedRealtime();lastPosition=savedPosition;notifyPlayer();watch(attempt,p);}
        catch(IllegalStateException unavailable){retry();}
    }
    private void watch(int attempt,MediaPlayer p){handler.postDelayed(()->{
        if(!valid(attempt,p)||!"playing".equals(state))return;
        int now=position();if(now!=lastPosition){lastPosition=now;savedPosition=now;progressed=SystemClock.elapsedRealtime();}
        if(SystemClock.elapsedRealtime()-progressed>20000){retry();return;}if(SystemClock.elapsedRealtime()-playingSince>=30000)failures=0;locks.renew();watch(attempt,p);
    },5000);}
    private void retry(){
        savedPosition=position();release();if(++failures>3){pauseNow("unavailable");return;}state="retrying";notifyPlayer();final int attempt=epoch;
        handler.postDelayed(()->{if(epoch==attempt)prepare();},failures==1?1000:failures==2?3000:8000);
    }
    private void release(){epoch++;handler.removeCallbacksAndMessages(null);AudioWaveform.musicSession(0);MediaPlayer old=player;player=null;
        if(old!=null){old.setOnPreparedListener(null);old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnSeekCompleteListener(null);}
        if(locks!=null)locks.release();if(bridge!=null){bridge.close();bridge=null;}if(old!=null)old.release();
    }
    private void pauseNow(String result){savedPosition=position();release();state=result;enqueued=false;if(audio!=null)audio.abandonAudioFocus(this);stopForeground(true);foreground=false;stopSelf();}
    private void notifyPlayer(){
        NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);String channel="music";if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(channel,"Музыка",NotificationManager.IMPORTANCE_LOW));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class).putExtra("open_screen","music"),flags);
        PendingIntent pause=PendingIntent.getService(this,3,new Intent(this,MusicPlaybackService.class).setAction("pause"),flags);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        MusicTrack song=track();b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(song==null?"Музыка":song.name).setContentText("playing".equals(state)?song.artist:"Подключение…").setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Пауза",pause);startForeground(407,b.build());foreground=true;
    }
    @Override public void onAudioFocusChange(int focus){if(focus<0)pauseNow("paused");}
    @Override public void onDestroy(){release();locks.close();unregisterReceiver(noisy);if(audio!=null)audio.abandonAudioFocus(this);if(running==this){running=null;if(active())state="paused";}foreground=false;super.onDestroy();}
}
