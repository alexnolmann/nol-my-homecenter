package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.DisplayFormat;
import local.mediacentre.client.presentation.Presenters;

/** Shared light/dark weather: the approved 1586 × 992 composition minus its 117 px rail.
 * Shared controls retain their listeners and lifecycle; restore() returns them to their owners. */
final class PosterWeatherView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992,FORECAST=1423;
    private final int ink,blue,night,background,rule,dayRule,droplet,trackColor,signal;
    private final boolean dark;
    private static Typeface numbers;
    private static Bitmap photograph;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final View miniRoot;
    private final int miniVisibility;
    private final DayStrip strip;
    private final HorizontalScrollView forecast;
    private final boolean apparentAvailable;

    private static final class Box {
        final View view;final float x,y,w,h,font;
        Box(View v,float x,float y,float w,float h,float font){view=v;this.x=x;this.y=y;this.w=w;this.h=h;this.font=font;}
    }
    /** Saved in removal order and restored in reverse order, preserving sibling indices. */
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;
        final Typeface face;final float font,scale;final int gravity;final boolean fontPadding;
        final ColorStateList colors;final Drawable background;
        final Drawable thumb,track;final int thumbOffset;
        Borrowed(View v){view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();fontPadding=t.getIncludeFontPadding();colors=t.getTextColors();}
            else{face=null;font=scale=0;gravity=0;fontPadding=false;colors=null;}
            if(v instanceof SeekBar){SeekBar bar=(SeekBar)v;thumb=bar.getThumb();track=bar.getProgressDrawable();thumbOffset=bar.getThumbOffset();}else{thumb=track=null;thumbOffset=0;}
            parent.removeView(v);
        }
        void restore(){((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);view.setBackground(background);
            if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(fontPadding);t.setTextColor(colors);}
            if(view instanceof SeekBar){SeekBar bar=(SeekBar)view;bar.setProgressDrawable(track);bar.setThumb(thumb);bar.setThumbOffset(thumbOffset);}
            new PanelStyle(view.getContext()).repaint(view);
        }
    }

    PosterWeatherView(Context c,Dtos.Weather data,TextClock clock,AudioOutputControls output,
            WeatherRefreshButton refresh,View miniRoot,TextView miniName,
            View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume){
        super(c);setWillNotDraw(false);setClipChildren(true);
        PanelStyle palette=new PanelStyle(c);dark=palette.palette.dark;
        ink=dark?palette.color(PanelStyle.TEXT):0xff050d15;
        background=palette.color(PanelStyle.BG);blue=dark?0xff16358a:0xff0634e8;
        night=dark?0xffb8c9df:0xff15334e;rule=dark?0xff43566e:0xff27323c;
        dayRule=dark?0xff40536c:0xff334453;droplet=dark?0xff90b2d8:0xff496c87;
        trackColor=dark?0xff3b4d66:0xffbfc6d0;signal=dark?palette.color(PanelStyle.SIGNAL):0xffff4b12;
        if(numbers==null)numbers=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");
        if(photograph==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inSampleSize=2;o.inPreferredConfig=Bitmap.Config.RGB_565;photograph=BitmapFactory.decodeResource(getResources(),R.drawable.neo_weather_neva,o);}
        this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);
        apparentAvailable=data.apparent!=null;

        place(new CityPhoto(c,dark),613,0,517,566,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        TextView title=label("Погода · "+data.city,ink,true);title.setContentDescription("weather_title");place(title,24,14,575,59,46);
        title.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{Paint measure=new Paint(title.getPaint());measure.setTextScaleX(1);float textWidth=measure.measureText(title.getText().toString()),scale=Math.min(1,(r-l)/textWidth);if(Math.abs(title.getTextScaleX()-scale)>.001f)title.setTextScaleX(scale);});
        TextView current=ink(temperature(data.temperature),ink);current.setContentDescription("weather_current");place(current,24,96,336,297,0);
        place(new HomeWeatherView(c,data.condition,dark),315,97,342,312,0);
        TextView condition=label(Presenters.condition(data.condition),ink,true);condition.setContentDescription("weather_condition");place(condition,27,394,580,61,58);

        String[] labels={"Ощущается","Ветер","Влажность"};
        String[] values={temperature(data.apparent),whole(data.wind)+" м/с",whole(data.humidity)+"%"};
        float[] x={27,270,475},w={197,166,140};
        for(int i=0;i<3;i++){
            if(i==0&&!apparentAvailable)continue;
            float left=apparentAvailable?x[i]:i==1?27:270;
            place(label(labels[i],ink,true),left,468,w[i],30,22);
            TextView v=label(values[i],ink,true);v.setTypeface(PosterTypography.face(c));v.setContentDescription("weather_metric_"+i);place(v,left,498,w[i],52,53);
        }

        List<Dtos.WeatherPeriod> periods=WeatherScreen.upcoming(data);
        for(int i=0;i<periods.size();i++){
            Dtos.WeatherPeriod p=periods.get(i);float left=1145+(i%2)*162,top=143+(i/2)*225;
            TextView name=label(WeatherScreen.periodLabel(p.at,data.timezone).replace(" · завтра","").replace("Ночью","Ночь").replace("Утром","Утро").replace("Днём","День").replace("Вечером","Вечер"),Color.WHITE,true);
            name.setContentDescription("weather_period_"+p.at);place(name,left,top,137,33,23);
            place(new HomeWeatherView(c,p.condition),left+9,top+34,88,75,0);
            place(ink(temperature(p.temperature),Color.WHITE),left,top+117,115,57,0);
        }

        PosterAudioHeader.bind(this::take,clock,output);
        take(refresh,1392,84,68,64,0);refresh.setTextColor(Color.WHITE);refresh.setBackgroundColor(Color.TRANSPARENT);

        forecast=new HorizontalScrollView(c);forecast.setContentDescription("weather_week");forecast.setHorizontalScrollBarEnabled(false);forecast.setSmoothScrollingEnabled(true);forecast.setFillViewport(false);
        strip=new DayStrip(c,data.days);forecast.addView(strip,new HorizontalScrollView.LayoutParams(-2,-1));place(forecast,24,586,FORECAST,272,0);
        if(data.days.isEmpty())place(label("Прогноз пока не получен. Обновите данные.",night,false),24,640,FORECAST,70,25);

        ImageView badge=new ImageView(c);badge.setImageBitmap(photograph);badge.setScaleType(ImageView.ScaleType.CENTER_CROP);if(dark)badge.setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);place(badge,14,912,63,63,0);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,0);
    }

    void restore(){for(int i=borrowed.size()-1;i>=0;i--)borrowed.get(i).restore();borrowed.clear();miniRoot.setVisibility(miniVisibility);}
    int forecastOffset(){return forecast.getScrollX();}
    void forecastOffset(int x){forecast.post(()->forecast.scrollTo(x,0));}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private TextView label(String text,int color,boolean bold){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(color);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setSingleLine();v.setIncludeFontPadding(false);v.setEllipsize(TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView ink(String text,int color){TextView v=new InkText(getContext());v.setText(text);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    private static String whole(BigDecimal v){return v==null?"—":v.setScale(0,RoundingMode.HALF_UP).toPlainString();}
    private static String temperature(BigDecimal v){if(v==null)return "—°";String value=whole(v);return (new BigDecimal(value).signum()>0?"+":"")+value+"°";}
    private void measureBoxes(List<Box> list,float sx,float sy){for(Box b:list){if(b.font>0)((TextView)b.view).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.view.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    private void layoutBoxes(List<Box> list,float sx,float sy){for(Box b:list){int x=Math.round(b.x*sx),y=Math.round(b.y*sy);b.view.layout(x,y,x+b.view.getMeasuredWidth(),y+b.view.getMeasuredHeight());}}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);strip.sx=w/WIDTH;strip.sy=h/HEIGHT;measureBoxes(boxes,w/WIDTH,h/HEIGHT);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes(boxes,getWidth()/WIDTH,getHeight()/HEIGHT);}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(blue);c.drawRect(956,84,WIDTH,566,paint);
        paint.setColor(rule);paint.setStrokeWidth(1);c.drawLine(24,566,WIDTH,566,paint);c.drawLine(14,900,1447,900,paint);c.drawLine(243,466,243,550,paint);if(apparentAvailable)c.drawLine(447,466,447,550,paint);
        paint.setColor(0xbfffffff);c.drawLine(1279,146,1279,315,paint);c.drawLine(1279,368,1279,537,paint);c.drawLine(1146,342,1438,342,paint);c.restoreToCount(save);
    }
    private final class DayStrip extends ViewGroup {
        final List<Box> days=new ArrayList<>();final int count;float sx=1,sy=1;
        DayStrip(Context c,List<Dtos.Day> values){super(c);setWillNotDraw(false);count=values.size();float width=FORECAST/7;
            for(int i=0;i<count;i++){
                Dtos.Day day=values.get(i);float x=i*width;String date=DisplayFormat.day(day.date,"EEE, d MMM").replace(".","");date=date.substring(0,1).toUpperCase(new Locale("ru"))+date.substring(1);
                TextView name=label(date,ink,true);name.setTypeface(PosterTypography.face(c));name.setGravity(Gravity.CENTER);name.setContentDescription("weather_day_"+day.date);add(name,x+5,0,width-10,29,24);
                add(new HomeWeatherView(c,day.condition,dark),x+(width-98)/2,36,98,78,0);
                TextView value=ink(temperature(day.maximum),ink);value.setContentDescription("weather_day_temp_"+day.date);float degreeWidth=temperature(day.maximum).length()>3?118:101;add(value,x+(width-degreeWidth)/2,122,degreeWidth,67,0);
                TextView nightText=label("Ночью "+temperature(day.minimum),night,false);nightText.setGravity(Gravity.CENTER);add(nightText,x+3,201,width-6,37,23);
                TextView rain=label(DisplayFormat.weather(day.rain)+" мм",night,false);rain.setGravity(Gravity.CENTER);rain.setContentDescription("weather_rain_"+day.date);rain.setCompoundDrawablesWithIntrinsicBounds(new Droplet(17,droplet),null,null,null);rain.setCompoundDrawablePadding(new PanelStyle(c).dp(7));add(rain,x+width*.19f,241,width*.65f,31,24);
            }
        }
        private void add(View v,float x,float y,float w,float h,float font){days.add(new Box(v,x,y,w,h,font));addView(v);}
        @Override protected void onMeasure(int ws,int hs){setMeasuredDimension(Math.round(FORECAST/7*count*sx),Math.round(272*sy));measureBoxes(days,sx,sy);}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes(days,sx,sy);}
        @Override protected void onDraw(Canvas c){paint.setColor(dayRule);paint.setStrokeWidth(Math.max(1,sx));for(int i=1;i<count;i++){float x=FORECAST/7*i*sx;c.drawLine(x,0,x,268*sy,paint);}}
    }
    private static final class InkText extends TextView {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect bounds=new Rect();
        InkText(Context c){super(c);}
        @Override protected void onDraw(Canvas c){String text=getText().toString();if(text.isEmpty())return;p.setTypeface(numbers);p.setTextSize(100);p.setColor(getCurrentTextColor());p.getTextBounds(text,0,text.length(),bounds);if(bounds.width()==0||bounds.height()==0)return;int save=c.save();float sy=getHeight()/(float)bounds.height();if(text.startsWith("—"))sy=Math.min(sy,getHeight()/80f);c.scale(getWidth()/(float)bounds.width(),sy);c.drawText(text,-bounds.left,-bounds.top,p);c.restoreToCount(save);}
    }
    private static final class CityPhoto extends ImageView {
        final Path mask=new Path();CityPhoto(Context c,boolean dark){super(c);setImageBitmap(photograph);setScaleType(ScaleType.CENTER_CROP);if(dark)setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);mask.rewind();mask.moveTo(0,0);mask.lineTo(341,0);mask.lineTo(517,340);mask.lineTo(380,566);mask.cubicTo(220,568,88,506,61,357);mask.close();Matrix m=new Matrix();m.setScale(w/517f,h/566f);mask.transform(m);}
        @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);super.draw(c);c.restoreToCount(save);}
    }
    private final class Droplet extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size;Droplet(int s,int color){size=new PanelStyle(getContext()).dp(s);p.setColor(color);}
        public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/20f,getBounds().height()/27f);Path d=new Path();d.moveTo(10,0);d.cubicTo(8,7,1,15,1,19);d.cubicTo(1,30,19,30,19,19);d.cubicTo(19,15,12,7,10,0);c.drawPath(d,p);c.restoreToCount(save);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return Math.round(size*1.35f);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private final class Track extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF rect=new RectF();final float radius=new PanelStyle(getContext()).dp(3);Track(int color){p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();rect.set(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius);c.drawRoundRect(rect,radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private final class Thumb extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size=new PanelStyle(getContext()).dp(10),radius=new PanelStyle(getContext()).dp(3);Thumb(){p.setColor(signal);}public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
