package local.mediacentre.client.ui;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.math.RoundingMode;
import java.util.*;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.*;
import local.mediacentre.client.radio.*;
final class HomeScreen {
    interface Actions{void open(String screen);}
    private final PanelStyle s;
    private PosterHomeView posterHome;
    private TextView date;
    private LinearLayout photoRow;
    private AudioTile radioTile,musicTile;
    private String zone,lastDate;private SimpleDateFormat dateFormat;
    private static final class AudioTile {
        TextView name,badge;FrameLayout toggle;ImageView icon;ProgressBar loading;RadioEqualizerView equalizer;
        String lastName,lastState;boolean dark;
    }
    HomeScreen(Context c){s=new PanelStyle(c);}
    void render(LinearLayout parent,Dtos.Dashboard data,Actions actions){
        zone=data.weather.timezone;dateFormat=new SimpleDateFormat("EEEE, d MMMM",new Locale("ru"));dateFormat.setTimeZone(TimeZone.getTimeZone(zone));
        if(s.poster){posterHome=new PosterHomeView(s.context,data,actions);parent.addView(posterHome,new LinearLayout.LayoutParams(-1,-1));photoRow=posterHome.photos;return;}
        LinearLayout hero=new LinearLayout(s.context);parent.addView(hero);
        LinearLayout clock=s.column();hero.addView(clock,new LinearLayout.LayoutParams(0,-2,0.78f));TextView city=s.text(data.weather.city,22,PanelStyle.MUTED);city.setMaxLines(2);clock.addView(city);
        TextClock time=new TextClock(s.context);time.setTimeZone(zone);time.setFormat12Hour("HH:mm");time.setFormat24Hour("HH:mm");time.setTextSize(96);time.setIncludeFontPadding(false);time.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));s.ink(time,PanelStyle.TEXT);time.setContentDescription("home_clock");clock.addView(time);
        date=s.text("",22,PanelStyle.TEXT);date.setContentDescription("home_date");clock.addView(date);
        LinearLayout weather=s.card();weather.setOrientation(LinearLayout.HORIZONTAL);weather.setGravity(Gravity.CENTER_VERTICAL);weather.setMinimumHeight(s.dp(206));weather.setContentDescription("open_weather");weather.setFocusable(true);weather.setOnClickListener(v->actions.open("weather"));LinearLayout.LayoutParams weatherParams=new LinearLayout.LayoutParams(0,-2,1.22f);weatherParams.leftMargin=s.dp(20);hero.addView(weather,weatherParams);
        LinearLayout current=s.column();weather.addView(current,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout now=new LinearLayout(s.context);now.setGravity(Gravity.CENTER_VERTICAL);current.addView(now);
        LinearLayout iconColumn=s.column();iconColumn.setGravity(Gravity.CENTER_HORIZONTAL);now.addView(iconColumn,new LinearLayout.LayoutParams(s.dp(94),-2));iconColumn.addView(s.weather(data.weather.condition,76),new LinearLayout.LayoutParams(s.dp(88),s.dp(76)));TextView condition=s.text(Presenters.condition(data.weather.condition),17,PanelStyle.MUTED);condition.setContentDescription("home_weather_condition");condition.setGravity(Gravity.CENTER);condition.setMaxLines(2);iconColumn.addView(condition,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout degrees=s.column();LinearLayout.LayoutParams degreeParams=new LinearLayout.LayoutParams(0,-2,1);degreeParams.leftMargin=s.dp(8);now.addView(degrees,degreeParams);degrees.addView(s.title(DisplayFormat.weather(data.weather.temperature)+"°",55));degrees.addView(s.text("Ощущается "+DisplayFormat.weather(data.weather.apparent)+"°",17,PanelStyle.MUTED));
        List<Dtos.WeatherPeriod> upcoming=WeatherScreen.upcoming(data.weather);
        if(!upcoming.isEmpty()){LinearLayout periods=s.column();LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1.2f);pp.leftMargin=s.dp(14);weather.addView(periods,pp);
            for(Dtos.WeatherPeriod period:upcoming){LinearLayout row=new LinearLayout(s.context);row.setGravity(Gravity.CENTER_VERTICAL);row.setContentDescription("home_weather_period_"+period.at);periods.addView(row,new LinearLayout.LayoutParams(-1,s.dp(43)));
                TextView label=s.text(WeatherScreen.periodLabel(period.at,data.weather.timezone),15,PanelStyle.MUTED);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                row.addView(s.weather(period.condition,26),new LinearLayout.LayoutParams(s.dp(30),s.dp(30)));
                TextView temperature=s.title(DisplayFormat.weather(period.temperature)+"°",20);temperature.setGravity(Gravity.RIGHT);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(s.dp(68),-2);tp.leftMargin=s.dp(5);row.addView(temperature,tp);
            }
        }s.gap(parent,16);
        if(!data.weather.days.isEmpty()){LinearLayout week=s.card();week.setPadding(s.dp(16),s.dp(8),s.dp(16),s.dp(8));week.setOrientation(LinearLayout.HORIZONTAL);week.setContentDescription("open_weather_week");week.setFocusable(true);week.setOnClickListener(v->actions.open("weather"));parent.addView(week);for(int n=0;n<Math.min(4,data.weather.days.size());n++){Dtos.Day d=data.weather.days.get(n);LinearLayout cell=new LinearLayout(s.context);cell.setGravity(Gravity.CENTER_VERTICAL);week.addView(cell,new LinearLayout.LayoutParams(0,s.dp(76),1));cell.addView(s.weather(d.condition,52),new LinearLayout.LayoutParams(s.dp(60),s.dp(60)));LinearLayout labels=s.column();cell.addView(labels,new LinearLayout.LayoutParams(0,-2,1));labels.addView(s.text(DisplayFormat.day(d.date,"EEE, d MMM"),16,PanelStyle.MUTED));labels.addView(s.title(DisplayFormat.weather(d.maximum)+"° / "+DisplayFormat.weather(d.minimum)+"°",22));}s.gap(parent,16);}
        LinearLayout bottom=new LinearLayout(s.context);photoRow=bottom;parent.addView(bottom);
        LinearLayout audio=s.card();audio.setPadding(s.dp(16),s.dp(10),s.dp(16),s.dp(10));audio.setMinimumHeight(s.dp(224));half(bottom,audio,false);
        radioTile=audioTile(audio,"РАДИО","radio",actions,()->{
            if(RadioPlaybackService.active()||RadioPlaybackService.starting())RadioPlaybackService.stop(s.context);
            else if(RadioStore.saved(s.context)!=null)RadioPlaybackService.play(s.context);else actions.open("stations");tick();
        });
        View divider=new View(s.context);s.flat(divider,PanelStyle.SOFT);LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,s.dp(1));dividerParams.topMargin=s.dp(8);dividerParams.bottomMargin=s.dp(8);audio.addView(divider,dividerParams);
        musicTile=audioTile(audio,"МУЗЫКА","music",actions,()->{
            if(MusicPlaybackService.track()==null)actions.open("music");
            else if(MusicPlaybackService.active())MusicPlaybackService.pause(s.context);else MusicPlaybackService.play(s.context);tick();
        });
        LinearLayout finance=s.card();finance.setMinimumHeight(s.dp(224));finance.setContentDescription("open_finance");finance.setFocusable(true);finance.setOnClickListener(v->actions.open("finance"));half(bottom,finance,true);finance.addView(s.title("Курсы валют",22));int shown=0;for(Dtos.Instrument i:data.finance.items){if(!"currency".equals(i.kind)||shown++>=3)continue;LinearLayout row=new LinearLayout(s.context);row.setGravity(Gravity.CENTER_VERTICAL);finance.addView(row);TextView value=s.text(i.symbol+"  "+homeRate(i),23,PanelStyle.TEXT);value.setContentDescription("home_finance_"+i.symbol);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));TextView asof=s.text(i.enabled&&i.price!=null?DisplayFormat.day(i.rateDate,"dd.MM.yyyy"):"—",18,PanelStyle.MUTED);row.addView(asof);}
        if(shown==0)finance.addView(s.text("Валюты не выбраны",18,PanelStyle.MUTED));tick();
    }
    private void half(LinearLayout row,LinearLayout cell,boolean gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(gap)p.leftMargin=s.dp(20);row.addView(cell,p);}
    void climate(java.util.List<local.mediacentre.client.climate.ClimateSensor> sensors){if(posterHome!=null)posterHome.climate(sensors);}
    LinearLayout photoRow(){return photoRow;}
    private static String homeRate(Dtos.Instrument instrument){return instrument.enabled&&instrument.price!=null?instrument.price.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')+" руб.":"—";}
    void tick(){
        if(posterHome!=null){posterHome.tick();return;}
        if(date==null)return;
        String today=dateFormat.format(new Date());if(!today.equals(lastDate)){lastDate=today;date.setText(today);}
        if(radioTile==null)return;
        RadioStation saved=RadioStore.saved(s.context);String radioState=RadioPlaybackService.state();boolean radioBusy=RadioPlaybackService.active();
        updateAudio(radioTile,radioBusy?RadioPlaybackService.name():saved==null?"Станция не выбрана":saved.name,radioState,radioBusy||RadioPlaybackService.starting(),RadioPlaybackService.starting(),false);
        local.mediacentre.client.music.MusicTrack track=MusicPlaybackService.track();
        updateAudio(musicTile,track==null?"Выберите музыку":track.name,MusicPlaybackService.state(),MusicPlaybackService.active(),false,true);
    }
    private AudioTile audioTile(LinearLayout parent,String heading,String destination,Actions actions,Runnable toggle){
        AudioTile tile=new AudioTile();LinearLayout row=new LinearLayout(s.context);row.setGravity(Gravity.CENTER_VERTICAL);row.setContentDescription("open_"+destination);row.setFocusable(true);row.setOnClickListener(v->actions.open(destination));parent.addView(row,new LinearLayout.LayoutParams(-1,s.dp(93)));
        tile.badge=s.badge(destination.equals("music")?"♫":"FM",40);row.addView(tile.badge,new LinearLayout.LayoutParams(s.dp(40),s.dp(40)));
        LinearLayout labels=s.column();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=s.dp(10);lp.rightMargin=s.dp(8);row.addView(labels,lp);
        LinearLayout caption=new LinearLayout(s.context);caption.setGravity(Gravity.CENTER_VERTICAL);labels.addView(caption);
        caption.addView(s.text(heading,14,PanelStyle.MUTED),new LinearLayout.LayoutParams(0,-2,1));
        tile.equalizer=new RadioEqualizerView(s.context);tile.equalizer.setContentDescription("home_"+destination+"_equalizer");s.bind(tile.equalizer,p->tile.equalizer.setColor(p.color(PanelStyle.ACCENT)));tile.equalizer.setVisibility(View.GONE);caption.addView(tile.equalizer,new LinearLayout.LayoutParams(s.dp(48),s.dp(24)));
        tile.name=s.title("",19);tile.name.setContentDescription("home_"+destination+"_name");tile.name.setSingleLine();tile.name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(tile.name);
        tile.toggle=new FrameLayout(s.context);tile.toggle.setContentDescription("home_"+destination+"_toggle");s.bind(tile.toggle,p->tile.toggle.setBackground(p.surface(p.poster?PanelStyle.SIGNAL:PanelStyle.ACCENT,p.poster?PanelStyle.SIGNAL:PanelStyle.ACCENT,100)));tile.toggle.setOnClickListener(v->toggle.run());row.addView(tile.toggle,new LinearLayout.LayoutParams(s.dp(44),s.dp(44)));
        tile.icon=new ImageView(s.context);tile.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.toggle.addView(tile.icon,new FrameLayout.LayoutParams(s.dp(24),s.dp(24),Gravity.CENTER));
        tile.loading=new ProgressBar(s.context);tile.loading.setIndeterminate(true);tile.loading.setIndeterminateDrawable(tile.loading.getIndeterminateDrawable().mutate());tile.loading.setVisibility(View.GONE);tile.loading.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);s.bind(tile.loading,p->tile.loading.getIndeterminateDrawable().setColorFilter(p.color(PanelStyle.ON_ACCENT),PorterDuff.Mode.SRC_IN));tile.toggle.addView(tile.loading,new FrameLayout.LayoutParams(s.dp(24),s.dp(24),Gravity.CENTER));
        return tile;
    }
    private void updateAudio(AudioTile tile,String name,String state,boolean active,boolean starting,boolean music){
        if(name==null)name=music?"Выберите музыку":"Станция не выбрана";
        if(!name.equals(tile.lastName)){tile.lastName=name;tile.name.setText(s.poster&&!music?PosterLabels.station(name):name);tile.badge.setText(music?"♫":PanelStyle.initials(name));}
        boolean playing="playing".equals(state),connecting=starting||"connecting".equals(state)||"retrying".equals(state);
        boolean dark=new PanelStyle(s.context).palette.dark;String controlState=state+(starting?"-starting":"");
        if(!controlState.equals(tile.lastState)||tile.dark!=dark){
            tile.lastState=controlState;tile.dark=dark;
            tile.icon.setImageDrawable(new PanelIcon(active?(music?"pause":"stop"):"play",new PanelStyle(s.context).color(s.poster&&!music?PanelStyle.ON_SIGNAL:PanelStyle.ON_ACCENT),s.dp(24)));
            tile.icon.setVisibility(connecting?View.GONE:View.VISIBLE);tile.loading.setVisibility(connecting?View.VISIBLE:View.GONE);
            tile.equalizer.setVisibility(s.poster?View.VISIBLE:playing?View.VISIBLE:View.GONE);tile.equalizer.setPlaying(playing);
        }
    }
}
