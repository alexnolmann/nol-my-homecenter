package local.mediacentre.client.ui;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** One drawable avoids KitKat InsetDrawable intrinsic-size and mutation bugs. */
final class PanelCheck extends Drawable {
    private final int size,gap,accent,surface,line,ink;private final Paint paint=new Paint(3);private boolean checked;
    PanelCheck(int size,int gap,int accent,int surface,int line,int ink){this.size=size;this.gap=gap;this.accent=accent;this.surface=surface;this.line=line;this.ink=ink;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){boolean next=false;for(int state:states)if(state==android.R.attr.state_checked)next=true;if(next==checked)return false;checked=next;invalidateSelf();return true;}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(size/24f,size/24f);paint.setStyle(Paint.Style.FILL);paint.setColor(checked?accent:surface);c.drawRoundRect(new RectF(1,1,23,23),5,5,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(checked?accent:line);c.drawRoundRect(new RectF(1,1,23,23),5,5,paint);if(checked){paint.setColor(ink);paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();path.moveTo(6,12);path.lineTo(10,16);path.lineTo(18,8);c.drawPath(path,paint);}c.restore();}
    @Override public int getIntrinsicWidth(){return size+gap;}@Override public int getIntrinsicHeight(){return size;}
    @Override public ConstantState getConstantState(){return new ConstantState(){public Drawable newDrawable(){return new PanelCheck(size,gap,accent,surface,line,ink);}public int getChangingConfigurations(){return 0;}};}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
