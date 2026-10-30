package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.climate.*;

/** Shared light/dark Neo Poster climate, with real room samples and the existing shared audio controls. */
final class PosterClimateView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992;
    private final int ink,muted,blue,signal,background,rule,metricColor,warning,trackColor,secondCurve,thirdCurve;
    interface Manage {void open(String address);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final ArrayList<Room> rooms=new ArrayList<>();
    private final View miniRoot,statusRoot;
    private final int miniVisibility,statusVisibility;
    private final TimeZone zone;
    private View conflict,empty;
    private boolean hasConflict;
    private static Typeface numbers;

    private static final class Box {
        final View view;final float x,y,w,h,font;
        Box(View view,float x,float y,float w,float h,float font){this.view=view;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;}
    }
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;
        final ColorStateList colors;final Drawable background,thumb,track;final int offset;
        Borrowed(View v){
            view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();}
            else{face=null;font=scale=0;gravity=0;padding=false;colors=null;}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;thumb=b.getThumb();track=b.getProgressDrawable();offset=b.getThumbOffset();}else{thumb=track=null;offset=0;}
            parent.removeView(v);
        }
        void restore(){
            ((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);view.setBackground(background);
            if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);}
            if(view instanceof SeekBar){SeekBar b=(SeekBar)view;b.setProgressDrawable(track);b.setThumb(thumb);b.setThumbOffset(offset);}
            new PanelStyle(view.getContext()).repaint(view);
        }
    }

    PosterClimateView(Context c,TextClock clock,AudioOutputControls output,WeatherRefreshButton refresh,
            View miniRoot,TextView miniName,View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume,
            View statusRoot,TextView status){
        super(c);setWillNotDraw(false);setClipChildren(true);
        PanelStyle palette=new PanelStyle(c);boolean dark=palette.palette.dark;
        ink=dark?palette.color(PanelStyle.TEXT):0xff050d15;muted=dark?0xffb8c9df:0xff536274;
        background=palette.color(PanelStyle.BG);blue=dark?0xff16358a:0xff0634e8;
        signal=dark?palette.color(PanelStyle.SIGNAL):0xffff4b12;rule=dark?0xff43566e:0xff6b7785;
        metricColor=dark?0xff90b2d8:0xff18354a;warning=dark?palette.color(PanelStyle.WARNING):0xff9c3400;
        trackColor=dark?0xff3b4d66:0xffbfc6d0;secondCurve=dark?0xff9ab7ff:blue;thirdCurve=dark?0xff83c9d9:0xff1b5268;
        if(numbers==null)numbers=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");
        zone=TimeZone.getTimeZone(DisplaySettings.preferences(c).getString("timezone","Europe/Moscow"));
        this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);
        this.statusRoot=statusRoot;statusVisibility=statusRoot.getVisibility();statusRoot.setVisibility(GONE);
        place(new ClimateRoomArt(c,ClimateRoomArt.OFFICE,true),450,0,710,224,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        TextView title=value("Климат",ink);title.setTypeface(PosterTypography.face(c));title.setContentDescription("climate_title");place(title,24,28,460,133,0);
        place(label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),muted,false),26,179,420,31,23);
        PosterAudioHeader.bind(this::take,clock,output);
        take(refresh,1388,151,65,60,0);refresh.setTextColor(Color.WHITE);refresh.setBackground(outline(c));
        ImageView badge=new ImageView(c);badge.setImageBitmap(ClimateRoomArt.bitmap(c,ClimateRoomArt.OFFICE));badge.setScaleType(ImageView.ScaleType.CENTER_CROP);badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);place(badge,14,902,63,63,0);
        if(dark)badge.setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,-10);
        take(status,24,973,1423,18,12);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);status.setIncludeFontPadding(false);
    }

    void tools(Button add,View conflicts){
        add.setText("+  Добавить датчик");add.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0x85ffffff,Color.WHITE}));
        add.setBackground(outline(getContext()));place(add,1080,151,292,60,21);
        conflict=conflicts;place(conflicts,24,231,1423,90,0);
    }
    void conflicting(boolean value){if(conflict!=null)conflict.setVisibility(value?VISIBLE:GONE);if(hasConflict!=value){hasConflict=value;requestLayout();invalidate();}}
    void sensors(List<ClimateSensor> sensors,Manage manage){
        for(Room room:rooms)removeView(room);rooms.clear();
        if(empty!=null){removeView(empty);empty=null;}
        if(sensors.isEmpty()){
            LinearLayout body=new LinearLayout(getContext());body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER);
            TextView heading=label("Климат в вашем доме",ink,true);heading.setTextSize(32);heading.setGravity(Gravity.CENTER);body.addView(heading);
            TextView hint=label("Добавьте Xiaomi LYWSD03MMC, чтобы видеть температуру и влажность в комнате.",muted,false);hint.setSingleLine(false);hint.setTextSize(21);hint.setGravity(Gravity.CENTER);body.addView(hint);
            TextView more=label("Включите датчик рядом с планшетом. Mi Home для чтения показаний не нужен.",muted,false);more.setSingleLine(false);more.setTextSize(18);more.setGravity(Gravity.CENTER);body.addView(more);empty=body;addView(body);
        }else for(int i=0;i<sensors.size();i++){Room room=new Room(sensors.get(i),i,sensors.size(),manage);rooms.add(room);addView(room);}
        requestLayout();invalidate();
    }
    void state(ClimateController climate){for(Room room:rooms)room.state(climate);}
    void restore(){for(int i=borrowed.size()-1;i>=0;i--)borrowed.get(i).restore();borrowed.clear();miniRoot.setVisibility(miniVisibility);statusRoot.setVisibility(statusVisibility);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,int color,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setSingleLine();v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView value(String text,int color){TextView v=new NumberText(getContext());v.setText(text);v.setTypeface(numbers);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    private static Drawable outline(Context c){
        int dp=new PanelStyle(c).dp(1);StateListDrawable states=new StateListDrawable();
        for(int state:new int[]{android.R.attr.state_pressed,android.R.attr.state_focused,0}){GradientDrawable d=new GradientDrawable();d.setColor(state==0?Color.TRANSPARENT:0x25ffffff);d.setStroke(dp,0x90ffffff);d.setCornerRadius(new PanelStyle(c).dp(32));states.addState(state==0?new int[]{}:new int[]{state},d);}return states;
    }
    private void measureBoxes(List<Box> list,float sx,float sy){for(Box b:list){if(b.font>0)((TextView)b.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.view.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    private void layoutBoxes(List<Box> list,float sx,float sy){for(Box b:list){int x=Math.round(b.x*sx),y=Math.round(b.y*sy);b.view.layout(x,y,x+b.view.getMeasuredWidth(),y+b.view.getMeasuredHeight());}}
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);
        float sx=w/WIDTH,sy=h/HEIGHT;measureBoxes(boxes,sx,sy);
        float top=hasConflict?340:242,height=862-top;
        for(Room room:rooms)room.measure(MeasureSpec.makeMeasureSpec(Math.round((1423f/rooms.size()-32)*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(height*sy),MeasureSpec.EXACTLY));
        if(empty!=null)empty.measure(MeasureSpec.makeMeasureSpec(Math.round(1360*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(450*sy),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        float sx=getWidth()/WIDTH,sy=getHeight()/HEIGHT;layoutBoxes(boxes,sx,sy);float top=hasConflict?340:242;
        for(int i=0;i<rooms.size();i++){Room room=rooms.get(i);int x=Math.round((24+i*1423f/rooms.size())*sx),y=Math.round(top*sy);room.layout(x,y,x+room.getMeasuredWidth(),y+room.getMeasuredHeight());}
        if(empty!=null){int x=Math.round(50*sx),y=Math.round((top+70)*sy);empty.layout(x,y,x+empty.getMeasuredWidth(),y+empty.getMeasuredHeight());}
    }
    @Override protected void onDraw(Canvas c){
        int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(blue);c.drawRect(990,0,WIDTH,224,paint);
        paint.setColor(rule);paint.setStrokeWidth(1);c.drawLine(24,224,1447,224,paint);c.drawLine(14,890,1447,890,paint);
        for(int i=1;i<rooms.size();i++){float x=24+i*1423f/rooms.size()-16;c.drawLine(x,hasConflict?340:242,x,854,paint);}c.restoreToCount(save);
    }

    private final class Room extends ViewGroup {
        final ClimateSensor sensor;final float width;final ArrayList<Box> items=new ArrayList<>();final TextView ageLabel,age,state;
        final PosterClimateChart chart;
        Room(ClimateSensor sensor,int index,int count,Manage manage){
            super(PosterClimateView.this.getContext());this.sensor=sensor;width=1423f/count-32;setContentDescription("climate_sensor_"+sensor.address);
            float metric=count==1?650:width;
            TextView name=label(sensor.name,ink,true);name.setTypeface(PosterTypography.face(getContext()));name.setContentDescription("climate_room_name");put(name,0,0,width-60,52,36);
            Button options=new ClimateOptionsButton(getContext());new PanelStyle(getContext()).plain(options,false);options.setContentDescription("climate_manage_"+sensor.address);options.setOnClickListener(v->manage.open(sensor.address));put(options,width-58,0,56,48,0);
            String lower=sensor.name.toLowerCase(new Locale("ru"));int kind=lower.contains("спаль")?ClimateRoomArt.BEDROOM:lower.contains("кабин")||lower.contains("офис")?ClimateRoomArt.OFFICE:ClimateRoomArt.LIVING;
            put(new ClimateRoomArt(getContext(),kind,false),0,178,metric*.64f,183,0);
            TextView temperature=value(sensor.latest==null?"—°":String.format(new Locale("ru"),"%.1f°",sensor.latest.temperature),ink);temperature.setContentDescription("climate_temperature");put(temperature,metric*.08f,62,metric*.56f,131,0);
            put(new MetricIcon(getContext(),false,metricColor),metric*.67f,77,24,36,0);
            TextView humidity=value(sensor.latest==null?"—%":sensor.latest.humidity+"%",metricColor);humidity.setContentDescription("climate_humidity");put(humidity,metric*.74f,78,metric*.26f,57,0);
            put(label("Батарейка",muted,false),metric*.68f,182,metric*.32f,24,14);
            put(new MetricIcon(getContext(),true,metricColor),metric*.68f,212,24,24,0);
            TextView battery=label(sensor.latest==null?"— В":String.format(new Locale("ru"),"%.2f В",sensor.latest.batteryMillivolts/1000.0),sensor.latest!=null&&sensor.latest.batteryMillivolts<2400?warning:ink,true);put(battery,metric*.68f+29,208,metric*.32f-29,31,19);
            ageLabel=label(sensor.latest==null?"Нет показаний":"Обновлено",muted,false);put(ageLabel,metric*.68f,261,metric*.32f,24,14);
            age=label(sensor.latest==null?"—":"",muted,false);age.setSingleLine(false);put(age,metric*.68f,287,metric*.32f,58,17);
            state=label("",warning,false);state.setSingleLine(false);state.setContentDescription("climate_room_state");put(state,0,370,metric,38,14);
            float chartLeft=count==1?730:0,chartTop=count==1?90:416,chartWidth=count==1?width-730:width;
            TextView heading=label("За последние 24 часа",ink,true);put(heading,chartLeft,chartTop,chartWidth,28,17);
            chart=new PosterClimateChart(getContext(),sensor.history,index==0?signal:index==1?secondCurve:thirdCurve,zone);chart.setContentDescription("climate_temperature_history");put(chart,chartLeft,chartTop+33,chartWidth,137,0);
            TextView range=label(chart.humidityRange(),muted,false);put(range,chartLeft,chartTop+177,chartWidth,26,15);
            if(count==1){TextView note=label("Температура комнаты",muted,false);put(note,0,395,650,32,20);}
        }
        private void put(View v,float x,float y,float w,float h,float font){items.add(new Box(v,x,y,w,h,font));addView(v);}
        void state(ClimateController climate){
            ClimateSensor current=climate.store().find(sensor.address);if(current==null)return;
            boolean stale=current.latest==null||System.currentTimeMillis()<current.latest.measuredAt||System.currentTimeMillis()-current.latest.measuredAt>Math.max(15*60_000L,2*climate.store().pollMs());
            PanelStyle.update(ageLabel,current.latest==null?"Нет показаний":stale?"Последние данные":"Обновлено");
            SimpleDateFormat format=new SimpleDateFormat(stale?"dd.MM · HH:mm":"HH:mm",new Locale("ru"));format.setTimeZone(zone);
            PanelStyle.update(age,current.latest==null?"—":format.format(new Date(current.latest.measuredAt)));age.setTextColor(stale?warning:muted);
            String error=climate.error(sensor.address);PanelStyle.update(state,sensor.address.equals(climate.currentAddress())?"Обновление…":error==null?"":error);state.setVisibility(state.getText().length()==0?GONE:VISIBLE);
            chart.window(System.currentTimeMillis(),climate.store().pollMs());
        }
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);setMeasuredDimension(w,h);measureBoxes(items,w/width,h/620f);}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes(items,getWidth()/width,getHeight()/620f);}
    }
    private static final class NumberText extends TextView {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect bounds=new Rect();
        NumberText(Context c){super(c);}
        @Override protected void onDraw(Canvas c){String text=getText().toString();if(text.isEmpty())return;p.setTypeface(getTypeface());p.setTextSize(100);p.setColor(getCurrentTextColor());
            if(text.startsWith("—")){p.setTypeface(PosterTypography.face(getContext()));p.setTextSize(Math.min(getHeight()*.65f,getWidth()*.45f));Paint.FontMetrics f=p.getFontMetrics();c.drawText(text,0,getHeight()/2f-(f.ascent+f.descent)/2,p);return;}
            p.getTextBounds(text,0,text.length(),bounds);if(bounds.width()==0||bounds.height()==0)return;float scale=Math.min(getWidth()/(float)bounds.width(),getHeight()/(float)bounds.height());int save=c.save();c.translate(0,(getHeight()-bounds.height()*scale)/2);c.scale(scale,scale);c.drawText(text,-bounds.left,-bounds.top,p);c.restoreToCount(save);}
    }
    private static final class MetricIcon extends View {
        final boolean battery;final int color;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MetricIcon(Context c,boolean battery,int color){super(c);this.battery=battery;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);if(battery){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);c.drawRoundRect(new RectF(2,6,20,18),1,1,p);c.drawLine(22,10,22,14,p);p.setStyle(Paint.Style.FILL);c.drawRect(5,9,16,15,p);}else{Path d=new Path();d.moveTo(12,1);d.cubicTo(9,7,4,13,4,17);d.cubicTo(4,26,20,26,20,17);d.cubicTo(20,13,15,7,12,1);c.drawPath(d,p);}c.restoreToCount(save);}
    }
    private static final class Track extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final float radius;Track(Context c,int color){p.setColor(color);radius=new PanelStyle(c).dp(3);}
        public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Thumb extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size,radius;Thumb(Context c,int color){p.setColor(color);PanelStyle s=new PanelStyle(c);size=s.dp(10);radius=s.dp(3);}
        public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
