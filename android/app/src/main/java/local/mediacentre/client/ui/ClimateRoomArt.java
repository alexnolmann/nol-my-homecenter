package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import local.mediacentre.client.R;

/** Decorative room scenes, decoded once and clipped with static native paths. */
final class ClimateRoomArt extends ImageView {
    static final int OFFICE=0,BEDROOM=1,LIVING=2;
    private static final Bitmap[] pictures=new Bitmap[3];
    private final Path mask=new Path();private final boolean header;
    static Bitmap bitmap(Context c,int kind){
        if(pictures[kind]==null){int resource=kind==OFFICE?R.drawable.neo_climate_office:kind==BEDROOM?R.drawable.neo_climate_bedroom:R.drawable.neo_climate_living;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inSampleSize=2;o.inPreferredConfig=Bitmap.Config.RGB_565;pictures[kind]=BitmapFactory.decodeResource(c.getResources(),resource,o);}
        return pictures[kind];
    }
    ClimateRoomArt(Context c,int kind,boolean header){super(c);this.header=header;setImageBitmap(bitmap(c,kind));setScaleType(ScaleType.CENTER_CROP);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);if(new PanelStyle(c).palette.dark)setColorFilter(0x18050d1c,PorterDuff.Mode.SRC_ATOP);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);mask.rewind();mask.moveTo(0,0);
        if(header){mask.lineTo(w*.78f,0);mask.lineTo(w,h*.70f);mask.lineTo(w*.84f,h);mask.lineTo(w*.57f,h);mask.cubicTo(w*.23f,h,w*.08f,h*.68f,0,0);}
        else{mask.cubicTo(w*.60f,0,w,h*.45f,w,h);mask.lineTo(0,h);}mask.close();
    }
    @Override public void draw(Canvas c){int save=c.save();c.clipPath(mask);super.draw(c);c.restoreToCount(save);}
}
