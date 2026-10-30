package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** One compact player layout across poster pages; climate reserves a small status line below it. */
final class PosterMiniPlayer {
    static void bind(Context c,PosterAudioHeader.Placement placement,TextView name,View waveform,Button toggle,
            VolumeButton mute,SeekBar slider,TextView volume,int dy){
        PanelStyle s=new PanelStyle(c);int ink=s.palette.dark?s.color(PanelStyle.TEXT):0xff050d15;
        int signal=s.palette.dark?s.color(PanelStyle.SIGNAL):0xffff4b12,base=s.palette.dark?0xff3b4d66:0xffbfc6d0;
        placement.take(name,95,914+dy,224,61,35);name.setTextScaleX(.93f);name.setIncludeFontPadding(false);
        placement.take(waveform,340,916+dy,640,60,0);placement.take(toggle,1020,903+dy,80,80,0);s.miniTransportStyle(toggle);
        placement.take(mute,1100,903+dy,80,80,0);mute.setTextColor(ink);
        placement.take(slider,1184,917+dy,200,53,0);
        LayerDrawable track=new LayerDrawable(new Drawable[]{new Track(c,base),new ClipDrawable(new Track(c,signal),Gravity.LEFT,ClipDrawable.HORIZONTAL)});
        track.setId(0,android.R.id.background);track.setId(1,android.R.id.progress);slider.setProgressDrawable(track);slider.setThumb(new Thumb(c,signal));slider.setThumbOffset(s.dp(5));
        placement.take(volume,1394,922+dy,52,44,19);volume.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    }
    private static final class Track extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int radius;
        Track(Context c,int color){radius=new PanelStyle(c).dp(3);paint.setColor(color);}
        public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(new RectF(b.left,b.exactCenterY()-radius,b.right,b.exactCenterY()+radius),radius,radius,paint);}
        public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private static final class Thumb extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int size,radius;
        Thumb(Context c,int color){size=new PanelStyle(c).dp(10);radius=new PanelStyle(c).dp(3);paint.setColor(color);}
        public void draw(Canvas c){Rect b=getBounds();c.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,paint);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void setAlpha(int a){paint.setAlpha(a);}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private PosterMiniPlayer(){}
}
