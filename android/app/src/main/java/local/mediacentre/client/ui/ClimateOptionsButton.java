package local.mediacentre.client.ui;
import android.content.Context;
import android.graphics.*;
import android.widget.Button;

/** Three local dots, including on KitKat fonts without the ellipsis glyph. */
final class ClimateOptionsButton extends Button {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    ClimateOptionsButton(Context context){super(context);setText("");setPadding(0,0,0,0);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);ink.setColor(getCurrentTextColor());
        float unit=Math.min(getWidth(),getHeight())/24f;
        for(int n=-1;n<=1;n++)canvas.drawCircle(getWidth()/2f+n*5*unit,getHeight()/2f,1.5f*unit,ink);
    }
}
