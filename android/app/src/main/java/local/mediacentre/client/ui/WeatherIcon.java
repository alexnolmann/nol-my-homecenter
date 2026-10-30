package local.mediacentre.client.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Weather symbols use only the known condition; unknown data stays unknown. */
final class WeatherIcon extends Drawable {
    private final String kind;private final boolean dark;private final int size;private final Paint p=new Paint(3);
    WeatherIcon(String kind,boolean dark,int size){this.kind=kind;this.dark=dark;this.size=size;}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/100f,getBounds().height()/100f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(4);p.setStyle(Paint.Style.FILL);
        if("clear".equals(kind)||"partly_cloudy".equals(kind)){p.setColor(dark?0xfff5c772:0xffb57510);c.drawCircle(42,38,17,p);p.setStyle(Paint.Style.STROKE);for(int n=0;n<8;n++){c.save();c.rotate(n*45,42,38);c.drawLine(42,10,42,15,p);c.restore();}p.setStyle(Paint.Style.FILL);}
        if(!"clear".equals(kind)&&!"unknown".equals(kind)){p.setColor(dark?0xffa9bfd9:0xff6c8bac);Path cloud=new Path();cloud.moveTo(24,67);cloud.cubicTo(4,67,4,39,27,39);cloud.cubicTo(33,12,70,16,73,44);cloud.cubicTo(96,43,97,67,76,67);cloud.close();c.drawPath(cloud,p);}
        if("rain".equals(kind)||"freezing_rain".equals(kind)||"thunderstorm".equals(kind)){p.setColor(dark?0xff93beff:0xff225ebc);for(int x:new int[]{30,49,68})c.drawLine(x,77,x-5,87,p);}
        if("drizzle".equals(kind)){p.setColor(dark?0xffafd4ff:0xff4b89ca);for(int x:new int[]{28,46,64}){c.drawCircle(x,79,2.8f,p);c.drawCircle(x+8,88,2.8f,p);}}
        if("snow".equals(kind)){p.setColor(dark?0xffdce8f9:0xff225ebc);for(int x:new int[]{28,49,70}){c.drawLine(x,76,x,90,p);c.drawLine(x-5,79,x+5,87,p);c.drawLine(x-5,87,x+5,79,p);}}
        if("fog".equals(kind)){p.setColor(dark?0xffb0c1d7:0xff4d627d);c.drawLine(20,78,80,78,p);c.drawLine(30,89,70,89,p);}
        if("unknown".equals(kind)||kind==null){p.setColor(dark?0xffb0c1d7:0xff4d627d);c.drawLine(34,48,66,48,p);}c.restore();}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
