package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.climate.ClimateSensor;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.*;
import local.mediacentre.client.radio.*;

/** The approved 1586 × 992 poster, excluding its 117 px navigation rail.
 * Both colour modes share geometry. Children are real, accessible Android controls. */
final class PosterHomeView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private static final int INK=0xff050d15,BLUE=0xff0634e8,ORANGE=0xffff4b12;
    private static Bitmap shore,musician;
    private static Typeface numbers;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final boolean dark;
    private final int ink,blue,signal,musicColor,controlInk,line;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final TextView title,date,room;
    private final InkText roomValue,humidity;
    private final SimpleDateFormat dateFormat;
    private final Audio radio,music;
    final PhotoSlot photos;
    private String lastDate;
    private static final class Box {
        final View view;final float x,y,w,h,font;
        Box(View v,float x,float y,float w,float h,float font){view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;}
    }
    private static final class Audio {
        TextView name;FrameLayout control;ImageView icon;ProgressBar spinner;PosterHomeBars bars;
        String lastName,lastState;
    }

    PosterHomeView(Context context,Dtos.Dashboard data,HomeScreen.Actions actions){
        super(context);setWillNotDraw(false);setClipChildren(true);
        PanelStyle style=new PanelStyle(context);dark=style.palette.dark;ink=dark?style.color(PanelStyle.TEXT):INK;blue=dark?0xff102a94:BLUE;signal=dark?style.color(PanelStyle.SIGNAL):ORANGE;musicColor=dark?style.color(PanelStyle.ACCENT):BLUE;controlInk=dark?style.color(PanelStyle.ON_ACCENT):Color.WHITE;line=dark?style.color(PanelStyle.LINE):0xff28343d;
        if(numbers==null)numbers=Typeface.createFromAsset(context.getAssets(),"fonts/Anton-Regular.ttf");
        if(shore==null)shore=decode(context,R.drawable.neo_home_shore,2,false);
        if(musician==null)musician=decode(context,R.drawable.neo_home_music,4,true);
        title=label("",46,true,ink);title.setContentDescription("home_title");place(title,25,14,535,54,46);
        InkClock clock=new InkClock(context,ink);clock.setTimeZone(data.weather.timezone);clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");clock.setContentDescription("home_clock");place(clock,17,82,543,370,0);
        date=label("",35,true,ink);date.setGravity(Gravity.CENTER);date.setContentDescription("home_date");place(date,18,465,544,48,35);
        dateFormat=new SimpleDateFormat("EEEE, d MMMM",new Locale("ru"));dateFormat.setTimeZone(TimeZone.getTimeZone(data.weather.timezone));
        View weather=new View(context);weather.setContentDescription("open_weather");weather.setOnClickListener(v->actions.open("weather"));weather.setFocusable(true);place(weather,1100,0,369,542,0);
        TextView city=label(data.weather.city,34,true,Color.WHITE);place(city,1108,16,345,55,34);
        InkText degrees=ink(temperature(data.weather.temperature),Color.WHITE);place(degrees,1110,85,181,189,0);
        place(new HomeWeatherView(context,data.weather.condition),1286,79,172,201,0);
        int n=0;for(Dtos.WeatherPeriod period:WeatherScreen.upcoming(data.weather)){
            if(n==4)break;float y=294+n++*57;
            String periodName=WeatherScreen.periodLabel(period.at,data.weather.timezone).replace(" · завтра","").replace("Ночью","Ночь").replace("Утром","Утро").replace("Днём","День").replace("Вечером","Вечер");
            TextView label=label(periodName,20,true,Color.WHITE);place(label,1231,y+5,86,47,20);
            place(new HomeWeatherView(context,period.condition),1320,y+3,45,47,0);
            TextView value=label(temperature(period.temperature),25,true,Color.WHITE);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(value,1372,y+5,69,47,25);
        }
        radio=audio("ИНТЕРНЕТ-РАДИО","radio",actions,220,580,588,54,662,630,134,signal,()->{
            if(RadioPlaybackService.active()||RadioPlaybackService.starting())RadioPlaybackService.stop(context);
            else if(RadioStore.saved(context)!=null)RadioPlaybackService.play(context);else actions.open("stations");tick();
        });
        radio.bars=new PosterHomeBars(context,signal);place(radio.bars,220,645,420,112,0);
        ImageView art=new ImageView(context);art.setImageBitmap(musician);art.setScaleType(ImageView.ScaleType.FIT_START);if(dark)art.setColorFilter(new ColorMatrixColorFilter(new float[]{.88f,0,0,0,14,0,.91f,0,0,18,0,0,1,0,25,0,0,0,1,0}));art.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);place(art,819,635,385,149,0);
        music=audio("МУЗЫКА","music",actions,834,582,375,49,1080,647,124,musicColor,()->{
            if(MusicPlaybackService.track()==null)actions.open("music");else if(MusicPlaybackService.active())MusicPlaybackService.pause(context);else MusicPlaybackService.play(context);tick();
        });
        View climate=new View(context);climate.setContentDescription("open_climate");climate.setOnClickListener(v->actions.open("climate"));climate.setFocusable(true);place(climate,1237,550,219,235,0);
        room=label("Климат в доме",34,true,ink);room.setContentDescription("home_climate");place(room,1251,561,204,51,34);
        roomValue=ink("—°",ink);place(roomValue,1252,625,188,80,0);
        ImageView drop=new ImageView(context);drop.setImageDrawable(new Drop(dark?0xffbac8d7:0xff203849,dark?0xff7695bb:0xff607c8d));place(drop,1256,730,30,40,0);
        humidity=ink("—%",dark?0xffbac8d7:0xff203849);place(humidity,1301,735,96,35,0);
        View finance=new View(context);finance.setContentDescription("open_finance");finance.setOnClickListener(v->actions.open("finance"));finance.setFocusable(true);place(finance,766,801,690,191,0);
        place(label("КУРСЫ ВАЛЮТ",15,true,ink),790,802,500,30,15);
        n=0;for(Dtos.Instrument item:data.finance.items){
            if(!"currency".equals(item.kind)||n==3)continue;float x=790+n++*238;
            place(label(item.symbol,30,true,ink),x,836,197,34,30);
            String price=item.enabled&&item.price!=null?item.price.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',','):"—";
            InkText rate=ink(price,ink);rate.setContentDescription("home_finance_"+item.symbol);place(rate,x,879,150,73,0);
            if(FinancialLabels.comparable(item)){
                ImageView delta=new ImageView(context);delta.setImageDrawable(new PosterDelta(item.change.signum()<0,item.change.signum()<0?(dark?0xffff9e8c:0xffff4b25):(dark?0xff6edda0:0xff07902e),24));delta.setContentDescription(FinancialLabels.change(item));place(delta,x+160,914,25,24,0);
            }
        }
        // Decorative corner covers the waveform only inside its visible diagonal.
        place(new CornerArtwork(context,dark),0,454,722,538,0);
        // The carousel follows the upper photo mask; exposed weather stays tappable.
        photos=new PhotoSlot(context,dark);place(photos,573,0,650,542,0);
        tick();
    }
    private static Bitmap decode(Context context,int resource,int sample,boolean alpha){BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inSampleSize=sample;o.inPreferredConfig=alpha?Bitmap.Config.ARGB_8888:Bitmap.Config.RGB_565;return BitmapFactory.decodeResource(context.getResources(),resource,o);}
    private TextView label(String value,int size,boolean bold,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setIncludeFontPadding(false);v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private InkText ink(String value,int color){InkText v=new InkText(getContext());v.setText(value);v.setTextColor(color);return v;}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private Audio audio(String heading,String destination,HomeScreen.Actions actions,float x,float y,float w,float font,float cx,float cy,float diameter,int color,Runnable toggle){
        Audio a=new Audio();View link=new View(getContext());link.setContentDescription("open_"+destination);link.setFocusable(true);link.setOnClickListener(v->actions.open(destination));place(link,x-9,548,w+12,235,0);
        place(label(heading,16,true,ink),x-9,554,w,25,16);
        a.name=label("",(int)font,true,ink);a.name.setTypeface(PosterTypography.face(getContext()));a.name.setTextScaleX(.93f);a.name.setContentDescription("home_"+destination+"_name");place(a.name,x,y,w,57,font);
        a.control=new FrameLayout(getContext());a.control.setContentDescription("home_"+destination+"_toggle");a.control.setFocusable(true);a.control.setBackground(new Circle(color));a.control.setOnClickListener(v->toggle.run());place(a.control,cx,cy,diameter,diameter,0);
        a.icon=new ImageView(getContext());a.icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);a.control.addView(a.icon,new FrameLayout.LayoutParams(-1,-1));
        a.spinner=new ProgressBar(getContext());a.spinner.setVisibility(GONE);a.spinner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);a.spinner.getIndeterminateDrawable().mutate().setColorFilter(controlInk,PorterDuff.Mode.SRC_IN);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(new PanelStyle(getContext()).dp(35),new PanelStyle(getContext()).dp(35),Gravity.CENTER);a.control.addView(a.spinner,lp);return a;
    }
    void tick(){
        PanelStyle.update(title,getContext().getSharedPreferences("connection",Context.MODE_PRIVATE).getString("display_title","Мой дом"));
        String today=dateFormat.format(new Date());if(!today.equals(lastDate)){lastDate=today;date.setText(today.substring(0,1).toUpperCase(new Locale("ru"))+today.substring(1));}
        RadioStation saved=RadioStore.saved(getContext());update(radio,RadioPlaybackService.active()?RadioPlaybackService.name():saved==null?"Выберите станцию":saved.name,RadioPlaybackService.state(),RadioPlaybackService.active()||RadioPlaybackService.starting(),false);
        local.mediacentre.client.music.MusicTrack track=MusicPlaybackService.track();update(music,track==null?"Выберите музыку":track.name,MusicPlaybackService.state(),MusicPlaybackService.active(),true);
    }
    private void update(Audio a,String name,String state,boolean active,boolean music){
        if(!TextUtils.equals(a.lastName,name)){a.lastName=name;a.name.setText(music?name:PosterLabels.station(name==null?"Выберите станцию":name));}
        if(a.bars!=null)a.bars.setPlaying("playing".equals(state));
        if(TextUtils.equals(a.lastState,state))return;a.lastState=state;
        boolean loading="connecting".equals(state)||"retrying".equals(state);a.icon.setImageDrawable(new Transport(active?(music?"pause":"stop"):"play",controlInk));a.icon.setVisibility(loading?GONE:VISIBLE);a.spinner.setVisibility(loading?VISIBLE:GONE);
    }
    void climate(List<ClimateSensor> sensors){for(ClimateSensor sensor:sensors)if(sensor.latest!=null){PanelStyle.update(room,sensor.name);PanelStyle.update(roomValue,String.format(Locale.ROOT,"%.1f°",sensor.latest.temperature).replace('.',','));PanelStyle.update(humidity,sensor.latest.humidity+"%");return;}PanelStyle.update(room,"Климат в доме");PanelStyle.update(roomValue,"—°");PanelStyle.update(humidity,"—%");}
    private static String temperature(java.math.BigDecimal value){if(value==null)return "—°";long t=Math.round(value.doubleValue());return (t>0?"+":"")+t+"°";}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=pageHeight(widthSpec,heightSpec);setMeasuredDimension(w,h);float sx=w/WIDTH,sy=h/HEIGHT;for(Box b:boxes){if(b.font>0)((TextView)b.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.view.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float sx=getWidth()/WIDTH,sy=getHeight()/HEIGHT;for(Box box:boxes){int x=Math.round(box.x*sx),y=Math.round(box.y*sy);box.view.layout(x,y,x+box.view.getMeasuredWidth(),y+box.view.getMeasuredHeight());}}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(dark?0xff0b101b:0xfff8f5ee);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(blue);c.drawRect(983,0,WIDTH,542,paint);
        paint.setColor(line);paint.setStrokeWidth(1);c.drawLine(203,542,1449,542,paint);c.drawLine(385,792,1449,792,paint);c.drawLine(812,562,812,780,paint);c.drawLine(1221,562,1221,780,paint);c.drawLine(746,809,746,971,paint);c.drawLine(996,820,996,966,paint);c.drawLine(1234,820,1234,966,paint);
        paint.setColor(dark?0x75ffffff:0xbfffffff);for(int i=0;i<4;i++)c.drawLine(1223,291+i*57,1449,291+i*57,paint);c.restoreToCount(save);
    }
    private static void drawCrop(Canvas c,Bitmap bitmap,RectF target,Paint p){float scale=Math.max(target.width()/bitmap.getWidth(),target.height()/bitmap.getHeight());float w=target.width()/scale,h=target.height()/scale;Rect source=new Rect(Math.round((bitmap.getWidth()-w)/2),Math.round((bitmap.getHeight()-h)/2),Math.round((bitmap.getWidth()+w)/2),Math.round((bitmap.getHeight()+h)/2));c.drawBitmap(bitmap,source,target,p);}

    /** TextClock retains Android's minute ticker; only its ink metrics are changed. */
    private static final class InkClock extends TextClock {
        private final InkDrawing ink=new InkDrawing();InkClock(Context c,int color){super(c);setIncludeFontPadding(false);setTextColor(color);}
        @Override protected void onDraw(Canvas c){ink.draw(c,getText(),getWidth(),getHeight(),getCurrentTextColor());}
    }
    private static final class InkText extends TextView {
        private final InkDrawing ink=new InkDrawing();InkText(Context c){super(c);setIncludeFontPadding(false);}
        @Override protected void onDraw(Canvas c){ink.draw(c,getText(),getWidth(),getHeight(),getCurrentTextColor());}
    }
    private static final class InkDrawing {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Rect bounds=new Rect();
        void draw(Canvas c,CharSequence chars,int width,int height,int color){String text=chars==null?"":chars.toString();if(text.isEmpty())return;p.setTypeface(numbers);p.setTextSize(100);p.setColor(color);p.getTextBounds(text,0,text.length(),bounds);if(bounds.width()==0||bounds.height()==0)return;int save=c.save();float sy=height/(float)bounds.height();if(text.startsWith("—"))sy=Math.min(sy,height/80f);c.scale(width/(float)bounds.width(),sy);c.drawText(text,-bounds.left,-bounds.top,p);c.restoreToCount(save);}
    }
    /** Upper carousel: both clipping and hit testing follow the curved photograph. */
    static final class PhotoSlot extends LinearLayout {
        private final Path mask=new Path();private final Region hit=new Region();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final boolean dark;private final Drawable placeholder;private final int background;
        PhotoSlot(Context c,boolean dark){super(c);this.dark=dark;setWillNotDraw(false);PanelStyle style=new PanelStyle(c);background=style.color(PanelStyle.SOFT);placeholder=new PanelIcon("photoframe",style.color(PanelStyle.MUTED),style.dp(64));}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);mask.rewind();mask.moveTo(47,0);mask.lineTo(410,0);mask.lineTo(650,430);mask.cubicTo(572,523,462,542,377,542);mask.cubicTo(147,542,-10,414,0,220);mask.cubicTo(2,128,25,44,47,0);mask.close();Matrix scale=new Matrix();scale.setScale(w/650f,h/542f);mask.transform(scale);hit.setPath(mask,new Region(0,0,w,h));int size=placeholder.getIntrinsicWidth();placeholder.setBounds((w-size)/2,(h-size)/2,(w+size)/2,(h+size)/2);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&!hit.contains((int)event.getX(),(int)event.getY()))return false;return super.dispatchTouchEvent(event);}
        @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);if(getChildCount()==0){paint.setColor(background);c.drawRect(0,0,getWidth(),getHeight(),paint);placeholder.draw(c);}super.draw(c);if(dark){paint.setColor(0x3003091a);c.drawRect(0,0,getWidth(),getHeight(),paint);}c.restoreToCount(save);}
    }
    /** A local static photograph composed for the diagonal and shallow bottom strip. */
    private static final class CornerArtwork extends View {
        private final Path mask=new Path();private final Region hit=new Region();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        CornerArtwork(Context c,boolean dark){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(dark)paint.setColorFilter(new ColorMatrixColorFilter(new float[]{.78f,0,0,0,0,0,.8f,0,0,0,0,0,.86f,0,0,0,0,0,1,0}));}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);float sx=w/722f,sy=h/538f;mask.rewind();mask.moveTo(0,0);mask.lineTo(385*sx,355*sy);mask.lineTo(w,355*sy);mask.lineTo(w,h);mask.lineTo(0,h);mask.close();hit.setPath(mask,new Region(0,0,w,h));}
        @Override protected void onDraw(Canvas c){int save=c.save();c.clipPath(mask);drawCrop(c,shore,new RectF(0,0,getWidth(),getHeight()),paint);c.restoreToCount(save);}
        // Consume only the visible decorative area, never a player button through an empty corner.
        @Override public boolean dispatchTouchEvent(MotionEvent event){return event.getActionMasked()!=MotionEvent.ACTION_DOWN||hit.contains((int)event.getX(),(int)event.getY());}
    }
    private static final class Circle extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Circle(int color){p.setColor(color);}public void draw(Canvas c){c.drawOval(new RectF(getBounds()),p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Transport extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String action;Transport(String action,int color){this.action=action;p.setColor(color);}
        public void draw(Canvas c){Rect b=getBounds();float cx=b.exactCenterX(),cy=b.exactCenterY(),s=Math.min(b.width(),b.height());if("stop".equals(action))c.drawRoundRect(new RectF(cx-s*.155f,cy-s*.155f,cx+s*.155f,cy+s*.155f),s*.015f,s*.015f,p);else if("pause".equals(action)){c.drawRect(cx-s*.16f,cy-s*.19f,cx-s*.04f,cy+s*.19f,p);c.drawRect(cx+s*.04f,cy-s*.19f,cx+s*.16f,cy+s*.19f,p);}else{Path path=new Path();path.moveTo(cx-s*.12f,cy-s*.19f);path.lineTo(cx+s*.2f,cy);path.lineTo(cx-s*.12f,cy+s*.19f);path.close();c.drawPath(path,p);}}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Drop extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int body,shine;Drop(int body,int shine){this.body=body;this.shine=shine;}public void draw(Canvas c){int s=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/30f,getBounds().height()/40f);Path d=new Path();d.moveTo(15,0);d.cubicTo(13,8,1,23,1,28);d.cubicTo(1,44,29,44,29,28);d.cubicTo(29,22,17,7,15,0);p.setColor(body);c.drawPath(d,p);p.setColor(shine);p.setStrokeWidth(1.5f);c.drawLine(8,21,5,29,p);c.restoreToCount(s);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
