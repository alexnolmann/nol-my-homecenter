package local.mediacentre.client.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.util.Log;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import local.mediacentre.client.BuildConfig;
import local.mediacentre.client.R;
import local.mediacentre.client.data.*;
import local.mediacentre.client.presentation.*;
import local.mediacentre.client.radio.*;
import local.mediacentre.client.climate.*;

public final class MainActivity extends Activity {
    private static final String[] SCREENS={"home","weather","climate","radio","stations","music","finance","photoframe","settings"};
    private static final String[] LABELS={"Главная","Погода","Климат","Радио","Станции","Музыка","Финансы","Фоторамка","Настройки"};
    private static final int PHOTO_SCREEN_INDEX=7;
    private final Handler handler=new Handler();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(8));
    private final long started=SystemClock.elapsedRealtime();
    private SharedPreferences preferences;
    private LinearLayout content,controls;
    private TextView heading,status,weatherServiceStatus,weatherSourceCredit;
    private TextView navigationTitle;
    private WeatherRefreshButton weatherRefresh;
    private ValueAnimator weatherSpin;
    private ValueAnimator financeSpin;
    private ValueAnimator climateSpin;
    private ClimateController climate;
    private ClimateSync climateSync;
    private ClimateScreen climateScreen;
    private MusicScreen musicScreen;
    private boolean waitingForWeather;
    private boolean waitingForFinance;
    private String lastWeatherFetch,weatherBeforeRefresh;
    private final Runnable weatherWaitTimeout=()->{if(waitingForWeather){finishWeatherWait();status.setText("Свежий прогноз пока не получен · попробуйте позже.");}};
    private final Runnable financeWaitTimeout=()->{if(waitingForFinance){finishFinanceWait();status.setText("Финансовые данные пока не обновились · попробуйте позже.");}};
    private String screen="home",scenario="success",query="",selectedStation;
    private int financeSharesScrollX;
    private boolean favoritesOnly=false,active=false;
    private int generation=0;
    private PanelRepository api;
    private Future<?> pending;
    private Runnable search;
    private TextView radioState;
    private RadioScreen radioScreen;
    private PanelStyle style;
    private AudioOutputControls audioOutput;
    private HomeScreen homeScreen;
    private ScreenModel homePaletteModel;
    private ScreenModel weatherPaletteModel;
    private ScreenModel radioPaletteModel;
    private ScreenModel financePaletteModel;
    private PosterWeatherView posterWeather;
    private PosterClimateView posterClimate;
    private PosterStationsView posterStations;
    private PosterFinanceView posterFinance;
    private PosterSettingsView posterSettings;
    private EditText stationSearch;
    private CheckBox stationFavorites;
    private int weatherForecastOffset;
    private int posterPreviousSystemUi=-1;
    private HomePhotoPreview homePhotoPreview;
    private PhotoFrameScreen photoFrameScreen;
    private boolean photoAvailable;
    private StationsScreen stationsScreen;
    private TextClock headerClock;
    private TextView miniLabel;
    private TextView miniBadge,miniVolume;
    private Button miniToggle;
    private Dtos.Catalogue radioFavorites;
    private static final int STATIONS_PAGE_SIZE=50;
    private String genre="";
    private final List<String> availableGenres=new ArrayList<>();
    private Button genreButton;
    private final Button[] navigationButtons=new Button[SCREENS.length];
    private String lastDataStatus="Данные ещё не загружены";
    private String lastSnapshot;
    private int writes=0;
    private long lastInteraction=0;
    private boolean firstCacheFrameLogged;
    private final BroadcastReceiver powerChanged=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(active)applyDisplay();}};
    private final Runnable refreshTimer=new Runnable(){public void run(){
        if(!active)return;
        if(api!=null && !screen.equals("settings") && !screen.equals("photoframe") && !screen.equals("climate") && !screen.equals("music") && search==null && writes==0 && (pending==null||pending.isDone()) && SystemClock.elapsedRealtime()-lastInteraction>=5000)load(true);
        handler.postDelayed(this,30000);
    }};
    private final Runnable displayTimer=new Runnable(){public void run(){if(!active)return;applyDisplay();handler.postDelayed(this,30000);}};
    private final Runnable playbackStatus=new Runnable(){public void run(){if(!active)return;audioOutput.refresh();if(radioState!=null)PanelStyle.update(radioState,playbackLabel());if(radioScreen!=null)radioScreen.tick();updateMiniPlayer();if(homeScreen!=null){homeScreen.tick();homeScreen.climate(climate.store().sensors());}if(stationsScreen!=null)stationsScreen.tick();if(climateScreen!=null)climateScreen.tick();if(musicScreen!=null)musicScreen.tick();handler.postDelayed(this,1000);}};
    private final Runnable heartbeat=new Runnable(){@Override public void run(){
        if(!active)return;
        final PanelRepository client=api;final String current=screen,currentScenario=scenario;
        if(client!=null)submit(()->{try{
            JSONObject body=new JSONObject();body.put("client_version",BuildConfig.VERSION_NAME);body.put("uptime_seconds",(SystemClock.elapsedRealtime()-started)/1000);body.put("current_screen",current);
            client.heartbeat(body,currentScenario);Log.i("MediaCentreH1","H1_HEARTBEAT_OK");
        }catch(Exception error){if(error instanceof ApiClient.HttpError && ((ApiClient.HttpError)error).code==401)handler.post(()->{if(active && api==client)requirePairing("Доступ отозван. Получите новый код в панели.");});Log.i("MediaCentreH1","H1_HEARTBEAT_UNAVAILABLE type="+error.getClass().getSimpleName()+(error instanceof ApiClient.HttpError?" http="+((ApiClient.HttpError)error).code:""));}});
        handler.postDelayed(this,60000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);setContentView(R.layout.activity_main);style=new PanelStyle(this);style.flat(findViewById(android.R.id.content),PanelStyle.BG);
        preferences=getSharedPreferences("connection",MODE_PRIVATE);
        climate=new ClimateController(this);climate.listen(()->{if(active&&"climate".equals(screen)&&climateScreen!=null)climateScreen.update();});
        climateSync=new ClimateSync(this,climate);climate.onLocalChange(()->climateSync.changed());
        photoAvailable=preferences.getBoolean("photoframe_enabled",false);
        selectedStation=preferences.getString("selected_station",null);
        content=(LinearLayout)findViewById(R.id.content);controls=(LinearLayout)findViewById(R.id.controls);
        heading=(TextView)findViewById(R.id.heading);status=(TextView)findViewById(R.id.status);weatherServiceStatus=(TextView)findViewById(R.id.weather_service_status);weatherSourceCredit=(TextView)findViewById(R.id.weather_source_credit);weatherRefresh=(WeatherRefreshButton)findViewById(R.id.weather_refresh);
        headerClock=(TextClock)findViewById(R.id.header_clock);style.ink(headerClock,PanelStyle.ACCENT);style.ink(heading,PanelStyle.TEXT);style.ink(weatherSourceCredit,PanelStyle.MUTED);style.status(status);style.status(weatherServiceStatus);status.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence v,int start,int count,int after){}public void onTextChanged(CharSequence v,int start,int before,int count){style.repaint(status);PanelStyle.update(weatherServiceStatus,v.toString());style.repaint(weatherServiceStatus);if("home".equals(screen)&&v.length()>0)rememberDiagnostic("home_data_status",v.toString());if("weather".equals(screen)&&v.length()>0)rememberDiagnostic("weather_data_status",v.toString());}public void afterTextChanged(Editable v){}});weatherRefresh.setAllCaps(false);weatherRefresh.setTextSize(28);weatherRefresh.setPadding(0,0,0,0);style.buttonStyle(weatherRefresh,false);weatherRefresh.setOnClickListener(v->{if("weather".equals(screen))refreshWeather();else if("finance".equals(screen))refreshFinance();else if("music".equals(screen)&&musicScreen!=null)musicScreen.reload();else if("climate".equals(screen)&&climateScreen!=null)climateScreen.ensureBluetooth(()->climate.refresh());});style.flat((android.view.View)content.getRootView(),PanelStyle.BG);
        LinearLayout navigation=(LinearLayout)findViewById(R.id.navigation);style.flat(navigation,PanelStyle.NAV);style.flat((android.view.View)navigation.getParent(),PanelStyle.BG);
        navigationTitle=style.title(preferences.getString("display_title","Мой дом"),24);navigationTitle.setMaxLines(2);navigationTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);navigationTitle.setContentDescription("navigation_title");navigation.addView(navigationTitle);style.gap(navigation,18);
        for(int i=0;i<SCREENS.length;i++){final String destination=SCREENS[i];Button button=style.navigationButton(LABELS[i],destination,()->navigate(destination));navigationButtons[i]=button;if(i==PHOTO_SCREEN_INDEX&&!photoAvailable)button.setVisibility(android.view.View.GONE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.bottomMargin=dp(6);navigation.addView(button,p);}
        audioOutput=(AudioOutputControls)findViewById(R.id.audio_output);
        buildMiniPlayer();
        applyChrome();
        applyDebugIntent(getIntent());
        if(state!=null){screen=state.getString("screen","home");query=state.getString("query","");selectedStation=state.getString("station");genre=state.getString("genre","");favoritesOnly=state.getBoolean("favorites_only",false);}
        openRequestedScreen(getIntent());
        connect();
    }
    private void openRequestedScreen(Intent intent){String requested=intent.getStringExtra("open_screen");if(requested!=null)for(String known:SCREENS)if(known.equals(requested)){screen=known;return;}}
    private void applyDebugIntent(Intent intent){
        if(!BuildConfig.DEBUG)return;
        SharedPreferences.Editor edit=preferences.edit();
        if(intent.hasExtra("base_url"))edit.putString("base_url",intent.getStringExtra("base_url"));
        if(intent.hasExtra("dev_token"))edit.putString("token",intent.getStringExtra("dev_token")).remove("server_id");
        if(intent.hasExtra("scenario"))edit.putString("scenario",intent.getStringExtra("scenario"));
        edit.apply();
        if(intent.hasExtra("screen")){String candidate=intent.getStringExtra("screen");for(String known:SCREENS)if(known.equals(candidate))screen=candidate;}
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(BuildConfig.DEBUG&&intent.hasExtra("photoframe_layout_probe")){
            try{Class.forName("local.mediacentre.client.ui.PhotoFrameLayoutProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("photoframe_layout_probe"));}catch(Exception error){Log.e("MediaCentrePhotoFrame","PHOTOFRAME_LAYOUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG&&intent.hasExtra("finance_layout_probe")){
            try{Class.forName("local.mediacentre.client.ui.FinanceLayoutProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("finance_layout_probe"));}catch(Exception error){Log.e("MediaCentreFinance","FINANCE_LAYOUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG&&intent.hasExtra("header_layout_probe")){
            try{Class.forName("local.mediacentre.client.ui.HeaderLayoutProbe").getMethod("snapshot",Activity.class).invoke(null,this);}catch(Exception error){Log.e("MediaCentreHeader","HEADER_LAYOUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG&&intent.hasExtra("stations_layout_probe")){
            try{Class.forName("local.mediacentre.client.ui.StationsLayoutProbe").getMethod("snapshot",Activity.class).invoke(null,this);}catch(Exception error){Log.e("MediaCentreStations","STATIONS_LAYOUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("climate_layout_probe")){
            try{Class.forName("local.mediacentre.client.ui.ClimateLayoutProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("climate_layout_probe"));}catch(Exception error){Log.e("MediaCentreClimate","CLIMATE_LAYOUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("design_probe")){
            String design=intent.getStringExtra("design_probe");if("classic".equals(design)||"neo_poster".equals(design)){DisplaySettings.preferences(this).edit().putString("design",design).apply();applyDisplay();Log.i("MediaCentreDesign","DESIGN_SET "+design);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("waveform_probe")){
            try{Class.forName("local.mediacentre.client.radio.WaveformProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("waveform_probe"));}catch(Exception error){Log.e("MediaCentreWaveform","WAVEFORM_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("output_probe")){
            try{Class.forName("local.mediacentre.client.radio.OutputProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("output_probe"));}catch(Exception error){Log.e("MediaCentreOutput","OUTPUT_PROBE_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("music_probe")){
            final String action=intent.getStringExtra("music_probe");submit(()->{try{Class.forName("local.mediacentre.client.music.MusicProbe").getMethod("run",Context.class,String.class).invoke(null,this,action);}catch(Exception error){Log.e("MediaCentreMusic","MUSIC_PROBE_LAUNCH_ERROR",error);}});return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("weather_direct_probe",false)){
            submit(()->{try{Class.forName("local.mediacentre.client.data.WeatherProbe").getMethod("run",android.content.Context.class).invoke(null,this);}
            catch(Exception error){Log.e("MediaCentreWeather","WEATHER_DIRECT_PROBE_LAUNCH_ERROR",error);}});return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("theme_probe")){
            try{Class.forName("local.mediacentre.client.ui.StyleProbe").getMethod("theme",Activity.class,String.class).invoke(null,this,intent.getStringExtra("theme_probe"));}catch(Exception error){Log.e("MediaCentreH7","H7_THEME_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("settings_draft_probe")){
            try{Class.forName("local.mediacentre.client.ui.SettingsDraftProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("settings_draft_probe"));Log.i("MediaCentreSettings","SETTINGS_DRAFT_OK "+intent.getStringExtra("settings_draft_probe"));}catch(Exception error){Log.e("MediaCentreSettings","SETTINGS_DRAFT_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("ui_audit_probe")){
            try{Class.forName("local.mediacentre.client.ui.UiAuditProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("ui_audit_probe"));}catch(Exception error){Log.e("MediaCentreAudit","UI_AUDIT_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("style_probe",false)){
            navigate("settings");try{Class.forName("local.mediacentre.client.ui.StyleProbe").getMethod("render",Activity.class).invoke(null,this);Log.i("MediaCentreH7","H7_COMPONENTS_OK");}catch(Exception error){Log.e("MediaCentreH7","H7_COMPONENTS_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("lifecycle_probe")){
            try{Class.forName("local.mediacentre.client.ui.LifecycleProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("lifecycle_probe"));}catch(Exception error){Log.i("MediaCentreH6","H6_LIFECYCLE_PROBE_ERROR");}return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("api_benchmark",false)){
            submit(()->{try{Class.forName("local.mediacentre.client.data.ApiBenchmark").getMethod("run",Context.class).invoke(null,this);Log.i("MediaCentreH6","H6_API_BENCHMARK_OK");}catch(Exception error){Log.i("MediaCentreH6","H6_API_BENCHMARK_ERROR");}});return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("soak_probe")){
            try{Class.forName("local.mediacentre.client.ui.SoakProbe").getMethod("run",Activity.class,String.class).invoke(null,this,intent.getStringExtra("soak_probe"));applyDisplay();Log.i("MediaCentreH6","H6_SOAK_SETTINGS_OK");}catch(Exception error){Log.i("MediaCentreH6","H6_SOAK_SETTINGS_ERROR");}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("profile_probe")){
            final String action=intent.getStringExtra("profile_probe");generation++;if(pending!=null)pending.cancel(true);active=false;handler.removeCallbacks(refreshTimer);handler.removeCallbacks(heartbeat);RadioPlaybackService.stop(this);
            submit(()->{boolean ok=false;try{Class.forName("local.mediacentre.client.data.ProfileProbe").getMethod("run",Context.class,String.class).invoke(null,this,action);ok=true;}catch(Exception error){Log.i("MediaCentreH5","H5_PROFILE_ERROR action="+action);}final boolean success=ok;handler.post(()->{if(isFinishing())return;active=true;selectedStation=preferences.getString("selected_station",null);connect();navigate("home");handler.postDelayed(refreshTimer,30000);handler.postDelayed(heartbeat,1000);if(success)Log.i("MediaCentreH5","H5_PROFILE_OK action="+action);});});return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("display_power_probe",false)){
            try{Class.forName("local.mediacentre.client.ui.DisplayProbe").getMethod("verifyPowerPolicy",Activity.class).invoke(null,this);Log.i("MediaCentrePower","POWER_POLICY_OK");}catch(Exception error){Log.e("MediaCentrePower","POWER_POLICY_ERROR",error);}return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("display_probe",false)){
            try{Class.forName("local.mediacentre.client.ui.DisplayProbe").getMethod("export",Activity.class).invoke(null,this);Log.i("MediaCentreH5","H5_DISPLAY_PROBE_OK");}catch(Exception error){Log.i("MediaCentreH5","H5_DISPLAY_PROBE_ERROR");}return;
        }
        if(BuildConfig.DEBUG && intent.hasExtra("radio_action")){
            String action=intent.getStringExtra("radio_action");
            if("play_stop".equals(action)||"play_stop_play".equals(action)){RadioPlaybackService.play(this);RadioPlaybackService.stop(this);if("play_stop_play".equals(action))RadioPlaybackService.play(this);}
            else if("play".equals(action))RadioPlaybackService.play(this);else RadioPlaybackService.stop(this);return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("radio_locks_probe",false)){
            submit(()->{try{Class.forName("local.mediacentre.client.radio.RadioLockProbe").getMethod("run",Context.class).invoke(null,this);Log.i("MediaCentreH6","H6_LOCK_LEASE_OK");}catch(Exception error){Log.i("MediaCentreH6","H6_LOCK_LEASE_ERROR");}});return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("radio_probe",false)){
            try{Class.forName("local.mediacentre.client.radio.RadioProbe").getMethod("export",Context.class).invoke(null,this);Log.i("MediaCentreH4","H4_RADIO_PROBE_OK");}catch(Exception error){Log.i("MediaCentreH4","H4_RADIO_PROBE_ERROR");}return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("cache_probe",false)){
            submit(()->{try{Class.forName("local.mediacentre.client.data.CacheProbe").getMethod("export",Context.class).invoke(null,this);Log.i("MediaCentreH1","H2_CACHE_PROBE_OK");}
            catch(Exception error){Log.i("MediaCentreH1","H2_CACHE_PROBE_ERROR");}});
            return;
        }
        if(BuildConfig.DEBUG && intent.getBooleanExtra("cache_recovery",false)){
            submit(()->{try{Class.forName("local.mediacentre.client.data.CacheProbe").getMethod("recovery",Context.class).invoke(null,this);Log.i("MediaCentreH6","H6_ATOMIC_CACHE_OK");}
            catch(Exception error){Log.i("MediaCentreH6","H6_ATOMIC_CACHE_ERROR");}});
            return;
        }
        setIntent(intent);applyDebugIntent(intent);openRequestedScreen(intent);connect();navigate(screen);
    }
    private void connect(){
        scenario=BuildConfig.DEBUG?preferences.getString("scenario","database"):"database";
        try{api=new PanelRepository(this,preferences.getString("base_url",""),preferences.getString("token",""),preferences.getString("server_id",null));}
        catch(Exception error){api=null;if(!"climate".equals(screen))screen="settings";}
    }
    @Override protected void onStart(){super.onStart();active=true;audioOutput.start();registerReceiver(powerChanged,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));navigate(screen);climate.start();climateSync.start();handler.postDelayed(heartbeat,1000);handler.post(playbackStatus);handler.post(displayTimer);handler.postDelayed(refreshTimer,30000);}
    @Override protected void onStop(){active=false;if(miniWaveform!=null)miniWaveform.setPlaying(false);if(radioScreen!=null)radioScreen.pause();audioOutput.stop();climateSync.stop();climate.stop();finishClimateSpin();if(climateScreen!=null){climateScreen.close();climateScreen=null;}if(musicScreen!=null){musicScreen.close();musicScreen=null;}if(homePhotoPreview!=null){homePhotoPreview.close();homePhotoPreview=null;}if(photoFrameScreen!=null){photoFrameScreen.close();photoFrameScreen=null;}finishWeatherWait();finishFinanceWait();unregisterReceiver(powerChanged);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);generation++;if(pending!=null)pending.cancel(true);handler.removeCallbacks(heartbeat);handler.removeCallbacks(playbackStatus);handler.removeCallbacks(displayTimer);handler.removeCallbacks(refreshTimer);if(search!=null)handler.removeCallbacks(search);search=null;super.onStop();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_DOWN||event.getAction()==MotionEvent.ACTION_MOVE)lastInteraction=SystemClock.elapsedRealtime();return super.dispatchTouchEvent(event);}
    @Override protected void onDestroy(){climateSync.close();executor.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(!"home".equals(screen))navigate("home");else super.onBackPressed();}
    @Override public void onSaveInstanceState(Bundle state){state.putString("screen",screen);state.putString("query",query);state.putString("station",selectedStation);state.putString("genre",genre);state.putBoolean("favorites_only",favoritesOnly);super.onSaveInstanceState(state);}
    private void navigate(String destination){
        restorePosterViews();
        finishWeatherWait();
        finishFinanceWait();
        finishClimateSpin();
        if("finance".equals(destination)&&!destination.equals(screen))financeSharesScrollX=0;
        if(!destination.equals(screen)){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(content.getWindowToken(),0);content.clearFocus();}
        screen=destination;generation++;if(pending!=null)pending.cancel(true);executor.purge();
        boolean homePage="home".equals(screen),radioPage="radio".equals(screen),photoPage="photoframe".equals(screen),refreshPage="weather".equals(screen)||"finance".equals(screen)||"climate".equals(screen)||"music".equals(screen),footerStatus=refreshPage&&!"weather".equals(screen)&&!"climate".equals(screen);status.setVisibility(homePage||radioPage||"stations".equals(screen)||refreshPage||photoPage?android.view.View.GONE:android.view.View.VISIBLE);findViewById(R.id.footer_status).setVisibility(footerStatus?android.view.View.VISIBLE:android.view.View.GONE);weatherSourceCredit.setText("");weatherRefresh.setVisibility(refreshPage?android.view.View.VISIBLE:android.view.View.GONE);weatherRefresh.setContentDescription(screen+"_refresh");headerClock.setVisibility(homePage?android.view.View.GONE:android.view.View.VISIBLE);findViewById(R.id.mini_player).setVisibility(homePage||radioPage||"music".equals(screen)?android.view.View.GONE:android.view.View.VISIBLE);
        if(search!=null)handler.removeCallbacks(search);search=null;lastSnapshot=null;
        if(homePhotoPreview!=null){homePhotoPreview.close();homePhotoPreview=null;}if(photoFrameScreen!=null){photoFrameScreen.close();photoFrameScreen=null;}if(climateScreen!=null){climateScreen.close();climateScreen=null;}if(musicScreen!=null){musicScreen.close();musicScreen=null;}content.removeAllViews();controls.removeAllViews();radioState=null;radioScreen=null;homeScreen=null;stationsScreen=null;radioFavorites=null;
        for(int i=0;i<SCREENS.length;i++){navigationButtons[i].setSelected(SCREENS[i].equals(screen));style.repaint(navigationButtons[i]);}
        updateHeading();
        if(screen.equals("settings")){settings();return;}
        if(photoPage){photoFrameScreen=new PhotoFrameScreen(this);Runnable launch=()->startActivity(new Intent(this,PhotoFrameActivity.class).putExtra("start_slideshow",true).putExtra("slideshow_only",true));if(posterPhotoFrameScreen())photoFrameScreen.renderPoster(content,launch,headerClock,audioOutput,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume,findViewById(R.id.footer_status));else photoFrameScreen.render(content,launch);updateMiniPlayer();return;}
        if(screen.equals("climate")){
            climateScreen=new ClimateScreen(this,climate,climateSync,status,this::updateClimateRefresh);
            if(posterClimateScreen()){
                posterClimate=new PosterClimateView(this,headerClock,audioOutput,weatherRefresh,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume,findViewById(R.id.footer_status));
                content.addView(posterClimate,new LinearLayout.LayoutParams(-1,-1));climateScreen.renderPoster(posterClimate);
            }else climateScreen.render(content,controls);
            updateMiniPlayer();return;
        }
        if(screen.equals("music")){musicScreen=new MusicScreen(this,status);musicScreen.render(content,controls,headerClock,audioOutput,weatherRefresh,findViewById(R.id.footer_status),weatherServiceStatus);updateMiniPlayer();return;}
        if(screen.equals("stations")){catalogueControls();if(posterStationsScreen())createStationsPoster();}
        load();
    }
    private void updateClimateRefresh(){
        boolean busy=active&&"climate".equals(screen)&&climate.busy();
        weatherRefresh.setEnabled(!busy);
        if(busy&&climateSpin==null){
            climateSpin=ValueAnimator.ofFloat(0f,360f);climateSpin.setDuration(1000);climateSpin.setRepeatCount(ValueAnimator.INFINITE);climateSpin.setInterpolator(new LinearInterpolator());
            climateSpin.addUpdateListener(animation->weatherRefresh.setSpinAngle((Float)animation.getAnimatedValue()));climateSpin.start();
        }else if(!busy)finishClimateSpin();
    }
    private void finishClimateSpin(){if(climateSpin!=null){climateSpin.cancel();climateSpin=null;}weatherRefresh.setSpinAngle(0f);weatherRefresh.setEnabled(true);}
    private void resumeClimateAction(){handler.post(()->{if(!active||climateScreen==null||!"climate".equals(screen)||!BluetoothAccess.granted(this))return;if(climate.store().sensors().isEmpty())climateScreen.search();else climate.refresh();});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==ClimateScreen.PERMISSION_REQUEST)resumeClimateAction();if(request==RadioScreen.WAVEFORM_PERMISSION&&active&&radioScreen!=null)radioScreen.tick();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==ClimateScreen.ENABLE_REQUEST&&result==RESULT_OK)resumeClimateAction();}
    private void catalogueControls(){
        LinearLayout filters=new LinearLayout(this);controls.addView(filters);
        EditText input=new EditText(this);stationSearch=input;input.setSingleLine(true);style.input(input);input.setHint("Поиск станции");input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});input.setText(query);input.setContentDescription("station_search");input.setMinHeight(dp(56));filters.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));
        genreButton=style.button(stationGenreLabel(),"station_genre",()->{
            List<String> choices=new ArrayList<>();choices.add("Все жанры");choices.addAll(availableGenres);String[] labels=choices.toArray(new String[0]);
            style.dialog().setTitle("Жанр станции").setItems(labels,(dialog,n)->{genre=n==0?"":labels[n];genreButton.setText(stationGenreLabel());catalogueChanged(false);}).setNegativeButton("Закрыть",null).show();
        });LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(220),dp(56));gp.leftMargin=dp(12);filters.addView(genreButton,gp);
        CheckBox filter=posterStationsScreen()?new PosterStationsView.FavoritesSwitch(this):new CheckBox(this);stationFavorites=filter;filter.setText("Избранные");filter.setContentDescription("station_favorites_filter");filter.setTextSize(18);style.checkbox(filter);filter.setMinHeight(dp(56));filter.setChecked(favoritesOnly);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(180),dp(56));fp.leftMargin=dp(20);filters.addView(filter,fp);
        filter.setOnCheckedChangeListener((button,value)->{favoritesOnly=value;catalogueChanged(false);});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();catalogueChanged(true);}public void afterTextChanged(Editable value){}});
        style.gap(controls,8);
    }
    private String stationGenreLabel(){return posterStationsScreen()?(genre.isEmpty()?"Все жанры":genre):(genre.isEmpty()?"Жанр: все":"Жанр: "+genre);}
    private void createStationsPoster(){posterStations=new PosterStationsView(this,stationSearch,genreButton,stationFavorites,controls,headerClock,audioOutput,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume,findViewById(R.id.footer_status));content.addView(posterStations,new LinearLayout.LayoutParams(-1,-1));posterStations.loading();}
    private StationsScreen.Actions stationActions(){return new StationsScreen.Actions(){public void select(Dtos.Station station){selectStation(station,false);}public void play(Dtos.Station station){if(station.id.equals(RadioPlaybackService.stationId())){RadioPlaybackService.stop(MainActivity.this);updateMiniPlayer();stationsScreen.tick();}else selectStation(station,true,false);}public void favorite(Dtos.Station station,Button button){setFavorite(station,button);}};}
    private void catalogueChanged(boolean debounce){
        generation++;if(pending!=null)pending.cancel(true);executor.purge();if(search!=null)handler.removeCallbacks(search);
        if(posterStations!=null)posterStations.loading();else content.removeAllViews();status.setText("Поиск…");
        search=null;if(debounce){search=()->{search=null;if(active&&screen.equals("stations"))load();};handler.postDelayed(search,350);}else load();
    }
    private void load(){load(false);}
    private void load(boolean quiet){
        if("climate".equals(screen))return;
        final PanelRepository client=api;
        if(client==null){status.setText("Подключите планшет по коду в настройках.");return;}
        final int request=++generation;final String destination=screen,selected=selectedStation,searchQuery=query,genreQuery=genre;final boolean favorites=favoritesOnly;
        final String scenarioQuery="?scenario="+scenario;
        if(!quiet)status.setText("Загрузка…");if(pending!=null&&!pending.isDone())pending.cancel(true);executor.purge();
        if(!quiet&&destination.equals("stations")&&posterStations==null)content.removeAllViews();
        final long loadStarted=SystemClock.elapsedRealtime();
        pending=submit(()->{
          boolean cachedShown=false;
          for(int phase=quiet?1:0;phase<2;phase++){
            final boolean cacheOnly=phase==0;boolean configurationChanged=false;
            try{
                JSONObject configuration=client.read("/api/v1/bootstrap"+scenarioQuery,cacheOnly);
                Dtos.Bootstrap bootstrap=new Dtos.Bootstrap(configuration);
                configurationChanged=CacheCoherence.affects(configuration,destination);
                if(configurationChanged)handler.post(()->{if(active&&request==generation&&api==client){restorePosterViews();content.removeAllViews();lastSnapshot=null;status.setText("Настройки изменились · загружаем новые данные…");}});
                JSONObject payload;String signature,catalogueSignature=null;
                ScreenModel model;
                switch(destination){
                    case "home":payload=client.dashboard(configuration,"/api/v1/dashboard"+scenarioQuery,cacheOnly);model=Presenters.home(new Dtos.Dashboard(payload));break;
                    case "weather":payload=client.weather(configuration,"/api/v1/weather"+scenarioQuery+"&days=14",cacheOnly);model=Presenters.weather(new Dtos.Weather(payload));break;
                    case "finance":payload=client.read("/api/v1/finance"+scenarioQuery,cacheOnly);model=Presenters.finance(new Dtos.Finance(payload));break;
                    case "stations":{
                        Dtos.Catalogue first=null;List<Dtos.Station> all=new ArrayList<>();java.util.HashSet<String> ids=new java.util.HashSet<>();StringBuilder pages=new StringBuilder();int nextOffset=0;
                        do{
                            payload=client.read("/api/v1/radio/stations"+scenarioQuery+"&limit="+STATIONS_PAGE_SIZE+"&offset="+nextOffset+"&q="+ApiClient.encode(searchQuery)+"&genre="+ApiClient.encode(genreQuery)+"&favorites="+(favorites?"1":"0"),cacheOnly);
                            Dtos.Catalogue page=new Dtos.Catalogue(payload);if(first==null)first=page;
                            if(page.total!=first.total||page.offset!=nextOffset||page.stations.isEmpty()&&nextOffset<page.total)throw new IllegalStateException("Station catalogue changed during loading.");
                            for(Dtos.Station station:page.stations){if(!ids.add(station.id))throw new IllegalStateException("Duplicate station.");all.add(station);}
                            nextOffset+=page.stations.size();if(nextOffset>500)throw new IllegalStateException("Station catalogue too large for this tablet.");pages.append(ScreenSnapshot.of(payload));
                        }while(nextOffset<first.total);
                        model=Presenters.stations(new Dtos.Catalogue(first,all));catalogueSignature=pages.toString();break;
                    }
                    default:
                        String station=selected==null?bootstrap.selectedStation:selected;
                        if(station==null){payload=client.read("/api/v1/radio/stations"+scenarioQuery,cacheOnly);model=Presenters.radio(new Dtos.Catalogue(payload),null);}
                        else {
                            try{payload=client.read("/api/v1/radio/stations/"+station+scenarioQuery,cacheOnly);model=Presenters.radio(new Dtos.Station(payload.getJSONObject("data")),new Dtos.Meta(payload));}
                            catch(ApiClient.HttpError missing){if(missing.code!=404)throw missing;payload=client.read("/api/v1/radio/stations"+scenarioQuery,cacheOnly);model=Presenters.radio(new Dtos.Catalogue(payload),null);}
                        }
                        break;
                }
                signature=ScreenSnapshot.of(configuration)+(catalogueSignature==null?ScreenSnapshot.of(payload):catalogueSignature);
                if(destination.equals("radio")){JSONObject favoritesData=client.read("/api/v1/radio/stations"+scenarioQuery+"&favorites=1&limit=50",cacheOnly);model.radioFavorites=new Dtos.Catalogue(favoritesData);signature+=ScreenSnapshot.of(favoritesData);}
                // Reconcile the saved URL on every screen, without restarting the active player.
                Dtos.Station savedStation=model.playbackStation;String stationId=selected==null?bootstrap.selectedStation:selected;
                if(bootstrap.radioEnabled&&!destination.equals("radio")&&stationId!=null)try{JSONObject card=client.read("/api/v1/radio/stations/"+stationId+scenarioQuery,cacheOnly);savedStation=new Dtos.Station(card.getJSONObject("data"));signature+=ScreenSnapshot.of(card);}catch(PanelRepository.NoSnapshot absent){if(!cacheOnly)throw absent;}catch(ApiClient.HttpError denied){if(denied.code!=403&&denied.code!=404)throw denied;}
                if(cacheOnly)cachedShown=true;
                final boolean incremental=quiet||!cacheOnly&&cachedShown;final boolean newConfiguration=configurationChanged;
                final ScreenModel rendered=model;
                final Dtos.Station currentStation=savedStation;final String snapshot=signature;
                handler.post(new Runnable(){public void run(){
                    if(!active||request!=generation||api!=client)return;
                    long interactionAge=SystemClock.elapsedRealtime()-lastInteraction;
                    // Keep controls stable during a gesture, but do not discard the initial
                    // network result: its cache-checking state must eventually resolve.
                    if(incremental&&!newConfiguration&&interactionAge<5000){if(!quiet)handler.postDelayed(this,5000-interactionAge);return;}
                    headerClock.setTimeZone(bootstrap.timezone);DisplaySettings.preferences(MainActivity.this).edit().putString("timezone",bootstrap.timezone).apply();applyDisplay();if(!bootstrap.displayTitle.equals(navigationTitle.getText().toString())){navigationTitle.setText(bootstrap.displayTitle);preferences.edit().putString("display_title",bootstrap.displayTitle).apply();if(style.poster&&"home".equals(screen))PanelStyle.update(heading,bootstrap.displayTitle);}if(!RadioStore.hasVolume(MainActivity.this))RadioPlaybackService.volume(MainActivity.this,bootstrap.radioVolume);if(currentStation!=null&&currentStation.enabled)remember(currentStation);
                    photoAvailable=bootstrap.photoframeEnabled;preferences.edit().putBoolean("photoframe_enabled",photoAvailable).apply();navigationButtons[PHOTO_SCREEN_INDEX].setVisibility(photoAvailable?android.view.View.VISIBLE:android.view.View.GONE);
                    boolean changed=!snapshot.equals(lastSnapshot);if(!incremental||changed){ScrollView scroll=(ScrollView)content.getParent();final int position=scroll.getScrollY();render(rendered);lastSnapshot=snapshot;if(incremental)scroll.post(()->{if(active&&request==generation)scroll.scrollTo(0,position);});}else status.setText(rendered.status);if(!cacheOnly&&"finance".equals(destination))finishFinanceWait();updateMiniPlayer();
                    if(cacheOnly&&request==generation){
                        Log.i("MediaCentreH6","H6_CACHE_SCREEN_OK screen="+destination+" elapsed_ms="+(SystemClock.elapsedRealtime()-loadStarted));
                        if(BuildConfig.DEBUG&&!firstCacheFrameLogged){
                            firstCacheFrameLogged=true;
                            final android.view.ViewTreeObserver observer=content.getViewTreeObserver();
                            observer.addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener(){boolean posted;public void onDraw(){if(posted)return;posted=true;handler.post(()->{if(observer.isAlive())observer.removeOnDrawListener(this);if(active&&request==generation)Log.i("MediaCentreH6","H6_FIRST_CACHE_FRAME screen="+destination+" elapsed_ms="+(SystemClock.elapsedRealtime()-started));});}});content.invalidate();
                        }
                    }if(!cacheOnly&&request==generation)Log.i("MediaCentreH1","H1_SCREEN_OK screen="+destination+" scenario="+scenario+" test_data="+rendered.testData+" home="+bootstrap.homeEnabled+" rows="+rendered.rows.size());if(quiet&&request==generation)Log.i("MediaCentreH5","H5_REFRESH_OK screen="+destination+" changed="+changed);}});
            }catch(Exception error){
                if(cacheOnly){if(error instanceof PairIdentity.ServerChanged)handler.post(()->{if(active&&request==generation&&api==client)requirePairing("Сервер изменился. Получите новый код в панели.");});continue;}
                if(error instanceof InterruptedException)return;
                final boolean invalidated=configurationChanged||error instanceof PanelRepository.SnapshotInvalidated;
                final boolean preserve=(cachedShown||quiet)&&!invalidated;
                handler.post(()->{if(!active||request!=generation||api!=client)return;if("finance".equals(destination))finishFinanceWait();
                if(error instanceof PairIdentity.ServerChanged || (error instanceof ApiClient.HttpError && ((ApiClient.HttpError)error).code==401)){requirePairing("Доступ отозван или сервер изменился. Получите новый код в панели.");return;}
                if(preserve&&!(error instanceof ApiClient.HttpError&&((ApiClient.HttpError)error).code==403)){status.setText("Нет обновления · показаны прежние данные. Проверьте сеть и адрес сервера.");Log.i("MediaCentreH5","H5_REFRESH_UNAVAILABLE screen="+destination);return;}
                status.setText(error instanceof ApiClient.HttpError && ((ApiClient.HttpError)error).code==403?"Для этого экрана нужны права. Проверьте разрешения планшета в панели.":"Не удалось загрузить данные. Проверьте сеть и адрес сервера.");restorePosterViews();content.removeAllViews();lastSnapshot=null;Button retry=button("Повторить");content.addView(retry);retry.setOnClickListener(v->load());Log.i("MediaCentreH1","H1_SCREEN_ERROR screen="+destination+" type="+error.getClass().getSimpleName()+(error instanceof ApiClient.HttpError?" http="+((ApiClient.HttpError)error).code:""));});}
          }
        });
    }
    private void render(ScreenModel model){
        if(model.catalogue!=null&&posterStationsScreen()){
            lastDataStatus="Последняя загрузка: "+model.status;status.setText(model.status);availableGenres.clear();availableGenres.addAll(model.catalogue.availableGenres);
            if(posterStations==null){restorePosterViews();content.removeAllViews();updateHeading();createStationsPoster();}
            stationsScreen=new StationsScreen(this);stationsScreen.renderPoster(posterStations,model.catalogue,stationActions(),favoritesOnly);return;
        }
        restorePosterViews();
        lastDataStatus="Последняя загрузка: "+model.status;
        if(model.finance!=null){android.view.View oldShares=content.findViewWithTag("finance_shares_scroll");if(oldShares!=null)financeSharesScrollX=oldShares.getScrollX();}
        updateHeading();status.setText(model.status);if(homePhotoPreview!=null&&(model.dashboard==null||!photoAvailable)){homePhotoPreview.close();homePhotoPreview=null;}content.removeAllViews();
        if(model.playbackStation!=null){Dtos.Station station=model.playbackStation;if(station.enabled)remember(station);else if(station.id.equals(selectedStation)){AudioPlayback.stopAll(this);RadioStore.clear(this);}}
        if(model.dashboard!=null){
            weatherCredit(model.dashboard.weather);
            for(Dtos.Station station:model.dashboard.favorites)if(station.id.equals(selectedStation)&&station.enabled)remember(station);
            homePaletteModel=model;homeScreen=new HomeScreen(this);homeScreen.render(content,model.dashboard,destination->navigate(destination));if(photoAvailable){if(homePhotoPreview!=null)homePhotoPreview.attach(homeScreen.photoRow());else homePhotoPreview=new HomePhotoPreview(this,homeScreen.photoRow(),()->startActivity(new Intent(this,PhotoFrameActivity.class).putExtra("start_slideshow",true).putExtra("slideshow_only",true)),style.poster?144:224);}updateMiniPlayer();return;
        }
        if(model.weather!=null){weatherPaletteModel=model;weatherCredit(model.weather);if(waitingForWeather && model.weather.meta.fetchedAt!=null && (!model.weather.meta.fetchedAt.equals(weatherBeforeRefresh)||"met_norway".equals(model.weather.meta.source)&&"fresh".equals(model.weather.meta.freshness)&&!model.weather.meta.expired)){finishWeatherWait();status.setText("Прогноз актуален.");}lastWeatherFetch=model.weather.meta.fetchedAt;heading.setText("Погода · "+model.weather.city);controls.removeAllViews();
            if(posterWeatherScreen()){
                posterWeather=new PosterWeatherView(this,model.weather,headerClock,audioOutput,weatherRefresh,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume);
                content.addView(posterWeather,new LinearLayout.LayoutParams(-1,-1));posterWeather.forecastOffset(weatherForecastOffset);
            }else new WeatherScreen(this).render(content,model.weather);return;}
        if(model.finance!=null){financePaletteModel=model;
            if(posterFinanceScreen()){
                posterFinance=new PosterFinanceView(this,model.finance,financeSharesScrollX,headerClock,audioOutput,weatherRefresh,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume,findViewById(R.id.footer_status));
                content.addView(posterFinance,new LinearLayout.LayoutParams(-1,-1));
            }else new FinanceScreen(this).render(content,model.finance,financeSharesScrollX);return;}
        if(model.catalogue!=null){
            Dtos.Catalogue catalogue=model.catalogue;
            availableGenres.clear();availableGenres.addAll(catalogue.availableGenres);
            stationsScreen=new StationsScreen(this);stationsScreen.render(content,catalogue,stationActions());return;
        }
        if("radio".equals(screen)){
            radioPaletteModel=model;
            radioFavorites=model.radioFavorites;
            radioScreen=new RadioScreen(this);
            radioState=radioScreen.render(content,model.playbackStation,radioFavorites,playbackLabel(),new RadioScreen.Actions(){
                public void play(){if(RadioStore.saved(MainActivity.this)!=null)RadioPlaybackService.play(MainActivity.this);}
                public void stop(){RadioPlaybackService.stop(MainActivity.this);if(radioScreen!=null)radioScreen.tick();}
                public void favorite(Dtos.Station station,Button button){setFavorite(station,button);}
                public void cycle(int direction){if(radioFavorites==null)return;Dtos.Station station=RadioCycle.next(radioFavorites.stations,selectedStation,direction);if(station!=null){selectStation(station,RadioPlaybackService.active());}}
                public void volume(int value){try{persistPreferences(new JSONObject().put("radio_volume",value));}catch(Exception error){status.setText("Громкость сохранена только на планшете.");}}
                public void openCatalogue(){navigate("stations");}
            },headerClock,audioOutput,findViewById(R.id.footer_status));return;
        }
        for(ScreenModel.Row row:model.rows){
            LinearLayout card=style.card();card.setMinimumHeight(dp(64));
            card.addView(text(row.title,21,PanelStyle.TEXT));card.addView(text(row.body,16,PanelStyle.MUTED));
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(8);content.addView(card,layout);
            if(row.stationId!=null){card.setContentDescription("select_station_"+row.stationId);card.setClickable(true);card.setOnClickListener(v->{if(row.station!=null)selectStation(row.station,false);});
                Button star=style.button(row.station.favorite?"★ В избранном":"☆ В избранное","favorite_station_"+row.stationId,()->{});star.setOnClickListener(v->setFavorite(row.station,star));card.addView(star);
            }
        }
    }
    private void remember(Dtos.Station station){try{RadioStore.remember(this,new RadioStation(station.id,station.name,station.url));RadioPlaybackService.selectionChanged(this);selectedStation=station.id;preferences.edit().putString("selected_station",station.id).apply();}catch(IllegalArgumentException error){RadioStore.clear(this);}}
    private void beginWeatherWait(){
        waitingForWeather=true;weatherBeforeRefresh=lastWeatherFetch;weatherRefresh.setEnabled(false);
        weatherSpin=ValueAnimator.ofFloat(0f,360f);weatherSpin.setDuration(900);weatherSpin.setRepeatCount(ValueAnimator.INFINITE);weatherSpin.setInterpolator(new LinearInterpolator());weatherSpin.addUpdateListener(animation->weatherRefresh.setSpinAngle((Float)animation.getAnimatedValue()));weatherSpin.start();
        handler.postDelayed(weatherWaitTimeout,35000);Log.i("MediaCentreWeather","WEATHER_REFRESH_SPIN_START");
    }
    private void finishWeatherWait(){
        if(!waitingForWeather)return;waitingForWeather=false;handler.removeCallbacks(weatherWaitTimeout);
        if(weatherSpin!=null){weatherSpin.cancel();weatherSpin=null;}weatherRefresh.setSpinAngle(0f);weatherRefresh.setEnabled(true);Log.i("MediaCentreWeather","WEATHER_REFRESH_SPIN_STOP");
    }
    private void refreshFinance(){
        if(api==null||waitingForFinance)return;
        waitingForFinance=true;weatherRefresh.setEnabled(false);status.setText("Обновляем финансовые данные…");
        financeSpin=ValueAnimator.ofFloat(0f,360f);financeSpin.setDuration(900);financeSpin.setRepeatCount(ValueAnimator.INFINITE);financeSpin.setInterpolator(new LinearInterpolator());financeSpin.addUpdateListener(animation->weatherRefresh.setSpinAngle((Float)animation.getAnimatedValue()));financeSpin.start();
        handler.postDelayed(financeWaitTimeout,35000);load(true);if(pending==null)finishFinanceWait();
    }
    private void finishFinanceWait(){
        if(!waitingForFinance)return;waitingForFinance=false;handler.removeCallbacks(financeWaitTimeout);
        if(financeSpin!=null){financeSpin.cancel();financeSpin=null;}weatherRefresh.setSpinAngle(0f);weatherRefresh.setEnabled(true);
    }
    private void refreshWeather(){
        if(api==null||waitingForWeather)return;
        if(!"database".equals(scenario)){status.setText("В тестовом режиме данные фиксированы.");return;}
        final PanelRepository client=api;beginWeatherWait();status.setText("Запрашиваем свежий прогноз…");writes++;
        Future<?> job=submit(()->{try{client.refreshWeather();Log.i("MediaCentreWeather","WEATHER_REFRESH_QUEUED");handler.post(()->{if(!active||api!=client||!"weather".equals(screen)||!waitingForWeather)return;status.setText("Обновление запрошено · ожидаем данные сервера");handler.postDelayed(()->{if(waitingForWeather&&active&&api==client&&"weather".equals(screen))load(true);},6000);handler.postDelayed(()->{if(waitingForWeather&&active&&api==client&&"weather".equals(screen))load(true);},20000);});}
            catch(Exception error){Log.i("MediaCentreWeather","WEATHER_REFRESH_ERROR type="+error.getClass().getSimpleName()+(error instanceof ApiClient.HttpError?" http="+((ApiClient.HttpError)error).code:""));
                if(PanelRepository.canUseDirectWeather(error))try{
                    JSONObject bootstrap=client.read("/api/v1/bootstrap"+"?scenario="+scenario,true);
                    JSONObject direct=client.directWeather(bootstrap);ScreenModel model=Presenters.weather(new Dtos.Weather(direct));
                    handler.post(()->{if(active&&api==client&&"weather".equals(screen)){render(model);finishWeatherWait();}});return;
                }catch(Exception directError){Log.i("MediaCentreWeather","WEATHER_DIRECT_REFRESH_ERROR type="+directError.getClass().getSimpleName());}
                handler.post(()->{finishWeatherWait();if(active&&api==client&&"weather".equals(screen))writeError(error,"Не удалось обновить погоду. Проверьте сеть.");});}
            finally{handler.post(()->writes--);}});
        if(job==null){writes--;finishWeatherWait();}
    }
    private void selectStation(Dtos.Station station,boolean play){
        selectStation(station,play,true);
    }
    private void selectStation(Dtos.Station station,boolean play,boolean openRadio){
        remember(station);if(play)RadioPlaybackService.play(this);
        try{persistPreferences(new JSONObject().put("selected_station_id",station.id));}catch(Exception error){status.setText("Станция сохранена только на планшете.");}
        if(openRadio)navigate("radio");else{updateMiniPlayer();if(stationsScreen!=null)stationsScreen.tick();}
    }
    private void setFavorite(Dtos.Station station,Button button){
        if(station==null)return;if(api==null){favoriteFailed(station,button);return;}final PanelRepository client=api;final String mode=scenario;final boolean onStations="stations".equals(screen);final boolean newFavorite=!(button.getTag() instanceof Boolean?(Boolean)button.getTag():station.favorite);button.setEnabled(false);generation++;if(pending!=null)pending.cancel(true);executor.purge();
        writes++;Future<?> write=submit(()->{try{client.favorite(station.id,newFavorite,mode);handler.post(()->{if(!active||api!=client)return;if(onStations&&"stations".equals(screen)&&stationsScreen!=null)stationsScreen.favoriteChanged(station.id,newFavorite,favoritesOnly);else if(!screen.equals("settings"))load();Log.i("MediaCentreH5","H5_FAVORITE_OK");});}
            catch(Exception error){handler.post(()->{if(!active||api!=client)return;favoriteFailed(station,button);writeError(error,"Избранное не сохранено. Проверьте сеть, разрешения и лимит 50 станций.");});}finally{handler.post(()->writes--);}});
        if(write==null){writes--;favoriteFailed(station,button);}
    }
    private void favoriteFailed(Dtos.Station station,Button button){if("stations".equals(screen)&&stationsScreen!=null)stationsScreen.favoriteFailed(station.id);else button.setEnabled(true);}
    private void persistPreferences(JSONObject patch){
        final PanelRepository client=api;final String mode=scenario;if(client==null)return;
        writes++;Future<?> write=submit(()->{try{client.preferences(patch,mode);Log.i("MediaCentreH5","H5_PREFERENCES_OK");}catch(Exception error){handler.post(()->{if(active&&api==client)writeError(error,"Выбор и громкость действуют на планшете. На сервере не сохранены: проверьте сеть и права.");});}finally{handler.post(()->writes--);}});if(write==null)writes--;
    }
    private void writeError(Exception error,String message){if(error instanceof PairIdentity.ServerChanged || (error instanceof ApiClient.HttpError && ((ApiClient.HttpError)error).code==401))requirePairing("Доступ отозван. Получите новый код в панели.");else status.setText(message);}
    private Future<?> submit(Runnable task){try{return executor.submit(task);}catch(RejectedExecutionException busy){status.setText("Подождите завершения текущего запроса и повторите действие.");return null;}}
    private String playbackLabel(){String state=RadioPlaybackService.state();String label;switch(state){case "playing":label="В эфире";break;case "connecting":label="Подключение…";break;case "retrying":label="Нет потока · повторное подключение…";break;case "headphones_disconnected":label="Наушники отключены · воспроизведение остановлено";break;case "unavailable":case "error":case "timeout":label="Поток недоступен · повторите запуск";break;case "focus_lost":label="Остановлено другим приложением";break;default:label="Остановлено";}return label+(RadioPlaybackService.active()&&!RadioPlaybackService.name().isEmpty()?" · "+RadioPlaybackService.name():"");}
    private RadioWaveformView miniWaveform;
    private SeekBar miniSlider;
    private VolumeButton miniMute;
    private VolumeButton muteButton(){VolumeButton b=new VolumeButton(this,()->{AudioPlayback.toggleMute(this);updateMiniPlayer();});style.plain(b,false);return b;}
    private void buildPosterMiniPlayer(LinearLayout footer){
        footer.removeAllViews();footer.setPadding(dp(4),dp(8),dp(4),0);footer.getLayoutParams().height=dp(88);footer.requestLayout();style.bind(footer,p->footer.setBackground(new PosterRule(p.color(PanelStyle.BG),p.color(PanelStyle.LINE),p.dp(1),true)));
        miniBadge=style.title("FM",28);miniBadge.setGravity(android.view.Gravity.CENTER);style.bind(miniBadge,p->{miniBadge.setTextColor(0xffffb658);miniBadge.setBackground(new PosterStationArt());});footer.addView(miniBadge,new LinearLayout.LayoutParams(dp(62),dp(62)));
        miniLabel=style.title("",28);miniLabel.setSingleLine();miniLabel.setEllipsize(TextUtils.TruncateAt.END);miniLabel.setContentDescription("mini_player_state");miniLabel.setOnClickListener(v->navigate(AudioPlayback.music()?"music":"radio"));LinearLayout.LayoutParams name=new LinearLayout.LayoutParams(0,-2,.75f);name.leftMargin=dp(16);footer.addView(miniLabel,name);
        miniWaveform=new RadioWaveformView(this,true);style.bind(miniWaveform,p->miniWaveform.setColor(p.color(PanelStyle.SIGNAL)));LinearLayout.LayoutParams wave=new LinearLayout.LayoutParams(0,dp(60),1.75f);wave.leftMargin=dp(20);wave.rightMargin=dp(20);footer.addView(miniWaveform,wave);
        MusicTransportButton footerToggle=new MusicTransportButton(this,"play");footerToggle.inline();miniToggle=footerToggle;miniToggle.setContentDescription("mini_player_toggle");style.miniTransportStyle(miniToggle);miniToggle.setOnClickListener(v->{if(!AudioPlayback.music()&&RadioStore.saved(this)==null)navigate("stations");else AudioPlayback.toggle(this);updateMiniPlayer();});footer.addView(miniToggle,new LinearLayout.LayoutParams(dp(74),dp(74)));
        miniMute=muteButton();LinearLayout.LayoutParams mute=new LinearLayout.LayoutParams(dp(52),dp(64));mute.leftMargin=dp(12);footer.addView(miniMute,mute);
        miniSlider=style.slider();miniSlider.setMax(100);miniSlider.setProgress(RadioStore.volume(this));miniSlider.setContentDescription("mini_player_volume");LinearLayout.LayoutParams slider=new LinearLayout.LayoutParams(dp(200),dp(54));slider.leftMargin=dp(12);footer.addView(miniSlider,slider);miniSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){AudioPlayback.volume(MainActivity.this,value);miniMute.refresh();}}public void onStopTrackingTouch(SeekBar b){}});
        miniVolume=style.text("",19,PanelStyle.TEXT);miniVolume.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);footer.addView(miniVolume,new LinearLayout.LayoutParams(dp(58),dp(48)));updateMiniPlayer();
    }
    private void buildMiniPlayer(){
        LinearLayout footer=(LinearLayout)findViewById(R.id.mini_player);if(style.poster){buildPosterMiniPlayer(footer);return;}miniSlider=null;footer.getLayoutParams().height=dp(72);footer.setPadding(dp(10),dp(10),dp(10),dp(10));footer.removeAllViews();style.background(footer,PanelStyle.CARD);miniBadge=style.badge("FM",48);footer.addView(miniBadge,new LinearLayout.LayoutParams(dp(48),dp(48)));miniLabel=style.text("",18,PanelStyle.TEXT);miniLabel.setMaxLines(2);miniLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);miniLabel.setContentDescription("mini_player_state");LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);labelParams.leftMargin=dp(16);footer.addView(miniLabel,labelParams);
        miniWaveform=new RadioWaveformView(this,true);style.bind(miniWaveform,p->miniWaveform.setColor(p.color(PanelStyle.ACCENT)));LinearLayout.LayoutParams wave=new LinearLayout.LayoutParams(0,dp(48),1.5f);wave.leftMargin=dp(12);wave.rightMargin=dp(16);footer.addView(miniWaveform,wave);
        miniMute=muteButton();footer.addView(miniMute,new LinearLayout.LayoutParams(dp(48),dp(48)));
        miniVolume=style.text("",18,PanelStyle.MUTED);miniVolume.setGravity(android.view.Gravity.CENTER);footer.addView(miniVolume,new LinearLayout.LayoutParams(dp(72),dp(48)));
        if(style.poster){miniToggle=new MusicTransportButton(this,"play");miniToggle.setContentDescription("mini_player_toggle");style.transportStyle(miniToggle,true);miniToggle.setOnClickListener(v->{if(!AudioPlayback.music()&&RadioStore.saved(this)==null)navigate("stations");else AudioPlayback.toggle(this);updateMiniPlayer();});}
        else miniToggle=style.primary("Слушать","mini_player_toggle",()->{if(!AudioPlayback.music()&&RadioStore.saved(this)==null)navigate("stations");else AudioPlayback.toggle(this);updateMiniPlayer();});
        footer.addView(miniToggle,new LinearLayout.LayoutParams(dp(style.poster?56:144),dp(52)));
        if(style.poster){miniLabel.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD));miniLabel.setTextSize(24);miniLabel.setOnClickListener(v->navigate(AudioPlayback.music()?"music":"radio"));}
        else{Button open=style.button("Плеер →","mini_player_open",()->navigate(AudioPlayback.music()?"music":"radio"));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(128),dp(52));p.leftMargin=dp(12);footer.addView(open,p);}updateMiniPlayer();
    }
    private void updateMiniPlayer(){
        if(miniLabel==null)return;boolean busy=AudioPlayback.active();String name=AudioPlayback.name(this);
        PanelStyle.update(miniLabel,style.poster&&!AudioPlayback.music()?local.mediacentre.client.presentation.PosterLabels.station(name):name);if(miniToggle instanceof MusicTransportButton)((MusicTransportButton)miniToggle).symbol(busy?(AudioPlayback.music()?"pause":"stop"):"play");else PanelStyle.update(miniToggle,busy?(AudioPlayback.music()?"Пауза":"Остановить"):"Слушать");PanelStyle.update(miniBadge,AudioPlayback.music()?"♫":PanelStyle.initials(name));PanelStyle.update(miniVolume,RadioStore.volume(this)+" %");
        if(miniWaveform!=null)miniWaveform.setPlaying(active&&miniWaveform.isShown()&&AudioWaveform.allowed(this)&&"playing".equals(AudioPlayback.state()));
        if(miniSlider!=null&&!miniSlider.isPressed())miniSlider.setProgress(RadioStore.volume(this));
        if(miniMute!=null)miniMute.refresh();
        // KitKat can omit the unchanged footer display list after a content redraw.
        // Redraw this small area on the existing visible-only tick, without relayout.
        android.view.View footer=findViewById(R.id.mini_player);if(footer.getVisibility()==android.view.View.VISIBLE)footer.invalidate();
    }
    private void settings(){
        SettingsScreen.Actions actions=new SettingsScreen.Actions(){
            public void pairing(LinearLayout box){pairingControls(box);}
            public void displayChanged(){applyDisplay();}
            public void check(Button button){checkServer(button);}
            public String zone(){return displayZone();}
            public String lastData(){return preferences.getString("home_data_status",lastDataStatus);}
            public String weatherSource(){String source=preferences.getString("weather_attribution","");return source.isEmpty()?"Пока не получен":source;}
            public String weatherData(){return preferences.getString("weather_data_status","Пока не получена");}
        };
        if(posterSettingsScreen()){
            posterSettings=new PosterSettingsView(this,actions,headerClock,audioOutput,status,findViewById(R.id.mini_player),miniLabel,miniWaveform,miniToggle,miniMute,miniSlider,miniVolume,findViewById(R.id.footer_status));
            content.addView(posterSettings,new LinearLayout.LayoutParams(-1,-1));updateMiniPlayer();
        }else new SettingsScreen(this).render(content,actions);
        content.setFocusableInTouchMode(true);content.requestFocus();
    }
    private void rebuildSettings(){
        EditText address=(EditText)PanelStyle.find(content,"pair_address"),code=(EditText)PanelStyle.find(content,"pair_code");
        String draftAddress=address==null?null:address.getText().toString(),draftCode=code==null?null:code.getText().toString();
        String focus=address!=null&&address.hasFocus()?"pair_address":code!=null&&code.hasFocus()?"pair_code":null;
        EditText editor="pair_address".equals(focus)?address:code;int start=editor==null?0:editor.getSelectionStart(),end=editor==null?0:editor.getSelectionEnd();
        navigate("settings");
        EditText nextAddress=(EditText)PanelStyle.find(content,"pair_address"),nextCode=(EditText)PanelStyle.find(content,"pair_code");
        if(draftAddress!=null&&nextAddress!=null)nextAddress.setText(draftAddress);if(draftCode!=null&&nextCode!=null)nextCode.setText(draftCode);
        if(focus!=null){EditText next=(EditText)PanelStyle.find(content,focus);if(next!=null){next.requestFocus();next.setSelection(Math.max(0,Math.min(start,next.length())),Math.max(0,Math.min(end,next.length())));}}
    }
    private String displayZone(){return DisplaySettings.preferences(this).getString("timezone","Europe/Moscow");}
    private void rememberDiagnostic(String key,String value){if(!TextUtils.equals(preferences.getString(key,null),value))preferences.edit().putString(key,value).apply();}
    private void weatherCredit(Dtos.Weather weather){String credit=weather.days.isEmpty()?"":weather.attribution;PanelStyle.update(weatherSourceCredit,credit);rememberDiagnostic("weather_attribution",credit);}
    private void applyDisplay(){
        if(!active)return;DisplaySettings.apply(this,displayZone());PanelStyle updated=new PanelStyle(this);
        if(style.poster!=updated.poster){
            android.view.View address=PanelStyle.find(content,"pair_address"),code=PanelStyle.find(content,"pair_code");
            String draftAddress=address instanceof EditText?((EditText)address).getText().toString():null,draftCode=code instanceof EditText?((EditText)code).getText().toString():null;
            int scroll=((ScrollView)content.getParent()).getScrollY();restorePosterViews();style=updated;applyChrome();buildMiniPlayer();navigate(screen);
            address=PanelStyle.find(content,"pair_address");code=PanelStyle.find(content,"pair_code");
            if(draftAddress!=null&&address instanceof EditText)((EditText)address).setText(draftAddress);
            if(draftCode!=null&&code instanceof EditText)((EditText)code).setText(draftCode);
            if("settings".equals(screen))content.post(()->((ScrollView)content.getParent()).scrollTo(0,scroll));
            Log.i("MediaCentreDesign","DESIGN_APPLIED poster="+style.poster);
        }else if(style.palette.dark!=updated.palette.dark){PosterStationsView.AppearanceState stationAppearance=posterStations==null?null:posterStations.appearance();int[] settingsPosition=posterSettings==null?null:posterSettings.scrollPosition();restorePosterViews();style=updated;if(climateScreen!=null)climateScreen.theme(updated);style.repaint(findViewById(android.R.id.content));if(style.poster&&"home".equals(screen)){String homeStatus=status.getText().toString();if(homePhotoPreview!=null){homePhotoPreview.close();homePhotoPreview=null;}updateHeading();if(homePaletteModel!=null)render(homePaletteModel);status.setText(homeStatus);}else if(style.poster&&"weather".equals(screen)){String weatherStatus=status.getText().toString();updateHeading();if(weatherPaletteModel!=null)render(weatherPaletteModel);status.setText(weatherStatus);}else if(style.poster&&"finance".equals(screen)){updateHeading();if(financePaletteModel!=null)render(financePaletteModel);}else if(style.poster&&"photoframe".equals(screen)){navigate(screen);}else if(style.poster&&"climate".equals(screen)){navigate(screen);}else if(style.poster&&"radio".equals(screen)){updateHeading();if(radioPaletteModel!=null)render(radioPaletteModel);}else if(style.poster&&"stations".equals(screen)){navigate(screen);if(stationAppearance!=null&&posterStations!=null)posterStations.appearance(stationAppearance);}else if(style.poster&&"music".equals(screen)&&musicScreen!=null){updateHeading();musicScreen.retheme();}else if(style.poster&&"settings".equals(screen)){rebuildSettings();if(settingsPosition!=null&&posterSettings!=null)posterSettings.scrollPosition(settingsPosition);}Log.i("MediaCentreH7","H7_THEME_APPLIED dark="+style.palette.dark);}
    }
    private boolean posterWeatherScreen(){return style.poster&&"weather".equals(screen);}
    private boolean posterClimateScreen(){return style.poster&&"climate".equals(screen);}
    private boolean posterRadioScreen(){return style.poster&&"radio".equals(screen);}
    private boolean posterStationsScreen(){return style.poster&&"stations".equals(screen);}
    private boolean posterFinanceScreen(){return style.poster&&"finance".equals(screen);}
    private boolean posterPhotoFrameScreen(){return style.poster&&"photoframe".equals(screen);}
    private boolean posterSettingsScreen(){return style.poster&&"settings".equals(screen);}
    private void restorePosterViews(){if(posterSettings!=null){posterSettings.restore();posterSettings=null;}if(photoFrameScreen!=null)photoFrameScreen.restore();if(posterFinance!=null){financeSharesScrollX=posterFinance.sharesOffset();posterFinance.restore();posterFinance=null;}if(musicScreen!=null)musicScreen.restore();if(posterStations!=null){posterStations.restore();posterStations=null;}if(radioScreen!=null)radioScreen.restore();if(posterClimate!=null){posterClimate.restore();posterClimate=null;}if(posterWeather!=null){weatherForecastOffset=posterWeather.forecastOffset();posterWeather.restore();posterWeather=null;}}
    private void updateHeading(){
        heading.setText(style.poster&&"home".equals(screen)?preferences.getString("display_title","Мой дом"):LABELS[Arrays.asList(SCREENS).indexOf(screen)]);
        if(style.poster){
            boolean simple="home".equals(screen)||"weather".equals(screen)||"radio".equals(screen);
            int size="home".equals(screen)?25:"weather".equals(screen)?34:"radio".equals(screen)?38:"music".equals(screen)?76:94;
            PosterTypography.headline(heading,size);
            android.view.View header=findViewById(R.id.panel_header);int height=dp("home".equals(screen)?40:"weather".equals(screen)?50:"radio".equals(screen)?50:"music".equals(screen)?100:"finance".equals(screen)?106:"photoframe".equals(screen)||"stations".equals(screen)?104:"climate".equals(screen)?124:138);
            if(header.getLayoutParams().height!=height){header.getLayoutParams().height=height;header.requestLayout();}
            style.bind(header,p->header.setBackground(simple?null:new PosterBanner(this,p.palette.dark?0xff102a94:0xff0738e8)));audioOutput.contrast(!simple);style.bind(headerClock,p->headerClock.setTextColor(simple?p.color(PanelStyle.ACCENT):android.graphics.Color.WHITE));style.plain(weatherRefresh,!simple);
            status.setVisibility(android.view.View.GONE);
        }else{audioOutput.contrast(false);style.ink(headerClock,PanelStyle.ACCENT);style.buttonStyle(weatherRefresh,false);heading.setTextSize(30);heading.setTextScaleX(1);android.view.View header=findViewById(R.id.panel_header);header.getLayoutParams().height=dp(56);style.bind(header,p->header.setBackground(null));header.requestLayout();}
        configurePosterChrome();
    }
    /** Navigation is theme-wide; only the content composition varies between tabs. */
    private void configurePosterChrome(){
        boolean edge=style.poster&&"home".equals(screen)||posterWeatherScreen()||posterClimateScreen()||posterRadioScreen()||posterStationsScreen()||posterFinanceScreen()||posterPhotoFrameScreen()||posterSettingsScreen()||style.poster&&"music".equals(screen);
        findViewById(R.id.panel_header).setVisibility(edge?android.view.View.GONE:android.view.View.VISIBLE);
        android.view.View body=findViewById(R.id.panel_body);int horizontal=dp(edge?0:style.poster?18:24),vertical=dp(edge?0:style.poster?8:24);body.setPadding(horizontal,vertical,horizontal,vertical);
        ((ScrollView)content.getParent()).setVerticalScrollBarEnabled(!edge);
        android.view.View decor=getWindow().getDecorView();
        if(style.poster){if(posterPreviousSystemUi<0)posterPreviousSystemUi=decor.getSystemUiVisibility();int flags=posterPreviousSystemUi|android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;if(decor.getSystemUiVisibility()!=flags)decor.setSystemUiVisibility(flags);}
        else if(posterPreviousSystemUi>=0){decor.setSystemUiVisibility(posterPreviousSystemUi);posterPreviousSystemUi=-1;}
    }
    private void applyChrome(){
        LinearLayout navigation=findViewById(R.id.navigation);LinearLayout.LayoutParams width=(LinearLayout.LayoutParams)navigation.getLayoutParams();width.width=dp(style.poster?94:184);navigation.setLayoutParams(width);navigation.setPadding(dp(style.poster?4:12),dp(12),dp(style.poster?4:12),dp(12));
        navigationTitle.setVisibility(style.poster?android.view.View.GONE:android.view.View.VISIBLE);navigationTitle.setTypeface(style.poster?PosterTypography.face(this):android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));navigationTitle.setTextScaleX(1);navigation.getChildAt(1).setVisibility(style.poster?android.view.View.GONE:android.view.View.VISIBLE);
        for(Button button:navigationButtons){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)button.getLayoutParams();p.height=style.poster?0:dp(54);p.weight=style.poster?1:0;p.bottomMargin=dp(style.poster?0:6);button.setLayoutParams(p);}
        android.view.View body=findViewById(R.id.panel_body);body.setPadding(dp(style.poster?18:24),dp(style.poster?8:24),dp(style.poster?18:24),dp(style.poster?8:24));
        android.view.View header=findViewById(R.id.panel_header);header.getLayoutParams().height=dp(style.poster?72:56);header.requestLayout();
        heading.setTypeface(style.poster?PosterTypography.face(this):android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerClock.setTypeface(style.poster?PosterTypography.face(this):android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));headerClock.setTextSize(style.poster?28:26);
        for(android.view.View utility:new android.view.View[]{audioOutput,headerClock}){LinearLayout.LayoutParams up=(LinearLayout.LayoutParams)utility.getLayoutParams();up.height=style.poster?dp(48):-1;up.gravity=style.poster?android.view.Gravity.TOP:android.view.Gravity.CENTER_VERTICAL;utility.setLayoutParams(up);}
        style.repaint(findViewById(android.R.id.content));updateHeading();
    }
    private void checkServer(Button button){
        final PanelRepository client=api;if(client==null){status.setText("Введите адрес сервера и код для подключения.");return;}button.setEnabled(false);status.setText("Проверка сервера…");
        Future<?> job=submit(()->{try{JSONObject response=client.get("/api/v1/bootstrap?scenario="+scenario);Dtos.Bootstrap bootstrap=new Dtos.Bootstrap(response);Dtos.Meta meta=new Dtos.Meta(response);handler.post(()->{if(!active||api!=client)return;button.setEnabled(true);DisplaySettings.preferences(this).edit().putString("timezone",bootstrap.timezone).apply();applyDisplay();status.setText(meta.offline?"Нет связи · доступны сохранённые данные":"Соединение и регистрация проверены");Log.i("MediaCentreH5",meta.offline?"H5_SERVER_CHECK_OFFLINE":"H5_SERVER_CHECK_OK");});}catch(Exception error){handler.post(()->{if(!active||api!=client)return;button.setEnabled(true);writeError(error,"Сервер недоступен. Проверьте сеть, адрес и сертификат.");});}});if(job==null)button.setEnabled(true);
    }
    private void pairingControls(LinearLayout box){
        status.setText(preferences.contains("server_id")?"Планшет подключён":"Подключите планшет по одноразовому коду");
        box.addView(text("Адрес сервера",style.poster?17:20,PanelStyle.TEXT));EditText address=new EditText(this);address.setSingleLine(true);address.setMinHeight(dp(52));style.input(address);address.setText(preferences.getString("base_url",""));address.setHint("http://mediacentre.miniw");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);box.addView(address);
        address.setContentDescription("pair_address");
        box.addView(text("Одноразовый код из веб-панели",style.poster?17:20,PanelStyle.TEXT));EditText code=new EditText(this);code.setSingleLine(true);code.setMinHeight(dp(52));code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});style.input(code);code.setHint(style.poster?"8 символов · 10 минут":"8 символов · действует 10 минут");code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setContentDescription("pair_code");box.addView(code);
        Button pair=button(preferences.contains("server_id")?"Подключить по новому коду":"Подключить планшет");if(style.poster)style.bind(pair,p->{pair.setTextColor(p.color(PanelStyle.ON_SIGNAL));pair.setBackground(p.surface(PanelStyle.SIGNAL,-1,4));});pair.setContentDescription("pair_submit");box.addView(pair);
        pair.setOnClickListener(v->{
            final String target=address.getText().toString().trim(),entered=code.getText().toString();final int request=++generation;
            pair.setEnabled(false);status.setText("Подключение и сопряжение…");
            pending=submit(()->{try{
                PairIdentity result=new ApiClient(this,target,null).pair(entered,"ASUS K014");
                PanelRepository.clearCache(this); // Disk/cache locks stay off the UI thread.
                handler.post(()->{if(!active||request!=generation)return;
                    if(!preferences.edit().putString("base_url",target).putString("token",result.token).putString("server_id",result.serverId).putString("scenario","database").remove("selected_station").remove("photoframe_enabled").commit()){pair.setEnabled(true);status.setText("Не удалось сохранить подключение. Выдайте новый код.");return;}
                    AudioPlayback.stopAll(this);RadioStore.clear(this);selectedStation=null;photoAvailable=false;navigationButtons[PHOTO_SCREEN_INDEX].setVisibility(android.view.View.GONE);connect();navigate("home");Log.i("MediaCentreH1","H3_PAIR_OK");
                });
            }catch(Exception error){handler.post(()->{if(!active||request!=generation)return;pair.setEnabled(true);
                String message="Проверьте адрес сервера, сеть и восьмисимвольный код.";
                if(error instanceof ApiClient.HttpError){int http=((ApiClient.HttpError)error).code;message=http==401?"Код неверен, просрочен или уже использован. Выдайте новый.":http==429?"Слишком много попыток. Повторите через 10 минут.":"Сервер временно не может выполнить сопряжение.";}
                status.setText(message);Log.i("MediaCentreH1","H3_PAIR_ERROR");
            });}});if(pending==null)pair.setEnabled(true);
        });
        if(BuildConfig.DEBUG && !preferences.contains("server_id")){box.addView(text("ТЕСТОВЫЕ ДАННЫЕ · сценарий: "+scenario,18,PanelStyle.WARNING));LinearLayout cases=new LinearLayout(this);for(String value:new String[]{"success","empty","partial","stale"}){Button choose=button(value);cases.addView(choose,new LinearLayout.LayoutParams(0,dp(56),1));choose.setOnClickListener(v->{preferences.edit().putString("scenario",value).apply();scenario=value;content.removeAllViews();settings();});}box.addView(cases);}
        box.addView(text("Место, станции и финансовый список задаются в веб-панели. Откройте сервер в браузере, чтобы изменить их.",17,PanelStyle.MUTED));
        Log.i("MediaCentreH1","H1_SETTINGS_OK home=false");
    }
    private void requirePairing(String message){AudioPlayback.stopAll(this);RadioStore.clear(this);api=null;selectedStation=null;navigate("settings");status.setText(message);Log.i("MediaCentreH1","H3_REPAIR_REQUIRED");}
    private Button button(String title){return style.button(title,title,()->{});}
    private TextView text(String value,int size,int role){return style.text(value,size,role);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
