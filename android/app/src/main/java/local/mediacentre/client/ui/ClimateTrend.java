package local.mediacentre.client.ui;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;
import local.mediacentre.client.climate.ClimateReading;

/** Two small independent, normalized trends: solid temperature, dashed humidity. */
final class ClimateTrend extends View {
    private final List<ClimateReading> samples;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int temperatureColor,humidityColor,lineColor;
    ClimateTrend(Context context,List<ClimateReading> samples){super(context);this.samples=samples;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void colors(int temperature,int humidity,int line){temperatureColor=temperature;humidityColor=humidity;lineColor=line;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float width=getWidth(),height=getHeight();paint.setStrokeWidth(1);paint.setColor(lineColor);paint.setStyle(Paint.Style.STROKE);paint.setPathEffect(null);
        canvas.drawLine(0,height/2,width,height/2,paint);
        if(samples.size()<2)return;
        drawTrend(canvas,false,temperatureColor,width,height);drawTrend(canvas,true,humidityColor,width,height);
    }
    private void drawTrend(Canvas canvas,boolean humidity,int color,float width,float height){
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(ClimateReading sample:samples){double value=humidity?sample.humidity:sample.temperature;min=Math.min(min,value);max=Math.max(max,value);}
        double range=Math.max(humidity?5:1,max-min),center=(min+max)/2;min=center-range/2;
        long first=samples.get(0).measuredAt,last=samples.get(samples.size()-1).measuredAt;
        Path path=new Path();boolean started=false;
        for(ClimateReading sample:samples){
            float x=(float)((sample.measuredAt-first)/(double)Math.max(1,last-first)*(width-4)+2);
            float y=(float)(height-12-((humidity?sample.humidity:sample.temperature)-min)/range*(height-24));
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
        }
        paint.setColor(color);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);paint.setPathEffect(humidity?new DashPathEffect(new float[]{5,5},0):null);canvas.drawPath(path,paint);paint.setPathEffect(null);
    }
}
