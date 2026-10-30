package local.mediacentre.client.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import local.mediacentre.client.BuildConfig;
import local.mediacentre.client.data.ApiClient;
import local.mediacentre.client.radio.RadioPlaybackService;
import local.mediacentre.client.radio.RadioStation;
import local.mediacentre.client.radio.RadioStore;
import local.mediacentre.client.radio.AudioPlayback;
import local.mediacentre.client.radio.AudioOutputController;
import org.json.*;

/** Bundled first-party screen, activated by the server's feature flag. */
public final class PhotoFrameActivity extends Activity {
    private static final String MANIFEST="/api/v1/plugins/photoframe/manifest";
    private final Handler ui=new Handler();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService preloader=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String,Object> fileLocks=new ConcurrentHashMap<>();
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final ArrayList<String> history=new ArrayList<>();
    private final HashSet<String> seen=new HashSet<>();
    private final Random random=new Random();
    private AudioOutputController audioOutput;private MusicTransportButton slideOutput;
    private final Runnable outputChanged=()->updateRadio();
    private FrameLayout root;private LinearLayout setup;private ImageView image;private TextView caption,summary;private PanelStyle panel;
    private Spinner speed;private CheckBox shuffled,fillScreen;private Button start,radioToggle;private TextView radioLabel,radioBadge,radioVolume,loading,slideStationName;private FrameLayout slideRadio;private LinearLayout slideRadioLine;private ImageView slideRadioIcon;
    private ApiClient api;private File cache;private String etag,currentId,preparedRandom;private int interval=10,generation;
    private boolean playing,visible,autoStart,slideshowOnly;private float touchX,touchY;
    private final Runnable poll=new Runnable(){public void run(){if(!visible)return;fetchManifest();ui.postDelayed(this,30000);}};
    private final Runnable heartbeat=new Runnable(){public void run(){if(!visible)return;enqueue(worker,()->{try{JSONObject data=new JSONObject().put("client_version",BuildConfig.VERSION_NAME).put("uptime_seconds",SystemClock.elapsedRealtime()/1000).put("current_screen","photoframe");api.heartbeat(data,"database");}catch(Exception ignored){}});ui.postDelayed(this,60000);}};
    private final Runnable advance=new Runnable(){public void run(){if(visible&&playing)next();}};
    private final Runnable radioTick=new Runnable(){public void run(){if(!visible)return;updateRadio();ui.postDelayed(this,1000);}};
    private static final class Photo {
        final String id,url,hash;Photo(String id,String url){this.id=id;this.url=url;this.hash=url.substring(url.lastIndexOf('/')+1);}
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        SharedPreferences connection=getSharedPreferences("connection",MODE_PRIVATE);
        String base=connection.getString("base_url","");String token=connection.getString("token","");String server=connection.getString("server_id",null);
        try{api=new ApiClient(this,base,token,server);}catch(Exception error){finish();return;}
        cache=new File(getCacheDir(),"photoframe-"+digest((base+token+server).getBytes()));cache.mkdirs();
        SharedPreferences settings=getSharedPreferences("photoframe",MODE_PRIVATE);interval=settings.getInt("seconds",10);autoStart=getIntent().getBooleanExtra("start_slideshow",false);slideshowOnly=getIntent().getBooleanExtra("slideshow_only",false);
        audioOutput=AudioOutputController.get(this);
        panel=new PanelStyle(this);root=new FrameLayout(this);root.setBackgroundColor(panel.color(PanelStyle.BG));setContentView(root);
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.HORIZONTAL);root.addView(shell,new FrameLayout.LayoutParams(-1,-1));if(slideshowOnly){shell.setVisibility(View.GONE);root.setBackgroundColor(Color.BLACK);}
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.VERTICAL);navigation.setPadding(panel.dp(12),panel.dp(12),panel.dp(12),panel.dp(12));panel.flat(navigation,PanelStyle.NAV);shell.addView(navigation,new LinearLayout.LayoutParams(panel.dp(184),-1));
        TextView navigationTitle=panel.title(connection.getString("display_title","Мой дом"),24);navigationTitle.setMaxLines(2);navigationTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);navigationTitle.setContentDescription("navigation_title");navigation.addView(navigationTitle);panel.gap(navigation,28);
        String[] screens={"home","weather","climate","radio","stations","finance","photoframe","settings"};String[] labels={"Главная","Погода","Климат","Радио","Станции","Финансы","Фоторамка","Настройки"};
        for(int i=0;i<screens.length;i++){final String destination=screens[i];Button button=panel.button(labels[i],"nav_"+destination,()->openScreen(destination));panel.navigation(button,destination);button.setSelected("photoframe".equals(destination));panel.repaint(button);LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,panel.dp(60));navParams.bottomMargin=panel.dp(10);navigation.addView(button,navParams);}
        LinearLayout stage=panel.column();stage.setPadding(panel.dp(24),panel.dp(24),panel.dp(24),panel.dp(12));shell.addView(stage,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);stage.addView(header,new LinearLayout.LayoutParams(-1,panel.dp(56)));
        TextView title=panel.title("Фоторамка",30);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextClock clock=new TextClock(this);clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");clock.setTimeZone(DisplaySettings.preferences(this).getString("timezone","Europe/Moscow"));clock.setTextSize(26);panel.ink(clock,PanelStyle.ACCENT);header.addView(clock);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);stage.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=panel.column();body.setPadding(0,panel.dp(12),0,panel.dp(12));scroll.addView(body);
        setup=panel.card();setup.setPadding(panel.dp(32),panel.dp(28),panel.dp(32),panel.dp(28));
        body.addView(setup,new LinearLayout.LayoutParams(-1,-2));
        setup.addView(panel.title("Показ фотографий",24));panel.gap(setup,12);
        summary=panel.text("Загружаем список…",21,PanelStyle.MUTED);setup.addView(summary);panel.gap(setup,22);
        TextView speedLabel=panel.title("Смена фотографий",20);setup.addView(speedLabel);panel.gap(setup,8);
        speed=new Spinner(this);String[] choices={"5 секунд","10 секунд","20 секунд","60 секунд"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,choices){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView value=(TextView)super.getView(position,convert,parent);value.setTextColor(panel.color(PanelStyle.TEXT));value.setTextSize(20);return value;}
            @Override public View getDropDownView(int position,View convert,ViewGroup parent){TextView value=(TextView)super.getDropDownView(position,convert,parent);value.setTextColor(panel.color(PanelStyle.TEXT));value.setBackgroundColor(panel.color(PanelStyle.CARD));value.setTextSize(20);value.setPadding(panel.dp(16),panel.dp(12),panel.dp(16),panel.dp(12));return value;}
        };adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);speed.setAdapter(adapter);speed.setPadding(panel.dp(16),0,panel.dp(16),0);speed.setBackground(panel.surface(PanelStyle.BG,PanelStyle.LINE,12));
        int[] values={5,10,20,60};int selected=1;for(int n=0;n<values.length;n++)if(values[n]==interval)selected=n;speed.setSelection(selected);setup.addView(speed,new LinearLayout.LayoutParams(-1,panel.dp(64)));panel.gap(setup,18);
        shuffled=new CheckBox(this);shuffled.setText("Случайный порядок");panel.checkbox(shuffled);shuffled.setChecked(settings.getBoolean("shuffle",false));setup.addView(shuffled);panel.gap(setup,14);
        fillScreen=new CheckBox(this);fillScreen.setText("Заполнить экран (края фото обрезаются)");fillScreen.setContentDescription("photoframe_fill");panel.checkbox(fillScreen);fillScreen.setChecked(settings.getBoolean("fill",false));setup.addView(fillScreen);panel.gap(setup,20);
        shuffled.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("shuffle",checked).apply());
        fillScreen.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("fill",checked).apply());
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){interval=values[position];settings.edit().putInt("seconds",interval).apply();}public void onNothingSelected(AdapterView<?> parent){}});
        start=panel.primary("Начать показ","photoframe_start",()->{interval=values[speed.getSelectedItemPosition()];settings.edit().putInt("seconds",interval).putBoolean("shuffle",shuffled.isChecked()).putBoolean("fill",fillScreen.isChecked()).apply();begin();});start.setEnabled(false);setup.addView(start,new LinearLayout.LayoutParams(-1,panel.dp(64)));
        panel.gap(stage,12);buildRadioFooter(stage);
        loading=label("Загрузка фото…",18);loading.setTextColor(0x99FFFFFF);loading.setGravity(Gravity.CENTER);loading.setVisibility(slideshowOnly?View.VISIBLE:View.GONE);root.addView(loading,new FrameLayout.LayoutParams(-1,-1));
        image=new ImageView(this);image.setBackgroundColor(Color.BLACK);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setVisibility(View.GONE);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        caption=label("",13);caption.setTextColor(0x88FFFFFF);caption.setShadowLayer(panel.dp(2),0,panel.dp(1),0xAA000000);caption.setPadding(panel.dp(8),panel.dp(4),panel.dp(8),panel.dp(4));caption.setVisibility(View.GONE);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);cp.setMargins(0,0,panel.dp(16),panel.dp(12));root.addView(caption,cp);
        slideRadioLine=new LinearLayout(this);slideRadioLine.setOrientation(LinearLayout.HORIZONTAL);slideRadioLine.setGravity(Gravity.CENTER_VERTICAL);slideRadioLine.setVisibility(View.GONE);FrameLayout.LayoutParams radioParams=new FrameLayout.LayoutParams(-2,panel.dp(48),Gravity.BOTTOM|Gravity.LEFT);radioParams.setMargins(panel.dp(16),0,0,panel.dp(12));root.addView(slideRadioLine,radioParams);
        slideRadio=new FrameLayout(this);android.graphics.drawable.GradientDrawable radioBackground=new android.graphics.drawable.GradientDrawable();radioBackground.setColor(0x330B1D36);radioBackground.setCornerRadius(panel.dp(100));slideRadio.setBackground(radioBackground);slideRadio.setContentDescription("photoframe_radio_toggle");slideRadio.setOnClickListener(v->toggleRadio());slideRadioLine.addView(slideRadio,new LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)));
        slideRadioIcon=new ImageView(this);FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(panel.dp(23),panel.dp(23),Gravity.CENTER);slideRadio.addView(slideRadioIcon,iconParams);
        slideStationName=label("",14);slideStationName.setContentDescription("photoframe_station_name");slideStationName.setTextColor(0x88FFFFFF);slideStationName.setShadowLayer(panel.dp(2),0,panel.dp(1),0xAA000000);slideStationName.setSingleLine(true);slideStationName.setEllipsize(android.text.TextUtils.TruncateAt.END);slideStationName.setMaxWidth(panel.dp(420));LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(-2,-2);nameParams.leftMargin=panel.dp(8);slideRadioLine.addView(slideStationName,nameParams);
        slideOutput=new MusicTransportButton(this,"speaker");slideOutput.setTextColor(0xFFFFFFFF);slideOutput.setBackgroundColor(Color.TRANSPARENT);slideOutput.setAlpha(0.53f);slideOutput.setContentDescription("photoframe_audio_output");slideOutput.setOnClickListener(v->AudioOutputUi.quickToggle(this));slideOutput.setOnLongClickListener(v->{AudioOutputUi.choose(this,false);return true;});LinearLayout.LayoutParams outputParams=new LinearLayout.LayoutParams(panel.dp(48),panel.dp(48));outputParams.leftMargin=panel.dp(12);slideRadioLine.addView(slideOutput,outputParams);updateRadio();
        image.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();return true;}if(event.getAction()==MotionEvent.ACTION_UP){float dx=event.getX()-touchX,dy=event.getY()-touchY;float threshold=getResources().getDisplayMetrics().density*64;if(Math.abs(dy)>threshold&&Math.abs(dy)>Math.abs(dx)*1.2f&&dy>0)end();else if(Math.abs(dx)>threshold&&Math.abs(dx)>Math.abs(dy)*1.2f){if(dx<0)next();else previous();}return true;}return true;});
    }
    private void openScreen(String destination){if("photoframe".equals(destination))return;Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);intent.putExtra("open_screen",destination);startActivity(intent);finish();}
    private void buildRadioFooter(LinearLayout stage){
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(panel.dp(10),0,panel.dp(10),0);panel.background(footer,PanelStyle.CARD);stage.addView(footer,new LinearLayout.LayoutParams(-1,panel.dp(72)));
        radioBadge=panel.badge("FM",48);footer.addView(radioBadge,new LinearLayout.LayoutParams(panel.dp(48),panel.dp(48)));
        radioLabel=panel.text("",18,PanelStyle.TEXT);radioLabel.setMaxLines(2);radioLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);radioLabel.setContentDescription("mini_player_state");LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.leftMargin=panel.dp(16);footer.addView(radioLabel,nameParams);
        radioVolume=panel.text("",18,PanelStyle.MUTED);radioVolume.setGravity(Gravity.CENTER);footer.addView(radioVolume,new LinearLayout.LayoutParams(panel.dp(72),panel.dp(48)));
        radioToggle=panel.primary("Слушать","mini_player_toggle",()->toggleRadio());footer.addView(radioToggle,new LinearLayout.LayoutParams(panel.dp(144),panel.dp(52)));
        Button open=panel.button("Плеер →","mini_player_open",()->openScreen(AudioPlayback.music()?"music":"radio"));LinearLayout.LayoutParams openParams=new LinearLayout.LayoutParams(panel.dp(128),panel.dp(52));openParams.leftMargin=panel.dp(12);footer.addView(open,openParams);
    }
    private void toggleRadio(){if(AudioPlayback.music()||RadioStore.saved(this)!=null)AudioPlayback.toggle(this);else if(!playing)openScreen("stations");updateRadio();}
    private void updateRadio(){
        boolean active=AudioPlayback.active();String name=AudioPlayback.name(this);
        if(radioLabel!=null){String state=AudioPlayback.state();String playback="playing".equals(state)?(AudioPlayback.music()?"Музыка":"В эфире"):"connecting".equals(state)?"Подключение…":"retrying".equals(state)?"Повторное подключение…":"Остановлено";PanelStyle.update(radioLabel,name==null?"Выберите радиостанцию":name+" · "+playback);PanelStyle.update(radioBadge,PanelStyle.initials(name));PanelStyle.update(radioVolume,RadioStore.volume(this)+" %");PanelStyle.update(radioToggle,active?(AudioPlayback.music()?"Пауза":"Остановить"):"Слушать");}
        if(slideOutput!=null&&audioOutput!=null){slideOutput.symbol(audioOutput.busy()?"loading":audioOutput.routedBluetooth()?"bluetooth":"speaker");slideOutput.setEnabled(!audioOutput.busy());}
        if(slideRadio!=null){slideRadioIcon.setImageDrawable(new PanelIcon(active?(AudioPlayback.music()?"pause":"stop"):"play",0x88FFFFFF,panel.dp(23)));slideRadio.setContentDescription(active?"Остановить звук":"Слушать");slideStationName.setText(name==null?"Станция не выбрана":name);}
    }
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,10,0,10);return t;}
    @Override protected void onStart(){super.onStart();visible=true;if(audioOutput!=null)audioOutput.listen(outputChanged);DisplaySettings.apply(this,DisplaySettings.preferences(this).getString("timezone","Europe/Moscow"));ui.post(poll);ui.post(radioTick);ui.postDelayed(heartbeat,1000);if(playing)show();}
    @Override protected void onStop(){visible=false;if(audioOutput!=null)audioOutput.unlisten(outputChanged);generation++;ui.removeCallbacks(poll);ui.removeCallbacks(radioTick);ui.removeCallbacks(heartbeat);ui.removeCallbacks(advance);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onStop();}
    @Override protected void onDestroy(){worker.shutdownNow();preloader.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(playing)end();else super.onBackPressed();}
    private void fetchManifest(){final String previous=etag;worker.execute(()->{try{
        ApiClient.Response result=api.fetch(MANIFEST,previous);if(result.code==304)return;if(result.code!=200)throw new ApiClient.HttpError(result.code);
        JSONObject data=new JSONObject(result.body).getJSONObject("data");JSONArray rows=data.getJSONArray("items");if(rows.length()>500)throw new IOException("Album too large.");
        ArrayList<Photo> incoming=new ArrayList<>();HashSet<String> ids=new HashSet<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String id=row.getString("id"),url=row.getString("url");if(!id.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")||!url.matches("/api/v1/plugins/photoframe/photos/"+id+"/[a-f0-9]{64}")||!ids.add(id))throw new IOException("Invalid album.");incoming.add(new Photo(id,url));}
        ui.post(()->{if(!visible)return;photos.clear();photos.addAll(incoming);etag=result.etag;start.setEnabled(!photos.isEmpty());summary.setText(photos.isEmpty()?"Фотографий пока нет":"Фотографий: "+photos.size());
            if(slideshowOnly&&photos.isEmpty()){Toast.makeText(this,"В альбоме пока нет фото",Toast.LENGTH_SHORT).show();finish();return;}
            if(playing){if(photos.isEmpty()){end();summary.setText("Фотографий пока нет");}else if(find(currentId)==null){currentId=photos.get(0).id;show();}else{seen.retainAll(ids);history.retainAll(ids);}}
            else if(autoStart&&!photos.isEmpty()){autoStart=false;begin();}
            enqueue(worker,()->prune(incoming));});
    }catch(Exception error){ui.post(()->{if(!visible)return;if(error instanceof ApiClient.HttpError){int code=((ApiClient.HttpError)error).code;if(code==401||code==403){clearCache();finish();return;}if(code==404){finish();return;}}if(slideshowOnly&&!playing){Toast.makeText(this,"Не удалось загрузить фото",Toast.LENGTH_SHORT).show();finish();return;}if(!playing)summary.setText(photos.isEmpty()?"Нет связи с сервером":"Нет связи · доступно "+photos.size()+" фото");else caption.setText("Нет связи · показаны сохранённые фото");});}});}
    private Photo find(String id){for(Photo photo:photos)if(photo.id.equals(id))return photo;return null;}
    private void begin(){if(photos.isEmpty())return;playing=true;history.clear();seen.clear();preparedRandom=null;currentId=photos.get(0).id;seen.add(currentId);image.setScaleType(fillScreen.isChecked()?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);root.setBackgroundColor(Color.BLACK);loading.setVisibility(View.GONE);image.setVisibility(View.VISIBLE);caption.setVisibility(View.VISIBLE);slideRadioLine.setVisibility(View.VISIBLE);root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);show();}
    private void end(){playing=false;generation++;ui.removeCallbacks(advance);if(slideshowOnly){finish();return;}image.setImageDrawable(null);image.setVisibility(View.GONE);caption.setVisibility(View.GONE);slideRadioLine.setVisibility(View.GONE);root.setBackgroundColor(panel.color(PanelStyle.BG));root.setSystemUiVisibility(0);}
    private Photo randomCandidate(){ArrayList<Photo> choices=new ArrayList<>();for(Photo item:photos)if(!item.id.equals(currentId)&&!seen.contains(item.id))choices.add(item);if(choices.isEmpty()){seen.clear();seen.add(currentId);for(Photo item:photos)if(!item.id.equals(currentId))choices.add(item);}return choices.isEmpty()?null:choices.get(random.nextInt(choices.size()));}
    private void next(){if(photos.isEmpty())return;String prior=currentId;String next=null;
        if(shuffled.isChecked()){Photo chosen=preparedRandom==null?null:find(preparedRandom);if(chosen==null||seen.contains(chosen.id))chosen=randomCandidate();if(chosen!=null)next=chosen.id;preparedRandom=null;}
        else{for(int i=0;i<photos.size();i++)if(photos.get(i).id.equals(currentId)){next=photos.get((i+1)%photos.size()).id;break;}}
        if(next!=null&&!next.equals(currentId)){history.add(prior);if(history.size()>500)history.remove(0);seen.add(next);currentId=next;}show();
    }
    private void previous(){if(photos.isEmpty())return;preparedRandom=null;if(shuffled.isChecked()&&!history.isEmpty())currentId=history.remove(history.size()-1);else for(int i=0;i<photos.size();i++)if(photos.get(i).id.equals(currentId)){currentId=photos.get((i+photos.size()-1)%photos.size()).id;break;}show();}
    private void show(){ui.removeCallbacks(advance);if(!visible||!playing)return;Photo item=find(currentId);if(item==null)return;int requested=++generation;image.setImageDrawable(null);caption.setText("");ui.postDelayed(()->{if(visible&&playing&&requested==generation&&image.getDrawable()==null)caption.setText("Загрузка…");},500);enqueue(worker,()->{try{File file=file(item);Bitmap bitmap=decode(file);ui.post(()->{if(!visible||!playing||requested!=generation){bitmap.recycle();return;}image.setImageBitmap(bitmap);int index=photos.indexOf(find(item.id))+1;caption.setText(index+" / "+photos.size());ui.postDelayed(advance,interval*1000L);if(shuffled.isChecked()){Photo upcoming=randomCandidate();preparedRandom=upcoming==null?null:upcoming.id;if(upcoming!=null)enqueue(preloader,()->{try{file(upcoming);}catch(Exception ignored){}});}else enqueue(preloader,()->prefetch(item));});}catch(Exception error){ui.post(()->{if(!visible||!playing||requested!=generation)return;caption.setText("Фото недоступно · повторим на следующем кадре");ui.postDelayed(advance,interval*1000L);});}});}
    private File file(Photo photo)throws Exception {Object lock=fileLocks.get(photo.id);if(lock==null){Object created=new Object();Object prior=fileLocks.putIfAbsent(photo.id,created);lock=prior==null?created:prior;}synchronized(lock){return lockedFile(photo);}}
    private File lockedFile(Photo photo)throws Exception {File target=new File(cache,photo.id+"-"+photo.hash+".jpg");if(target.isFile()&&photo.hash.equals(digestFile(target))){target.setLastModified(System.currentTimeMillis());return target;}
        byte[] bytes=api.photo(photo.url);if(!photo.hash.equals(digest(bytes)))throw new IOException("Photo checksum mismatch.");File temporary=new File(cache,target.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes);out.getFD().sync();}if(!temporary.renameTo(target)){temporary.delete();throw new IOException("Photo cache unavailable.");}trimCache();return target;}
    private Bitmap decode(File file)throws IOException {BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("Bad photo.");BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>1920||bounds.outHeight/options.inSampleSize>1200)options.inSampleSize*=2;Bitmap result=BitmapFactory.decodeFile(file.getPath(),options);if(result==null)throw new IOException("Bad photo.");return result;}
    private void prefetch(Photo item){if(!visible||!playing||photos.size()<2)return;int index=photos.indexOf(find(item.id));if(index<0)return;try{file(photos.get((index+1)%photos.size()));file(photos.get((index+photos.size()-1)%photos.size()));}catch(Exception ignored){}}
    private void prune(ArrayList<Photo> incoming){HashSet<String> keep=new HashSet<>();for(Photo item:incoming)keep.add(item.id+"-"+item.hash+".jpg");File[] files=cache.listFiles();if(files!=null)for(File file:files)if(!keep.contains(file.getName())&&(!file.getName().endsWith(".tmp")||System.currentTimeMillis()-file.lastModified()>60000))file.delete();trimCache();}
    private void trimCache(){File[] files=cache.listFiles();if(files==null)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long size=0;for(File f:files)if(!f.getName().endsWith(".tmp"))size+=f.length();for(File f:files)if(size>150L*1024*1024&&!f.getName().endsWith(".tmp")){size-=f.length();f.delete();}}
    private static void enqueue(ExecutorService queue,Runnable task){try{queue.execute(task);}catch(RejectedExecutionException ignored){}}
    private void clearCache(){File[] files=cache.listFiles();if(files!=null)for(File file:files)file.delete();}
    private static String digestFile(File file)throws Exception {try(InputStream input=new FileInputStream(file)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)md.update(block,0,n);return hex(md.digest());}}
    private static String digest(byte[] bytes){try{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception error){throw new IllegalStateException(error);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
}
