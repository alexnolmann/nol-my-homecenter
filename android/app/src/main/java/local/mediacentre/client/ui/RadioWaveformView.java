package local.mediacentre.client.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import local.mediacentre.client.radio.RadioPlaybackService;
import local.mediacentre.client.radio.AudioWaveform;
import local.mediacentre.client.radio.AudioPlayback;

/** A line drawn from the current player's captured PCM samples. Idle and failure are a flat line. */
final class RadioWaveformView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trace=new Path();
    private final float[] samples=new float[512];
    private boolean playing,attached,active,framePending;
    private final boolean poster;
    private final boolean compact;
    private final String prefix;
    private float gain=1;
    private final Runnable frame=()->{framePending=false;capture();};
    RadioWaveformView(Context context){this(context,false);}
    RadioWaveformView(Context context,boolean compact){
        super(context);this.compact=compact;prefix=compact?"mini_player_waveform_":"radio_waveform_";poster=new PanelStyle(context).poster;paint.setStyle(Paint.Style.STROKE);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        description("idle");
    }
    private void description(String state){String value=prefix+state;if(!value.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(value);}
    void setColor(int color){paint.setColor(color);invalidate();}
    void setPlaying(boolean value){boolean wasActive=active;playing=value;sync();if(wasActive&&active&&!framePending)capture();}
    private boolean visible(){return attached&&playing&&isShown()&&getWindowVisibility()==VISIBLE&&"playing".equals(compact?AudioPlayback.state():RadioPlaybackService.state());}
    private void sync(){
        boolean next=visible();
        if(next==active)return;
        active=next;removeCallbacks(frame);framePending=false;
        AudioWaveform.visible(getContext(),this,next);
        if(next)capture();
        else{java.util.Arrays.fill(samples,0);gain=1;description("idle");invalidate();}
    }
    private void capture(){
        if(!active||!visible()){sync();return;}
        boolean received=AudioWaveform.read(samples);
        description(received?"live":"unavailable");
        float peak=0;for(float value:samples)peak=Math.max(peak,Math.abs(value));
        float desired=peak<0.01f?1:Math.min(4,0.85f/peak);
        gain=desired<gain?desired:gain+(desired-gain)*0.12f;
        invalidate();if(received){framePending=true;postDelayed(frame,50);}
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float left=8*density,width=Math.max(0,getWidth()-16*density),center=getHeight()/2f;
        float height=Math.max(0,getHeight()/2f-(compact?5:14)*density);
        paint.setAlpha(28);paint.setStrokeWidth(density);canvas.drawLine(left,center,left+width,center,paint);
        trace.rewind();
        for(int i=0;i<samples.length;i++){
            float x=left+width*i/(samples.length-1),y=center-Math.max(-1,Math.min(1,samples[i]*gain))*height;
            if(i==0)trace.moveTo(x,y);else trace.lineTo(x,y);
        }
        if(poster&&active){paint.setAlpha(24);paint.setStrokeWidth(9*density);canvas.drawPath(trace,paint);paint.setAlpha(70);paint.setStrokeWidth(4*density);canvas.drawPath(trace,paint);}paint.setAlpha(active?255:65);paint.setStrokeWidth((poster?2f:1.5f)*density);canvas.drawPath(trace,paint);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;sync();}
    @Override protected void onDetachedFromWindow(){attached=false;sync();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(frame!=null)sync();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(frame!=null)sync();}
}
