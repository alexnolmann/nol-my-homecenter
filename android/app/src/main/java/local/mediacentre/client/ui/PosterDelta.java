package local.mediacentre.client.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** A value-direction marker independent of fallback-font ascent/descender metrics on KitKat. */
final class PosterDelta extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path shape=new Path();private final int size;private final boolean down;
    PosterDelta(boolean down,int color,int size){this.down=down;this.size=size;paint.setColor(color);}
    public void draw(Canvas c){Rect b=getBounds();shape.rewind();shape.moveTo(b.left,down?b.top:b.bottom);shape.lineTo(b.right,down?b.top:b.bottom);shape.lineTo(b.exactCenterX(),down?b.bottom:b.top);shape.close();c.drawPath(shape,paint);}
    public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
    public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
