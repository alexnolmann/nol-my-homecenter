package local.mediacentre.client.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.DisplayFormat;
import local.mediacentre.client.presentation.Presenters;

final class WeatherScreen {
    private final PanelStyle s;
    WeatherScreen(Context c){s=new PanelStyle(c);}

    void render(LinearLayout parent,Dtos.Weather data){
        List<Dtos.WeatherPeriod> upcoming=upcoming(data);
        if(s.poster)posterOverview(parent,data,upcoming);else{
        LinearLayout overview=new LinearLayout(s.context);overview.setGravity(Gravity.CENTER_VERTICAL);parent.addView(overview,new LinearLayout.LayoutParams(-1,s.dp(204)));
        LinearLayout current=new LinearLayout(s.context);current.setGravity(Gravity.CENTER_VERTICAL);current.setContentDescription("weather_current");LinearLayout.LayoutParams currentParams=new LinearLayout.LayoutParams(0,-1,upcoming.isEmpty()?1:2);currentParams.rightMargin=upcoming.isEmpty()?0:s.dp(12);overview.addView(current,currentParams);
        LinearLayout iconColumn=s.column();iconColumn.setGravity(Gravity.CENTER_HORIZONTAL);current.addView(iconColumn,new LinearLayout.LayoutParams(s.dp(160),-2));iconColumn.addView(s.weather(data.condition,140),new LinearLayout.LayoutParams(s.dp(160),s.dp(140)));TextView currentCondition=s.text(Presenters.condition(data.condition),20,PanelStyle.TEXT);currentCondition.setGravity(Gravity.CENTER);iconColumn.addView(currentCondition,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout left=s.column();current.addView(left,new LinearLayout.LayoutParams(0,-2,1));left.addView(s.title(DisplayFormat.weather(data.temperature)+"°",s.poster?118:108));
        if(s.poster)current.addView(PosterPhotoView.artwork(s.context,PosterPhotoView.ANGLED),new LinearLayout.LayoutParams(s.dp(144),s.dp(192)));
        LinearLayout right=s.column();current.addView(right,new LinearLayout.LayoutParams(0,-2,1));if(data.apparent!=null)right.addView(s.text("Ощущается "+DisplayFormat.weather(data.apparent)+" °C",26,PanelStyle.TEXT));right.addView(s.text("Ветер "+DisplayFormat.weather(data.wind)+" м/с",20,PanelStyle.MUTED));right.addView(s.text("Влажность "+DisplayFormat.weather(data.humidity)+" %",20,PanelStyle.MUTED));

        if(!upcoming.isEmpty()){
            LinearLayout periods=s.column();periods.setContentDescription("weather_periods");overview.addView(periods,new LinearLayout.LayoutParams(0,-1,1));
            int rowCount=(upcoming.size()+1)/2;
            for(int rowIndex=0;rowIndex<rowCount;rowIndex++){
                LinearLayout row=new LinearLayout(s.context);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);if(rowIndex==0&&rowCount>1)rp.bottomMargin=s.dp(8);periods.addView(row,rp);
                for(int column=0;column<2;column++){
                    int index=rowIndex*2+column;
                    if(index>=upcoming.size()){row.addView(new View(s.context),new LinearLayout.LayoutParams(0,-1,1));continue;}
                    Dtos.WeatherPeriod period=upcoming.get(index);LinearLayout card=s.card();card.setPadding(s.dp(12),s.dp(8),s.dp(12),s.dp(8));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);if(column==1)cp.leftMargin=s.dp(8);row.addView(card,cp);card.setContentDescription("weather_period_"+period.at);
                    TextView label=s.text(periodLabel(period.at,data.timezone),16,PanelStyle.MUTED);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(label);
                    LinearLayout detail=new LinearLayout(s.context);detail.setGravity(Gravity.CENTER_VERTICAL);card.addView(detail);detail.addView(s.weather(period.condition,30),new LinearLayout.LayoutParams(s.dp(34),s.dp(34)));
                    TextView temperature=s.title(DisplayFormat.weather(period.temperature)+"°",24);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=s.dp(6);detail.addView(temperature,tp);
                    TextView condition=s.text(Presenters.condition(period.condition),14,PanelStyle.MUTED);condition.setSingleLine(true);condition.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams conditionParams=new LinearLayout.LayoutParams(0,-2,1);conditionParams.leftMargin=s.dp(6);detail.addView(condition,conditionParams);
                }
            }
        }

        }
        s.gap(parent,14);
        if(data.days.isEmpty()){LinearLayout empty=s.card();empty.addView(s.text("Прогноз пока не получен. Обновите данные или подождите сбора на сервере.",20,PanelStyle.MUTED));parent.addView(empty);return;}
        LinearLayout card=s.card();card.setPadding(s.dp(8),s.dp(12),s.dp(8),s.dp(12));parent.addView(card);
        HorizontalScrollView scroll=new HorizontalScrollView(s.context);scroll.setHorizontalScrollBarEnabled(false);scroll.setSmoothScrollingEnabled(true);scroll.setContentDescription("weather_week");card.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=new LinearLayout(s.context);row.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(row);
        List<LinearLayout> cells=new ArrayList<>();
        for(int i=0;i<data.days.size();i++){
            Dtos.Day day=data.days.get(i);
            if(i>0){View separator=new View(s.context);s.flat(separator,PanelStyle.LINE);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(s.dp(1),-1);row.addView(separator,sp);}
            LinearLayout cell=s.column();cell.setPadding(s.dp(6),0,s.dp(6),0);cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setContentDescription("weather_day_"+day.date);row.addView(cell,new LinearLayout.LayoutParams(s.dp(128),-2));cells.add(cell);
            TextView date=s.title(DisplayFormat.day(day.date,"EEE, d MMM"),17);date.setSingleLine(true);date.setGravity(Gravity.CENTER);cell.addView(date);
            cell.addView(s.weather(day.condition,s.poster?66:52),new LinearLayout.LayoutParams(s.dp(s.poster?80:56),s.dp(s.poster?70:52)));
            TextView condition=s.text(Presenters.condition(day.condition),15,PanelStyle.MUTED);condition.setGravity(Gravity.CENTER);condition.setMaxLines(2);condition.setEllipsize(android.text.TextUtils.TruncateAt.END);if(!s.poster)cell.addView(condition,new LinearLayout.LayoutParams(-1,s.dp(40)));
            TextView dayTemp=s.title(DisplayFormat.weather(day.maximum)+"°",s.poster?56:25);dayTemp.setContentDescription("weather_day_temp_"+day.date);dayTemp.setGravity(Gravity.CENTER);cell.addView(dayTemp,new LinearLayout.LayoutParams(-1,-2));
            TextView nightTemp=s.text("Ночью "+DisplayFormat.weather(day.minimum)+"°",s.poster?18:16,PanelStyle.MUTED);nightTemp.setSingleLine(true);nightTemp.setGravity(Gravity.CENTER);cell.addView(nightTemp,new LinearLayout.LayoutParams(-1,-2));
            TextView rain=s.text((s.poster?"◦ ":"Осадки ")+DisplayFormat.weather(day.rain)+" мм",s.poster?17:14,PanelStyle.MUTED);rain.setSingleLine(true);rain.setGravity(Gravity.CENTER);rain.setContentDescription("weather_rain_"+day.date);cell.addView(rain,new LinearLayout.LayoutParams(-1,-2));
        }
        scroll.addOnLayoutChangeListener((view,x1,y1,x2,y2,oldLeft,oldTop,oldRight,oldBottom)->{
            int width=(x2-x1-6*s.dp(1)+6)/7;
            if(width<=0)return;
            for(LinearLayout cell:cells){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)cell.getLayoutParams();if(p.width!=width){p.width=width;cell.setLayoutParams(p);}}
        });
    }

    private void posterOverview(LinearLayout parent,Dtos.Weather data,List<Dtos.WeatherPeriod> upcoming){
        android.widget.FrameLayout hero=new android.widget.FrameLayout(s.context);hero.setClipChildren(false);parent.addView(hero,new LinearLayout.LayoutParams(-1,s.dp(312)));
        View blue=new View(s.context);s.bind(blue,p->blue.setBackgroundColor(p.palette.dark?0xff102a94:0xff0738e8));hero.addView(blue);
        PosterPhotoView photo=PosterPhotoView.artwork(s.context,PosterPhotoView.HERO);hero.addView(photo);
        LinearLayout row=new LinearLayout(s.context);row.setBaselineAligned(false);hero.addView(row,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout current=s.column();current.setContentDescription("weather_current");row.addView(current,new LinearLayout.LayoutParams(0,-1,.49f));
        LinearLayout now=new LinearLayout(s.context);now.setGravity(Gravity.CENTER_VERTICAL);current.addView(now,new LinearLayout.LayoutParams(-1,0,1));TextView temp=s.title(DisplayFormat.weather(data.temperature)+"°",180);PosterTypography.fit(temp,180,130);now.addView(temp,new LinearLayout.LayoutParams(0,-1,1));now.addView(s.weather(data.condition,158),new LinearLayout.LayoutParams(s.dp(172),s.dp(172)));
        TextView condition=s.title(Presenters.condition(data.condition),34);current.addView(condition);s.gap(current,8);
        LinearLayout metrics=new LinearLayout(s.context);current.addView(metrics,new LinearLayout.LayoutParams(-1,s.dp(78)));
        String[] names={"Ощущается","Ветер","Влажность"},values={DisplayFormat.weather(data.apparent)+"°",DisplayFormat.weather(data.wind)+" м/с",DisplayFormat.weather(data.humidity)+" %"};
        int metricCount=0;for(int n=0;n<3;n++){if(n==0&&data.apparent==null)continue;if(metricCount++>0)divider(metrics);LinearLayout metric=s.column();metric.setPadding(s.dp(8),0,s.dp(6),0);metrics.addView(metric,new LinearLayout.LayoutParams(0,-1,1));metric.addView(s.text(names[n],15,PanelStyle.TEXT));TextView value=s.title(values[n],38);PosterTypography.fit(value,38,26);metric.addView(value);}
        row.addView(new View(s.context),new LinearLayout.LayoutParams(0,-1,.22f));
        LinearLayout periods=s.column();periods.setContentDescription("weather_periods");periods.setPadding(s.dp(10),s.dp(4),s.dp(4),s.dp(8));row.addView(periods,new LinearLayout.LayoutParams(0,-1,.29f));
        for(int r=0;r<2;r++){if(r==1){View line=new View(s.context);line.setBackgroundColor(0x65ffffff);periods.addView(line,new LinearLayout.LayoutParams(-1,s.dp(1)));}LinearLayout pair=new LinearLayout(s.context);periods.addView(pair,new LinearLayout.LayoutParams(-1,0,1));
            for(int col=0;col<2;col++){int index=r*2+col;if(index>=upcoming.size())continue;if(col>0){View line=new View(s.context);line.setBackgroundColor(0x65ffffff);pair.addView(line,new LinearLayout.LayoutParams(s.dp(1),-1));}Dtos.WeatherPeriod period=upcoming.get(index);LinearLayout cell=s.column();cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setContentDescription("weather_period_"+period.at);pair.addView(cell,new LinearLayout.LayoutParams(0,-1,1));TextView label=s.text(periodLabel(period.at,data.timezone).replace(" · завтра",""),17,PanelStyle.TEXT);white(label);cell.addView(label);cell.addView(s.weather(period.condition,58,true),new LinearLayout.LayoutParams(s.dp(66),s.dp(60)));TextView degrees=s.title(DisplayFormat.weather(period.temperature)+"°",48);white(degrees);cell.addView(degrees);}
        }
        hero.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int w=r-l,h=b-t;android.widget.FrameLayout.LayoutParams bp=new android.widget.FrameLayout.LayoutParams(Math.round(w*.33f),h);bp.leftMargin=Math.round(w*.67f);blue.setLayoutParams(bp);android.widget.FrameLayout.LayoutParams pp=new android.widget.FrameLayout.LayoutParams(Math.round(w*.35f),h+s.dp(58));pp.leftMargin=Math.round(w*.40f);pp.topMargin=-s.dp(58);photo.setLayoutParams(pp);});
    }
    private void white(TextView text){s.bind(text,p->text.setTextColor(android.graphics.Color.WHITE));}
    private void divider(LinearLayout row){View line=new View(s.context);s.flat(line,PanelStyle.LINE);row.addView(line,new LinearLayout.LayoutParams(s.dp(1),-1));}

    static List<Dtos.WeatherPeriod> upcoming(Dtos.Weather data){
        List<Dtos.WeatherPeriod> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(Dtos.WeatherPeriod period:data.periods){Date at=parse(period.at);if(at!=null && at.getTime()>now){result.add(period);if(result.size()==4)break;}}
        return result;
    }
    private static Date parse(String value){try{SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.parse(value);}catch(Exception ignored){return null;}}
    static String periodLabel(String at,String zone){
        Date time=parse(at);if(time==null)return "Скоро";
        TimeZone timezone=TimeZone.getTimeZone(zone);Calendar then=Calendar.getInstance(timezone);then.setTime(time);int hour=then.get(Calendar.HOUR_OF_DAY);
        String name=hour==6?"Утром":hour==12?"Днём":hour==18?"Вечером":"Ночью";
        Calendar now=Calendar.getInstance(timezone);return then.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&then.get(Calendar.DAY_OF_YEAR)==now.get(Calendar.DAY_OF_YEAR)?name:name+" · завтра";
    }
}
