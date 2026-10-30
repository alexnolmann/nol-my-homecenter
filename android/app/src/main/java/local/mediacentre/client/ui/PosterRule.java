package local.mediacentre.client.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Open panels use a baseline rather than a rounded enclosing border. No animation or shadow. */
final class PosterRule extends Drawable {
    private final Paint paint=new Paint();private final int fill,line,height;private final boolean top;private int alpha=255;
    PosterRule(int fill,int line,int height){this(fill,line,height,false);}
    PosterRule(int fill,int line,int height,boolean top){this.fill=fill;this.line=line;this.height=height;this.top=top;}
    public void draw(Canvas canvas){Rect b=getBounds();paint.setColor(fill);paint.setAlpha(alpha);canvas.drawRect(b,paint);paint.setColor(line);paint.setAlpha(alpha);canvas.drawRect(b.left,top?b.top:b.bottom-height,b.right,top?b.top+height:b.bottom,paint);}
    public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    public int getOpacity(){return alpha==255?PixelFormat.OPAQUE:PixelFormat.TRANSLUCENT;}
}
