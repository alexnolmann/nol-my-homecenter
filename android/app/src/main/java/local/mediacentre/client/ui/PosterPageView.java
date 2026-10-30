package local.mediacentre.client.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import local.mediacentre.client.R;

/** Full-page posters follow the available window; only their own lists and strips scroll. */
abstract class PosterPageView extends ViewGroup {
    private ScrollView viewport;
    private final OnLayoutChangeListener resized=(v,l,t,r,b,ol,ot,or,ob)->{
        if(b-t!=ob-ot){requestLayout();((ScrollView)v).scrollTo(0,0);}
    };
    PosterPageView(Context context){super(context);}
    protected final int pageHeight(int widthSpec,int heightSpec){
        if(viewport!=null&&viewport.getMeasuredHeight()>0)return viewport.getMeasuredHeight();
        return MeasureSpec.getMode(heightSpec)==MeasureSpec.UNSPECIFIED
                ?Math.round(MeasureSpec.getSize(widthSpec)*992f/1469f):MeasureSpec.getSize(heightSpec);
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        for(ViewParent p=getParent();p!=null;p=p.getParent())if(p instanceof ScrollView&&((View)p).getId()==R.id.content_scroll){viewport=(ScrollView)p;viewport.addOnLayoutChangeListener(resized);requestLayout();break;}
    }
    @Override protected void onDetachedFromWindow(){
        if(viewport!=null){viewport.removeOnLayoutChangeListener(resized);viewport=null;}
        super.onDetachedFromWindow();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();ViewParent parent=getParent();
        if(parent!=null){if(action==MotionEvent.ACTION_DOWN)parent.requestDisallowInterceptTouchEvent(true);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)parent.requestDisallowInterceptTouchEvent(false);}
        return super.dispatchTouchEvent(event);
    }
}
