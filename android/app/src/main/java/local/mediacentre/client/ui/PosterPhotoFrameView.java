package local.mediacentre.client.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import local.mediacentre.client.R;

/** Shared light/dark photo-frame settings. Album and slideshow retain their owners. */
final class PosterPhotoFrameView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private final int ink,muted,blue,signal,soft,background,rule,selected,pressed,onSignal,disabled,trackColor;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final View miniRoot,statusRoot;private final int miniVisibility,statusVisibility;
    private final AudioOutputControls output;
    final PreviewSlot photos;
    final TextView summary;
    final Button start;
    Runnable modeChanged;

    private static final class Box {final View v;final float x,y,w,h,font;Box(View v,float x,float y,float w,float h,float f){this.v=v;this.x=x;this.y=y;this.w=w;this.h=h;font=f;}}
    private static final class Borrowed {
        final View v;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;final Drawable background,thumb,track;final int offset;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;final ColorStateList colors;
        Borrowed(View v){this.v=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();}else{face=null;font=scale=0;gravity=0;padding=false;colors=null;}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;thumb=b.getThumb();track=b.getProgressDrawable();offset=b.getThumbOffset();}else{thumb=track=null;offset=0;}
            parent.removeView(v);
        }
        void restore(){((ViewGroup)v.getParent()).removeView(v);parent.addView(v,index,params);v.setBackground(background);
            if(v instanceof TextView){TextView t=(TextView)v;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;b.setProgressDrawable(track);b.setThumb(thumb);b.setThumbOffset(offset);}new PanelStyle(v.getContext()).repaint(v);
        }
    }
    PosterPhotoFrameView(Context c,SharedPreferences settings,Runnable launch,TextClock clock,AudioOutputControls output,
            View miniRoot,TextView miniName,View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume,View statusRoot){
        super(c);setWillNotDraw(false);this.output=output;
        PanelStyle style=new PanelStyle(c);boolean dark=style.palette.dark;
        ink=dark?style.color(PanelStyle.TEXT):0xff050d15;muted=dark?0xffb8c9df:0xff657180;
        blue=dark?0xff16358a:0xff0634e8;signal=dark?style.color(PanelStyle.SIGNAL):0xffff4b12;
        soft=dark?0xff202d43:0xffe9edf0;background=style.color(PanelStyle.BG);rule=dark?0xff43566e:muted;
        selected=dark?0xff2548b8:blue;pressed=dark?0xff315de0:0xff0828ac;
        onSignal=dark?0xff0b101b:Color.WHITE;disabled=dark?0xff303d52:0xffc7cdd5;trackColor=dark?0xff3b4d66:0xffbfc6d0;
        this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);
        this.statusRoot=statusRoot;statusVisibility=statusRoot.getVisibility();statusRoot.setVisibility(GONE);
        photos=new PreviewSlot(c);place(photos,0,160,978,674,0);
        place(new TitlePaper(c,background),0,0,620,204,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        place(label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),ink,false),24,16,650,32,24);
        TextView title=label("Фоторамка",ink,true);title.setTextScaleX(.78f);title.setContentDescription("photoframe_title");place(title,24,48,700,156,140);
        PosterAudioHeader.bind(this::take,clock,output);
        place(label("Показ фотографий",ink,true),1009,195,437,48,38);
        summary=label("Загружаем список…",muted,false);summary.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));summary.setContentDescription("photoframe_summary");place(summary,1009,245,437,39,27);
        place(label("Смена фотографий",ink,true),1009,317,437,40,30);
        int[] seconds={5,10,20,60};Button[] speeds=new Button[seconds.length];
        for(int n=0;n<seconds.length;n++){final int value=seconds[n];Button button=new Choice(c,false,false);button.setText(value+" секунд");button.setContentDescription("photoframe_seconds_"+value);button.setSelected(settings.getInt("seconds",10)==value);speeds[n]=button;button.setOnClickListener(v->{settings.edit().putInt("seconds",value).apply();for(int k=0;k<speeds.length;k++)speeds[k].setSelected(seconds[k]==value);});place(button,1009+n*109.25f,365,109.25f,70,20);}
        PosterSwitch shuffle=new PosterSwitch(c);shuffle.setText("Случайный порядок");shuffle.setContentDescription("photoframe_shuffle");new PanelStyle(c).checkbox(shuffle);shuffle.colors(selected,dark?0xff43566e:0xffc1c9d2,Color.WHITE);shuffle.setTypeface(PosterTypography.face(c));shuffle.setChecked(settings.getBoolean("shuffle",false));shuffle.setOnCheckedChangeListener((v,on)->settings.edit().putBoolean("shuffle",on).apply());place(shuffle,1009,470,437,69,29);
        place(label("Режим показа",ink,true),1009,567,437,38,30);
        Button[] modes=new Button[2];
        for(int n=0;n<2;n++){final boolean fill=n==1;Button button=new Choice(c,true,fill);button.setText(fill?"Заполнить экран":"С полосами");button.setContentDescription(fill?"photoframe_fill":"photoframe_fit");button.setSelected(settings.getBoolean("fill",false)==fill);modes[n]=button;button.setOnClickListener(v->{settings.edit().putBoolean("fill",fill).apply();for(int k=0;k<modes.length;k++)modes[k].setSelected((k==1)==fill);if(modeChanged!=null)modeChanged.run();});place(button,1009+n*226,610,211,115,23);}
        Choice launchButton=new Choice(c,false,false);launchButton.launchButton=true;launchButton.background();launchButton.setTextColor(onSignal);start=launchButton;start.setText("Начать показ");start.setTypeface(PosterTypography.face(c));start.setContentDescription("photoframe_start");start.setEnabled(false);start.setOnClickListener(v->launch.run());place(start,1009,751,437,83,35);
        ImageView badge=new ImageView(c);badge.setImageBitmap(PosterPhotoView.bitmap(c));badge.setScaleType(ImageView.ScaleType.CENTER_CROP);badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);place(badge,14,912,63,63,0);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,0);
    }
    void restore(){for(int n=borrowed.size()-1;n>=0;n--)borrowed.get(n).restore();borrowed.clear();miniRoot.setVisibility(miniVisibility);statusRoot.setVisibility(statusVisibility);output.contrast(false);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,int color,boolean display){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(color);v.setTypeface(display?PosterTypography.face(getContext()):Typeface.create("sans-serif",Typeface.BOLD));v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);float sx=w/WIDTH,sy=h/HEIGHT;for(Box b:boxes){if(b.font>0)((TextView)b.v).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.v.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH,sy=getHeight()/HEIGHT;for(Box box:boxes){int x=Math.round(box.x*sx),y=Math.round(box.y*sy);box.v.layout(x,y,x+box.v.getMeasuredWidth(),y+box.v.getMeasuredHeight());}}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(rule);paint.setStrokeWidth(1);c.drawLine(825,156,1446,156,paint);c.drawLine(987,198,987,834,paint);for(int y:new int[]{296,455,545})c.drawLine(1009,y,1446,y,paint);c.drawLine(14,900,1447,900,paint);c.restoreToCount(save);}
    static final class PreviewSlot extends LinearLayout {PreviewSlot(Context c){super(c);}}
    static final class PreviewImage extends ImageView {
        private final Path mask=new Path();PreviewImage(Context c){super(c);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);mask.rewind();mask.moveTo(0,0);mask.lineTo(w*.77f,0);mask.cubicTo(w*.92f,h*.08f,w,h*.25f,w,h*.52f);mask.cubicTo(w,h*.75f,w*.92f,h*.93f,w*.79f,h);mask.lineTo(0,h);mask.close();}
        @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);super.draw(c);c.restoreToCount(save);}
    }
    private static final class TitlePaper extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path paper=new Path();TitlePaper(Context c,int color){super(c);p.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){paper.rewind();paper.moveTo(0,0);paper.lineTo(getWidth(),0);paper.lineTo(getWidth(),getHeight());paper.lineTo(getWidth()*.045f,getHeight());paper.lineTo(0,getHeight()*.84f);paper.close();c.drawPath(paper,p);}
    }
    private final class Choice extends Button {
        final boolean mode,fill;boolean launchButton;
        Choice(Context c,boolean mode,boolean fill){super(c);this.mode=mode;this.fill=fill;setAllCaps(false);setSingleLine();setIncludeFontPadding(false);setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));setMinWidth(0);setMinHeight(0);setPadding(0,0,0,0);setGravity(Gravity.CENTER);background();}
        @Override public void setSelected(boolean selected){super.setSelected(selected);setTextColor(launchButton?onSignal:selected?Color.WHITE:ink);icon();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);icon();}
        private void icon(){if(getHeight()<=0)return;int color=launchButton?onSignal:isSelected()?Color.WHITE:ink;
            if(mode){
                float unit=getHeight()/115f;int size=Math.round(34*unit),gap=Math.round(10*unit);
                Paint.FontMetrics metrics=getPaint().getFontMetrics();
                int inset=Math.max(0,Math.round((getHeight()-size-gap-(metrics.descent-metrics.ascent))/2f));
                // KitKat anchors the top drawable to padding. Reserve equal outer space for the whole group.
                setCompoundDrawablesWithIntrinsicBounds(null,new ModeIcon(fill,color,size),null,null);
                setCompoundDrawablePadding(gap);setPadding(0,inset,0,inset);
            }
            else if(launchButton){setCompoundDrawablesWithIntrinsicBounds(new PanelIcon("play",onSignal,Math.round(getHeight()*.52f)),null,null,null);setCompoundDrawablePadding(Math.round(getHeight()*.08f));setPadding(Math.round(getWidth()*.16f),0,Math.round(getWidth()*.10f),0);}
        }
        void background(){StateListDrawable state=new StateListDrawable();state.addState(new int[]{-android.R.attr.state_enabled},surface(launchButton?disabled:soft));state.addState(new int[]{android.R.attr.state_pressed},surface(launchButton?(onSignal==Color.WHITE?0xffe6410a:0xffe6743f):pressed));state.addState(new int[]{android.R.attr.state_selected},surface(selected));state.addState(new int[]{},surface(launchButton?signal:soft));setBackground(state);}
        private GradientDrawable surface(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(mode?6:3);return d;}
    }
    private static final class ModeIcon extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean fill;final int size;final Path shape=new Path();
        ModeIcon(boolean fill,int color,int size){this.fill=fill;this.size=size;p.setColor(color);}
        public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.SQUARE);p.setStrokeJoin(Paint.Join.MITER);
            if(fill){for(int n=0;n<4;n++){c.save();c.rotate(n*90,12,12);shape.rewind();shape.moveTo(3,8);shape.lineTo(3,3);shape.lineTo(8,3);c.drawPath(shape,p);c.restore();}}
            else{c.drawRect(6,3,18,21,p);c.drawLine(2,3,2,21,p);c.drawLine(22,3,22,21,p);}c.restoreToCount(save);
        }
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Track extends Drawable {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int r;Track(Context c,int color){r=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-r,b.right,b.exactCenterY()+r),r,r,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    private static final class Thumb extends Drawable {final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size,r;Thumb(Context c,int color){size=new PanelStyle(c).dp(10);r=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),r,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
}
