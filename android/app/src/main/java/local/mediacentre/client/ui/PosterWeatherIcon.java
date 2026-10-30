package local.mediacentre.client.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Layered cloud volumes and fine precipitation; static vector drawing at the requested size. */
final class PosterWeatherIcon extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;private final int size;private final boolean blueField;
    private final Path cloud=new Path();
    private final Shader cloudShader=new LinearGradient(30,18,62,74,new int[]{0xffffffff,0xffe6eff7,0xff779ab9},null,Shader.TileMode.CLAMP);
    PosterWeatherIcon(String kind,int size,boolean blueField){this.kind=kind;this.size=size;this.blueField=blueField;
        cloud.moveTo(18,68);cloud.cubicTo(1,68,1,42,23,42);cloud.cubicTo(21,22,41,16,51,26);cloud.cubicTo(64,11,85,28,80,44);cloud.cubicTo(103,46,98,70,78,70);cloud.close();}
    public void draw(Canvas c){int saved=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/100f,getBounds().height()/100f);p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.ROUND);
        if(kind==null||"unknown".equals(kind)){p.setColor(blueField?Color.WHITE:0xff8293a4);p.setStrokeWidth(3);c.drawLine(34,48,66,48,p);c.restoreToCount(saved);return;}
        if("clear".equals(kind)||"partly_cloudy".equals(kind)){p.setColor(0xffffb900);c.drawCircle(44,35,18,p);p.setStrokeWidth(3);for(int n=0;n<8;n++){c.save();c.rotate(n*45,44,35);c.drawLine(44,5,44,12,p);c.restore();}}
        if(!"clear".equals(kind)){p.setColor(0x193b5a80);c.save();c.translate(1,3);c.drawPath(cloud,p);c.restore();p.setAlpha(255);p.setShader(cloudShader);c.drawPath(cloud,p);p.setShader(null);p.setColor(0x24ffffff);c.drawCircle(37,37,11,p);p.setColor(0x18ffffff);c.drawCircle(60,36,15,p);}
        p.setColor(blueField?0xfff5f8ff:0xff084ae5);p.setStrokeWidth(2);
        if("rain".equals(kind)||"freezing_rain".equals(kind)||"thunderstorm".equals(kind))for(int x=24;x<=78;x+=13)c.drawLine(x,77,x-5,91,p);
        if("drizzle".equals(kind))for(int x=25;x<=78;x+=14){c.drawCircle(x,79,1.8f,p);c.drawCircle(x-3,89,1.3f,p);}
        if("snow".equals(kind))for(int x=25;x<=78;x+=18){c.drawLine(x,77,x,90,p);c.drawLine(x-4,80,x+4,87,p);c.drawLine(x+4,80,x-4,87,p);}
        if("fog".equals(kind)){p.setColor(blueField?Color.WHITE:0xff8293a4);c.drawLine(15,79,85,79,p);c.drawLine(25,89,75,89,p);}
        if("thunderstorm".equals(kind)){p.setColor(0xffffb900);Path lightning=new Path();lightning.moveTo(51,61);lightning.lineTo(43,79);lightning.lineTo(51,79);lightning.lineTo(46,96);lightning.lineTo(66,72);lightning.lineTo(56,72);lightning.close();c.drawPath(lightning,p);}
        c.restoreToCount(saved);
    }
    public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
    public void setAlpha(int alpha){p.setAlpha(alpha);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
