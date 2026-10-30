package local.mediacentre.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import org.json.JSONObject;
import java.net.URI;

/** Direct original-stream probe. No API request, proxy, URL rewrite or TLS override. */
public final class RadioProbeService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static final String STATUS_ACTION = "local.mediacentre.probe.RADIO_STATUS";
    private final Handler handler = new Handler();
    private MediaPlayer player;
    private WifiManager.WifiLock wifiLock;
    private AudioManager audio;
    private String stationId;
    private String streamUrl;
    private String caseName;
    private boolean desired;
    private int retrySeconds = 2;
    private final Runnable reconnect = this::connect;
    private final Runnable watchdog = () -> fail("prepare-timeout");
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                publish("HEARTBEAT", "playing=true position_ms=" + player.getCurrentPosition());
                handler.postDelayed(this, 2000);
            }
        }
    };

    private final BroadcastReceiver connectivity = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!desired) return;
            if (!online()) {
                releasePlayer();
                handler.removeCallbacks(reconnect);
                publish("WAITING_NETWORK", "playing=false");
            } else if (player == null) {
                handler.removeCallbacks(reconnect);
                handler.post(reconnect);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MediaCentreH0:radio");
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(connectivity, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(connectivity, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getStringExtra("h0_action");
        if ("wifi_off".equals(action) || "wifi_on".equals(action)) {
            // Diagnostic API-19 control only, invoked explicitly by the reversible ADB test.
            if (Build.VERSION.SDK_INT != 19) throw new IllegalStateException("Wi-Fi toggle fixture requires API 19.");
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            boolean accepted = wifi.setWifiEnabled("wifi_on".equals(action));
            publish("WIFI_TOGGLE", "action=" + action + " accepted=" + accepted);
            return START_NOT_STICKY;
        }
        if ("stop".equals(action)) {
            desired = false;
            releasePlayer();
            handler.removeCallbacksAndMessages(null);
            publish("STOPPED", "playing=false");
            stopSelf();
            return START_NOT_STICKY;
        }
        caseName = intent.getStringExtra("h0_case");
        try {
            SharedPreferences cache = getSharedPreferences("radio_probe", MODE_PRIVATE);
            if ("play_cached".equals(action)) {
                stationId = cache.getString("station_id", null);
                streamUrl = cache.getString("stream_url", null);
                if (stationId == null || streamUrl == null) throw new IllegalStateException("No cached station.");
                publish("CACHE_LOADED", "source=private-preferences");
            } else {
                JSONObject station = StationCatalog.find(this, intent.getStringExtra("h0_station"));
                stationId = station.getString("id");
                streamUrl = station.getString("stream_url");
                cache.edit().putString("station_id", stationId).putString("stream_url", streamUrl).apply();
            }
            String scheme = URI.create(streamUrl).getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) throw new IllegalArgumentException("Unsupported stream scheme.");
            desired = true;
            retrySeconds = 2;
            handler.removeCallbacksAndMessages(null);
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.createNotificationChannel(new NotificationChannel("h0_radio", "H0 radio", NotificationManager.IMPORTANCE_LOW));
                builder = new Notification.Builder(this, "h0_radio");
            } else {
                builder = new Notification.Builder(this);
            }
            Notification notification = builder
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Media Centre H0 — радио")
                    .setContentText(stationId).setOngoing(true).build();
            startForeground(19, notification);
            audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            connect();
        } catch (Exception error) {
            desired = false;
            releasePlayer();
            publish("ERROR", error.getClass().getSimpleName());
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private boolean online() {
        NetworkInfo network = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return network != null && network.isConnected();
    }

    private void connect() {
        if (!desired) return;
        releasePlayer();
        if (!online()) { publish("WAITING_NETWORK", "playing=false"); return; }
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setVolume(0.15f, 0.15f); // Local player gain only; system volume is unchanged.
            wifiLock.acquire();
            player.setOnPreparedListener(prepared -> {
                if (prepared != player || !desired) return;
                handler.removeCallbacks(watchdog);
                prepared.start();
                retrySeconds = 2;
                publish("PLAYING", "scheme=" + URI.create(streamUrl).getScheme() + " playing=" + prepared.isPlaying());
                handler.postDelayed(heartbeat, 2000);
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (failed == player) fail("media-error what=" + what + " extra=" + extra);
                return true;
            });
            player.setOnCompletionListener(completed -> { if (completed == player) fail("stream-ended"); });
            publish("PREPARING", "scheme=" + URI.create(streamUrl).getScheme());
            player.setDataSource(streamUrl); // Original public station URL; no API authorization headers.
            player.prepareAsync();
            handler.postDelayed(watchdog, 15000);
        } catch (Exception error) {
            fail(error.getClass().getSimpleName());
        }
    }

    private void fail(String reason) {
        releasePlayer();
        publish("ERROR", reason);
        if (desired) {
            handler.removeCallbacks(reconnect);
            handler.postDelayed(reconnect, retrySeconds * 1000L);
            retrySeconds = Math.min(30, retrySeconds * 2);
        }
    }

    private void releasePlayer() {
        handler.removeCallbacks(watchdog);
        handler.removeCallbacks(heartbeat);
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnErrorListener(null);
            player.setOnCompletionListener(null);
            player.release();
            player = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
    }

    private void publish(String state, String details) {
        String line = "H0_04_" + state + " case=" + caseName + " station=" + stationId + " " + details;
        Log.i("MediaCentreH0", line);
        getSharedPreferences("radio_probe", MODE_PRIVATE).edit().putString("state", state).putString("last_observation", line).apply();
        sendBroadcast(new Intent(STATUS_ACTION).setPackage(getPackageName()).putExtra("status", line));
    }

    @Override public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            desired = false;
            releasePlayer();
            handler.removeCallbacks(reconnect);
            publish("STOPPED", "audio-focus-loss");
            stopSelf();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player != null) {
            releasePlayer();
            publish("WAITING_FOCUS", "playing=false");
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && desired && player == null) {
            handler.post(reconnect);
        }
    }

    @Override public void onDestroy() {
        desired = false;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        audio.abandonAudioFocus(this);
        unregisterReceiver(connectivity);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
