package local.mediacentre.client.ui;

import android.app.*;
import android.content.Intent;
import android.bluetooth.BluetoothAdapter;
import android.provider.Settings;
import android.os.SystemClock;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import local.mediacentre.client.climate.*;

/** Room readings stay usable offline; configuration and latest samples synchronize with the panel. */
final class ClimateScreen {
    static final int PERMISSION_REQUEST=740,ENABLE_REQUEST=741;
    private final Activity activity;
    private PanelStyle style;
    private long historyWindow,nextTick;
    private final ClimateController climate;
    private final ClimateSync sync;
    private LinearLayout conflictControls;
    private final TextView footer;
    private final Runnable refreshState;
    private LinearLayout cards,discoveryRows;
    private TextView discoveryStatus;
    private Button addButton;
    private AlertDialog discovery;
    private String signature="";
    private PosterClimateView poster;
    private final List<Card> displayed=new ArrayList<>();
    private final Map<String,Boolean> discoveryButtons=new HashMap<>();

    ClimateScreen(Activity activity,ClimateController climate,ClimateSync sync,TextView footer,Runnable refreshState){
        this.activity=activity;this.style=new PanelStyle(activity);this.climate=climate;this.sync=sync;this.footer=footer;this.refreshState=refreshState;
    }
    void render(LinearLayout parent,LinearLayout controls){
        LinearLayout tools=new LinearLayout(activity);
        addButton=style.button("Добавить датчик","climate_add",this::search);
        if(style.poster){
            android.widget.FrameLayout overlay=new android.widget.FrameLayout(activity);overlay.setClipChildren(false);controls.setClipChildren(false);android.widget.FrameLayout.LayoutParams button=new android.widget.FrameLayout.LayoutParams(style.dp(210),style.dp(48),Gravity.RIGHT);button.topMargin=-style.dp(54);overlay.addView(addButton,button);controls.addView(overlay,new LinearLayout.LayoutParams(-1,0));style.bind(addButton,p->{addButton.setTextColor(addButton.isEnabled()?android.graphics.Color.WHITE:0xff9fafd0);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(android.graphics.Color.TRANSPARENT);bg.setStroke(p.dp(1),0xff9fafd0);bg.setCornerRadius(p.dp(24));addButton.setBackground(bg);});
        }else{tools.addView(addButton,new LinearLayout.LayoutParams(style.dp(220),style.dp(52)));controls.addView(tools);style.gap(controls,14);}
        buildConflictControls();controls.addView(conflictControls);if(!style.poster)style.gap(controls,10);
        cards=style.column();parent.addView(cards,new LinearLayout.LayoutParams(-1,-2));update();
    }
    void renderPoster(PosterClimateView view){
        poster=view;addButton=style.button("Добавить датчик","climate_add",this::search);
        buildConflictControls();view.tools(addButton,conflictControls);update();
    }
    private void buildConflictControls(){
        conflictControls=style.column();conflictControls.addView(style.text("Настройки датчиков изменены одновременно на планшете и в панели.",17,PanelStyle.WARNING));
        LinearLayout choices=new LinearLayout(activity);choices.addView(style.button("Принять из панели","climate_conflict_remote",()->sync.resolve(true)));choices.addView(style.button("Сохранить с планшета","climate_conflict_local",()->sync.resolve(false)));conflictControls.addView(choices);
    }
    void update(){
        historyWindow=System.currentTimeMillis()/ClimateController.HISTORY_BUCKET_MS;
        List<ClimateSensor> sensors=climate.store().sensors();
        StringBuilder key=new StringBuilder().append(historyWindow);for(ClimateSensor sensor:sensors)key.append(sensor.address).append(sensor.name).append(sensor.latest==null?0:sensor.latest.measuredAt);
        if(!key.toString().equals(signature)||poster==null&&cards.getChildCount()==0){signature=key.toString();renderCards(sensors);}
        addButton.setEnabled(sensors.size()<ClimateSensor.MAX_SENSORS);if(style.poster&&poster==null)addButton.setVisibility(sensors.size()<ClimateSensor.MAX_SENSORS?View.VISIBLE:View.GONE);
        for(Card card:displayed)card.update();
        if(poster!=null)poster.state(climate);
        String text=climate.message();if(!climate.busy()&&!sensors.isEmpty()&&BluetoothAccess.unavailable(activity)==null&&"Показания сохраняются на планшете".equals(text))text=climate.intervalLabel();
        conflictControls.setVisibility(sync.conflicting()?View.VISIBLE:View.GONE);
        if(poster!=null)poster.conflicting(sync.conflicting());
        if(!climate.busy()&&BluetoothAccess.unavailable(activity)==null&&!sensors.isEmpty())text=climate.intervalLabel();
        if(!sync.status().isEmpty())text+=" · "+sync.status();
        footer.setText(text);refreshState.run();updateDiscovery();
    }
    private void renderCards(List<ClimateSensor> sensors){
        if(poster!=null){poster.sensors(sensors,this::manage);return;}
        cards.removeAllViews();displayed.clear();
        if(sensors.isEmpty()){
            LinearLayout empty=style.card();empty.addView(style.title("Климат в вашем доме",26));style.gap(empty,12);
            empty.addView(style.text("Добавьте Xiaomi LYWSD03MMC, чтобы видеть температуру и влажность в комнате.",20,PanelStyle.MUTED));style.gap(empty,12);
            empty.addView(style.text("Включите датчик и положите его рядом с планшетом. Mi Home для чтения показаний не нужен.",17,PanelStyle.MUTED));
            cards.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;
        }
        LinearLayout row=new LinearLayout(activity);row.setBaselineAligned(false);if(style.poster)style.columns(row);cards.addView(row,new LinearLayout.LayoutParams(-1,-2));
        for(int n=0;n<sensors.size();n++){
            ClimateSensor sensor=sensors.get(n);Card card=new Card(sensor);displayed.add(card);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-2,1);if(n>0)layout.leftMargin=style.dp(16);row.addView(card.view,layout);
        }
    }
    private final class Card {
        final String address;final LinearLayout view;final TextView age,state;
        Card(ClimateSensor sensor){
            address=sensor.address;view=style.card();view.setContentDescription("climate_sensor_"+address);
            LinearLayout titleRow=new LinearLayout(activity);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=style.title(sensor.name,style.poster?36:24);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);titleRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button manage=new ClimateOptionsButton(activity);style.buttonStyle(manage,false);manage.setContentDescription("climate_manage_"+address);manage.setOnClickListener(v->manage(address));titleRow.addView(manage,new LinearLayout.LayoutParams(style.dp(52),style.dp(48)));view.addView(titleRow);style.gap(view,style.poster?8:18);
            if(sensor.latest==null){view.addView(style.text("— °C",52,PanelStyle.ACCENT));view.addView(style.text("— %",32,PanelStyle.TEXT));}
            else{
                LinearLayout values=new LinearLayout(activity);values.setGravity(Gravity.BOTTOM);values.setBaselineAligned(false);
                LinearLayout temperature=style.column(),humidity=style.column();
                String degrees=String.format(Locale.US,"%.1f°",sensor.latest.temperature);if(style.poster)degrees=degrees.replace('.',',');
                temperature.addView(style.poster?style.title(degrees,sensorsCount()>2?90:124):style.text(degrees,sensorsCount()>1?50:64,PanelStyle.ACCENT));
                temperature.addView(style.text("Температура",16,PanelStyle.MUTED));
                humidity.addView(style.poster?style.title(sensor.latest.humidity+" %",52):style.text(sensor.latest.humidity+" %",sensorsCount()>1?34:44,PanelStyle.TEXT));
                humidity.addView(style.text("Влажность",16,PanelStyle.MUTED));
                values.addView(temperature,new LinearLayout.LayoutParams(0,-2,3));values.addView(humidity,new LinearLayout.LayoutParams(0,-2,2));view.addView(values);
                style.gap(view,24);
                long cutoff=System.currentTimeMillis()-ClimateSensor.HISTORY_MS;
                List<ClimateReading> recent=new ArrayList<>();for(ClimateReading reading:sensor.history)if(reading.measuredAt>=cutoff&&reading.measuredAt<=System.currentTimeMillis())recent.add(reading);
                if(recent.size()>1){
                    view.addView(style.text("За последние 24 часа",16,PanelStyle.MUTED));
                    ClimateTrend trend=new ClimateTrend(activity,recent);style.bind(trend,s->trend.colors(s.color(PanelStyle.ACCENT),s.color(PanelStyle.MUTED),s.color(PanelStyle.LINE)));
                    view.addView(trend,new LinearLayout.LayoutParams(-1,style.dp(style.poster?108:90)));
                    double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;int low=100,high=0;
                    for(ClimateReading reading:recent){min=Math.min(min,reading.temperature);max=Math.max(max,reading.temperature);low=Math.min(low,reading.humidity);high=Math.max(high,reading.humidity);}
                    view.addView(style.text(String.format(Locale.US,"%.1f–%.1f° · %d–%d %%",min,max,low,high),16,PanelStyle.MUTED));style.gap(view,12);
                }
                view.addView(style.text(String.format(Locale.US,"Батарейка · %.2f В",sensor.latest.batteryMillivolts/1000.0),15,sensor.latest.batteryMillivolts<2400?PanelStyle.WARNING:PanelStyle.MUTED));
            }
            style.gap(view,12);age=style.text("",15,PanelStyle.MUTED);view.addView(age);state=style.text("",15,PanelStyle.MUTED);view.addView(state);
        }
        void update(){
            ClimateSensor sensor=climate.store().find(address);if(sensor==null)return;
            if(sensor.latest==null)PanelStyle.update(age,"Показаний пока нет");
            else{
                String time=new SimpleDateFormat("dd.MM · HH:mm",Locale.getDefault()).format(new Date(sensor.latest.measuredAt));
                long elapsedAge=System.currentTimeMillis()-sensor.latest.measuredAt;boolean stale=elapsedAge<0||elapsedAge>Math.max(15*60_000L,2*climate.store().pollMs());
                PanelStyle.update(age,(stale?"Последние показания · ":"Обновлено · ")+time);
                style.ink(age,stale?PanelStyle.WARNING:PanelStyle.MUTED);
            }
            String error=climate.error(address);
            String text=address.equals(climate.currentAddress())?"Обновление…":error==null?"":error;
            PanelStyle.update(state,text);style.ink(state,error==null?PanelStyle.MUTED:PanelStyle.WARNING);
            state.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
        }
    }
    private int sensorsCount(){return climate.store().sensors().size();}
    private void manage(String address){
        ClimateSensor sensor=climate.store().find(address);if(sensor==null)return;
        style.dialog().setTitle(sensor.name).setItems(new String[]{"Название комнаты","Удалить датчик"},(dialog,n)->{
            if(n==0)askName(address,true);else style.dialog().setTitle("Удалить датчик?").setMessage("Показания и локальная история этой комнаты будут удалены.").setPositiveButton("Удалить",(d,w)->climate.remove(address)).setNegativeButton("Отмена",null).show();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void askName(String address,boolean rename){
        EditText name=new EditText(activity);style.input(name);name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        ClimateSensor sensor=climate.store().find(address);name.setText(rename&&sensor!=null?sensor.name:"Комната");name.selectAll();
        LinearLayout holder=style.column();holder.setPadding(style.dp(20),style.dp(8),style.dp(20),style.dp(8));holder.addView(name);
        AlertDialog dialog=style.dialog().setTitle(rename?"Название комнаты":"Добавить датчик").setView(holder).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(name.getText().toString().trim().isEmpty()){name.setError("Введите название комнаты");return;}
            try{if(rename)climate.rename(address,name.getText().toString());else climate.add(address,name.getText().toString());dialog.dismiss();}
            catch(RuntimeException failed){name.setError("Не удалось сохранить датчик");}
        }));dialog.show();
    }
    boolean ensureBluetooth(Runnable action){
        if(!BluetoothAccess.granted(activity)){BluetoothAccess.request(activity,PERMISSION_REQUEST);return false;}
        String unavailable=BluetoothAccess.unavailable(activity);
        if(unavailable!=null){
            BluetoothAdapter adapter=BluetoothAccess.adapter(activity);
            if(adapter!=null&&!adapter.isEnabled())activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_REQUEST);
            else footer.setText(unavailable);
            return false;
        }
        action.run();return true;
    }
    void search(){
        ensureBluetooth(()->{
            if(!BluetoothAccess.locationEnabledForScan(activity)){
                style.dialog().setTitle("Поиск Bluetooth").setMessage("На этой версии Android поиск датчиков требует включённой геолокации. Координаты приложение не собирает.")
                    .setPositiveButton("Открыть настройки",(d,w)->activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Отмена",null).show();return;
            }
            discoveryButtons.clear();discoveryRows=style.column();discoveryStatus=style.text("Поиск датчиков рядом…",18,PanelStyle.MUTED);
            LinearLayout body=style.column();body.setPadding(style.dp(20),style.dp(12),style.dp(20),style.dp(12));body.addView(discoveryStatus);style.gap(body,12);
            ScrollView scroll=new ScrollView(activity);scroll.addView(discoveryRows);body.addView(scroll,new LinearLayout.LayoutParams(-1,style.dp(220)));
            discovery=style.dialog().setTitle("Xiaomi LYWSD03MMC").setView(body).setNegativeButton("Закрыть",null).create();
            discovery.setOnDismissListener(d->{climate.stopScan();discovery=null;discoveryRows=null;});discovery.show();climate.scan();updateDiscovery();
        });
    }
    private void updateDiscovery(){
        if(discovery==null||discoveryRows==null)return;
        PanelStyle.update(discoveryStatus,climate.scanning()?"Поиск датчиков рядом…":climate.message());
        for(ClimateController.Nearby nearby:climate.nearby()){
            if(discoveryButtons.containsKey(nearby.address))continue;discoveryButtons.put(nearby.address,true);
            boolean saved=climate.store().find(nearby.address)!=null;
            Button choose=style.button((saved?"Добавлен · ":"")+nearby.address+" · "+nearby.rssi+" dBm","climate_found_"+nearby.address,()->{
                AlertDialog dialog=discovery;if(dialog!=null)dialog.dismiss();askName(nearby.address,false);
            });choose.setEnabled(!saved);
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,style.dp(56));layout.bottomMargin=style.dp(8);discoveryRows.addView(choose,layout);
        }
    }
    void theme(PanelStyle updated){style=updated;}
    void tick(){long now=SystemClock.elapsedRealtime();if(now<nextTick)return;nextTick=now+30_000;if(historyWindow!=System.currentTimeMillis()/ClimateController.HISTORY_BUCKET_MS)update();else{for(Card card:displayed)card.update();if(poster!=null)poster.state(climate);}}
    void close(){if(discovery!=null)discovery.dismiss();}
}
