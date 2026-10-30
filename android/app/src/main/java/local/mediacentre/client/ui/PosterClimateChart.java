package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;
import local.mediacentre.client.climate.*;

/** Actual timestamps in a fixed 24-hour window; large gaps are never interpolated. */
final class PosterClimateChart extends View {
    private final List<ClimateReading> history;
    private final ArrayList<ClimateReading> recent=new ArrayList<>();
    private final ArrayList<Path> curves=new ArrayList<>(),fills=new ArrayList<>();
    private final ArrayList<PointF> points=new ArrayList<>();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color,muted,emptyColor,grid,verticalGrid,background,fillAlpha;private final TimeZone zone;
    private long now,bucket=-1,pollMs;private double low,high;private float lastX,lastY;private boolean point;
    private int humidityLow=100,humidityHigh=0;
    PosterClimateChart(Context c,List<ClimateReading> history,int color,TimeZone zone){
        super(c);this.history=history;this.color=color;this.zone=zone;PanelStyle style=new PanelStyle(c);boolean dark=style.palette.dark;
        muted=dark?0xffb8c9df:0xff536274;emptyColor=dark?muted:0xff657487;grid=dark?0xff43566e:0xffc1c8cf;verticalGrid=dark?0xff2d3c51:0xffd2d8de;
        background=style.color(PanelStyle.BG);fillAlpha=dark?0x26000000:0x16000000;
        window(System.currentTimeMillis(),15*60_000L);
    }
    void window(long value,long poll){if(value/ClimateController.HISTORY_BUCKET_MS==bucket&&poll==pollMs)return;now=value;pollMs=poll;bucket=value/ClimateController.HISTORY_BUCKET_MS;rebuild();invalidate();}
    String humidityRange(){return recent.isEmpty()?"Истории влажности пока нет":"Влажность "+humidityLow+(humidityHigh==humidityLow?"":"–"+humidityHigh)+"% за 24 часа";}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);rebuild();}
    private void rebuild(){
        recent.clear();curves.clear();fills.clear();points.clear();humidityLow=100;humidityHigh=0;point=false;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(ClimateReading r:history)if(r.measuredAt>=now-ClimateSensor.HISTORY_MS&&r.measuredAt<=now){recent.add(r);min=Math.min(min,r.temperature);max=Math.max(max,r.temperature);humidityLow=Math.min(humidityLow,r.humidity);humidityHigh=Math.max(humidityHigh,r.humidity);}
        if(recent.isEmpty()||getWidth()==0||getHeight()==0)return;
        low=Math.floor(min-.2);high=Math.ceil(max+.2);
        float sx=getWidth()/420f,sy=getHeight()/137f,left=37*sx,right=getWidth()-10*sx,top=9*sy,bottom=105*sy;
        Path curve=null,fill=null;float previousX=0;long previousTime=0;
        for(ClimateReading r:recent){float x=left+(float)((r.measuredAt-(now-ClimateSensor.HISTORY_MS))/(double)ClimateSensor.HISTORY_MS)*(right-left),y=bottom-(float)((r.temperature-low)/(high-low))*(bottom-top);
            if(curve==null||r.measuredAt-previousTime>Math.max(30*60_000L,pollMs*3)){if(fill!=null){fill.lineTo(previousX,bottom);fill.close();}curve=new Path();fill=new Path();curves.add(curve);fills.add(fill);curve.moveTo(x,y);fill.moveTo(x,bottom);fill.lineTo(x,y);}else{curve.lineTo(x,y);fill.lineTo(x,y);}
            points.add(new PointF(x,y));previousTime=r.measuredAt;previousX=x;lastX=x;lastY=y;point=true;
        }
        if(fill!=null){fill.lineTo(previousX,bottom);fill.close();}
    }
    @Override protected void onDraw(Canvas c){
        float sx=getWidth()/420f,sy=getHeight()/137f,scale=Math.min(sx,sy),left=37*sx,right=getWidth()-10*sx,top=9*sy,bottom=105*sy;
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(12*scale);p.setTextAlign(Paint.Align.RIGHT);p.setStyle(Paint.Style.FILL);p.setPathEffect(null);
        if(recent.isEmpty()){p.setColor(emptyColor);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(14*scale);c.drawText("История появится после измерений",getWidth()/2f,getHeight()/2f,p);return;}
        for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;p.setColor(grid);p.setStrokeWidth(Math.max(1,scale));c.drawLine(left,y,right,y,p);p.setColor(muted);double v=high-(high-low)*i/2;c.drawText(String.format(new Locale("ru"),Math.abs(v-Math.rint(v))<.01?"%.0f°":"%.1f°",v),left-7*sx,y+4*sy,p);}
        for(int i=0;i<3;i++){float x=left+(right-left)*i/2;p.setColor(verticalGrid);c.drawLine(x,top,x,bottom,p);}
        p.setColor((color&0xffffff)|fillAlpha);p.setStyle(Paint.Style.FILL);for(Path path:fills)c.drawPath(path,p);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*scale);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);for(Path path:curves)c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);for(PointF value:points)c.drawCircle(value.x,value.y,1.8f*scale,p);
        if(point){p.setStyle(Paint.Style.FILL);p.setColor(background);c.drawCircle(lastX,lastY,4*scale,p);p.setStyle(Paint.Style.STROKE);p.setColor(color);c.drawCircle(lastX,lastY,4*scale,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(muted);p.setTextSize(12*scale);SimpleDateFormat time=new SimpleDateFormat("HH:mm",new Locale("ru"));time.setTimeZone(zone);
        p.setTextAlign(Paint.Align.LEFT);c.drawText(time.format(new Date(now-ClimateSensor.HISTORY_MS)),left,132*sy,p);
        p.setTextAlign(Paint.Align.CENTER);c.drawText(time.format(new Date(now-ClimateSensor.HISTORY_MS/2)),(left+right)/2,132*sy,p);
        p.setTextAlign(Paint.Align.RIGHT);c.drawText("Сейчас",right,132*sy,p);
    }
}
