package local.mediacentre.client.ui;
import android.content.Context;
import android.widget.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.radio.*;
final class RadioScreen {
    interface Actions{void play();void stop();void favorite(Dtos.Station station,Button button);void cycle(int direction);void volume(int value);void openCatalogue();}
    private final PanelStyle s;
    private PosterRadioView poster;
    static final int WAVEFORM_PERMISSION=742;
    private RadioWaveformView visualizer;
    private Button waveformPermission;
    private VolumeButton mute;
    private SeekBar volumeSlider;
    private TextView volumeLabel;
    private String volumePrefix;
    void tick(){
        if(poster!=null){poster.tick();return;}
        syncVolume();
        boolean allowed=RadioPlaybackService.waveformAllowed(s.context);
        if(waveformPermission!=null)waveformPermission.setVisibility(allowed?android.view.View.GONE:android.view.View.VISIBLE);
        if(visualizer!=null)visualizer.setPlaying(allowed&&"playing".equals(RadioPlaybackService.state()));
    }
    void pause(){if(poster!=null)poster.pause();if(visualizer!=null)visualizer.setPlaying(false);}
    void restore(){if(poster!=null){poster.restore();poster=null;}}
    RadioScreen(Context c){s=new PanelStyle(c);}
    TextView render(LinearLayout parent,Dtos.Station station,Dtos.Catalogue favorites,String state,Actions a,TextClock clock,AudioOutputControls output,android.view.View footer){
        if(s.poster){poster=new PosterRadioView(s.context,station,favorites,a,clock,output,footer,state);parent.addView(poster,new LinearLayout.LayoutParams(-1,-1));tick();return poster.state;}
        LinearLayout card=s.card();if(s.poster)card.setPadding(s.dp(12),s.dp(8),s.dp(12),s.dp(8));parent.addView(card);
        LinearLayout identity=new LinearLayout(s.context);identity.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(identity);identity.addView(s.badge(station==null?null:station.name,112),new LinearLayout.LayoutParams(s.dp(112),s.dp(112)));LinearLayout labels=s.column();LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);labelParams.leftMargin=s.dp(28);identity.addView(labels,labelParams);
        labels.addView(s.text("ИНТЕРНЕТ-РАДИО",16,PanelStyle.MUTED));TextView name=s.title(station==null?"Станция не выбрана":station.name,s.poster?48:32);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setContentDescription("radio_station_name");labels.addView(name);
        if(s.poster){LinearLayout.LayoutParams picture=new LinearLayout.LayoutParams(s.dp(276),s.dp(140));picture.leftMargin=s.dp(20);identity.addView(PosterPhotoView.artwork(s.context,PosterPhotoView.ANGLED),picture);}
        labels.addView(s.text(station==null?"Откройте каталог радиостанций":station.genre+(station.enabled?"":" · отключена в каталоге"),20,PanelStyle.MUTED));
        TextView stateView=s.text(state,s.poster?16:20,PanelStyle.ACCENT);stateView.setContentDescription("radio_state");labels.addView(stateView);s.gap(card,s.poster?12:28);
        LinearLayout transport=new LinearLayout(s.context);transport.setGravity(android.view.Gravity.CENTER);card.addView(transport);
        Button previous=new RadioTransportButton(s.context,RadioTransportButton.PREVIOUS),play=new RadioTransportButton(s.context,RadioTransportButton.PLAY),stop=new RadioTransportButton(s.context,RadioTransportButton.STOP),next=new RadioTransportButton(s.context,RadioTransportButton.NEXT);
        previous.setContentDescription("radio_previous");play.setContentDescription("radio_play");stop.setContentDescription("radio_stop");next.setContentDescription("radio_next");
        previous.setOnClickListener(v->a.cycle(-1));play.setOnClickListener(v->a.play());stop.setOnClickListener(v->a.stop());next.setOnClickListener(v->a.cycle(1));
        s.transportStyle(previous,false);s.transportStyle(play,!s.poster);s.transportStyle(stop,s.poster);s.transportStyle(next,false);
        previous.setEnabled(favorites!=null && !favorites.stations.isEmpty());next.setEnabled(previous.isEnabled());play.setEnabled(station!=null && station.enabled);
        for(Button b:new Button[]{previous,play,stop,next}){int size=s.poster?(b==stop?80:64):(b==play?88:72);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(s.dp(size),s.dp(size));if(transport.getChildCount()>0)p.leftMargin=s.dp(16);transport.addView(b,p);}
        s.gap(card,16);volumeControls(card,a,false);
        s.gap(card,12);LinearLayout actions=new LinearLayout(s.context);card.addView(actions);
        Button star=s.button(station!=null && station.favorite?"★ В избранном":"☆ В избранное","radio_favorite",()->{});star.setEnabled(station!=null && (station.enabled||station.favorite));star.setOnClickListener(v->a.favorite(station,star));
        Button catalogue=s.button("Каталог станций →","radio_catalogue",a::openCatalogue);
        actions.addView(star,new LinearLayout.LayoutParams(0,s.dp(56),1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,s.dp(56),1);p.leftMargin=s.dp(12);actions.addView(catalogue,p);
        visualizer=new RadioWaveformView(s.context);
        s.bind(visualizer,palette->visualizer.setColor(palette.color(palette.poster?PanelStyle.SIGNAL:PanelStyle.ACCENT)));
        LinearLayout.LayoutParams visualizerLayout=new LinearLayout.LayoutParams(-1,s.dp(152));visualizerLayout.topMargin=s.dp(20);
        FrameLayout waveformArea=new FrameLayout(s.context);
        waveformArea.addView(visualizer,new FrameLayout.LayoutParams(-1,-1));
        waveformPermission=s.button("Разрешить визуализацию","radio_waveform_permission",()->{
            if(android.os.Build.VERSION.SDK_INT>=23&&s.context instanceof android.app.Activity)
                ((android.app.Activity)s.context).requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},WAVEFORM_PERMISSION);
        });
        FrameLayout.LayoutParams permissionLayout=new FrameLayout.LayoutParams(s.dp(280),s.dp(56),android.view.Gravity.CENTER);
        waveformArea.addView(waveformPermission,permissionLayout);
        parent.addView(waveformArea,visualizerLayout);tick();
        return stateView;
    }
    private void plain(Button button){s.plain(button,false);}

    private void volumeControls(LinearLayout parent,Actions actions,boolean poster){
        volumePrefix=poster?"Громкость  ":"Громкость приложения · ";
        volumeLabel=s.text("",poster?19:20,PanelStyle.TEXT);
        if(!poster)parent.addView(volumeLabel);
        LinearLayout row=new LinearLayout(s.context);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        parent.addView(row,new LinearLayout.LayoutParams(-1,s.dp(poster?68:60)));
        mute=new VolumeButton(s.context,R.id.radio_mute,()->{AudioPlayback.toggleMute(s.context);syncVolume();});plain(mute);
        row.addView(mute,new LinearLayout.LayoutParams(s.dp(52),s.dp(56)));
        if(poster)row.addView(volumeLabel,new LinearLayout.LayoutParams(s.dp(200),-2));
        volumeSlider=s.slider();volumeSlider.setMax(100);volumeSlider.setContentDescription("radio_volume");
        row.addView(volumeSlider,new LinearLayout.LayoutParams(0,s.dp(54),1));
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int value,boolean user){if(user){AudioPlayback.volume(s.context,value);syncVolume();}}
            public void onStopTrackingTouch(SeekBar b){actions.volume(b.getProgress());}
        });
        syncVolume();
    }
    private void syncVolume(){
        if(volumeSlider==null)return;int level=RadioStore.volume(s.context);
        PanelStyle.update(volumeLabel,volumePrefix+level+" %");
        if(!volumeSlider.isPressed())volumeSlider.setProgress(level);
        mute.refresh();
    }

}
