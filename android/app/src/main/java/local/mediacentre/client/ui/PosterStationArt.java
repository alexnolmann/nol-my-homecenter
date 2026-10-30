package local.mediacentre.client.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A local, generic station monogram. It does not pretend to be a provider's official logo. */
final class PosterStationArt extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public void draw(Canvas c){Rect b=getBounds();int save=c.save();c.translate(b.left,b.top);c.scale(b.width()/100f,b.height()/100f);p.setColor(0xff061521);c.drawRect(0,0,100,100,p);p.setShader(new LinearGradient(15,40,80,100,0xffffad42,0xffff4b0b,Shader.TileMode.CLAMP));c.drawCircle(40,84,34,p);p.setShader(null);p.setColor(0xff061521);for(int y=76;y<100;y+=5)c.drawRect(0,y,100,y+2,p);c.restoreToCount(save);}
    public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.OPAQUE;}
}
