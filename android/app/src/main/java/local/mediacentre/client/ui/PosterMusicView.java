package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.music.*;
import local.mediacentre.client.radio.*;

/** Music poster in either palette. Catalogue requests and audio remain in their owners. */
final class PosterMusicView extends PosterPageView {
    interface Actions {void toggle();void previous();void stop();void next();void shuffle();void repeat();void up();void playFolder();void recursive(boolean value);void seek(int value);}
    private static final float WIDTH=1469,HEIGHT=992;
    private final boolean dark;
    private final int ink,muted,blue,signal,background,rule,rowRule,selectedRow,accent,trackBase,volumeBase,controlSurface,button,buttonPressed,disabled,outlineColor,onSignal;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final View statusRoot;private final int statusVisibility;
    private final AudioOutputControls output;
    private final TextView now,artist,currentTime,durationTime,volumeValue,path;
    private final Artwork art;
    private final IconButton play,previous,next,stop,shuffle,repeat;
    private final SeekBar position,volume;
    private final VolumeButton mute;
    final LinearLayout catalogue;
    final CheckBox recursive;
    final Button playFolder;
    private final Typeface latin;
    private float scaleY=1;

    private static final class Box {final View v;final float x,y,w,h,font;Box(View v,float x,float y,float w,float h,float f){this.v=v;this.x=x;this.y=y;this.w=w;this.h=h;font=f;}}
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;final Drawable background;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;final ColorStateList colors;
        Borrowed(View v){view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();}else{face=null;font=scale=0;gravity=0;padding=false;colors=null;}parent.removeView(v);}
        void restore(){((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);view.setBackground(background);if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);}new PanelStyle(view.getContext()).repaint(view);}
    }
    PosterMusicView(Context c,TextClock clock,AudioOutputControls output,View refresh,View statusRoot,TextView status,boolean children,Actions actions){
        super(c);this.output=output;this.statusRoot=statusRoot;statusVisibility=statusRoot.getVisibility();statusRoot.setVisibility(GONE);latin=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");
        dark=new PanelStyle(c).palette.dark;
        ink=dark?0xfff8f5ee:0xff050d15;muted=dark?0xffb8c9df:0xff536274;blue=dark?0xff16358a:0xff0634e8;signal=dark?0xffff8a50:0xffff4b12;
        background=dark?0xff0b101b:0xfff8f5ee;rule=dark?0xff43566e:0xff9ca4ae;rowRule=dark?0xff43566e:0xffc1c8d1;selectedRow=dark?0xff1b2d49:0xffe5eaff;
        accent=dark?0xff9ab7ff:blue;trackBase=dark?0xff3b4d66:0xffc8cdd4;volumeBase=dark?0xff3b4d66:0xffd3d8e0;controlSurface=dark?0xff141d2d:0xffedf0f5;
        button=dark?0xff2548b8:blue;buttonPressed=dark?0xff315de0:0xff0828ac;disabled=dark?0xff8795aa:0xffb0b9c7;outlineColor=dark?0xff63738b:0xffb7c1d1;onSignal=dark?0xff0b101b:Color.WHITE;
        place(new Backdrop(c),0,0,1469,992,0);
        place(label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),ink,true),24,16,760,30,24);
        TextView title=label("Музыка",ink,true);title.setTypeface(PosterTypography.face(c));title.setTextScaleX(.9f);title.setContentDescription("music_title");place(title,24,43,850,107,112);
        PosterAudioHeader.bind(this::take,clock,output);
        art=new Artwork(c,dark,blue);place(art,24,156,654,414,0);
        now=new FittedTitle(c);now.setTextColor(ink);now.setContentDescription("music_current");place(now,24,572,654,77,0);
        artist=label("",ink,true);artist.setTypeface(PosterTypography.face(c));artist.setContentDescription("music_artist");place(artist,24,650,654,40,30);
        position=seekBar(c,signal,trackBase);position.setContentDescription("music_position");place(position,24,701,654,35,0);position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){actions.seek(b.getProgress());}});
        currentTime=label("0:00",ink,false);place(currentTime,24,737,250,35,22);durationTime=label("0:00",ink,false);durationTime.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(durationTime,428,737,250,35,22);
        shuffle=icon("shuffle","music_shuffle",actions::shuffle);place(shuffle,24,795,70,66,0);
        previous=icon("previous_solid","Предыдущий трек",actions::previous);place(previous,135,795,70,66,0);
        play=icon("play","Воспроизвести",actions::toggle);play.circle=true;play.tint=onSignal;place(play,235,768,120,120,0);
        stop=icon("stop","Остановить",actions::stop);place(stop,384,795,70,66,0);
        next=icon("next_solid","Следующий трек",actions::next);place(next,491,795,70,66,0);
        repeat=icon("repeat","Повтор выключен",actions::repeat);place(repeat,598,795,70,66,0);
        mute=new VolumeButton(c,R.id.music_mute,()->{AudioPlayback.toggleMute(c);syncVolume();});mute.setTextColor(ink);mute.setBackgroundColor(Color.TRANSPARENT);place(mute,16,892,68,66,0);
        volume=seekBar(c,accent,volumeBase);volume.setMax(100);volume.setContentDescription("music_volume");place(volume,90,906,516,42,0);volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(user){AudioPlayback.volume(c,v);syncVolume();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        volumeValue=label("",ink,false);volumeValue.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(volumeValue,613,907,65,40,22);
        path=label("Библиотеки",ink,true);path.setContentDescription("music_path");place(path,737,170,536,58,26);
        take(refresh,1289,173,59,51,0);((TextView)refresh).setTextColor(ink);refresh.setBackground(surface(controlSurface,4));
        IconButton up=icon("up","music_up",actions::up);up.outline=true;place(up,1377,173,67,51,0);
        recursive=new CheckBox(c);recursive.setText("С подпапками");recursive.setContentDescription("music_recursive");recursive.setTextColor(ink);recursive.setSingleLine();new PanelStyle(c).checkbox(recursive);recursive.setChecked(children);recursive.setOnCheckedChangeListener((b,value)->actions.recursive(value));place(recursive,737,244,350,64,23);
        playFolder=new Button(c);playFolder.setAllCaps(false);playFolder.setText("Слушать папку");playFolder.setTextColor(Color.WHITE);playFolder.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));playFolder.setContentDescription("music_play_folder");playFolder.setPadding(16,0,16,0);playFolder.setCompoundDrawablePadding(10);playFolder.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon("play",Color.WHITE,new PanelStyle(c).dp(27)),null,null,null);StateListDrawable folderState=new StateListDrawable();folderState.addState(new int[]{-android.R.attr.state_enabled},surface(dark?0xff303d52:0xffb5becf,4));folderState.addState(new int[]{android.R.attr.state_pressed},surface(buttonPressed,4));folderState.addState(new int[]{},surface(button,4));playFolder.setBackground(folderState);playFolder.setOnClickListener(v->actions.playFolder());place(playFolder,1169,241,275,64,23);
        catalogue=new LinearLayout(c);catalogue.setOrientation(LinearLayout.VERTICAL);place(catalogue,725,325,720,532,0);
        take(status,737,929,660,40,14);status.setIncludeFontPadding(false);status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        TextView format=label("MP3",muted,false);format.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(format,1388,929,57,40,16);
        tick();
    }
    void folder(String name){PanelStyle.update(path,name);}
    void libraries(){PanelStyle.update(path,"Библиотеки");playFolder.setEnabled(false);}
    void tick(){
        MusicTrack song=MusicPlaybackService.track();String state=MusicPlaybackService.state();boolean active=MusicPlaybackService.active();
        PanelStyle.update(now,song==null?"Выберите трек":song.name);PanelStyle.update(artist,song==null?"":song.artist.isEmpty()?song.album:song.artist+(song.album.isEmpty()?"":" · "+song.album));art.track(song);
        play.symbol="playing".equals(state)?"pause":active?"loading":"play";play.setContentDescription(active?"Пауза":"Воспроизвести");play.invalidate();
        previous.setEnabled(song!=null);next.setEnabled(song!=null);stop.setEnabled(song!=null);
        shuffle.tint=MusicPlaybackService.shuffled()?signal:ink;shuffle.setSelected(MusicPlaybackService.shuffled());shuffle.setContentDescription(MusicPlaybackService.shuffled()?"Случайный порядок включён":"Случайный порядок выключен");shuffle.invalidate();
        int mode=MusicPlaybackService.repeat();repeat.symbol=mode==MusicQueue.ONE?"repeat_one":"repeat";repeat.tint=mode==MusicQueue.OFF?ink:signal;repeat.setSelected(mode!=MusicQueue.OFF);repeat.setContentDescription(mode==MusicQueue.OFF?"Повтор выключен":mode==MusicQueue.ALL?"Повтор всей очереди":"Повтор одного трека");repeat.invalidate();
        int current=MusicPlaybackService.position(),duration=MusicPlaybackService.duration();position.setMax(Math.max(1,duration));position.setEnabled(duration>0);if(!position.isPressed())position.setProgress(current);PanelStyle.update(currentTime,clock(current));PanelStyle.update(durationTime,clock(duration));syncVolume();
    }
    private void syncVolume(){int level=RadioStore.volume(getContext());if(!volume.isPressed())volume.setProgress(level);PanelStyle.update(volumeValue,level+"%");mute.refresh();}
    void restore(){for(int n=borrowed.size()-1;n>=0;n--)borrowed.get(n).restore();borrowed.clear();output.contrast(false);statusRoot.setVisibility(statusVisibility);}
    View row(int index,org.json.JSONObject directory,MusicTrack song,View recycled,Runnable open){Row row=recycled instanceof Row?(Row)recycled:new Row(getContext());row.bind(index,directory,song,open);return row;}
    private final class Row extends ViewGroup {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final TextView number,title,duration;private final IconButton more;private final RadioEqualizerView wave;private boolean folder,selected;private MusicTrack song;
        Row(Context c){super(c);setWillNotDraw(false);number=label("",ink,false);number.setGravity(Gravity.CENTER);addView(number);title=label("",ink,true);addView(title);duration=label("",ink,false);duration.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);addView(duration);more=icon("more","Сведения о треке",()->{if(song!=null)new PanelStyle(c).dialog().setTitle(song.name).setMessage((song.artist.isEmpty()?"":song.artist+"\n")+(song.album.isEmpty()?"":song.album+"\n")+String.format(Locale.ROOT,"MP3 · %.1f МБ",song.bytes/1048576.0)).setPositiveButton("Закрыть",null).show();});more.setFocusable(false);addView(more);wave=new RadioEqualizerView(c);wave.setColor(signal);wave.setContentDescription("music_track_indicator");addView(wave);}
        void bind(int index,org.json.JSONObject dir,MusicTrack track,Runnable open){folder=dir!=null;song=track;MusicTrack current=MusicPlaybackService.track();String state=MusicPlaybackService.state();selected=track!=null&&current!=null&&MusicPlaybackService.selected()&&track.id.equals(current.id)&&!"stopped".equals(state);setSelected(selected);setBackgroundColor(selected?selectedRow:background);setContentDescription(folder?"music_folder":"music_track_"+track.id);setOnClickListener(v->open.run());
            String name=folder?dir.optString("name"):track.name,author=folder?"":track.artist;title.setTypeface(name.matches(".*[А-Яа-яЁё].*")?PosterTypography.face(getContext()):latin);SpannableString text=new SpannableString(name+(author.isEmpty()?"":" · "+author));if(!author.isEmpty()){text.setSpan(new ForegroundColorSpan(muted),name.length(),text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(new TypefaceSpan("sans-serif-condensed"),name.length(),text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);text.setSpan(new RelativeSizeSpan(.86f),name.length(),text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}title.setText(text);number.setText(folder?"":Integer.toString(index+1));number.setVisibility(!folder&&!selected?VISIBLE:INVISIBLE);more.setVisibility(folder?GONE:VISIBLE);duration.setText(selected&&MusicPlaybackService.duration()>0?clock(MusicPlaybackService.duration()):"");wave.setPlaying(selected&&"playing".equals(state));wave.setVisibility(selected&&"playing".equals(state)?VISIBLE:INVISIBLE);invalidate();}
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=Math.max(1,Math.round(76*scaleY));setMeasuredDimension(w,h);float sx=w/720f,sy=h/76f;measure(number,96*sx,h);measure(title,473*sx,h);measure(duration,78*sx,h);measure(more,42*sx,h);measure(wave,34*sx,44*sy);number.setTextSize(TypedValue.COMPLEX_UNIT_PX,24*Math.min(sx,sy));title.setTextSize(TypedValue.COMPLEX_UNIT_PX,29*Math.min(sx,sy));duration.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*Math.min(sx,sy));}
        private void measure(View v,float w,float h){v.measure(MeasureSpec.makeMeasureSpec(Math.round(w),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(h),MeasureSpec.EXACTLY));}
        private void at(View v,float x,float y){int l=Math.round(x*getWidth()/720f),t=Math.round(y*getHeight()/76f);v.layout(l,t,l+v.getMeasuredWidth(),t+v.getMeasuredHeight());}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){at(number,0,0);at(title,112,0);at(duration,586,0);at(more,678,0);at(wave,31,16);}
        @Override protected void onDraw(Canvas c){p.setColor(rowRule);p.setStrokeWidth(1);c.drawLine(16,getHeight()-1,getWidth(),getHeight()-1,p);if(folder){PanelIcon icon=new PanelIcon("folder_solid",accent,Math.round(getHeight()*.60f));icon.setBounds(Math.round(getWidth()*32/720f),getHeight()/5,Math.round(getWidth()*32/720f)+icon.getIntrinsicWidth(),getHeight()/5+icon.getIntrinsicHeight());icon.draw(c);PanelIcon arrow=new PanelIcon("chevron_right",ink,getHeight()/3);arrow.setBounds(getWidth()-getHeight()/2,getHeight()/3,getWidth()-getHeight()/6,getHeight()*2/3);arrow.draw(c);}else if(selected&&!"playing".equals(MusicPlaybackService.state())){PanelIcon icon=new PanelIcon("pause",signal,getHeight()/3);int x=Math.round(getWidth()*35/720f);icon.setBounds(x,getHeight()/3,x+getHeight()/3,getHeight()*2/3);icon.draw(c);}}
    }
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,int color,boolean bold){TextView t=new TextView(getContext());t.setText(text);t.setTextColor(color);t.setSingleLine();t.setEllipsize(TextUtils.TruncateAt.END);t.setIncludeFontPadding(false);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private IconButton icon(String symbol,String description,Runnable action){IconButton b=new IconButton(getContext(),symbol);b.setContentDescription(description);b.setOnClickListener(v->action.run());return b;}
    private static GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private SeekBar seekBar(Context c,int color,int base){PanelStyle s=new PanelStyle(c);SeekBar b=new CenteredSeekBar(c);LayerDrawable d=new LayerDrawable(new Drawable[]{new Track(s,base),new ClipDrawable(new Track(s,color),Gravity.LEFT,ClipDrawable.HORIZONTAL)});d.setId(0,android.R.id.background);d.setId(1,android.R.id.progress);b.setProgressDrawable(d);b.setThumb(new Thumb(s,color));b.setThumbOffset(s.dp(12));b.setPadding(s.dp(12),0,s.dp(12),0);return b;}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);float sx=w/WIDTH;scaleY=h/HEIGHT;for(Box b:boxes){if(b.font>0)((TextView)b.v).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,scaleY));b.v.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*scaleY),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH;for(Box box:boxes){int x=Math.round(box.x*sx),y=Math.round(box.y*scaleY);box.v.layout(x,y,x+box.v.getMeasuredWidth(),y+box.v.getMeasuredHeight());}}
    private static String clock(int millis){int seconds=Math.max(0,millis)/1000;return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private final class IconButton extends Button {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);String symbol;int tint=ink;boolean circle,outline;
        IconButton(Context c,String symbol){super(c);this.symbol=symbol;setText("");setBackgroundColor(Color.TRANSPARENT);setPadding(0,0,0,0);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(circle){p.setColor(isPressed()?(dark?0xffffa071:0xffdf3f08):signal);c.drawCircle(w/2,h/2,Math.min(w,h)/2,p);}else if(outline){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(outlineColor);c.drawRoundRect(new RectF(1,1,w-1,h-1),5,5,p);p.setStyle(Paint.Style.FILL);}int size=Math.round(Math.min(w,h)*(circle?.62f:.64f));PanelIcon icon=new PanelIcon(symbol,isEnabled()?tint:disabled,size);int x=(getWidth()-size)/2,y=(getHeight()-size)/2;icon.setBounds(x,y,x+size,y+size);icon.draw(c);}
    }
    private static final class FittedTitle extends TextView {
        FittedTitle(Context c){super(c);setSingleLine();setEllipsize(TextUtils.TruncateAt.END);setIncludeFontPadding(false);setGravity(Gravity.CENTER_VERTICAL);setTypeface(PosterTypography.face(c));}
        @Override protected void onMeasure(int ws,int hs){float base=MeasureSpec.getSize(hs)*.96f,current=getTextSize(),available=MeasureSpec.getSize(ws),width=current>0?getPaint().measureText(getText().toString())*base/current:0;float target=width>available?Math.max(base*.48f,base*available/width):base;if(Math.abs(current-target)>.1f)setTextSize(TypedValue.COMPLEX_UNIT_PX,target);super.onMeasure(ws,hs);}
    }
    private static final class Artwork extends View {
        private final boolean dark;private final int blue;private static Bitmap photo;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final Path cut=new Path();private final Typeface face;private String author="",album="";
        Artwork(Context c,boolean dark,int blue){super(c);this.dark=dark;this.blue=blue;face=PosterTypography.face(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(photo==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;o.inScaled=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;photo=BitmapFactory.decodeResource(c.getResources(),R.drawable.neo_home_music,o);}}
        void track(MusicTrack song){String a=song==null?"":song.artist,b=song==null?"":song.album;if(!author.equals(a)||!album.equals(b)){author=a;album=b;invalidate();}}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setColor(blue);c.drawRect(0,0,w,h,p);int save=c.save();cut.rewind();cut.moveTo(0,0);cut.lineTo(w*.50f,0);cut.cubicTo(w*.66f,h*.20f,w*.71f,h*.38f,w*.73f,h*.70f);cut.lineTo(w,h);cut.lineTo(0,h);cut.close();c.clipPath(cut);p.setColor(0xff061522);c.drawRect(0,0,w,h,p);float scale=h*1.08f/photo.getHeight(),bw=photo.getWidth()*scale,bh=photo.getHeight()*scale;c.drawBitmap(photo,null,new RectF(-w*.16f,h-bh,-w*.16f+bw,h),p);if(dark){p.setColor(0x33050d1c);c.drawRect(0,0,w,h,p);}c.restoreToCount(save);p.setTypeface(face);p.setColor(Color.WHITE);p.setTextScaleX(.88f);String[] words=author.toUpperCase(Locale.ROOT).split(" +");float y=h*.18f;for(int n=0;n<Math.min(3,words.length);n++){p.setTextSize(h*.15f);float width=p.measureText(words[n]);if(width>w*.23f)p.setTextSize(p.getTextSize()*w*.23f/width);c.drawText(words[n],w*.74f,y+n*h*.17f,p);}p.setColor(0xffa8c1ff);p.setTextSize(h*.085f);String text=album.toUpperCase(Locale.ROOT);int limit=Math.min(text.length(),22);String shown=text.substring(0,limit);float width=p.measureText(shown);if(width>w*.23f)p.setTextSize(p.getTextSize()*w*.23f/width);c.drawText(shown,w*.74f,h*.77f,p);p.setTextScaleX(1);}
    }
    private final class Backdrop extends View {private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path header=new Path();Backdrop(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}@Override protected void onDraw(Canvas c){c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);p.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,p);p.setColor(rule);p.setStrokeWidth(1);c.drawLine(708,170,708,963,p);c.drawLine(708,156,1445,156,p);c.drawLine(737,325,1445,325,p);c.drawLine(725,910,1445,910,p);PosterAudioHeader.draw(c,p,header,blue);c.restore();}}
    private static final class Track extends Drawable {private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int radius;Track(PanelStyle s,int color){radius=s.dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    private static final class Thumb extends Drawable {private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size,radius;Thumb(PanelStyle s,int color){size=s.dp(24);radius=s.dp(10);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
}
