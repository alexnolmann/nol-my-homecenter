package local.mediacentre.client.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import local.mediacentre.client.R;
import local.mediacentre.client.data.Dtos;
import local.mediacentre.client.presentation.FinancialLabels;

/** Shared light/dark finance poster. Real values; shared controls retain their owners. */
final class PosterFinanceView extends PosterPageView {
    private static final float WIDTH=1469,HEIGHT=992,STRIP=1423;
    private final boolean dark;
    private final int ink,muted,blue,rule,up,down,background,signal,trackColor;
    private static final Map<Integer,Bitmap> photos=new HashMap<>();
    private static Typeface numbers;
    private final ArrayList<Box> boxes=new ArrayList<>();
    private final ArrayList<Borrowed> borrowed=new ArrayList<>();
    private final View miniRoot,statusRoot;
    private final int miniVisibility,statusVisibility;
    private final AudioOutputControls output;
    private final HorizontalScrollView shares;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final class Box {final View v;final float x,y,w,h,font;Box(View v,float x,float y,float w,float h,float f){this.v=v;this.x=x;this.y=y;this.w=w;this.h=h;font=f;}}
    private static final class Borrowed {
        final View view;final ViewGroup parent;final ViewGroup.LayoutParams params;final int index;
        final Typeface face;final float font,scale;final int gravity;final boolean padding;
        final ColorStateList colors;final Drawable background,thumb,track;final int offset;
        Borrowed(View v){view=v;parent=(ViewGroup)v.getParent();params=v.getLayoutParams();index=parent.indexOfChild(v);background=v.getBackground();
            if(v instanceof TextView){TextView t=(TextView)v;face=t.getTypeface();font=t.getTextSize();scale=t.getTextScaleX();gravity=t.getGravity();padding=t.getIncludeFontPadding();colors=t.getTextColors();}else{face=null;font=scale=0;gravity=0;padding=false;colors=null;}
            if(v instanceof SeekBar){SeekBar b=(SeekBar)v;thumb=b.getThumb();track=b.getProgressDrawable();offset=b.getThumbOffset();}else{thumb=track=null;offset=0;}
            parent.removeView(v);
        }
        void restore(){((ViewGroup)view.getParent()).removeView(view);parent.addView(view,index,params);view.setBackground(background);
            if(view instanceof TextView){TextView t=(TextView)view;t.setTypeface(face);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,font);t.setTextScaleX(scale);t.setGravity(gravity);t.setIncludeFontPadding(padding);t.setTextColor(colors);}
            if(view instanceof SeekBar){SeekBar b=(SeekBar)view;b.setProgressDrawable(track);b.setThumb(thumb);b.setThumbOffset(offset);}
            new PanelStyle(view.getContext()).repaint(view);
        }
    }

    PosterFinanceView(Context c,Dtos.Finance data,int scrollX,TextClock clock,AudioOutputControls output,WeatherRefreshButton refresh,
            View miniRoot,TextView miniName,View waveform,View toggle,VolumeButton mute,SeekBar slider,TextView volume,View statusRoot){
        super(c);setWillNotDraw(false);this.output=output;
        PanelStyle style=new PanelStyle(c);dark=style.palette.dark;
        ink=dark?style.color(PanelStyle.TEXT):0xff050d15;muted=dark?0xffb8c9df:0xff536274;
        blue=dark?0xff16358a:0xff0634e8;rule=dark?0xff43566e:0xff657180;
        up=dark?0xff6edda0:0xff078642;down=dark?0xffff9e8c:0xffff351e;
        background=style.color(PanelStyle.BG);signal=dark?style.color(PanelStyle.SIGNAL):0xffff4b12;trackColor=dark?0xff3b4d66:0xffbfc6d0;
        this.miniRoot=miniRoot;miniVisibility=miniRoot.getVisibility();miniRoot.setVisibility(GONE);
        this.statusRoot=statusRoot;statusVisibility=statusRoot.getVisibility();statusRoot.setVisibility(GONE);
        if(numbers==null)numbers=Typeface.createFromAsset(c.getAssets(),"fonts/Anton-Regular.ttf");
        place(new Photo(c,R.drawable.neo_finance_city,true),500,0,969,230,0);
        place(new PosterAudioHeader.Background(c,blue),0,0,1469,112,0);
        place(label(c.getSharedPreferences("connection",0).getString("display_title","Мой дом"),ink,false),24,16,620,32,24);
        TextView title=label("Финансы",ink,true);title.setTextScaleX(.82f);title.setContentDescription("finance_title");place(title,24,59,650,151,144);
        PosterAudioHeader.bind(this::take,clock,output);
        take(refresh,1380,155,66,64,0);refresh.setTextColor(ink);refresh.setBackgroundColor(Color.TRANSPARENT);
        List<Dtos.Instrument> currencies=new ArrayList<>(),stocks=new ArrayList<>();
        for(Dtos.Instrument item:data.items)("currency".equals(item.kind)?currencies:stocks).add(item);
        place(label("КУРСЫ ВАЛЮТ",ink,false),30,244,1000,26,17);
        if(currencies.isEmpty())place(label("Выберите валюты в веб-панели",muted,false),30,300,1300,70,30);
        HorizontalScrollView rates=strip(c,"finance_currencies_scroll");
        InstrumentStrip currencyStrip=new InstrumentStrip(c,currencies,true);rates.addView(currencyStrip);place(rates,24,277,1423,282,0);
        place(label("АКЦИИ · МОСБИРЖА",ink,false),30,596,1000,26,17);
        shares=strip(c,"finance_shares_scroll");shares.setTag("finance_shares_scroll");
        shares.addView(new InstrumentStrip(c,stocks,false));place(shares,24,628,1423,208,0);
        if(stocks.isEmpty())place(label("Выберите акции в веб-панели",muted,false),30,669,1300,70,30);
        shares.post(()->shares.scrollTo(scrollX,0));
        ImageView badge=new ImageView(c);badge.setImageBitmap(photo(c,R.drawable.neo_home_music));badge.setScaleType(ImageView.ScaleType.CENTER_CROP);badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(dark)badge.setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);place(badge,14,912,63,63,0);
        PosterMiniPlayer.bind(c,this::take,miniName,waveform,(Button)toggle,mute,slider,volume,0);
    }
    int sharesOffset(){return shares.getScrollX();}
    void restore(){for(int i=borrowed.size()-1;i>=0;i--)borrowed.get(i).restore();borrowed.clear();miniRoot.setVisibility(miniVisibility);statusRoot.setVisibility(statusVisibility);output.contrast(false);}
    private HorizontalScrollView strip(Context c,String description){HorizontalScrollView v=new HorizontalScrollView(c);v.setHorizontalScrollBarEnabled(false);v.setSmoothScrollingEnabled(true);v.setContentDescription(description);return v;}
    private void place(View v,float x,float y,float w,float h,float font){boxes.add(new Box(v,x,y,w,h,font));addView(v);}
    private void take(View v,float x,float y,float w,float h,float font){borrowed.add(new Borrowed(v));place(v,x,y,w,h,font);}
    private TextView label(String text,int color,boolean display){TextView v=new TextView(getContext());v.setText(text);v.setTextColor(color);v.setTypeface(display?PosterTypography.face(getContext()):Typeface.create("sans-serif",Typeface.BOLD));v.setSingleLine();v.setEllipsize(TextUtils.TruncateAt.END);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView number(String text){TextView v=new Number(getContext());v.setText(text);v.setTextColor(ink);v.setIncludeFontPadding(false);return v;}
    private TextView delta(Dtos.Instrument item){boolean comparable=FinancialLabels.comparable(item);int color=!comparable?muted:item.change.signum()<0?down:item.change.signum()>0?up:muted;TextView v=label(comparable?(item.change.signum()>0?"+":item.change.signum()<0?"−":"")+price(item.change.abs())+"%":"—",color,true);
        if(comparable&&item.change.signum()!=0){v.setCompoundDrawablesWithIntrinsicBounds(new PosterDelta(item.change.signum()<0,color,new PanelStyle(getContext()).dp(22)),null,null,null);v.setCompoundDrawablePadding(new PanelStyle(getContext()).dp(9));}v.setContentDescription("finance_change_"+item.symbol);return v;}
    private static String price(BigDecimal value){if(value==null)return "—";DecimalFormatSymbols symbols=new DecimalFormatSymbols(Locale.ROOT);symbols.setDecimalSeparator(',');symbols.setGroupingSeparator(' ');DecimalFormat f=new DecimalFormat("#,##0.00",symbols);f.setRoundingMode(RoundingMode.HALF_UP);return f.format(value);}
    private void measureBoxes(List<Box> list,float sx,float sy){for(Box b:list){if(b.font>0)((TextView)b.v).setTextSize(TypedValue.COMPLEX_UNIT_PX,b.font*Math.min(sx,sy));b.v.measure(MeasureSpec.makeMeasureSpec(Math.round(b.w*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(b.h*sy),MeasureSpec.EXACTLY));}}
    private void layoutBoxes(List<Box> list,float sx,float sy){for(Box b:list){int x=Math.round(b.x*sx),y=Math.round(b.y*sy);b.v.layout(x,y,x+b.v.getMeasuredWidth(),y+b.v.getMeasuredHeight());}}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=pageHeight(ws,hs);setMeasuredDimension(w,h);measureBoxes(boxes,w/WIDTH,h/HEIGHT);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes(boxes,getWidth()/WIDTH,getHeight()/HEIGHT);}
    @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/WIDTH,getHeight()/HEIGHT);paint.setColor(background);c.drawRect(0,0,WIDTH,HEIGHT,paint);paint.setColor(rule);paint.setStrokeWidth(1);c.drawLine(30,578,1447,578,paint);c.drawLine(14,900,1447,900,paint);c.restoreToCount(save);}

    private final class InstrumentStrip extends ViewGroup {
        final ArrayList<Box> cells=new ArrayList<>();final int count;final boolean currency;float sx,sy;
        InstrumentStrip(Context c,List<Dtos.Instrument> items,boolean currency){super(c);setWillNotDraw(false);this.currency=currency;count=items.size();float width=STRIP/3;
            for(int i=0;i<count;i++){Dtos.Instrument item=items.get(i);float left=i*width;
                if(currency){
                    TextView symbol=label(item.symbol+" / RUB",ink,true);symbol.setTypeface(numbers);add(symbol,left+7,0,width-43,59,51);
                    TextView current=number(price(item.enabled?item.price:null));current.setContentDescription("finance_price_"+item.symbol);add(current,left+7,73,width-61,150,0);
                    TextView previous=label("Было "+price(FinancialLabels.comparable(item)?item.previousPrice:null),muted,false);previous.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));previous.setContentDescription("finance_previous_"+item.symbol);add(previous,left+7,235,205,43,26);
                    TextView change=delta(item);change.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);add(change,left+222,231,width-252,49,34);
                }else{
                    int resource=art(item.symbol);add(new Photo(c,resource,false),left+7,0,170,198,0);
                    TextView ticker=label(item.symbol,ink,true);ticker.setTypeface(numbers);ticker.setContentDescription("finance_share_"+item.symbol);add(ticker,left+190,0,width-211,58,49);
                    TextView current=number(price(item.enabled?item.price:null)+" ₽");current.setContentDescription("finance_price_"+item.symbol);add(current,left+190,70,width-210,62,0);
                    add(delta(item),left+190,145,width-210,49,34);
                }
            }
        }
        private int art(String symbol){if(symbol.startsWith("SBER")||symbol.startsWith("VTBR")||symbol.startsWith("TCS")||symbol.startsWith("YDEX"))return R.drawable.neo_finance_bank;if(symbol.startsWith("GAZP")||symbol.startsWith("NVTK"))return R.drawable.neo_finance_gas;if(symbol.startsWith("TAT")||symbol.startsWith("LKOH")||symbol.startsWith("ROSN")||symbol.startsWith("SNGS"))return R.drawable.neo_finance_oil;return R.drawable.neo_finance_city;}
        private void add(View v,float x,float y,float w,float h,float font){cells.add(new Box(v,x,y,w,h,font));addView(v);}
        @Override protected void onMeasure(int ws,int hs){sx=PosterFinanceView.this.getMeasuredWidth()/WIDTH;sy=PosterFinanceView.this.getMeasuredHeight()/HEIGHT;setMeasuredDimension(Math.round(STRIP/3*count*sx),Math.round((currency?282:208)*sy));measureBoxes(cells,sx,sy);}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){layoutBoxes(cells,sx,sy);}
        @Override protected void onDraw(Canvas c){paint.setColor(rule);paint.setStrokeWidth(Math.max(1,sx));for(int i=1;i<count;i++){float x=(STRIP/3*i-9)*sx;c.drawLine(x,0,x,(currency?277:198)*sy,paint);}}
    }
    private static Bitmap photo(Context c,int resource){Bitmap result=photos.get(resource);if(result==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;o.inScaled=false;o.inPreferredConfig=Bitmap.Config.RGB_565;result=BitmapFactory.decodeResource(c.getResources(),resource,o);photos.put(resource,result);}return result;}
    private final class Photo extends ImageView {
        private final Path mask=new Path();private final boolean banner;
        Photo(Context c,int resource,boolean banner){super(c);this.banner=banner;setImageBitmap(photo(c,resource));setScaleType(ScaleType.CENTER_CROP);if(dark)setColorFilter(0x33050d1c,PorterDuff.Mode.SRC_ATOP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);mask.rewind();if(banner){mask.moveTo(0,0);mask.lineTo(w,0);mask.lineTo(w,h*.30f);mask.lineTo(w*.75f,h);mask.lineTo(w*.16f,h);}else{mask.moveTo(0,0);mask.lineTo(w,0);mask.lineTo(w*.70f,h*.50f);mask.lineTo(w,h);mask.lineTo(0,h);}mask.close();}
        @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);super.draw(c);c.restoreToCount(save);}
    }
    /** Uses the real text bounds and uniform scaling: no cropping or distorted ruble sign. */
    private static final class Number extends TextView {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Rect bounds=new Rect();
        Number(Context c){super(c);}
        @Override protected void onDraw(Canvas c){String value=getText().toString();p.setTypeface(numbers);p.setTextSize(100);p.setColor(getCurrentTextColor());p.getTextBounds(value,0,value.length(),bounds);if(bounds.width()<=0)return;float scale=Math.min(getWidth()/(float)bounds.width(),getHeight()/(float)Math.max(1,bounds.height()));if(value.startsWith("—"))scale=Math.min(scale,getHeight()/100f);int save=c.save();c.translate(0,(getHeight()-bounds.height()*scale)/2);c.scale(scale,scale);c.drawText(value,-bounds.left,-bounds.top,p);c.restoreToCount(save);}
    }
    private static final class Track extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int radius;Track(Context c,int color){radius=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,p);}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Thumb extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int size,radius;Thumb(Context c,int color){size=new PanelStyle(c).dp(10);radius=new PanelStyle(c).dp(3);p.setColor(color);}public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,p);}public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
