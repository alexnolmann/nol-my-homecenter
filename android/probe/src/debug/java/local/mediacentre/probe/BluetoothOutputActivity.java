package local.mediacentre.probe;

import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.util.Log;
import android.widget.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/** Debug-only, manual compatibility test. Never discovers, pairs or toggles Bluetooth. */
public final class BluetoothOutputActivity extends Activity {
    private static final String TAG = "MediaCentreBtProbe";
    private final Handler handler = new Handler();
    private BluetoothAdapter adapter;
    private BluetoothA2dp a2dp;
    private BluetoothHeadset headset;
    private BluetoothDevice target;
    private TextView status;
    private boolean closed, registered;
    private String pending;
    private final Runnable timeout = () -> {
        if (pending != null) {
            report("TIMEOUT action=" + pending);
            pending = null;
            snapshot("timeout");
        }
    };
    private final BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (closed) { adapter.closeProfileProxy(profile, proxy); return; }
            if (profile == BluetoothProfile.A2DP) {
                a2dp = (BluetoothA2dp) proxy;
                List<BluetoothDevice> connected = a2dp.getConnectedDevices();
                // Test only the single currently connected speaker, never an arbitrary paired device.
                if (connected.size() == 1) target = connected.get(0);
            } else if (profile == BluetoothProfile.HEADSET) headset = (BluetoothHeadset) proxy;
            snapshot("profile_ready");
        }
        @Override public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) a2dp = null;
            else if (profile == BluetoothProfile.HEADSET) headset = null;
            if (!closed) snapshot("profile_lost");
        }
    };
    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (target != null && target.equals(device)) {
                    report("A2DP_EVENT state=" + state);
                    if (("connect".equals(pending) && state == BluetoothProfile.STATE_CONNECTED)
                            || ("disconnect".equals(pending) && state == BluetoothProfile.STATE_DISCONNECTED)) {
                        report("CONFIRMED action=" + pending);
                        pending = null;
                        handler.removeCallbacks(timeout);
                    }
                }
            }
            snapshot(intent.getAction());
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("Проверка Bluetooth-выхода");
        title.setTextSize(28);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(20);
        layout.addView(status);
        button(layout, "Встроенные динамики: отключить колонку", () -> command("disconnect"));
        button(layout, "Подключить ту же колонку", () -> command("connect"));
        button(layout, "Проверить состояние", () -> snapshot("manual"));
        button(layout, "Вернуться в медиацентр", () -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("local.mediacentre.client.dev");
            if (launch != null) startActivity(launch);
        });
        setContentView(layout);
        if (Build.VERSION.SDK_INT != 19) { report("Этот пробник предназначен для ASUS / API 19"); return; }
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { report("Bluetooth выключен или отсутствует"); return; }
        IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(events, filter);
        registered = true;
        adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP);
        adapter.getProfileProxy(this, listener, BluetoothProfile.HEADSET);
    }
    private void button(LinearLayout layout, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        layout.addView(button);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getStringExtra("bt_action");
        if ("connect".equals(action) || "disconnect".equals(action)) command(action);
        else snapshot("intent");
    }
    private void command(String action) {
        if (a2dp == null || target == null) { report("NOT_READY: нужна одна подключённая колонка при открытии"); return; }
        if (pending != null) { report("BUSY action=" + pending); return; }
        try {
            int wanted = "connect".equals(action) ? BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED;
            if (a2dp.getConnectionState(target) == wanted) { snapshot("already_" + action); return; }
            boolean accepted = (Boolean) BluetoothA2dp.class.getMethod(action, BluetoothDevice.class).invoke(a2dp, target);
            report("REQUEST action=" + action + " accepted=" + accepted);
            if (accepted) { pending = action; handler.postDelayed(timeout, 15000); }
            snapshot("request");
        } catch (Exception error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getCause() : error;
            report("ERROR action=" + action + " type=" + cause.getClass().getSimpleName());
        }
    }
    @SuppressWarnings("deprecation")
    private void snapshot(String reason) {
        if (closed) return;
        try {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            String value = "reason=" + reason + " bt=" + (adapter != null && adapter.isEnabled())
                    + " a2dp_route=" + audio.isBluetoothA2dpOn()
                    + " connected=" + (a2dp == null ? -1 : a2dp.getConnectedDevices().size())
                    + " target_state=" + (a2dp == null || target == null ? -1 : a2dp.getConnectionState(target))
                    + " hfp_connected=" + (headset == null ? -1 : headset.getConnectedDevices().size())
                    + " music_active=" + audio.isMusicActive();
            status.setText((target == null ? "Колонка пока не определена" : target.getName())
                    + "\n" + value + "\nСмена выхода может остановить радио. Запуск — в медиацентре.");
            Log.i(TAG, "SNAPSHOT " + value);
        } catch (RuntimeException error) { report("SNAPSHOT_ERROR " + error.getClass().getSimpleName()); }
    }
    private void report(String value) { status.setText(value); Log.i(TAG, value); }
    @Override protected void onDestroy() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        if (registered) unregisterReceiver(events);
        if (adapter != null && a2dp != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
        if (adapter != null && headset != null) adapter.closeProfileProxy(BluetoothProfile.HEADSET, headset);
        super.onDestroy();
    }
}
