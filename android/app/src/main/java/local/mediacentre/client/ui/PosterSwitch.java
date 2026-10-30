package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.widget.CheckBox;

/** Native checkable/focus/accessibility behaviour, with a compact right-aligned toggle. */
final class PosterSwitch extends CheckBox {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track=new RectF();private int on,off,knob;
    PosterSwitch(Context context){super(context);setPadding(0,0,Math.round(64*getResources().getDisplayMetrics().density),0);}
    void colors(int on,int off,int knob){this.on=on;this.off=off;this.knob=knob;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float unit=getResources().getDisplayMetrics().density;
        float right=getWidth()-2*unit,center=getHeight()/2f;track.set(right-50*unit,center-15*unit,right,center+15*unit);
        paint.setColor(isChecked()?on:off);c.drawRoundRect(track,15*unit,15*unit,paint);paint.setColor(knob);c.drawCircle(isChecked()?right-15*unit:right-35*unit,center,12*unit,paint);
        if(isPressed()||isFocused()){paint.setColor(0x400738e8);c.drawCircle(isChecked()?right-15*unit:right-35*unit,center,18*unit,paint);}
    }
}
