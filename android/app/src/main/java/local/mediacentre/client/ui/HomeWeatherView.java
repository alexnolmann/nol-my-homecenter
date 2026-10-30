package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import local.mediacentre.client.R;

/** Static poster-home artwork: the precipitation always follows the actual condition code. */
final class HomeWeatherView extends View {
    private static Bitmap cloud;
    private final String condition;
    private final int precipitation;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF image=new RectF(0,7,100,74);
    HomeWeatherView(Context context,String condition){this(context,condition,true);}
    HomeWeatherView(Context context,String condition,boolean blueField){super(context);this.condition=condition;precipitation=blueField?Color.WHITE:0xff1264ed;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(cloud==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;o.inScaled=false;cloud=BitmapFactory.decodeResource(getResources(),R.drawable.neo_home_cloud,o);}}
    @Override protected void onDraw(Canvas c){int saved=c.save();c.scale(getWidth()/100f,getHeight()/100f);paint.setStrokeCap(Paint.Cap.ROUND);
        if(condition==null||"unknown".equals(condition)){paint.setColor(precipitation);paint.setStrokeWidth(2);c.drawLine(36,50,64,50,paint);c.restoreToCount(saved);return;}
        if("clear".equals(condition)||"partly_cloudy".equals(condition)){paint.setColor(0xffffcc23);c.drawCircle(40,34,20,paint);paint.setStrokeWidth(2);for(int n=0;n<8;n++){int s=c.save();c.rotate(n*45,40,34);c.drawLine(40,4,40,10,paint);c.restoreToCount(s);}}
        if(!"clear".equals(condition)){paint.setColor(Color.WHITE);c.drawBitmap(cloud,null,image,paint);}
        paint.setColor(precipitation);paint.setStrokeWidth(.9f);
        if("rain".equals(condition)||"freezing_rain".equals(condition)||"thunderstorm".equals(condition))for(int x=25;x<90;x+=13){c.drawLine(x,68,x-8,85,paint);c.drawLine(x-9,86,x-14,97,paint);}
        if("drizzle".equals(condition))for(int x=23;x<90;x+=16){c.drawCircle(x,76,1.1f,paint);c.drawCircle(x-4,87,.8f,paint);}
        if("snow".equals(condition))for(int x=22;x<90;x+=19){c.drawLine(x,72,x,87,paint);c.drawLine(x-4,76,x+4,83,paint);c.drawLine(x+4,76,x-4,83,paint);}
        if("fog".equals(condition)){c.drawLine(14,79,86,79,paint);c.drawLine(24,88,76,88,paint);}
        if("thunderstorm".equals(condition)){paint.setColor(0xffffcc23);Path bolt=new Path();bolt.moveTo(49,58);bolt.lineTo(40,80);bolt.lineTo(50,80);bolt.lineTo(43,99);bolt.lineTo(66,73);bolt.lineTo(56,73);bolt.close();c.drawPath(bolt,paint);}
        c.restoreToCount(saved);
    }
}
