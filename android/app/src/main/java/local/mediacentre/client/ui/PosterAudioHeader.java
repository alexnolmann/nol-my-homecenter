package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.view.Gravity;
import android.view.View;
import android.widget.TextClock;

/** Shared reference geometry for full-width poster audio headers. */
final class PosterAudioHeader {
    static final int CLOCK_X=940,CLOCK_Y=14,CLOCK_W=125,CLOCK_H=80,CLOCK_FONT=52;
    static final int OUTPUT_X=1115,OUTPUT_Y=24,OUTPUT_W=335,OUTPUT_H=60;
    interface Placement {void take(View view,float x,float y,float width,float height,float font);}
    static void bind(Placement placement,TextClock clock,AudioOutputControls output){
        placement.take(clock,CLOCK_X,CLOCK_Y,CLOCK_W,CLOCK_H,CLOCK_FONT);clock(clock);
        placement.take(output,OUTPUT_X,OUTPUT_Y,OUTPUT_W,OUTPUT_H,0);output.setBackgroundColor(Color.TRANSPARENT);output.contrast(true);
    }
    static void clock(TextClock clock){clock.setTypeface(PosterTypography.face(clock.getContext()));clock.setTextColor(Color.WHITE);clock.setTextScaleX(1);clock.setGravity(Gravity.CENTER);clock.setIncludeFontPadding(false);}
    static void draw(Canvas c,Paint p,Path path,int blue){
        p.setColor(blue);path.rewind();path.moveTo(740,0);path.lineTo(1469,0);path.lineTo(1469,112);path.lineTo(852,112);path.close();c.drawPath(path,p);
        p.setColor(0x90ffffff);p.setStrokeWidth(1);c.drawLine(1087,25,1087,93,p);
    }
    /** Can cover decorative header artwork without changing its crop or the page body. */
    static final class Background extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();private final int blue;
        Background(Context c,int blue){super(c);this.blue=blue;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){int save=c.save();c.scale(getWidth()/1469f,getHeight()/112f);PosterAudioHeader.draw(c,paint,path,blue);c.restoreToCount(save);}
    }
    private PosterAudioHeader(){}
}
