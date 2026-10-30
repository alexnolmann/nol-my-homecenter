package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import local.mediacentre.client.R;

/** Shared light/dark settings poster. Native forms and playback controls retain their actions. */
final class PosterSettingsView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private final boolean dark;
    private final int ink,blue,signal,rule,background,inputBackground,trackColor,sliderAccent,thumbFill;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final View miniRoot,footer;private final int miniVisibility,footerVisibility;
    private final AudioOutputControls output;
    final ScrollView displayScroll,connectionScroll,audioScroll;
    int[] scrollPosition(){return new int[]{displayScroll.getScrollY(),connectionScroll.getScrollY(),audioScroll.getScrollY()};}
    void scrollPosition(int[] position){post(()->{if(isAttachedToWindow()){displayScroll.scrollTo(0,position[0]);connectionScroll.scrollTo(0,position[1]);audioScroll.scrollTo(0,position[2]);}});}
    private static final class Box {final View v;final float x,y,w,h,font;Box(View v,float x,float y,float w,float h,float f){this.v=v;this.x=x;this.y=y;this.w=w;this.h=h;font=f;}}
    private static final class Borrowed {
        final View v;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index,visibility;final Drawable background,thumb,track;final int offset;
        final Typeface face;final float font,scale;final int gravity,maxLines;final boolean padding;final ColorStateList colors;
        Borrowed(View v){this.v=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();visibility=v.getVisibility();
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();maxLines=t.getMaxLines();}else{face=null;font=scale=0;gravity=maxLines=0;padding=false;colors=null;}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;thumb=b.getThumb();track=b.getProgressDrawable();offset=b.getThumbOffset();}else{thumb=track=null;offset=0;}
            parent.removeView(v);
        }
        void restore(){((ViewGroup)v.getParent()).removeView(v);parent.addView(v,index,params);v.setBackground(background);v.setVisibility(visibility);
            if(v instanceof TextView){TextView t=(TextView)v;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);t.setMaxLines(maxLines);}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;b.setProgressDrawable(track);b.setThumb(thumb);b.setThumbOffset(offset);}new PanelStyle(v.getContext()).repaint(v);
        }
    }
    PosterSettingsView(Context c,SettingsScreen.Actions actions,TextClock clock,AudioOutputControls output,TextView status,
            View miniRoot,TextView miniName,View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume,View footer){
        super(c);setWillNotDraw(false);this.output=output;PanelStyle s=new PanelStyle(c);
        dark=s.palette.dark;ink=dark?s.color(PanelStyle.TEXT):0xff050d15;blue=dark?0xff16358a:0xff0634e8;
        signal=dark?s.color(PanelStyle.SIGNAL):0xffff4b12;rule=dark?0xff43566e:0xffaab1b8;background=s.color(PanelStyle.BG);
        inputBackground=dark?0xff202d43:0xffe9edf0;trackColor=dark?0xff3b4d66:0xffbfc6d0;
        sliderAccent=dark?s.color(PanelStyle.ACCENT):blue;thumbFill=dark?s.color(PanelStyle.CARD):Color.WHITE;
        this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);this.footer=footer;footerVisibility=footer.getVisibility();footer.setVisibility(GONE);
        place(new HeaderPhoto(c,dark),410,0,1059,215,0);place(new TitlePaper(c,background),0,0,740,215,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        place(label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),false),24,16,620,32,24);
        TextView title=label("Настройки",true);title.setTextScaleX(.82f);title.setContentDescription("settings_title");place(title,24,59,700,151,144);
        PosterAudioHeader.bind(this::take,clock,output);
        LinearLayout display=s.column(),connection=s.column(),audio=s.column();
        displayScroll=column(display,"settings_display_scroll",24,238,540,633);
        connectionScroll=column(connection,"settings_connection_scroll",610,238,370,633);
        audioScroll=column(audio,"settings_audio_scroll",1025,238,421,633);
        section(display,"Экран и оформление","screen");new SettingsScreen(c).posterDisplay(display,actions);decorateSliders(display);
        section(connection,"Подключение","link");actions.pairing(connection);
        for(int n=0;n<connection.getChildCount();n++){
            View child=connection.getChildAt(n);
            if(child instanceof EditText){EditText edit=(EditText)child;edit.setTextSize(16);edit.setMinHeight(s.dp(46));s.bind(edit,p->{edit.setTextColor(ink);edit.setHintTextColor(p.color(PanelStyle.MUTED));GradientDrawable face=new GradientDrawable();face.setColor(inputBackground);face.setCornerRadius(p.dp(3));edit.setBackground(face);});}
            else if(child instanceof Button){Button button=(Button)child;if("pair_submit".equals(child.getContentDescription()))button.setText("Подключить");button.setTextSize(20);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,s.dp(48));p.topMargin=s.dp(12);p.bottomMargin=s.dp(8);button.setLayoutParams(p);}
            else if(child instanceof TextView&&n>0){((TextView)child).setTextSize(15);}
        }
        Button check=s.button("Проверить сервер","settings_check",()->{});check.setOnClickListener(v->actions.check(check));LinearLayout.LayoutParams checkParams=new LinearLayout.LayoutParams(-1,s.dp(48));checkParams.topMargin=s.dp(12);connection.addView(check,checkParams);
        borrowed.add(new Borrowed(status));connection.addView(status,new LinearLayout.LayoutParams(-1,-2));status.setVisibility(VISIBLE);status.setTextSize(14);status.setMaxLines(4);s.status(status);
        new SettingsScreen(c).diagnostics(connection,actions,false);
        AudioOutputUi.settings(audio);LinearLayout card=(LinearLayout)PanelStyle.find(audio,"audio_output_settings");card.removeViewAt(0);TextView audioTitle=sectionTitle("Аудиовыход","speaker");card.addView(audioTitle,0,new LinearLayout.LayoutParams(-1,s.dp(46)));TextView currentLabel=s.text("Текущее устройство",16,PanelStyle.TEXT);card.addView(currentLabel,2);
        View current=card.findViewWithTag("audio_output_current");if(current instanceof TextView){TextView name=(TextView)current;name.setTextSize(21);if(dark)s.bind(name,p->{GradientDrawable face=p.surface(PanelStyle.ACCENT,-1,4);face.setColor(0xff2548b8);name.setBackground(face);name.setTextColor(Color.WHITE);});name.setCompoundDrawablePadding(s.dp(10));name.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon("speaker",Color.WHITE,s.dp(26)),null,new PanelIcon("check",Color.WHITE,s.dp(20)),null);}
        String[] names={"audio_output_primary","audio_output_rename","audio_output_pairing"},icons={"speaker","stations","bluetooth"};
        for(int n=0;n<names.length;n++){Button button=(Button)PanelStyle.find(audio,names[n]);button.setTextSize(18);button.setCompoundDrawablePadding(s.dp(12));button.setCompoundDrawablesWithIntrinsicBounds(new PanelIcon(icons[n],ink,s.dp(25)),null,new PanelIcon("chevron_right",ink,s.dp(18)),null);}
        ImageView badge=new ImageView(c);badge.setImageBitmap(PosterPhotoView.bitmap(c));badge.setScaleType(ImageView.ScaleType.CENTER_CROP);if(dark)badge.setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);place(badge,14,912,63,63,0);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,0);
    }
    private void decorateSliders(View view){
        if(view instanceof SeekBar){SeekBar bar=(SeekBar)view;PanelStyle s=new PanelStyle(getContext());LayerDrawable track=new LayerDrawable(new Drawable[]{new Track(getContext(),dark?trackColor:0xffd3d8de),new ClipDrawable(new Track(getContext(),sliderAccent),Gravity.LEFT,ClipDrawable.HORIZONTAL)});track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);bar.setProgressDrawable(track);GradientDrawable thumb=new GradientDrawable();thumb.setShape(GradientDrawable.OVAL);thumb.setColor(thumbFill);thumb.setStroke(s.dp(2),sliderAccent);thumb.setSize(s.dp(18),s.dp(18));bar.setThumb(thumb);bar.setThumbOffset(s.dp(9));}
        else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)decorateSliders(group.getChildAt(n));}
    }
    private TextView sectionTitle(String text,String icon){PanelStyle s=new PanelStyle(getContext());TextView title=s.title(text,26);title.setCompoundDrawablePadding(s.dp(12));title.setCompoundDrawablesWithIntrinsicBounds(new SettingsIcon(icon,ink,s.dp(30)),null,null,null);return title;}
    private void section(LinearLayout column,String text,String icon){PanelStyle s=new PanelStyle(getContext());column.addView(sectionTitle(text,icon),new LinearLayout.LayoutParams(-1,s.dp(46)));s.gap(column,8);}
    private ScrollView column(LinearLayout form,String id,float x,float y,float w,float h){ScrollView scroll=new ScrollView(getContext());scroll.setContentDescription(id);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);scroll.addView(form,new ScrollView.LayoutParams(-1,-2));scroll.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE)v.getParent().requestDisallowInterceptTouchEvent(true);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);return false;});place(scroll,x,y,w,h,0);return scroll;}
    void restore(){for(int n=borrowed.size()-1;n>=0;n--)borrowed.get(n).restore();borrowed.clear();miniRoot.setVisibility(miniVisibility);footer.setVisibility(footerVisibility);output.contrast(false);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,boolean title){TextView view=new TextView(getContext());view.setText(text);view.setTextColor(ink);view.setTypeface(title?PosterTypography.face(getContext()):Typeface.create("sans-serif",Typeface.BOLD));view.setSingleLine();view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);float sx=w/WIDTH,sy=h/HEIGHT;for(Box b:boxes){if(b.font>0)((TextView)b.v).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.v.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH,sy=getHeight()/HEIGHT;for(Box box:boxes){int x=Math.round(box.x*sx),y=Math.round(box.y*sy);box.v.layout(x,y,x+box.v.getMeasuredWidth(),y+box.v.getMeasuredHeight());}}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(rule);paint.setStrokeWidth(1);c.drawLine(586,238,586,871,paint);c.drawLine(1002,238,1002,871,paint);c.drawLine(14,900,1447,900,paint);c.restoreToCount(save);}
    private static final class HeaderPhoto extends ImageView {HeaderPhoto(Context c,boolean dark){super(c);setImageBitmap(PosterPhotoView.bitmap(c));setScaleType(ScaleType.CENTER_CROP);if(dark)setColorFilter(0x33050d1c,PorterDuff.Mode.SRC_ATOP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}}
    private static final class TitlePaper extends View {private final Paint p=new Paint(3);private final Path path=new Path();TitlePaper(Context c,int color){super(c);p.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}@Override protected void onDraw(Canvas c){path.rewind();path.moveTo(0,0);path.lineTo(getWidth()*.75f,0);path.lineTo(getWidth(),getHeight());path.lineTo(0,getHeight());path.close();c.drawPath(path,p);}}
    private static final class SettingsIcon extends Drawable {private final String kind;private final int color,size;private final Paint p=new Paint(3);SettingsIcon(String kind,int color,int size){this.kind=kind;this.color=color;this.size=size;}public void draw(Canvas c){if("speaker".equals(kind)){PanelIcon icon=new PanelIcon(kind,color,size);icon.setBounds(getBounds());icon.draw(c);return;}int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);if("screen".equals(kind)){c.drawRoundRect(new RectF(2,3,22,17),1,1,p);c.drawLine(12,17,12,21,p);c.drawLine(8,21,16,21,p);}else{c.save();c.rotate(-40,12,12);c.drawRoundRect(new RectF(2,8,13,16),4,4,p);c.drawRoundRect(new RectF(11,8,22,16),4,4,p);c.restore();}c.restoreToCount(save);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    private static final class Track extends Drawable {final Paint p=new Paint(3);final int r;Track(Context c,int color){r=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-r,b.right,b.exactCenterY()+r),r,r,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    private static final class Thumb extends Drawable {final Paint p=new Paint(3);final int size,r;Thumb(Context c,int color){size=new PanelStyle(c).dp(10);r=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),r,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
}
