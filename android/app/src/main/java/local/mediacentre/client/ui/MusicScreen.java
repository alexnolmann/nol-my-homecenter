package local.mediacentre.client.ui;
import android.app.Activity;
import android.os.Handler;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.ApiClient;
import local.mediacentre.client.music.*;
import local.mediacentre.client.radio.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
/** Fetches one catalogue page at a time; only the selected folder becomes a bounded queue. */
final class MusicScreen {
    private final Activity activity;private PanelStyle style;private final Handler handler=new Handler();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(4));
    private final TextView footer;
    private LinearLayout content,controls,rootContent,rootControls;
    private PosterMusicView poster;private TextClock headerClock;private AudioOutputControls audioOutput;private View refresh,statusRoot;private TextView serviceStatus;private boolean recursiveValue;
    private ListView listing;private BaseAdapter adapter;
    private final List<JSONObject> directories=new ArrayList<>();
    private TextView now,artist,time,path;
    private PosterMusicArt posterArt;private MusicTransportButton play,repeat;private Button shuffle,playFolder;
    private Boolean styledShuffle;private int styledRepeat=-1;private String drawnIcon="",drawnTrack="";
    private SeekBar position,volume;
    private VolumeButton mute;
    private CheckBox recursive;
    private String library,folder,parent,shownLibrary,shownFolder,folderName="Библиотеки";
    private long generation;private int offset,total,folderTotal,epoch;
    private boolean closed,busy,queueBusy,reusingCatalogue;
    private final List<MusicTrack> visible=new ArrayList<>();
    private ScrollView scroll;
    MusicScreen(Activity activity,TextView footer){this.activity=activity;style=new PanelStyle(activity);this.footer=footer;}
    private ApiClient api()throws Exception{
        android.content.SharedPreferences p=activity.getSharedPreferences("connection",0);
        return new ApiClient(activity,p.getString("base_url",""),p.getString("token",""),p.getString("server_id",null));
    }
    void render(LinearLayout content,LinearLayout controls,TextClock clock,AudioOutputControls output,View refresh,View statusRoot,TextView serviceStatus){
        this.content=content;this.controls=controls;rootContent=content;rootControls=controls;headerClock=clock;audioOutput=output;this.refresh=refresh;this.statusRoot=statusRoot;this.serviceStatus=serviceStatus;
        if(style.poster){renderPoster();return;}
        LinearLayout player=style.card();
        if(style.poster){LinearLayout split=new LinearLayout(activity);split.setBaselineAligned(false);content.addView(split,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,0.85f);pp.rightMargin=style.dp(20);split.addView(player,pp);this.content=style.column();split.addView(this.content,new LinearLayout.LayoutParams(0,-2,1.25f));posterArt=new PosterMusicArt(activity);player.addView(posterArt,new LinearLayout.LayoutParams(-1,style.dp(244)));style.gap(player,12);}
        else{controls.addView(player,new LinearLayout.LayoutParams(-1,-2));style.gap(controls,12);}
        now=style.title("",style.poster?46:25);now.setMaxLines(1);now.setEllipsize(TextUtils.TruncateAt.END);now.setContentDescription("music_current");player.addView(now);
        artist=style.text("",style.poster?22:16,PanelStyle.MUTED);artist.setSingleLine();artist.setEllipsize(TextUtils.TruncateAt.END);player.addView(artist);
        LinearLayout timeline=new LinearLayout(activity);timeline.setGravity(Gravity.CENTER_VERTICAL);position=style.slider();position.setContentDescription("music_position");timeline.addView(position,new LinearLayout.LayoutParams(0,style.dp(38),1));time=style.text("",16,PanelStyle.MUTED);time.setGravity(Gravity.RIGHT);timeline.addView(time,new LinearLayout.LayoutParams(style.dp(145),-2));player.addView(timeline);
        LinearLayout buttons=new LinearLayout(activity);buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.addView(transport("previous","Предыдущий трек",()->MusicPlaybackService.previous(activity)));
        play=transport("play","Воспроизвести / пауза",()->{if(MusicPlaybackService.track()==null)startFolder(null);else if(MusicPlaybackService.active())MusicPlaybackService.pause(activity);else MusicPlaybackService.play(activity);});buttons.addView(play);
        buttons.addView(transport("stop","Остановить",()->MusicPlaybackService.stop(activity)));
        buttons.addView(transport("next","Следующий трек",()->MusicPlaybackService.next(activity)));
        shuffle=style.button("⇄","music_shuffle",()->{MusicPlaybackService.shuffle(activity);tick();});buttons.addView(shuffle,new LinearLayout.LayoutParams(style.dp(66),style.dp(52)));
        repeat=new MusicTransportButton(activity,"repeat");style.buttonStyle(repeat,false);repeat.setOnClickListener(v->{MusicPlaybackService.repeat(activity);tick();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(style.dp(66),style.dp(52));rp.leftMargin=style.dp(8);buttons.addView(repeat,rp);
        LinearLayout volumeRow=style.poster?new LinearLayout(activity):buttons;
        mute=new VolumeButton(activity,R.id.music_mute,()->{AudioPlayback.toggleMute(activity);tick();});style.plain(mute,false);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(style.dp(52),style.dp(52));mp.leftMargin=style.dp(style.poster?0:16);volumeRow.addView(mute,mp);
        TextView speaker=style.text("Громкость",15,PanelStyle.MUTED);volumeRow.addView(speaker,new LinearLayout.LayoutParams(-2,-2));
        volume=style.slider();volume.setMax(100);volume.setProgress(RadioStore.volume(activity));volume.setContentDescription("music_volume");volumeRow.addView(volume,new LinearLayout.LayoutParams(0,style.dp(48),1));volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(user){AudioPlayback.volume(activity,v);mute.refresh();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        player.addView(buttons);if(style.poster){volumeRow.setGravity(Gravity.CENTER_VERTICAL);player.addView(volumeRow);}
        position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){MusicPlaybackService.seek(activity,b.getProgress());}});
        if(content.getParent() instanceof ScrollView)scroll=(ScrollView)content.getParent();
        if(library==null)libraries();else loadPage(true);tick();
    }
    private void renderPoster(){
        poster=new PosterMusicView(activity,headerClock,audioOutput,refresh,statusRoot,serviceStatus,recursiveValue,new PosterMusicView.Actions(){
            public void toggle(){if(MusicPlaybackService.track()==null)startFolder(null);else if(MusicPlaybackService.active())MusicPlaybackService.pause(activity);else MusicPlaybackService.play(activity);tick();}
            public void previous(){MusicPlaybackService.previous(activity);tick();}public void stop(){MusicPlaybackService.stop(activity);tick();}public void next(){MusicPlaybackService.next(activity);tick();}
            public void shuffle(){MusicPlaybackService.shuffle(activity);tick();}public void repeat(){MusicPlaybackService.repeat(activity);tick();}
            public void up(){if(parent==null)libraries();else{folder=parent;loadPage(true);}}public void playFolder(){startFolder(null);}
            public void recursive(boolean value){recursiveValue=value;if(library!=null)loadPage(true);}public void seek(int value){MusicPlaybackService.seek(activity,value);tick();}
        });rootContent.addView(poster,new LinearLayout.LayoutParams(-1,-1));content=poster.catalogue;recursive=poster.recursive;playFolder=poster.playFolder;scroll=null;
        if(reusingCatalogue){poster.folder(parent==null?"Все треки":folderName);buildList(null);playFolder.setEnabled(total>0&&!queueBusy);listing.setEnabled(!busy);}else if(library==null)libraries();else loadPage(true);tick();
    }
    void restore(){if(poster!=null){poster.restore();poster=null;}}
    void retheme(){
        final Parcelable position=listing==null?null:listing.onSaveInstanceState();reusingCatalogue=listing!=null&&library!=null&&generation>0;
        recursiveValue=recursive!=null&&recursive.isChecked();restore();if(listing!=null){listing.setOnScrollListener(null);listing.setAdapter(null);}listing=null;adapter=null;recursive=null;now=null;posterArt=null;styledShuffle=null;styledRepeat=-1;drawnTrack=drawnIcon="";scroll=null;
        rootContent.removeAllViews();rootControls.removeAllViews();style=new PanelStyle(activity);try{render(rootContent,rootControls,headerClock,audioOutput,refresh,statusRoot,serviceStatus);}finally{reusingCatalogue=false;}
        if(position!=null&&listing!=null){final ListView target=listing;target.post(()->{if(!closed&&target==listing&&target.isAttachedToWindow())target.onRestoreInstanceState(position);});}
    }
    private MusicTransportButton transport(String icon,String label,Runnable action){
        MusicTransportButton b=new MusicTransportButton(activity,icon);b.setContentDescription(label);b.setOnClickListener(v->{action.run();tick();});style.transportStyle(b,icon.equals("play"));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(style.dp(56),style.dp(52));p.rightMargin=style.dp(8);b.setLayoutParams(p);return b;
    }
    void tick(){
        if(closed)return;if(poster!=null){poster.tick();MusicTrack song=MusicPlaybackService.track();String signature=(song==null?"":song.id)+":"+MusicPlaybackService.selected()+":"+MusicPlaybackService.state();if(!signature.equals(drawnTrack)){drawnTrack=signature;if(adapter!=null)adapter.notifyDataSetChanged();}if("unavailable".equals(MusicPlaybackService.state()))PanelStyle.update(footer,"Поток недоступен · проверьте сервер и сеть, затем повторите запуск.");return;}if(now==null)return;PanelStyle updated=new PanelStyle(activity);boolean themeChanged=updated.palette.dark!=style.palette.dark;style=updated;MusicTrack song=MusicPlaybackService.track();String state=MusicPlaybackService.state();
        PanelStyle.update(now,song==null?(style.poster?"Выберите трек":"Выберите папку или трек"):song.name);PanelStyle.update(artist,song==null?"MP3 с домашнего сервера":song.artist.isEmpty()?song.album:song.artist+(song.album.isEmpty()?"":" · "+song.album));
        if(posterArt!=null)posterArt.track(song==null?null:song.name,song==null?null:song.artist);
        boolean active=MusicPlaybackService.active();String icon="playing".equals(state)?"pause":active?"loading":"play";
        if(!icon.equals(drawnIcon)){drawnIcon=icon;play.symbol(icon);play.setContentDescription(active?"Пауза":"Воспроизвести");}
        int current=MusicPlaybackService.position(),duration=MusicPlaybackService.duration();position.setMax(Math.max(1,duration));position.setEnabled(duration>0);if(!position.isPressed())position.setProgress(current);
        PanelStyle.update(time,clock(current)+" / "+clock(duration));
        boolean shuffled=MusicPlaybackService.shuffled();if(styledShuffle==null||styledShuffle!=shuffled||themeChanged){styledShuffle=shuffled;style.buttonStyle(shuffle,shuffled);shuffle.setContentDescription(shuffled?"Случайный порядок включён":"Случайный порядок выключен");}
        int mode=MusicPlaybackService.repeat();if(styledRepeat!=mode||themeChanged){styledRepeat=mode;repeat.symbol(mode==MusicQueue.ONE?"repeat_one":"repeat");repeat.setContentDescription(mode==MusicQueue.OFF?"Повтор выключен":mode==MusicQueue.ALL?"Повтор всей очереди":"Повтор одного трека");style.buttonStyle(repeat,mode!=MusicQueue.OFF);}
        String currentTrack=song==null?"":song.id+":"+MusicPlaybackService.selected()+":"+state;
        if(!currentTrack.equals(drawnTrack)){drawnTrack=currentTrack;if(adapter!=null)adapter.notifyDataSetChanged();}
        if(!volume.isPressed())volume.setProgress(RadioStore.volume(activity));
        mute.refresh();
        if("unavailable".equals(state))PanelStyle.update(footer,"Поток недоступен · проверьте сервер и сеть, затем повторите запуск.");
    }
    private static String clock(int value){int seconds=Math.max(0,value)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private void schedule(Runnable task){if(closed)return;worker.getQueue().clear();try{worker.execute(task);}catch(RejectedExecutionException stopped){busy=queueBusy=false;}}
    void reload(){if(busy||queueBusy)return;if(library==null)libraries();else loadPage(true);}
    private void libraries(){
        final int request=++epoch;library=null;shownLibrary=shownFolder=null;queueBusy=false;busy=true;content.removeAllViews();if(poster!=null)poster.libraries();footer.setText("Загрузка музыкальных библиотек…");
        schedule(()->{try{
            ApiClient.Response response=api().fetch("/api/v1/music/libraries",null);if(response.code!=200)throw new ApiClient.HttpError(response.code);
            JSONArray items=new JSONObject(response.body).getJSONObject("data").getJSONArray("items");
            handler.post(()->{if(closed||request!=epoch)return;busy=false;content.removeAllViews();
                if(items.length()==1){library=items.optJSONObject(0).optString("id");folder=null;generation=0;loadPage(true);return;}
                if(items.length()==0){content.addView(style.text("Добавьте каталог с MP3 в веб-панели → Музыка.",21,PanelStyle.MUTED));footer.setText("Библиотек пока нет.");return;}
                for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String id=item.optString("id");String label=item.optString("name");Button button=style.button("♫  "+label+"  ·  "+item.optInt("tracks")+" треков","music_library",()->{library=id;folder=null;generation=0;loadPage(true);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,style.dp(64));p.bottomMargin=style.dp(10);content.addView(button,p);}
                footer.setText("Выберите библиотеку.");
            });
        }catch(Exception error){failed(request,error);}});
    }
    private String browsePath(String lib,String selected,long gen,int offset,boolean children)throws Exception{
        return "/api/v1/music/browse?library="+ApiClient.encode(lib)+(selected==null?"":"&folder="+ApiClient.encode(selected))+(gen>0?"&generation="+gen:"")+"&limit=50&offset="+offset+"&recursive="+(children?"1":"0");
    }
    private void loadPage(boolean reset){
        if(closed||busy&& !reset||library==null)return;final int request=reset?++epoch:epoch;final String lib=library,selected=folder;final long gen=reset?0:generation;final int start=reset?0:offset;final boolean children=recursive!=null?recursive.isChecked():recursiveValue;
        if(reset){queueBusy=false;if(listing!=null)listing.setEnabled(false);}busy=true;footer.setText("Загрузка каталога…");
        schedule(()->{try{
            ApiClient.Response response=api().fetch(browsePath(lib,selected,gen,start,children),null);if(response.code!=200)throw new ApiClient.HttpError(response.code);
            JSONObject data=new JSONObject(response.body).getJSONObject("data");JSONArray incoming=data.getJSONArray("items"),dirs=data.getJSONArray("folders");List<MusicTrack> tracks=new ArrayList<>();for(int i=0;i<incoming.length();i++)tracks.add(new MusicTrack(incoming.getJSONObject(i)));
            handler.post(()->{if(closed||request!=epoch)return;busy=false;
                generation=data.optLong("generation");folder=data.optString("folder");parent=data.isNull("parent")?null:data.optString("parent");folderName=data.optString("name");shownLibrary=library;shownFolder=folder;total=data.optInt("total");folderTotal=data.optInt("folder_total",dirs.length());offset=start+50;
                if(reset){
                    visible.clear();directories.clear();content.removeAllViews();if(scroll!=null)scroll.scrollTo(0,0);if(poster!=null){poster.folder(parent==null?"Все треки":folderName);recursive=poster.recursive;playFolder=poster.playFolder;buildList(null);}else{LinearLayout tools=new LinearLayout(activity);tools.setGravity(Gravity.CENTER_VERTICAL);
                    Button back=style.button("‹","music_up",()->{if(parent==null)libraries();else{folder=parent;loadPage(true);}});tools.addView(back,new LinearLayout.LayoutParams(style.dp(56),style.dp(52)));
                    path=style.title(folderName,20);path.setSingleLine();path.setEllipsize(TextUtils.TruncateAt.MIDDLE);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);pp.leftMargin=style.dp(12);tools.addView(path,pp);
                    recursive=new CheckBox(activity);recursive.setText("С подпапками");style.checkbox(recursive);recursive.setChecked(children);tools.addView(recursive);recursive.setOnCheckedChangeListener((b,checked)->loadPage(true));
                    playFolder=style.button("Слушать папку","music_play_folder",()->startFolder(null));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(style.dp(style.poster?148:190),style.dp(52));fp.leftMargin=style.dp(12);tools.addView(playFolder,fp);content.addView(tools);style.gap(content,12);
                    buildList(tools);}
                }
                for(int i=0;i<dirs.length();i++)if(directories.size()<5000)directories.add(dirs.optJSONObject(i));
                for(MusicTrack song:tracks)if(visible.size()<5000)visible.add(song);
                adapter.notifyDataSetChanged();listing.setEnabled(true);playFolder.setEnabled(total>0&&!queueBusy);
                footer.setText(total==0&&folderTotal==0?(generation==0?"Индекс ещё не готов. Обновите позже.":"В этой папке нет MP3."):total+" треков · каталог с сервера"+(total>5000||folderTotal>5000?" · для полной очереди выберите подпапку":""));
            });
        }catch(Exception error){failed(request,error);}});
    }
    private void buildList(LinearLayout tools){
        listing=new ListView(activity);if(poster!=null){listing.setDivider(null);listing.setCacheColorHint(android.graphics.Color.TRANSPARENT);listing.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));listing.setVerticalScrollBarEnabled(false);}else style.bind(listing,s->{listing.setDivider(new android.graphics.drawable.ColorDrawable(s.color(s.poster?PanelStyle.LINE:PanelStyle.BG)));listing.setCacheColorHint(s.color(PanelStyle.BG));listing.setDividerHeight(s.dp(s.poster?1:8));});
        listing.setContentDescription("music_catalogue");listing.setOnTouchListener((view,event)->{view.getParent().requestDisallowInterceptTouchEvent(true);return false;});
        adapter=new BaseAdapter(){
            public int getCount(){return directories.size()+visible.size();}
            public Object getItem(int index){return index<directories.size()?directories.get(index):visible.get(index-directories.size());}
            public long getItemId(int index){return index;}
            public View getView(int index,View recycled,ViewGroup parent){
                if(poster!=null){final JSONObject directory=index<directories.size()?directories.get(index):null;final MusicTrack song=directory==null?visible.get(index-directories.size()):null;return poster.row(directory==null?index-directories.size():index,directory,song,recycled,()->{if(directory!=null){folder=directory.optString("id");loadPage(true);}else startFolder(song.id);});}
                LinearLayout card;if(recycled instanceof LinearLayout)card=(LinearLayout)recycled;else{
                    card=style.card();card.setPadding(style.dp(16),style.dp(8),style.dp(16),style.dp(8));card.setGravity(Gravity.CENTER_VERTICAL);
                    TextView title=new TextView(activity);title.setTextSize(20);title.setTypeface(style.poster?PosterTypography.face(activity):android.graphics.Typeface.create("sans-serif-medium",0));title.setIncludeFontPadding(false);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);title.setCompoundDrawablePadding(style.dp(12));card.addView(title,new LinearLayout.LayoutParams(-1,-2));card.setMinimumHeight(style.dp(48));
                }
                final LinearLayout row=card;final TextView title=(TextView)card.getChildAt(0);
                final JSONObject directory=index<directories.size()?directories.get(index):null;
                final MusicTrack song=directory==null?visible.get(index-directories.size()):null;
                style.bind(row,s->{
                    MusicTrack current=MusicPlaybackService.track();String state=MusicPlaybackService.state();
                    boolean selected=song!=null&&current!=null&&MusicPlaybackService.selected()&&song.id.equals(current.id)&&!state.equals("stopped");
                    row.setSelected(selected);row.setBackground(s.surface(selected?PanelStyle.SOFT:PanelStyle.CARD,selected?PanelStyle.ACCENT:PanelStyle.SOFT,14));
                    String name=directory!=null?directory.optString("name"):song.name,author=song==null?"":song.artist;
                    android.text.SpannableString label=new android.text.SpannableString(name+(author.isEmpty()?"":" · "+author));
                    if(!author.isEmpty())label.setSpan(new android.text.style.ForegroundColorSpan(s.color(PanelStyle.MUTED)),name.length(),label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    title.setTextColor(s.color(selected?PanelStyle.ACCENT:PanelStyle.TEXT));title.setText(label);
                    title.setCompoundDrawablesWithIntrinsicBounds(directory!=null?new PanelIcon("folder",s.color(PanelStyle.ACCENT),s.dp(22)):selected?new PanelIcon("play",s.color(PanelStyle.ACCENT),s.dp(18)):null,null,null,null);
                });
                card.setContentDescription(directory!=null?"music_folder":"music_track_"+song.id);return card;
            }
        };listing.setAdapter(adapter);
        listing.setOnItemClickListener((parent,view,index,id)->{if(index<directories.size()){folder=directories.get(index).optString("id");loadPage(true);}else startFolder(visible.get(index-directories.size()).id);});
        listing.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){}public void onScroll(AbsListView v,int first,int count,int all){
            if(!closed&&!busy&&!queueBusy&&count>0&&first+count>=all-3&&offset<5000&&(offset<total||offset<folderTotal))loadPage(false);
        }});
        content.addView(listing,new LinearLayout.LayoutParams(-1,poster!=null?-1:style.dp(350)));
        if(scroll!=null)scroll.post(()->{if(!closed&&listing!=null){ViewGroup.LayoutParams p=listing.getLayoutParams();p.height=Math.max(style.dp(160),scroll.getHeight()-tools.getHeight()-style.dp(12));listing.setLayoutParams(p);}});
    }
    private void startFolder(String selectedId){
        if(closed||library==null||busy||queueBusy||generation<1)return;
        final int request=epoch;final String lib=library,selected=folder;final long gen=generation;final boolean children=recursive!=null?recursive.isChecked():recursiveValue;
        queueBusy=true;if(playFolder!=null)playFolder.setEnabled(false);footer.setText("Подготовка очереди…");
        schedule(()->{try{
            List<MusicTrack> songs=new ArrayList<>();int target=selectedId==null?0:-1;ApiClient api=api();
            for(int page=0;;page+=50){
                if(closed||Thread.currentThread().isInterrupted()||request!=epoch)return;
                ApiClient.Response response=api.fetch(browsePath(lib,selected,gen,page,children),null);if(response.code!=200)throw new ApiClient.HttpError(response.code);
                JSONObject data=new JSONObject(response.body).getJSONObject("data");int count=data.getInt("total");if(count>5000)throw new IllegalArgumentException("В папке больше 5000 треков. Выберите подпапку.");
                JSONArray rows=data.getJSONArray("items");for(int i=0;i<rows.length();i++){MusicTrack song=new MusicTrack(rows.getJSONObject(i));if(song.id.equals(selectedId))target=songs.size();songs.add(song);}
                if(songs.size()>=count)break;if(rows.length()==0)throw new IllegalArgumentException("Каталог изменился. Обновите папку.");
            }
            if(target<0)throw new IllegalArgumentException("Трек больше не входит в папку. Обновите каталог.");
            final int index=target;handler.post(()->{if(closed||request!=epoch)return;queueBusy=false;if(playFolder!=null)playFolder.setEnabled(total>0);if(!songs.isEmpty()){MusicPlaybackService.load(activity,songs,index);footer.setText("В очереди: "+songs.size()+" · аудио передаётся потоком");tick();}});
        }catch(Exception error){failed(request,error);}});
    }
    private void failed(int request,Exception error){handler.post(()->{if(closed||request!=epoch)return;busy=queueBusy=false;if(listing!=null)listing.setEnabled(true);folder=library!=null&&library.equals(shownLibrary)?shownFolder:null;if(playFolder!=null)playFolder.setEnabled(total>0);String message;
        if(error instanceof ApiClient.HttpError){int code=((ApiClient.HttpError)error).code;message=code==403?"Включите право «Музыка» для планшета в веб-панели.":code==404?"Каталог изменился. Обновите папку.":"Нет связи с сервером · повторите обновление.";if(code==401){MusicPlaybackService.stop(activity);message="Доступ отозван. Подключите планшет заново.";}}
        else message=error instanceof IllegalArgumentException?error.getMessage():"Не удалось загрузить каталог · проверьте сервер и сеть.";
        footer.setText(message);
    });}
    void close(){restore();closed=true;epoch++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();if(listing!=null){listing.setOnScrollListener(null);listing.setAdapter(null);}}
}
