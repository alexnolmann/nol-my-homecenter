package local.mediacentre.probe;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import org.json.JSONArray;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hardware compatibility probe, not the final wall-panel interface. */
public final class ProbeActivity extends Activity {
    private static final String TAG = "MediaCentreH0";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText endpoint;
    private TextView radioStatus;
    private JSONArray stations;
    private final BroadcastReceiver radioUpdates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            radioStatus.setText(intent.getStringExtra("status"));
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * metrics.density);
        layout.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Media Centre — проверка H0");
        title.setTextSize(28);
        layout.addView(title);

        TextView details = new TextView(this);
        details.setTextSize(20);
        details.setText("Нативные Java / Android Views\n\n"
                + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + "ABI: " + Build.CPU_ABI + "\n"
                + "Экран Activity: " + metrics.widthPixels + " × " + metrics.heightPixels
                + ", " + metrics.densityDpi + " dpi\n\n"
                + "APK установлен и Activity запущена.\n"
                + "Результаты HTTPS и прямого радио отображаются ниже.");
        layout.addView(details);
        endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setHint("https://адрес-сервера:8443/h0/probe.php");
        endpoint.setText(getPreferences(MODE_PRIVATE).getString("endpoint", ""));
        layout.addView(endpoint);
        Button test = new Button(this);
        test.setText("Проверить HTTPS / TLS 1.2");
        test.setOnClickListener(view -> checkHttps(endpoint.getText().toString(), true, "manual"));
        layout.addView(test);
        status = new TextView(this);
        status.setTextSize(18);
        layout.addView(status);
        try {
            stations = StationCatalog.load(this);
            String[] names = new String[stations.length()];
            for (int i = 0; i < stations.length(); i++) names[i] = stations.getJSONObject(i).getString("name");
            Spinner selection = new Spinner(this);
            selection.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
            layout.addView(selection);
            Button play = new Button(this);
            play.setText("Слушать исходный поток — тихий режим H0");
            play.setOnClickListener(view -> {
                try { radio("play", stations.getJSONObject(selection.getSelectedItemPosition()).getString("id")); }
                catch (Exception error) { radioStatus.setText(error.getMessage()); }
            });
            layout.addView(play);
            Button stop = new Button(this);
            stop.setText("Остановить радио");
            stop.setOnClickListener(view -> radio("stop", null));
            layout.addView(stop);
        } catch (Exception error) {
            Log.e(TAG, "Invalid H0 station catalogue", error);
        }
        radioStatus = new TextView(this);
        radioStatus.setTextSize(16);
        radioStatus.setText(getSharedPreferences("radio_probe", MODE_PRIVATE).getString("last_observation", "Радио остановлено"));
        layout.addView(radioStatus);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        IntentFilter radioFilter = new IntentFilter(RadioProbeService.STATUS_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(radioUpdates, radioFilter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(radioUpdates, radioFilter);
        Log.i(TAG, "H0_02_ACTIVITY_STARTED api=" + Build.VERSION.SDK_INT
                + " abi=" + Build.CPU_ABI);
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String url = intent.getStringExtra("h0_url");
        if (url != null) {
            endpoint.setText(url);
            checkHttps(url, !intent.getBooleanExtra("h0_system_trust", false),
                    intent.getStringExtra("h0_case"));
        }
    }

    private void radio(String action, String stationId) {
        Intent intent = new Intent(this, RadioProbeService.class).putExtra("h0_action", action)
                .putExtra("h0_station", stationId).putExtra("h0_case", "manual-radio");
        startService(intent);
    }

    private void checkHttps(String url, boolean localCa, String caseName) {
        getPreferences(MODE_PRIVATE).edit().putString("endpoint", url).apply();
        status.setText("Проверка HTTPS…");
        network.execute(() -> {
            String message;
            try {
                String result = TlsProbe.run(getApplicationContext(), url, localCa, caseName).toString();
                message = "HTTPS: " + result;
                Log.i(TAG, "H0_03_TLS_OK case=" + caseName + " " + result);
            } catch (Exception error) {
                message = error.getClass().getSimpleName() + ": " + error.getMessage();
                Log.i(TAG, "H0_03_TLS_REJECTED case=" + caseName + " " + message);
            }
            final String text = message;
            runOnUiThread(() -> { if (!isFinishing()) status.setText(text); });
        });
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        unregisterReceiver(radioUpdates);
        super.onDestroy();
    }
}
