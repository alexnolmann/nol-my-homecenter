package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Decorative indicator for the home tile and catalogue row. Actual audio uses RadioWaveformView. */
final class RadioEqualizerView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar=new RectF();
    private boolean playing,attached;
    private final Runnable frame=()->{if(canAnimate())invalidate();};
    RadioEqualizerView(Context context){super(context);setContentDescription("home_radio_equalizer");}
    void setColor(int color){paint.setColor(color);invalidate();}
    void setPlaying(boolean value){if(playing==value)return;playing=value;removeCallbacks(frame);invalidate();}
    private boolean canAnimate(){return attached&&playing&&isShown()&&getWindowVisibility()==VISIBLE;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(playing){
            float unit=getResources().getDisplayMetrics().density,center=getHeight()/2f;
            paint.setAlpha(190);int count=Math.max(6,Math.min(44,(int)(getWidth()/(8*unit))));float gap=3*unit,width=Math.max(2*unit,(getWidth()-(count-1)*gap)/count);
            double time=SystemClock.uptimeMillis()/1000.0;
            for(int i=0;i<count;i++){
                double wave=Math.sin(time*(2.0+i%3*0.21)+i*1.23);
                double drift=Math.sin(time*0.79+i*0.71);
                float height=(float)((4*unit+(getHeight()-8*unit)*(0.5+0.5*wave)*(0.76+0.24*drift)));
                float left=i*(width+gap);
                bar.set(left,center-height/2,left+width,center+height/2);
                canvas.drawRoundRect(bar,2*unit,2*unit,paint);
            }
        }
        removeCallbacks(frame);if(canAnimate())postDelayed(frame,140);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(playing)invalidate();}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(frame!=null)removeCallbacks(frame);if(visibility==VISIBLE&&playing)invalidate();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(frame!=null)removeCallbacks(frame);if(visibility==VISIBLE&&playing)invalidate();}
}
