package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.PosterLabels;
import local.mediacentre.client.radio.*;

/** Shared light/dark Neo Poster radio on the approved 1469 x 992 composition. */
final class PosterRadioView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private final int ink,muted,blue,signal,background,rule,trackColor,progressColor,liveDot,idleDot;
    private static Bitmap photograph;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final View footer;
    private final int footerVisibility;
    private final AudioOutputControls output;
    private final RadioWaveformView waveform;
    private final RadioToggle toggle;
    private final VolumeButton mute;
    private final SeekBar slider;
    private final TextView volume,permission;
    final TextView state;
    private boolean live;

    private static final class Box {
        final View view;final float x,y,w,h,font;
        Box(View v,float x,float y,float w,float h,float font){view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;}
    }
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;final ColorStateList colors;
        Borrowed(View v){view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();}
            else{face=null;font=scale=0;gravity=0;padding=false;colors=null;}parent.removeView(v);}
        void restore(){((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);
            if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);}
            new PanelStyle(view.getContext()).repaint(view);}
    }

    PosterRadioView(Context c,Dtos.Station station,Dtos.Catalogue favorites,RadioScreen.Actions actions,
            TextClock clock,AudioOutputControls output,View footer,String playback){
        super(c);setWillNotDraw(false);this.output=output;this.footer=footer;footerVisibility=footer.getVisibility();footer.setVisibility(GONE);
        PanelStyle s=new PanelStyle(c);
        boolean dark=s.palette.dark;ink=dark?s.color(PanelStyle.TEXT):0xff050d15;muted=dark?0xffb8c9df:0xff536274;
        background=s.color(PanelStyle.BG);blue=dark?0xff16358a:0xff0634e8;signal=dark?s.color(PanelStyle.SIGNAL):0xffff4b12;
        rule=dark?0xff43566e:0xff9ca4ae;trackColor=dark?0xff3b4d66:0xffbfc6d0;progressColor=dark?s.color(PanelStyle.ACCENT):blue;
        liveDot=dark?0xff6edda0:0xff12b45b;idleDot=dark?0xff8795aa:0xff9ca4ae;
        place(new RadioPhoto(c,dark),698,0,771,690,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        TextView title=label("Радио",ink,true);title.setTypeface(PosterTypography.face(c));title.setContentDescription("radio_title");place(title,24,13,600,65,52);
        PosterAudioHeader.bind(this::take,clock,output);
        place(new StationArt(c,station==null?"fm":PanelStyle.initials(PosterLabels.station(station.name)).toLowerCase(Locale.ROOT)),30,130,237,280,0);
        TextView eyebrow=label("ИНТЕРНЕТ-РАДИО",ink,true);place(eyebrow,285,126,405,24,14);
        TextView name=new StationTitle(c);name.setText(station==null?"Выберите станцию":PosterLabels.station(station.name));name.setTextColor(ink);name.setContentDescription("radio_station_name");place(name,280,155,408,259,0);
        String genre=station==null?"Откройте каталог":station.genre+(station.enabled?"":" · отключена");
        TextView category=label(genre,muted,false);category.setContentDescription("radio_genre");place(category,285,425,418,43,30);
        Button favorite=new FavoriteButton(c);favorite.setText(station!=null&&station.favorite?"★":"☆");favorite.setTag(station!=null&&station.favorite);favorite.setContentDescription("radio_favorite");s.plain(favorite,false);favorite.setEnabled(station!=null&&(station.enabled||station.favorite));favorite.setOnClickListener(v->actions.favorite(station,favorite));place(favorite,30,527,90,80,0);
        Button previous=new RadioTransportButton(c,RadioTransportButton.PREVIOUS);previous.setContentDescription("radio_previous");s.plain(previous,false);previous.setEnabled(favorites!=null&&!favorites.stations.isEmpty());previous.setOnClickListener(v->actions.cycle(-1));place(previous,156,529,80,80,0);
        toggle=new RadioToggle(c);toggle.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{dark?s.color(PanelStyle.DISABLED):Color.WHITE,dark?s.color(PanelStyle.ON_SIGNAL):Color.WHITE}));toggle.setBackground(buttonBackground(signal,dark?0xffffa06f:0xffe23f00,75,s));toggle.setEnabled(station!=null&&station.enabled);toggle.setOnClickListener(v->{if(RadioPlaybackService.active()||RadioPlaybackService.starting())actions.stop();else actions.play();tick();});place(toggle,252,491,150,150,0);
        Button next=new RadioTransportButton(c,RadioTransportButton.NEXT);next.setContentDescription("radio_next");s.plain(next,false);next.setEnabled(previous.isEnabled());next.setOnClickListener(v->actions.cycle(1));place(next,420,529,80,80,0);
        Button catalogue=new CatalogueButton(c);catalogue.setText("Каталог станций");catalogue.setTextColor(Color.WHITE);catalogue.setTypeface(PosterTypography.face(c));catalogue.setBackground(buttonBackground(dark?0xff2548b8:blue,dark?0xff315de0:0xff0625ad,22,s));catalogue.setContentDescription("radio_catalogue");catalogue.setOnClickListener(v->actions.openCatalogue());place(catalogue,530,522,251,84,22);
        waveform=new RadioWaveformView(c);waveform.setColor(signal);place(waveform,0,640,1469,180,0);
        Button grant=s.button("Разрешить визуализацию","radio_waveform_permission",()->{if(android.os.Build.VERSION.SDK_INT>=23&&c instanceof android.app.Activity)((android.app.Activity)c).requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},RadioScreen.WAVEFORM_PERMISSION);});permission=grant;place(grant,570,704,330,56,20);
        mute=new VolumeButton(c,R.id.radio_mute,()->{AudioPlayback.toggleMute(c);syncVolume();});s.plain(mute,false);place(mute,20,817,68,70,0);
        volume=label("",ink,true);volume.setContentDescription("radio_volume_label");place(volume,123,828,170,46,22);
        slider=new CenteredSeekBar(c);slider.setMax(100);slider.setContentDescription("radio_volume");
        LayerDrawable track=new LayerDrawable(new Drawable[]{new Track(s,trackColor),new ClipDrawable(new Track(s,progressColor),Gravity.LEFT,ClipDrawable.HORIZONTAL)});track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);slider.setProgressDrawable(track);slider.setThumb(new Thumb(s));slider.setThumbOffset(s.dp(18));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){AudioPlayback.volume(c,value);syncVolume();}}public void onStopTrackingTouch(SeekBar b){actions.volume(b.getProgress());}});place(slider,300,818,1125,64,0);
        state=label(playback,muted,false);state.setContentDescription("radio_state");place(state,60,929,670,34,16);
        String quality=station==null?"":PosterLabels.quality(station.name);if(!quality.isEmpty())place(label(quality,muted,false),60,964,670,21,12);
        tick();
    }
    void tick(){
        syncVolume();String value=RadioPlaybackService.state();boolean starting=RadioPlaybackService.starting(),busy=RadioPlaybackService.active()||starting;
        toggle.symbol(starting?"loading":busy?"stop":"play");toggle.setContentDescription(busy?"radio_stop":"radio_play");
        boolean allowed=RadioPlaybackService.waveformAllowed(getContext());permission.setVisibility(allowed?GONE:VISIBLE);waveform.setPlaying(allowed&&"playing".equals(value));
        boolean next="playing".equals(value);if(live!=next){live=next;invalidate();}
    }
    private void syncVolume(){int level=RadioStore.volume(getContext());PanelStyle.update(volume,"Громкость "+level+"%");if(!slider.isPressed())slider.setProgress(level);mute.refresh();}
    void pause(){waveform.setPlaying(false);}
    void restore(){pause();for(int n=borrowed.size()-1;n>=0;n--)borrowed.get(n).restore();borrowed.clear();output.contrast(false);footer.setVisibility(footerVisibility);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,int color,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private static Drawable buttonBackground(int color,int pressed,int radius,PanelStyle s){StateListDrawable states=new StateListDrawable();int[][] sets={{-android.R.attr.state_enabled},{android.R.attr.state_pressed},{android.R.attr.state_focused},{}};int[] colors={s.palette.dark?0xff27364b:0xffccd2dc,pressed,pressed,color};for(int n=0;n<sets.length;n++){GradientDrawable d=new GradientDrawable();d.setColor(colors[n]);d.setCornerRadius(s.dp(radius));states.addState(sets[n],d);}return states;}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);float sx=w/WIDTH,sy=h/HEIGHT;for(Box b:boxes){if(b.font>0)((TextView)b.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.view.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH,sy=getHeight()/HEIGHT;for(Box item:boxes){int x=Math.round(item.x*sx),y=Math.round(item.y*sy);item.view.layout(x,y,x+item.view.getMeasuredWidth(),y+item.view.getMeasuredHeight());}}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(blue);Path corner=new Path();corner.moveTo(960,0);corner.lineTo(WIDTH,0);corner.lineTo(WIDTH,449);corner.close();c.drawPath(corner,paint);paint.setStrokeWidth(1);paint.setColor(rule);c.drawLine(30,909,1440,909,paint);paint.setColor(live?liveDot:idleDot);c.drawCircle(40,946,9,paint);c.restoreToCount(save);}

    private static final class RadioPhoto extends ImageView {
        private final Path mask=new Path();RadioPhoto(Context c,boolean dark){super(c);if(photograph==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inSampleSize=2;o.inPreferredConfig=Bitmap.Config.RGB_565;photograph=BitmapFactory.decodeResource(getResources(),R.drawable.neo_radio_neva_v2,o);}setImageBitmap(photograph);if(dark)setColorFilter(0x33050d1c,PorterDuff.Mode.SRC_ATOP);setScaleType(ScaleType.CENTER_CROP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);mask.rewind();mask.moveTo(w*.107f,0);mask.lineTo(w*.34f,0);mask.lineTo(w,h*.65f);mask.lineTo(w,h*.86f);mask.cubicTo(w*.70f,h*1.08f,w*.14f,h*1.02f,0,h*.57f);mask.cubicTo(-w*.04f,h*.32f,w*.04f,h*.12f,w*.107f,0);mask.close();}
        @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);super.draw(c);c.restoreToCount(save);}
    }
    private static final class StationArt extends View {
        private final String initials;private final int base,sunTop,sunBottom,letters;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect bounds=new Rect();
        StationArt(Context c,String initials){super(c);this.initials=initials;boolean dark=new PanelStyle(c).palette.dark;base=dark?0xff101c2f:0xff061521;sunTop=dark?0xffffc675:0xffffaa42;sunBottom=dark?0xffff8a50:0xffff4b12;letters=dark?0xffffd29b:0xffffb658;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/237f,getHeight()/280f);p.setColor(base);c.drawRect(0,0,237,280,p);p.setShader(new LinearGradient(35,120,170,280,sunTop,sunBottom,Shader.TileMode.CLAMP));c.drawCircle(98,257,94,p);p.setShader(null);p.setColor(base);for(int y=217;y<280;y+=10)c.drawRect(0,y,237,y+5,p);
            p.setTypeface(Typeface.create("serif",Typeface.NORMAL));p.setTextSize(100);p.setColor(letters);p.getTextBounds(initials,0,initials.length(),bounds);float scale=Math.min(215f/Math.max(1,bounds.width()),196f/Math.max(1,bounds.height()));c.translate((237-bounds.width()*scale)/2,35);c.scale(scale,scale);c.drawText(initials,-bounds.left,-bounds.top,p);c.restoreToCount(save);}
    }
    private static final class StationTitle extends TextView {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect bounds=new Rect();private final Typeface latin;
        StationTitle(Context c){super(c);latin=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");}
        @Override protected void onDraw(Canvas c){String text=getText().toString();p.setTypeface(text.matches(".*[А-Яа-яЁё].*")?PosterTypography.face(getContext()):latin);p.setTextSize(100);p.setColor(getCurrentTextColor());String[] lines={text};float best=Float.MAX_VALUE;
            for(int n=1;n<text.length()-1;n++)if(text.charAt(n)==' '){String left=text.substring(0,n).trim(),right=text.substring(n+1).trim();float width=Math.max(p.measureText(left),p.measureText(right));if(width<best){best=width;lines=new String[]{left,right};}}
            float width=0,height=0;int[] tops=new int[lines.length],heights=new int[lines.length];for(int n=0;n<lines.length;n++){p.getTextBounds(lines[n],0,lines[n].length(),bounds);width=Math.max(width,p.measureText(lines[n]));tops[n]=bounds.top;heights[n]=bounds.height();height+=bounds.height();}height+=(lines.length-1)*8;float scale=Math.min(getWidth()/Math.max(1,width),getHeight()/Math.max(1,height));int save=c.save();c.translate(0,(getHeight()-height*scale)/2);c.scale(scale,scale);float y=0;for(int n=0;n<lines.length;n++){c.drawText(lines[n],0,y-tops[n],p);y+=heights[n]+8;}c.restoreToCount(save);}
    }
    private static final class RadioToggle extends Button {
        private String symbol="play";private PanelIcon icon;private int size,tint;
        RadioToggle(Context c){super(c);setText("");setPadding(0,0,0,0);}
        void symbol(String value){if(!symbol.equals(value)){symbol=value;icon=null;invalidate();}}
        @Override protected void onDraw(Canvas c){int next=Math.round(Math.min(getWidth(),getHeight())*.72f);if(icon==null||size!=next||tint!=getCurrentTextColor()){size=next;tint=getCurrentTextColor();icon=new PanelIcon(symbol,tint,size);}int x=(getWidth()-size)/2,y=(getHeight()-size)/2;icon.setBounds(x,y,x+size,y+size);icon.draw(c);}
    }
    private static final class FavoriteButton extends Button {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path star=new Path();FavoriteButton(Context c){super(c);setPadding(0,0,0,0);}
        @Override protected void onDraw(Canvas c){p.setColor(getCurrentTextColor());p.setStyle("★".contentEquals(getText())?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(getWidth()*.045f);p.setStrokeJoin(Paint.Join.ROUND);star.rewind();float radius=Math.min(getWidth(),getHeight())*.35f;for(int n=0;n<10;n++){double angle=-Math.PI/2+n*Math.PI/5;float r=n%2==0?radius:radius*.47f,x=getWidth()/2f+(float)Math.cos(angle)*r,y=getHeight()/2f+(float)Math.sin(angle)*r;if(n==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();c.drawPath(star,p);}
    }
    private static final class CatalogueButton extends Button {
        CatalogueButton(Context c){super(c);PanelStyle s=new PanelStyle(c);setAllCaps(false);setSingleLine();setPadding(s.dp(12),0,s.dp(12),0);setCompoundDrawablePadding(s.dp(6));setCompoundDrawablesWithIntrinsicBounds(new PanelIcon("stations",Color.WHITE,s.dp(26)),null,null,null);}
    }
    private static final class Track extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int radius;Track(PanelStyle s,int color){p.setColor(color);radius=s.dp(3);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Thumb extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size,radius,halo,fill;Thumb(PanelStyle s){size=s.dp(36);radius=s.dp(12);halo=s.palette.dark?0x329ab7ff:0x220634e8;fill=s.palette.dark?s.color(PanelStyle.TEXT):Color.WHITE;}public void draw(Canvas c){Rect b=getBounds();p.setColor(halo);c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius*1.32f,p);p.setColor(fill);c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
