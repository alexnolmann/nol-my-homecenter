package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.SeekBar;

/** Keeps the native thumb aligned when KitKat centers a shorter track in a tall touch target. */
final class CenteredSeekBar extends SeekBar {
    CenteredSeekBar(Context context){super(context);}

    @Override protected synchronized void onDraw(Canvas canvas){
        Drawable thumb=getThumb(),track=getProgressDrawable();
        if(thumb!=null && track!=null){
            Rect bounds=thumb.getBounds();
            int height=bounds.height();
            int top=track.getBounds().centerY()-height/2;
            if(bounds.top!=top)thumb.setBounds(bounds.left,top,bounds.right,top+height);
        }
        super.onDraw(canvas);
    }
}
