package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.Button;

/** Draws the rotating glyph above a stationary button background. */
public final class WeatherRefreshButton extends Button {
    private final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float spinAngle;

    public WeatherRefreshButton(Context context,AttributeSet attributes){super(context,attributes);setText("");}

    void setSpinAngle(float angle){spinAngle=angle;invalidate();}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        icon.setColor(getCurrentTextColor());icon.setTextSize(28*getResources().getDisplayMetrics().scaledDensity);
        icon.setTypeface(getTypeface());icon.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics=icon.getFontMetrics();float x=getWidth()/2f,y=getHeight()/2f-(metrics.ascent+metrics.descent)/2f;
        canvas.save();canvas.rotate(spinAngle,getWidth()/2f,getHeight()/2f);canvas.drawText("↻",x,y,icon);canvas.restore();
    }
}
