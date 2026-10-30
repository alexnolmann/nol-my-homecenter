package local.mediacentre.client.ui;

import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.radio.AudioOutputController;

/** Pairing stays in Android settings; normal switching and friendly names stay in this app. */
final class AudioOutputUi {
    private AudioOutputUi(){}
    static PopupWindow dropdown(Activity activity,View anchor){
        AudioOutputController output=AudioOutputController.get(activity);PanelStyle s=new PanelStyle(activity);
        if(!AudioOutputController.legacySupported()){system(activity);return null;}
        List<BluetoothDevice> devices=output.devices();LinearLayout rows=s.column();rows.setPadding(s.dp(4),s.dp(4),s.dp(4),s.dp(4));rows.setContentDescription("audio_output_menu");rows.setBackground(s.surface(PanelStyle.CARD,PanelStyle.LINE,4));
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(rows);scroll.setVerticalScrollBarEnabled(false);
        int width=Math.min(Math.max(anchor.getWidth(),s.dp(280)),activity.getResources().getDisplayMetrics().widthPixels-s.dp(24));
        int height=Math.min(s.dp((devices.size()+2)*52+8),activity.getResources().getDisplayMetrics().heightPixels/2);
        // Give the new window the current immersive flags before transferring focus on KitKat.
        int systemUi=anchor.getRootView().getSystemUiVisibility();scroll.setSystemUiVisibility(systemUi);
        PopupWindow popup=new PopupWindow(scroll,width,height,false);popup.setBackgroundDrawable(new ColorDrawable(s.color(PanelStyle.CARD)));popup.setOutsideTouchable(true);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        for(int i=0;i<=devices.size();i++){
            final BluetoothDevice device=i==0?null:devices.get(i-1);boolean selected=device==null?!output.routedBluetooth():output.routedBluetooth()&&output.connected(device);
            String name=device==null?"Встроенные динамики":output.name(device);
            Button row=outputRow(s,name,"audio_output_option_"+i,device==null?"speaker":"bluetooth",selected,()->{popup.dismiss();if(!output.busy()){if(device!=null)output.primary(device);output.select(device);}});rows.addView(row,new LinearLayout.LayoutParams(-1,s.dp(52)));
        }
        s.rule(rows);rows.addView(outputRow(s,"Настройки Bluetooth","audio_output_system","bluetooth",false,()->{popup.dismiss();system(activity);}),new LinearLayout.LayoutParams(-1,s.dp(52)));
        popup.showAsDropDown(anchor,anchor.getWidth()-width,0);scroll.getRootView().setSystemUiVisibility(systemUi);popup.setFocusable(true);popup.update();return popup;
    }
    private static Button outputRow(PanelStyle s,String title,String id,String icon,boolean selected,Runnable action){
        Button row=s.button(title,id,action);row.setSingleLine();row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setTextSize(16);row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setPadding(s.dp(12),0,s.dp(12),0);row.setCompoundDrawablePadding(s.dp(10));row.setSelected(selected);
        s.bind(row,p->{int ink=p.color(selected?PanelStyle.ACCENT:PanelStyle.TEXT);row.setTextColor(ink);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(p.color(PanelStyle.SOFT)));states.addState(new int[]{android.R.attr.state_focused},new ColorDrawable(p.color(PanelStyle.SOFT)));states.addState(new int[]{},new ColorDrawable(selected?p.color(PanelStyle.SOFT):p.color(PanelStyle.CARD)));row.setBackground(states);row.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon(icon,ink,p.dp(22)),null,selected?new PanelIcon("check",ink,p.dp(18)):null,null);});return row;
    }
    static void quickToggle(Activity activity){
        AudioOutputController output=AudioOutputController.get(activity);
        if(output.busy())return;
        if(!AudioOutputController.legacySupported()){system(activity);return;}
        if(output.routedBluetooth()){output.select(null);return;}
        BluetoothDevice primary=output.primaryDevice();
        if(primary==null){choose(activity,false);return;}
        output.select(primary);
    }
    static void choose(Activity activity,boolean primaryOnly){
        AudioOutputController output=AudioOutputController.get(activity);PanelStyle s=new PanelStyle(activity);
        if(output.busy()){s.dialog().setTitle("Переключение аудиовыхода").setMessage("Дождитесь подключения. Stop отменяет автоматическое возобновление звука.").setPositiveButton("Закрыть",null).setNegativeButton("Отменить переключение",(d,n)->output.cancel()).show();return;}
        if(!AudioOutputController.legacySupported()){system(activity);return;}
        List<BluetoothDevice> devices=output.devices();int offset=primaryOnly?0:1;
        String[] labels=new String[devices.size()+offset];
        if(!primaryOnly)labels[0]="Встроенные динамики";
        int selected=!primaryOnly&&!output.routedBluetooth()?0:-1;
        for(int n=0;n<devices.size();n++){
            BluetoothDevice device=devices.get(n);
            labels[n+offset]=output.name(device)+(device.getAddress().equals(output.primary())?"  ★":"")+(output.connected(device)?" · подключена":"");
            if(primaryOnly&&device.getAddress().equals(output.primary())||!primaryOnly&&output.routedBluetooth()&&output.connected(device))selected=n+offset;
        }
        AlertDialog.Builder dialog=s.dialog().setTitle(primaryOnly?"Основная колонка":"Куда вывести звук")
                .setSingleChoiceItems(labels,selected,(d,n)->{
                    if(!primaryOnly&&n==0)output.select(null);
                    else{BluetoothDevice device=devices.get(n-offset);output.primary(device);if(!primaryOnly)output.select(device);}
                    d.dismiss();
                }).setNeutralButton("Спарить в Android",(d,n)->system(activity)).setNegativeButton("Закрыть",null);
        if(devices.isEmpty()&&primaryOnly)dialog.setMessage(output.bluetoothOn()?"Нет спаренных аудиоустройств. Сначала спарьте колонку в настройках Android.":"Включите Bluetooth и спарьте колонку в настройках Android.");
        dialog.show();
    }
    static void rename(Activity activity){
        AudioOutputController output=AudioOutputController.get(activity);List<BluetoothDevice> devices=output.devices();PanelStyle s=new PanelStyle(activity);
        if(devices.isEmpty()){choose(activity,true);return;}
        String[] names=new String[devices.size()];for(int n=0;n<names.length;n++)names[n]=output.name(devices.get(n));
        s.dialog().setTitle("Название колонки в приложении").setItems(names,(dialog,n)->{
            BluetoothDevice device=devices.get(n);EditText text=new EditText(activity);s.input(text);text.setSingleLine();text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});text.setText(output.name(device));text.setSelectAllOnFocus(true);
            LinearLayout box=s.column();int pad=s.dp(20);box.setPadding(pad,s.dp(8),pad,s.dp(8));box.addView(text);
            s.dialog().setTitle("Переименовать колонку").setView(box).setPositiveButton("Сохранить",(d,k)->output.rename(device,text.getText().toString())).setNeutralButton("Исходное имя",(d,k)->output.rename(device,"")).setNegativeButton("Отмена",null).show();
        }).setNegativeButton("Закрыть",null).show();
    }
    static void settings(LinearLayout parent){
        PanelStyle s=new PanelStyle(parent.getContext());Activity activity=(Activity)parent.getContext();
        s.gap(parent,s.poster?0:16);LinearLayout card=s.card();if(s.poster)card.setPadding(0,0,0,0);card.setContentDescription("audio_output_settings");parent.addView(card);
        card.addView(s.title("Аудиовыход",24));s.gap(card,8);
        if(!s.poster)card.addView(s.text("Нажмите на название аудиовыхода или стрелку вверху, чтобы открыть список динамиков и колонок.",18,PanelStyle.MUTED));else{TextView current=s.title(AudioOutputController.get(activity).label(),23);current.setTag("audio_output_current");current.setContentDescription("audio_output_current");current.setFocusable(true);current.setOnClickListener(v->quickToggle(activity));s.bind(current,p->{current.setBackground(p.surface(PanelStyle.ACCENT,-1,4));current.setTextColor(p.color(PanelStyle.ON_ACCENT));});current.setPadding(s.dp(14),s.dp(14),s.dp(14),s.dp(14));card.addView(current);}s.gap(card,12);
        LinearLayout actions=new LinearLayout(activity);if(s.poster)actions.setOrientation(LinearLayout.VERTICAL);card.addView(actions);
        Button primary=s.button("Основная колонка","audio_output_primary",()->choose(activity,true));
        Button names=s.button("Названия колонок","audio_output_rename",()->rename(activity));
        Button pairing=s.button("Настройки Bluetooth","audio_output_pairing",()->system(activity));
        for(Button b:new Button[]{primary,names,pairing}){if(s.poster){b.setTextSize(20);s.bind(b,p->{b.setTextColor(p.color(PanelStyle.TEXT));b.setBackground(new PosterRule(p.color(PanelStyle.BG),p.color(PanelStyle.LINE),p.dp(1)));});b.setGravity(android.view.Gravity.LEFT|android.view.Gravity.CENTER_VERTICAL);}LinearLayout.LayoutParams p=s.poster?new LinearLayout.LayoutParams(-1,s.dp(52)):new LinearLayout.LayoutParams(0,s.dp(52),1);if(actions.getChildCount()>0){if(s.poster)p.topMargin=s.dp(8);else p.leftMargin=s.dp(12);}actions.addView(b,p);}
        s.gap(card,10);card.addView(s.text(s.poster?"Первое сопряжение — в настройках Android.":"Сначала спарьте колонку в Android. Для обычного подключения настройки больше не нужны. Названия и выбор основной колонки сохраняются на этом планшете.",16,PanelStyle.MUTED));
    }
    private static void system(Activity activity){
        try{activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}
        catch(ActivityNotFoundException missing){Toast.makeText(activity,"Настройки Bluetooth недоступны",Toast.LENGTH_LONG).show();}
    }
}
