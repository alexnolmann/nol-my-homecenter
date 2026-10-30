package local.mediacentre.client.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextClock;
import android.widget.SeekBar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import local.mediacentre.client.data.ApiClient;
import org.json.JSONObject;

/** Photo settings live in the same tab container as every other screen. */
final class PhotoFrameScreen {
    private static final String MANIFEST="/api/v1/plugins/photoframe/manifest";
    private static final int[] SECONDS={5,10,20,60};
    private final Activity activity;
    private final PanelStyle style;
    private final Handler ui=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SharedPreferences settings;
    private TextView summary;
    private Button start;
    private String etag;
    private HomePhotoPreview preview;
    private PosterPhotoFrameView posterFrame;
    private volatile boolean closed;
    private final Runnable poll=new Runnable(){public void run(){if(closed)return;worker.execute(()->fetch());ui.postDelayed(this,30000);}};

    PhotoFrameScreen(Activity activity){this.activity=activity;style=new PanelStyle(activity);settings=activity.getSharedPreferences("photoframe",Activity.MODE_PRIVATE);}

    void renderPoster(LinearLayout parent,Runnable launch,TextClock clock,AudioOutputControls output,View miniRoot,TextView miniName,View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume,View statusRoot){
        posterFrame=new PosterPhotoFrameView(activity,settings,launch,clock,output,miniRoot,miniName,waveform,toggle,mute,slider,volume,statusRoot);parent.addView(posterFrame,new LinearLayout.LayoutParams(-1,-1));
        summary=posterFrame.summary;start=posterFrame.start;preview=new HomePhotoPreview(activity,posterFrame.photos,launch,0,true);preview.fill(settings.getBoolean("fill",false));posterFrame.modeChanged=()->preview.fill(settings.getBoolean("fill",false));ui.post(poll);
    }
    void restore(){if(posterFrame!=null){posterFrame.restore();posterFrame=null;}}

