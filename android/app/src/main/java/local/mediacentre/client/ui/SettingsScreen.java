package local.mediacentre.client.ui;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;
import local.mediacentre.client.BuildConfig;
import local.mediacentre.client.presentation.DisplayPolicy;
final class SettingsScreen {
    interface Actions{void pairing(LinearLayout box);void displayChanged();void check(Button button);String zone();String lastData();String weatherSource();String weatherData();}
    private final PanelStyle s;
    private boolean compact;
    SettingsScreen(Context c){s=new PanelStyle(c);}
    void render(LinearLayout parent,Actions actions){
        LinearLayout columns=new LinearLayout(s.context);parent.addView(columns);
        LinearLayout display=s.card();if(s.poster){display.setPadding(s.dp(8),0,s.dp(16),s.dp(12));s.columns(columns);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,0.52f);columns.addView(display,lp);display.addView(s.title("Экран и оформление",s.poster?27:24));
        LinearLayout connection=s.card();if(s.poster)connection.setPadding(s.dp(16),0,s.dp(16),s.dp(12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,0.48f);rp.leftMargin=s.dp(20);columns.addView(connection,rp);connection.addView(s.title("Подключение",s.poster?27:24));actions.pairing(connection);
        if(s.poster){LinearLayout audio=s.column();audio.setPadding(s.dp(16),0,0,0);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,0.4f);ap.leftMargin=s.dp(16);columns.addView(audio,ap);AudioOutputUi.settings(audio);}
        SharedPreferences p=DisplaySettings.preferences(s.context);
        display.addView(s.text("Стиль интерфейса",s.poster?16:18,PanelStyle.TEXT));
        LinearLayout designs=new LinearLayout(s.context);display.addView(designs);
        String[] designValues={"classic","neo_poster"},designLabels={"Классика","Неоплакат"};
        for(int n=0;n<designValues.length;n++){
            final String design=designValues[n];Button b=s.button(designLabels[n],"design_"+design,()->{if(!design.equals(p.getString("design","classic"))){p.edit().putString("design",design).apply();actions.displayChanged();}});
            b.setSelected(design.equals(local.mediacentre.client.presentation.DesignStyle.normalize(p.getString("design","classic"))));s.repaint(b);
            LinearLayout.LayoutParams option=new LinearLayout.LayoutParams(0,s.dp(s.poster?46:52),1);if(n>0)option.leftMargin=s.dp(8);designs.addView(b,option);
        }
        s.gap(display,12);
        display.addView(s.text("Цветовой режим",s.poster?16:18,PanelStyle.TEXT));
        LinearLayout themes=new LinearLayout(s.context);display.addView(themes);String[] modes={"light","dark","auto"},labels={"Светлая","Тёмная","По часам"};Button[] choices=new Button[3];
        for(int n=0;n<3;n++){final String mode=modes[n];Button b=s.button(labels[n],"theme_"+mode,()->{p.edit().putString("theme",mode).apply();for(int i=0;i<choices.length;i++)choices[i].setSelected(modes[i].equals(mode));actions.displayChanged();});choices[n]=b;b.setSelected(mode.equals(p.getString("theme","auto")));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,s.dp(s.poster?46:52),1);if(n>0)tp.leftMargin=s.dp(6);themes.addView(b,tp);}
        display.addView(s.text("По часам — тёмная тема в ночные часы ниже.",s.poster?13:16,PanelStyle.MUTED));s.gap(display,12);
        slider(display,"Яркость окна","display_brightness","brightness",80,p,actions);
        CheckBox night=check("Ночное затемнение","display_night",p.getBoolean("night",false));display.addView(night);night.setOnCheckedChangeListener((b,v)->{p.edit().putBoolean("night",v).apply();actions.displayChanged();});
        LinearLayout times=new LinearLayout(s.context);display.addView(times);hour(times,"С ","night_start",22,"display_night_start",p,actions);hour(times,"До ","night_end",7,"display_night_end",p,actions);
        slider(display,"Ночная яркость","display_night_brightness","night_brightness",10,p,actions);
        CheckBox keep=check("Не гасить экран на питании","display_keep_screen",p.getBoolean("keep_screen",true));display.addView(keep);keep.setOnCheckedChangeListener((b,v)->{p.edit().putBoolean("keep_screen",v).apply();actions.displayChanged();});
        display.addView(s.text("От батареи действует системный таймаут экрана.",s.poster?13:16,PanelStyle.MUTED));
        CheckBox boot=check("Открывать после включения","display_autostart",p.getBoolean("autostart",false));display.addView(boot);boot.setEnabled(Build.VERSION.SDK_INT<29);boot.setOnCheckedChangeListener((b,v)->p.edit().putBoolean("autostart",v).apply());
        display.addView(s.text(Build.VERSION.SDK_INT<29?"Автозапуск открывает панель без звука. Радио включается кнопкой «Слушать».":"Android 10+ ограничивает открытие из фона. Запускайте панель значком приложения.",16,PanelStyle.MUTED));
        display.addView(s.text("Ночные часы: "+actions.zone()+". Яркость других приложений не меняется.",16,PanelStyle.MUTED));
        if(!s.poster)AudioOutputUi.settings(parent);
        s.gap(parent,16);diagnostics(parent,actions,true);
    }
    void diagnostics(LinearLayout parent,Actions actions,boolean includeCheck){
        LinearLayout diagnostics=s.card();if(!includeCheck)diagnostics.setPadding(0,s.dp(12),0,s.dp(12));diagnostics.setContentDescription("settings_diagnostics");parent.addView(diagnostics);diagnostics.addView(s.title("Версия и диагностика",22));
        diagnostics.addView(s.text("Media Centre "+BuildConfig.VERSION_NAME+" · Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+") · "+Build.MODEL,18,PanelStyle.TEXT));
        Intent battery=s.context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(battery!=null){int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100),temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);String label="Батарея: "+(level>=0&&scale>0?level*100/scale+" %":"—")+(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0?" · питание подключено":" · от батареи");if(temp>=0)label+=String.format(Locale.US," · %.1f °C",temp/10f);diagnostics.addView(s.text(label,16,PanelStyle.MUTED));}
        TextView weather=s.text("Источник погоды: "+actions.weatherSource(),16,PanelStyle.MUTED);weather.setContentDescription("settings_weather_source");diagnostics.addView(weather);
        TextView weatherData=s.text("Погода: "+actions.weatherData(),16,PanelStyle.MUTED);weatherData.setContentDescription("settings_weather_data_status");diagnostics.addView(weatherData);
        TextView data=s.text("Главная: "+actions.lastData(),16,PanelStyle.MUTED);data.setContentDescription("settings_home_data_status");diagnostics.addView(data);if(includeCheck){Button check=s.button("Проверить сервер","settings_check",()->{});check.setOnClickListener(v->actions.check(check));diagnostics.addView(check);}
    }
    void posterDisplay(LinearLayout display,Actions actions){
        compact=true;SharedPreferences p=DisplaySettings.preferences(s.context);
        display.addView(s.text("Тема оформления",16,PanelStyle.TEXT));
        options(display,new String[]{"light","dark","auto"},new String[]{"Светлая","Тёмная","По часам"},"theme",p,actions);
        s.gap(display,10);LinearLayout designs=new LinearLayout(s.context);designs.setGravity(Gravity.CENTER_VERTICAL);display.addView(designs);
        TextView style=s.text("Стиль",15,PanelStyle.MUTED);designs.addView(style,new LinearLayout.LayoutParams(s.dp(58),s.dp(40)));
        options(designs,new String[]{"classic","neo_poster"},new String[]{"Классика","Неоплакат"},"design",p,actions);
        s.gap(display,10);slider(display,"Яркость","display_brightness","brightness",80,p,actions);s.rule(display);
        CheckBox night=check("Ночное затемнение","display_night",p.getBoolean("night",false));display.addView(night);night.setOnCheckedChangeListener((b,v)->{p.edit().putBoolean("night",v).apply();actions.displayChanged();});
        LinearLayout times=new LinearLayout(s.context);display.addView(times);hour(times,"С ","night_start",22,"display_night_start",p,actions);hour(times,"До ","night_end",7,"display_night_end",p,actions);
        slider(display,"Ночная яркость","display_night_brightness","night_brightness",10,p,actions);s.rule(display);
        CheckBox keep=check("Не гасить экран на питании","display_keep_screen",p.getBoolean("keep_screen",true));display.addView(keep);keep.setOnCheckedChangeListener((b,v)->{p.edit().putBoolean("keep_screen",v).apply();actions.displayChanged();});
        display.addView(s.text("От батареи действует системный таймаут.",13,PanelStyle.MUTED));s.rule(display);
        CheckBox boot=check("Автозапуск","display_autostart",p.getBoolean("autostart",false));display.addView(boot);boot.setEnabled(Build.VERSION.SDK_INT<29);boot.setOnCheckedChangeListener((b,v)->p.edit().putBoolean("autostart",v).apply());
        display.addView(s.text(Build.VERSION.SDK_INT<29?"Открывать панель при включении устройства, без запуска звука.":"Android ограничивает открытие из фона.",13,PanelStyle.MUTED));
        s.gap(display,8);display.addView(s.text("По часам — тёмная тема в ночные часы. Часовой пояс: "+actions.zone()+". Яркость других приложений не меняется.",13,PanelStyle.MUTED));
    }
    private void options(LinearLayout parent,String[] values,String[] labels,String key,SharedPreferences p,Actions actions){
        LinearLayout row=new LinearLayout(s.context);parent.addView(row,parent.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,s.dp(40),1):new LinearLayout.LayoutParams(-1,s.dp(56)));Button[] buttons=new Button[values.length];
        for(int n=0;n<values.length;n++){final String value=values[n];Button button=s.button(labels[n],key+"_"+value,()->{if(value.equals(p.getString(key,key.equals("theme")?"auto":"classic")))return;p.edit().putString(key,value).apply();for(int k=0;k<buttons.length;k++)buttons[k].setSelected(values[k].equals(value));actions.displayChanged();});buttons[n]=button;button.setTextSize(key.equals("theme")?18:15);button.setMinHeight(0);button.setSelected(value.equals(p.getString(key,key.equals("theme")?"auto":"classic")));
            s.bind(button,t->{button.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_pressed},new int[]{android.R.attr.state_selected},new int[]{}},new int[]{Color.WHITE,Color.WHITE,t.color(PanelStyle.TEXT)}));StateListDrawable states=new StateListDrawable();GradientDrawable pressed=t.surface(PanelStyle.ACCENT,-1,4),selected=t.surface(PanelStyle.ACCENT,-1,4);pressed.setColor(t.palette.dark?0xff315de0:0xff0024ae);if(t.palette.dark)selected.setColor(0xff2548b8);states.addState(new int[]{android.R.attr.state_pressed},pressed);states.addState(new int[]{android.R.attr.state_selected},selected);states.addState(new int[]{},t.surface(PanelStyle.CARD,PanelStyle.LINE,4));button.setBackground(states);});
            if("theme".equals(key)){StateListDrawable mark=new StateListDrawable();mark.addState(new int[]{android.R.attr.state_pressed},new ThemeMark(value,Color.WHITE,s.dp(20)));mark.addState(new int[]{android.R.attr.state_selected},new ThemeMark(value,Color.WHITE,s.dp(20)));mark.addState(new int[]{},new ThemeMark(value,s.color(PanelStyle.TEXT),s.dp(20)));button.setCompoundDrawablesWithIntrinsicBounds(mark,null,null,null);button.setCompoundDrawablePadding(s.dp(5));}
            LinearLayout.LayoutParams option=new LinearLayout.LayoutParams(0,-1,1);if(n>0)option.leftMargin=s.dp(6);row.addView(button,option);
        }
    }
    private static final class ThemeMark extends Drawable {
        final String mode;final int size;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path moon=new Path();
        ThemeMark(String mode,int color,int size){this.mode=mode;this.size=size;p.setColor(color);}
        public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);
            if("light".equals(mode)){c.drawCircle(12,12,4,p);for(int n=0;n<8;n++){c.save();c.rotate(n*45,12,12);c.drawLine(12,2,12,4,p);c.restore();}}
            else if("dark".equals(mode)){p.setStyle(Paint.Style.FILL);moon.rewind();moon.moveTo(15,2);moon.cubicTo(4,0,0,15,9,21);moon.cubicTo(15,25,23,19,22,14);moon.cubicTo(13,19,8,9,15,2);moon.close();c.drawPath(moon,p);}
            else{c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,17,15,p);}c.restoreToCount(save);
        }
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private CheckBox check(String label,String id,boolean selected){CheckBox c=s.poster?new PosterSwitch(s.context):new CheckBox(s.context);c.setText(label);c.setTextSize(18);s.checkbox(c);if(s.poster)c.setTextSize(16);c.setContentDescription(id);c.setChecked(selected);c.setMinHeight(s.dp(compact?44:52));return c;}
    private void slider(LinearLayout box,String title,String id,String key,int fallback,SharedPreferences p,Actions actions){
        TextView label=s.text(title+": "+DisplayPolicy.percent(p.getInt(key,fallback))+" %",s.poster?16:18,PanelStyle.TEXT);box.addView(label);SeekBar seek=s.slider();seek.setMax(95);seek.setProgress(DisplayPolicy.percent(p.getInt(key,fallback))-5);seek.setContentDescription(id);box.addView(seek,new LinearLayout.LayoutParams(-1,s.dp(compact?36:s.poster?46:52)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){label.setText(title+": "+(value+5)+" %");if(user){p.edit().putInt(key,value+5).apply();actions.displayChanged();}}});
    }
    private void hour(LinearLayout row,String prefix,String key,int fallback,String id,SharedPreferences p,Actions actions){Button b=s.button(prefix+String.format(Locale.US,"%02d:00",DisplaySettings.hour(p,key,fallback)),id,()->{});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,s.dp(compact?36:s.poster?46:52),1);lp.rightMargin=s.dp(6);row.addView(b,lp);b.setOnClickListener(v->{String[] hours=new String[24];for(int h=0;h<24;h++)hours[h]=String.format(Locale.US,"%02d:00",h);new PanelStyle(s.context).dialog().setTitle(prefix.equals("С ")?"Начало ночного режима":"Конец ночного режима").setSingleChoiceItems(hours,DisplaySettings.hour(p,key,fallback),(dialog,n)->{p.edit().putInt(key,n).apply();b.setText(prefix+hours[n]);actions.displayChanged();dialog.dismiss();}).setNegativeButton("Отмена",null).show();});}
}
