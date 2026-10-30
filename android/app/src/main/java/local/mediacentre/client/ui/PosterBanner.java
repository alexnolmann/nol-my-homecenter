package local.mediacentre.client.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** One cached photograph across the header, cut into the cobalt field. No layers or animation. */
final class PosterBanner extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path mask = new Path();
    private final RectF destination = new RectF();
    private final Bitmap picture;
    private final int blue;
    PosterBanner(android.content.Context context, int blue) { this.blue=blue;picture=PosterPhotoView.bitmap(context); }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();float w=b.width(),h=b.height();int save=canvas.save();canvas.translate(b.left,b.top);
        paint.setColor(blue);mask.rewind();mask.moveTo(w*.59f,0);mask.lineTo(w,0);mask.lineTo(w,h);mask.lineTo(w*.75f,h);mask.close();canvas.drawPath(mask,paint);
        mask.rewind();mask.moveTo(w*.38f,0);mask.lineTo(w*.66f,0);mask.lineTo(w*.80f,h);mask.lineTo(w*.49f,h);mask.close();
        int clip=canvas.save();canvas.clipPath(mask);destination.set(w*.38f,-h*.7f,w*.80f,h*1.4f);canvas.drawBitmap(picture,null,destination,paint);canvas.restoreToCount(clip);canvas.restoreToCount(save);
    }
    public void setAlpha(int value) {paint.setAlpha(value);invalidateSelf();}
    public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
    public int getOpacity() {return PixelFormat.TRANSLUCENT;}
}
