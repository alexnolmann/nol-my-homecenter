package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.Button;

/** Draws transport symbols as vectors so they remain legible on Android 4.4 fonts. */
final class RadioTransportButton extends Button {
    static final int PREVIOUS=0,PLAY=1,STOP=2,NEXT=3;
    private final Paint icon=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle=new Path();
    private final RectF stopShape=new RectF(-13f,-13f,13f,13f);
    private final int action;

    RadioTransportButton(Context context,int action){super(context);this.action=action;setText("");setAllCaps(false);setPadding(0,0,0,0);}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        icon.setColor(getCurrentTextColor());icon.setStyle(Paint.Style.FILL);
        float scale=Math.min(getWidth(),getHeight())/64f;
        canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);canvas.scale(scale,scale);
        if(action==STOP){canvas.drawRoundRect(stopShape,3f,3f,icon);}
        else if(action==PLAY){triangle.reset();triangle.moveTo(-10f,-16f);triangle.lineTo(17f,0f);triangle.lineTo(-10f,16f);triangle.close();canvas.drawPath(triangle,icon);}
        else{
            if(action==NEXT)canvas.scale(-1f,1f);
            canvas.drawRect(-18f,-15f,-13f,15f,icon);
            triangle.reset();triangle.moveTo(-12f,0f);triangle.lineTo(14f,-16f);triangle.lineTo(14f,16f);triangle.close();canvas.drawPath(triangle,icon);
        }
        canvas.restore();
    }
}
