package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.ImageView;
import local.mediacentre.client.R;

/** A cached local photograph and simple Canvas masks; no render loop or off-screen layer. */
final class PosterPhotoView extends ImageView {
    static final int CIRCLE=0,ANGLED=1,HERO=2,SLICE=3;
    private static Bitmap artwork;
    private final int shape;
    private final Path mask=new Path();private final RectF oval=new RectF();
    PosterPhotoView(Context context,int shape){super(context);this.shape=shape;setScaleType(ScaleType.CENTER_CROP);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    static Bitmap bitmap(Context context){
        if(artwork==null){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;options.inPreferredConfig=Bitmap.Config.RGB_565;options.inScaled=false;artwork=BitmapFactory.decodeResource(context.getResources(),R.drawable.neo_poster_neva,options);}
        return artwork;
    }
    static PosterPhotoView artwork(Context context,int shape){PosterPhotoView view=new PosterPhotoView(context,shape);view.setImageBitmap(bitmap(context));return view;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);mask.rewind();if(shape==CIRCLE){float side=Math.min(w,h);oval.set((w-side)/2,(h-side)/2,(w+side)/2,(h+side)/2);mask.addOval(oval,Path.Direction.CW);}else if(shape==HERO){mask.moveTo(w*.12f,0);mask.lineTo(w*.64f,0);mask.lineTo(w,h*.7f);mask.cubicTo(w*.79f,h*1.18f,w*.12f,h*1.02f,0,h*.54f);mask.cubicTo(-w*.03f,h*.3f,w*.02f,h*.13f,w*.12f,0);mask.close();}else if(shape==SLICE){mask.moveTo(0,0);mask.lineTo(w*.74f,0);mask.lineTo(w,h*.55f);mask.lineTo(w*.74f,h);mask.lineTo(w*.13f,h);mask.close();}else{mask.moveTo(0,0);mask.lineTo(w*.72f,0);mask.cubicTo(w*.9f,h*.05f,w,h*.28f,w,h*.5f);mask.cubicTo(w,h*.73f,w*.9f,h*.95f,w*.72f,h);mask.lineTo(0,h);mask.close();}}
    @Override public void draw(Canvas canvas){int saved=canvas.save();canvas.clipPath(mask);super.draw(canvas);canvas.restoreToCount(saved);}
}