    void render(LinearLayout parent,Runnable launch){
        if(style.poster){poster(parent,launch);return;}
        LinearLayout card=style.card();card.setPadding(style.dp(32),style.dp(28),style.dp(32),style.dp(28));
        if(style.poster){LinearLayout row=new LinearLayout(activity);row.setBaselineAligned(false);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));LinearLayout photos=new LinearLayout(activity);row.addView(photos,new LinearLayout.LayoutParams(0,-2,1.15f));preview=new HomePhotoPreview(activity,photos,launch,400);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,0.85f);cp.leftMargin=style.dp(18);row.addView(card,cp);card.setPadding(style.dp(16),style.dp(12),style.dp(16),style.dp(12));}
        else parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
        card.addView(style.title("Показ фотографий",24));style.gap(card,12);
        summary=style.text("Загружаем список…",21,PanelStyle.MUTED);summary.setContentDescription("photoframe_summary");card.addView(summary);style.gap(card,22);
        card.addView(style.title("Смена фотографий",20));style.gap(card,8);
        Spinner speed=new Spinner(activity);String[] choices={"5 секунд","10 секунд","20 секунд","60 секунд"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_item,choices){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView value=(TextView)super.getView(position,convert,parent);new PanelStyle(activity).bind(value,p->{value.setTextColor(p.color(PanelStyle.TEXT));value.setBackgroundColor(p.color(PanelStyle.BG));});value.setTextSize(20);return value;}
            @Override public View getDropDownView(int position,View convert,ViewGroup parent){TextView value=(TextView)super.getDropDownView(position,convert,parent);new PanelStyle(activity).bind(value,p->{value.setTextColor(p.color(PanelStyle.TEXT));value.setBackgroundColor(p.color(PanelStyle.CARD));});value.setTextSize(20);value.setPadding(style.dp(16),style.dp(12),style.dp(16),style.dp(12));return value;}
        };adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);speed.setAdapter(adapter);speed.setPadding(style.dp(16),0,style.dp(16),0);style.bind(speed,p->speed.setBackground(p.surface(PanelStyle.BG,PanelStyle.LINE,12)));
        int selected=1;for(int n=0;n<SECONDS.length;n++)if(SECONDS[n]==settings.getInt("seconds",10))selected=n;speed.setSelection(selected);card.addView(speed,new LinearLayout.LayoutParams(-1,style.dp(64)));style.gap(card,18);
        CheckBox shuffled=new CheckBox(activity);shuffled.setText("Случайный порядок");style.checkbox(shuffled);shuffled.setChecked(settings.getBoolean("shuffle",false));card.addView(shuffled);style.gap(card,14);
        CheckBox fill=new CheckBox(activity);fill.setText("Заполнить экран (края фото обрезаются)");fill.setContentDescription("photoframe_fill");style.checkbox(fill);fill.setChecked(settings.getBoolean("fill",false));card.addView(fill);style.gap(card,20);
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){settings.edit().putInt("seconds",SECONDS[position]).apply();}public void onNothingSelected(AdapterView<?> parent){}});
        shuffled.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("shuffle",checked).apply());
        fill.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("fill",checked).apply());
        start=style.primary("Начать показ","photoframe_start",launch);start.setEnabled(false);card.addView(start,new LinearLayout.LayoutParams(-1,style.dp(64)));
        ui.post(poll);
    }

    private void poster(LinearLayout parent,Runnable launch){
        LinearLayout row=new LinearLayout(activity);row.setBaselineAligned(false);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));LinearLayout photos=new LinearLayout(activity);row.addView(photos,new LinearLayout.LayoutParams(0,-2,1.8f));preview=new HomePhotoPreview(activity,photos,launch,432,true);
        LinearLayout options=style.column();options.setPadding(style.dp(20),0,0,0);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,-2,.8f);op.leftMargin=style.dp(18);row.addView(options,op);options.addView(style.title("Показ фотографий",28));
        summary=style.text("Загружаем список…",20,PanelStyle.MUTED);summary.setContentDescription("photoframe_summary");options.addView(summary);style.gap(options,12);style.rule(options);style.gap(options,10);
        options.addView(style.title("Смена фотографий",24));LinearLayout speeds=new LinearLayout(activity);options.addView(speeds);Button[] choices=new Button[SECONDS.length];for(int n=0;n<SECONDS.length;n++){final int seconds=SECONDS[n];Button b=style.button(seconds+" с","photoframe_seconds_"+seconds,()->{settings.edit().putInt("seconds",seconds).apply();for(int k=0;k<choices.length;k++){choices[k].setSelected(SECONDS[k]==seconds);style.repaint(choices[k]);}});b.setTextSize(16);b.setPadding(0,0,0,0);b.setSelected(seconds==settings.getInt("seconds",10));style.repaint(b);choices[n]=b;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,style.dp(48),1);if(n>0)bp.leftMargin=style.dp(3);speeds.addView(b,bp);}
        style.gap(options,10);style.rule(options);PosterSwitch shuffle=new PosterSwitch(activity);shuffle.setText("Случайный порядок");style.checkbox(shuffle);shuffle.setChecked(settings.getBoolean("shuffle",false));shuffle.setOnCheckedChangeListener((b,on)->settings.edit().putBoolean("shuffle",on).apply());options.addView(shuffle,new LinearLayout.LayoutParams(-1,style.dp(64)));style.rule(options);style.gap(options,10);
        options.addView(style.title("Режим показа",24));LinearLayout modes=new LinearLayout(activity);options.addView(modes);Button[] modeButtons=new Button[2];String[] names={"С полосами","Заполнить экран"};for(int n=0;n<2;n++){final boolean fill=n==1;Button b=style.button(names[n],fill?"photoframe_fill":"photoframe_fit",()->{settings.edit().putBoolean("fill",fill).apply();for(int k=0;k<2;k++){modeButtons[k].setSelected((k==1)==fill);style.repaint(modeButtons[k]);}});b.setTextSize(18);b.setSelected(fill==settings.getBoolean("fill",false));style.repaint(b);modeButtons[n]=b;LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,style.dp(68),1);if(n>0)bp.leftMargin=style.dp(8);modes.addView(b,bp);}
        style.gap(options,18);start=style.primary("▶  Начать показ","photoframe_start",launch);style.bind(start,p->{start.setTextColor(p.color(PanelStyle.ON_SIGNAL));start.setBackground(p.surface(PanelStyle.SIGNAL,-1,4));});start.setTextSize(26);start.setEnabled(false);options.addView(start,new LinearLayout.LayoutParams(-1,style.dp(66)));ui.post(poll);
    }

    private void fetch(){
        try{
            SharedPreferences connection=activity.getSharedPreferences("connection",Activity.MODE_PRIVATE);
            ApiClient api=new ApiClient(activity,connection.getString("base_url",""),connection.getString("token",""),connection.getString("server_id",null));
            ApiClient.Response response=api.fetch(MANIFEST,etag);
            if(response.code==304)return;
            if(response.code!=200)throw new ApiClient.HttpError(response.code);
            int count=new JSONObject(response.body).getJSONObject("data").getJSONArray("items").length();
            if(count>500)throw new IllegalStateException("Album too large");
            ui.post(()->{if(closed)return;etag=response.etag;start.setEnabled(count>0);summary.setText(count==0?"Фотографий пока нет":posterFrame==null?"Фотографий: "+count:count+" "+(count%100>=11&&count%100<=14?"фотографий":count%10==1?"фотография":count%10>=2&&count%10<=4?"фотографии":"фотографий"));});
        }catch(Exception error){ui.post(()->{if(closed)return;summary.setText("Нет связи с фотоальбомом");});}
    }

    void close(){restore();closed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();if(preview!=null)preview.close();}
}
