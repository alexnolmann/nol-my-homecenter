package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Smooth decorative bars for the poster home. No audio capture. */
final class PosterHomeBars extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar=new RectF();
    private boolean playing,attached;
    private final Runnable frame=()->{if(canAnimate())invalidate();};
    PosterHomeBars(Context c,int color){super(c);paint.setColor(color);setContentDescription("home_radio_equalizer");}
    void setPlaying(boolean value){if(playing==value)return;playing=value;removeCallbacks(frame);invalidate();}
    private boolean canAnimate(){return attached&&playing&&isShown()&&getWindowVisibility()==VISIBLE;}
    @Override protected void onDraw(Canvas c){
        if(playing){float step=getWidth()/43f,width=step*.59f,center=getHeight()/2f;double time=SystemClock.uptimeMillis()/1000.0;
            for(int i=0;i<43;i++){double wave=Math.abs(Math.sin(i*.19-time*.8)*Math.cos(i*.33+time*.6));float height=getHeight()*(.15f+.77f*(float)wave);float left=i*step;bar.set(left,center-height/2,left+width,center+height/2);c.drawRoundRect(bar,width/2,width/2,paint);}}
        removeCallbacks(frame);if(canAnimate())postDelayed(frame,140);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(playing)invalidate();}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(frame!=null)removeCallbacks(frame);if(visibility==VISIBLE&&playing)invalidate();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(frame!=null)removeCallbacks(frame);if(visibility==VISIBLE&&playing)invalidate();}
}
